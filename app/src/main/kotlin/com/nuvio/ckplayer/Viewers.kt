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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Who's watching (1.94.0 two profiles; 1.114.0 six; the shared player's wire). An account holds up to six profiles.
 * Each keeps its own watch history (Continue Watching, resume points, ticks, My List), its own tracking accounts and
 * its own add-ons, or by choice the first profile's. The first profile's IS the account's as it always was
 * ("progress", "library", "addons"), so nothing moves for anyone; every other profile's sits beside it under its
 * letter ("progress_b" … "progress_f", "addons_b" + its stamps "addons_sync_b" …) on this device and in the cloud.
 *
 * Two synced lists say who exists, newest wins on each: `viewers` ({at, list:[{id:"a"|"b", name, av, lock?}]}, the
 * shape released apps read and rewrite) and `people` ({at, list:[{id:"c"…"f", name, av, lock?}], x:{id:{pic?, share?,
 * born?}}}: profiles three to six, and what the older shape has no room for: a picture, "use the first profile's
 * add-ons", and `born`, the stamp that tells a profile from a later one in the same slot). Which profile this device
 * is on is never synced. A profile may carry a 4-digit PIN (`lock`, ProfilePin.kt): which profiles were let in on
 * this run is memory only, so every launch asks again.
 *
 * Removing a profile is for good: its history, list, add-ons and tracking go from this device, from the account
 * (Cloud.dropProfile) and from every other device on its next sync (reconcile: a slot that is empty now, or holds a
 * profile born at another time, is wiped here). Settings are this device's on Android, shared by its profiles.
 */
object Viewers {
    private const val PREFS = "ckplayer"
    private val PER_PROFILE = setOf("progress", "library", "addons", "addons_sync", "track")   // (tracking accounts since 11-10)
    /** A removed profile's documents on the account (the web player keeps a profile's settings there too). */
    internal val WIRE_KEYS = listOf("progress", "library", "addons", "track", "settings")
    internal val IDS = listOf("a", "b", "c", "d", "e", "f")
    internal val REST = IDS.drop(1)
    const val MAX = 6
    private val AV_DEF = mapOf("b" to "#0A84FF", "c" to "#30D158", "d" to "#BF5AF2", "e" to "#FF9F0A", "f" to "#FF375F")
    private val PIC_NAME = Regex("^[a-z]{1,16}$")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    data class Viewer(
        val id: String, val name: String, val av: String, val lock: ProfilePin.Lock? = null,
        val pic: String = "", val share: Boolean = false, val born: Long = 0L,
    )

    /** "a" … "f": the profile this device is on. */
    var current by mutableStateOf("a"); private set
    /** Bumped when the list changes (a sync, a rename): the picker and Settings redraw. */
    var version by mutableIntStateOf(0); private set
    /** At launch with more than one profile: the picker is up until one is chosen. */
    var asking by mutableStateOf(false); private set
    /** The picker is the way in (at launch, or the profile this device was on just went): Back cannot leave it while
        that profile is locked and not let in. False when it was opened from Settings. */
    var launchGate by mutableStateOf(false); private set
    /** Profiles let in on this run: their PIN typed, or locked or unlocked here. Never stored. */
    private val unlocked = HashSet<String>()
    /** The profiles that use the first profile's add-ons (kept in memory: [local] is asked constantly). */
    @Volatile private var sharing: Set<String> = emptySet()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val l = list(ctx)
        sharing = l.filter { it.share }.map { it.id }.toSet()
        val stored = prefs(ctx).getString("viewer", "a") ?: "a"
        current = if (l.any { it.id == stored }) stored else "a"
        if (current != stored) prefs(ctx).edit().putString("viewer", current).apply()
        reconcile(ctx)                       // a first run of this build notes who exists; later ones clear what is gone
        asking = (l.size >= 2 && askAtLaunch(ctx)) || cur(ctx).lock != null
        launchGate = asking
    }

    /** Ask who's watching when the app opens (this device's choice; a profile with a PIN always asks). */
    fun askAtLaunch(ctx: Context): Boolean = prefs(ctx).getBoolean("who_ask", true)
    fun setAskAtLaunch(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("who_ask", v).apply(); version++ }

    /** A profile's first use of its own add-ons on this device (a switch to it, or a launch on it after an update from
        when add-ons were shared), decided BEFORE its pull. The cloud is asked first: if the account already holds its
        list (made on another device), the profile starts empty here and the pull adopts that list as it is — a copy of
        the first's would leak the first's add-ons into it. Otherwise (no such list, signed out, or no answer) it starts
        as a copy of the first's list AND its stamps, the first's own times, so a removal synced later still wins the
        merge; an empty first list is not copied. True when the profile's store was written. */
    suspend fun prepareAddons(ctx: Context, viewer: String): Boolean {
        if (viewer == "a" || viewer !in IDS || viewer in sharing) return false
        val p = prefs(ctx)
        val wk = local("addons", viewer)
        if (p.contains(wk)) return false
        val keys = Cloud.remoteKeys(ctx)
        val inCloud = keys?.contains(wk) == true
        if (p.contains(wk)) return false                      // (a pull wrote it while the cloud was asked)
        val (list, sync) = addonStart(false, inCloud, p.getString("addons", null), p.getString("addons_sync", null)) ?: return false
        val e = p.edit().putString(wk, list)
        if (sync != null) e.putString(local("addons_sync", viewer), sync)
        // a copy made without the account's answer (signed out, no answer) only stands in: the account's own list,
        // when a pull finds one, replaces it whole — merged, the first's add-ons went up into the other's (10-26)
        if (standIn(keys, inCloud)) e.putBoolean(seedKey(viewer), true) else e.remove(seedKey(viewer))
        e.apply()
        if (inCloud) Cloud.forgetRev(ctx, wk)                 // the pull must fetch it, whatever this device last saw
        return true
    }

    /** How a profile's own add-ons start on this device (list, stamps), or null to leave them as they are: it has a
        store already; the cloud has its own list → empty, for the pull to fill; else a copy of the first's (null when
        that is empty or unreadable). Pure, for the tests. */
    internal fun addonStart(bHas: Boolean, cloudHasB: Boolean, aList: String?, aSync: String?): Pair<String, String?>? {
        if (bHas) return null
        if (cloudHasB) return "[]" to "{}"
        if (aList == null || runCatching { JSONArray(aList).length() }.getOrDefault(0) == 0) return null
        return aList to aSync?.takeIf { runCatching { JSONObject(it) }.isSuccess }
    }

    /** A profile's copy of the first's add-ons is a stand-in when the account was not asked or did not answer (the key
        list is null) and none was found there. Pure, for the tests. */
    internal fun standIn(keys: Set<String>?, cloudHasB: Boolean): Boolean = keys == null && !cloudHasB
    private fun seedKey(id: String) = "addons_${id}_seed"
    /** The profile this device is on holds a stand-in copy (never the first, never one that uses the first's list). */
    fun seedHeld(ctx: Context): Boolean = current != "a" && current !in sharing && prefs(ctx).getBoolean(seedKey(current), false)
    fun seedDrop(ctx: Context) { val k = seedKey(current); if (prefs(ctx).contains(k)) prefs(ctx).edit().remove(k).apply() }

    /** This device's data now belongs to another account (signed into a different one): the other profiles — their
        names, PINs, history and add-ons — were the old account's and must not go up into the new one. Back on the
        first; the lists empty until the new account's arrive. The first profile's own data merges as it always has. */
    fun resetForAccount(ctx: Context) {
        if (current != "a") { setCurrent(ctx, "a"); Progress.reset(); Library.reset(); Track.reset() }
        REST.forEach { wipe(ctx, it) }
        prefs(ctx).edit().remove("viewers_v1").remove("people_v1").remove("who_born").remove("who_drop").apply()
        sharing = emptySet()
        unlocked.clear()
        asking = false; launchGate = false
        version++
    }

    /** After a sign-in, once its pull has landed: who's watching, as at a launch, when the account holds more than one
        profile or a PIN on the one this device is on. */
    fun askAfterSignIn(ctx: Context) {
        if (asking) return
        if (list(ctx).size >= 2 || needsPin(ctx, current)) { asking = true; launchGate = true }
    }

    /** The name a per-profile store goes under for the profile this device is on. */
    fun local(key: String): String = local(key, current, current in sharing)
    internal fun local(key: String, viewer: String, shares: Boolean = false): String = when {
        viewer == "a" || viewer !in IDS || key !in PER_PROFILE -> key
        shares && (key == "addons" || key == "addons_sync") -> key        // (it uses the first profile's list)
        else -> key + "_" + viewer
    }
    /** A wire or store name without its profile's letter ("progress_c" → "progress"); others come back as they are. */
    internal fun baseKey(wk: String): String {
        val i = wk.lastIndexOf('_')
        if (i <= 0 || i != wk.length - 2 || wk.substring(i + 1) !in REST) return wk
        val base = wk.substring(0, i)
        return if (base in PER_PROFILE) base else wk
    }

    internal fun doc(ctx: Context): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString("viewers_v1", "{}") ?: "{}") }.getOrDefault(JSONObject())
    internal fun people(ctx: Context): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString("people_v1", "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun saveDoc(ctx: Context, d: JSONObject) { prefs(ctx).edit().putString("viewers_v1", d.toString()).apply(); version++ }
    private fun savePeople(ctx: Context, d: JSONObject) {
        prefs(ctx).edit().putString("people_v1", d.toString()).apply()
        sharing = list(ctx).filter { it.share }.map { it.id }.toSet()
        version++
    }

    /** The profiles, the first always there, in order. */
    fun list(ctx: Context): List<Viewer> = parse(doc(ctx), people(ctx))

    /** [d] is `viewers` (the first two), [p] is `people` (the rest, and everyone's extras). */
    internal fun parse(d: JSONObject, p: JSONObject = JSONObject()): List<Viewer> {
        val out = LinkedHashMap<String, Viewer>()
        val x = p.optJSONObject("x")
        fun take(a: JSONArray?, ids: List<String>) {
            if (a == null) return
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id !in ids || id in out) continue
                val e = x?.optJSONObject(id)
                out[id] = Viewer(
                    id, o.optString("name").take(24), o.optString("av"), ProfilePin.lockOf(o.optJSONObject("lock")),
                    pic = e?.optString("pic").orEmpty().takeIf { PIC_NAME.matches(it) }.orEmpty(),
                    share = id != "a" && e != null && (e.optInt("share") != 0 || e.optBoolean("share")),
                    born = e?.optLong("born")?.takeIf { it > 0 } ?: 0L,
                )
            }
        }
        take(d.optJSONArray("list"), listOf("a", "b"))
        take(p.optJSONArray("list"), listOf("c", "d", "e", "f"))
        if ("a" !in out) out["a"] = Viewer("a", "", "", pic = x?.optJSONObject("a")?.optString("pic").orEmpty().takeIf { PIC_NAME.matches(it) }.orEmpty())
        return out.values.sortedBy { it.id }
    }

    fun name(v: Viewer): String = v.name.trim().ifEmpty {
        if (v.id != "a") "Profile " + (IDS.indexOf(v.id) + 1) else Cloud.profile?.name?.takeIf { it.isNotBlank() } ?: "You"
    }
    // the first profile wears the account's colour until it is given one of its own (the picker's editor)
    fun avatar(v: Viewer): String = v.av.ifEmpty { if (v.id != "a") AV_DEF[v.id] ?: "#0A84FF" else Cloud.profile?.avatar ?: "#636366" }
    fun cur(ctx: Context): Viewer = list(ctx).firstOrNull { it.id == current } ?: list(ctx).first()

    /** A list as the wire carries it; a lock goes with its profile through every rename, add and removal. */
    internal fun listJson(l: List<Viewer>): JSONArray {
        val a = JSONArray()
        l.forEach { v ->
            val o = JSONObject().put("id", v.id).put("name", v.name.take(24)).put("av", v.av)
            v.lock?.let { o.put("lock", ProfilePin.lockJson(it)) }
            a.put(o)
        }
        return a
    }
    /** Everyone's extras as `people.x` carries them: only what is set. */
    internal fun extrasJson(l: List<Viewer>): JSONObject {
        val x = JSONObject()
        l.forEach { v ->
            val e = JSONObject()
            if (PIC_NAME.matches(v.pic)) e.put("pic", v.pic)
            if (v.share && v.id != "a") e.put("share", 1)
            if (v.born > 0) e.put("born", v.born)
            if (e.length() > 0) x.put(v.id, e)
        }
        return x
    }

    /** Write the profiles back: the first two into `viewers` (name, colour, PIN: all an older app knows), the rest and
        everyone's extras into `people`. Only a list that really changed is stamped and sent. */
    private fun setList(ctx: Context, l: List<Viewer>) {
        val was = list(ctx)
        fun basic(v: Viewer) = v.copy(name = v.name.take(24), pic = "", share = false, born = 0L)
        fun extra(v: Viewer) = Triple(v.pic.takeIf { PIC_NAME.matches(it) }.orEmpty(), v.share && v.id != "a", v.born)
        val now = System.currentTimeMillis()
        val two = l.filter { it.id <= "b" }; val more = l.filter { it.id > "b" }
        val hadTwo = doc(ctx).optJSONArray("list") != null
        if (!hadTwo || two.map(::basic) != was.filter { it.id <= "b" }.map(::basic)) {
            saveDoc(ctx, JSONObject().put("at", now).put("list", listJson(two)))
            Cloud.noteChanged(ctx, "viewers")
        }
        val extrasNow = l.associate { it.id to extra(it) }.filterValues { it.first.isNotEmpty() || it.second || it.third > 0 }
        val extrasWas = was.associate { it.id to extra(it) }.filterValues { it.first.isNotEmpty() || it.second || it.third > 0 }
        if (more.map(::basic) != was.filter { it.id > "b" }.map(::basic) || extrasNow != extrasWas) {
            savePeople(ctx, JSONObject().put("at", now).put("list", listJson(more)).put("x", extrasJson(l)))
            Cloud.noteChanged(ctx, "people")
        }
        version++
    }

    /** The PIN screen comes first: [id] is locked and was not let in on this run. */
    fun needsPin(ctx: Context, id: String): Boolean = id !in unlocked && list(ctx).firstOrNull { it.id == id }?.lock != null
    fun unlock(id: String) { unlocked += id; version++ }
    fun setPin(ctx: Context, id: String, pin: String) {
        if (!ProfilePin.pinOk(pin)) return
        unlocked += id
        setList(ctx, list(ctx).map { if (it.id == id) it.copy(lock = ProfilePin.make(pin)) else it })
    }
    fun removePin(ctx: Context, id: String) {
        unlocked += id
        setList(ctx, list(ctx).map { if (it.id == id) it.copy(lock = null) else it })
    }
    /** Back on the picker: not at launch while the profile this device is on is locked and not let in. */
    fun canLeave(ctx: Context): Boolean = !(launchGate && needsPin(ctx, current))

    /** A new profile in the lowest free slot: its id, or null when it could not be made. Signed in, the slot's old
        documents must be off the account first: a profile removed long ago, or by an older app, may have left some
        there, and a new person must not inherit them. */
    suspend fun add(ctx: Context): String? {
        val l = list(ctx)
        if (l.size >= MAX) return null
        val id = REST.firstOrNull { r -> l.none { it.id == r } } ?: return null
        if (!Cloud.dropProfile(ctx, id)) return null
        if (list(ctx).any { it.id == id }) return null            // (a sync brought one into the slot meanwhile)
        wipe(ctx, id)
        dropQueue(ctx, dropQueued(ctx) - id)
        setList(ctx, list(ctx) + Viewer(id, "", AV_DEF[id] ?: "#0A84FF", born = System.currentTimeMillis()))
        noteBorn(ctx)
        return id
    }

    /** The editor's Save. True when the profile this device is on now reads another add-on list (its own, or the first
        profile's): the caller follows with [addonsMoved]. */
    fun edit(ctx: Context, id: String, name: String, av: String, pic: String, share: Boolean): Boolean {
        val before = local("addons")
        setList(ctx, list(ctx).map { if (it.id == id) it.copy(name = name.trim().take(24), av = av, pic = pic, share = share && id != "a") else it })
        return local("addons") != before
    }
    /** The profile this device is on changed whose add-ons it uses: that list is read, here and from the account. */
    suspend fun addonsMoved(ctx: Context) {
        prepareAddons(ctx, current)
        Cloud.onApplied?.invoke(setOf("addons"))
        Cloud.pullAll(ctx, force = true)
    }

    /** Remove a profile for good: off the list, off this device, off the account. */
    suspend fun remove(ctx: Context, id: String) {
        if (id == "a" || id !in IDS) return
        // removed while on it: on to the first, whose PIN is asked first when it has one (the picker stays up)
        val here = current == id
        val gate = here && needsPin(ctx, "a")
        if (here) {
            setCurrent(ctx, "a"); Progress.reset(); Library.reset(); Track.reset()
            if (!gate) { asking = false; launchGate = false }
        }
        setList(ctx, list(ctx).filter { it.id != id })
        wipe(ctx, id)
        noteBorn(ctx)
        if (Cloud.linked(ctx)) dropQueue(ctx, dropQueued(ctx) + id)
        if (gate) { asking = true; launchGate = true }
        if (here) Cloud.onApplied?.invoke(setOf("progress", "library", "addons"))
        dropFlush(ctx)
        if (here) Cloud.pullAll(ctx, force = true)
    }

    /** A profile's own things off THIS device: its stores, its downloads, and whatever was waiting to be sent. */
    private fun wipe(ctx: Context, id: String) {
        if (id == "a" || id !in IDS) return
        val e = prefs(ctx).edit()
        listOf("progress_$id", "library_$id", "addons_$id", "addons_sync_$id", seedKey(id)).forEach { e.remove(it) }
        e.apply()
        Track.forget(ctx, id)
        Cloud.forgetProfile(ctx, id)
        runCatching { Downloads.rows.filter { it.meta.viewer == id }.forEach { Downloads.remove(ctx, it) } }
        unlocked -= id
    }

    // which profile each slot held when this device last looked (`born`; 0 for one from before 1.114.0)
    private fun bornMap(ctx: Context): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString("who_born", "{}") ?: "{}") }.getOrDefault(JSONObject())
    private fun noteBorn(ctx: Context) {
        val m = JSONObject(); list(ctx).forEach { if (it.id != "a") m.put(it.id, it.born) }
        prefs(ctx).edit().putString("who_born", m.toString()).apply()
    }
    /** What a list means for a device that knew [known] (slot → born): the slots to wipe. Pure, for the tests. A slot
        this device never saw filled is left alone (an older app kept a removed second profile's history on purpose). */
    internal fun stale(known: Map<String, Long>, now: List<Viewer>): Set<String> {
        val have = now.associateBy { it.id }
        return REST.filter { id -> id in known && (have[id] == null || have[id]!!.born != known[id]) }.toSet()
    }
    /** The list as it now stands against what this device knew: a profile that is gone, or that is another one in the
        same slot now, leaves nothing behind here. True when this device was on a profile that is gone and has been
        moved to the first. */
    private fun reconcile(ctx: Context): Boolean {
        val m = bornMap(ctx)
        val known = HashMap<String, Long>(); m.keys().forEach { k -> known[k] = m.optLong(k) }
        val l = list(ctx)
        var moved = false; var fresh = false
        for (id in stale(known, l)) {
            if (l.none { it.id == id }) {
                if (current == id) { setCurrent(ctx, "a"); moved = true }
            } else if (current == id) fresh = true
            wipe(ctx, id)
        }
        noteBorn(ctx)
        if (moved || fresh) {
            Progress.reset(); Library.reset(); Track.reset()
            Cloud.onApplied?.invoke(setOf("progress", "library", "addons"))
        }
        return moved
    }

    // removals the account has not heard of yet (made offline): asked again on the next sync
    private fun dropQueued(ctx: Context): Set<String> =
        runCatching { JSONArray(prefs(ctx).getString("who_drop", "[]") ?: "[]").let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in REST }.toSet() } }
            .getOrDefault(emptySet())
    private fun dropQueue(ctx: Context, ids: Set<String>) {
        if (ids.isEmpty()) prefs(ctx).edit().remove("who_drop").apply()
        else prefs(ctx).edit().putString("who_drop", JSONArray(ids.sorted()).toString()).apply()
    }
    /** A slot that holds a profile again is left alone: whoever made that one cleared the slot first. */
    internal suspend fun dropFlush(ctx: Context) {
        if (!Cloud.linked(ctx)) return
        val q = dropQueued(ctx).filter { id -> list(ctx).none { it.id == id } }.toSet()
        dropQueue(ctx, q)
        for (id in q) if (Cloud.dropProfile(ctx, id)) dropQueue(ctx, dropQueued(ctx) - id)
    }

    private fun setCurrent(ctx: Context, id: String) {
        current = id; prefs(ctx).edit().putString("viewer", id).apply()
        WatchNext.poke(ctx, soon = true)                  // the launcher's row is the other profile's now (TV)
    }

    /** On to another profile: what this one changed goes up under its own names first, then the other's history and
        add-ons are read (this device's copy at once, the cloud's after a pull) and every screen that shows them redraws. */
    suspend fun switchTo(ctx: Context, id: String, keepAsking: Boolean = false) {
        val to = if (list(ctx).any { it.id == id }) id else "a"
        if (!keepAsking) { asking = false; launchGate = false }
        if (to == current) return
        Cloud.flushProfile(ctx)
        prepareAddons(ctx, to)                                 // before the pull below, and before the list is read
        setCurrent(ctx, to)
        Progress.reset()
        Library.reset()
        Track.reset()
        // this device's copy at once (Home, Search, the Guide, the add-ons screen re-read the list); the pull's own
        // changes redraw again when they land
        Cloud.onApplied?.invoke(setOf("progress", "library", "addons"))
        Cloud.pullAll(ctx, force = true)
    }

    fun answered() { asking = false; launchGate = false }

    /** Settings › Who's watching: the same picker, to switch, add or edit (Back keeps the profile this device is on). */
    fun open() { asking = true; launchGate = false }

    /** A list of profiles arrived from the account: what it removed or replaced is cleared here, the picker follows. */
    private fun arrived(ctx: Context, addonsWas: String) {
        val was = current
        val moved = reconcile(ctx)
        // a lock that came from elsewhere throws no one out; only a device sent to a locked first profile, or still at
        // the launch picker for it, is asked its PIN
        if (moved) { val hold = needsPin(ctx, "a"); asking = hold; launchGate = hold }
        else if (list(ctx).size < 2) { val hold = launchGate && needsPin(ctx, "a"); asking = hold; launchGate = hold }
        if (!moved && current == was && local("addons") != addonsWas) scope.launch { addonsMoved(ctx.applicationContext) }
    }

    /** A synced `viewers` (Cloud.merge): newest wins; a profile removed elsewhere sends a device on it back to the first. */
    internal fun merge(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        val mine = doc(ctx)
        val ra = remote.optLong("at"); val la = mine.optLong("at")
        if (remote.optJSONArray("list") == null) return false to (la > 0)
        if (ra > la) {
            val k0 = local("addons")
            saveDoc(ctx, JSONObject().put("at", ra).put("list", remote.getJSONArray("list")))
            arrived(ctx, k0)
            return true to false
        }
        return false to (la > ra)
    }

    /** A synced `people`: the same rule. */
    internal fun mergePeople(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        val mine = people(ctx)
        val ra = remote.optLong("at"); val la = mine.optLong("at")
        if (ra > la) {
            val k0 = local("addons")
            val d = JSONObject().put("at", ra).put("list", remote.optJSONArray("list") ?: JSONArray())
            remote.optJSONObject("x")?.let { d.put("x", it) }
            savePeople(ctx, d)
            arrived(ctx, k0)
            return true to false
        }
        return false to (la > ra)
    }
}

