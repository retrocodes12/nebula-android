package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stream row that is a web player page: which rows are, and which hosts a play reads through the page. */
class DeviceEmbedTest {
    @Test fun parse_onlyTheAddOnsPageHint() {
        assertTrue(DeviceEmbed.parse(JSONObject("""{"notWebReady":true,"nebulaEmbed":{"v":1,"capture":"hls"}}""")))
        assertFalse(DeviceEmbed.parse(JSONObject("""{"nebulaEmbed":{"v":1,"capture":"dash"}}""")))
        assertFalse(DeviceEmbed.parse(JSONObject("""{"notWebReady":true}""")))
        assertFalse(DeviceEmbed.parse(null))
    }

    @Test fun aPageIsRememberedByItsAddress_andForgottenWhenTheListSaysOtherwise() {
        val page = "https://taifood-blog.asia/embed/nhl/2026-10-29/mtl-dal"
        DeviceEmbed.note(page, true)
        assertTrue(DeviceEmbed.isPage(page))
        assertFalse(DeviceEmbed.isPage("https://taifood-blog.asia/embed/nhl/2026-10-29/van-sj"))
        DeviceEmbed.note(page, false)
        assertFalse(DeviceEmbed.isPage(page))
    }

    @Test fun playlistHosts_namesAbsoluteLinesAndKeys_notRelativeOnes() {
        val master = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1280x720
https://pl.whywelove2.space/playlist/58855/a.m3u8
#EXT-X-KEY:METHOD=AES-128,URI="https://Keys.Example.net:8443/k"
tracks-v1a1/mono.ts.m3u8
""".toByteArray()
        assertEquals(setOf("pl.whywelove2.space", "keys.example.net"), DeviceEmbed.playlistHosts(master))
        assertTrue(DeviceEmbed.playlistHosts("<html>https://a.example/x</html>".toByteArray()).isEmpty())
    }
}
