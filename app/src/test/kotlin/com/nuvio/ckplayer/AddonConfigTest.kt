package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An add-on's settings page: which add-ons have one, where it is, and what it hands back. */
class AddonConfigTest {
    private fun addon(url: String, on: Boolean = true) = Addon(url, url, url, enabled = on)

    @Test fun configurable_readsEitherHint() {
        assertTrue(AddonConfig.configurable(JSONObject("""{"behaviorHints":{"configurable":true}}""")))
        assertTrue(AddonConfig.configurable(JSONObject("""{"behaviorHints":{"configurationRequired":true}}""")))
        assertFalse(AddonConfig.configurable(JSONObject("""{"behaviorHints":{"configurable":false}}""")))
        assertFalse(AddonConfig.configurable(JSONObject("""{"name":"x"}""")))
    }

    @Test fun pageUrl_keepsTheSettingsAlreadyInTheAddress() {
        assertEquals("https://a.example/configure", AddonConfig.pageUrl("https://a.example/manifest.json"))
        assertEquals("https://a.example/sort=size|lang=en/configure", AddonConfig.pageUrl("https://a.example/sort=size|lang=en/manifest.json"))
        assertEquals("https://a.example/x/configure?k=1", AddonConfig.pageUrl("https://a.example/x/manifest.json?k=1"))
        assertNull(AddonConfig.pageUrl("https://a.example/other.json"))
        assertNull(AddonConfig.pageUrl("file:///sdcard/manifest.json"))
    }

    @Test fun installLink_takesEveryShapeAPageHandsOver() {
        assertEquals("https://a.example/k=1/manifest.json", AddonConfig.installLink("stremio://a.example/k=1/manifest.json"))
        assertEquals("https://a.example/k=1/manifest.json", AddonConfig.installLink("https://a.example/k=1/manifest.json"))
        assertEquals(
            "https://a.example/k=1/manifest.json",
            AddonConfig.installLink("https://web.stremio.com/#/addons?addon=https%3A%2F%2Fa.example%2Fk%3D1%2Fmanifest.json"),
        )
    }

    @Test fun installLink_leavesEveryOtherNavigationAlone() {
        for (u in listOf("https://a.example/configure", "https://a.example/manifest.json.html", "stremio://detail/movie/tt1",
                "intent://a.example/manifest.json#Intent;end", "file:///manifest.json", "javascript:alert(1)", "", null,
                "https://evil.example/?addon=https%3A%2F%2Fa.example%2Fmanifest.json")) {
            assertNull(u.toString(), AddonConfig.installLink(u))
        }
    }

    @Test fun host_nameTheRealSiteOnly() {
        assertEquals("a.example", AddonConfig.host("https://a.example/secret-key/manifest.json"))
        assertEquals("a.example:8080", AddonConfig.host("http://a.example:8080/manifest.json"))
        assertEquals("evil.example", AddonConfig.host("https://a.example@evil.example/manifest.json"))
        assertEquals("", AddonConfig.host("stremio://a.example/manifest.json"))
    }

    @Test fun replaced_keepsRankAndOnOff() {
        val list = listOf(addon("https://a/manifest.json"), addon("https://b/manifest.json", on = false), addon("https://c/manifest.json"))
        val out = AddonConfig.replaced(list, "https://b/manifest.json", addon("https://b/k=1/manifest.json"))
        assertEquals(listOf("https://a/manifest.json", "https://b/k=1/manifest.json", "https://c/manifest.json"), out.map { it.manifestUrl })
        assertFalse(out[1].enabled)
    }

    @Test fun replaced_neverListsAnAddOnTwice() {
        val list = listOf(addon("https://a/manifest.json"), addon("https://b/manifest.json"), addon("https://b/k=1/manifest.json"))
        val out = AddonConfig.replaced(list, "https://a/manifest.json", addon("https://b/k=1/manifest.json"))
        assertEquals(listOf("https://b/k=1/manifest.json", "https://b/manifest.json"), out.map { it.manifestUrl })
    }

    @Test fun replaced_addsItWhenASyncRemovedTheOldOne() {
        val out = AddonConfig.replaced(listOf(addon("https://a/manifest.json")), "https://gone/manifest.json", addon("https://b/manifest.json"))
        assertEquals(listOf("https://a/manifest.json", "https://b/manifest.json"), out.map { it.manifestUrl })
    }
}
