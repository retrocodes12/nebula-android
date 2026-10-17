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

    @Test fun releaseKey_takesTheEpisodeOut_andKeepsTheReleaseTail() {
        val k = StreamTwin.releaseKey("Show.Name.S01E03.Pilot.1080p.WEB-DL.x265-GRP.mkv")
        assertEquals("e:show.name|1080p.web.dl.x265.grp", k)
        assertEquals(k, StreamTwin.releaseKey("Show.Name.S01E04.The.Other.One.1080p.WEB-DL.x265-GRP.mkv"))
        assertEquals(StreamTwin.releaseKey("Show Name 1x03 [1080p] GRP.mkv"), StreamTwin.releaseKey("Show Name 1x04 [1080p] GRP.mkv"))
        assertEquals(StreamTwin.releaseKey("[Subs] Anime EP.03 (1080p).mkv"), StreamTwin.releaseKey("[Subs] Anime EP.04 (1080p).mkv"))
        assertEquals("m:movie.name.2019.1920x1080.bluray.x264.grp", StreamTwin.releaseKey("Movie.Name.2019.1920x1080.BluRay.x264-GRP.mkv"))
        assertFalse(StreamTwin.releaseKey("Show.S01E03.1080p.WEB.x264-A") == StreamTwin.releaseKey("Show.S01E04.1080p.WEB.x264-B"))
        assertEquals("", StreamTwin.releaseKey(""))
    }

    @Test fun theSameFile_winsOverALookAlike_andAnotherFileIsNoTwin() {
        fun hinted(file: String) = StreamItem("Torrentio\n1080p", "WEB 1080p\n💾 1.2 GB", "https://x/$file", fileName = file)
        val sig = StreamTwin.sig(hinted("Show.S01E01.Name.1080p.WEB.h264-AAA.mkv"), addon)
        val bbb = hinted("Show.S01E02.Name.1080p.WEB.h264-BBB.mkv")
        val aaa = hinted("Show.S01E02.Other.1080p.WEB.h264-AAA.mkv")
        assertSame(aaa, StreamTwin.match(listOf(bbb, aaa), sig, addon))
        assertTrue(StreamTwin.isTwin(aaa, sig, addon))
        assertFalse(StreamTwin.isTwin(bbb, sig, addon))
    }

    @Test fun oneTorrent_isTheSameRelease_evenUnderAnotherFileName() {
        val pack = StreamItem("Torrentio\n1080p", "WEB 1080p", "p2p://h/1", infoHash = "abc", fileName = "Show.S01E01.1080p.WEB-AAA.mkv")
        val next = StreamItem("Torrentio\n1080p", "WEB 1080p", "p2p://h/2", infoHash = "ABC", fileName = "Show S01E02 renamed.mkv")
        assertTrue(StreamTwin.isTwin(next, StreamTwin.sig(pack, addon), addon))
    }
}
