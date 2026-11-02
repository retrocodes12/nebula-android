package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlanTest {
    private val gb = 1L shl 30

    @Test fun rowsThatAreNeverOffered() {
        assertTrue(DownloadPlan.savable("https://host/film.mkv", torrent = false, page = false))
        assertFalse("a torrent would seed from the phone", DownloadPlan.savable("nebula-p2p://abc/0", torrent = true, page = false))
        assertFalse("a web player page", DownloadPlan.savable("https://host/embed/1", torrent = false, page = true))
        assertFalse("protected streams come as manifests", DownloadPlan.savable("https://host/live/index.mpd?t=1", torrent = false, page = false))
        assertFalse(DownloadPlan.savable("file:///data/x.mp4", torrent = false, page = false))
    }

    @Test fun firstBytesDecide() {
        assertEquals(DownloadPlan.Source.File(2 * gb), DownloadPlan.sniff(206, "video/x-matroska", "\u001aE", 2 * gb))
        assertEquals(DownloadPlan.Source.Hls, DownloadPlan.sniff(200, "text/plain", "#EXTM3U\n#EXT-X-VERSION:3", 0))
        assertEquals(DownloadPlan.Source.Hls, DownloadPlan.sniff(200, "application/vnd.apple.mpegurl", "", 0))
        assertEquals(DownloadPlan.Source.Refused(DownloadPlan.NO_KIND), DownloadPlan.sniff(200, "application/xml", "<?xml?><MPD>", 500))
        assertEquals(DownloadPlan.Source.Refused(DownloadPlan.NO_PAGE), DownloadPlan.sniff(200, "text/html; charset=utf-8", "<html>", gb))
        assertEquals(DownloadPlan.Source.Refused(DownloadPlan.NO_REACH), DownloadPlan.sniff(403, "video/mp4", "", gb))
        assertEquals("no length is no end", DownloadPlan.Source.Refused(DownloadPlan.NO_LIVE), DownloadPlan.sniff(200, "video/mp2t", "G@", 0))
        assertEquals("a debrid 'not ready' clip", DownloadPlan.Source.Refused(DownloadPlan.NOT_READY), DownloadPlan.sniff(206, "video/mp4", "", 8L shl 20))
    }

    @Test fun totalFromHeaders() {
        assertEquals(5000L, DownloadPlan.totalOf(206, "bytes 0-2047/5000", "2048"))
        assertEquals(5000L, DownloadPlan.totalOf(200, null, "5000"))
        assertEquals("a part's length is not the whole", 0L, DownloadPlan.totalOf(206, null, "2048"))
        assertEquals(0L, DownloadPlan.totalOf(206, "bytes 0-2047/*", "2048"))
    }

    @Test fun playlists() {
        assertNull(DownloadPlan.hlsRefusal(ended = true, encrypted = false))
        assertEquals(DownloadPlan.NO_LIVE, DownloadPlan.hlsRefusal(ended = false, encrypted = false))
        assertEquals(DownloadPlan.NO_PROTECTED, DownloadPlan.hlsRefusal(ended = true, encrypted = true))
    }

    @Test fun room() {
        assertTrue(DownloadPlan.hasRoom(gb, 2 * gb))
        assertFalse(DownloadPlan.hasRoom(gb, gb + 100))
        assertTrue("an unknown size still needs the margin only", DownloadPlan.hasRoom(0, gb))
    }

    @Test fun metaSurvivesTheDatabase() {
        val m = DownloadPlan.Meta(
            viewer = "b", type = "series", id = "tt1:1:2", name = "Show · S1E2 · Name", poster = "https://p/1.jpg",
            art = "/data/art", addonUrl = "https://a/manifest.json", streamName = "1080p", streamTitle = "file\n2 GB",
            fileName = "show.s01e02.mkv", source = "1080p · A", description = "A line.",
            subs = listOf(SubTrack("file:///x/sub-0.srt", "eng")), headers = mapOf("Referer" to "https://r/"), at = 42L,
        )
        assertEquals(m, DownloadPlan.Meta.read(m.toBytes()))
        val bare = DownloadPlan.Meta(viewer = "a", type = "movie", id = "tt2", name = "Film")
        assertEquals(bare, DownloadPlan.Meta.read(bare.toBytes()))
        assertNull(DownloadPlan.Meta.read(null))
        assertNull(DownloadPlan.Meta.read("not json".toByteArray()))
    }

    @Test fun retryFindsTheSameRow() {
        val m = DownloadPlan.Meta(viewer = "a", type = "movie", id = "1", name = "F", streamName = "N", streamTitle = "T", fileName = "f.mkv")
        val same = StreamItem("N", "T", "https://new/same", fileName = "other.mkv")
        val file = StreamItem("X", "Y", "https://new/file", fileName = "f.mkv")
        val url = StreamItem("Q", "R", "https://old/link")
        assertEquals(url, DownloadPlan.sameRow(listOf(same, file, url), "https://old/link", m))
        assertEquals(file, DownloadPlan.sameRow(listOf(same, file), "https://old/link", m))
        assertEquals(same, DownloadPlan.sameRow(listOf(same), "https://old/link", m))
        assertNull(DownloadPlan.sameRow(listOf(StreamItem("A", "B", "https://x")), "https://old/link", m))
    }

    @Test fun subtitlesOnePerLanguageInOrder() {
        val offers = listOf(SubTrack("1", "fre"), SubTrack("2", "eng"), SubTrack("3", "eng"))
        val keyOf = { c: String? -> c.orEmpty().take(2) }
        assertEquals(listOf("2", "1"), DownloadPlan.pickSubs(offers, listOf("en", "fr"), keyOf).map { it.url })
        assertEquals(listOf("2"), DownloadPlan.pickSubs(offers, listOf("en", "en", "de"), keyOf).map { it.url })
        assertEquals("sub-0.vtt", DownloadPlan.subFile(0, "WEBVTT\n\n"))
        assertEquals("sub-1.srt", DownloadPlan.subFile(1, "1\n00:00:01,000"))
    }

    @Test fun statusLines() {
        val p = DownloadPlan.Phase.RUNNING
        assertEquals("43% · 1.0 GB of 2.0 GB", DownloadPlan.line(p, 43f, gb, 2 * gb, true))
        assertEquals("a playlist has no total yet", "8% · 300 MB", DownloadPlan.line(p, 8f, 300L shl 20, -1, true))
        assertEquals("Waiting for Wi-Fi", DownloadPlan.line(DownloadPlan.Phase.WAITING, -1f, 0, 0, true))
        assertEquals("Waiting for a connection", DownloadPlan.line(DownloadPlan.Phase.WAITING, -1f, 0, 0, false))
        assertEquals("2.0 GB", DownloadPlan.line(DownloadPlan.Phase.DONE, 100f, 2 * gb, 2 * gb, true))
    }

    @Test fun names() {
        assertEquals("Show" to "S1E2 · Name", DownloadPlan.nameParts("Show · S1E2 · Name"))
        assertEquals("Face · Off" to null, DownloadPlan.nameParts("Face · Off"))
        assertEquals(1 to 2, DownloadPlan.numbers("Show · S1E2 · Name"))
        assertEquals(3 to null, DownloadPlan.numbers("Show · S3"))
        assertNull(DownloadPlan.numbers("Film"))
    }

    @Test fun shelvesGroupShowsAndKeepPlayingOrder() {
        fun m(id: String, name: String, at: Long) = DownloadPlan.Meta(viewer = "a", type = if ("·" in name) "series" else "movie", id = id, name = name, at = at)
        val rows = listOf(
            m("f", "Film", 5), m("3", "Show · S2E1", 4), m("1", "Show · S1E2", 3), m("2", "Show · S1E1 · One", 2), m("0", "Show · S0E1", 1),
        )
        val out = DownloadPlan.shelves(rows) { it }
        assertEquals(2, out.size)
        assertEquals("newest shelf first", "f", out[0].second.single().id)
        assertEquals("specials last", listOf("2", "1", "3", "0"), out[1].second.map { it.id })
        assertNotNull(out[1].first)
    }
}
