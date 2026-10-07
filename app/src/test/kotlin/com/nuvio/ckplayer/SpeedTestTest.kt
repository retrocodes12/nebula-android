package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

/** The speed test's pure parts (branch speedtest): the verdicts, the window maths, HLS and DASH plans. */
class SpeedTestTest {

    // ---- verdicts and words ----

    @Test fun verdict_thresholdsAreHalfAgainAndATenthMore() {
        val need = 10_000_000L
        assertEquals(SpeedTest.Verdict.SMOOTH, SpeedTest.verdict(15_000_000, need))      // exactly 1.5 ×
        assertEquals(SpeedTest.Verdict.SHOULD, SpeedTest.verdict(14_999_999, need))
        assertEquals(SpeedTest.Verdict.SHOULD, SpeedTest.verdict(11_000_000, need))      // exactly 1.1 × (not "may stall")
        assertEquals(SpeedTest.Verdict.STALL, SpeedTest.verdict(10_999_999, need))
        assertEquals(SpeedTest.Verdict.STALL, SpeedTest.verdict(0, need))
        assertEquals(SpeedTest.Verdict.UNKNOWN, SpeedTest.verdict(50_000_000, 0))
    }

    @Test fun mbps_wholeFromTenUp_oneDecimalBelow() {
        assertEquals("92 Mbps", SpeedTest.mbps(92_000_000))
        assertEquals("10 Mbps", SpeedTest.mbps(9_960_000))
        assertEquals("9.9 Mbps", SpeedTest.mbps(9_940_000))
        assertEquals("6.4 Mbps", SpeedTest.mbps(6_400_000))
        assertEquals("6 Mbps", SpeedTest.mbps(6_030_000))
        assertEquals("under 0.1 Mbps", SpeedTest.mbps(40_000))
    }

    @Test fun line_saysTheVerdictInWords_orJustTheSpeed() {
        assertEquals("48 Mbps · plays smoothly", SpeedTest.line(48_000_000, 30_000_000))
        assertEquals("14 Mbps · should play", SpeedTest.line(14_000_000, 12_000_000))
        assertEquals("6 Mbps · may stall", SpeedTest.line(6_000_000, 8_000_000))
        assertEquals("31 Mbps", SpeedTest.line(31_000_000, 0))
    }

    @Test fun rowWords_coverEveryState() {
        assertEquals("Testing…", RowSpeed.Testing().words())
        assertEquals("Testing… 23 Mbps", RowSpeed.Testing(23_000_000).words())
        assertEquals("48 Mbps · plays smoothly", RowSpeed.Done(48_000_000, 30_000_000).words())
        assertEquals("Couldn’t reach it", RowSpeed.Failed.words())
        assertEquals("Can’t test this kind of stream", RowSpeed.Unsupported.words())
        assertEquals(RowSpeed.Unsupported, SpeedTest.Result(unsupported = true).toRow())
        assertEquals(RowSpeed.Failed, SpeedTest.Result(failed = true).toRow())
        assertEquals(RowSpeed.Done(5, 7), SpeedTest.Result(bps = 5, need = 7).toRow())
    }

    @Test fun tone_isTheVerdictsColour_noneWithoutAVerdict() {
        assertEquals(OkC, RowSpeed.Done(48_000_000, 30_000_000).tone())
        assertEquals(AmberC, RowSpeed.Done(14_000_000, 12_000_000).tone())
        assertEquals(BadC, RowSpeed.Done(6_000_000, 8_000_000).tone())
        assertNull(RowSpeed.Done(31_000_000, 0).tone())
        assertNull(RowSpeed.Testing().tone())
    }

    @Test fun agoText_readsLikeAPerson() {
        val now = 1_800_000_000_000L
        assertEquals("just now", agoText(now - 30_000, now))
        assertEquals("5 min ago", agoText(now - 5 * 60_000, now))
        assertEquals("3 h ago", agoText(now - 3 * 3_600_000, now))
        assertEquals("yesterday", agoText(now - 30 * 3_600_000L, now))
        assertEquals("4 days ago", agoText(now - 4 * 86_400_000L, now))
    }

    // ---- the window maths ----

