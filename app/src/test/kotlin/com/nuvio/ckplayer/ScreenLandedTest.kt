package com.nuvio.ckplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When LandingFallback (TvFocus.kt) treats a screen as landed — screenLanded (android-tv-29, review R3). */
class ScreenLandedTest {
    private val opened = 1_000L

    @Test fun nothingLit_isNotLanded() {
        assertFalse(screenLanded(hasFocus = false, backLit = false, lastKeyDownAt = 0L, openedAt = opened))
    }

    @Test fun aControlLit_isLanded() {
        assertTrue(screenLanded(hasFocus = true, backLit = false, lastKeyDownAt = 0L, openedAt = opened))
        assertTrue(screenLanded(hasFocus = true, backLit = false, lastKeyDownAt = 2_000L, openedAt = opened))
    }

    @Test fun backSeededWithNoPress_isNotLanded() {
        // the page's own focus seeding lit its top-left control, Back: OK there would leave the page (Catalog, Friends,
        // a pushed Profile). The OK that opened the page went down BEFORE it opened, so it does not count
        assertFalse(screenLanded(hasFocus = true, backLit = true, lastKeyDownAt = 0L, openedAt = opened))
        assertFalse(screenLanded(hasFocus = true, backLit = true, lastKeyDownAt = opened - 40, openedAt = opened))
    }

    @Test fun backChosenAfterAPress_isLanded() {
        // the viewer walked Up to Back: never pulled away from it
        assertTrue(screenLanded(hasFocus = true, backLit = true, lastKeyDownAt = opened + 500, openedAt = opened))
    }
}
