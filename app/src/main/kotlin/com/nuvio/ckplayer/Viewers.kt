package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Who's watching (1.94.0; the shared player's `viewers`, the same wire). An account holds up to two profiles. Each
 * keeps its own watch history — Continue Watching, resume points, ticks, My List; add-ons and settings stay the
 * account's. The first profile's history IS the account's history as it always was ("progress", "library"), so nothing
 * moves for anyone; the second's sits beside it ("progress_b", "library_b") on this device and in the cloud. The list is
 * synced (`viewers`: {at, list:[{id:"a"|"b", name, av}]}, newest wins); which profile this device is on is not.
 */
object Viewers {
    private const val PREFS = "ckplayer"
    private val PER_PROFILE = setOf("progress", "library")

    data class Viewer(val id: String, val name: String, val av: String)

    /** "a" or "b": the profile this device is on. */
    var current by mutableStateOf("a"); private set
    /** Bumped when the list changes (a sync, a rename): the picker and Settings redraw. */
    var version by mutableIntStateOf(0); private set
    /** At launch with two profiles: the picker is up until one is chosen. */
    var asking by mutableStateOf(false); private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        current = if (prefs(ctx).getString("viewer", "a") == "b") "b" else "a"
        if (current == "b" && list(ctx).size < 2) setCurrent(ctx, "a")
        asking = list(ctx).size == 2
    }

    /** The name a per-profile store goes under for the profile this device is on. */
    fun local(key: String): String = if (current == "b" && key in PER_PROFILE) key + "_b" else key
    internal fun local(key: String, viewer: String): String = if (viewer == "b" && key in PER_PROFILE) key + "_b" else key

    internal fun doc(ctx: Context): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString("viewers_v1", "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun saveDoc(ctx: Context, d: JSONObject) { prefs(ctx).edit().putString("viewers_v1", d.toString()).apply(); version++ }

    /** The profiles, the first always there. */
    fun list(ctx: Context): List<Viewer> = parse(doc(ctx))

    internal fun parse(d: JSONObject): List<Viewer> {
        val out = LinkedHashMap<String, Viewer>()
        val a = d.optJSONArray("list") ?: JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if ((id == "a" || id == "b") && id !in out) out[id] = Viewer(id, o.optString("name").take(24), o.optString("av"))
        }
        if ("a" !in out) out["a"] = Viewer("a", "", "")
        return out.values.sortedBy { it.id }
    }

    fun name(v: Viewer): String = v.name.trim().ifEmpty { if (v.id == "b") "Profile 2" else Cloud.profile?.name?.takeIf { it.isNotBlank() } ?: "You" }
    fun avatar(v: Viewer): String = if (v.id == "b") v.av.ifEmpty { "#0A84FF" } else Cloud.profile?.avatar ?: "#636366"
    fun cur(ctx: Context): Viewer = list(ctx).firstOrNull { it.id == current } ?: list(ctx).first()

    private fun setList(ctx: Context, l: List<Viewer>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name.take(24)).put("av", it.av)) }
        saveDoc(ctx, JSONObject().put("at", System.currentTimeMillis()).put("list", a))
        Cloud.noteChanged(ctx, "viewers")
    }

    fun add(ctx: Context) { val l = list(ctx); if (l.size < 2) setList(ctx, l + Viewer("b", "", "#0A84FF")) }
    fun rename(ctx: Context, name: String) = setList(ctx, list(ctx).map { if (it.id == current) it.copy(name = name.trim().take(24)) else it })
    suspend fun removeSecond(ctx: Context) {
        if (current == "b") switchTo(ctx, "a")
        setList(ctx, list(ctx).filter { it.id != "b" })
    }

    private fun setCurrent(ctx: Context, id: String) { current = id; prefs(ctx).edit().putString("viewer", id).apply() }

    /** On to the other profile: what this one changed goes up under its own names first, then the other's history is
        read (this device's copy at once, the cloud's after a pull) and every screen that shows a history redraws. */
    suspend fun switchTo(ctx: Context, id: String) {
        val to = if (id == "b") "b" else "a"
        asking = false
        if (to == current) return
        Cloud.flushProfile(ctx)
        setCurrent(ctx, to)
        Progress.reset()
        Library.reset()
        Cloud.pullAll(ctx, force = true)
        Cloud.onApplied?.invoke(setOf("progress", "library"))
    }

    fun answered() { asking = false }

    /** A synced list (Cloud.merge): newest wins; the second removed elsewhere sends this device back to the first. */
    internal fun merge(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        val local = doc(ctx)
        val ra = remote.optLong("at"); val la = local.optLong("at")
        if (remote.optJSONArray("list") == null) return false to (la > 0)
        if (ra > la) {
            saveDoc(ctx, JSONObject().put("at", ra).put("list", remote.getJSONArray("list")))
            if (list(ctx).size < 2) {
                asking = false
                if (current == "b") { setCurrent(ctx, "a"); Progress.reset(); Library.reset() }
            }
            return true to false
        }
        return false to (la > ra)
    }
}