    @Test fun windowBps_leavesTheRampOut() {
        val s = listOf(250L to 1_000_000L, 500L to 2_000_000L, 1_000L to 4_000_000L, 8_000L to 32_000_000L)
        assertEquals(32_000_000L, SpeedTest.windowBps(s, 500))            // 30 MB over 7.5 s
        assertEquals(32_000_000L, SpeedTest.windowBps(s, 750))            // the bytes at 750 ms read off the line: 3 MB
        assertEquals(0L, SpeedTest.windowBps(s, 8_000))
        assertEquals(0L, SpeedTest.windowBps(emptyList(), 0))
    }

    @Test fun bytesAt_startsFromNothingAtZero() {
        assertEquals(500L, SpeedTest.bytesAt(listOf(100L to 1_000L), 50))
        assertEquals(1_000L, SpeedTest.bytesAt(listOf(100L to 1_000L), 400))
    }

    @Test fun median_ofOddAndEvenCounts() {
        assertEquals(3L, SpeedTest.median(listOf(5L, 1L, 3L)))
        assertEquals(3L, SpeedTest.median(listOf(4L, 2L)))
        assertEquals(0L, SpeedTest.median(emptyList()))
    }

    @Test fun testable_onlyWebAddresses_neverP2p() {
        assertTrue(SpeedTest.testable(StreamItem("n", "t", "https://x.example/y.mp4")))
        assertFalse(SpeedTest.testable(StreamItem("n", "t", "nebula-p2p://" + "a".repeat(40) + "/0")))
        assertFalse(SpeedTest.testable(StreamItem("n", "t", "https://x.example/y.mp4", infoHash = "a".repeat(40))))
        assertFalse(SpeedTest.testable(StreamItem("n", "t", "rtsp://x.example/live")))
    }

    // ---- what an address is, when its Content-Type does not say ----

    @Test fun sniffKind_playlistsAndManifestsByTheirFirstBytes() {
        assertEquals("hls", SpeedPlans.sniffKind("#EXTM3U\n#EXT-X-VERSION:3\n"))
        assertEquals("hls", SpeedPlans.sniffKind("\uFEFF#EXTM3U\n"))                 // a byte-order mark
        assertEquals("hls", SpeedPlans.sniffKind("\r\n  #EXTM3U\n"))                 // white space first
        assertEquals("dash", SpeedPlans.sniffKind("<?xml version=\"1.0\"?>\n<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\">"))
        assertNull(SpeedPlans.sniffKind("\u0000\u0000\u0000\u0018ftypmp42"))           // an MP4's own start: a file
        assertNull(SpeedPlans.sniffKind("<html><body>Not found</body></html>"))
        assertNull(SpeedPlans.sniffKind("x#EXTM3U"))
        assertNull(SpeedPlans.sniffKind(""))
    }

    // ---- HLS ----

