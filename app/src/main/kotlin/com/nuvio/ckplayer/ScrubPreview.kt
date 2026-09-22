package com.nuvio.ckplayer

import android.content.Context
import android.net.ConnectivityManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Frames for the scrub preview: a second, silent reader of the same progressive file.
 *
 * `MediaMetadataRetriever` reads the file through a [RangeSource] — range requests over the
 * player's own HTTP identity (MediaHttp.kt: same User-Agent, headers and cookies ExoPlayer sends),
 * so the host sees one client — and pulls one small frame per 10-second bucket, on the IO
 * dispatcher, one at a time: the latest request wins and everything asked for in between is
 * dropped. Frames live in a 200-entry LRU; `release()` on player dispose closes the reader. Only
 * plain http(s) files qualify (`eligible`): manifests, encrypted and live streams get the bubble
 * with no picture. [status] says in words what happened ("Ready", "Waiting", "Unavailable — …")
 * for the playback Info panel. Nothing here ever touches the player.
 *
 * Why there is a sweep (09-17, the Founder's phone: "no preview frames"): a phone drag lasts a
 * second, and the first frame used to cost far more than that — the reader opened on the FIRST
 * scrub, parsed the file's index (an MP4's moov or an MKV's cues, often at the tail) through
 * 256 KB range requests and then decoded, so the picture landed after the finger had gone and
 * the next drag rarely fell within a minute of it. So [warm] opens the reader a few seconds into
 * playback and pulls a coarse set of frames spread over the whole film in the background (an
 * even-coverage order, so every prefix of it covers the file), one at a time with a pause between,
 * never while the player is buffering, never on a metered connection, and never past a byte
 * budget; a scrub then shows the nearest swept frame at once and the exact one when it lands.
 */
internal class ScrubPreview(private val url: String, ctx: Context) {
    companion object {
        const val BUCKET_MS = 10_000L
        private const val CAP = 200                 // 256×144 RGB 565 = 74 KB each: ~15 MB full
        private const val NEAR = 6                  // a cached frame within a minute stands in until the right one lands
        private const val SWEEP_MAX = 120           // frames the background sweep may pull (a 2 h film: one a minute)
        private const val SWEEP_GAP_MS = 20_000L    // never closer than this — a short file gets fewer
        private const val SWEEP_EDGE = 0.03f        // skip the first and last 3 %: logos and credits
        private const val SWEEP_BUDGET = 128L * 1024 * 1024  // bytes the sweep may cost (Wi-Fi only); a scrub itself is never budgeted
        private const val SWEEP_PAUSE_MS = 120L     // between swept frames: the film's own download comes first
        private const val REST_MS = 160L            // a finger still this long wants its exact frame; a moving one gets neighbours
        private const val NEIGHBOUR_MS = 5 * 60_000L // how far around a moving finger the plan is pulled forward
        const val WAITING = "Waiting"
        const val READY = "Ready"
        const val UNAVAILABLE = "Unavailable — "

        /** A plain http(s) file — not a manifest (which also rules out ClearKey: keys ride on .mpd here). */
        fun eligible(url: String): Boolean {
            val u = url.trim()
            if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) return false
            if (Regex("\\.mpd(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)) return false
            if (Regex("\\.m3u8", RegexOption.IGNORE_CASE).containsMatchIn(u)) return false
            // a P2P stream is downloaded in order: a second reader jumping ahead for frames would
            // pull the swarm away from the pieces the picture needs
            if (P2p.isLocal(u)) return false
            return true
        }

        /** The i-th point (1-based) of the even-coverage order on [0, 1): the bit-reversed fraction. */
        internal fun spread(i: Int): Float {
            var n = i; var f = 0f; var d = 0.5f
            while (n > 0) { if (n and 1 == 1) f += d; d /= 2; n = n shr 1 }
            return f
        }
    }

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    // access-ordered: a hit moves to the back, the front is the oldest — every touch under [lock]
    private val cache = object : LinkedHashMap<Long, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean = size > CAP
    }
    private val misses = HashSet<Long>()                    // buckets the file had no frame for (under lock)
    private var hits = 0                                    // frames decoded so far (under lock)
    private var retriever: MediaMetadataRetriever? = null   // opened and used by the worker only
    private var source: RangeSource? = null                 // the retriever's file, closed with it
    private val ua = MediaHttp.userAgent(ctx)
    private var running = false                             // a worker is alive (under lock)
    private val sweep = ArrayDeque<Long>()                  // positions still to pull, best coverage first (under lock)
    private var near = NEAR                                 // how far a stand-in may be, in buckets (under lock)
    @Volatile private var busy = false                      // the player is buffering: the sweep waits
    @Volatile private var wantedMs = -1L                    // where the finger / ghost is now; -1 = nothing wanted
    @Volatile private var wantedAt = 0L                     // when it last moved: the exact frame waits for it to rest
    @Volatile private var dead = false                      // unreadable file, or released: stay quiet for good
    @Volatile private var reason: String? = null            // why it is dead, in words (null = released or alive)
    private val frameState = mutableStateOf<Bitmap?>(null)
    private val statusState = mutableStateOf(WAITING)

    /** The frame for the last requested position — its own bucket, or a neighbour while that one loads. */
    val frame: State<Bitmap?> get() = frameState

    /** "Waiting" until a frame lands, "Ready" after, "Unavailable — <why>" once the file is given up on. */
    val status: State<String> get() = statusState

    /** Main thread. Point the preview at [posMs]: the tip gets the best frame at once, the exact one later. */
    fun request(posMs: Long) {
        if (dead) return
        val ms = posMs.coerceAtLeast(0L)
        if (ms != wantedMs) wantedAt = System.currentTimeMillis()
        wantedMs = ms
        val b = ms / BUCKET_MS
        val start: Boolean
        synchronized(lock) {
            frameState.value = best(b)
            start = !running && !cache.containsKey(b) && b !in misses
            if (start) running = true
        }
        if (start) io.launch { work() }
    }

    /** Main thread. The finger lifted or the preview closed: the fetch in flight is the last one. */
    fun idle() { wantedMs = -1L }

    /**
     * Main thread, once playback runs and the length is known. Plan the background sweep over
     * [durationMs] and start it — unless the connection is metered (then frames come only when
     * asked for, as before). Calling it again is harmless: a plan already made is kept.
     */
    fun warm(ctx: Context, durationMs: Long) {
        if (dead || durationMs <= 0) return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm?.isActiveNetworkMetered == true) return
        val start: Boolean
        synchronized(lock) {
            if (sweep.isNotEmpty() || hits + misses.size > 4) return   // planned already, or scrubbed plenty by hand
            val lo = (durationMs * SWEEP_EDGE).toLong()
            val span = durationMs - 2 * lo
            val n = minOf(SWEEP_MAX.toLong(), span / SWEEP_GAP_MS).toInt()
            if (n < 2) return
            for (i in 1..n) sweep.addLast(lo + (span * spread(i)).toLong())
            // a stand-in may come from as far as the sweep's own spacing: after it every point has one
            near = maxOf(NEAR, (span / n / BUCKET_MS).toInt() + 1)
            start = !running
            if (start) running = true
        }
        if (start) io.launch { work() }
    }

    /**
     * Any thread. The player is buffering ([b] true): the sweep stands aside until it is not — and
     * when it is not, a sweep that was left waiting picks up again (the worker exits when it has
     * nothing it may do, so something has to wake it).
     */
    fun busy(b: Boolean) {
        busy = b
        if (b || dead) return
        val start: Boolean
        synchronized(lock) { start = !running && sweep.isNotEmpty(); if (start) running = true }
        if (start) io.launch { work() }
    }

    /** Main thread. Player dispose: no more fetches; the retriever closes once any fetch in flight ends. */
    fun release() {
        dead = true
        wantedMs = -1L
        val closeNow: Boolean
        synchronized(lock) { closeNow = !running; cache.clear(); misses.clear(); sweep.clear() }
        frameState.value = null
        if (closeNow) io.launch { closeRetriever() }
    }

    /** Under the lock: the frame for bucket [b], else the nearest one within [near], else null. */
    private fun best(b: Long): Bitmap? {
        cache[b]?.let { return it }
        var pick: Bitmap? = null
        var dist = near + 1
        for ((k, v) in cache) {
            val d = abs(k - b).toInt()
            if (d < dist) { dist = d; pick = v }
        }
        return pick
    }

    /** A position to fetch: [paced] = a background sweep frame (pause before it), else the finger's business. */
    private class Want(val ms: Long, val paced: Boolean)

    /** Under the lock: the planned position nearest [ms] within [NEIGHBOUR_MS] and not yet covered, taken out of the plan. */
    private fun takeNear(ms: Long): Long? {
        var pick = -1; var dist = NEIGHBOUR_MS + 1
        for (i in sweep.indices) {
            val d = abs(sweep[i] - ms)
            if (d < dist) { dist = d; pick = i }
        }
        if (pick < 0) return null
        val p = sweep.removeAt(pick)
        val pb = p / BUCKET_MS
        return if (!cache.containsKey(pb) && pb !in misses) p else takeNear(ms)
    }

    /**
     * Under the lock: what to fetch next, or null when there is nothing to do — the worker then exits.
     * A finger at REST gets its exact frame; a finger still MOVING gets the plan pulled forward around
     * it instead (an exact frame for a place it has already left would land stale, and every one it
     * waited on was a second the tip showed nothing new — the Founder's "kind of slow", 09-17); then
     * the sweep, only while the player is not buffering and the budget holds, skipping covered buckets.
     */
    private fun nextWant(): Want? = synchronized(lock) {
        val want = wantedMs
        val b = want / BUCKET_MS
        if (dead) { running = false; return@synchronized null }
        if (want >= 0 && !cache.containsKey(b) && b !in misses) {
            if (System.currentTimeMillis() - wantedAt >= REST_MS) return@synchronized Want(want, false)
            takeNear(want)?.let { return@synchronized Want(it, false) }
            return@synchronized Want(want, false)
        }
        if (!busy && (source?.bytesFetched ?: 0L) < SWEEP_BUDGET) {
            while (sweep.isNotEmpty()) {
                val p = sweep.removeFirst()
                val pb = p / BUCKET_MS
                if (!cache.containsKey(pb) && pb !in misses) return@synchronized Want(p, true)
            }
        }
        running = false
        null
    }

    // The single worker: drains "wanted" until it is cached or gone, then the sweep. Exiting and the
    // nothing-to-do decision happen under one lock, so a request arriving as it leaves starts a fresh worker.
    private suspend fun work() {
        while (true) {
            val want = nextWant() ?: break
            if (want.paced) delay(SWEEP_PAUSE_MS)
            val b = want.ms / BUCKET_MS
            val bmp = fetch(want.ms)
            val decoded: Boolean
            synchronized(lock) {
                if (bmp != null) { cache[b] = bmp; hits++ }
                else {
                    misses.add(b)
                    // it opens but nothing decodes (a codec the retriever lacks): stop asking, each try costs a download
                    if (hits == 0 && misses.size >= 3 && !dead) { dead = true; reason = source?.failure ?: "cannot decode this file" }
                }
                decoded = hits > 0
            }
            val show = wantedMs
            val why = reason
            val count = synchronized(lock) { hits }
            withContext(Dispatchers.Main) {
                if (dead) { if (why != null) statusState.value = UNAVAILABLE + why }
                else {
                    if (decoded) statusState.value = READY + " · " + count + (if (count == 1) " frame" else " frames")
                    if (show >= 0) frameState.value = synchronized(lock) { best(show / BUCKET_MS) }
                }
            }
        }
        if (dead) closeRetriever()
    }

    /** Worker thread. One frame near [posMs], scaled for the tip; null when the file has none there. */
    private fun fetch(posMs: Long): Bitmap? {
        val r = retriever ?: run {
            val m = MediaMetadataRetriever()
            val s = RangeSource(url, ua)
            try {
                m.setDataSource(s)
            } catch (e: Exception) {
                runCatching { m.release() }
                runCatching { s.close() }
                // the host refused the reader, or the container is one the retriever cannot parse:
                // bubble only, no retry storm — and the Info panel gets the sentence
                if (!dead) { dead = true; reason = s.failure ?: "cannot read this file" }
                return null
            }
            source = s
            retriever = m
            m
        }
        return try {
            val us = posMs * 1000
            val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 144)
            } else {
                val big = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
                // fit inside 256×144 whatever the picture's shape, as getScaledFrameAtTime does above
                val k = minOf(256f / big.width.coerceAtLeast(1), 144f / big.height.coerceAtLeast(1))
                val w = (big.width * k).toInt().coerceAtLeast(1)
                val h = (big.height * k).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(big, w, h, true).also { if (it !== big) big.recycle() }
            } ?: return null
            // half the memory per frame: the tip is 128 dp wide, no one sees the missing bits
            if (full.config == Bitmap.Config.RGB_565) full
            else (full.copy(Bitmap.Config.RGB_565, false) ?: full).also { if (it !== full) full.recycle() }
        } catch (e: Exception) {
            null
        }
    }

    private fun closeRetriever() {
        val (r, s) = synchronized(lock) { val x = retriever; val y = source; retriever = null; source = null; x to y }
        runCatching { r?.release() }
        runCatching { s?.close() }
    }
}

