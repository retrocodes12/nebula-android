package com.nuvio.ckplayer

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The phone's nav as liquid glass (11-12, "bitchord like motion style and fluid dynamics").
 *
 * Two things live here. The selection pill: one lit shape that belongs to the bar, not to a tab — a tap lifts it out
 * of the bar as a glass bead, carries it to the tab on a spring, bends it with its own acceleration and sets it down
 * flat again; a finger can pick it up and carry it along the tabs (PillMotion.kt is the physics, this is the clock
 * and the drawing). And the bar's own glass: on Android 12 and later what is behind the bar shows through it,
 * frosted — the page is recorded once as it is drawn and the bar draws that recording again, blurred.
 *
 * Everything that moves is read while drawing or laying out, never in composition: a travelling pill recomposes
 * nothing. A TV keeps its rail and Motion: Reduced keeps the pill a plain cut — both never come through here moving.
 */
@Stable
internal class LiquidPill(private val count: Int, start: Int) {
    private val motion = PillMotion(count, start.coerceIn(0, count - 1))

    var pos by mutableFloatStateOf(motion.pos); private set
    var liftX by mutableFloatStateOf(0f); private set
    var liftY by mutableFloatStateOf(0f); private set
    var glass by mutableFloatStateOf(0f); private set
    var deviation by mutableFloatStateOf(0f); private set
    var presence by mutableFloatStateOf(0f); private set

    /** False when the screen on show is none of the pill's tabs: nothing is lit. */
    var shown by mutableStateOf(start >= 0); private set

    // where the tabs are, in the row's own pixels; [geometry] is what a drawing reads to hear of a change
    private val lefts = FloatArray(count)
    private val widths = FloatArray(count)
    var cellTop = 0f; private set
    var cellH = 0f; private set
    var geometry by mutableIntStateOf(0); private set

    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun placed(i: Int, x: Float, y: Float, w: Float, h: Float) {
        if (i !in 0 until count) return
        if (lefts[i] == x && widths[i] == w && (i != 0 || (cellTop == y && cellH == h))) return
        lefts[i] = x; widths[i] = w
        if (i == 0) { cellTop = y; cellH = h }
        geometry++
    }

    private fun centerOf(i: Int) = lefts[i] + widths[i] / 2f

    /** The pill's middle at a position in tab steps; past an end it runs on at the last pitch. */
    fun centerAt(p: Float): Float {
        if (count < 2) return centerOf(0)
        val i = floor(p).toInt().coerceIn(0, count - 2)
        return centerOf(i) + (centerOf(i + 1) - centerOf(i)) * (p - i)
    }

    /** The tab's own width at a position: the names differ in length, so the pill changes width as it goes. */
    fun widthAt(p: Float): Float {
        if (count < 2) return widths[0]
        val i = floor(p).toInt().coerceIn(0, count - 2)
        return widths[i] + (widths[i + 1] - widths[i]) * (p - i).coerceIn(0f, 1f)
    }

    /** A finger's x in the row, as a position in tab steps (it may be past either end). */
    private fun fracAt(x: Float): Float {
        if (count < 2) return 0f
        var i = 0
        while (i < count - 2 && x > centerOf(i + 1)) i++
        val a = centerOf(i); val b = centerOf(i + 1)
        return if (b - a <= 0f) i.toFloat() else i + (x - a) / (b - a)
    }

    /** How lit a tab is: 1 under the pill, 0 a whole tab away. Read while drawing. */
    fun lit(i: Int): Float = if (!shown) 0f else (1f - abs(pos - i)).coerceIn(0f, 1f)

    /** The screen on show changed (or was never one of the tabs). [moves] false = Motion: Reduced. */
    fun show(index: Int, moves: Boolean) {
        val was = shown
        shown = index >= 0
        if (index < 0) return
        if (moves && was) { motion.animateTo(index); wake.trySend(Unit) } else { motion.snapTo(index); publish() }
    }

    fun grab(x: Float) { motion.grab(fracAt(x)); wake.trySend(Unit) }
    fun dragTo(x: Float) = motion.dragTo(fracAt(x))
    fun release(): Int { val next = motion.release(); wake.trySend(Unit); return next }

