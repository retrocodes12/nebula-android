@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** The player's plain functions: the timing readout, subtitle language keys, the overlay's cue lookup, HUD words. */
class PlayerExtrasTest {
    private val saved = Locale.getDefault()

    @After fun restoreLocale() = Locale.setDefault(saved)

    @Test fun fmtSubOffset_signedToATenth_withATrueMinus() {
        assertEquals("+0.5 s", fmtSubOffset(500))
        assertEquals("−0.1 s", fmtSubOffset(-100))
        assertEquals("0.0 s", fmtSubOffset(0))
    }

    @Test fun fmtSubOffset_asciiDigitsWhateverTheDeviceLocale() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        assertEquals("+1.5 s", fmtSubOffset(1_500))
    }

    @Test fun subLangKey_codesOfEveryShapeMeetOnTwoLetters() {
        assertEquals("en", subLangKey("eng"))
        assertEquals("en", subLangKey("en-US"))
        assertEquals("fr", subLangKey("fre"))                 // bibliographic
        assertEquals("pt", subLangKey("pob"))                 // OpenSubtitles' Brazilian Portuguese
    }

    @Test fun subLangKey_macrolanguagesMapBackToTheLanguage() {
        // Media3 files Serbian under "hbs-srp" and Indonesian under "ms-ind"; the settings say "sr" and "id"
        assertEquals("sr", subLangKey("scc"))
        assertEquals("id", subLangKey("id"))
    }

    @Test fun subLangKey_namesByTheLongestMatch() {
        assertEquals("en", subLangKey("English"))
        assertEquals("ml", subLangKey("Malayalam"))           // not Malay
        assertEquals("pt", subLangKey("Portuguese (Brazil)"))
    }

    @Test fun subLangKey_undeterminedIsNoLanguage() {
        assertEquals("", subLangKey("und"))
        assertEquals("", subLangKey(null))
    }

    @Test fun cuesAt_overlappingCuesShowTogether_andAnEndIsExclusive() {
        val all = listOf(
            SubCue(0, 2_000_000, emptyList()),
            SubCue(1_000_000, 3_000_000, emptyList()),
            SubCue(5_000_000, 6_000_000, emptyList()),
        )
        val on = mutableListOf(9)
        cuesAt(all, 1_500_000, on)
        assertEquals(listOf(0, 1), on)
        cuesAt(all, 2_000_000, on)
        assertEquals(listOf(1), on)
        cuesAt(all, 4_000_000, on)
        assertEquals(emptyList<Int>(), on)
    }

    @Test fun codecName_namesPeopleKnow_andNeverBlank() {
        assertEquals("H.264", codecName("avc1.640028", null))
        assertEquals("Dolby Digital Plus", codecName(null, "audio/eac3"))
        assertEquals("xyz9", codecName("xyz9", null))
        assertNull(codecName(null, null))
    }

    @Test fun hudWords() {
        assertEquals("2.5 Mb/s", fmtBits(2_500_000))
        assertEquals("1 kb/s", fmtBits(800))
        assertEquals("4 min", bufferLabel(240))
        assertEquals("50 s", bufferLabel(50))
    }
}
