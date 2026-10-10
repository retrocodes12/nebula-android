package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which HTTP answers a reading failure is asked again for (2026-10-02: "ERROR_CODE_IO_UNSPECIFIED (2000)" mid-film). */
class IoRetryTest {
    @Test fun serverTroubleIsAskedAgainARefusalIsNot() {
        for (c in listOf(500, 502, 503, 504, 520, 408, 429)) assertTrue("$c", httpWorthRetry(c))
        for (c in listOf(400, 401, 403, 404, 410, 416, 451)) assertFalse("$c", httpWorthRetry(c))
    }
}

/** A link the host stopped honouring mid-play is asked of the add-on again — for the same row, never another release. */
class FreshLinkTest {
    private val pengu = Addon("https://pengu.test/manifest.json", "PenguPlay", "https://pengu.test")
    private val other = Addon("https://other.test/manifest.json", "Other", "https://other.test")
    private fun row(name: String, title: String, url: String, binge: String = "") = StreamItem(name, title, url, bingeGroup = binge)

    @Test fun refusalsAreTheLinkNotTheServer() {
        for (c in listOf(401, 403, 404, 410)) org.junit.Assert.assertTrue("$c", linkRefusedCode(c))
        for (c in listOf(200, 416, 429, 500, 503)) org.junit.Assert.assertFalse("$c", linkRefusedCode(c))
    }

    @Test fun theSameAddressWhenItIsStillListed() {
        val cur = row("PenguPlay 1080p", "The Mentalist S04E02 WEB-DL", "https://pengu.test/p/abc?sig=1")
        val got = StallWatch.fresh(listOf(row("x", "y", "https://other.test/1") to other, cur to pengu), cur.url, null)
        assertEquals(cur.url, got?.first?.url)
    }

    @Test fun aFreshlySignedTwinFromTheSameAddon() {
        val old = row("PenguPlay 1080p", "The Mentalist S04E02 WEB-DL x264", "https://pengu.test/p/abc?sig=OLD", binge = "pengu-1080-web")
        val sig = StreamTwin.sig(old, pengu)
        val cands = listOf(
            row("Other 1080p", "The Mentalist S04E02 WEB-DL x264", "https://other.test/1", binge = "pengu-1080-web") to other,
            row("PenguPlay 720p", "The Mentalist S04E02 HDTV", "https://pengu.test/p/def?sig=NEW") to pengu,
            row("PenguPlay 1080p", "The Mentalist S04E02 WEB-DL x264", "https://pengu.test/p/abc?sig=NEW", binge = "pengu-1080-web") to pengu,
        )
        val got = StallWatch.fresh(cands, old.url, sig)
        assertEquals("https://pengu.test/p/abc?sig=NEW", got?.first?.url)
    }

    @Test fun noTwinNoGuess() {
        val old = row("PenguPlay 4K", "The Mentalist S04E02 2160p REMUX", "https://pengu.test/p/old", binge = "pengu-4k")
        val cands = listOf(row("PenguPlay 480p", "The Mentalist S04E02 SD", "https://pengu.test/p/sd") to pengu)
        assertEquals(null, StallWatch.fresh(cands, old.url, StreamTwin.sig(old, pengu)))
    }
}
