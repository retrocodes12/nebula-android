package com.nuvio.ckplayer

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * `MediaMetadataRetriever` opens the URL once (presenting the headers the add-on requests carry)
 * and pulls one small frame per 10-second bucket, on the IO dispatcher, one at a time — the latest
 * request wins and everything asked for in between is dropped. Frames live in a 60-entry LRU;
 * `release()` on player dispose closes the retriever. Only plain http(s) files qualify
 * (`eligible`): manifests, encrypted and live streams get the bubble with no picture. Nothing
 * here ever touches the player.
 */
internal class ScrubPreview(private val url: String) {
    companion object {
        const val BUCKET_MS = 10_000L
        private const val CAP = 60
        private const val NEAR = 6          // a cached frame within a minute stands in until the right one lands

        /** What Stremio.kt sends on add-on requests; the retriever shows the same face. */
        val HEADERS = mapOf("User-Agent" to "NebulaPlayer", "X-Nebula-Client" to "android")

        /** A plain http(s) file — not a manifest (which also rules out ClearKey: keys ride on .mpd here). */
        fun eligible(url: String): Boolean {
            val u = url.trim()
            if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) return false
            if (Regex("\\.mpd(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)) return false
            if (Regex("\\.m3u8", RegexOption.IGNORE_CASE).containsMatchIn(u)) return false
            return true
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
    private var running = false                             // a worker is alive (under lock)
    @Volatile private var wantedMs = -1L                    // where the finger / ghost is now; -1 = nothing wanted
    @Volatile private var dead = false                      // unreadable file, or released: stay quiet for good
    private val frameState = mutableStateOf<Bitmap?>(null)

    /** The frame for the last requested position — its own bucket, or a neighbour while that one loads. */
    val frame: State<Bitmap?> get() = frameState

    /** Main thread. Point the preview at [posMs]: the tip gets the best frame at once, the exact one later. */
    fun request(posMs: Long) {
        if (dead) return
        val ms = posMs.coerceAtLeast(0L)
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

    /** Main thread. Player dispose: no more fetches; the retriever closes once any fetch in flight ends. */
    fun release() {
        dead = true
        wantedMs = -1L
        val closeNow: Boolean
        synchronized(lock) { closeNow = !running; cache.clear(); misses.clear() }
        frameState.value = null
        if (closeNow) io.launch { closeRetriever() }
    }

    /** Under the lock: the frame for bucket [b], else the nearest one within a minute, else null. */
    private fun best(b: Long): Bitmap? {
        cache[b]?.let { return it }
        var pick: Bitmap? = null
        var dist = NEAR + 1
        for ((k, v) in cache) {
            val d = abs(k - b).toInt()
            if (d < dist) { dist = d; pick = v }
        }
        return pick
    }

    /** Under the lock: what to fetch next, or null when there is nothing to do — the worker then exits. */
    private fun nextWant(): Long? = synchronized(lock) {
        val want = wantedMs
        val b = want / BUCKET_MS
        if (dead || want < 0 || cache.containsKey(b) || b in misses) { running = false; null } else want
    }

    // The single worker: drains "wanted" until it is cached or gone. Exiting and the nothing-to-do
    // decision happen under one lock, so a request arriving as it leaves starts a fresh worker.
    private suspend fun work() {
        while (true) {
            val want = nextWant() ?: break
            val b = want / BUCKET_MS
            val bmp = fetch(want)
            synchronized(lock) {
                if (bmp != null) { cache[b] = bmp; hits++ }
                else {
                    misses.add(b)
                    // it opens but nothing decodes (a codec the retriever lacks): stop asking, each try costs a download
                    if (hits == 0 && misses.size >= 3) dead = true
                }
            }
            val show = wantedMs
            if (show >= 0 && !dead) {
                val pick = synchronized(lock) { best(show / BUCKET_MS) }
                withContext(Dispatchers.Main) { if (!dead) frameState.value = pick }
            }
        }
        if (dead) closeRetriever()
    }

    /** Worker thread. One frame near [posMs], scaled for the tip; null when the file has none there. */
    private fun fetch(posMs: Long): Bitmap? {
        val r = retriever ?: run {
            val m = MediaMetadataRetriever()
            try {
                m.setDataSource(url, HEADERS)
            } catch (e: Exception) {
                runCatching { m.release() }
                dead = true                     // a second reader cannot open it: bubble only, no retry storm
                return null
            }
            retriever = m
            m
        }
        return try {
            val us = posMs * 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 144)
            } else {
                val full = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
                // fit inside 256×144 whatever the picture's shape, as getScaledFrameAtTime does above
                val k = minOf(256f / full.width.coerceAtLeast(1), 144f / full.height.coerceAtLeast(1))
                val w = (full.width * k).toInt().coerceAtLeast(1)
                val h = (full.height * k).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(full, w, h, true).also { if (it !== full) full.recycle() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun closeRetriever() {
        val r = synchronized(lock) { val x = retriever; retriever = null; x }
        runCatching { r?.release() }
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
            Modifier.fillMaxWidth().height(26.dp).padding(top = 12.dp)
                .onSizeChanged { trackWidth = it.width.coerceAtLeast(1) }
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
                            val dir = if (e.key == Key.DirectionLeft) -1 else 1
                            // live, or a length not known yet: nothing to preview, so seek at once as before
                            if (isLive || durationMs <= 0) { onSeekBy(dir * 10_000L); return@onKeyEvent true }
                            val now = System.currentTimeMillis()
                            scrubRun = if (now - scrubLastAt <= 400) scrubRun + 1 else 1
                            scrubLastAt = now
                            val step = when { scrubRun > 10 -> 60_000L; scrubRun > 5 -> 30_000L; else -> 10_000L }
                            val t = ((scrubMs ?: positionMs) + dir * step).coerceIn(0L, durationMs)
                            scrubMs = t
                            scrubTick++
                            onScrubNow(t)
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
                },
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
