package com.nuvio.ckplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's Featured carousel moves on by itself only when it is on and View Details is not lit (heroAdvances). */
class HeroRotationTest {
    @Test fun advances_whenOnAndNotFocused() {
        assertTrue(heroAdvances(everySecs = 10, detailsFocused = false))
        assertTrue(heroAdvances(everySecs = 6, detailsFocused = false))
    }

    @Test fun holdsStill_underALitViewDetails() {
        // android-tv-2: the hero moved from one title to another under a focused View Details, with no key pressed
        assertFalse(heroAdvances(everySecs = 10, detailsFocused = true))
    }

    @Test fun off_neverAdvances() {
        assertFalse(heroAdvances(everySecs = 0, detailsFocused = false))
        assertFalse(heroAdvances(everySecs = -1, detailsFocused = false))
    }
}