    private fun publish() {
        pos = motion.pos; liftX = motion.liftX; liftY = motion.liftY
        glass = motion.glass; deviation = motion.deviation; presence = motion.presence
    }

    /**
     * The pill's clock: asleep until something moves it, then one step a frame until it is at rest again.
     * [tick] is called as a held pill crosses from one tab to the next.
     */
    suspend fun run(density: Float, tick: () -> Unit) {
        while (true) {
            wake.receive()
            var t0 = -1L
            var last = 0L
            var cell = motion.pos.roundToInt()
            var busy = true
            while (busy) {
                withFrameNanos { t ->
                    if (t0 < 0L) { t0 = t; last = t - 16_666_667L }
                    val dt = ((t - last) / 1e9f).coerceIn(0f, 0.064f)
                    last = t
                    val pitch = if (count < 2) 0f else (centerOf(count - 1) - centerOf(0)) / (count - 1) / density
                    busy = motion.step(dt, (t - t0) / 1e9f, pitch)
                    publish()
                    val now = motion.pos.roundToInt()
                    if (motion.held && now != cell) tick()
                    cell = now
                }
            }
        }
    }
}

/** How much taller than the bar the pill stands while it is lifted. */
private val PillGrow = 9.dp
/** The bar's own padding above and below its tabs (BottomBar). */
internal val NavPadV = 7.dp
internal val NavPadH = 8.dp
internal val NavCorner = 34.dp
private val PillCorner = 26.dp

/** A tab reports where it sits in the row (put it first on the tab, so it is the tab's whole box). */
internal fun Modifier.liquidTab(pill: LiquidPill?, i: Int): Modifier = if (pill == null) this else
    onGloballyPositioned { c ->
        val at = c.positionInParent()
        pill.placed(i, at.x, at.y, c.size.width.toFloat(), c.size.height.toFloat())
    }

/** A finger along the bar picks the pill up, carries it, and sets it down on the nearest tab ([onSettle]). */
internal fun Modifier.liquidDrag(pill: LiquidPill, enabled: Boolean, onSettle: (Int) -> Unit): Modifier =
    if (!enabled) this else pointerInput(pill) {
        detectHorizontalDragGestures(
            onDragStart = { at -> pill.grab(at.x) },
            onDragEnd = { onSettle(pill.release()) },
            onDragCancel = { onSettle(pill.release()) },
        ) { change, _ -> change.consume(); pill.dragTo(change.position.x) }
    }

/**
 * The pill, drawn under the tabs. It goes after the row's padding, so it draws in the same box the tabs are placed
 * in. Lifted, it is taller than the bar and stands out of it — nothing here clips.
 */
