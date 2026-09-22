package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The stream row's text: what the name keeps, what the description keeps, and the rate a row needs. */
class StreamBadgesTest {
    @Test fun cleanName_dropsTheAddonNameAndTheResolutionThePlateShows() {
        assertEquals("HDR", StreamBadges.cleanName("Torrentio\n4k HDR", "Torrentio"))
    }

    @Test fun cleanName_leavesNoDanglingSeparators() {
        assertEquals("Main", StreamBadges.cleanName("Nebula • 1080p • Main", "Nebula"))
    }

    @Test fun cleanName_dropsSymbolsAndCopesWithNull() {
        assertEquals("Movie Name", StreamBadges.cleanName("✅ Movie Name ⚙️", null))
        assertEquals("", StreamBadges.cleanName(null, "Torrentio"))
    }

    @Test fun cleanDesc_dropsWhatTheHeaderAlreadySays() {
        // the page title, the episode tag, a bare year and the add-on's own name all go; the rest stays
        assertEquals(
            "5.1 GB",
            StreamBadges.cleanDesc("The Matrix · S01E02 · 1999 · Torrentio · 5.1 GB", "Torrentio", "The Matrix"),
        )
    }

    @Test fun cleanDesc_dedupesAndKeepsFour() {
        assertEquals("a1  ·  b2  ·  c3  ·  d4", StreamBadges.cleanDesc("a1 · a1 · b2 · c3 · d4 · e5", null, "X"))
    }

    @Test fun bps_theStatedRateWins() {
        assertEquals(12_500_000L, StreamBadges.bps(4L shl 30, "~ 12.5 Mbps", "120 min"))
    }

    @Test fun bps_elseSizeOverRunningTime() {
        val twoGiB = 2L shl 30
        assertEquals(twoGiB * 8 / 7200, StreamBadges.bps(0, "2 GB", "120 min"))
        assertEquals(twoGiB * 8 / 7200, StreamBadges.bps(twoGiB, "", "2h 0min"))
    }

    @Test fun bps_unknownWithoutARunningTime() {
        assertEquals(0L, StreamBadges.bps(0, "1.4 GB", null))
    }

    @Test fun runtimeSecs_readsTheShapesMetasUse() {
        assertEquals(6720L, StreamBadges.runtimeSecs("1h 52min"))
        assertEquals(6720L, StreamBadges.runtimeSecs("112 min"))
        assertEquals(6720L, StreamBadges.runtimeSecs("112"))          // a bare number is minutes
    }

    @Test fun slow_nothingIsSlowBeforeTheConnectionWasMeasured() {
        // Prefs.bw is 0 until a play has sampled the connection: no mark, however heavy the row
        assertFalse(StreamBadges.slow(StreamItem("Nebula", "~ 80 Mbps", "https://x/y.mkv"), "120 min"))
    }

    @Test fun plate_ranksTheResolution() {
        assertEquals(4, StreamBadges.resRank("Movie 2160p"))
        assertEquals(3, StreamBadges.resRank("Movie FHD"))
        assertEquals(0, StreamBadges.resRank("Movie"))
    }
}
