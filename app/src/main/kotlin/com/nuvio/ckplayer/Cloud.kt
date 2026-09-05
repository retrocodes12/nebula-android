package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Nebula Cloud client — sync plus the Nebula Profile that carries it.
 *
 * A profile is an @handle and a password, nothing else: no email, no tracking.
 * Every device that signs in gets a token of its own (revocable one by one from
 * any signed-in device), and add-ons, watch progress, My List, ratings and the
 * subtitle style flow between them, newest change winning per record.
 *
 * The WIRE FORMAT IS THE WEB PLAYER'S, verbatim — that is the whole point
 * (one profile spans Android, web, desktop and TV). Two impedance mismatches are
 * handled here and only here:
 *   - positions/durations travel in SECONDS (web-style); Android stores ms.
 *   - the add-on list carries no timestamps locally, so this layer keeps its
 *     own added/removed stamps (prefs "addons_sync"), exactly like the web's.
 *
 * Installs from before profiles hold a group master secret from a link code;
 * [Account.boot] trades it for a device token and the device shows as "legacy"
 * until a profile is added to the group. The profile actions themselves
 * (sign in, create, recover, devices, TV codes) live in [Account].
 */
object Cloud {
    private const val BASE = "https://play.rifflehq.in/cloud"
    private const val PREFS = "ckplayer"
    private val SYNC_KEYS = listOf("addons", "progress", "library", "sub_style", "ratings")
    private val JSON_MT = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushJobs = HashMap<String, Job>()
    private var lastPullAt = 0L

    /** Called (on main) after a pull changed local state, with the keys that changed. */
    @Volatile var onApplied: ((Set<String>) -> Unit)? = null
    /** Called (on main) when a signed request came back 401 — the token was revoked elsewhere. */
    @Volatile var onSignedOut: (() -> Unit)? = null

    /** The profile this device is signed in to, if any (Compose state). */
    var profile by mutableStateOf<Profile?>(null); private set
    /** Devices on the profile, from the last [Account.refreshProfile]. */
    var devices by mutableStateOf<List<DeviceRec>>(emptyList()); internal set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun gid(ctx: Context) = prefs(ctx).getString("cloud_gid", null)
    private fun secret(ctx: Context) = prefs(ctx).getString("cloud_secret", null)
    private fun token(ctx: Context) = prefs(ctx).getString("cloud_token", null)
    internal fun hasToken(ctx: Context) = !token(ctx).isNullOrEmpty()
    /** A device token replaces the master secret for good. */
    internal fun setToken(ctx: Context, t: String) = prefs(ctx).edit().putString("cloud_token", t).remove("cloud_secret").apply()
    fun linked(ctx: Context) = !gid(ctx).isNullOrEmpty() && (!token(ctx).isNullOrEmpty() || !secret(ctx).isNullOrEmpty())
    /** "out" (nothing), "in" (a profile), or "legacy" (a link-code group with no profile yet). */
    fun state(ctx: Context): String = if (!linked(ctx)) "out" else if (profile != null) "in" else "legacy"

    fun load(ctx: Context) {
        profile = parseProfile(runCatching { JSONObject(prefs(ctx).getString("profile", "") ?: "") }.getOrNull())
    }
    private fun parseProfile(o: JSONObject?): Profile? {
        val h = o?.optString("handle") ?: return null
        if (h.isEmpty()) return null
        // /v1/profile/me carries `supporter: {since, wall} | null`; creds replies, a PUT reply
        // and the copy stored below carry the flattened `sup` / `supSince` / `wall`.
        val sup = o.optJSONObject("supporter")
        return Profile(
            h, o.optString("name").ifEmpty { h }, o.optString("avatar").ifEmpty { Account.AVATARS[0] },
            sup = o.optBoolean("sup") || sup != null,
            supSince = if (sup != null) sup.optLong("since") else o.optLong("supSince"),
            wall = if (sup != null) sup.optBoolean("wall") else o.optBoolean("wall"),
        )
    }
    /** Accepts any server object carrying handle/name/avatar (a creds reply, /me, a PUT reply). */
    internal fun setProfile(ctx: Context, o: JSONObject?) {
        var p = parseProfile(o)
        // A creds or PUT /v1/profile reply says `sup: true` and nothing more, so keep the date
        // and the wall choice the last /v1/profile/me brought rather than blanking them.
        val prev = profile
        if (p != null && p.sup && p.supSince == 0L && prev != null && prev.handle == p.handle && prev.supSince > 0L) {
            p = p.copy(supSince = prev.supSince, wall = prev.wall)
        }
        profile = p
        storeProfile(ctx, p)
    }

