package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tracking — Trakt · Simkl · MDBList (2026-11-10, the shared player's `track*` block rule for rule). What plays is
 * reported to the viewer's OWN account at a service they connected in Settings › Playback: it started, it was paused,
 * it stopped — one call per thing the viewer did, never on a timer (each service moves the clock itself and marks the
 * title watched at 80 %). The app calls the services directly; only Trakt's sign-in passes through our cloud, which
 * holds the secret that turning a code into a token (and renewing it once a day) needs. Which services exist comes
 * from the cloud (/v1/track/config: public client ids) — a service that is not set up there has no row.
 * The connection is the synced doc `track` (`track_b` for the second profile), each service its own newest-wins
 * record, kept in `ckplayer_private` (out of backups): connect on a phone and the TV reports too.
 */
internal object Track {
    private const val TRAKT = "https://api.trakt.tv"
    private const val SIMKL = "https://api.simkl.com"
    private const val MDBL = "https://api.mdblist.com"
    private const val CLOUD = "https://play.rifflehq.in/cloud"
    private const val PREFS = "ckplayer_private"
    private const val CFG = "track_cfg"
    private const val CFG_MS = 6L * 3600_000
    private const val SPARE_MS = 3600_000L                     // a token is renewed with an hour of life left
    private val JSON_MT = "application/json".toMediaType()
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renewing = Mutex()

    /** Compose state: Settings redraws when a sync brings a connection in or takes it away. */
    var doc by mutableStateOf(TrackDoc()); private set
    var traktId by mutableStateOf(""); private set
    var simklId by mutableStateOf(""); private set
    var mdblOffered by mutableStateOf(false); private set
    private var loadedFor: String? = null
    private var cfgAt = 0L
    private var cfgRead = false
    @Volatile private var warnedMdbl = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun prefKey() = if (Viewers.current == "b") "track_v1_b" else "track_v1"

    /** The profile this device is on changed: its own connection is read at the next use. */
    fun reset() { loadedFor = null }

    /** The second profile is gone from this device (another account signed in): so is its connection. */
    fun forgetSecond(ctx: Context) { prefs(ctx).edit().remove("track_v1_b").apply(); if (Viewers.current == "b") reset() }

    fun load(ctx: Context) {
        val k = prefKey()
        if (loadedFor != k) {
            loadedFor = k
            doc = runCatching { TrackWire.read(JSONObject(prefs(ctx).getString(k, null) ?: "{}")) }.getOrDefault(TrackDoc())
        }
        if (!cfgRead) {
            cfgRead = true
            runCatching {
                val o = JSONObject(prefs(ctx).getString(CFG, null) ?: return@runCatching)
                traktId = o.optString("trakt").takeIf { TrackWire.validClientId(it) }.orEmpty()
                simklId = o.optString("simkl").takeIf { TrackWire.validClientId(it) }.orEmpty()
                mdblOffered = o.optBoolean("mdblist")
                cfgAt = o.optLong("at")
            }
        }
    }

    private fun store(ctx: Context, d: TrackDoc) {
        doc = d
        prefs(ctx).edit().putString(prefKey(), TrackWire.doc(d)).apply()
    }

    fun stamped(ctx: Context): Boolean { load(ctx); return doc.stamped }
    internal fun docJson(ctx: Context): String { load(ctx); return TrackWire.doc(doc) }

    /** Cloud.kt's merge for the `track` doc: (changed here, push ours back). */
    internal fun merge(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        load(ctx)
        val (kept, changed, ours) = TrackWire.merge(doc, TrackWire.read(remote))
        if (changed) store(ctx, kept)
        return changed to ours
    }

    /** One service's record replaced and stamped now, so it wins on every other device of the profile. */
    private fun put(ctx: Context, d: TrackDoc) {
        load(ctx)
        store(ctx, d)
        Cloud.noteChanged(ctx, "track")
    }

    fun disconnect(ctx: Context, svc: String) {
        load(ctx)
        val now = System.currentTimeMillis()
        put(ctx, when (svc) {
            "trakt" -> doc.copy(trakt = TrackAuth(at = now))
            "simkl" -> doc.copy(simkl = TrackAuth(at = now))
            else -> doc.copy(mdbl = TrackKey(at = now))
        })
    }

    /** Which services this server offers (asked every 6 h at most; an old cloud without the route offers none). */
    suspend fun config(ctx: Context, force: Boolean = false) {
        load(ctx)
        if (!force && cfgAt > 0 && System.currentTimeMillis() - cfgAt < CFG_MS) return
        val o = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url("$CLOUD/v1/track/config").header("Accept", "application/json").build()).execute().use { r ->
                    if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null
                }
            }.getOrElse { if (it is CancellationException) throw it; null }
        } ?: return
        traktId = o.optString("trakt").takeIf { TrackWire.validClientId(it) }.orEmpty()
        simklId = o.optString("simkl").takeIf { TrackWire.validClientId(it) }.orEmpty()
        mdblOffered = o.optBoolean("mdblist")
        cfgAt = System.currentTimeMillis()
        prefs(ctx).edit().putString(CFG, JSONObject().put("trakt", traktId).put("simkl", simklId).put("mdblist", mdblOffered).put("at", cfgAt).toString()).apply()
    }

    // ---------- signing in: a code from the service, typed on its own page; polled until the person approves it there

    /** A sign-in under way. [device] is what we poll with (never shown); [user] is what the person types. */
    class Code(val svc: String, val device: String, val user: String, val url: String, val full: String, val until: Long, var everyMs: Long)

    enum class Poll { PENDING, DONE, EXPIRED, DENIED, SETUP }

    private fun simklUrl(path: String, version: String) = (SIMKL + path).toHttpUrl().newBuilder()
        .addQueryParameter("client_id", simklId).addQueryParameter("app-name", "nebula-player").addQueryParameter("app-version", version.ifEmpty { "1" }).build()

    private fun post(url: okhttp3.HttpUrl, body: RequestBody, vararg headers: Pair<String, String>): Pair<Int, JSONObject?> {
        val b = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "NebulaPlayer").post(body)
        headers.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r ->
            return r.code to runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull()
        }
    }

    /** Asks the service for a code; null when it did not answer. */
    suspend fun askCode(svc: String, version: String): Code? = withContext(Dispatchers.IO) {
        runCatching {
            val (code, o) = if (svc == "trakt") post("$TRAKT/oauth/device/code".toHttpUrl(), JSONObject().put("client_id", traktId).toString().toRequestBody(JSON_MT))
            else post(simklUrl("/oauth2/device", version), FormBody.Builder().add("client_id", simklId).add("scope", "media:read media:write").build())
            if (code !in 200..299 || o == null) return@runCatching null
            val device = o.optString("device_code")
            val user = o.optString("user_code")
            val url = o.optString("verification_url").ifEmpty { o.optString("verification_uri") }
            if (device.isEmpty() || user.isEmpty() || !url.startsWith("https://")) return@runCatching null
            val whole = o.optString("verification_uri_complete")
            val full = if (whole.startsWith("https://")) whole else if (svc == "trakt") url.trimEnd('/') + "/" + user else url
            Code(svc, device, user.take(16), url, full,
                System.currentTimeMillis() + o.optLong("expires_in", 600).coerceIn(60, 900) * 1000,
                o.optLong("interval", 5).coerceIn(5, 30) * 1000)
        }.getOrElse { if (it is CancellationException) throw it; null }
    }

    /** One look at whether the person approved [c]; on DONE the connection is kept (and synced). A hiccup is PENDING. */
    suspend fun poll(ctx: Context, c: Code, version: String): Poll {
        if (System.currentTimeMillis() > c.until) return Poll.EXPIRED
        val got: Any = withContext(Dispatchers.IO) {
            runCatching<Any> {
                if (c.svc == "trakt") {
                    val (code, o) = post("$CLOUD/v1/trakt/token".toHttpUrl(), JSONObject().put("device_code", c.device).toString().toRequestBody(JSON_MT))
                    when {
                        code == 200 && o != null && TrackWire.validToken(o.optString("access_token")) -> TrackAuth(
                            o.optString("access_token"), o.optString("refresh_token").takeIf { TrackWire.validToken(it) }.orEmpty(),
                            (o.optLong("created_at").takeIf { it > 0 }?.times(1000) ?: System.currentTimeMillis()) + o.optLong("expires_in", 86400) * 1000,
                            "", System.currentTimeMillis(),
                        )
                        code == 200 && o != null && o.optBoolean("pending") -> { if (o.optBoolean("slow")) c.everyMs += 5000; Poll.PENDING }
                        code == 410 -> if (o?.optString("error") == "denied") Poll.DENIED else Poll.EXPIRED
                        code == 503 -> Poll.SETUP
                        else -> Poll.PENDING
                    }
                } else {
                    val (code, o) = post(simklUrl("/oauth2/token", version), FormBody.Builder()
                        .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").add("client_id", simklId).add("device_code", c.device).build())
                    val err = o?.optString("error").orEmpty()
                    when {
                        code == 200 && o != null && TrackWire.validToken(o.optString("access_token")) -> TrackAuth(
                            o.optString("access_token"), o.optString("refresh_token").takeIf { TrackWire.validToken(it) }.orEmpty(),
                            System.currentTimeMillis() + o.optLong("expires_in", 604800) * 1000, "", System.currentTimeMillis(),
                        )
                        err == "slow_down" -> { c.everyMs += 5000; Poll.PENDING }
                        err == "expired_token" -> Poll.EXPIRED
                        code == 401 -> Poll.SETUP
                        else -> Poll.PENDING
                    }
                }
            }.getOrElse { if (it is CancellationException) throw it; Poll.PENDING }
        }
        if (got is TrackAuth) {
            put(ctx, if (c.svc == "trakt") doc.copy(trakt = got) else doc.copy(simkl = got))
            scope.launch { who(ctx, c.svc, version) }
            return Poll.DONE
        }
        return got as Poll
    }

    /** The account's name, for the row (asked once after connecting; the connection works without it). */
    private suspend fun who(ctx: Context, svc: String, version: String) {
        val tok = token(ctx, svc, version)
        if (tok.isEmpty()) return
        val name = runCatching {
            val req = if (svc == "trakt") Request.Builder().url("$TRAKT/users/settings").header("trakt-api-version", "2").header("trakt-api-key", traktId)
                .header("Authorization", "Bearer $tok").header("User-Agent", "NebulaPlayer").build()
            else Request.Builder().url(simklUrl("/users/settings", version)).header("Authorization", "Bearer $tok").header("User-Agent", "NebulaPlayer")
                .post(ByteArray(0).toRequestBody(null)).build()
            http.newCall(req).execute().use { r ->
                val u = if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()).optJSONObject("user") else null
                TrackWire.cleanName(u?.optString("username").orEmpty().ifEmpty { u?.optString("name").orEmpty() })
            }
        }.getOrElse { if (it is CancellationException) throw it; "" }
        if (name.isEmpty()) return
        withContext(Dispatchers.Main) {
            val cur = if (svc == "trakt") doc.trakt else doc.simkl
            if (cur.a.isEmpty() || cur.user == name) return@withContext
            val named = cur.copy(user = name, at = System.currentTimeMillis())
            put(ctx, if (svc == "trakt") doc.copy(trakt = named) else doc.copy(simkl = named))
        }
    }

    /** MDBList: who a key belongs to — (true, the name, may be "") or (false, why, in words). */
    suspend fun mdblVerify(k: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$MDBL/user".toHttpUrl().newBuilder().addQueryParameter("apikey", k).build()).header("Accept", "application/json").build()
            http.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> {
                        val o = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull()
                        true to TrackWire.cleanName(o?.optString("username").orEmpty().ifEmpty { o?.optString("name").orEmpty() })
                    }
                    r.code == 401 || r.code == 403 -> false to "MDBList does not know that key"
                    r.code == 429 -> false to "Too many tries — wait a minute"
                    else -> false to "Could not reach MDBList"
                }
            }
        }.getOrElse { if (it is CancellationException) throw it; false to "Could not reach MDBList" }
    }

    fun mdblConnect(ctx: Context, key: String, user: String) {
        load(ctx)
        warnedMdbl = false
        put(ctx, doc.copy(mdbl = TrackKey(key, TrackWire.cleanName(user), System.currentTimeMillis())))
    }

    // ---------- tokens

    private fun authOf(svc: String) = if (svc == "trakt") doc.trakt else doc.simkl

    /** → the new record · null not reachable now · a record with no token = refused (sign in again) */
    private fun renew(svc: String, old: TrackAuth, version: String): TrackAuth? = runCatching {
        if (svc == "trakt") {
            val (code, o) = post("$CLOUD/v1/trakt/token".toHttpUrl(), JSONObject().put("refresh_token", old.r).toString().toRequestBody(JSON_MT))
            when {
                code == 403 -> TrackAuth(at = System.currentTimeMillis())
                code == 200 && o != null && TrackWire.validToken(o.optString("access_token")) -> old.copy(
                    a = o.optString("access_token"), r = o.optString("refresh_token").takeIf { TrackWire.validToken(it) } ?: old.r,
                    exp = (o.optLong("created_at").takeIf { it > 0 }?.times(1000) ?: System.currentTimeMillis()) + o.optLong("expires_in", 86400) * 1000,
                    at = System.currentTimeMillis(),
                )
                else -> null
            }
        } else {
            val (code, o) = post(simklUrl("/oauth2/token", version), FormBody.Builder()
                .add("grant_type", "refresh_token").add("client_id", simklId).add("refresh_token", old.r).build())
            val err = o?.optString("error").orEmpty()
            when {
                code == 200 && o != null && TrackWire.validToken(o.optString("access_token")) -> old.copy(
                    a = o.optString("access_token"), r = o.optString("refresh_token").takeIf { TrackWire.validToken(it) } ?: old.r,
                    exp = System.currentTimeMillis() + o.optLong("expires_in", 604800) * 1000, at = System.currentTimeMillis(),
                )
                err == "invalid_grant" || err == "invalid_client" -> TrackAuth(at = System.currentTimeMillis())
                else -> null
            }
        }
    }.getOrElse { if (it is CancellationException) throw it; null }

    private suspend fun dropped(ctx: Context, svc: String, gone: TrackAuth) {
        withContext(Dispatchers.Main) {
            put(ctx, if (svc == "trakt") doc.copy(trakt = gone) else doc.copy(simkl = gone))
            Toasts.show((if (svc == "trakt") "Trakt" else "Simkl") + " asks you to connect again — Settings › Playback")
        }
    }

    /** A token to call [svc] with ("" when not connected, or it could not be renewed). Renewed with an hour to spare:
        the profile's copy is looked at first — another device may have renewed it already (Trakt's renewal token works
        once). */
    suspend fun token(ctx: Context, svc: String, version: String): String {
        val now = withContext(Dispatchers.Main) { load(ctx); authOf(svc) }
        if (now.a.isEmpty()) return ""
        if (now.exp <= 0 || now.exp - System.currentTimeMillis() > SPARE_MS) return now.a
        return renewing.withLock {
            if (Cloud.linked(ctx)) runCatching { Cloud.pullAll(ctx, force = true) }.onFailure { if (it is CancellationException) throw it }
            val cur = withContext(Dispatchers.Main) { authOf(svc) }
            if (cur.a.isEmpty()) return@withLock ""
            if (cur.exp - System.currentTimeMillis() > SPARE_MS) return@withLock cur.a
            if (cur.r.isEmpty()) return@withLock if (cur.exp > System.currentTimeMillis()) cur.a else ""
            val got = withContext(Dispatchers.IO) { renew(svc, cur, version) }
                ?: return@withLock if (cur.exp > System.currentTimeMillis()) cur.a else ""      // not reachable: the old one while it lives
            if (got.a.isEmpty()) { dropped(ctx, svc, got); return@withLock "" }
            withContext(Dispatchers.Main) { put(ctx, if (svc == "trakt") doc.copy(trakt = got) else doc.copy(simkl = got)) }
            got.a
        }
    }

    // ---------- reporting what plays

    /** Anything to report to at all (the player asks before it works out where the film is). */
    fun on(ctx: Context): Boolean {
        load(ctx)
        val d = doc
        return (d.trakt.a.isNotEmpty() && traktId.isNotEmpty()) || (d.simkl.a.isNotEmpty() && simklId.isNotEmpty()) || d.mdbl.key.isNotEmpty()
    }

    /** One event ("start" | "pause" | "stop") for [item] at [pct] percent, off the main thread, to every connected service. */
    fun report(ctx: Context, event: String, item: TrackWire.Item, pct: Double, version: String) {
        load(ctx)
        val d = doc
        val app = ctx.applicationContext
        if (d.trakt.a.isNotEmpty() && traktId.isNotEmpty() && item.trakt != null) {
            val call = TrackWire.traktCall(event, pct)
            if (call != null) scope.launch { runCatching { trakt(app, call, TrackWire.body(item.trakt, pct), version) } }
        }
        if (d.simkl.a.isNotEmpty() && simklId.isNotEmpty() && item.simkl != null) scope.launch { runCatching { simkl(app, event, TrackWire.body(item.simkl, pct), version) } }
        if (d.mdbl.key.isNotEmpty() && item.mdbl != null) scope.launch { runCatching { mdbl(d.mdbl.key, event, TrackWire.body(item.mdbl, pct), version) } }
    }

    /** One call with the service's token; a 401 looks at the profile's copy once (another device renewed it) before giving up. */
    private suspend fun withToken(ctx: Context, svc: String, version: String, call: (String) -> Int) {
        val tok = token(ctx, svc, version)
        if (tok.isEmpty()) return
        if (call(tok) != 401) return
        if (Cloud.linked(ctx)) runCatching { Cloud.pullAll(ctx, force = true) }.onFailure { if (it is CancellationException) throw it }
        val cur = withContext(Dispatchers.Main) { authOf(svc) }
        if (cur.a.isNotEmpty() && cur.a != tok) { call(cur.a); return }
        if (cur.a.isNotEmpty()) dropped(ctx, svc, TrackAuth(at = System.currentTimeMillis()))
    }

    private suspend fun trakt(ctx: Context, call: String, body: JSONObject, version: String) = withToken(ctx, "trakt", version) { tok ->
        post("$TRAKT/scrobble/$call".toHttpUrl(), body.toString().toRequestBody(JSON_MT),
            "trakt-api-version" to "2", "trakt-api-key" to traktId, "Authorization" to "Bearer $tok").first
    }

    private suspend fun simkl(ctx: Context, event: String, body: JSONObject, version: String) = withToken(ctx, "simkl", version) { tok ->
        post(simklUrl("/scrobble/$event", version), body.toString().toRequestBody(JSON_MT), "Authorization" to "Bearer $tok").first
    }

    private suspend fun mdbl(key: String, event: String, body: JSONObject, version: String) {
        if (version.isNotEmpty()) body.put("app_version", version)
        val (code, _) = post("$MDBL/scrobble/$event".toHttpUrl().newBuilder().addQueryParameter("apikey", key).build(), body.toString().toRequestBody(JSON_MT))
        if ((code == 401 || code == 403) && !warnedMdbl) {
            warnedMdbl = true
            withContext(Dispatchers.Main) { Toasts.show("MDBList refused your key — check it in Settings › Playback") }
        }
    }
}

/** One player's tracking state: the play the services were told about and the last thing they were told. */
internal class TrackSession {
    var type: String? = null
    var id: String? = null
    var item: TrackWire.Item? = null
    var sent = ""            // "start" | "pause" | "" (nothing said about this play yet)
    var done = false         // its stop went out (it ended, or the player moved on)
    var lastPct = -1.0       // where it was when last seen — for the stop sent after the player moved on in place
}
