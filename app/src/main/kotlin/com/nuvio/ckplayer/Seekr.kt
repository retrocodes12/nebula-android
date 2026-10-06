package com.nuvio.ckplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
 * One play's Seekr frames. Once the lookup answers ([start]) every sprite sheet of the title is DOWNLOADED in the
 * background (compressed, ~0.5 MB each — a film is a few MB), nearest the playhead first; a sheet the scrub points at
 * jumps the queue. A picture is never a whole-sheet decode: each sheet gets a BitmapRegionDecoder over its bytes and
 * only the cue's 320×180 is decoded (a few ms), into a crop LRU. The decode worker serves the wanted cue first, then
 * the next [AHEAD] in the scrub's direction, then [WARM] around where the viewer is, so a moving finger mostly finds
 * its crop already made. While a crop is being made the tip keeps the nearest one it has — it never goes blank in the
 * middle of a scrub (a null frame folds the card to the pill, which read as "breaks"). 1.85.0 fetched and fully
 * decoded a 3200×1800 sheet only when a scrub reached it, kept three, and showed nothing meanwhile.
 * A signed URL that answers 403 (expired) looks the title up again once; a play whose sheets keep failing gives up
 * ([ready] false) so the scrub falls back to the app's own frames.
 */
internal class SeekrFrames internal constructor(
    private val q: List<Pair<String, String>>,
    private val get: (String) -> Pair<Int, ByteArray?> = ::httpGet,    // a sheet: status + bytes (tests pass their own)
    tv: Boolean = false,                                                // a TV box: a smaller share of a smaller memory
) {
    // what is held: a phone keeps a whole title's sheets and plenty decoded; a TV box (often 1 GB, the film's buffer on
    // the same heap) a third of it — the web's TV keeps half the tiles too
    private val cropsMax = if (tv) 80 else 160           // 320×180 RGB 565 = 115 KB each → ≤ 18 MB (9 MB on a TV)
    private val decodersMax = if (tv) 4 else 12          // region decoders kept (each holds its sheet's bytes)
    private val bytesMax = if (tv) 24L * 1024 * 1024 else 64L * 1024 * 1024   // a title's sheets, compressed

    companion object {
        private const val NEAR = 6                       // a crop this many cues away stands in at once
        private const val AHEAD = 4                      // crops made past the wanted one, the way the scrub moves
        private const val WARM = 6                       // crops made either side of where the viewer is, when idle
        private const val GIVE_UP = 3                    // sheets failing before any worked

        /** Null when no key is connected or the title cannot be looked up (live, kitsu, no id…). */
        fun forPlay(ctx: Context, type: String?, id: String?): SeekrFrames? {
            Seekr.load(ctx)
            if (Seekr.key.isEmpty()) return null
            return SeekrWire.query(type, id)?.let { SeekrFrames(it, tv = Account.isTv(ctx)) }
        }

        /** Worker thread. One sheet's bytes; no key on this hop. */
        private fun httpGet(url: String): Pair<Int, ByteArray?> =
            Seekr.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                r.code to (if (r.isSuccessful) Seekr.bytesOf(r.body, Seekr.SHEET_MAX) else null)
            }

        private fun regionDecoder(b: ByteArray): BitmapRegionDecoder? = runCatching {
            if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(b, 0, b.size)
            else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(b, 0, b.size, false)
        }.getOrNull()
    }

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val kickFetch = Channel<Unit>(Channel.CONFLATED)
    private val kickDecode = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var track: Seekr.Track? = null
    private var durMs = 0L
    private var started = false
    private var relooked = false
    private var order: List<String> = emptyList()       // the title's sheets, nearest the start position first (under lock)
    private val bytes = HashMap<String, ByteArray>()     // sheet URL → JPEG (under lock)
    private var held = 0L                                // their total size (under lock)
    private val decoders = object : LinkedHashMap<String, BitmapRegionDecoder>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BitmapRegionDecoder>?): Boolean {
            if (size <= decodersMax) return false
            runCatching { eldest?.value?.recycle() }
            return true
        }
    }                                                    // decode thread only
    private val crops = object : LinkedHashMap<Int, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?) = size > cropsMax
    }
    private val bad = HashSet<String>()                  // sheet URLs that would not load (under lock)
    private val skip = HashSet<Int>()                    // cues whose region would not decode (under lock)
    private var good = 0                                 // sheets that did (under lock)
    @Volatile private var wanted = -1                    // the cue the scrub points at; -1 = nothing wanted
    @Volatile private var home = 0                       // where the viewer is: the start, then the last scrub
    @Volatile private var dir = 1                        // which way the scrub last moved
    @Volatile private var dead = false
    private val frameState = mutableStateOf<Bitmap?>(null)

    /** The lookup answered with previews and they still load: the scrub asks here instead of the app's own reader. */
    var ready by mutableStateOf(false); private set
    /** The lookup has answered, either way. */
    var settled by mutableStateOf(false); private set
    val frame: State<Bitmap?> get() = frameState

    /** Main thread, once the length is known and the item is not live. Once per play. [atMs] = where playback is. */
    fun start(durationMs: Long, atMs: Long = 0) {
        if (started || dead || durationMs <= 0) return
        started = true; durMs = durationMs
        io.launch {
            val t = Seekr.track(q, durationMs)
            withContext(Dispatchers.Main) { if (!dead) begin(t, atMs) }
        }
    }

    /** Tests: skip the lookup. Main thread. */
    internal fun startWith(t: Seekr.Track, atMs: Long = 0) { if (!started && !dead) { started = true; begin(t, atMs) } }

    private fun begin(t: Seekr.Track?, atMs: Long) {
        track = t; ready = t != null; settled = true
        if (t == null) return
        home = SeekrWire.cueIndex(t.cues, atMs, t.scale).coerceAtLeast(0)
        synchronized(lock) { order = sheetOrder(t, home) }
        io.launch { fetchLoop() }
        io.launch { decodeLoop() }
    }

    /** The title's distinct sheets, the one holding cue [from] first, then outward from it. */
    private fun sheetOrder(t: Seekr.Track, from: Int): List<String> {
        val seq = LinkedHashSet<String>()
        t.cues.forEach { seq.add(it.sheet) }
        val all = seq.toList()
        val at = all.indexOf(t.cues[from.coerceIn(0, t.cues.size - 1)].sheet).coerceAtLeast(0)
        val out = ArrayList<String>(all.size)
        for (d in 0 until all.size) {
            if (at + d < all.size) out.add(all[at + d])
            if (d > 0 && at - d >= 0) out.add(all[at - d])
        }
        return out
    }

    /** Main thread. Point the preview at the player position [posMs]. */
    fun request(posMs: Long) {
        val t = track ?: return
        if (dead || !ready) return
        val i = SeekrWire.cueIndex(t.cues, posMs, t.scale)
        if (i < 0) return
        val was = wanted
        if (was >= 0 && i != was) dir = if (i > was) 1 else -1
        wanted = i; home = i
        show(t, i)
        kickDecode.trySend(Unit); kickFetch.trySend(Unit)
    }

    /** Main thread. The scrub ended. */
    fun idle() { wanted = -1; kickDecode.trySend(Unit) }

    /** Main thread. Player dispose. */
    fun release() {
        dead = true
        wanted = -1
        io.cancel()
        synchronized(lock) { crops.clear(); bytes.clear(); held = 0 }
        Thread { synchronized(decoders) { decoders.values.forEach { runCatching { it.recycle() } }; decoders.clear() } }.start()
        frameState.value = null
    }

    /** Under the lock: cue [i] if its crop is made, else the nearest made within [NEAR], else -1. */
    private fun best(t: Seekr.Track, i: Int): Int {
        if (crops.containsKey(i)) return i
        for (d in 1..NEAR) {
            if (i - d >= 0 && crops.containsKey(i - d)) return i - d
            if (i + d < t.cues.size && crops.containsKey(i + d)) return i + d
        }
        return -1
    }

    /** Main thread. Show the best crop for cue [i]; nothing changes when none is near (never a blank mid-scrub). */
    private fun show(t: Seekr.Track, i: Int) {
        val (j, b) = synchronized(lock) { best(t, i).let { it to (if (it >= 0) crops[it] else null) } }
        if (b != null) { frameState.value = b; shownCue = j }
    }

    /** Tests: the cue whose picture is showing, and whether a cue's crop is made. */
    @Volatile internal var shownCue = -1; private set
    internal fun hasCrop(i: Int) = synchronized(lock) { crops.containsKey(i) }

    // ---- downloads: the wanted cue's sheet first, then the title's sheets in order ----

    private fun nextFetch(t: Seekr.Track): String? = synchronized(lock) {
        val w = wanted
        if (w >= 0 && w < t.cues.size) {
            val u = t.cues[w].sheet
            if (!bytes.containsKey(u) && u !in bad) return@synchronized u
        }
        if (held >= bytesMax) return@synchronized null
        order.firstOrNull { !bytes.containsKey(it) && it !in bad }
    }

    private suspend fun fetchLoop() {
        while (!dead) {
            val t = track ?: return
            val u = nextFetch(t)
            if (u == null) { kickFetch.receive(); continue }
            val (code, b) = try { get(u) } catch (e: CancellationException) { throw e } catch (e: Exception) { 0 to null }
            if (dead) return
            if (code == 403) {
                // the signed URLs ran out (6 h): one fresh lookup, then everything again from the new addresses
                val nt = if (relooked) null else { relooked = true; Seekr.track(q, durMs, fresh = true) }
                if (nt != null) {
                    synchronized(lock) { track = nt; bytes.clear(); held = 0; order = sheetOrder(nt, home) }
                    continue
                }
            }
            val giveUp = synchronized(lock) {
                if (b != null && code in 200..299) { bytes[u] = b; held += b.size; good++ } else bad.add(u)
                good == 0 && bad.size >= GIVE_UP
            }
            if (giveUp) {
                withContext(Dispatchers.Main) { if (!dead) { ready = false; frameState.value = null } }
                return
            }
            kickDecode.trySend(Unit)
        }
    }

    // ---- decodes: one cue's region at a time ----

    /** The next crop worth making: the wanted cue, then ahead of it, then around [home]; only from sheets in hand. */
    private fun nextDecode(t: Seekr.Track): Int? = synchronized(lock) {
        val n = t.cues.size
        fun ok(i: Int) = i in 0 until n && !crops.containsKey(i) && i !in skip && bytes.containsKey(t.cues[i].sheet)
        val w = wanted
        if (w >= 0) {
            if (ok(w)) return@synchronized w
            for (d in 1..AHEAD) { val i = w + dir * d; if (ok(i)) return@synchronized i }
        }
        val h = if (w >= 0) w else home
        if (ok(h)) return@synchronized h
        for (d in 1..WARM) {
            if (ok(h + d)) return@synchronized h + d
            if (ok(h - d)) return@synchronized h - d
        }
        null
    }

    private suspend fun decodeLoop() {
        while (!dead) {
            val t = track ?: return
            val i = nextDecode(t)
            if (i == null) { kickDecode.receive(); continue }
            val c = t.cues[i]
            val (made, sheetBad) = cropFrom(c)
            synchronized(lock) {
                when {
                    made != null -> crops[i] = made
                    sheetBad -> { bad.add(c.sheet); bytes.remove(c.sheet)?.let { held -= it.size } }   // never again
                    else -> skip.add(i)
                }
            }
            val w = wanted
            if (made != null && w >= 0 && kotlin.math.abs(w - i) <= NEAR) withContext(Dispatchers.Main) {
                val now = wanted
                if (!dead && now >= 0) show(t, now)
            }
        }
    }

    /** Decode thread. The cue's region of its sheet, at full size, RGB 565 — and whether the SHEET itself is unusable. */
    private fun cropFrom(c: SeekrCue): Pair<Bitmap?, Boolean> = synchronized(decoders) {
        val d = decoders[c.sheet] ?: run {
            val b = synchronized(lock) { bytes[c.sheet] } ?: return@synchronized null to false
            regionDecoder(b)?.also { decoders[c.sheet] = it } ?: return@synchronized null to true
        }
        runCatching {
            val r = Rect(c.x, c.y, minOf(c.x + c.w, d.width), minOf(c.y + c.h, d.height))
            if (r.width() <= 0 || r.height() <= 0) null
            else d.decodeRegion(r, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
        }.getOrNull() to false
    }
}
