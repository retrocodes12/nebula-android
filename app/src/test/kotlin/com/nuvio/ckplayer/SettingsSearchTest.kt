package com.nuvio.ckplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings' search (SettingsRoot.kt): every word somewhere in the name, the page or the words, case aside. */
class SettingsSearchTest {
    @Test fun everyWordMustBeThere_inAnyOrder() {
        assertTrue(settingsHit("Tracking", "Services", "trakt simkl mdblist", "trakt"))
        assertTrue(settingsHit("Tracking", "Services", "trakt simkl mdblist", "SIMKL services"))
        assertTrue(settingsHit("Subtitles and languages", "Watch › Playback", "subtitle style size", "playback subtitle"))
        assertFalse(settingsHit("Tracking", "Services", "trakt simkl mdblist", "trakt playback"))
    }

    @Test fun anEmptyQueryFindsNothing() {
        assertFalse(settingsHit("Account", "You", "profile", ""))
        assertFalse(settingsHit("Account", "You", "profile", "  , "))
    }
}
