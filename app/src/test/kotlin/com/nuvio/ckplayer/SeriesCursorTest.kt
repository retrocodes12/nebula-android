package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The series cursor (seriesCursorOf): what a PLAYED record says is where the viewer is; a hand mark only lets the
 * cursor step past episodes already ticked off. Each case is a bug an audit round found or a rule it wrote down.
 */
class SeriesCursorTest {
    private fun ep(s: Int, e: Int) = Episode("tt1:$s:$e", s, e, "S${s}E$e", null, null)

    // two seasons of three, plus one special (season 0 sorts LAST on purpose)
    private val show = listOf(ep(2, 1), ep(1, 2), ep(0, 1), ep(1, 1), ep(2, 3), ep(1, 3), ep(2, 2))

    private fun played(e: Episode, at: Long, done: Boolean = false, pos: Long = 600_000) =
        ProgressRec("series", e.id, pos = pos, dur = 2_400_000, done = done, at = at)
    private fun marked(e: Episode, at: Long) = ProgressRec("series", e.id, done = true, hand = true, at = at)

    private fun cursor(vararg recs: ProgressRec): SeriesCursor? {
        val byId = recs.associateBy { it.id }
        return seriesCursorOf(show) { byId[it] }
    }

    @Test fun nothingStarted_noCursor() {
        assertNull(cursor())
    }

    @Test fun partWay_theSameEpisodeIsUpNext() {
        assertEquals("tt1:1:2", cursor(played(ep(1, 2), at = 10))?.upNext?.id)
    }

    @Test fun finished_theNextEpisodeIsUpNext_acrossASeason() {
        assertEquals("tt1:2:1", cursor(played(ep(1, 3), at = 10, done = true))?.upNext?.id)
    }

    @Test fun under15Seconds_isNotAPlace() {
        assertNull(cursor(played(ep(1, 2), at = 10, pos = 5_000)))
    }

    @Test fun theNewestPlay_winsOverAFurtherOne() {
        // rewatching an earlier episode is where the viewer is now
        val c = cursor(played(ep(2, 2), at = 10, done = true), played(ep(1, 1), at = 20))
        assertEquals("tt1:1:1", c?.upNext?.id)
    }

    @Test fun aMarkFarAhead_leavesThePlaceAlone() {
        // round 1: reading the furthest mark as a position teleported the cursor past everything unwatched
        val c = cursor(played(ep(1, 1), at = 10, done = true), marked(ep(2, 2), at = 20))
        assertEquals("tt1:1:2", c?.upNext?.id)
    }

    @Test fun marksDirectlyAhead_areSteppedOver() {
        val c = cursor(played(ep(1, 1), at = 10, done = true), marked(ep(1, 2), at = 20), marked(ep(1, 3), at = 21))
        assertEquals("tt1:2:1", c?.upNext?.id)
    }

    @Test fun onlyMarks_startFromTheTopAndStepOverThem() {
        assertEquals("tt1:1:2", cursor(marked(ep(1, 1), at = 10))?.upNext?.id)
    }

    @Test fun finishedShow_noRing_butTheSeatStaysInTheLastSeason() {
        // round 2: a bare null threw the season away and dropped a finished show to season 1
        val c = cursor(played(ep(2, 3), at = 10, done = true))
        assertNull(c?.upNext)
        assertEquals("tt1:2:3", c?.seat?.id)
    }

    @Test fun aViewerWhoWatchesSpecials_isLedIntoThem() {
        val c = cursor(played(ep(0, 1), at = 5, done = true), played(ep(2, 3), at = 10, done = true))
        assertEquals("tt1:0:1", c?.upNext?.id)
    }

    @Test fun dismissedRecords_areNotAPlace() {
        assertNull(cursor(played(ep(1, 2), at = 10).copy(dismissed = true)))
    }
}
