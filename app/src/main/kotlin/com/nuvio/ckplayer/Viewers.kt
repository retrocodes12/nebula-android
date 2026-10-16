package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * keeps its own watch history — Continue Watching, resume points, ticks, My List — and its own add-ons; settings stay
 * the account's. The first profile's IS the account's as it always was ("progress", "library", "addons"), so nothing
 * moves for anyone; the second's sits beside it ("progress_b", "library_b", "addons_b" + its stamps "addons_sync_b") on
 * this device and in the cloud. The list is synced (`viewers`: {at, list:[{id:"a"|"b", name, av}]}, newest wins); which
 * profile this device is on is not.
 */
object Viewers {
    private const val PREFS = "ckplayer"
    private val PER_PROFILE = setOf("progress", "library", "addons", "addons_sync")

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

    /** The second profile's first use of add-ons on this device (a switch to it, or a launch on it after an update from
        when add-ons were shared), decided BEFORE its pull. The cloud is asked first: if the account already holds
        "addons_b" (made on another device), the second starts empty here and the pull adopts that list as it is — a
        copy of the first's would leak the first's add-ons into it. Otherwise (no "addons_b", signed out, or no answer)
        it starts as a copy of the first's list AND its stamps, the first's own times, so a removal synced later still
        wins the merge; an empty first list is not copied. True when the second's store was written. */
    suspend fun prepareAddons(ctx: Context, viewer: String): Boolean {
        if (viewer != "b") return false
        val p = prefs(ctx)
        val wk = local("addons", "b")
        if (p.contains(wk)) return false
        val inCloud = Cloud.remoteKeys(ctx)?.contains(wk) == true
        if (p.contains(wk)) return false                      // (a pull wrote it while the cloud was asked)
        val (list, sync) = addonStart(false, inCloud, p.getString("addons", null), p.getString("addons_sync", null)) ?: return false
        val e = p.edit().putString(wk, list)
        if (sync != null) e.putString(local("addons_sync", "b"), sync)
        e.apply()
        if (inCloud) Cloud.forgetRev(ctx, wk)                 // the pull must fetch it, whatever this device last saw
        return true
    }

    /** How the second profile's add-ons start on this device (list, stamps), or null to leave them as they are: it has
        a store already; the cloud has its own list → empty, for the pull to fill; else a copy of the first's (null when
        that is empty or unreadable). Pure, for the tests. */
    internal fun addonStart(bHas: Boolean, cloudHasB: Boolean, aList: String?, aSync: String?): Pair<String, String?>? {
        if (bHas) return null
        if (cloudHasB) return "[]" to "{}"
        if (aList == null || runCatching { JSONArray(aList).length() }.getOrDefault(0) == 0) return null
        return aList to aSync?.takeIf { runCatching { JSONObject(it) }.isSuccess }
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
    // the first profile wears the account's colour until it is given one of its own (the picker's editor)
    fun avatar(v: Viewer): String = v.av.ifEmpty { if (v.id == "b") "#0A84FF" else Cloud.profile?.avatar ?: "#636366" }
    fun cur(ctx: Context): Viewer = list(ctx).firstOrNull { it.id == current } ?: list(ctx).first()

    private fun setList(ctx: Context, l: List<Viewer>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name.take(24)).put("av", it.av)) }
        saveDoc(ctx, JSONObject().put("at", System.currentTimeMillis()).put("list", a))
        Cloud.noteChanged(ctx, "viewers")
    }

    fun add(ctx: Context) { val l = list(ctx); if (l.size < 2) setList(ctx, l + Viewer("b", "", "#0A84FF")) }
    fun edit(ctx: Context, id: String, name: String, av: String) =
        setList(ctx, list(ctx).map { if (it.id == id) it.copy(name = name.trim().take(24), av = av) else it })
    suspend fun removeSecond(ctx: Context) {
        if (current == "b") switchTo(ctx, "a")
        setList(ctx, list(ctx).filter { it.id != "b" })
    }

    private fun setCurrent(ctx: Context, id: String) { current = id; prefs(ctx).edit().putString("viewer", id).apply() }

    /** On to the other profile: what this one changed goes up under its own names first, then the other's history and
        add-ons are read (this device's copy at once, the cloud's after a pull) and every screen that shows them redraws. */
    suspend fun switchTo(ctx: Context, id: String) {
        val to = if (id == "b") "b" else "a"
        asking = false
        if (to == current) return
        Cloud.flushProfile(ctx)
        prepareAddons(ctx, to)                                 // before the pull below, and before the list is read
        setCurrent(ctx, to)
        Progress.reset()
        Library.reset()
        // this device's copy at once (Home, Search, the Guide, the add-ons screen re-read the list); the pull's own
        // changes redraw again when they land
        Cloud.onApplied?.invoke(setOf("progress", "library", "addons"))
        Cloud.pullAll(ctx, force = true)
    }

    fun answered() { asking = false }

    /** Settings › Who's watching: the same picker, to switch, add or edit. */
    fun open() { asking = true }

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