    @Test fun hlsVariants_readBandwidth_notAverage_andResolveAddresses() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,AVERAGE-BANDWIDTH=1000000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:CODECS="avc1.640028,mp4a.40.2",BANDWIDTH=6400000,RESOLUTION=1920x1080
            https://cdn.example.com/hi/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000
            ../mid/index.m3u8?token=a,b
        """.trimIndent()
        val v = SpeedPlans.hlsVariants(master, "https://host.example/path/master.m3u8")
        assertEquals(
            listOf(
                SpeedPlans.Variant(1_280_000, "https://host.example/path/low/index.m3u8"),
                SpeedPlans.Variant(6_400_000, "https://cdn.example.com/hi/index.m3u8"),
                SpeedPlans.Variant(2_560_000, "https://host.example/mid/index.m3u8?token=a,b"),
            ),
            v,
        )
        assertEquals(6_400_000L, v.maxBy { it.bandwidth }.bandwidth)
        assertTrue(SpeedPlans.hlsVariants("#EXTM3U\n#EXTINF:6,\nseg1.ts\n", "https://h.example/a.m3u8").isEmpty())
    }

    @Test fun attr_quotedValuesMayHoldCommas() {
        assertEquals("5", SpeedPlans.attr("CODECS=\"a,b\",BANDWIDTH=5", "BANDWIDTH"))
        assertEquals("a,b", SpeedPlans.attr("BANDWIDTH=5,CODECS=\"a,b\"", "CODECS"))
        assertNull(SpeedPlans.attr("BANDWIDTH=5", "RESOLUTION"))
    }

    @Test fun hlsMedia_initAndSegmentsInOrder_endlistMeansNotLive() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg1.m4s
            #EXTINF:6.0,
            seg2.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val m = SpeedPlans.hlsMedia(media, "https://h.example/v/index.m3u8")
        assertEquals(SpeedPlans.Seg("https://h.example/v/init.mp4"), m.init)
        assertEquals(listOf("https://h.example/v/seg1.m4s", "https://h.example/v/seg2.m4s"), m.segs.map { it.url })
        assertFalse(m.live)
    }

    @Test fun hlsMedia_byteRangesFollowOnFromThePreviousOne() {
        val media = """
            #EXTM3U
            #EXT-X-MAP:URI="main.mp4",BYTERANGE="720@0"
            #EXTINF:6,
            #EXT-X-BYTERANGE:1000@720
            main.mp4
            #EXTINF:6,
            #EXT-X-BYTERANGE:2000
            main.mp4
        """.trimIndent()
        val m = SpeedPlans.hlsMedia(media, "https://h.example/v/index.m3u8")
        assertEquals(SpeedPlans.Seg("https://h.example/v/main.mp4", 0, 720), m.init)
        assertEquals(
            listOf(SpeedPlans.Seg("https://h.example/v/main.mp4", 720, 1000), SpeedPlans.Seg("https://h.example/v/main.mp4", 1720, 2000)),
            m.segs,
        )
        assertTrue(m.live)
    }

    // ---- DASH ----

    private fun plan(xml: String, url: String, now: Long = System.currentTimeMillis()) = SpeedPlans.dashPlan(xml.trimIndent(), url, KXmlParser(), now)

    @Test fun dash_numberTemplate_bestVideo_throughTheBaseUrlChain() {
        val p = plan("""
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT10M">
              <BaseURL>https://cdn.example/a/</BaseURL>
              <Period>
                <BaseURL>p1/</BaseURL>
                <AdaptationSet mimeType="audio/mp4">
                  <SegmentTemplate media="a-${'$'}Number${'$'}.m4s" initialization="a-init.mp4" duration="4"/>
                  <Representation id="aud" bandwidth="9000000"/>
                </AdaptationSet>
                <AdaptationSet mimeType="video/mp4">
                  <BaseURL>video/</BaseURL>
                  <SegmentTemplate media="${'$'}RepresentationID${'$'}/seg-${'$'}Number%05d${'$'}.m4s" initialization="${'$'}RepresentationID${'$'}/init.mp4" startNumber="3" timescale="1000" duration="4000"/>
                  <Representation id="v720" bandwidth="3000000" width="1280" height="720"/>
                  <Representation id="v1080" bandwidth="6000000" width="1920" height="1080"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """, "https://origin.example/x/manifest.mpd")
        assertNotNull(p)
        p!!
        assertEquals(6_000_000L, p.need)                                   // the best VIDEO, not the 9 Mbps audio
        assertEquals("https://cdn.example/a/p1/video/v1080/init.mp4", p.init)
        assertEquals(6, p.media.size)
        assertEquals("https://cdn.example/a/p1/video/v1080/seg-00003.m4s", p.media.first())
        assertEquals("https://cdn.example/a/p1/video/v1080/seg-00008.m4s", p.media.last())
        assertNull(p.single)
    }

    @Test fun dash_timelineTemplate_timesFromTheSRows() {
        val p = plan("""
            <MPD type="static">
              <Period>
                <AdaptationSet contentType="video">
                  <SegmentTemplate timescale="1000" media="v/${'$'}Bandwidth${'$'}/${'$'}Time${'$'}.m4s" initialization="v/${'$'}Bandwidth${'$'}/init.m4s">
                    <SegmentTimeline>
                      <S t="100" d="2000" r="2"/>
                      <S d="1000"/>
                      <S d="3000" r="5"/>
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="1" bandwidth="2500000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """, "https://h.example/live/manifest.mpd")!!
        assertEquals(2_500_000L, p.need)
        assertEquals("https://h.example/live/v/2500000/init.m4s", p.init)
        assertEquals(
            listOf(100, 2100, 4100, 6100, 7100, 10100).map { "https://h.example/live/v/2500000/$it.m4s" },
            p.media,
        )
    }

    @Test fun timelineSegments_repeatAndContinue() {
        val rows = listOf(longArrayOf(0, 10, 1), longArrayOf(-1, 5, 0))
        assertEquals(listOf(1L to 0L, 2L to 10L, 3L to 20L), SpeedPlans.timelineSegments(rows, 1, 10))
        assertEquals(listOf(1L to 0L, 2L to 10L), SpeedPlans.timelineSegments(rows, 1, 2))
    }

    @Test fun dash_liveNumberedByTime_takesTheNewestShortOfTheEdge() {
        val ast = 1_791_331_200_000L                                       // 2026-10-07T00:00:00Z
        val p = plan("""
            <MPD type="dynamic" availabilityStartTime="2026-10-07T00:00:00Z">
              <Period start="PT0S">
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate media="s-${'$'}Number${'$'}.m4s" initialization="i.mp4" startNumber="1" duration="2"/>
                  <Representation id="v" bandwidth="4000000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """, "https://h.example/l/m.mpd", now = ast + 100_000)!!
        // 100 s at 2 s a segment from number 1: the edge is 51 — the six before the last two
        assertEquals((44..49).map { "https://h.example/l/s-$it.m4s" }, p.media)
    }

    @Test fun dash_segmentBase_isOneFileToRead() {
        val p = plan("""
            <MPD type="static"><Period><AdaptationSet mimeType="video/mp4">
              <Representation id="v" bandwidth="1400000"><BaseURL>v-576p.mp4</BaseURL><SegmentBase indexRange="0-100"/></Representation>
              <Representation id="w" bandwidth="800000"><BaseURL>v-360p.mp4</BaseURL><SegmentBase indexRange="0-100"/></Representation>
            </AdaptationSet></Period></MPD>
        """, "https://h.example/x/m.mpd")!!
        assertEquals("https://h.example/x/v-576p.mp4", p.single)
        assertEquals(1_400_000L, p.need)
        assertTrue(p.media.isEmpty())
    }

    @Test fun dash_segmentListOrNothingToFetch_cannotBeTested() {
        assertNull(plan("""
            <MPD><Period><AdaptationSet mimeType="video/mp4"><Representation id="v" bandwidth="1">
              <SegmentList><SegmentURL media="a.m4s"/></SegmentList>
            </Representation></AdaptationSet></Period></MPD>
        """, "https://h.example/m.mpd"))
        assertNull(plan("""
            <MPD><Period><AdaptationSet mimeType="video/mp4"><Representation id="v" bandwidth="1"/></AdaptationSet></Period></MPD>
        """, "https://h.example/m.mpd"))
    }

    @Test fun fill_identifiersWidthsAndDollars() {
        assertEquals("\$x007-9-r-5", SpeedPlans.fill("\$\$x\$Number%03d\$-\$Time\$-\$RepresentationID\$-\$Bandwidth\$", "r", 5, 7, 9))
    }

    @Test fun durationMs_readsXsDuration() {
        assertEquals(3_723_500L, SpeedPlans.durationMs("PT1H2M3.5S"))
        assertEquals(10_000L, SpeedPlans.durationMs("P0Y0M0DT0H0M10S"))
        assertEquals(86_400_000L, SpeedPlans.durationMs("P1D"))
        assertEquals(0L, SpeedPlans.durationMs("soon"))
    }

    @Test fun isoMs_zoneOrNoZoneIsUtc() {
        assertEquals(1_791_331_200_000L, SpeedPlans.isoMs("2026-10-07T00:00:00Z"))
        assertEquals(1_791_331_200_000L, SpeedPlans.isoMs("2026-10-07T00:00:00"))
        assertEquals(1_791_331_200_000L, SpeedPlans.isoMs("2026-10-07T05:30:00+05:30"))
        assertNull(SpeedPlans.isoMs("yesterday"))
    }
}
