package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The month's recap: what is added where, what is kept, and how a month adds up. */
class WatchLogTest {
    private fun fresh() = JSONObject().put("v", 1).put("months", JSONObject())

    @Test fun add_countsTotalTitleAndDay() {
        val d = fresh()
        WatchLog.add(d, "2026-09", 28, "The Bear", 600)
        WatchLog.add(d, "2026-09", 28, "The Bear", 30)
        WatchLog.add(d, "2026-09", 3, "Sky Sports Golf", 1200)
        val m = d.getJSONObject("months").getJSONObject("2026-09")
        assertEquals(1830L, m.getLong("total"))
        assertEquals(630L, m.getJSONObject("titles").getLong("The Bear"))
        assertEquals(630L, m.getJSONObject("days").getLong("28"))
        assertEquals(1200L, m.getJSONObject("days").getLong("3"))
    }

    @Test fun add_ignoresNothingAndImpossibleDays() {
        val d = fresh()
        WatchLog.add(d, "2026-09", 28, "X", 0)
        WatchLog.add(d, "2026-09", 32, "X", 60)
        WatchLog.add(d, "2026-09", 0, "X", 60)
        assertFalse(d.getJSONObject("months").has("2026-09"))
    }

    @Test fun titles_areCleanedAndCapped() {
        assertEquals("The Bear S1", WatchLog.cleanTitle("  The   Bear\nS1 "))
        assertEquals("Something", WatchLog.cleanTitle("   "))
        assertEquals(WatchLog.MAX_TITLE_LEN, WatchLog.cleanTitle("x".repeat(500)).length)
        val d = fresh()
        for (i in 0 until WatchLog.MAX_TITLES + 5) WatchLog.add(d, "2026-09", 1, "T$i", 100L + i)
        val titles = d.getJSONObject("months").getJSONObject("2026-09").getJSONObject("titles")
        assertEquals(WatchLog.MAX_TITLES, titles.length())
        assertFalse(titles.has("T0"))                                          // the smallest went
        assertTrue(titles.has("T${WatchLog.MAX_TITLES + 4}"))                  // the one being watched stays
    }

    @Test fun months_rollOverTheYear_andOnlyThreeAreKept() {
        assertEquals("2025-12", WatchLog.prevMonth("2026-01"))
        assertEquals("2026-08", WatchLog.prevMonth("2026-09"))
        assertEquals(listOf("2026-02", "2026-01", "2025-12"), WatchLog.keptMonths("2026-02"))
        val d = fresh()
        listOf("2026-06", "2026-07", "2026-08", "2026-09").forEach { WatchLog.add(d, it, 1, "X", 100) }
        WatchLog.prune(d, "2026-09")
        val keys = d.getJSONObject("months").keys().asSequence().toSet()
        assertEquals(setOf("2026-09", "2026-08", "2026-07"), keys)
    }

    @Test fun recap_addsUpTheMonth() {
        val d = fresh()
        WatchLog.add(d, "2026-09", 1, "Film A", 3 * 3600)
        WatchLog.add(d, "2026-09", 2, "Show B", 2 * 3600)
        WatchLog.add(d, "2026-09", 5, "Show B", 2 * 3600)
        WatchLog.add(d, "2026-09", 5, "Channel C", 1800)
        WatchLog.add(d, "2026-09", 9, "Trailer", 20)            // under a minute: no day, no top title
        WatchLog.add(d, "2026-09", 12, "Show D", 1800)
        val r = WatchLog.recap(d, "2026-09")
        assertEquals(3L * 3600 + 4L * 3600 + 1800 + 20 + 1800, r.totalSecs)
        assertEquals(4, r.days)                                  // 1, 2, 5, 12 — not 9
        assertEquals(listOf("Show B" to 4L * 3600, "Film A" to 3L * 3600, "Channel C" to 1800L), r.top)
        assertEquals(1, r.busiestDay)                            // 3 h on the 1st beats 2.5 h on the 5th
        assertEquals(3L * 3600, r.busiestSecs)
    }

    @Test fun recap_tiesGoToTheEarlierDayAndTheFirstName() {
        val d = fresh()
        WatchLog.add(d, "2026-09", 20, "B", 600)
        WatchLog.add(d, "2026-09", 10, "A", 600)
        val r = WatchLog.recap(d, "2026-09")
        assertEquals(10, r.busiestDay)
        assertEquals(listOf("A" to 600L, "B" to 600L), r.top)
    }

    @Test fun recap_ofAnEmptyMonthIsZero() {
        val r = WatchLog.recap(fresh(), "2026-09")
        assertEquals(0L, r.totalSecs)
        assertEquals(0, r.days)
        assertTrue(r.top.isEmpty())
        assertEquals(0, r.busiestDay)
    }

    @Test fun monthsShown_currentAlways_othersWhenTheyHoldSomething() {
        val d = fresh()
        assertEquals(listOf("2026-09"), WatchLog.monthsShown(d, "2026-09"))
        WatchLog.add(d, "2026-07", 4, "X", 100)
        assertEquals(listOf("2026-09", "2026-07"), WatchLog.monthsShown(d, "2026-09"))
    }

    @Test fun labels() {
        assertEquals("September", WatchLog.monthName("2026-09"))
        assertEquals("28 Sep", WatchLog.dayLabel("2026-09", 28))
        assertEquals("42 min", WatchLog.fmtDuration(42 * 60 + 30))
        assertEquals("3.4 h", WatchLog.fmtDuration(3 * 3600 + 24 * 60))
        assertEquals("12 h", WatchLog.fmtDuration(12 * 3600 + 10 * 60))
        assertEquals("42" to "minutes watched", WatchLog.hoursStat(42 * 60))
        assertEquals("12.4" to "hours watched", WatchLog.hoursStat(12 * 3600 + 24 * 60))
        assertEquals("25" to "hours watched", WatchLog.hoursStat(25 * 3600))
        assertEquals("1" to "hour watched", WatchLog.hoursStat(3600))
        assertEquals("140" to "hours watched", WatchLog.hoursStat(140 * 3600 + 1200))
    }
}