/** At launch with two profiles: who's watching? (the remote's ←/→ and OK; Back keeps the profile this device was on) */
@Composable
internal fun WhoPicker() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = remember(Viewers.version) { Viewers.list(ctx) }
    val first = remember { FocusRequester() }
    androidx.activity.compose.BackHandler { Viewers.answered() }
    Box(Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Who's watching?", color = TextC, fontSize = 28.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Row(Modifier.padding(top = 36.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                l.forEach { v ->
                    Column(
                        Modifier
                            .then(if (v.id == Viewers.current) Modifier.focusRequester(first) else Modifier)
                            .focusRing(RoundedCornerShape(20.dp), landing = false)
                            .clickable { scope.launch { Viewers.switchTo(ctx, v.id) } }
                            .padding(16.dp)
                            .width(120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(Viewers.avatar(v), Viewers.name(v), 104.dp)
                        Text(Viewers.name(v), color = MutedC, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** Settings: the profiles (pick = switch, + = add and switch to it), this one's name, removing the second. */
@Composable
internal fun WhoSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = remember(Viewers.version, Viewers.current) { Viewers.list(ctx) }
    SettingsHeader("WHO'S WATCHING", "Each profile keeps its own Continue Watching, resume points and My List · Add-ons and settings are shared")
    SettingsGroup {
        SettingsChips(
            "Profiles", if (l.size < 2) "Add a second profile for someone else in the house" else "Tap to switch",
            l.map { it.id to Viewers.name(it) } + (if (l.size < 2) listOf("+" to "+ Add a profile") else emptyList()),
            Viewers.current, divider = l.size > 1,
        ) { id ->
            scope.launch {
                if (id == "+") {
                    Viewers.add(ctx); Viewers.switchTo(ctx, "b")
                    Toasts.show("Added Profile 2 — it starts with an empty history")
                } else if (id != Viewers.current) {
                    Viewers.switchTo(ctx, id)
                    Toasts.show("Watching as ${Viewers.name(Viewers.cur(ctx))}")
                }
            }
        }
        if (l.size > 1) {
            val typing = tvTyping()
            var draft by remember(Viewers.current, Viewers.version) { mutableStateOf(Viewers.cur(ctx).name) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("This profile's name", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(24) },
                    placeholder = { Text(Viewers.name(Viewers.cur(ctx)), color = MutedC) },
                    singleLine = true,
                    readOnly = typing.readOnly,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { Viewers.rename(ctx, draft); typing.done() }),
                    modifier = typing.modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
            }
            SettingsRow(Icons.Filled.PersonRemove, "Remove the second profile", "Its history is kept, and comes back if a second profile is added again", false) {
                scope.launch { Viewers.removeSecond(ctx); Toasts.show("Removed the second profile") }
            }
        }
    }
}
