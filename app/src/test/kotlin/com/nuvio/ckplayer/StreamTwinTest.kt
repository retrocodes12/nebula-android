package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same source, same quality: the next episode's row is chosen by its likeness to the row picked last. */
class StreamTwinTest {
    private val addon = Addon("https://torrentio.example/manifest.json", "Torrentio", "https://torrentio.example")
    private fun row(res: String, release: String, provider: String) =
        StreamItem("Torrentio\n$res", "$release\n💾 1.2 GB ⚙️ $provider", "https://x/$release")

    private val picked = row("1080p", "Show.S01E02.1080p.WEB-DL.x265-GRP", "RARBG")
    private val sameRelease = row("1080p", "Show.S01E03.1080p.WEB-DL.x265-GRP", "RARBG")
    private val other = row("720p", "Show.S01E03.720p.HDTV.x264-OTHER", "YTS")

    @Test fun sig_keepsThePlateAndTheProvider_andDropsEpisodeWords() {
        val sig = StreamTwin.sig(picked, addon)
        assertEquals("1080", sig.res)
        assertEquals("rarbg", sig.provider)
        assertTrue("grp" in sig.words)
        assertFalse("s01e02" in sig.words)        // episode-specific: it must not count against the next episode
    }

    @Test fun theNextEpisodeOfTheSameRelease_isATwin_andAnotherReleaseIsNot() {
        val sig = StreamTwin.sig(picked, addon)
        assertTrue(StreamTwin.isTwin(sameRelease, sig, addon))
        assertFalse(StreamTwin.isTwin(other, sig, addon))
    }

    @Test fun match_findsTheTwinWhereverItSits_andTheFirstRowWithoutAPick() {
        val sig = StreamTwin.sig(picked, addon)
        assertSame(sameRelease, StreamTwin.match(listOf(other, sameRelease), sig, addon))
        assertSame(other, StreamTwin.match(listOf(other, sameRelease), null, addon))
    }

    @Test fun label_namesThePlateAndTheAddon() {
        assertEquals("1080p · Torrentio", StreamTwin.label(StreamTwin.sig(picked, addon)))
    }
}
