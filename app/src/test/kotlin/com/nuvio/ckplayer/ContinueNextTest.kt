package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Continue watching, Nuvio's way (ContinueNext.plan): one card per show, Up next after a finished episode, 90 % is
 * finished, a removed Up next stays off until another episode is finished. The same cases as the shared player's
 * `cwflow` rig.
 */
class ContinueNextTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private fun rec(id: String, at: Long, pos: Long = 0, dur: Long = 0, done: Boolean = false, hand: Boolean = false, type: String = "series") =
        ProgressRec(type, id, name = id, pos = pos, dur = dur, done = done, hand = hand, at = at)
    private fun ans(sig: Long, ep: ContinueNext.Ep?, same: Boolean = true) =
        ContinueNext.Answer(sig = sig, t = now, name = "Show", poster = "p", back = "b", same = same, ep = ep)
    private fun ep(id: String, s: Int, e: Int, relDays: Int) =
        ContinueNext.Ep(id, s, e, "Ep $s.$e", null, java.time.Instant.ofEpochMilli(now + relDays * day).toString())

    private fun plan(
        recs: List<ProgressRec>, answers: Map<String, ContinueNext.Answer> = emptyMap(),
        unaired: Boolean = false, nextOn: Boolean = true, wanted: MutableList<String> = ArrayList(),
    ) = ContinueNext.plan(
        recs.associateBy { Progress.key(it.type, it.id) }, emptySet(), nextOn, unaired, az = false, now = now,
        answer = { answers[it.id] }, want = { wanted += it.id },
    )

    @Test fun onePerShow_theNewestResumePoint() {
        val out = plan(listOf(rec("tt1:1:1", now - 7 * day, 500_000, 2_400_000), rec("tt1:1:2", now - 6 * day, 900_000, 2_400_000)))
        assertEquals(listOf("tt1:1:2"), out.map { it.id })
    }

    @Test fun finished_upNextFromTheAnswer_andAskedOnlyWhenUnknown() {
        val done = rec("tt1:1:1", now - 3 * day, done = true)
        val wanted = ArrayList<String>()
        assertTrue(plan(listOf(done), wanted = wanted).isEmpty())
        assertEquals(listOf("tt1"), wanted)
        val out = plan(listOf(done), mapOf("tt1" to ans(done.at, ep("tt1:1:2", 1, 2, -1))))
        assertEquals("tt1:1:2", out.single().id)
        assertTrue(out.single().next)
        assertEquals("Show · S1E2 · Ep 1.2", out.single().name)
        assertTrue("out after the finish = New episode", out.single().fresh)
    }

    @Test fun newerPartWay_isResumedInsteadOfUpNext() {
        val out = plan(listOf(rec("tt1:1:1", now - 5 * day, done = true), rec("tt1:1:2", now - 4 * day, 600_000, 2_400_000)))
        assertEquals("tt1:1:2", out.single().id)
        assertFalse(out.single().next)
    }

    @Test fun ninetyPercent_isFinished() {
        val film = { pos: Long -> rec("tt9", now - day, pos, 3_600_000, type = "movie") }
        assertTrue(plan(listOf(film(3_330_000))).isEmpty())
        assertEquals(1, plan(listOf(film(3_060_000))).size)
    }

    @Test fun removed_staysOffUntilAnotherEpisodeIsFinished() {
        val done = rec("tt1:1:1", now - 5 * day, done = true)
        val gone = ProgressRec(ContinueNext.NEXTUP, "tt1", dismissed = true, at = now - 4 * day)
        assertTrue(plan(listOf(done, gone), mapOf("tt1" to ans(done.at, ep("tt1:1:2", 1, 2, -10)))).isEmpty())
        val later = rec("tt1:1:2", now - day, done = true)
        val out = plan(listOf(done, later, gone), mapOf("tt1" to ans(later.at, ep("tt1:1:3", 1, 3, -10))))
        assertEquals("tt1:1:3", out.single().id)
    }

    @Test fun notOutYet_onlyWithTheSetting_andANewSeasonOnlyInItsLastWeek() {
        val done = rec("tt1:1:3", now - day, done = true)
        val soonSame = mapOf("tt1" to ans(done.at, ep("tt1:1:4", 1, 4, 3)))
        assertTrue(plan(listOf(done), soonSame).isEmpty())
        val out = plan(listOf(done), soonSame, unaired = true)
        assertTrue(out.single().airs > now)
        val farNewSeason = mapOf("tt1" to ans(done.at, ep("tt1:2:1", 2, 1, 30), same = false))
        assertTrue(plan(listOf(done), farNewSeason, unaired = true).isEmpty())
        val nearNewSeason = mapOf("tt1" to ans(done.at, ep("tt1:2:1", 2, 1, 3), same = false))
        assertEquals(1, plan(listOf(done), nearNewSeason, unaired = true).size)
    }

    @Test fun anAnswerForOlderRecords_isNotShown() {
        val done = rec("tt1:1:2", now - day, done = true)
        val wanted = ArrayList<String>()
        assertTrue(plan(listOf(done), mapOf("tt1" to ans(done.at - 1, ep("tt1:1:2", 1, 2, -10))), wanted = wanted).isEmpty())
        assertEquals(listOf("tt1"), wanted)
    }

    @Test fun switchedOff_nothingAsked() {
        val wanted = ArrayList<String>()
        assertTrue(plan(listOf(rec("tt1:1:1", now - day, done = true)), nextOn = false, wanted = wanted).isEmpty())
        assertTrue(wanted.isEmpty())
    }

    @Test fun theNextEpisodeStartedEarlier_isItsOwnResumeCard() {
        val part = rec("tt1:1:2", now - 9 * day, 700_000, 2_400_000)
        val done = rec("tt1:1:1", now - 2 * day, done = true)
        val out = plan(listOf(part, done), mapOf("tt1" to ans(done.at, ep("tt1:1:2", 1, 2, -30))))
        assertEquals("tt1:1:2", out.single().id)
        assertFalse(out.single().next)
        assertEquals(700_000L, out.single().pos)
    }
}
