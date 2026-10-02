package com.nuvio.ckplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Seekr previews (seekr.tv): a viewer's OWN free key turns a scrub into instant pictures for films and episodes —
 * Seekr hosts ready-made thumbnails per title, so nothing is decoded here. The device calls Seekr directly; the key
 * never goes to our cloud except as the viewer's own synced doc `seekr` {key, at} (newest wins, "" = disconnected),
 * the same doc the web/TV player writes. Kept in `ckplayer_private`, which backups leave out (backup_rules.xml).
 * One lookup per title and length per session (process); a refused key (401) says so once and stops for the session,
 * the daily limit (429) stops silently. The parsing rules live in SeekrWire.kt.
 */
internal object Seekr {
    private const val API = "https://api.seekr.tv"
    private const val PREFS = "ckplayer_private"
    private const val PREF = "seekr_v1"
    private const val STALE_MS = 5L * 3600_000 + 30 * 60_000    // signed URLs live 6 h: look up again a little before
    private const val VTT_MAX = 4L * 1024 * 1024
    const val SHEET_MAX = 16L * 1024 * 1024

    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Compose state, so Settings redraws when a sync brings a key in or takes it away. */
    var key by mutableStateOf(""); private set
    private var at = 0L
    private var loaded = false

    // the session's verdicts on a key: refused (401, toasted once) or out of quota (429, silent)
    @Volatile private var refusedKey: String? = null
    @Volatile private var limitedKey: String? = null