/**
 * Who's watching? (10-16: the layout of the reference screenshots the Founder sent — the mark and the question at the
 * top, big round circles with their names under them, "Manage Profiles" below; managing puts a pencil on each, adds an
 * "Add Profile" circle while there is room for one (six profiles), and the button reads Done.) At launch with several,
 * and from Settings › Who's watching. Back leaves managing, then keeps the profile this device was on — except at
 * launch while that profile is locked: then the picker stays. A locked profile asks its PIN before it is entered or
 * edited (ProfilePin.kt).
 */
@Composable
internal fun WhoPicker() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = remember(Viewers.version) { Viewers.list(ctx) }
    var managing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf<PinAsk?>(null) }
    val draft = remember(editing) { WhoDraft() }                 // the editor's unsaved name, picture and colour, kept under a PIN step
    var adding by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val tv = Account.isTv(ctx)
    androidx.activity.compose.BackHandler {
        when {
            editing != null -> editing = null
            managing -> managing = false
            Viewers.canLeave(ctx) -> Viewers.answered()
            else -> {}                                           // at launch, locked: the picker stays
        }
    }
    Box(Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}) {
        val ask = pin
        if (ask != null) {
            PinScreen(
                ask, onAsk = { pin = it },
                onPassed = {
                    pin = null
                    when (ask.purpose) {
                        PinFor.ENTER -> scope.launch { Viewers.switchTo(ctx, ask.id) }
                        PinFor.EDIT -> editing = ask.id
                        PinFor.SET -> {}                                  // back to the editor
                    }
                },
                onCancel = { pin = null },
            )
            return@Box
        }
        val ed = editing
        if (ed != null) {
            WhoEditor(ed, draft, onPin = { pin = PinAsk(ed, PinMode.CHOOSE, PinFor.SET) }, onDone = { editing = null })
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
            // a phone: two to a row, the Add circle wrapping under them while managing (as the reference), and three
            // smaller ones to a row once there are more than four, so six and the button under them fit one screen;
            // a TV: one line, the circles smaller past four
            val tiles: List<String> = l.map { it.id } + (if (managing && l.size < Viewers.MAX) listOf("+") else emptyList())
            val many = tiles.size > 4
            val wide = LocalConfiguration.current.screenWidthDp.dp
            val size = when {
                tv -> if (many) 100.dp else 132.dp
                many -> ((wide - 48.dp) / 3 - 30.dp).coerceIn(64.dp, 110.dp)
                else -> 150.dp
            }
            Column(
                Modifier.padding(top = if (tv) 40.dp else if (many) 44.dp else 64.dp), verticalArrangement = Arrangement.spacedBy(if (tv) 36.dp else 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                tiles.chunked(if (tv) 7 else if (many) 3 else 2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (tv) (if (many) 24.dp else 56.dp) else if (many) 8.dp else 28.dp)) {
                        pair.forEach { id ->
                            val v = l.firstOrNull { it.id == id }
                            WhoTile(
                                v = v, managing = managing, current = v?.id == Viewers.current, size = size,
                                modifier = if (v?.id == Viewers.current) Modifier.focusRequester(first) else Modifier,
                            ) {
                                when {
                                    v == null -> if (!adding) {
                                        adding = true
                                        scope.launch {
                                            val made = Viewers.add(ctx)
                                            adding = false
                                            if (made == null) Toasts.show("Could not reach Nebula to add a profile. Try again.") else editing = made
                                        }
                                    }
                                    Viewers.needsPin(ctx, v.id) ->
                                        pin = PinAsk(v.id, PinMode.CHECK, if (managing) PinFor.EDIT else PinFor.ENTER)
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
    LaunchedEffect(editing, pin) { if (editing == null && pin == null) runCatching { withFrameNanos {}; first.requestFocus() } }
}

/** One profile's circle and name (or, with v null, the Add Profile circle). */
@Composable
private fun WhoTile(v: Viewers.Viewer?, managing: Boolean, current: Boolean, size: Dp, modifier: Modifier, onClick: () -> Unit) {
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
                } else WhoMonogram(Viewers.avatar(v), Viewers.name(v), size, v.pic)
            }
            if (managing && v != null) Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = if (size < 120.dp) 4.dp else 10.dp).size(if (size < 120.dp) 36.dp else 44.dp)
                    .border(2.dp, Bg, CircleShape).background(Color(0xFFF2F2F7), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.Black, modifier = Modifier.size(if (size < 120.dp) 17.dp else 20.dp)) }
        }
        Text(
            if (v == null) "Add Profile" else Viewers.name(v),
            color = if (v == null) MutedC else TextC, fontSize = if (size < 120.dp) 16.sp else 18.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp),
        )
        // a PIN on it: seen before pressing
        if (v?.lock != null) Icon(Icons.Filled.Lock, contentDescription = "PIN", tint = MutedC, modifier = Modifier.padding(top = 6.dp).size(16.dp))
    }
}

