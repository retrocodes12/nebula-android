package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The watch party's rules — the same numbers and sentences as the shared player (party-2026-11-06.md). */
class PartySyncTest {
    private fun st(playing: Boolean, pos: Double, at: Long, rate: Double = 1.0, name: String = "Ana", waiting: Boolean = false) =
        PartyState(playing, pos, false, rate, waiting, at, 1, "x", name)

    @Test fun expect_runsAtTheRateWhilePlaying() {
        assertEquals(20.0, PartySync.expect(st(true, 10.0, 1_000, 1.0), 11_000), 1e-9)
        assertEquals(25.0, PartySync.expect(st(true, 10.0, 1_000, 1.5), 11_000), 1e-9)
        assertEquals(10.0, PartySync.expect(st(false, 10.0, 1_000), 99_000), 1e-9)
        assertEquals(10.0, PartySync.expect(st(true, 10.0, 5_000), 1_000), 1e-9)   // a clock before the stamp: no going back
    }

    @Test fun move_seeksNudgesHolds() {
        assertTrue(PartySync.move(2.0, 1.0, false) is PartySync.Move.Seek)
        assertEquals(PartySync.Move.Nudge(1.05), PartySync.move(0.5, 1.0, false))
        assertEquals(PartySync.Move.Nudge(0.95), PartySync.move(-0.5, 1.0, false))
        assertEquals(PartySync.Move.Hold(1.0), PartySync.move(0.2, 1.0, false))        // not yet worth a nudge
        assertEquals(PartySync.Move.Nudge(1.05), PartySync.move(0.2, 1.0, true))       // …but a nudge runs on to 0.1 s
        assertEquals(PartySync.Move.Hold(1.5), PartySync.move(0.05, 1.5, true))
        assertTrue(PartySync.isNudge(1.575, 1.5))
        assertTrue(!PartySync.isNudge(1.25, 1.0))
    }

    @Test fun clock_shortestRoundTripWins() {
        val c = PartySync.Clock()
        c.sample(1_000, 50_000, 1_200)            // rtt 200 → off = 50000 + 100 − 1200
        assertEquals(48_900L, c.off)
        c.sample(2_000, 51_000, 2_050)            // rtt 50 wins → 51000 + 25 − 2050
        assertEquals(48_975L, c.off)
        c.sample(3_000, 60_000, 3_900)            // rtt 900: ignored
        assertEquals(48_975L, c.off)
    }

    @Test fun notice_saysWhoDidWhat() {
        val prev = st(true, 100.0, 10_000, name = "Host")
        assertEquals("Ana paused", PartySync.notice(prev, st(false, 101.0, 11_000)))
        assertEquals("Ana resumed", PartySync.notice(st(false, 101.0, 11_000), st(true, 101.0, 12_000)))
        assertEquals("Ana changed the speed to 1.5×", PartySync.notice(prev, st(true, 101.0, 11_000, rate = 1.5)))
        assertEquals("Ana skipped to 1:02:10", PartySync.notice(prev, st(true, 3730.0, 11_000)))
        assertNull(PartySync.notice(prev, st(true, 101.2, 11_000)))                      // a heartbeat
        assertNull(PartySync.notice(prev, st(false, 101.0, 11_000, waiting = true)))      // waiting has its own pill
    }

    @Test fun labels() {
        assertEquals("40 s", PartySync.gapLabel(40.2))
        assertEquals("3 min", PartySync.gapLabel(170.0))
        assertEquals("1 h 5 min", PartySync.gapLabel(3900.0))
        assertEquals(5, PartySync.behindReported(1.0))
        assertEquals(125, PartySync.behindReported(124.0))
        assertEquals("Choosing a source", PartySync.statusLabel(PartyMember("a", "A", false, "choosing", "", null, 2)))
        assertEquals("2 min behind", PartySync.statusLabel(PartyMember("a", "A", false, "behind", "", 120, 2)))
    }

    @Test fun keysAndShows() {
        assertEquals("movie:tt1", PartySync.keyOf("movie", "tt1", "https://x"))
        assertEquals("https://x", PartySync.keyOf(null, null, "https://x"))
        assertTrue(PartySync.sameShow("series", "tt5:1:2", "series", "tt5:1:3"))
        assertTrue(PartySync.sameShow("series", "kitsu:9:4", "series", "kitsu:9:5"))
        assertTrue(!PartySync.sameShow("movie", "tt1", "movie", "tt2"))
    }

    @Test fun waiters_onTheTitleLoadingOrBuffering() {
        val ms = listOf(
            PartyMember("me", "Me", true, "ready", "movie:tt1", null, 2),
            PartyMember("a", "Ana", false, "buffering", "movie:tt1", null, 2),
            PartyMember("b", "Bo", false, "loading", "movie:tt2", null, 2),
            PartyMember("c", "Cy", false, "loading", "movie:tt1", null, 2),
        )
        assertEquals(listOf("Ana", "Cy"), PartySync.waiters(ms, "me", "movie:tt1", emptySet()).map { it.name })
        assertEquals(listOf("Ana"), PartySync.waiters(ms, "me", "movie:tt1", setOf("c")).map { it.name })
    }

    @Test fun matchOwn_hashThenReleaseThenName() {
        val rows = listOf(
            StreamItem("A", "Other.Film.2010.720p.WEB.x264-X.mkv", "https://a"),
            StreamItem("B", "x", "https://b", fileName = "Sintel.2010.1080p.WEB.x264-RIG.mkv"),
            StreamItem("C", "y", "https://c", infoHash = "a".repeat(40)),
        )
        assertEquals("https://c", PartySync.matchOwn(rows, "whatever.mkv", "A".repeat(40))?.url)
        assertEquals("https://b", PartySync.matchOwn(rows, "Sintel.2010.1080p.WEB.x264-RIG.mkv", null)?.url)
        assertNull(PartySync.matchOwn(rows, "Nothing.Here.2001.mkv", null))
        assertNull(PartySync.matchOwn(rows, null, null))
    }
}
