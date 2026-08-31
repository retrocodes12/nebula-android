package com.nuvio.ckplayer

import android.content.Context
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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Nebula Cloud sync client — the same account-less pairing the web/TV player
 * uses: a 6-character code links devices into a group, then add-ons, watch
 * progress and My List flow both ways, newest change winning per record.
 *
 * The WIRE FORMAT IS THE WEB PLAYER'S, verbatim — that is the whole point
 * (one group spans Android, web, desktop and TV). Two impedance mismatches are
 * handled here and only here:
 *   - positions/durations travel in SECONDS (web-style); Android stores ms.
 *   - the add-on list carries no timestamps locally, so this layer keeps its
 *     own added/removed stamps (prefs "addons_sync"), exactly like the web's.
 */
object Cloud {
    private const val BASE = "https://play.rifflehq.in/cloud"
    private const val PREFS = "ckplayer"
    private val SYNC_KEYS = listOf("addons", "progress", "library", "sub_style")
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

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun gid(ctx: Context) = prefs(ctx).getString("cloud_gid", null)
    private fun secret(ctx: Context) = prefs(ctx).getString("cloud_secret", null)
    fun linked(ctx: Context) = !gid(ctx).isNullOrEmpty() && !secret(ctx).isNullOrEmpty()

    private fun jsonPref(ctx: Context, key: String): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
    private fun putJsonPref(ctx: Context, key: String, o: JSONObject) =
        prefs(ctx).edit().putString(key, o.toString()).apply()

    // ---------- HTTP ----------
    private class HttpFail(val code: Int) : RuntimeException("HTTP $code")
    private suspend fun api(ctx: Context, method: String, path: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(BASE + path)
            if (linked(ctx)) b.header("Authorization", "Bearer ${gid(ctx)}.${secret(ctx)}")
            when (method) {
                "POST" -> b.post((body ?: JSONObject()).toString().toRequestBody(JSON_MT))
                "PUT" -> b.put((body ?: JSONObject()).toString().toRequestBody(JSON_MT))
                else -> b.get()
            }
            http.newCall(b.build()).execute().use { r ->
                if (!r.isSuccessful) throw HttpFail(r.code)
                JSONObject(r.body?.string() ?: "{}")
            }
        }

    // ---------- linking ----------
    /** Start a fresh sync group; returns the first join code (or null on failure). */
    suspend fun createGroup(ctx: Context): String? = runCatching {
        val g = api(ctx, "POST", "/v1/group", JSONObject())
        prefs(ctx).edit()
            .putString("cloud_gid", g.getString("gid"))
            .putString("cloud_secret", g.getString("secret"))
            .putString("cloud_revs", "{}")
            .putString("cloud_dirty", "{}")
            .apply()
        SYNC_KEYS.forEach { k -> if (hasContent(ctx, k)) scope.launch { pushKey(ctx, k) } }
        mintCode(ctx)
    }.getOrNull()

    /** A fresh 15-minute join code for an already-linked device. */
    suspend fun mintCode(ctx: Context): String? = runCatching {
        val body = JSONObject().put("gid", gid(ctx)).put("secret", secret(ctx))
        api(ctx, "POST", "/v1/link", body).getString("code")
    }.getOrNull()

    /** Redeem a code from another device. Returns null on success, else a user-facing error. */
    suspend fun join(ctx: Context, codeRaw: String): String? {
        val code = codeRaw.trim().replace(" ", "").uppercase()
        if (code.length < 6) return "Enter the 6-character code first."
        return runCatching {
            val g = api(ctx, "POST", "/v1/join", JSONObject().put("code", code))
            prefs(ctx).edit()
                .putString("cloud_gid", g.getString("gid"))
                .putString("cloud_secret", g.getString("secret"))
                .putString("cloud_revs", "{}")
                .putString("cloud_dirty", "{}")
                .apply()
            pullAll(ctx, force = true)
            null
        }.getOrElse {
            if ((it as? HttpFail)?.code == 404) "That code was not found — it may have expired."
            else "Could not reach the sync server."
        }
    }

    fun leave(ctx: Context) {
        prefs(ctx).edit()
            .remove("cloud_gid").remove("cloud_secret")
            .putString("cloud_revs", "{}").putString("cloud_dirty", "{}")
            .apply()
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
            JSONObject().put("list", list).put("removed", s.optJSONObject("removed") ?: JSONObject()).toString()
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
        if (rAt > lAt && style != null) {
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
}