/** The big circle: the profile's colour as a deep tint, a ring of it, and in it the profile's picture, else its letter. */
@Composable
internal fun WhoMonogram(hex: String, name: String, size: Dp, pic: String = "") {
    val c = avatarColor(hex)
    Box(
        Modifier.size(size).background(c.copy(alpha = 0.2f).compositeOver(Bg), CircleShape).border(2.dp, c.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().removePrefix("@").take(1).uppercase()
        if (WhoPics.find(pic) != null) WhoGlyph(pic, c, size * 0.56f)
        else if (initial.isEmpty()) Icon(Icons.Filled.Person, contentDescription = null, tint = c, modifier = Modifier.size(size * 0.45f))
        else Text(initial, color = c, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold, fontFamily = Sans)
    }
}

/** A pencil pressed: the profile's name, picture and colour, whose add-ons it uses, its PIN; any but the first can be
    removed, and everything of it goes with it (asked twice). */
@Composable
internal fun WhoEditor(id: String, held: WhoDraft, onPin: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all = remember(Viewers.version) { Viewers.list(ctx) }
    val v = all.firstOrNull { it.id == id }
    if (v == null) { LaunchedEffect(Unit) { onDone() }; return }          // (removed on another device meanwhile)
    val draft = held.name ?: v.name
    val av = held.av ?: Viewers.avatar(v)
    val pic = held.pic ?: v.pic
    val share = held.share ?: v.share
    val tv = Account.isTv(ctx)
    val typing = tvTyping()
    var armed by remember { mutableStateOf(false) }                       // Remove pressed once: the second press does it
    val save = {
        val moved = Viewers.edit(ctx, id, draft, av, pic, share)
        if (moved) scope.launch { Viewers.addonsMoved(ctx) }
        typing.done(); onDone()
    }
    val shown = draft.ifBlank { Viewers.name(v.copy(name = "")) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = if (tv) 32.dp else 72.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Edit profile", color = TextC, fontSize = 28.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        Box(Modifier.padding(top = if (tv) 20.dp else 28.dp)) {
            WhoMonogram(av, shown, if (tv) 96.dp else 120.dp, pic)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { held.name = it.take(24) },
            placeholder = { Text(Viewers.name(v.copy(name = "")), color = MutedC) },
            singleLine = true,
            readOnly = typing.readOnly,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = typing.modifier.widthIn(max = 360.dp).fillMaxWidth().padding(top = if (tv) 18.dp else 24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                focusedTextColor = TextC, unfocusedTextColor = TextC,
            ),
        )
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Account.AVATARS.forEach { hex ->
                val on = hex.equals(av, ignoreCase = true)
                Box(
                    Modifier.size(32.dp)
                        .border(2.dp, if (on) Color.White else Color.Transparent, CircleShape).padding(4.dp)
                        .background(avatarColor(hex), CircleShape)
                        .focusRing(CircleShape, landing = false)
                        .clickable { held.av = hex },
                )
            }
        }
        // the pictures: Nebula's own set; the first choice is the plain letter
        val choices: List<WhoPics.Pic?> = listOf<WhoPics.Pic?>(null) + WhoPics.ALL
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            choices.chunked(if (tv) 13 else 7).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { p ->
                        val on = (p?.id ?: "") == pic
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (on) Color(0xFFF2F2F7) else SurfaceC, CircleShape)
                                .border(1.5.dp, if (on) Color(0xFFF2F2F7) else LineC, CircleShape)
                                .focusRing(CircleShape, landing = false)
                                .clickable { held.pic = p?.id ?: "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            val ink = if (on) Color.Black else MutedC
                            if (p == null) Text(shown.trim().removePrefix("@").take(1).uppercase().ifEmpty { "A" }, color = ink, fontSize = 15.sp, fontFamily = Sans, fontWeight = FontWeight.Bold)
                            else WhoGlyph(p.id, ink, 20.dp)
                        }
                    }
                }
            }
        }
        val pill = RoundedCornerShape(50)
        // whose add-ons: one row that flips between the two answers (never the first profile's row)
        if (id != "a") {
            val box = RoundedCornerShape(14.dp)
            Row(
                Modifier.padding(top = 18.dp).widthIn(max = 360.dp).fillMaxWidth().clip(box).background(SurfaceC, box).border(1.dp, LineC, box)
                    .focusRing(box, landing = false).clickable { held.share = !share }
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Add-ons", color = MutedC, fontSize = 15.sp, fontFamily = Sans)
                Text(
                    if (share) "The same as " + Viewers.name(all.first()) else "Its own list",
                    color = TextC, fontSize = 15.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
        // the PIN: add one, or change / remove it (this profile was let in to get here)
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(pill).border(1.dp, Line2, pill).focusRing(pill, landing = false).clickable { onPin() }
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            ) { Text(if (v.lock == null) "Add a PIN" else "Change PIN", color = TextC, fontSize = 16.sp, fontFamily = Sans, fontWeight = FontWeight.Medium) }
            if (v.lock != null) Box(
                Modifier.clip(pill).border(1.dp, Line2, pill).focusRing(pill, landing = false)
                    .clickable { Viewers.removePin(ctx, id); Toasts.show("PIN removed.") }
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            ) { Text("Remove PIN", color = TextC, fontSize = 16.sp, fontFamily = Sans, fontWeight = FontWeight.Medium) }
            Box(
                Modifier.clip(pill).background(Color(0xFFF2F2F7), pill)
                    .focusRing(pill, landing = false).clickable { save() }
                    .padding(horizontal = 34.dp, vertical = 12.dp),
            ) { Text("Save", color = Color.Black, fontSize = 17.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold) }
        }
        // removing is for good, so it is asked twice: the words say what will go, a second press does it
        if (id != "a") Text(
            if (armed) "Remove " + Viewers.name(v) + " for good" else "Remove profile",
            color = Color(0xFFFF453A), fontSize = 16.sp, fontFamily = Sans, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 14.dp).clip(pill).focusRing(pill, landing = false)
                .clickable {
                    if (!armed) armed = true
                    else {
                        val name = Viewers.name(v)
                        scope.launch { Viewers.remove(ctx, id); Toasts.show("Removed $name."); onDone() }
                    }
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
        Text(
            if (armed) "Its Continue Watching, My List, add-ons and downloads are deleted from every device. This cannot be undone."
            else "Each profile keeps its own Continue Watching, resume points, My List and add-ons. Settings are this device's.",
            color = MutedC, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp).padding(top = 18.dp),
        )
    }
}

/** Settings: one row that opens the picker (switch, add, edit — all there now). */
@Composable
internal fun WhoSettings() {
    val ctx = LocalContext.current
    val l = remember(Viewers.version, Viewers.current) { Viewers.list(ctx) }
    SettingsHeader("WHO'S WATCHING", "Each profile keeps its own Continue Watching, resume points, My List and add-ons")
    SettingsGroup {
        SettingsRow(
            Icons.Filled.People, "Who's watching",
            if (l.size < 2) "Add up to six profiles for the people in the house" else "Watching as ${Viewers.name(Viewers.cur(ctx))} · ${l.size} profiles · switch or edit",
            l.size >= 2,
        ) { Viewers.open() }
        if (l.size >= 2) SettingsToggle(
            "Ask who's watching when Nebula opens", "Off: straight to the profile this device was last on · A profile with a PIN always asks",
            Viewers.askAtLaunch(ctx), divider = false,
        ) { Viewers.setAskAtLaunch(ctx, it) }
    }
}