/** The glass card over the scrubber: the frame (when one is had) above the target time and the jump. */
@Composable
internal fun ScrubTip(
    targetMs: Long, deltaMs: Long, isLive: Boolean, liveBehindMs: Long, frame: Bitmap?, tv: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = if (frame != null) RoundedCornerShape(12.dp) else Pill
    val fs = if (tv) 20.sp else 14.sp
    val image = remember(frame) { frame?.asImageBitmap() }
    Column(
        modifier.background(BarGlass, shape).border(1.dp, Hairline, shape).padding(if (frame != null) 4.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (image != null) Image(
            image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(if (tv) 240.dp else 128.dp, if (tv) 135.dp else 72.dp)
                .clip(RoundedCornerShape(8.dp)).border(1.dp, Hairline, RoundedCornerShape(8.dp)),
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    !isLive -> fmtTime(targetMs)
                    liveBehindMs > 12_000 -> "−" + fmtTime(liveBehindMs) + " behind live"
                    else -> "At the live edge"
                },
                color = Ink, fontFamily = Mono, fontSize = fs, fontWeight = FontWeight.Medium, maxLines = 1,
            )
            if (!isLive) Text(
                (if (deltaMs >= 0) "+" else "−") + fmtTime(abs(deltaMs)),
                color = DimInk, fontFamily = Mono, fontSize = fs, fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** The scrubber's touch band (see Scrubber): about a fingertip, centred on the 9–12 dp bar. */
private val RAIL_BAND = 38.dp

/**
 * The Apple TV scrubber (thick, fully rounded, brighter under focus) with the scrub preview on it.
 * A finger on the rail moves a ghost knob and the tip above it and seeks on release, as before.
 * On a remote, ←/→ on the focused rail move a preview position instead of seeking — 10 s steps,
 * 30 s after five quick presses (within 400 ms of each other), 60 s after ten — OK commits it,
 * Back cancels one layer (the preview, not the player), 4 s without a key cancels, ↑/↓ move focus
 * away and cancel. Live streams and unknown lengths keep the immediate ±10 s seek. Tap = seek.
 * [onScrub] reports the preview position (null when it ends) so the host can fetch frames and
 * keep the chrome awake.
 */
@Composable
internal fun Scrubber(
    positionMs: Long, durationMs: Long, bufferedMs: Long, isLive: Boolean, liveOffsetMs: Long,
    frame: State<Bitmap?>?,
    onSeekBy: (Long) -> Unit, onSeekTo: (Long) -> Unit, onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    stepMs: Long = 10_000L,          // Skip by (Settings › Playback): one press; ×3 after five quick ones, ×6 after ten
    kick: Pair<Int, Int>? = null,    // (direction, serial): a ←/→ that woke the hidden chrome — take focus, take that step
    onKickTaken: (Int) -> Unit = {},  // the serial it was given, so a stale one never clears a newer kick
) {
    val context = LocalContext.current
    val tv = remember { Account.isTv(context) }
    var trackWidth by remember { mutableStateOf(1) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }       // the remote's preview position
    var scrubRun by remember { mutableStateOf(0) }                 // quick presses in a row: the step grows
    var scrubLastAt by remember { mutableStateOf(0L) }
    var scrubTick by remember { mutableStateOf(0) }                // bumped per press: restarts the 4 s cancel
    var swallowBackUp by remember { mutableStateOf(false) }        // the Back that cancelled must not also leave
    val seekInteraction = remember { MutableInteractionSource() }
    val seekFocused by seekInteraction.collectIsFocusedAsState()
    // the gesture blocks below restart only when the length changes, so they must read the host's CURRENT
    // callbacks: after the in-place episode hop the old onScrub points at a frame reader already released
    val onScrubNow by rememberUpdatedState(onScrub)
    val onSeekToNow by rememberUpdatedState(onSeekTo)
    fun cancelScrub() { if (scrubMs != null) { scrubMs = null; onScrubNow(null) } }
    /** One ←/→ on the focused rail: move the preview (a live stream, or no length yet, seeks at once). */
    fun step(dir: Int) {
        val now = System.currentTimeMillis()
        scrubRun = if (now - scrubLastAt <= 400) scrubRun + 1 else 1
        scrubLastAt = now
        val step = when { scrubRun > 10 -> stepMs * 6; scrubRun > 5 -> stepMs * 3; else -> stepMs }
        // live, or no length yet: nothing to preview, so it seeks at once — with the same speed-up, so walking back
        // through a long live window is not 10 s a press
        if (isLive || durationMs <= 0) { onSeekBy(dir * step); return }
        val t = ((scrubMs ?: positionMs) + dir * step).coerceIn(0L, durationMs)
        scrubMs = t
        scrubTick++
        onScrubNow(t)
    }
    // The chrome woke on a ←/→ (the TV rule the web player follows: the preview moves, OK jumps, Back drops it).
    // The rail is composed with the chrome, which fades in — so retry across a few frames, and the step waits for
    // the focus: a preview on an unfocused rail would be cancelled by the focus effect above the moment it ran.
    val railFocus = remember { FocusRequester() }
    val onKickTakenNow by rememberUpdatedState(onKickTaken)
    LaunchedEffect(kick?.second) {
        val k = kick ?: return@LaunchedEffect
        try {
            repeat(10) {
                withFrameNanos {}
                if (runCatching { railFocus.requestFocus() }.getOrDefault(false)) { step(k.first); return@LaunchedEffect }
            }
        } finally { onKickTakenNow(k.second) }    // taken or not, once: a chrome shown again later must not replay it
    }
    LaunchedEffect(scrubMs != null, scrubTick) { if (scrubMs != null) { delay(4000); cancelScrub() } }
    LaunchedEffect(seekFocused) { if (!seekFocused) cancelScrub() }
    LaunchedEffect(durationMs) { cancelScrub() }     // a new item's length arrived: a remote preview of the old one is void
    BackHandler(enabled = scrubMs != null) { cancelScrub() }
    // the chrome fading away mid-preview is a cancel too: the host must not think a preview is still up
    DisposableEffect(Unit) { onDispose { onScrubNow(null) } }

    val frac = (dragFrac ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f).coerceIn(0f, 1f)
    val bufFrac = (if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f).coerceIn(0f, 1f)
    // the preview: under the finger, or where the remote moved the ghost
    val previewMs: Long? = dragFrac?.let { if (durationMs > 0) (it * durationMs).toLong() else null } ?: scrubMs
    val previewFrac = if (previewMs != null && durationMs > 0) (previewMs.toFloat() / durationMs).coerceIn(0f, 1f) else frac
    val active = previewMs != null

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(26.dp)
                // The finger's band is 38 dp centred on the bar; the chrome still lays out 26 dp and the bar draws where
                // it did. It used to be the 14 dp left under the top padding, so a thumb a little high fell through to
                // the swipe-to-seek layer behind. The overhang (12 dp below) sits under the time pills, which are
                // later siblings and so still take their own taps first.
                .layout { m, c ->
                    val p = m.measure(c.copy(minHeight = RAIL_BAND.roundToPx(), maxHeight = RAIL_BAND.roundToPx()))
                    layout(p.width, c.maxHeight) { p.place(0, 0) }
                }
                .onSizeChanged { trackWidth = it.width.coerceAtLeast(1) }
                .focusRequester(railFocus)
                .focusable(interactionSource = seekInteraction)
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp) {
                        if (swallowBackUp && (e.key == Key.Back || e.key == Key.Escape)) { swallowBackUp = false; return@onKeyEvent true }
                        return@onKeyEvent false
                    }
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    swallowBackUp = false           // a KeyUp that never came back here must not eat a later Back
                    when (e.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            step(if (e.key == Key.DirectionLeft) -1 else 1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            val t = scrubMs ?: return@onKeyEvent false
                            scrubMs = null
                            onScrubNow(null)
                            onSeekToNow(t)
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (scrubMs == null) return@onKeyEvent false
                            cancelScrub()
                            swallowBackUp = true
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> { cancelScrub(); false }
                        else -> false
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { off ->
                        cancelScrub()
                        if (durationMs > 0) onSeekToNow((off.x / trackWidth * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { off ->
                            cancelScrub()
                            val f = (off.x / trackWidth).coerceIn(0f, 1f)
                            dragFrac = f
                            if (durationMs > 0) onScrubNow((f * durationMs).toLong())
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val f = (change.position.x / trackWidth).coerceIn(0f, 1f)
                            dragFrac = f
                            if (durationMs > 0) onScrubNow((f * durationMs).toLong())
                        },
                        onDragEnd = {
                            dragFrac?.let { if (durationMs > 0) onSeekToNow((it * durationMs).toLong()) }
                            dragFrac = null
                            onScrubNow(null)
                        },
                        onDragCancel = { dragFrac = null; onScrubNow(null) },
                    )
                }
                .padding(top = 12.dp, bottom = 12.dp),       // the drawn rail: 14 dp, 12 dp down, as before
        ) {
            val h = if (seekFocused || active) 12.dp else 9.dp
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(h).background(Color(0x3DFFFFFF), Pill))
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(bufFrac).height(h).background(Color(0x61FFFFFF), Pill))
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(frac).height(h).background(Ink, Pill))
            // the ghost knob: where a commit would land
            if (active) Box(
                Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset((previewFrac * trackWidth - 7.dp.toPx()).roundToInt(), 0) }
                    .size(14.dp)
                    .background(Color(0xE6FFFFFF), CircleShape)
                    .border(1.dp, Color(0x33000000), CircleShape),
            )
        }
        // the tip floats above the rail at the preview x, clamped to the rail's width; it takes no
        // room in the column (reports 0×0) and draws over whatever sits above the scrubber
        if (previewMs != null) ScrubTip(
            targetMs = previewMs,
            deltaMs = previewMs - positionMs,
            isLive = isLive,
            liveBehindMs = (liveOffsetMs + positionMs - previewMs).coerceAtLeast(0L),
            frame = if (isLive) null else frame?.value,
            tv = tv,
            modifier = Modifier.layout { m, c ->
                val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                layout(0, 0) {
                    val x = (previewFrac * trackWidth - p.width / 2f).roundToInt()
                        .coerceIn(0, (trackWidth - p.width).coerceAtLeast(0))
                    p.place(x, -p.height - 8.dp.roundToPx())
                }
            },
        )
    }
}