    class Track(val cues: List<SeekrCue>, val scale: Double, val at: Long)
    private val found = object : LinkedHashMap<String, Track>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Track>?) = size > 8
    }
    private val missing = HashSet<String>()      // lookups Seekr has no previews for (404), this session

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val (k, a) = SeekrWire.read(JSONObject(prefs(ctx).getString(PREF, null) ?: return)) ?: return
            key = k; at = a
        }
    }

    fun at(ctx: Context): Long { load(ctx); return at }

    internal fun doc(ctx: Context): String { load(ctx); return SeekrWire.doc(key, at) }

    private fun store(ctx: Context, k: String, stamp: Long) {
        key = k; at = stamp
        prefs(ctx).edit().putString(PREF, SeekrWire.doc(k, stamp)).apply()
    }

    /** Connect (a checked key) or disconnect (""), stamped now so it wins on every other device of the profile. */
    fun set(ctx: Context, k: String) {
        load(ctx)
        store(ctx, k, System.currentTimeMillis())
        Cloud.noteChanged(ctx, "seekr")
    }

    /** Cloud.kt's merge for the `seekr` doc: (changed here, push ours back). */
    internal fun merge(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        load(ctx)
        val (adopt, pushBack) = SeekrWire.mergeDecision(at, remote)
        if (adopt) SeekrWire.read(remote)?.let { (k, a) -> store(ctx, k, a) }
        return adopt to pushBack
    }

    enum class Check { OK, REFUSED, UNREACHABLE }

    /** GET /v1/keys/validate — costs no quota. */
    suspend fun validate(k: String): Check = withContext(Dispatchers.IO) {
        if (!SeekrWire.validKey(k)) return@withContext Check.REFUSED
        try {
            val req = Request.Builder().url("$API/v1/keys/validate").header("X-API-Key", k).header("Accept", "application/json").build()
            http.newCall(req).execute().use { r ->
                when {
                    r.code == 401 -> Check.REFUSED
                    !r.isSuccessful -> Check.UNREACHABLE
                    else -> {
                        val o = bytesOf(r.body, 64L * 1024)?.let { runCatching { JSONObject(String(it)) }.getOrNull() }
                        when {
                            o == null || !o.has("valid") -> Check.UNREACHABLE
                            o.optBoolean("valid") -> Check.OK
                            else -> Check.REFUSED
                        }
                    }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Check.UNREACHABLE }
    }

    /** A body read no further than [max] bytes; null when there is none or it is larger. */
    internal fun bytesOf(body: ResponseBody?, max: Long): ByteArray? {
        val src = body?.source() ?: return null
        src.request(max + 1)
        if (src.buffer.size > max) return null
        return src.buffer.readByteArray()
    }

    /**
     * IO. The previews for the title [q] at our length [durMs]: one lookup, then its VTT (no key on that hop).
     * Cached for the session per title + length; [fresh] skips the cache (a signed URL answered 403). Null = none,
     * for whatever reason — the player falls back to its own frames without a word, except a refused key.
     */
    suspend fun track(q: List<Pair<String, String>>, durMs: Long, fresh: Boolean = false): Track? = withContext(Dispatchers.IO) {
        val k = key
        if (k.isEmpty() || durMs <= 0 || k == refusedKey || k == limitedKey) return@withContext null
        val ck = q.joinToString("&") { it.first + "=" + it.second } + "|" + durMs
        if (!fresh) synchronized(found) {
            if (ck in missing) return@withContext null
            found[ck]?.let { if (System.currentTimeMillis() - it.at < STALE_MS) return@withContext it }
        }
        try {
            val url = "$API/sprites".toHttpUrl().newBuilder().addQueryParameter("duration_ms", durMs.toString())
                .apply { q.forEach { addQueryParameter(it.first, it.second) } }.build()
            val req = Request.Builder().url(url).header("X-API-Key", k).header("Accept", "application/json").build()
            val (code, body) = http.newCall(req).execute().use { r ->
                r.code to (if (r.isSuccessful) bytesOf(r.body, 256L * 1024)?.let { String(it) } else null)
            }
            when (code) {
                401 -> { refused(k); return@withContext null }
                429 -> { limitedKey = k; return@withContext null }
                404 -> { synchronized(found) { missing.add(ck) }; return@withContext null }
            }
            val o = body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@withContext null
            val vtt = o.optString("vtt_url")
            if (!vtt.startsWith("https://", ignoreCase = true)) return@withContext null
            val scale = o.optDouble("scale", 1.0).takeIf { it.isFinite() && it > 0 } ?: 1.0
            val (vcode, text) = http.newCall(Request.Builder().url(vtt).build()).execute().use { r ->
                r.code to (if (r.isSuccessful) bytesOf(r.body, VTT_MAX)?.let { String(it) } else null)
            }
            if (vcode == 403 && !fresh) return@withContext track(q, durMs, fresh = true)   // signed URL already stale
            val cues = text?.let { SeekrWire.parseVtt(it) } ?: return@withContext null
            if (cues.isEmpty()) { synchronized(found) { missing.add(ck) }; return@withContext null }
            Track(cues, scale, System.currentTimeMillis()).also { t -> synchronized(found) { found[ck] = t } }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }

    private suspend fun refused(k: String) {
        if (refusedKey == k) return
        refusedKey = k
        withContext(Dispatchers.Main) { Toasts.show("Your Seekr key was refused") }
    }
}

/**
 * One play's Seekr frames: the lookup once the length is known ([start]), then the scrub's pictures cropped out of
 * the sprite sheets — each sheet fetched and decoded once (RGB 565, the last [SHEETS] kept), a crop per cue kept in a
 * small LRU. Like ScrubPreview, one worker at a time and the latest position wins. A signed URL that answers 403
 * (expired) looks the title up again once; a play whose sheets keep failing gives up ([ready] false) so the scrub
 * falls back to the app's own frames.
 */
internal class SeekrFrames private constructor(private val q: List<Pair<String, String>>) {
    companion object {
        private const val SHEETS = 3
        private const val CROPS = 40                     // 320×180 RGB 565 = 115 KB each
        private const val NEAR = 6                       // a cue this many away stands in while the right sheet loads
        private const val SHEET_PX = 16_000_000          // a sheet bigger than this is decoded at half size
        private const val GIVE_UP = 3                    // sheets failing before any worked

        /** Null when no key is connected or the title cannot be looked up (live, kitsu, no id…). */
        fun forPlay(ctx: Context, type: String?, id: String?): SeekrFrames? {
            Seekr.load(ctx)
            if (Seekr.key.isEmpty()) return null
            return SeekrWire.query(type, id)?.let { SeekrFrames(it) }
        }
    }

    private class Sheet(val bmp: Bitmap, val sample: Int)

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    @Volatile private var track: Seekr.Track? = null
    private var durMs = 0L
    private var started = false
    private var relooked = false
    private val sheets = object : LinkedHashMap<String, Sheet>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Sheet>?) = size > SHEETS
    }
    private val crops = object : LinkedHashMap<Int, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?) = size > CROPS
    }
    private val bad = HashSet<String>()                  // sheet URLs that would not load (under lock)
    private var good = 0                                 // sheets that did (under lock)
    private var running = false                          // a worker is alive (under lock)
    @Volatile private var wanted = -1                    // the cue the scrub points at; -1 = nothing wanted
    @Volatile private var dead = false
    private val frameState = mutableStateOf<Bitmap?>(null)

    /** The lookup answered with previews and they still load: the scrub asks here instead of the app's own reader. */
    var ready by mutableStateOf(false); private set
    /** The lookup has answered, either way. */
    var settled by mutableStateOf(false); private set
    val frame: State<Bitmap?> get() = frameState

    /** Main thread, once the length is known and the item is not live. Once per play. */
    fun start(durationMs: Long) {
        if (started || dead || durationMs <= 0) return
        started = true; durMs = durationMs
        io.launch {
            val t = Seekr.track(q, durationMs)
            withContext(Dispatchers.Main) { if (!dead) { track = t; ready = t != null; settled = true } }
        }
    }

    /** Main thread. Point the preview at the player position [posMs]. */
    fun request(posMs: Long) {
        val t = track ?: return
        if (dead || !ready) return
        val i = SeekrWire.cueIndex(t.cues, posMs, t.scale)
        if (i < 0) return
        wanted = i
        val go: Boolean
        synchronized(lock) {
            frameState.value = best(t, i)
            val u = t.cues[i].sheet
            go = !running && !crops.containsKey(i) && !sheets.containsKey(u) && u !in bad
            if (go) running = true
        }
        if (go) io.launch { work() }
    }

    /** Main thread. The scrub ended. */
    fun idle() { wanted = -1 }

    /** Main thread. Player dispose. */
    fun release() {
        dead = true
        wanted = -1
        io.cancel()
        synchronized(lock) { sheets.clear(); crops.clear() }
        frameState.value = null
    }

    /** Under the lock: cue [i]'s crop, else a neighbour's within [NEAR] whose sheet is here, else null. */
    private fun best(t: Seekr.Track, i: Int): Bitmap? {
        cropOf(t, i)?.let { return it }
        for (d in 1..NEAR) {
            if (i - d >= 0) cropOf(t, i - d)?.let { return it }
            if (i + d < t.cues.size) cropOf(t, i + d)?.let { return it }
        }
        return null
    }

    private fun cropOf(t: Seekr.Track, i: Int): Bitmap? {
        crops[i]?.let { return it }
        val c = t.cues[i]
        val s = sheets[c.sheet] ?: return null
        val x = c.x / s.sample; val y = c.y / s.sample
        val w = minOf(c.w / s.sample, s.bmp.width - x); val h = minOf(c.h / s.sample, s.bmp.height - y)
        if (x < 0 || y < 0 || w <= 0 || h <= 0) return null
        val b = runCatching { Bitmap.createBitmap(s.bmp, x, y, w, h) }.getOrNull() ?: return null
        crops[i] = b
        return b
    }

    private sealed class Got {
        class Ok(val sheet: Sheet) : Got()
        object Expired : Got()
        object Failed : Got()
    }

    private suspend fun work() {
        while (true) {
            val t = track ?: break
            val u: String = synchronized(lock) {
                val i = wanted
                val url = if (dead || i < 0 || i >= t.cues.size || crops.containsKey(i)) null
                    else t.cues[i].sheet.takeIf { !sheets.containsKey(it) && it !in bad }
                if (url == null) running = false
                url
            } ?: break
            when (val got = fetch(u)) {
                is Got.Ok -> synchronized(lock) { sheets[u] = got.sheet; good++ }
                Got.Expired -> {
                    // the signed URLs ran out (6 h): one fresh lookup, then the same position again
                    val nt = if (relooked) null else { relooked = true; Seekr.track(q, durMs, fresh = true) }
                    if (nt != null) { synchronized(lock) { track = nt; sheets.clear() }; continue }
                    synchronized(lock) { bad.add(u) }
                }
                Got.Failed -> synchronized(lock) { bad.add(u) }
            }
            val giveUp = synchronized(lock) { good == 0 && bad.size >= GIVE_UP }
            withContext(Dispatchers.Main) {
                if (dead) return@withContext
                if (giveUp) { ready = false; frameState.value = null; return@withContext }
                val w = wanted
                val tt = track
                if (w >= 0 && tt != null && w < tt.cues.size) frameState.value = synchronized(lock) { best(tt, w) }
            }
            if (giveUp) { synchronized(lock) { running = false }; break }
        }
    }

    /** Worker thread. One sheet, decoded once; Expired on 403 (the signed URL ran out). No key on this hop. */
    private fun fetch(url: String): Got = try {
        val (code, bytes) = Seekr.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            r.code to (if (r.isSuccessful) Seekr.bytesOf(r.body, Seekr.SHEET_MAX) else null)
        }
        when {
            code == 403 -> Got.Expired
            bytes == null -> Got.Failed
            else -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth > 0 && bounds.outHeight > 0 &&
                    (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > SHEET_PX) sample *= 2
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565; inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { Got.Ok(Sheet(it, sample)) } ?: Got.Failed
            }
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Got.Failed }
}
