package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which country's age rating a device shows (Advisory.regionOf): the time zone first, then the locale, else the US. */
class AgeRegionTest {
    @Test fun india_fromTheClock_evenWithAnAmericanLanguage() = assertEquals("IN", Advisory.regionOf("Asia/Kolkata", "US"))
    @Test fun usZones() = assertEquals("US", Advisory.regionOf("America/Los_Angeles", ""))
    @Test fun australia() = assertEquals("AU", Advisory.regionOf("Australia/Perth", "GB"))
    @Test fun unknownZone_theLocale() = assertEquals("FR", Advisory.regionOf("Europe/Monaco", "fr"))
    @Test fun nothing_theUs() = assertEquals("US", Advisory.regionOf("UTC", ""))
    @Test fun overlayRows_ratingFirst() {
        val rows = Advisory.Answer(listOf(Advisory.Warn("Violence", "Severe")), "UA").rows()
        assertEquals(listOf("Rated UA", "Violence Severe"), rows.map { it.kind + " " + it.level })
        assertEquals(1, Advisory.Answer(listOf(Advisory.Warn("Violence", "Severe")), "").rows().size)
    }
}
