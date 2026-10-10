package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stream's own request headers: what is taken from an add-on, and which play sends them. */
class StreamHeadersTest {
    private fun hints(json: String) = JSONObject(json)

    @After fun clear() {
        StreamHeaders.active = null
    }

    @Test fun parse_takesTheRequestHeaders_inOneSpelling() {
        val h = StreamHeaders.parse(hints("""{"proxyHeaders":{"request":{"referer":"https://iplayer.is/","ORIGIN":"https://iplayer.is","user-agent":"Mozilla/5.0"}}}"""))
        assertEquals(mapOf("Referer" to "https://iplayer.is/", "Origin" to "https://iplayer.is", "User-Agent" to "Mozilla/5.0"), h)
    }

    @Test fun parse_refusesFramingLineBreaksAndNonStrings() {
        val h = StreamHeaders.parse(hints("""{"proxyHeaders":{"request":{
            "Host":"evil.example","Range":"bytes=0-1","Content-Length":"9","X-Nebula-Client":"web",
            "Referer":"https://a/\r\nX-Injected: 1","Bad Name":"x","Cookie":"","Origin":5,"Referer2":"ok"}}}"""))
        assertEquals(mapOf("Referer2" to "ok"), h)
    }

    @Test fun parse_noHintsNoHeaders() {
        assertTrue(StreamHeaders.parse(null).isEmpty())
        assertTrue(StreamHeaders.parse(hints("""{"bingeGroup":"x"}""")).isEmpty())
        assertTrue(StreamHeaders.parse(hints("""{"proxyHeaders":{"response":{"a":"b"}}}""")).isEmpty())
    }

    @Test fun aPlaySendsTheHeadersItsListGave_andOnlyItsOwnClaimClearsThem() {
        val url = "https://netanjahu.hls.st/t/abc/index.m3u8"
        StreamHeaders.note(url, mapOf("Referer" to "https://iplayer.is/"))
        val first = StreamHeaders.begin(url)
        assertEquals("https://iplayer.is/", StreamHeaders.active?.headers?.get("Referer"))
        // a swap: the next player opens before the old one goes away; the old one's end must not clear the new play
        val second = StreamHeaders.begin(url)
        StreamHeaders.end(first)
        assertSame(second, StreamHeaders.active)
        StreamHeaders.end(second)
        assertNull(StreamHeaders.active)
    }

    @Test fun anAddressWithoutHeadersSendsNone_andALaterListWithoutThemForgetsThem() {
        val url = "https://cdn.example/live.m3u8"
        StreamHeaders.note(url, mapOf("Referer" to "https://a/"))
        StreamHeaders.note(url, emptyMap())
        assertNull(StreamHeaders.begin(url))
        assertNull(StreamHeaders.active)
    }
}