/**
 * Who's watching? (10-16: the layout of the reference screenshots the Founder sent — the mark and the question at the
 * top, two big round monograms with their names under them, "Manage Profiles" below; managing puts a pencil on each,
 * adds an "Add Profile" circle while there is room for one, and the button reads Done.) At launch with two profiles,
 * and from Settings › Who's watching. Back leaves managing, then keeps the profile this device was on.
 */
@Composable
internal fun WhoPicker() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = remember(Viewers.version) { Viewers.list(ctx) }
    var managing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val tv = Account.isTv(ctx)
    androidx.activity.compose.BackHandler {
        when {
            editing != null -> editing = null
            managing -> managing = false
            else -> Viewers.answered()
        }
    }
    Box(Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}) {
        val ed = editing
        if (ed != null) {
            WhoEditor(ed, onDone = { editing = null })
            return@Box
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = if (tv) 48.dp else 72.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◆ ", color = Red, fontSize = 22.sp)
                Text("Nebula", color = TextC, fontSize = 26.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            }
            Text(
                "Who's watching?", color = TextC, fontSize = 32.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp, modifier = Modifier.padding(top = 22.dp),
            )
            // two to a row, the Add circle wrapping under them while managing (as the reference)
            val tiles: List<String> = l.map { it.id } + (if (managing && l.size < 2) listOf("+") else emptyList())
            Column(Modifier.padding(top = if (tv) 40.dp else 64.dp), verticalArrangement = Arrangement.spacedBy(36.dp)) {
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (tv) 56.dp else 40.dp)) {
                        pair.forEach { id ->
                            val v = l.firstOrNull { it.id == id }
                            WhoTile(
                                v = v, managing = managing, current = v?.id == Viewers.current, tv = tv,
                                modifier = if (v?.id == Viewers.current) Modifier.focusRequester(first) else Modifier,
                            ) {
                                when {
                                    v == null -> { Viewers.add(ctx); editing = "b" }
                                    managing -> editing = v.id
                                    else -> scope.launch { Viewers.switchTo(ctx, v.id) }
                                }
                            }
                        }
                    }
                }
            }
            val pill = RoundedCornerShape(50)
            Box(
                Modifier.padding(top = 56.dp)
                    .clip(pill)
                    .background(if (managing) Surface2 else Color.Transparent, pill)
                    .border(1.dp, if (managing) Line2 else LineC, pill)
                    .focusRing(pill, landing = false)
                    .clickable { managing = !managing }
                    .padding(horizontal = 34.dp, vertical = 14.dp),
            ) {
                Text(
                    if (managing) "Done" else "Manage Profiles",
                    color = if (managing) TextC else MutedC, fontSize = 18.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    LaunchedEffect(editing) { if (editing == null) runCatching { withFrameNanos {}; first.requestFocus() } }
}

/** One profile's circle and name (or, with v null, the Add Profile circle). */
@Composable
private fun WhoTile(v: Viewers.Viewer?, managing: Boolean, current: Boolean, tv: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val size = if (tv) 132.dp else 150.dp
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = focused && remoteMode()
    Column(
        modifier.width(size + 30.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size + 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(size + 12.dp)
                    // the remote's white ring; the profile this device is on wears a quiet grey one
                    .border(if (ring) 3.dp else 5.dp, if (ring) Color.White else if (current && v != null) Color(0x33FFFFFF) else Color.Transparent, CircleShape)
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (v == null) {
                    Box(
                        Modifier.fillMaxSize().background(SurfaceC, CircleShape).border(1.5.dp, Line2, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, contentDescription = null, tint = MutedC, modifier = Modifier.size(size * 0.3f)) }
                } else WhoMonogram(Viewers.avatar(v), Viewers.name(v), size)
            }
            if (managing && v != null) Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 10.dp).size(44.dp)
                    .border(2.dp, Bg, CircleShape).background(Color(0xFFF2F2F7), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.Black, modifier = Modifier.size(20.dp)) }
        }
        Text(
            if (v == null) "Add Profile" else Viewers.name(v),
            color = if (v == null) MutedC else TextC, fontSize = 18.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** The big monogram: the profile's colour as a deep tint, a ring of it, and the letter in it. */
@Composable
private fun WhoMonogram(hex: String, name: String, size: Dp) {
    val c = avatarColor(hex)
    Box(
        Modifier.size(size).background(c.copy(alpha = 0.2f).compositeOver(Bg), CircleShape).border(2.dp, c.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().removePrefix("@").take(1).uppercase()
        if (initial.isEmpty()) Icon(Icons.Filled.Person, contentDescription = null, tint = c, modifier = Modifier.size(size * 0.45f))
        else Text(initial, color = c, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold, fontFamily = Sans)
    }
}

/** A pencil pressed: the profile's name and colour; the second can be removed (its history is kept). */
@Composable
private fun WhoEditor(id: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v = remember(id, Viewers.version) { Viewers.list(ctx).firstOrNull { it.id == id } }
    if (v == null) { LaunchedEffect(Unit) { onDone() }; return }          // (removed on another device meanwhile)
    var draft by remember(id) { mutableStateOf(v.name) }
    var av by remember(id) { mutableStateOf(Viewers.avatar(v)) }
    val typing = tvTyping()
    val save = { Viewers.edit(ctx, id, draft, av); typing.done(); onDone() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 72.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Edit profile", color = TextC, fontSize = 28.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        Box(Modifier.padding(top = 32.dp)) {
            WhoMonogram(av, draft.ifBlank { Viewers.name(v) }, 132.dp)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(24) },
            placeholder = { Text(Viewers.name(v.copy(name = "")), color = MutedC) },
            singleLine = true,
            readOnly = typing.readOnly,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = typing.modifier.widthIn(max = 360.dp).fillMaxWidth().padding(top = 28.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                focusedTextColor = TextC, unfocusedTextColor = TextC,
            ),
        )
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Account.AVATARS.forEach { hex ->
                val on = hex.equals(av, ignoreCase = true)
                Box(
                    Modifier.size(32.dp)
                        .border(2.dp, if (on) Color.White else Color.Transparent, CircleShape).padding(4.dp)
                        .background(avatarColor(hex), CircleShape)
                        .focusRing(CircleShape, landing = false)
                        .clickable { av = hex },
                )
            }
        }
        val pill = RoundedCornerShape(50)
        Box(
            Modifier.padding(top = 36.dp).clip(pill).background(Color(0xFFF2F2F7), pill)
                .focusRing(pill, landing = false).clickable { save() }
                .padding(horizontal = 40.dp, vertical = 14.dp),
        ) { Text("Save", color = Color.Black, fontSize = 18.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold) }
        if (id == "b") Text(
            "Remove profile", color = Color(0xFFFF453A), fontSize = 16.sp, fontFamily = Sans, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 18.dp).clip(pill).focusRing(pill, landing = false)
                .clickable { scope.launch { Viewers.removeSecond(ctx); Toasts.show("Removed the second profile. Its history and add-ons are kept if you add it again."); onDone() } }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
        Text(
            "Each profile keeps its own Continue Watching, resume points, My List and add-ons. Settings are shared.",
            color = MutedC, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp).padding(top = 24.dp),
        )
    }
}

/** Settings: one row that opens the picker (switch, add, edit — all there now). */
@Composable
internal fun WhoSettings() {
    val ctx = LocalContext.current
    val l = remember(Viewers.version, Viewers.current) { Viewers.list(ctx) }
    SettingsHeader("WHO'S WATCHING", "Each profile keeps its own Continue Watching, resume points, My List and add-ons · Settings are shared")
    SettingsGroup {
        SettingsRow(
            Icons.Filled.People, "Who's watching",
            if (l.size < 2) "Add a second profile for someone else in the house" else "Watching as ${Viewers.name(Viewers.cur(ctx))} · switch or edit",
            false,
        ) { Viewers.open() }
    }
}
