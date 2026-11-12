package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The nav's selection pill as a thing with weight (PillMotion): the journey, the lift, the squash, a finger. */
class PillMotionTest {
    private val dt = 1f / 60f
    private val stepDp = 70f

    private class Run(val frames: Int, val pos: List<Float>, val liftX: List<Float>, val liftY: List<Float>,
        val glass: List<Float>, val dev: List<Float>, val presence: List<Float>)

    /** Steps until the pill is at rest (or [seconds] have gone), keeping what it drew on every frame. */
    private fun run(m: PillMotion, seconds: Float = 4f, from: Float = 0f, each: (Int) -> Unit = {}): Run {
        val pos = ArrayList<Float>(); val lx = ArrayList<Float>(); val ly = ArrayList<Float>()
        val g = ArrayList<Float>(); val d = ArrayList<Float>(); val pr = ArrayList<Float>()
        var i = 0
        while (i * dt < seconds) {
            each(i)
            val busy = m.step(dt, from + (i + 1) * dt, stepDp)
            pos += m.pos; lx += m.liftX; ly += m.liftY; g += m.glass; d += m.deviation; pr += m.presence
            i++
            if (!busy) break
        }
        return Run(i, pos, lx, ly, g, d, pr)
    }

    @Test fun startsAtRestOnItsTab() {
        val m = PillMotion(5, 2)
        assertTrue(m.idle)
        assertEquals(2f, m.pos, 0f)
        assertFalse(m.step(dt, dt, stepDp))
        assertEquals(0f, m.liftX, 0f); assertEquals(0f, m.glass, 0f); assertEquals(0f, m.deviation, 0f)
    }

    @Test fun aTapCarriesItToTheTabAndLeavesItPlain() {
        val m = PillMotion(5, 0)
        m.animateTo(3)
        assertEquals(3, m.target)
        val r = run(m)
        assertTrue("at rest within 2.5 s (took ${r.frames} frames)", m.idle && r.frames * dt < 2.5f)
        assertEquals("on the tab exactly", 3f, m.pos, 0f)
        assertEquals(0f, m.liftX, 0f); assertEquals(0f, m.liftY, 0f)
        assertEquals(0f, m.glass, 0f); assertEquals(0f, m.deviation, 0f); assertEquals(0f, m.presence, 0f)
        // it gets there without passing far beyond: the journey is all but critically damped
        assertTrue("no wild overshoot (max ${r.pos.max()})", r.pos.max() < 3.05f)
    }

    @Test fun itLiftsAtOnceStaysUpForTheJourneyAndComesDownAfter() {
        val m = PillMotion(5, 0)
        m.animateTo(4)
        val r = run(m)
        val at150ms = (0.15f / dt).toInt()
        assertTrue("the glass is on almost at once (${r.glass[at150ms]})", r.glass[at150ms] > 0.9f)
        // while the pill is still short of its landing it is up: glass on, size raised
        for (i in at150ms until r.frames) {
            if (r.pos[i] >= 4f * 0.9f) break
            assertTrue("glass stays on at frame $i (${r.glass[i]})", r.glass[i] > 0.9f)
            assertTrue("still raised at frame $i (${r.liftX[i]})", r.liftX[i] > 0.5f)
        }
        assertTrue("the width overshoots its raised size (${r.liftX.max()})", r.liftX.max() > 1.0f)
        assertTrue("and dips under its resting size on the way down (${r.liftX.min()})", r.liftX.min() < -0.01f)
        assertTrue("the width goes a shade further than the height", r.liftX.max() > r.liftY.max())
    }

    @Test fun aJourneyBendsItOneWayForItsWholeLength() {
        val right = PillMotion(5, 0).also { it.animateTo(3) }
        val r = run(right)
        assertTrue("going right it is held narrow and tall (${r.dev.min()})", r.dev.min() < -0.005f)
        assertTrue("and never the other way (${r.dev.max()})", r.dev.max() <= 1e-6f)
        val left = PillMotion(5, 3).also { it.animateTo(0) }
        val l = run(left)
        assertTrue("going left it is stretched wide (${l.dev.max()})", l.dev.max() > 0.005f)
        assertTrue("and never the other way (${l.dev.min()})", l.dev.min() >= -1e-6f)
        assertTrue("the bend is held inside its limit", (r.dev + l.dev).all { abs(it) <= 0.1201f })
    }

    @Test fun aNewTapMidFlightTurnsItRoundFromWhereItIs() {
        val m = PillMotion(5, 0)
        m.animateTo(4)
        var last = 0f
        var worst = 0f
        val r = run(m) { i ->
            if (i == 8) m.animateTo(0)
            worst = maxOf(worst, abs(m.pos - last)); last = m.pos
        }
        assertTrue("no jump on the turn (largest step $worst)", worst < 0.6f)
        assertEquals(0f, m.pos, 0f)
        assertTrue(m.idle && r.frames * dt < 3f)
    }

    @Test fun aFingerCarriesItAndItSettlesOnTheNearestTab() {
        val m = PillMotion(5, 0)
        m.grab(0.1f)
        m.dragTo(2.3f)
        run(m, seconds = 0.4f)
        assertTrue("held", m.held)
        assertEquals("it follows the finger", 2.3f, m.pos, 0.05f)
        assertTrue("and is lifted while held (${m.liftX})", m.liftX > 0.8f && m.glass > 0.9f)
        assertEquals(2, m.release())
        run(m, from = 1f)
        assertTrue(m.idle)
        assertEquals(2f, m.pos, 0f)
    }

    @Test fun pastAnEndItGivesALittleAndNoMore() {
        val m = PillMotion(5, 0)
        m.grab(0f)
        m.dragTo(-6f)
        run(m, seconds = 0.5f)
        assertTrue("pulled out past the first tab (${m.pos})", m.pos < -0.1f)
        assertTrue("but never off (${m.pos})", m.pos > -PillMotion.END_GIVE)
        assertEquals("and it comes back to the first tab", 0, m.release())
        m.grab(0f); m.dragTo(40f)
        run(m, seconds = 0.5f, from = 2f)
        assertTrue("the same at the far end (${m.pos})", m.pos > 4.1f && m.pos < 4f + PillMotion.END_GIVE)
        assertEquals(4, m.release())
    }

    @Test fun aPressThatNeverMovedGoesToWhereItPressed() {
        val m = PillMotion(5, 0)
        m.grab(3.1f)
        run(m, seconds = 0.1f)
        assertEquals(3, m.release())
    }

    @Test fun reducedMotionIsACut() {
        val m = PillMotion(5, 0)
        m.snapTo(4)
        assertTrue(m.idle)
        assertEquals(4f, m.pos, 0f)
        assertEquals(4, m.target)
        assertFalse(m.step(dt, dt, stepDp))
    }

    @Test fun aSpringOnALongFrameStaysStable() {
        val s = Spring1(0f)
        repeat(40) { s.step(1f, 0.064f, PillMotion.GLASS_K, PillMotion.GLASS_C) }
        assertEquals(1f, s.x, 0.001f)
    }
}
