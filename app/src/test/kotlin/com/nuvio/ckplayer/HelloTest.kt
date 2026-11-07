package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** The usage counter sends only the install's age in the device's own calendar days (Hello.kt). */
class HelloTest {
    private val india = TimeZone.getTimeZone("Asia/Kolkata")
    private val utc = TimeZone.getTimeZone("UTC")
    private val h = 3_600_000L
    private val morning = 1_793_840_400_000L      // 2026-11-05 01:00 UTC (06:30 India time)
    private val lateNight = 1_793_901_600_000L    // 2026-11-05 18:00 UTC (23:30 India time)

    @Test fun sameDay_isAgeZero() {
        assertEquals("s=android&f=phone&a=0", Hello.query(morning, morning + 10 * h, tv = false, tz = india))
    }

    @Test fun countsCalendarDaysNotHours() {
        // installed 23:30 India time, opened 01:00 the next morning: one day old, though only 90 minutes passed
        assertEquals("s=android&f=tv&a=1", Hello.query(lateNight, lateNight + h + h / 2, tv = true, tz = india))
        // the same two moments are one UTC day (18:00 and 19:30): the device's own calendar decides
        assertEquals("s=android&f=tv&a=0", Hello.query(lateNight, lateNight + h + h / 2, tv = true, tz = utc))
    }

    @Test fun aWeekLater_isSeven() {
        assertEquals("s=android&f=phone&a=7", Hello.query(morning, morning + 7 * 24 * h, tv = false, tz = india))
    }

    @Test fun aClockSetBackwards_neverGoesNegative() {
        assertEquals("s=android&f=phone&a=0", Hello.query(morning, morning - 3 * 24 * h, tv = false, tz = india))
    }

    @Test fun daysBeforeTheEpoch_roundDown() {
        assertEquals(-1L, Hello.localDay(-1L, utc))
        assertEquals(0L, Hello.localDay(0L, utc))
    }
}
