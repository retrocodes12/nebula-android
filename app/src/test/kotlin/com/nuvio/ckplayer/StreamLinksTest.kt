package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stream row that only opens a web page: which rows are kept as links, and the code a TV shows for one. */
class StreamLinksTest {
    // the sports add-on's two cards on a locked channel, as it sends them (2026-11-16)
    private val ad = JSONObject(
        """{"name":"▶ Watch an ad to unlock Live TV (12 h)","title":"Opens one ad in your browser.\nCome back and reopen this channel.\nEvery Live TV channel then plays for 12 hours.","externalUrl":"https://sports.nebulapro.xyz/sports/go/c/0123456789abcdef0123456789abcdef01234567","behaviorHints":{"notWebReady":true,"bingeGroup":"nebula-sports-channel-ad"}}"""
    )
    private val donate = JSONObject(
        """{"name":"Donate ${'$'}1.50 or more to unlock streams","title":"Donations only cover development and server expenses.","externalUrl":"https://play.rifflehq.in/support.html#from=sports-addon","behaviorHints":{"notWebReady":true}}"""
    )

    @Test fun theSportsCards_areLinksWithTheirOwnWords() {
        val a = StreamLinks.of(ad)!!
        assertEquals("▶ Watch an ad to unlock Live TV (12 h)", a.name)
        assertTrue(a.title.startsWith("Opens one ad in your browser.\nCome back"))
        assertTrue(a.url.startsWith("https://sports.nebulapro.xyz/sports/go/c/"))
        assertEquals("https://play.rifflehq.in/support.html#from=sports-addon", StreamLinks.of(donate)!!.url)
    }

    @Test fun onlyWebAddresses_andOnlyRowsWithSomethingToSay() {
        assertNull(StreamLinks.of(JSONObject("""{"name":"Open","externalUrl":"stremio:///detail/movie/tt1"}""")))
        assertNull(StreamLinks.of(JSONObject("""{"name":"Open","externalUrl":"javascript:alert(1)"}""")))
        assertNull(StreamLinks.of(JSONObject("""{"externalUrl":"https://example.com/"}""")))
        assertNull(StreamLinks.of(JSONObject("""{"name":"No link"}""")))
        assertEquals("Read me", StreamLinks.of(JSONObject("""{"description":"Read me","externalUrl":"http://example.com/"}"""))!!.title)
    }

    @Test fun aTvCode_holdsTheLongestAddressTheAddOnSends() {
        val m = StreamLinks.qr(StreamLinks.of(ad)!!.url)
        assertNotNull(m)
        assertTrue(m!!.width >= 21 && m.width == m.height)
    }
}
