package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.ImageLoader

/**
 * The phone's motion (10-22, "fast but there is no flow to it like in Nuvio").
 *
 * Every screen change was the same 180 ms cross-fade whichever way the viewer went, a tap on a card did nothing you
 * could see until the next screen was up, and pictures appeared all at once when they arrived. Here: a screen opened
 * slides in from the side it came from and Back slides it away the other way, a tab change fades through, a pressed
 * card gives under the finger, pictures fade in, and rows ease into place as add-ons answer.
 *
 * A TV keeps its cuts: drawing two full screens for a transition's length is what a TV box cannot afford (1.87.0), and
 * the remote's moves already have the focus zoom. Motion: Reduced (Settings › Appearance) turns all of it off.
 */
internal object Motion {
    /** Material's emphasised deceleration: quick off the mark, a long soft landing. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Whether this device moves at all: a phone (or tablet) whose viewer has not asked for reduced motion. */
    fun on(ctx: Context): Boolean = !Account.isTv(ctx) && !Prefs.reducedMotion

    /** Pictures fade in as they arrive (a picture from the memory cache shows at once — Coil skips the fade there). */
    fun installImages(ctx: Context) {
        if (!on(ctx)) return
        Coil.setImageLoader(ImageLoader.Builder(ctx.applicationContext).crossfade(220).build())
    }

    /**
     * Which way the stack moved: +1 a screen pushed on top, −1 Back, 0 anything else (a tab, a screen replaced).
     * Noted in AppRoot's composition before the screen change reads it.
     */
    class Direction {
        private var last: List<Any>? = null
        var dir = 0
            private set

        fun note(stack: List<Any>) {
            val was = last
            if (was === stack) return
            dir = when {
                was == null -> 0
                stack.size > was.size && stack.subList(0, was.size) == was -> 1
                stack.size < was.size && was.subList(0, stack.size) == stack -> -1
                else -> 0
            }
            last = stack
        }
    }

    /** The screen change for one move of the stack; [player] = the player is arriving or leaving. */
    fun screens(dir: Int, moving: Boolean, player: Boolean): ContentTransform {
        if (!moving) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
        // the player is a picture, not a page: it fades up and away
        if (player) {
            return fadeIn(tween(240, easing = Decelerate)) togetherWith fadeOut(tween(160))
        }
        // a tab, or a screen replaced in place: fade through — the old page out first, the new one settles in
        if (dir == 0) {
            return (fadeIn(tween(220, delayMillis = 90, easing = Decelerate)) +
                scaleIn(tween(320, delayMillis = 90, easing = Decelerate), initialScale = 0.96f)) togetherWith
                fadeOut(tween(90, easing = Accelerate))
        }
        // forward and Back along one axis: the page that arrives comes from the side it lives on and the page that
        // leaves goes a little the other way, so the viewer can feel where they are in the stack
        val enterFrom: (Int) -> Int = { w -> (w * 0.12f).toInt() * dir }
        val leaveTo: (Int) -> Int = { w -> -(w * 0.06f).toInt() * dir }
        return (slideInHorizontally(tween(360, easing = Decelerate), enterFrom) +
            fadeIn(tween(250, delayMillis = 60, easing = Decelerate))) togetherWith
            (slideOutHorizontally(tween(360, easing = Decelerate), leaveTo) + fadeOut(tween(110, easing = Accelerate)))
    }
}

/** A card's give under a finger: a little smaller while pressed, springing back on release. 1 when off. */
@Composable
internal fun pressScale(interaction: InteractionSource): State<Float> {
    val ctx = LocalContext.current
    val on = remember(ctx) { !Account.isTv(ctx) } && !Prefs.reducedMotion
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        if (on && pressed) 0.955f else 1f,
        spring(dampingRatio = 0.6f, stiffness = if (pressed) Spring.StiffnessMedium else Spring.StiffnessMediumLow),
        label = "press",
    )
}
