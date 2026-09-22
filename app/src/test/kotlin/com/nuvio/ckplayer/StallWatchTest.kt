package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stall watchdog's rules (the shared player's stall()): three real stalls inside ninety seconds. */
class StallWatchTest {
    private fun playing() = StallWatch().apply { playedAt = 1L }

    @Test fun beforeAnythingPlayed_itIsTheStart_notAStall() {
        val w = StallWatch()
        assertFalse(w.note(10_000))
        assertEquals(0, w.total)
    }

    @Test fun theBufferingAJumpCauses_doesNotCount() {
        val w = playing().apply { seekAt = 10_000 }
        assertFalse(w.note(11_000))
        assertEquals(0, w.total)
    }

    @Test fun oneStall_isNotOnePerHiccupInsideIt() {
        val w = playing()
        w.note(10_000); w.note(11_000); w.note(12_500)
        assertEquals(1, w.total)
    }

    @Test fun theThirdStallInsideNinetySeconds_offersASwap() {
        val w = playing()
        assertFalse(w.note(10_000))
        assertFalse(w.note(40_000))
        assertTrue(w.note(70_000))
    }

    @Test fun stallsOlderThanNinetySeconds_fallOut() {
        val w = playing()
        w.note(10_000); w.note(60_000)
        assertFalse(w.note(120_000))                        // the first is 110 s old: two in the window
        assertEquals(3, w.total)                            // the HUD still counts all three
    }

    @Test fun nothingIsOfferedTwice() {
        val w = playing().apply { offered = true }
        w.note(10_000); w.note(40_000)
        assertFalse(w.note(70_000))
    }
}
