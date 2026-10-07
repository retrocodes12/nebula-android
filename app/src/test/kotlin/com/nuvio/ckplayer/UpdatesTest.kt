package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update card: release notes made plain, and which version counts as newer. */
class UpdatesTest {
    @Test fun cleanNotes_markdownBecomesPlainText() {
        val raw = "## What's new\r\n- Fixed [the stall pill](https://github.com/x/y/pull/1)\r\n\r\n\r\n\r\n**Faster** `startup`"
        assertEquals("What's new\n• Fixed the stall pill\n\nFaster startup", Updates.cleanNotes(raw))
    }

    @Test fun cleanNotes_imagesGo() {
        assertEquals("Hello", Updates.cleanNotes("![screenshot](https://x/y.png) Hello"))
    }

    @Test fun isNewer_comparesNumbersNotText() {
        assertTrue(Updates.isNewer("1.10.0", "1.9.0"))       // as text "1.10.0" < "1.9.0"
        assertTrue(Updates.isNewer("1.79.0", "1.78.0"))
        assertFalse(Updates.isNewer("1.78.0", "1.78.0"))
        assertFalse(Updates.isNewer("1.77.9", "1.78.0"))
    }

    @Test fun isNewer_missingPartsCountAsZero() {
        assertFalse(Updates.isNewer("1.78", "1.78.0"))
        assertTrue(Updates.isNewer("1.78.1", "1.78"))
    }

    // ---- early builds (2026-09-28): X.Y.Z-beta.N ----

    @Test fun compare_releaseOutranksItsBetas_betasByNumber() {
        // the order the task names: 1.84.0 > 1.84.0-beta.2 > 1.84.0-beta.1 > 1.83.0
        val order = listOf("1.84.0", "1.84.0-beta.2", "1.84.0-beta.1", "1.83.0")
        for (i in order.indices) for (j in order.indices) {
            val c = Updates.compareVersions(order[i], order[j])!!
            when {
                i < j -> assertTrue("${order[i]} > ${order[j]}", c > 0)
                i > j -> assertTrue("${order[i]} < ${order[j]}", c < 0)
                else -> assertEquals(0, c)
            }
        }
    }

    @Test fun compare_betaNumbersAreNumbers_andVPrefixIsDropped() {
        assertTrue(Updates.isNewer("1.84.0-beta.10", "1.84.0-beta.9"))
        assertTrue(Updates.isNewer("v1.84.0-beta.1", "1.83.0"))
        assertFalse(Updates.isNewer("1.84.0-beta.1", "v1.84.0-beta.1"))
        assertTrue(Updates.isNewer("1.84.0-rc.1", "1.84.0-beta.3"))        // words compare as text: beta < rc
        assertTrue(Updates.isNewer("1.84.0-beta.1.1", "1.84.0-beta.1"))    // a longer run of equal parts is newer
    }

    @Test fun isNewer_turningEarlyBuildsOffNeverOffersADowngrade() {
        // on 1.84.0-beta.1 with only the release offered: 1.83.0 is not newer; 1.84.0 is, when it comes
        assertFalse(Updates.isNewer("1.83.0", "1.84.0-beta.1"))
        assertTrue(Updates.isNewer("1.84.0", "1.84.0-beta.1"))
        assertFalse(Updates.isNewer("1.84.0-beta.1", "1.84.0"))
    }

    // ---- the speed test's own build (branch speedtest): X.Y.Z-speedtest.N, below its release like an early build ----

    @Test fun compare_speedtestBuildIsBelowItsRelease_aboveTheOneBefore() {
        assertTrue(Updates.isNewer("1.88.0", "1.88.0-speedtest.1"))           // 1.88.0 is offered over the test build
        assertFalse(Updates.isNewer("1.88.0-speedtest.1", "1.88.0"))
        assertFalse(Updates.isNewer("1.87.0", "1.88.0-speedtest.1"))          // the released 1.87.0 is not offered over it
        assertTrue(Updates.isNewer("1.88.0-speedtest.1", "1.87.0"))
        assertTrue(Updates.isNewer("1.88.0-speedtest.2", "1.88.0-speedtest.1"))
        assertEquals("1.88.0-speedtest.1", Updates.cleanVersion("v1.88.0-speedtest.1"))
    }

