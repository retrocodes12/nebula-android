package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pause board's width rule and its Up next words (pauseBoardWidth / upNextLabel in PlayerExtras.kt). */
class PauseBoardTest {
    @Test fun upNext_spacedAsEveryOtherEpisodeLabel() {
        assertEquals("Up next · S1 E2 · Work Drinks", upNextLabel(1, 2, "Work Drinks"))
    }

    @Test fun upNext_noNumberOrNoName() {
        assertEquals("Up next · S1", upNextLabel(1, null, ""))
        assertEquals("Up next · S0 E3", upNextLabel(0, 3, ""))
    }

    @Test fun phoneUpright_takesTheWholeWidth() {
        // the 411 dp phone of the side-by-side: 62 % stacked one pill per line into the play circle
        assertEquals(BoardWidth(full = true, capDp = null), pauseBoardWidth(411, 914))
        assertEquals(BoardWidth(full = true, capDp = null), pauseBoardWidth(599, 1000))
    }

    @Test fun television_endsLeftOfTheMinusTenCircle() {
        // 960 x 540 dp = a 1080p TV: 320 dp, so the board (from 20 dp) ends at 340 dp = 680 px, the −10 circle starts at 700 px
        val w = pauseBoardWidth(960, 540)
        assertEquals(BoardWidth(full = false, capDp = 320), w)
    }

    @Test fun wideScreens_alwaysClearTheTransport() {
        // the −10 circle's left edge is 130 dp left of the centre; the board starts 20 dp in
        for (width in listOf(600, 720, 800, 960, 1024, 1280, 1920)) {
            val cap = pauseBoardWidth(width, 540).capDp!!
            val boardEnd = 20 + minOf(cap, (width * 0.62f).toInt())
            assertTrue("width $width: board ends at $boardEnd", boardEnd <= width / 2 - 130 - 10)
        }
    }

    @Test fun shortScreen_hidesTheBoardUnderTheControls_soNoCap() {
        // a phone on its side (under 480 dp tall): boardUp is false while the controls show
        assertEquals(BoardWidth(full = false, capDp = null), pauseBoardWidth(891, 411))
    }
}
