package com.nuvio.ckplayer

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

/**
 * The pull past the end of a list, as a pure sum: how far the content is drawn out for how far the finger has
 * pulled. It follows the finger less and less and never passes [limit] — the page can be drawn out, not off.
 */
internal object Rubber {
    const val GIVE = 0.55f

    fun shown(pull: Float, limit: Float): Float {
        if (pull == 0f || limit <= 0f) return 0f
        val p = abs(pull)
        return sign(pull) * (1f - 1f / (p * GIVE / limit + 1f)) * limit
    }

    /**
     * A finger's move against a page already pulled out: what is left of [delta] once it has paid the pull back
     * towards rest (a move the other way pays nothing here — it pulls further, after the list has had it).
     * Returns the pull after it; the part of the move that paid for it is `result - pull`.
     */
    fun payDown(pull: Float, delta: Float): Float {
        if (pull == 0f || delta == 0f || sign(pull) == sign(delta)) return pull
        return if (abs(delta) >= abs(pull)) 0f else pull + delta
    }
}

/**
 * A phone's lists give at their ends and spring back (11-12): pulled past its first or last row, a page comes with
 * the finger — less and less the further it goes — and is carried home on a spring when let go; a fling that runs
 * into an end spends what speed it has left as a short bounce. A TV never gets this (the remote does not pull),
 * nor does Motion: Reduced: both keep the platform's own edge.
 *
 * One of these per list, made by [RubberOverscrollFactory]. Each list scrolls one way, so only one of x and y is
 * ever away from zero.
 */
internal class RubberOverscroll : OverscrollEffect {
    private var pullX by mutableFloatStateOf(0f)
    private var pullY by mutableFloatStateOf(0f)
    private var limitX = 1f
    private var limitY = 1f
    /** Counts the finger's moves: a way home begun before the latest one gives way to it. */
    private var touch = 0

    override val isInProgress: Boolean get() = pullX != 0f || pullY != 0f

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        // a fling's own steps and a scroll made in code never pull the page out; a fling's end is applyToFling's
        if (source != NestedScrollSource.UserInput) {
            if (!isInProgress) return performScroll(delta)
            // still out while the list moves itself (a touch stopped the way home part-way): pay it back first
            val x = Rubber.payDown(pullX, delta.x); val y = Rubber.payDown(pullY, delta.y)
            val paid = Offset(x - pullX, y - pullY)
            pullX = x; pullY = y
            return paid + performScroll(delta - paid)
        }
        touch++
        // 1) a move back towards rest pays the pull down before the list sees any of it
        val x = Rubber.payDown(pullX, delta.x); val y = Rubber.payDown(pullY, delta.y)
        val paid = Offset(x - pullX, y - pullY)
        pullX = x; pullY = y
        val left = delta - paid
        // 2) the list takes what it can
        val used = performScroll(left)
        // 3) what nothing took pulls the page out
        val over = left - used
        if (abs(over.x) > 0.5f) pullX += over.x
        if (abs(over.y) > 0.5f) pullY += over.y
        return delta
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (isInProgress) {
            // let go while pulled out: no fling, the page goes home carrying the speed it was let go with
            home(velocity.x, velocity.y)
            return
        }
        val used = performFling(velocity)
        val left = velocity - used
        // the fling ran into an end with speed to spare: it carries a little past and comes back
        if (abs(left.x) > 1f || abs(left.y) > 1f) home(left.x, left.y)
    }

    private suspend fun home(vx: Float, vy: Float) {
        // a finger that takes the page again part-way stops this where it is; the page is the finger's from there
        val mine = touch
        if (abs(pullX) > 0f || abs(vx) > 1f) {
            AnimationState(pullX, vx.coerceIn(-MAX_SPEED, MAX_SPEED)).animateTo(0f, Home) {
                if (touch != mine) cancelAnimation() else pullX = value
            }
            if (touch == mine) pullX = 0f
        }
        if (abs(pullY) > 0f || abs(vy) > 1f) {
            AnimationState(pullY, vy.coerceIn(-MAX_SPEED, MAX_SPEED)).animateTo(0f, Home) {
                if (touch != mine) cancelAnimation() else pullY = value
            }
            if (touch == mine) pullY = 0f
        }
    }

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val p = measurable.measure(constraints)
            limitX = p.width.toFloat().coerceAtLeast(1f)
            limitY = p.height.toFloat().coerceAtLeast(1f)
            return layout(p.width, p.height) {
                // read here, in the layer: a pull moves the picture and lays out nothing
                p.placeWithLayer(0, 0) {
                    translationX = Rubber.shown(pullX, limitX)
                    translationY = Rubber.shown(pullY, limitY)
                }
            }
        }
    }

    private companion object {
        /** No bounce of its own: the page comes home and stops. About a third of a second. */
        val Home = spring<Float>(dampingRatio = 1f, stiffness = 247f, visibilityThreshold = 0.5f)
        const val MAX_SPEED = 9000f
    }
}

/** What AppRoot gives a phone's whole tree (LocalOverscrollFactory) in place of the platform's stretch. */
internal object RubberOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = RubberOverscroll()
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x52554242
}