@Composable
internal fun Modifier.liquidPill(pill: LiquidPill): Modifier {
    val outline = remember { Path() }
    return drawBehind {
        pill.geometry                       // the fold moves the tabs: draw again
        if (!pill.shown) return@drawBehind
        val p = pill.pos
        val restW = pill.widthAt(p)
        val restH = pill.cellH
        if (restW <= 0f || restH <= 0f) return@drawBehind
        val upH = restH + 2 * NavPadV.toPx() + PillGrow.toPx()
        val upW = upH * (restW / restH)
        val g = pill.glass
        val dev = pill.deviation
        val w = (restW + (upW - restW) * pill.liftX) * (1f + dev)
        val h = (restH + (upH - restH) * pill.liftY) * (1f - dev)
        if (w <= 1f || h <= 1f) return@drawBehind
        val left = pill.centerAt(p) - w / 2f
        val top = pill.cellTop + restH / 2f - h / 2f
        val half = min(w, h) / 2f
        val restR = min(PillCorner.toPx(), half)
        val r = restR + (half - restR) * g              // a bead once it is glass
        val corner = CornerRadius(r)
        val at = Offset(left, top)
        val box = Size(w, h)
        // the rim and the shadow go first on the way down and are gone while the body is still settling
        val rim = ((pill.presence - 0.45f) / 0.55f).coerceIn(0f, 1f) * g
        if (rim > 0.01f) {
            // what the bead stands on: a soft dark ring, outside it only — the body is see-through
            outline.reset()
            outline.addRoundRect(RoundRect(left, top, left + w, top + h, corner))
            clipPath(outline, ClipOp.Difference) {
                val drop = 3.dp.toPx()
                for (k in 1..6) {
                    val out = k * 1.6f.dp.toPx()
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.075f * rim * (1f - k / 7f)),
                        Offset(left - out, top - out + drop), Size(w + 2 * out, h + 2 * out), CornerRadius(r + out),
                    )
                }
            }
        }
        drawRoundRect(Color.White.copy(alpha = 0.13f - 0.04f * g), at, box, corner)
        if (g > 0.01f) {
            // lit from above: brightest at its top edge, a little light gathered again at the bottom
            drawRoundRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.17f * g),
                    0.55f to Color.White.copy(alpha = 0.02f * g),
                    1f to Color.White.copy(alpha = 0.08f * g),
                    startY = top, endY = top + h,
                ), at, box, corner,
            )
        }
        if (rim > 0.01f) {
            drawRoundRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.75f * rim),
                    0.45f to Color.White.copy(alpha = 0.10f * rim),
                    1f to Color.White.copy(alpha = 0.42f * rim),
                    start = at, end = Offset(left + w, top + h),
                ), at, box, corner, style = Stroke(1.dp.toPx()),
            )
        }
    }
}

/**
 * The page as it was last drawn, kept so the nav's glass can draw it again behind itself. AppRoot records it
 * ([pageBackdrop]) and hands it to BottomBar; null where there is no glass (before Android 12, on a TV).
 */
internal class PageBackdrop(val layer: GraphicsLayer) {
    var coords: LayoutCoordinates? = null
}

@Composable
internal fun rememberPageBackdrop(on: Boolean): PageBackdrop? {
    if (!on || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val layer = rememberGraphicsLayer()
    return remember(layer) { PageBackdrop(layer) }
}

/**
 * Records what this draws while [on] and draws it as it always did. Off for the player and every other full-bleed
 * screen: the nav is not over them, and a video surface is not something to draw twice.
 */
internal fun Modifier.pageBackdrop(b: PageBackdrop?, on: Boolean): Modifier = if (b == null) this else this
    .onGloballyPositioned { b.coords = it }
    .drawWithContent {
        if (on) {
            b.layer.record { this@drawWithContent.drawContent() }
            drawLayer(b.layer)
        } else drawContent()
    }

/** Frost: what is behind, blurred, its colours a little stronger so a poster still reads as a colour. */
@RequiresApi(Build.VERSION_CODES.S)
private fun frost(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
    val vivid = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.45f) })
    return android.graphics.RenderEffect.createColorFilterEffect(vivid, blur).asComposeRenderEffect()
}

/**
 * The frost itself: the recorded page, drawn where the bar is into a layer of its own under a blur — and made again
 * whenever the page may have changed.
 *
 * Two things Android does not do for a blurred picture, both found on emulators (11-12), are done here by hand.
 * It does not make the picture again because something it only refers to changed: so just before each frame the app
 * is drawing anyway, this asks to be drawn again (a still screen draws nothing, so a still bar costs nothing). And
 * on Android 12 it keeps the blurred result for as long as the blur is the same object, whatever was drawn into it
 * since — the bar went on glowing with Home's posters over Library and Profile: so each drawing hands the layer the
 * other of two identical blurs.
 */
private class FrostElement(val backdrop: PageBackdrop, val blurPx: Float, val at: () -> Offset) : ModifierNodeElement<FrostNode>() {
    override fun create() = FrostNode(backdrop, blurPx, at)
    override fun update(node: FrostNode) { node.backdrop = backdrop; node.at = at; node.blur(blurPx); node.invalidateDraw() }
    override fun equals(other: Any?) = other is FrostElement && other.backdrop === backdrop && other.blurPx == blurPx && other.at === at
    override fun hashCode() = System.identityHashCode(backdrop)
}

