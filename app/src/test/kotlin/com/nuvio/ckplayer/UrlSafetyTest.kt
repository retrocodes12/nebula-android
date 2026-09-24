package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-09-24 security pass: what an add-on, a party or the cloud's relay list may name. */
class UrlSafetyTest {
    @Test fun lanHost_onlyPrivateLanAddresses() {
        for (h in listOf("192.168.1.20", "10.0.0.5", "172.16.0.1", "172.31.255.254")) assertTrue(h, lanHost(h))
        for (h in listOf("93.184.216.34", "172.32.0.1", "172.15.0.1", "127.0.0.1", "169.254.169.254", "100.64.0.1",
                "evil.example", "192.168.1.20.evil.example", "", "::1", "192.168.1")) assertFalse(h, lanHost(h))
    }

    @Test fun webUrl_keepsHttpAndHttpsOnly() {
        assertEquals("https://cdn.example/a.mpd", webUrl("https://cdn.example/a.mpd"))
        assertEquals("http://1.2.3.4/x.mkv", webUrl(" http://1.2.3.4/x.mkv "))
        assertEquals("HTTPS://X.EXAMPLE/a", webUrl("HTTPS://X.EXAMPLE/a"))
        for (u in listOf("file:///sdcard/secret.txt", "content://com.android.contacts/data", "javascript:alert(1)",
                "data:video/mp4;base64,AAAA", "", null, "ftp://x/y")) assertEquals(u.toString(), "", webUrl(u))
    }
}
