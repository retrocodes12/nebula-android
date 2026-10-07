package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/** The TV Guide's rules (Guide.kt GuidePlan), against the shared spec. */
class GuideTest {
    private val H = GuidePlan.HOUR
    private val M = GuidePlan.MIN
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()
    private val now = t("2026-10-07T19:10:00Z")
    private val src = GuideSource(Addon("https://a.example/manifest.json", "Sports", "https://a.example"), CatalogRef("sports", "all", "Schedule", emptyList()))

    private fun entry(id: String, start: Long?, live: Boolean = false, minutes: Int = 120, label: String = "Baseball", name: String = id) =
        GuideItem(GuideEntry(MetaItem(id, "sports", name, null), start, live, minutes, label), src)

    @Test fun parse_theScheduleForm_andItsVariants() {
        val want = t("2026-10-07T20:00:00Z")
        assertEquals(want, GuidePlan.parseScheduleTime("2026-10-07 20:00 UTC"))
        assertEquals(want, GuidePlan.parseScheduleTime("2026-10-07T20:00"))
        assertEquals(want, GuidePlan.parseScheduleTime("  2026-10-07 20:00 GMT "))
        assertEquals(want, GuidePlan.parseScheduleTime("2026-10-07 20:00Z"))
        assertEquals(want + 30_000, GuidePlan.parseScheduleTime("2026-10-07 20:00:30 UTC"))
        assertNull(GuidePlan.parseScheduleTime("🔴 LIVE"))
        assertNull(GuidePlan.parseScheduleTime("2026"))
        assertNull(GuidePlan.parseScheduleTime("2026-10-07 20:00 PST"))
        assertNull(GuidePlan.parseScheduleTime("2026-13-07 20:00 UTC"))
    }

    @Test fun parse_releasedNeedsATimePart() {
        assertEquals(t("2026-10-07T20:00:00Z"), GuidePlan.parseIsoDateTime("2026-10-07T20:00:00.000Z"))
        assertEquals(t("2026-10-07T18:00:00Z"), GuidePlan.parseIsoDateTime("2026-10-07T20:00:00+02:00"))
        assertEquals(t("2026-10-07T20:00:00Z"), GuidePlan.parseIsoDateTime("2026-10-07T20:00"))
        assertNull(GuidePlan.parseIsoDateTime("2026-10-07"))
        assertNull(GuidePlan.parseIsoDateTime("soon"))
    }

    @Test fun parseCatalog_readsEveryStartForm_andOnlyABooleanIsLive() {
        val json = """{"metas":[
            {"id":"a","type":"sports","name":"Live one","isLive":true,"releaseInfo":"🔴 LIVE","genre":"Baseball"},
            {"id":"b","type":"sports","name":"Upcoming","releaseInfo":"2026-10-07 20:00 UTC","genre":"⚽ Football"},
            {"id":"c","type":"sports","name":"By time","time":"2026-10-07 21:00 UTC","genres":["Tennis"]},
            {"id":"d","type":"sports","name":"By released","released":"2026-10-07T22:00:00.000Z","runtime":"1h 35m"},
            {"id":"e","type":"sports","name":"Not live","isLive":"False","genre":"Golf"},
            {"id":"f","type":"sports","name":"Live words","releaseInfo":"live now"},
            {"id":"g","type":"sports","name":"Untimed live label","releaseInfo":"🔴 LIVE"}
        ]}"""
        val es = GuidePlan.parseCatalog(json, "sports").associateBy { it.meta.id }
        assertTrue(es.getValue("a").live); assertNull(es.getValue("a").start)
        assertEquals(t("2026-10-07T20:00:00Z"), es.getValue("b").start)
        assertEquals("Football", es.getValue("b").label)
        assertEquals(115, es.getValue("b").minutes)
        assertEquals(t("2026-10-07T21:00:00Z"), es.getValue("c").start)
        assertEquals("Tennis", es.getValue("c").label)
        assertEquals(t("2026-10-07T22:00:00Z"), es.getValue("d").start)
        assertEquals(95, es.getValue("d").minutes)
        assertEquals("Other", es.getValue("d").label)
        assertFalse(es.getValue("e").live); assertNull(es.getValue("e").start)
        assertFalse(es.getValue("f").live)          // more than the word live: not an event
        assertTrue(es.getValue("g").live)           // "🔴 LIVE" with isLive absent
        assertEquals(180, es.getValue("a").minutes)
    }

