package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A list pulled past its end (Rubber): how far it is drawn out, and how a move back pays that down. */
class RubberTest {
    private val limit = 1600f

    @Test fun atRestNothingIsDrawnOut() {
        assertEquals(0f, Rubber.shown(0f, limit), 0f)
        assertEquals(0f, Rubber.shown(300f, 0f), 0f)
    }

    @Test fun itFollowsTheFingerLessAndLess() {
        var last = 0f
        var lastGain = Float.MAX_VALUE
        for (pull in 50..3000 step 50) {
            val s = Rubber.shown(pull.toFloat(), limit)
            assertTrue("further out for a longer pull ($pull -> $s)", s > last)
            val gain = s - last
            assertTrue("but by less each time ($pull: $gain after $lastGain)", gain < lastGain)
            assertTrue("never as far as the finger ($pull -> $s)", s < pull * Rubber.GIVE + 0.01f)
            last = s; lastGain = gain
        }
    }

    @Test fun itNeverLeavesTheScreen() {
        assertTrue(Rubber.shown(1e7f, limit) < limit)
    }

    @Test fun bothWaysAreTheSame() {
        assertEquals(-Rubber.shown(420f, limit), Rubber.shown(-420f, limit), 0.0001f)
    }

    @Test fun aMoveBackPaysThePullDownFirst() {
        assertEquals("part of the way", 70f, Rubber.payDown(100f, -30f), 0f)
        assertEquals("all of it, and no further", 0f, Rubber.payDown(100f, -130f), 0f)
        assertEquals("the other end the same", -20f, Rubber.payDown(-60f, 40f), 0f)
    }

    @Test fun aMoveFurtherOutPaysNothing() {
        assertEquals(100f, Rubber.payDown(100f, 30f), 0f)
        assertEquals(-60f, Rubber.payDown(-60f, -10f), 0f)
        assertEquals(0f, Rubber.payDown(0f, 25f), 0f)
    }
}
