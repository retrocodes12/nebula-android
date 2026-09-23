package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an empty Streams page says, and in which order the reasons win (streamsEmptyStatus in MainActivity.kt). */
class StreamsEmptyTest {
    private fun why(streamers: Int, unread: Int, failures: Int, size: Int, floored: Boolean = false) =
        streamsEmptyStatus(orderEmpty = size == 0, streamers, unread, failures, size, floored)

    @Test fun everyAddonOff_winsOverEverything() {
        assertEquals(StreamsEmpty.OFF, why(streamers = 0, unread = 0, failures = 0, size = 0))
        assertEquals(StreamsEmpty.OFF, streamsEmptyStatus(true, 0, 0, 0, 0, floored = true))
    }

    @Test fun cinemetaOnly_isNonePlaysThis_notFailed() {
        // regression: the origin is always asked, Cinemeta answers the stream route with a 404 — one failure out of one
        // add-on — and the page said "Failed to load streams." for every title on a fresh install
        assertEquals(StreamsEmpty.NONE, why(streamers = 0, unread = 0, failures = 1, size = 1))
    }

    @Test fun cinemetaPlusAnAddonThatDoesNotStreamThisId_isNonePlaysThis() {
        assertEquals(StreamsEmpty.NONE, why(streamers = 0, unread = 0, failures = 1, size = 2))
    }

    @Test fun anUnreadManifest_isNeverNonePlaysThis() {
        // it might have played it: Cinemeta's 404 + the other add-on's manifest unreachable = everything failed
        assertEquals(StreamsEmpty.FAILED, why(streamers = 0, unread = 1, failures = 2, size = 2))
        // one unreadable, one read and not a streamer of this: not "none", not all failed
        assertEquals(StreamsEmpty.NOTHING, why(streamers = 0, unread = 1, failures = 2, size = 3))
    }

    @Test fun everyStreamerFailed_isFailed() {
        assertEquals(StreamsEmpty.FAILED, why(streamers = 1, unread = 0, failures = 2, size = 2))
        assertEquals(StreamsEmpty.FAILED, why(streamers = 2, unread = 0, failures = 2, size = 2))
    }

    @Test fun failedComesBeforeFloored() {
        assertEquals(StreamsEmpty.FAILED, why(streamers = 1, unread = 0, failures = 1, size = 1, floored = true))
    }

    @Test fun nonePlaysThisComesBeforeFailedAndFloored() {
        assertEquals(StreamsEmpty.NONE, why(streamers = 0, unread = 0, failures = 1, size = 1, floored = true))
    }

    @Test fun aStreamerAnsweredButMinimumQualityHidItAll_isFloored() {
        assertEquals(StreamsEmpty.FLOORED, why(streamers = 1, unread = 0, failures = 1, size = 2, floored = true))
    }

    @Test fun aStreamerAnsweredWithNothing_isNothingRightNow() {
        assertEquals(StreamsEmpty.NOTHING, why(streamers = 1, unread = 0, failures = 0, size = 1))
        assertEquals(StreamsEmpty.NOTHING, why(streamers = 1, unread = 0, failures = 1, size = 2))
    }

    @Test fun nonePlaysThis_pointsAtAddons() {
        assertTrue(StreamsEmpty.NONE.line.startsWith("None of your add-ons plays this"))
        assertTrue("Settings › Add-ons" in StreamsEmpty.NONE.line)
    }
}