    /** Patch the supporter fields from a `{since, wall}` reply (redeem, wall on/off). */
    internal fun noteSupporter(ctx: Context, s: JSONObject?) {
        val p = profile ?: return
        val next = if (s == null) p.copy(sup = false, supSince = 0L, wall = false)
        else p.copy(sup = true, supSince = s.optLong("since", p.supSince), wall = s.optBoolean("wall"))
        if (next == p) return
        profile = next
        storeProfile(ctx, next)
    }

    private fun storeProfile(ctx: Context, p: Profile?) {
        val json = if (p == null) "" else JSONObject()
            .put("handle", p.handle).put("name", p.name).put("avatar", p.avatar)
            .put("sup", p.sup).put("supSince", p.supSince).put("wall", p.wall)
            .toString()
        prefs(ctx).edit().putString("profile", json).apply()
    }

    private fun jsonPref(ctx: Context, key: String): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
    private fun putJsonPref(ctx: Context, key: String, o: JSONObject) =
        prefs(ctx).edit().putString(key, o.toString()).apply()

    // ---------- HTTP ----------
    class HttpFail(val code: Int, val error: String) : RuntimeException("HTTP $code $error")
    /** One call to the cloud. `auth = false` is for the public profile routes (sign-in, TV code). */
    internal suspend fun api(ctx: Context, method: String, path: String, body: JSONObject?, auth: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(BASE + path)
            val signed = auth && linked(ctx)
            if (signed) b.header("Authorization", "Bearer ${gid(ctx)}.${token(ctx) ?: secret(ctx)}")
            val payload = (body ?: JSONObject()).toString().toRequestBody(JSON_MT)
            when (method) {
                "POST" -> b.post(payload)
                "PUT" -> b.put(payload)
                "DELETE" -> b.delete(payload)
                else -> b.get()
            }
            http.newCall(b.build()).execute().use { r ->
                val text = r.body?.string() ?: "{}"
                if (!r.isSuccessful) {
                    val err = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                    // a dead credential: the device was signed out from elsewhere, or the profile is gone
                    if (r.code == 401 && signed) credentialDead(ctx)
                    throw HttpFail(r.code, err)
                }
                JSONObject(text)
            }
        }

    /** Take a credential set {gid, token, profile} as this device's identity. */
    internal fun adopt(ctx: Context, r: JSONObject, fresh: Boolean) {
        prefs(ctx).edit()
            .putString("cloud_gid", r.getString("gid"))
            .putString("cloud_token", r.getString("token"))
            .remove("cloud_secret")
            .putString("cloud_revs", "{}")
            .putString("cloud_dirty", "{}")
            .apply()
        setProfile(ctx, r.optJSONObject("profile"))
        devices = emptyList()
        Social.reset(ctx)                       // Friends state belongs to the identity, not the device
        lastPullAt = 0
        val app = ctx.applicationContext
        if (fresh) {
            // a brand-new profile: what this device holds IS the profile's data
            SYNC_KEYS.forEach { k -> if (hasContent(app, k)) scope.launch { pushKey(app, k) } }
        } else {
            // merge; newer local records push back on their own
            scope.launch { pullAll(app, force = true); Social.refresh(app) }
        }
    }

    /** Forget the credential on this device only; nothing local is deleted. */
    fun forget(ctx: Context) {
        prefs(ctx).edit()
            .remove("cloud_gid").remove("cloud_secret").remove("cloud_token").remove("profile")
            .putString("cloud_revs", "{}").putString("cloud_dirty", "{}")
            .apply()
        profile = null
        devices = emptyList()
        Social.reset(ctx)
    }

    private suspend fun credentialDead(ctx: Context) {
        if (!linked(ctx)) return
        forget(ctx)
        withContext(Dispatchers.Main) { onSignedOut?.invoke() }
    }

    // ---------- push ----------
    /** Mark a key changed and schedule a debounced push. Safe to call constantly. */
    fun noteChanged(ctx: Context, key: String) {
        if (!linked(ctx) || applying) return
        val d = jsonPref(ctx, "cloud_dirty").put(key, 1)
        putJsonPref(ctx, "cloud_dirty", d)
        val app = ctx.applicationContext
        pushJobs[key]?.cancel()
        pushJobs[key] = scope.launch {
            delay(if (key == "progress") 20_000 else 2_000)   // progress churns every 5s
            pushKey(app, key)
        }
    }

