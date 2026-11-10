package com.nuvio.ckplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The nav pill folds to its icons while a page is read downwards and opens on the way back up (NavFold). */
class NavFoldTest {
    private fun fold() = NavFold(foldAfter = 80f, openAfter = 40f)

    @Test fun startsOpen() {
        assertFalse(fold().folded)
    }

    @Test fun foldsOnceReadingOnHasGoneFarEnough() {
        val f = fold()
        assertFalse(f.scrolled(-30f))
        assertFalse(f.scrolled(-30f))
        assertTrue(f.scrolled(-30f))            // 90 px down in all
        assertTrue(f.folded)
    }

    @Test fun opensOnAShorterMoveBack() {
        val f = fold()
        f.scrolled(-200f)
        assertTrue(f.scrolled(20f))             // not yet
        assertFalse(f.scrolled(25f))            // 45 px back
    }

    @Test fun aLongWayDownIsNotOwedBack() {
        // the run starts again when the page turns round: 5 000 px read, 40 px back opens it
        val f = fold()
        repeat(50) { f.scrolled(-100f) }
        assertTrue(f.folded)
        assertFalse(f.scrolled(40f))
    }

    @Test fun aWobbleNeitherFoldsNorOpens() {
        val f = fold()
        repeat(40) { f.scrolled(-6f); f.scrolled(6f) }
        assertFalse(f.folded)
        f.scrolled(-200f)
        repeat(40) { f.scrolled(6f); f.scrolled(-6f) }
        assertTrue(f.folded)
    }

    @Test fun turningRoundForgetsTheRunSoFar() {
        val f = fold()
        f.scrolled(-70f)                        // nearly there
        f.scrolled(5f)                          // turned round
        assertFalse(f.scrolled(-70f))           // counted from the turn, not added to the first 70
        assertTrue(f.scrolled(-10f))
    }

    @Test fun aStillStepChangesNothing() {
        val f = fold()
        f.scrolled(-79f)
        assertFalse(f.scrolled(0f))
        assertFalse(f.scrolled(Float.NaN))
        assertTrue(f.scrolled(-1f))             // and the run so far was kept
    }

    @Test fun foldsAgainAfterOpening() {
        val f = fold()
        f.scrolled(-100f); f.scrolled(50f)
        assertFalse(f.folded)
        assertFalse(f.scrolled(-60f))
        assertTrue(f.scrolled(-30f))
    }

    @Test fun openPutsItBackAndForgetsTheRun() {
        val f = fold()
        f.scrolled(-100f)
        f.open()
        assertFalse(f.folded)
        f.scrolled(-70f)
        f.open()
        assertFalse(f.scrolled(-70f))           // the 70 before open() does not count
    }
}
