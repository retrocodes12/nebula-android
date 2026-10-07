package com.nuvio.ckplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.KeyEvent as AKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The TV Guide screen (Guide.kt holds the rules and the scan). Spec sizes are for the web's 1920-px TV page; a 1080p
 * Android TV is 960 dp wide and this app's TV type runs larger than the web's, so: label column 120 dp (web 220 px),
 * one hour 180 dp (300 px), a lane 62 dp with a 56 dp block (92 / 80 px).
 */
private val LABEL_W = 120.dp
private val HOUR_W = 180.dp
private val LANE_H = 62.dp
private val BLOCK_H = 56.dp
private val RULER_H = 30.dp
private val BLOCK_GAP = 4.dp

private val MOVE_KEYS = setOf(
    AKey.KEYCODE_DPAD_LEFT, AKey.KEYCODE_DPAD_RIGHT, AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_DPAD_DOWN,
    AKey.KEYCODE_CHANNEL_UP, AKey.KEYCODE_CHANNEL_DOWN, AKey.KEYCODE_PAGE_UP, AKey.KEYCODE_PAGE_DOWN,
)

@Composable
internal fun GuideScreen(onOpen: (Addon, MetaItem) -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // the now line and the clock move every minute; the data comes from Guide (AppRoot refreshes it every 5 min)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(GuidePlan.MIN - t % GuidePlan.MIN + 50)
            now = System.currentTimeMillis()
            Guide.recheck()
        }
    }
    val minute = now / GuidePlan.MIN
    val items = Guide.items
    val lanes = remember(items, minute) { GuidePlan.lanes(items, now) }
    // the left edge holds for the visit (re-flooring every half hour would slide the whole grid under the remote)
    val winStart = remember { GuidePlan.windowStart(System.currentTimeMillis()) }
    val winEnd = remember(lanes) { GuidePlan.windowEnd(lanes, winStart, now) }

    val hourPx = with(density) { HOUR_W.toPx() }
    val laneHpx = with(density) { LANE_H.toPx() }
    val pxPerMs = hourPx / GuidePlan.HOUR
    fun xOf(t: Long): Float = (maxOf(t, winStart) - winStart) * pxPerMs
    fun dp(px: Float): Dp = with(density) { px.toDp() }
    val totalWpx = xOf(winEnd) + with(density) { 48.dp.toPx() }
    val totalHpx = lanes.size * laneHpx

    var viewW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }
    val sx = remember { Animatable(0f) }
    val sy = remember { Animatable(0f) }
    fun maxSx() = maxOf(0f, totalWpx - viewW)
    fun maxSy() = maxOf(0f, totalHpx + with(density) { 16.dp.toPx() } - viewH)

    // where each block is, for the remote's moves
    val pos = remember(lanes) {
        HashMap<String, Pair<Int, Int>>().also { m -> lanes.forEachIndexed { li, l -> l.blocks.forEachIndexed { bi, b -> m[b.id] = li to bi } } }
    }
    val reqs = remember { HashMap<String, FocusRequester>() }
    fun reqFor(id: String) = reqs.getOrPut(id) { FocusRequester() }
    var focusedId by remember { mutableStateOf<String?>(null) }
    // how the next focus scrolls: a sideways move keeps ~1 h of context; Up/Down and landings scroll only when needed
    val soft = remember { BooleanArray(1) { true } }

    fun reveal(b: GuideBlock, li: Int, gentle: Boolean) {
        if (viewW <= 0) return
        val bx = xOf(b.start)
        val bw = xOf(b.end) - bx
        val edge = with(density) { 40.dp.toPx() }
        var tx = sx.targetValue
        val visible = bx + bw > tx + edge && bx < tx + viewW - edge
        if (!gentle || !visible) {
            if (bx < tx + hourPx) tx = bx - hourPx
            else if (bx + bw > tx + viewW) tx = minOf(bx - hourPx, bx + bw - viewW + edge)
        }
        tx = tx.coerceIn(0f, maxSx())
        val ly = li * laneHpx
        var ty = sy.targetValue
        if (ly < ty) ty = ly else if (ly + laneHpx > ty + viewH) ty = ly + laneHpx - viewH
        ty = ty.coerceIn(0f, maxSy())
        val ms = if (Prefs.reducedMotion) 0 else 160
        scope.launch { if (ms == 0) sx.snapTo(tx) else sx.animateTo(tx, tween(ms)) }
        scope.launch { if (ms == 0) sy.snapTo(ty) else sy.animateTo(ty, tween(ms)) }
    }

    // on open: the now line about an hour in from the label column
    LaunchedEffect(viewW > 0) {
        if (viewW > 0 && focusedId == null) sx.snapTo((xOf(now) - hourPx).coerceIn(0f, maxSx()))
    }
    // the landing: the first live block (top-most lane), else the earliest upcoming one; Back's hand-back wins over it
    val landing = remember(lanes.isNotEmpty()) { GuidePlan.landing(lanes, now) }
    tvFirstFocus(ready = landing != null && viewW > 0, target = landing?.let { reqFor(it.id) })
    // a refresh that dropped the lit block (it ended): land again, as on open
    LaunchedEffect(lanes) {
        val f = focusedId ?: return@LaunchedEffect
        if (pos.containsKey(f)) return@LaunchedEffect
        val to = GuidePlan.landing(lanes, now) ?: return@LaunchedEffect
        withFrameNanos {}
        soft[0] = true
        runCatching { reqFor(to.id).requestFocus() }
    }

    fun go(to: GuideBlock?, gentle: Boolean) {
        if (to == null) return
        soft[0] = gentle
        runCatching { reqFor(to.id).requestFocus() }
    }

    fun onKey(code: Int): Boolean {
        val f = focusedId ?: return false
        val (li, bi) = pos[f] ?: return false
        val lane = lanes[li]
        val b = lane.blocks[bi]
        when (code) {
            AKey.KEYCODE_DPAD_LEFT -> if (bi > 0) go(lane.blocks[bi - 1], false) else RailFocus.enter?.invoke()
            AKey.KEYCODE_DPAD_RIGHT -> if (bi < lane.blocks.size - 1) go(lane.blocks[bi + 1], false)
            else -> {
                val n = when (code) {
                    AKey.KEYCODE_DPAD_UP -> -1
                    AKey.KEYCODE_DPAD_DOWN -> 1
                    else -> {
                        val page = maxOf(1, (viewH / laneHpx).toInt() - 1)
                        if (code == AKey.KEYCODE_CHANNEL_UP || code == AKey.KEYCODE_PAGE_UP) -page else page
                    }
                }
                val to = (li + n).coerceIn(0, lanes.size - 1)
                if (to != li) {
                    val leftInView = winStart + (sx.value / pxPerMs).toLong()
                    go(GuidePlan.pickInLane(lanes[to], maxOf(b.start, leftInView), b.end), true)
                }
            }
        }
        return true
    }

    val dayFmt = remember { SimpleDateFormat("EEEE", Locale.US) }
    Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 24.dp)) {
        Text(
            "Guide", color = TextC, fontSize = 34.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        )
        Text(
            dayFmt.format(Date(now)) + " · " + clockAt(ctx, now), color = MutedC, fontSize = 14.sp, fontFamily = Sans,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
        )
        if (lanes.isEmpty()) {
            Text("Nothing on the schedule right now.", color = MutedC, fontSize = 15.sp, fontFamily = Sans,
                modifier = Modifier.padding(top = 40.dp))
            return@Column
        }
        val nowX = xOf(now)
        val contentHpx = maxOf(totalHpx + with(density) { 16.dp.toPx() }, viewH.toFloat())
        // the time ruler: a tick and a time every half hour, a "Now" marker
        Row(Modifier.fillMaxWidth().height(RULER_H)) {
            Spacer(Modifier.width(LABEL_W))
            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                Box(
                    Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset { IntOffset(-sx.value.roundToInt(), 0) }
                        .size(dp(totalWpx), RULER_H),
                ) {
                    var t = winStart
                    while (t <= winEnd) {
                        val x = dp(xOf(t))
                        Box(Modifier.offset(x, RULER_H - 7.dp).size(1.dp, 7.dp).background(Line2))
                        Text(
                            clockAt(ctx, t), color = MutedC, fontFamily = Mono, fontSize = 11.sp, maxLines = 1,
                            modifier = Modifier.offset(x + 5.dp, 4.dp),
                        )
                        t += GuidePlan.HALF
                    }
                    Box(Modifier.offset(dp(nowX) - 1.dp, 0.dp).size(2.dp, RULER_H).background(Red))
                    Text(
                        "Now", color = OnAccent, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.offset(dp(nowX) + 3.dp, 3.dp)
                            .background(Red, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxSize()) {
            // the label column: sticky sideways, scrolls with the lanes
            Box(Modifier.width(LABEL_W).fillMaxHeight().clipToBounds()) {
                Box(
                    Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset { IntOffset(0, -sy.value.roundToInt()) }
                        .size(LABEL_W, dp(contentHpx)),
                ) {
                    lanes.forEachIndexed { li, l ->
                        if (!l.first) return@forEachIndexed
                        val y = dp(li * laneHpx)
                        if (li > 0) Box(Modifier.offset(0.dp, y).size(LABEL_W, 1.dp).background(LineC))
                        Box(Modifier.offset(0.dp, y).size(LABEL_W, LANE_H).padding(end = 10.dp), contentAlignment = Alignment.CenterStart) {
                            Text(l.label, color = TextC, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                        }
                    }
                }
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().clipToBounds()
                    .onSizeChanged { viewW = it.width; viewH = it.height }
                    .onPreviewKeyEvent { ev ->
                        val code = ev.nativeKeyEvent.keyCode
                        if (code !in MOVE_KEYS) false
                        else if (ev.type != KeyEventType.KeyDown) focusedId?.let { pos.containsKey(it) } == true
                        else onKey(code)
                    },
            ) {
                Box(
                    Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset { IntOffset(-sx.value.roundToInt(), -sy.value.roundToInt()) }
                        .size(dp(totalWpx), dp(contentHpx)),
                ) {
                    // a hairline between sports
                    lanes.forEachIndexed { li, l ->
                        if (l.first && li > 0) Box(Modifier.offset(0.dp, dp(li * laneHpx)).size(dp(totalWpx), 1.dp).background(LineC))
                    }
                    // the now line: under the lit block's ring, over the rest
                    Box(Modifier.zIndex(1f).offset(dp(nowX) - 1.dp, 0.dp).size(2.dp, dp(contentHpx)).background(Red))
                    lanes.forEachIndexed { li, l ->
                        l.blocks.forEach { b ->
                            key(b.id) {
                                val x = xOf(b.start)
                                val w = maxOf(xOf(b.end) - x - with(density) { BLOCK_GAP.toPx() }, with(density) { 24.dp.toPx() })
                                GuideTile(
                                    b, dp(x), dp(li * laneHpx) + (LANE_H - BLOCK_H) / 2, dp(w), reqFor(b.id),
                                    lit = focusedId == b.id,
                                    onFocus = {
                                        focusedId = b.id
                                        val gentle = soft[0]; soft[0] = true
                                        pos[b.id]?.let { (bli, _) -> reveal(b, bli, gentle) }
                                    },
                                    onOpen = { onOpen(b.item.src.addon, b.item.entry.meta) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideTile(
    b: GuideBlock, x: Dp, y: Dp, w: Dp, req: FocusRequester, lit: Boolean,
    onFocus: () -> Unit, onOpen: () -> Unit,
) {
    val ctx = LocalContext.current
    val shape = cardShape()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // FocusCard's ring path (zoom off): a white ring, no growth, no shadow; Play on a lit block = OK (BrowseKeys)
    FocusCard(
        shape,
        modifier = Modifier.zIndex(if (lit || focused) 2f else 0f).offset(x, y).size(w, BLOCK_H)
            .focusRequester(req)
            .returnTo("guide/" + b.id)
            .onFocusChanged { if (it.hasFocus) onFocus() },
        onClick = onOpen,
        zoom = false,
        interactionSource = interaction,
        landing = false,
    ) {
        Box(Modifier.fillMaxSize().background(SurfaceC)) {
            if (b.live) Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(3.dp).background(Red))
            Column(Modifier.fillMaxSize().padding(start = if (b.live) 11.dp else 9.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
                Text(
                    b.item.entry.meta.name, color = TextC, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp,
                    lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (b.live) Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Red)) { append("● ") }
                        append("Live")
                    },
                    color = TextC, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1,
                )
                else Text(
                    clockAt(ctx, b.start) + " – " + clockAt(ctx, b.end), color = MutedC, fontFamily = Mono, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