    @Test fun liveTv_withIsLiveFalse_isNotLive_butTheRedLiveLabelIs() {
        val es = GuidePlan.parseCatalog("""{"metas":[
            {"id":"ch","type":"tv","name":"Sky Sports","releaseInfo":"Live TV","isLive":false},
            {"id":"ev","type":"sports","name":"Match","releaseInfo":"🔴 LIVE"},
            {"id":"lw","type":"sports","name":"Match 2","releaseInfo":" live "}
        ]}""", "tv").associateBy { it.meta.id }
        assertFalse(es.getValue("ch").live)
        assertFalse(GuidePlan.isSchedule(listOf(es.getValue("ch"))))
        assertTrue(es.getValue("ev").live)
        assertTrue(es.getValue("lw").live)
    }

    @Test fun runtime_andTheSportTable() {
        assertEquals(95, GuidePlan.runtimeMinutes("95"))
        assertEquals(95, GuidePlan.runtimeMinutes("95 min"))
        assertEquals(95, GuidePlan.runtimeMinutes("1h 35m"))
        assertEquals(120, GuidePlan.runtimeMinutes("2h"))
        assertEquals(95, GuidePlan.runtimeMinutes(95))
        assertNull(GuidePlan.runtimeMinutes("TBA"))
        assertNull(GuidePlan.runtimeMinutes(0))
        assertEquals(210, GuidePlan.sportMinutes("American Football"))
        assertEquals(210, GuidePlan.sportMinutes("NFL"))
        assertEquals(115, GuidePlan.sportMinutes("Football"))
        assertEquals(110, GuidePlan.sportMinutes("Rugby League"))
        assertEquals(120, GuidePlan.sportMinutes("Formula 1"))
        assertEquals(150, GuidePlan.sportMinutes("basketball"))
        assertEquals(120, GuidePlan.sportMinutes("Darts"))
        assertEquals(120, GuidePlan.sportMinutes(null))
    }

    @Test fun labels_loseEmojiAndSymbols() {
        assertEquals("Football", GuidePlan.cleanLabel(" ⚽ Football "))
        assertEquals("Ice Hockey", GuidePlan.cleanLabel("🏒 Ice  Hockey"))
        assertEquals("Other", GuidePlan.cleanLabel("🔴"))
        assertEquals("Other", GuidePlan.cleanLabel(null))
    }

    @Test fun live_withoutAStart_sitsAroundNow() {
        val b = GuidePlan.blocks(listOf(entry("x", null, live = true)), now).single()
        assertEquals(now - 90 * M, b.start)
        assertEquals(now + 60 * M, b.end)
    }

    @Test fun live_withAStart_runsAtLeastAQuarterHourMore() {
        // started 3 h ago, a 2 h sport: still live, so it reaches now + 15 min
        val late = GuidePlan.blocks(listOf(entry("x", now - 3 * H, live = true)), now).single()
        assertEquals(now - 3 * H, late.start)
        assertEquals(now + 15 * M, late.end)
        val fresh = GuidePlan.blocks(listOf(entry("y", now - 10 * M, live = true)), now).single()
        assertEquals(now - 10 * M + 120 * M, fresh.end)
    }

    @Test fun endedItems_andThoseBeyond48h_areDropped() {
        val bs = GuidePlan.blocks(listOf(
            entry("ended", now - 3 * H),                 // 2 h long, ended an hour ago
            entry("endsNow", now - 2 * H),               // ends exactly now
            entry("on", now - H),
            entry("later", now + 47 * H),
            entry("tooFar", now + 48 * H),
        ), now).map { it.id }
        assertEquals(listOf("on", "later"), bs)
    }

