package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pause board's fit (pauseBoardFit) and its Up next words (upNextLabel), in PlayerExtras.kt. The clearance checks
 * are measured against [Transport] — the constants the player's −10 · play · +10 row is built from — including the
 * growth and ring a circle wears when a remote lights it, so a change to the row moves these checks with it.
 */
class PauseBoardTest {
    @Test fun upNext_spacedAsEveryOtherEpisodeLabel() {
        assertEquals("Up next · S1 E2 · Work Drinks", upNextLabel(1, 2, "Work Drinks"))
    }

    @Test fun upNext_noNumberOrNoName() {
        assertEquals("Up next · S1", upNextLabel(1, null, ""))
        assertEquals("Up next · S0 E3", upNextLabel(0, 3, ""))
    }

    @Test fun phoneUpright_takesTheWholeWidth_andEndsAbovePlay() {
        // the 411 x 914 dp phone of the side-by-side (62 % stacked one pill per line into the play circle), and a
        // shorter 360 x 740 one, where the board used to run into the play glass (review R6)
        assertEquals(BoardFit(full = true, capDp = null, maxHeightDp = 457 - 40 - 10 - 84), pauseBoardFit(411, 914))
        assertEquals(BoardFit(full = true, capDp = null, maxHeightDp = 370 - 40 - 10 - 84), pauseBoardFit(360, 740))
    }

    @Test fun television_endsLeftOfTheMinusTenCircle() {
        // 960 x 540 dp = a 1080p TV: 320 dp, so the board (from 20 dp) ends at 340 dp = 680 px; the −10 circle's glass
        // starts at 480 − 130 = 350 dp = 700 px. Beside the controls, so its height is free
        assertEquals(BoardFit(full = false, capDp = 320, maxHeightDp = null), pauseBoardFit(960, 540))
    }

    @Test fun tabletOnItsSide_isCapped() {
        assertEquals(BoardFit(full = false, capDp = 640 - 130 - 10 - 20, maxHeightDp = null), pauseBoardFit(1280, 800))
    }

    @Test fun wideScreenUpright_isNotSqueezed() {
        // review R2: capping by width alone made these 140, ~185 and 240 dp wide, where the board sits well above a
        // transport it can never reach sideways — upright they keep 62 % and stop above the play circle instead
        for ((w, h) in listOf(600 to 960, 690 to 829, 800 to 1280)) {
            val fit = pauseBoardFit(w, h)
            assertNull("$w x $h: no width cap upright", fit.capDp)
            assertEquals("$w x $h", false, fit.full)
            assertEquals("$w x $h", h / 2 - Transport.PLAY / 2 - BOARD_CLEAR - BOARD_TOP, fit.maxHeightDp)
        }
        // 62 % of 600 less its margins is 347 dp, not 140
        assertTrue((0.62f * (600 - 2 * BOARD_START)).toInt() >= 340)
    }

    @Test fun shortScreen_hidesTheBoardUnderTheControls_soNothingIsCapped() {
        // a phone on its side (under 480 dp tall): boardUp is false while the controls show
        assertEquals(BoardFit(full = false, capDp = null, maxHeightDp = null), pauseBoardFit(891, 411))
        assertEquals(BoardFit(full = true, capDp = null, maxHeightDp = null), pauseBoardFit(592, 360))
    }

    @Test fun besideTheControls_alwaysClearsALitMinusTenCircle() {
        for (w in listOf(600, 720, 800, 960, 1024, 1280, 1366, 1920)) for (h in listOf(480, 540, 590, w - 1)) {
            if (h >= w || h < 480) continue
            val fit = pauseBoardFit(w, h)
            val cap = fit.capDp!!
            val boardEnd = BOARD_START + minOf(cap.toFloat(), 0.62f * (w - 2 * BOARD_START))
            // the −10 circle's centre, and its left edge when lit: grown by LIT_SCALE with the ring outside the glass
            val seekCentre = w / 2f - Transport.HALF + Transport.SEEK / 2f
            val litLeft = seekCentre - (Transport.SEEK / 2f + Transport.RING) * Transport.LIT_SCALE
            assertTrue("$w x $h: board ends at $boardEnd, lit −10 at $litLeft", boardEnd < litLeft)
            assertTrue("$w x $h: ${litLeft - boardEnd} dp to spare", litLeft - boardEnd >= 3f)
        }
    }

    @Test fun aboveTheControls_alwaysClearsALitPlayCircle() {
        for (w in listOf(320, 360, 411, 600, 690, 800)) for (h in listOf(480, 640, 740, 829, 914, 960, 1280)) {
            if (h < w) continue
            val fit = pauseBoardFit(w, h)
            val boardBottom = BOARD_TOP + fit.maxHeightDp!!
            // the play circle is the tallest of the three, so its top is the transport's top
            val litTop = h / 2f - (Transport.PLAY / 2f + Transport.RING) * Transport.LIT_SCALE
            assertTrue("$w x $h: board bottom $boardBottom, lit play top $litTop", boardBottom < litTop)
        }
    }

    @Test fun theTransportRow_isWhatTheRuleAssumes() {
        // 56 + 34 + 80 + 34 + 56 = 260 dp: the −10 circle's left edge 130 dp left of the centre
        assertEquals(130, Transport.HALF)
        assertEquals(2 * Transport.SEEK + 2 * Transport.GAP + Transport.PLAY, 2 * Transport.HALF)
    }
}