    @Test fun isNewer_garbageIsNeverNewer() {
        assertFalse(Updates.isNewer("latest", "1.0.0"))
        assertFalse(Updates.isNewer("1.2.0", ""))
        assertNull(Updates.compareVersions("1.2.0-", "1.2.0"))
        assertNull(Updates.compareVersions("1.2.0-beta..1", "1.2.0"))
    }

    @Test fun cleanVersion_acceptsBetas_rejectsNoise() {
        assertEquals("1.84.0-beta.1", Updates.cleanVersion("v1.84.0-beta.1"))
        assertEquals("1.83.0", Updates.cleanVersion(" v1.83.0 "))
        assertEquals("", Updates.cleanVersion("1"))
        assertEquals("", Updates.cleanVersion("1.84.0 beta"))
        assertEquals("", Updates.cleanVersion("player-v1.90.0"))
    }

    private val base = "https://github.com/retrocodes12/nebula-android/releases/download/"

    @Test fun releaseFrom_betaNeedsItsOwnApkUnderOurReleases() {
        val ok = JSONObject("""{"version":"1.84.0-beta.1","tag":"v1.84.0-beta.1","notes":"- New","assets":[
            {"name":"Nebula-1.84.0-beta.1.apk","url":"${base}v1.84.0-beta.1/Nebula-1.84.0-beta.1.apk","size":35000000}]}""")
        val r = Updates.releaseFrom(ok, beta = true)!!
        assertTrue(r.beta)
        assertEquals("1.84.0-beta.1", r.version)
        assertEquals("${base}v1.84.0-beta.1/Nebula-1.84.0-beta.1.apk", r.apkUrl)
        assertEquals(35000000L, r.size)
        assertEquals("• New", r.notes)
        // no asset, or one hosted anywhere else: not offered (never the evergreen link under a beta's name)
        assertNull(Updates.releaseFrom(JSONObject("""{"version":"1.84.0-beta.1","assets":[]}"""), beta = true))
        assertNull(Updates.releaseFrom(JSONObject("""{"version":"1.84.0-beta.1","assets":[
            {"name":"Nebula-1.84.0-beta.1.apk","url":"https://evil.example/Nebula.apk"}]}"""), beta = true))
    }

    @Test fun releaseFrom_stableFallsBackToTheEvergreenLink() {
        val r = Updates.releaseFrom(JSONObject("""{"tag":"v1.83.0","assets":[]}"""), beta = false)!!
        assertFalse(r.beta)
        assertEquals("1.83.0", r.version)
        assertEquals(Updates.APK_URL, r.apkUrl)
        val named = Updates.releaseFrom(JSONObject("""{"version":"1.83.0","assets":[
            {"name":"Nebula-1.83.0.apk","url":"${base}v1.83.0/Nebula-1.83.0.apk","size":1},
            {"name":"Nebula.apk","url":"${base}v1.83.0/Nebula.apk","size":2}]}"""), beta = false)!!
        assertEquals("${base}v1.83.0/Nebula.apk", named.apkUrl)
        assertEquals(2L, named.size)
    }

    @Test fun newest_takesTheBetaOnlyWhenItIsNewer() {
        val stable = Updates.Release("1.84.0", "", Updates.APK_URL)
        val beta = Updates.Release("1.84.0-beta.2", "", "x", beta = true)
        val next = Updates.Release("1.85.0-beta.1", "", "y", beta = true)
        assertEquals(stable, Updates.newest(stable, beta))
        assertEquals(next, Updates.newest(stable, next))
        assertEquals(stable, Updates.newest(stable, null))
    }
}
