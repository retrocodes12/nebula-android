package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubPlaceTest {
    // a 2.39:1 film on a 1920×1080 screen: 804 tall, 138 above and under it
    @Test fun aWideFilmOnATvGoesDownByTheBar() {
        val s = assertNotNullSpot(SubPlace.bars(1080, 942, 804, tv = true))
        assertEquals(138, s.shift)
        assertEquals(0.035f * 1080, s.pad * 804, 0.5f)        // the gap under the last line, in pixels
    }

    @Test fun aPhoneLeavesASmallerGapThanATv() {
        val phone = assertNotNullSpot(SubPlace.bars(1080, 942, 804, tv = false))
        val tv = assertNotNullSpot(SubPlace.bars(1080, 942, 804, tv = true))
        assertTrue(phone.pad < tv.pad)
    }

    // upright: the picture is a thin strip in the middle, the bar nearly half the screen
    @Test fun anUprightPhoneMovesByTheWholeBarAndNoMore() {
        val s = assertNotNullSpot(SubPlace.bars(2400, 1426, 452, tv = false))
        assertEquals(974, s.shift)
        assertTrue("the frame's bottom edge lands on the screen's, not past it", 1426 + s.shift == 2400)
    }

    @Test fun noBarUnderThePictureMeansNoMove() {
        assertNull(SubPlace.bars(1080, 1080, 1080, tv = true))     // a 16:9 film, or Stretch
        assertNull(SubPlace.bars(1080, 1260, 1440, tv = true))     // Fill: the frame overhangs the screen
        assertNull(SubPlace.bars(1080, 1076, 1072, tv = true))     // 4 px is rounding, not a bar
    }

    @Test fun aThinBarStillCounts() {
        assertEquals(20, assertNotNullSpot(SubPlace.bars(1080, 1060, 1040, tv = false)).shift)
    }

    @Test fun nothingLaidOutYetMeansNoMove() {
        assertNull(SubPlace.bars(0, 0, 0, tv = false))
        assertNull(SubPlace.bars(1080, 540, 0, tv = false))
    }

    private fun assertNotNullSpot(s: SubPlace.Spot?): SubPlace.Spot { assertNotNull(s); return s!! }
}