@RequiresApi(Build.VERSION_CODES.S)
private class FrostNode(var backdrop: PageBackdrop, blurPx: Float, var at: () -> Offset) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var host: View? = null
    private var layer: GraphicsLayer? = null
    private var blurPx = blurPx
    private var blurs = arrayOf(frost(blurPx), frost(blurPx))
    private var turn = 0

    fun blur(px: Float) {
        if (px == blurPx) return
        blurPx = px; blurs = arrayOf(frost(px), frost(px))
    }

    private val beforeDraw = ViewTreeObserver.OnPreDrawListener {
        // only on a frame the app is drawing in (its view, or the parent a moved layer reports to, is marked):
        // asking on every frame would itself be a reason to draw the next one, for ever
        val v = host
        if (v != null && (v.isDirty || (v.parent as? View)?.isDirty == true)) invalidateDraw()
        true
    }

    override fun onAttach() {
        host = currentValueOf(LocalView).also { it.viewTreeObserver.addOnPreDrawListener(beforeDraw) }
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        host?.viewTreeObserver?.let { if (it.isAlive) it.removeOnPreDrawListener(beforeDraw) }
        host = null
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun ContentDrawScope.draw() {
        val l = layer
        if (l != null && size.width >= 1f && size.height >= 1f) {
            turn = turn xor 1
            l.renderEffect = blurs[turn]
            val o = at()
            l.record {
                drawRect(Bg)                // a page that paints no ground of its own still frosts to the app's
                translate(-o.x, -o.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(l)
        }
        drawContent()
    }
}

/**
 * The bar's body. With a [backdrop]: the page behind it, frosted, under a dark tint and a lit rim. Without one
 * (before Android 12): the solid plate the bar always was.
 */
@Composable
internal fun GlassPlate(backdrop: PageBackdrop?, corner: Dp, modifier: Modifier = Modifier) {
    val shape = remember(corner) { RoundedCornerShape(corner) }
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Box(modifier.background(Color(0xF0141419), shape).border(1.dp, Color(0x1FFFFFFF), shape))
        return
    }
    val blurPx = with(LocalDensity.current) { 18.dp.toPx() }
    // where the frost is on the page; it moves as the bar slides in and out
    var at by remember { mutableStateOf(Offset.Zero) }
    // the largest the bar has been: the frost is that size and stays that size
    val most = remember { IntArray(2) }
    // the bar's outline cuts the frost to shape — an outline only, so the bar changing size costs nothing here
    Box(modifier.graphicsLayer { clip = true; this.shape = shape }) {
        Box(
            Modifier.matchParentSize()
                // A blurred layer is a picture kept off screen, and one that changed size on every frame of a fold
                // would be a new picture on every frame. So the frost (FrostNode) never changes size: it is as big
                // as the bar at its widest, sits on the bar's bottom middle (the point a fold closes on), and the
                // outline above shows as much of it as the bar covers. While the bar folds it does not move.
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth; val h = constraints.maxHeight
                    if (w > most[0]) most[0] = w
                    if (h > most[1]) most[1] = h
                    val p = measurable.measure(Constraints.fixed(most[0], most[1]))
                    layout(w, h) { p.place((w - most[0]) / 2, h - most[1]) }
                }
                .onGloballyPositioned { c ->
                    val page = backdrop.coords
                    if (page != null && page.isAttached) at = page.localPositionOf(c, Offset.Zero)
                }
                .then(FrostElement(backdrop, blurPx) { at })
        )
        Box(
            Modifier.matchParentSize().drawBehind {
                val r = CornerRadius(corner.toPx())
                drawRoundRect(Color(0xFF141419).copy(alpha = 0.60f), cornerRadius = r)
                // the rim catches the light along the top and, fainter, along the bottom
                val w = 1.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.30f),
                        0.5f to Color.White.copy(alpha = 0.07f),
                        1f to Color.White.copy(alpha = 0.16f),
                    ),
                    topLeft = Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(r.x - w / 2f), style = Stroke(w),
                )
            }
        )
    }
}