    /** Push every dirty key right now — the app is going to background. */
    fun flushNow(ctx: Context) {
        if (!linked(ctx)) return
        val app = ctx.applicationContext
        val d = jsonPref(ctx, "cloud_dirty")
        for (k in d.keys()) {
            pushJobs[k]?.cancel()
            scope.launch { pushKey(app, k) }
        }
    }

    /** Push every dirty doc now and wait — for the moment before this device lets go of its credential. */
    suspend fun flushAndWait(ctx: Context) {
        if (!linked(ctx)) return
        val app = ctx.applicationContext
        val d = jsonPref(ctx, "cloud_dirty")
        for (k in d.keys()) { pushJobs[k]?.cancel(); pushKey(app, k) }
    }

    private suspend fun pushKey(ctx: Context, key: String) {
        if (!linked(ctx)) return
        val v = docFor(ctx, key) ?: return
        runCatching {
            val r = api(ctx, "PUT", "/v1/kv/$key", JSONObject().put("v", v))
            putJsonPref(ctx, "cloud_revs", jsonPref(ctx, "cloud_revs").put(key, r.getInt("rev")))
            val d = jsonPref(ctx, "cloud_dirty"); d.remove(key)
            putJsonPref(ctx, "cloud_dirty", d)
        }
    }

    private fun hasContent(ctx: Context, key: String): Boolean = when (key) {
        "addons" -> loadAddons(ctx).isNotEmpty()
        "progress" -> Progress.all(ctx).isNotEmpty()
        "library" -> Library.all(ctx).length() > 0
        "ratings" -> Ratings.all(ctx).length() > 0
        "sub_style" -> SubStyle.at(ctx) > 0
        else -> false
    }

    // ---------- pull + merge ----------
    @Volatile private var applying = false

