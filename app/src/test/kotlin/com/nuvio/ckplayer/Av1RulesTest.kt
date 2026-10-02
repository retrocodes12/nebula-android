package com.nuvio.ckplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words that decide an AV1 row and a software decoder (2026-10-02: AV1 on a phone with no AV1 chip, 1316 of 1590 dropped). */
class Av1RulesTest {
    @Test fun av1AsAWordOfTheRow() {
        assertTrue(StreamBadges.mentionsAv1("Movie.2026.1080p.WEB-DL.AV1.Opus-GRP"))
        assertTrue(StreamBadges.mentionsAv1("1080p · AV1 10bit"))
        assertTrue(StreamBadges.mentionsAv1("av1"))
        assertTrue(StreamBadges.mentionsAv1("Torrentio\n4k\n[AV1]"))
        assertFalse(StreamBadges.mentionsAv1("decoded by dav1d"))
        assertFalse(StreamBadges.mentionsAv1("SAV1 pack"))
        assertFalse(StreamBadges.mentionsAv1("AV10 remix"))
        assertFalse(StreamBadges.mentionsAv1("Movie.2026.1080p.WEB-DL.x265.HEVC"))
    }

    @Test fun softwareDecoderNames() {
        assertTrue(isSoftwareDecoderName("c2.android.av1.decoder"))
        assertTrue(isSoftwareDecoderName("c2.android.av1-dav1d.decoder"))
        assertTrue(isSoftwareDecoderName("OMX.google.h264.decoder"))
        assertTrue(isSoftwareDecoderName("ffmpegLib-libdav1d"))
        assertFalse(isSoftwareDecoderName("c2.exynos.h264.decoder"))
        assertFalse(isSoftwareDecoderName("c2.qti.av1.decoder"))
        assertFalse(isSoftwareDecoderName("OMX.MTK.VIDEO.DECODER.AVC"))
        assertFalse(isSoftwareDecoderName(null))
    }
}
