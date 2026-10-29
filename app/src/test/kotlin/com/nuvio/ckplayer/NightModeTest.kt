package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The info panel's Sound row for night mode (NightMode.kt). */
class NightModeTest {
    @Test fun soundRow() {
        assertNull(NightMode.soundLine(on = false, active = false, can = true))
        assertEquals("Night mode", NightMode.soundLine(on = true, active = true, can = true))
        assertEquals("Night mode could not start on this device", NightMode.soundLine(on = true, active = false, can = true))
        assertNull(NightMode.soundLine(on = true, active = false, can = false))     // below Android 9 the option is hidden
    }
}
