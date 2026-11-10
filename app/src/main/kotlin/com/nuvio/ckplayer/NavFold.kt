package com.nuvio.ckplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The phone's nav pill folds down to its icons while a page is being read and opens again on the way back up.
 *
 * This is only the decision — which way the page has been going, and for how far. It is fed every step of a
 * scroll (AppRoot's nested-scroll connection) and answers whether the pill is folded; the drawing is BottomBar's.
 *
 * Two distances rather than one, and a longer one to fold than to open: a thumb resting on the glass wobbles a
 * few pixels each way, and a pill that answered every wobble would flutter. Reading on has to be meant before
 * the labels go; the smallest move back brings them home, because that is when the viewer is looking for them.
 * The run starts again whenever the page turns round, so a long way down is not owed back before the pill opens.
 */
internal class NavFold(private val foldAfter: Float, private val openAfter: Float) {
    var folded = false
        private set

    /** How far the page has gone, in pixels, since it last turned round (or since the pill last changed). */
    private var run = 0f

    /**
     * One step of a scroll. [dy] is how far the page moved under the finger: negative = reading on (the page
     * going up), positive = coming back. Returns whether the pill is folded after it.
     */
    fun scrolled(dy: Float): Boolean {
        if (dy == 0f || dy.isNaN()) return folded
        if ((dy < 0f) != (run < 0f)) run = 0f
        run += dy
        if (!folded && run <= -foldAfter) { folded = true; run = 0f }
        else if (folded && run >= openAfter) { folded = false; run = 0f }
        return folded
    }

    /** Another tab, or a screen come back to: the pill is whole again. */
    fun open() { folded = false; run = 0f }
}

/**
 * AppRoot's hold on the fold: the connection every vertical scroll under the nav passes through on its way out,
 * and the answer BottomBar reads. No screen has to know about it.
 *
 * What counts is what the page really moved (a list at its end folds nothing) plus a pull past the top, which is
 * a viewer looking for the way out. A row sliding sideways carries no y and changes nothing.
 */
internal class NavFoldState(foldAfterPx: Float, openAfterPx: Float) {
    private val fold = NavFold(foldAfterPx, openAfterPx)

    var folded by mutableStateOf(false)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            folded = fold.scrolled(consumed.y + available.y.coerceAtLeast(0f))
            return Offset.Zero
        }
    }

    fun open() { fold.open(); folded = false }
}

/** Reading on has to be meant (28 dp) before the names go; a short move back (12 dp) brings them home. */
@Composable
internal fun rememberNavFold(): NavFoldState {
    val density = LocalDensity.current
    return remember(density) { with(density) { NavFoldState(28.dp.toPx(), 12.dp.toPx()) } }
}
