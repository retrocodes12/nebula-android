package com.nuvio.ckplayer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Episode ids and the time pills — the top-level helpers in MainActivity.kt that mirror the shared player. */
class EpisodeIdsTest {
    private val saved = Locale.getDefault()

    @After fun restoreLocale() = Locale.setDefault(saved)

    @Test fun seriesIdOf_imdbAndBareIdsAreTheirFirstSegment() {
        assertEquals("tt0903747", seriesIdOf("tt0903747:1:2"))
        assertEquals("12345", seriesIdOf("12345:1:2"))
    }

    @Test fun seriesIdOf_prefixedIdsKeepTheirPrefix() {
        // never a split on the first colon: kitsu:12345 is ONE series id
        assertEquals("kitsu:12345", seriesIdOf("kitsu:12345:3"))
        assertEquals("tmdb:1399", seriesIdOf("tmdb:1399:1:2"))
    }

    @Test fun seriesIdOf_aBareSeriesIdComesBackAsItIs() {
        assertEquals("tt0903747", seriesIdOf("tt0903747"))
    }

    @Test fun episodeNumbersOf_readsSeasonAndEpisodeOffTheTail() {
        assertEquals("1" to "2", episodeNumbersOf("tt0903747:1:2"))
        assertEquals("3" to "10", episodeNumbersOf("tmdb:1399:3:10"))
    }

    @Test fun episodeNumbersOf_kitsuCarriesNoSeason() {
        assertEquals(null to "3", episodeNumbersOf("kitsu:12345:3"))
    }

    @Test fun episodeNumbersOf_nullForASeriesId() {
        assertNull(episodeNumbersOf("tt0903747"))
    }

    @Test fun fmtTime_minutesThenHours() {
        assertEquals("0:59", fmtTime(59_999))
        assertEquals("1:02:03", fmtTime(3_723_000))
        assertEquals("0:00", fmtTime(-5_000))              // never a negative pill
    }

    @Test fun fmtTime_writesAsciiDigitsWhateverTheDeviceLocale() {
        // regression: the device's locale printed Arabic-Indic digits into the time pills
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        assertEquals("1:02:03", fmtTime(3_723_000))
        assertEquals("12:05", fmtTime(725_000))
    }

    @Test fun fmtLeft_neverUnderAMinute() {
        assertEquals("1m left", fmtLeft(20_000))
        assertEquals("1h 2m left", fmtLeft(3_720_000))
    }
}