    suspend fun pullAll(ctx: Context, force: Boolean = false) {
        if (!linked(ctx)) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPullAt < 45_000) return
        lastPullAt = now
        val applied = HashSet<String>()
        runCatching {
            val keys = api(ctx, "GET", "/v1/kv", null).optJSONObject("keys") ?: JSONObject()
            val revs = jsonPref(ctx, "cloud_revs")
            for (key in SYNC_KEYS) {
                val meta = keys.optJSONObject(key)
                if (meta == null) {
                    if (hasContent(ctx, key)) pushKey(ctx, key)
                    continue
                }
                if (revs.optInt(key, -1) == meta.optInt("rev")) {
                    if (jsonPref(ctx, "cloud_dirty").has(key)) pushKey(ctx, key)
                    continue
                }
                val rec = runCatching { api(ctx, "GET", "/v1/kv/$key", null) }.getOrNull() ?: continue
                val remote = runCatching { JSONObject(rec.getString("v")) }.getOrNull() ?: continue
                applying = true
                val (changed, localNewer) = try { merge(ctx, key, remote) } finally { applying = false }
                putJsonPref(ctx, "cloud_revs", jsonPref(ctx, "cloud_revs").put(key, rec.optInt("rev")))
                if (changed) applied.add(key)
                if (localNewer) pushKey(ctx, key)
            }
        }
        if (applied.isNotEmpty()) {
            withContext(Dispatchers.Main) { onApplied?.invoke(applied) }
        }
    }

    private fun merge(ctx: Context, key: String, remote: JSONObject): Pair<Boolean, Boolean> = when (key) {
        "addons" -> mergeAddons(ctx, remote)
        "progress" -> mergeProgress(ctx, remote)
        "library" -> mergeLibrary(ctx, remote)
        "ratings" -> mergeRatings(ctx, remote)
        "sub_style" -> mergeSubStyle(ctx, remote)
        else -> false to false
    }

    // ---------- wire docs ----------
    private fun docFor(ctx: Context, key: String): String? = when (key) {
        "addons" -> {
            val s = addonsSync(ctx)
            val at = s.optJSONObject("at") ?: JSONObject()
            var stamped = false
            val list = JSONObject()
            loadAddons(ctx).forEach { a ->
                // add-ons from before sync existed carry no stamp — stamp them now,
                // or the receiving side's newest-wins merge can never adopt them
                if (at.optLong(a.manifestUrl) == 0L) { at.put(a.manifestUrl, System.currentTimeMillis()); stamped = true }
                list.put(a.manifestUrl, JSONObject()
                    .put("name", a.name).put("base", a.base)
                    .put("logo", a.logo ?: "").put("at", at.optLong(a.manifestUrl)))
            }
            if (stamped) { s.put("at", at); putJsonPref(ctx, "addons_sync", s) }
            // The list is keyed by URL and so carries no order of its own — rank
            // travels separately, with its own stamp.
            val order = JSONArray()
            loadAddons(ctx).forEach { order.put(it.manifestUrl) }
            JSONObject().put("list", list).put("removed", s.optJSONObject("removed") ?: JSONObject())
                .put("order", order).put("orderAt", s.optLong("orderAt")).toString()
        }
        "progress" -> {
            val o = JSONObject()
            Progress.all(ctx).forEach { (k, r) ->
                val w = JSONObject().put("type", r.type).put("id", r.id).put("at", r.at)
                when {
                    r.done -> w.put("done", true)
                    r.dismissed -> w.put("dismissed", true)
                    else -> w.put("name", r.name).put("poster", r.poster ?: "").put("shape", r.shape)
                        .put("addonUrl", r.addonUrl)
                        .put("pos", r.pos / 1000.0).put("dur", r.dur / 1000.0)   // wire = seconds
                }
                o.put(k, w)
            }
            o.toString()
        }
        "library" -> Library.all(ctx).toString()
        "ratings" -> Ratings.all(ctx).toString()
        "sub_style" -> {
            val style = JSONObject()
            SubStyle.get(ctx).forEach { (k, v) -> style.put(k, v) }
            JSONObject().put("style", style).put("at", SubStyle.at(ctx)).toString()
        }
        else -> null
    }

    internal fun addonsSync(ctx: Context): JSONObject {
        val s = jsonPref(ctx, "addons_sync")
        if (!s.has("at")) s.put("at", JSONObject())
        if (!s.has("removed")) s.put("removed", JSONObject())
        return s
    }

    /** Seeded defaults must never beat a real removal in sync — stamp the epoch. */
    fun stampSeed(ctx: Context, url: String) {
        val s = addonsSync(ctx)
        s.getJSONObject("at").put(url, 1L)
        prefs(ctx).edit().putString("addons_sync", s.toString()).apply()
    }

    /** Stamp a deliberate reorder so the newest ranking wins across devices. */
    fun noteAddonOrder(ctx: Context) {
        val s = addonsSync(ctx)
        s.put("orderAt", System.currentTimeMillis())
        putJsonPref(ctx, "addons_sync", s)
    }

    /** Diff hook run by saveAddons: stamp additions, tombstone removals. */
    fun noteAddonsDiff(ctx: Context, prev: List<Addon>, next: List<Addon>) {
        val s = addonsSync(ctx)
        val at = s.getJSONObject("at")
        val removed = s.getJSONObject("removed")
        val now = System.currentTimeMillis()
        val pv = prev.map { it.manifestUrl }.toSet()
        val nx = next.map { it.manifestUrl }.toSet()
        next.forEach { if (it.manifestUrl !in pv) { at.put(it.manifestUrl, now); removed.remove(it.manifestUrl) } }
        prev.forEach { if (it.manifestUrl !in nx) { removed.put(it.manifestUrl, now); at.remove(it.manifestUrl) } }
        putJsonPref(ctx, "addons_sync", s)
    }

    private fun mergeAddons(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        val s = addonsSync(ctx)
        val at = s.getJSONObject("at")
        val removed = s.getJSONObject("removed")
        val arr = loadAddons(ctx).toMutableList()
        var changed = false
        var localNewer = false
        val rl = remote.optJSONObject("list") ?: JSONObject()
        val rr = remote.optJSONObject("removed") ?: JSONObject()
        val have = arr.map { it.manifestUrl }.toHashSet()
        for (u in rl.keys()) {
            val r = rl.getJSONObject(u)
            if (u in have) {
                if (at.optLong(u) > r.optLong("at")) localNewer = true
                else if (r.optLong("at") > at.optLong(u)) at.put(u, r.optLong("at"))
                continue
            }
            // adopt unless WE removed it more recently than they added it
            if (!removed.has(u) || r.optLong("at") > removed.optLong(u)) {
                arr.add(Addon(u, r.optString("name", "Add-on"),
                    r.optString("base").ifEmpty { Stremio.baseOf(u) },
                    r.optString("logo").ifEmpty { null }))
                at.put(u, if (r.optLong("at") > 0) r.optLong("at") else System.currentTimeMillis())
                removed.remove(u)
                changed = true
            } else localNewer = true
        }
        for (u in rr.keys()) {
            val idx = arr.indexOfFirst { it.manifestUrl == u }
            if (idx >= 0) {
                if (rr.optLong(u) > at.optLong(u)) {
                    arr.removeAt(idx); removed.put(u, rr.optLong(u)); at.remove(u)
                    changed = true
                } else localNewer = true
            } else if (removed.optLong(u) > rr.optLong(u)) localNewer = true
        }
        arr.forEach { if (!rl.has(it.manifestUrl)) localNewer = true }
        for (u in removed.keys()) if (!rr.has(u)) localNewer = true
        // Ranking, newest-wins. Add-ons the sender didn't know about keep their
        // relative position at the end rather than being dropped or shuffled.
        val ro = remote.optJSONArray("order")
        val roAt = remote.optLong("orderAt")
        if (roAt > s.optLong("orderAt") && ro != null && ro.length() > 0) {
            val rank = HashMap<String, Int>()
            for (i in 0 until ro.length()) rank[ro.optString(i)] = i
            val next = arr.filter { rank.containsKey(it.manifestUrl) }.sortedBy { rank[it.manifestUrl] } +
                arr.filter { !rank.containsKey(it.manifestUrl) }
            s.put("orderAt", roAt)
            if (next != arr.toList()) { arr.clear(); arr.addAll(next); changed = true }
        } else if (s.optLong("orderAt") > roAt) localNewer = true
        putJsonPref(ctx, "addons_sync", s)
        if (changed) saveAddonsRaw(ctx, arr)
        return changed to localNewer
    }

    private fun mergeProgress(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        val local = HashMap(Progress.all(ctx))
        var changed = false
        var localNewer = false
        for (k in remote.keys()) {
            val r = remote.optJSONObject(k) ?: continue
            val rAt = r.optLong("at")
            val l = local[k]
            if (l == null || rAt > l.at) {
                local[k] = ProgressRec(
                    type = r.optString("type"), id = r.optString("id"),
                    name = r.optString("name"),
                    poster = r.optString("poster").ifEmpty { null },
                    shape = r.optString("shape", "poster"),
                    addonUrl = r.optString("addonUrl"),
                    pos = (r.optDouble("pos", 0.0) * 1000).toLong(),   // wire = seconds
                    dur = (r.optDouble("dur", 0.0) * 1000).toLong(),
                    done = r.optBoolean("done"),
                    dismissed = r.optBoolean("dismissed"),
                    at = rAt,
                )
                changed = true
            }
        }
        local.forEach { (k, l) ->
            val r = remote.optJSONObject(k)
            if (r == null || l.at > r.optLong("at")) localNewer = true
        }
        if (changed) Progress.replaceAll(ctx, local)
        return changed to localNewer
    }

    private fun mergeSubStyle(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        val rAt = remote.optLong("at")
        val lAt = SubStyle.at(ctx)
        val style = remote.optJSONObject("style")
        // a malformed doc must not wedge the key: the rev gets recorded either
        // way, so push our good copy over it rather than silently skipping
        if (style == null) return false to (lAt > 0)
        if (rAt > lAt) {
            SubStyle.applyRemote(ctx, style, rAt)
            return true to false
        }
        return false to (lAt > rAt)
    }

    private fun mergeLibrary(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        val local = Library.all(ctx)
        var changed = false
        var localNewer = false
        for (k in remote.keys()) {
            val r = remote.optJSONObject(k) ?: continue
            val l = local.optJSONObject(k)
            if (l == null || r.optLong("at") > l.optLong("at")) { local.put(k, r); changed = true }
        }
        for (k in local.keys()) {
            val r = remote.optJSONObject(k)
            if (r == null || local.getJSONObject(k).optLong("at") > r.optLong("at")) localNewer = true
        }
        if (changed) Library.replaceAll(ctx, local)
        return changed to localNewer
    }

    private fun mergeRatings(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        val local = Ratings.all(ctx)
        var changed = false
        var localNewer = false
        for (k in remote.keys()) {
            val r = remote.optJSONObject(k) ?: continue
            val l = local.optJSONObject(k)
            if (l == null || r.optLong("at") > l.optLong("at")) { local.put(k, r); changed = true }
        }
        for (k in local.keys()) {
            val r = remote.optJSONObject(k)
            if (r == null || local.getJSONObject(k).optLong("at") > r.optLong("at")) localNewer = true
        }
        if (changed) Ratings.replaceAll(ctx, local)
        return changed to localNewer
    }
}