    @Test fun overlappingGames_takeLanes_greedily_labelOnTheFirst() {
        val lanes = GuidePlan.lanes(listOf(
            entry("a", now, minutes = 180),
            entry("b", now + H, minutes = 180),
            entry("c", now + 3 * H, minutes = 180),          // starts as a ends: back in lane 1
            entry("d", now + 2 * H, minutes = 60, label = "Tennis"),
        ), now)
        assertEquals(3, lanes.size)
        assertEquals(listOf("a", "c"), lanes[0].blocks.map { it.id })
        assertEquals(listOf("b"), lanes[1].blocks.map { it.id })
        assertTrue(lanes[0].first); assertFalse(lanes[1].first)
        assertEquals("Baseball", lanes[1].label)
        assertEquals("Tennis", lanes[2].label); assertTrue(lanes[2].first)
    }

    @Test fun groups_withALiveItemComeFirst_thenByEarliestStart() {
        val lanes = GuidePlan.lanes(listOf(
            entry("soon", now + 10 * M, label = "Tennis"),
            entry("later", now + 5 * H, label = "Golf"),
            entry("live", null, live = true, label = "Cricket"),
        ), now)
        assertEquals(listOf("Cricket", "Tennis", "Golf"), lanes.map { it.label })
    }

    @Test fun aChannelCatalogWithNoTimes_makesNoGuide() {
        val channels = """{"metas":[{"id":"ch1","type":"tv","name":"Sky Sports"},{"id":"ch2","type":"tv","name":"BT Sport","releaseInfo":"Channel"}]}"""
        val es = GuidePlan.parseCatalog(channels, "tv")
        assertEquals(2, es.size)
        assertFalse(GuidePlan.isSchedule(es))
        assertTrue(GuidePlan.isSchedule(GuidePlan.parseCatalog("""{"metas":[{"id":"x","isLive":true}]}""", "sports")))
    }

    @Test fun dedupe_byId_prefersTheCopyWithAStart() {
        val items = GuidePlan.dedupe(listOf(
            entry("m1", null, live = true, name = "first copy"),
            entry("m2", now + H),
            entry("m1", now - 10 * M, live = true, name = "timed copy"),
            entry("m2", null, live = true, name = "untimed copy"),
        ))
        assertEquals(listOf("m1", "m2"), items.map { it.entry.meta.id })
        assertEquals("timed copy", items[0].entry.meta.name)
        assertEquals(now + H, items[1].entry.start)
    }

    @Test fun window_startsHalfAnHourBeforeTheFlooredNow() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals(t("2026-10-07T18:30:00Z"), GuidePlan.windowStart(now, utc))
        // India's +5:30 floors on its own half hours too
        assertEquals(t("2026-10-07T18:30:00Z"), GuidePlan.windowStart(now, TimeZone.getTimeZone("Asia/Kolkata")))
    }

    @Test fun landing_firstLive_elseEarliestUpcoming() {
        val withLive = GuidePlan.lanes(listOf(entry("up", now + H, label = "Golf"), entry("lv", null, live = true, label = "Tennis")), now)
        assertEquals("lv", GuidePlan.landing(withLive, now)?.id)
        val noLive = GuidePlan.lanes(listOf(entry("late", now + 3 * H), entry("early", now + H, label = "Golf")), now)
        assertEquals("early", GuidePlan.landing(noLive, now)?.id)
    }

    @Test fun upDown_picksTheGreatestOverlap_elseTheNearestStart() {
        val lane = GuideLane("Baseball", true, GuidePlan.blocks(listOf(
            entry("p", now, minutes = 60), entry("q", now + 2 * H, minutes = 60), entry("r", now + 4 * H, minutes = 60),
        ), now))
        assertEquals("q", GuidePlan.pickInLane(lane, now + 90 * M, now + 150 * M)?.id)
        // no overlap at all: the block whose start is nearest the visible start
        assertEquals("r", GuidePlan.pickInLane(lane, now + 3 * H + 40 * M, now + 3 * H + 50 * M)?.id)
    }
}
