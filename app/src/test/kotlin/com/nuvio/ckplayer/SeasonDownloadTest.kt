package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole-season download's choices (SeasonDownload.kt): which episodes count, and which row is "the same source". */
class SeasonDownloadTest {
    private val a = Addon("https://t.example/manifest.json", "Torrentio", "https://t.example")
    private fun row(title: String, url: String = "https://h.example/" + title.hashCode()) = StreamItem("Torrentio\n1080p", title, url)

    @Test fun aired_byDateOrNoDate() {
        val today = "2026-11-04"
        assertTrue(SeasonDownload.aired(Episode("tt1:1:1", 1, 1, "A", null, null, released = "2026-10-01T00:00:00.000Z"), today))
        assertTrue(SeasonDownload.aired(Episode("tt1:1:2", 1, 2, "B", null, null, released = "2026-11-04"), today))
        assertFalse(SeasonDownload.aired(Episode("tt1:1:3", 1, 3, "C", null, null, released = "2026-12-01"), today))
        assertTrue(SeasonDownload.aired(Episode("tt1:1:4", 1, 4, "D", null, null, released = null), today))
    }

    @Test fun pickTwin_sameReleaseGroup() {
        val picked = row("The.Bear.S02E01.Beef.1080p.DSNP.WEB-DL.DDP5.1.H.264-FLUX.mkv\n💾 1.2 GB")
        val sig = StreamTwin.sig(picked, a)
        val ep2 = listOf(
            row("The.Bear.S02E02.Pasta.2160p.DSNP.WEB-DL.DDP5.1.HDR.H.265-OTHER.mkv\n💾 4 GB"),
            row("The.Bear.S02E02.Pasta.1080p.DSNP.WEB-DL.DDP5.1.H.264-FLUX.mkv\n💾 1.1 GB"),
        )
        assertEquals(ep2[1], SeasonDownload.pickTwin(ep2, sig, a))
    }

    @Test fun pickTwin_noneOfTheSameQuality_isNull() {
        val picked = row("The.Bear.S02E01.Beef.1080p.DSNP.WEB-DL.DDP5.1.H.264-FLUX.mkv")
        val sig = StreamTwin.sig(picked, a)
        assertNull(SeasonDownload.pickTwin(emptyList(), sig, a))
        val only4k = listOf(StreamItem("Torrentio\n4k", "The.Bear.S02E02.Pasta.2160p.WEB.H.265-OTHER.mkv", "https://h.example/x"))
        assertNull(SeasonDownload.pickTwin(only4k, sig, a))
    }
}
