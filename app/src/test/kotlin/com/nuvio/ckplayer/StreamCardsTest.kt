package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The release card's reading of a row and its designs (StreamCards.kt) — the same cases as the shared player's rig `cards`. */
class StreamCardsTest {
    @Test fun relTitle_keepsTheYear_dropsTheRest() {
        assertEquals("The Dark Knight 2008", StreamCards.relTitle("The.Dark.Knight.2008.UHD.BluRay.2160p.TrueHD.5.1.DV.HEVC.HYBRID.REMUX-FraMeSToR.mkv"))
        assertEquals("The Dark Knight 2008", StreamCards.relTitle("The Dark Knight (2008) IMAX 2160p UHD BluRay REMUX DV HDR 10bit HEVC [Hindi DDP 2.0 + English TrueHD 5.1] x265 (FraMeSToR-LUMiX) 4KHDHub.com.mkv"))
        assertEquals("Batman The Dark Knight 2008", StreamCards.relTitle("Batman.The.Dark.Knight.2008.AD.MULTi.VFF.2160p.WEB.HDR10.EAC3.5.1.H265-Overflow.mp4"))
        assertEquals("Charlotte's Web 2006", StreamCards.relTitle("Charlotte's Web 2006 1080p BluRay x264"))
        assertEquals("Some Film 2021", StreamCards.relTitle("[YTS] Some.Film.2021.1080p.WEB.mp4"))
        assertEquals("The Bear", StreamCards.relTitle("The.Bear.S02E03.Sundae.1080p.WEB.h264-GRP.mkv"))
        assertEquals("", StreamCards.relTitle(""))
    }

    @Test fun relTags_sourceThenCodec() {
        assertEquals(listOf("BDRemux", "HEVC"), StreamCards.relTags("The.Dark.Knight.2008.UHD.BluRay.2160p.TrueHD.5.1.DV.HEVC.HYBRID.REMUX-FraMeSToR.mkv"))
        assertEquals(listOf("WEB", "HEVC"), StreamCards.relTags("Batman.The.Dark.Knight.2008.AD.MULTi.VFF.2160p.WEB.HDR10.EAC3.5.1.H265-Overflow.mp4"))
        assertEquals(listOf("BluRay", "AVC"), StreamCards.relTags("The Dark Knight 2008 1080p BluRay x264 DTS-HD MA 5.1"))
        assertEquals(listOf("WEB-DL", "AV1"), StreamCards.relTags("Film.2024.2160p.WEB-DL.AV1"))
        assertTrue(StreamCards.relTags("Live feed").isEmpty())
    }

    @Test fun langCodes_wordsThenFlags() {
        val t = "x\n👤 154 💾 53.75 GB ⚙️ TorrentGalaxy\nMulti Audio / 🇬🇧 / 🇳🇱"
        assertEquals(listOf("MULTI", "EN", "NL"), StreamCards.langCodes(t))
        assertEquals(listOf("HI", "EN"), StreamCards.langCodes("[Hindi DDP 2.0 + English TrueHD 5.1]"))
    }

    @Test fun label_isWhatTheAddonAddsBeyondTheCard() {
        val fired = StreamBadges.match("Torrentio\n4k DV | HDR").fired
        assertEquals("", StreamCards.label("Torrentio\n4k DV | HDR", "Torrentio", "The Dark Knight 2008", fired, "TorrentGalaxy"))
        assertEquals("Direct", StreamCards.label("4KHDHub | Direct", "4KHDHub", "The Dark Knight 2008", emptyList(), null))
    }

    @Test fun build_theSampleRow() {
        val c = StreamCards.build(StreamCards.SAMPLE, "Torrentio", "The Dark Knight", StreamCards.RELEASE, "initial")
        assertEquals("The Dark Knight 2008", c.title)
        assertEquals(listOf("BDRemux", "HEVC"), c.tags)
        assertEquals("4K", c.res)
        assertEquals("53.75 GB", c.size)
        assertEquals("↑ 154 seeds", c.line2)
        assertEquals(listOf("Torrentio", "TorrentGalaxy"), c.pills)
        assertEquals(listOf("MULTI", "EN", "NL"), c.langs)
        assertTrue(c.file.endsWith("FraMeSToR.mkv"))
        assertTrue(c.badges.isNotEmpty() && c.badges.size <= 4 && c.badges.all { it.contains("/badges/w/") })
        assertTrue(c.badges.none { it.contains("HEVC") || it.contains("remux") || it.contains("4k_ultra") || it.contains("7_1") })
        val hidden = StreamCards.build(StreamCards.SAMPLE, "Torrentio", "The Dark Knight", StreamCards.RELEASE, "hidden")
        assertEquals(listOf("TorrentGalaxy"), hidden.pills)
        val minimal = StreamCards.build(StreamCards.SAMPLE, "Torrentio", "The Dark Knight", StreamCards.design("minimal").spec!!, "initial")
        assertTrue(minimal.pills.isEmpty() && minimal.badges.isEmpty() && minimal.langs.isEmpty() && minimal.file.isEmpty() && minimal.tags.isEmpty())
        assertEquals("53.75 GB", minimal.size)
    }

    @Test fun aRowNamingNoFile_usesAReleaseLikeFirstLine() {
        val s = StreamItem("Torrentio\n1080p", "The Dark Knight 2008 1080p BluRay x264 DTS-HD MA 5.1\n👤 61 💾 12.4 GB ⚙️ 1337x", "https://h.example/a")
        val c = StreamCards.build(s, "Torrentio", "The Dark Knight", StreamCards.RELEASE, "initial")
        assertEquals("The Dark Knight 2008", c.title)
        assertEquals("1080p", c.res)
        assertTrue(c.file.startsWith("The Dark Knight 2008 1080p"))
        val plain = StreamCards.build(StreamItem("Server 2", "Fast mirror · English", "https://h.example/b"), "4KHDHub", "The Dark Knight", StreamCards.RELEASE, "initial")
        assertEquals("Server 2", plain.title)
        assertEquals("", plain.file)
        assertTrue(plain.desc.contains("Fast mirror"))
    }

    @Test fun designs_specsAndTheCustomOne() {
        assertNull(StreamCards.specFor("classic", ""))
        assertEquals(StreamCards.RELEASE, StreamCards.specFor("release", ""))
        assertEquals(StreamCards.RELEASE, StreamCards.specFor("nonsense", ""))
        assertTrue(StreamCards.specFor("line", "")!!.row)
        assertEquals(StreamCards.RELEASE, StreamCards.specFor("custom", ""))          // never saved: every part on
        val c = StreamCards.RELEASE.copy(layout = "row", dense = true).with("langs", false).with("file", false)
        val back = StreamCards.decode(StreamCards.encode(c))
        assertEquals(c, back)
        assertFalse(back.has("langs")); assertTrue(back.has("tags"))
        assertEquals(6, StreamCards.DESIGNS.size)
        assertEquals("release", StreamCards.DESIGNS[0].id)
    }
}
