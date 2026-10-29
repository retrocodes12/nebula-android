package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The launcher's Watch Next row (WatchNext.kt): what it lists, and what a viewer's removal keeps off. */
class WatchNextTest {
    private fun rec(type: String, id: String, name: String, at: Long, pos: Long = 600_000, dur: Long = 3_000_000) =
        ProgressRec(type, id, name, poster = "https://img.test/$id.jpg", pos = pos, dur = dur, at = at)

    @Test fun oneRowPerShowTheNewestEpisode() {
        val got = WatchNextPlan.entries(listOf(
            rec("series", "tt1:1:2", "The Bear · S1E2 · Pasta", at = 100),
            rec("series", "tt1:1:3", "The Bear · S1E3 · Brigade", at = 200),
            rec("movie", "tt9", "Arrival", at = 150),
        ), emptyMap())
        assertEquals(listOf("series:tt1", "movie:tt9"), got.map { it.key })
        val bear = got[0]
        assertEquals("tt1:1:3", bear.id)
        assertEquals("The Bear", bear.title)
        assertEquals("Brigade", bear.episodeTitle)
        assertEquals(1, bear.season)
        assertEquals(3, bear.episode)
        assertNull(got[1].season)
        assertEquals("Arrival", got[1].title)
    }

    @Test fun episodeNumbersFromTheIdWhenTheNameHasNone() {
        val e = WatchNextPlan.entries(listOf(rec("series", "tt1:2:5", "The Bear", at = 1)), emptyMap()).single()
        assertEquals(2, e.season)
        assertEquals(5, e.episode)
        assertNull(e.episodeTitle)
    }

    @Test fun aRemovalStaysUntilPlayedAgain() {
        val removed = mapOf("movie:tt9" to 500L)
        assertTrue(WatchNextPlan.entries(listOf(rec("movie", "tt9", "Arrival", at = 400)), removed).isEmpty())
        assertEquals(1, WatchNextPlan.entries(listOf(rec("movie", "tt9", "Arrival", at = 900)), removed).size)
        // played again: forgotten, so a later removal is a new one
        assertTrue(WatchNextPlan.keepRemoved(removed, listOf(rec("movie", "tt9", "Arrival", at = 900))).isEmpty())
        assertEquals(removed, WatchNextPlan.keepRemoved(removed, listOf(rec("movie", "tt9", "Arrival", at = 400))))
    }

    @Test fun aRemovedShowStaysOffForEveryEpisodeUntilOneIsPlayed() {
        val removed = mapOf("series:tt1" to 500L)
        assertTrue(WatchNextPlan.entries(listOf(rec("series", "tt1:1:3", "The Bear · S1E3", at = 300)), removed).isEmpty())
        assertEquals(1, WatchNextPlan.entries(listOf(rec("series", "tt1:1:4", "The Bear · S1E4", at = 700)), removed).size)
    }

    @Test fun onlyOursThatAreNoLongerWantedAreDropped() {
        val want = WatchNextPlan.entries(listOf(rec("movie", "tt9", "Arrival", at = 1)), emptyMap())
        assertEquals(setOf("series:tt1"), WatchNextPlan.drops(want, setOf("movie:tt9", "series:tt1")))
    }

    @Test fun cappedAndPostersAreWebAddressesOnly() {
        val many = (1..30).map { rec("movie", "tt$it", "Film $it", at = it.toLong()) }
        val got = WatchNextPlan.entries(many, emptyMap())
        assertEquals(WatchNextPlan.MAX, got.size)
        assertEquals("movie:tt30", got.first().key)
        val local = WatchNextPlan.entries(listOf(rec("movie", "x", "X", at = 1).copy(poster = "file:///data/x.jpg")), emptyMap())
        assertNull(local.single().poster)
    }
}

/** nebula://play links: every form, and what a stranger's link cannot do. */
class DeepLinksTest {
    private fun q(vararg kv: Pair<String, String>): (String) -> String? { val m = kv.toMap(); return { m[it] } }

    @Test fun thePlayFormIsUnchanged() {
        assertEquals(LinkReq.Play("https://cdn.test/a.mpd", "Match"), DeepLinks.parse("nebula", "play", q("mpd" to "https://cdn.test/a.mpd", "t" to "Match")))
        assertNull(DeepLinks.parse("nebula", "play", q("mpd" to "file:///data/data/x")))
        assertNull(DeepLinks.parse("nebula", "play", q("mpd" to "content://x/y")))
        assertNull(DeepLinks.parse("https", "play", q("mpd" to "https://cdn.test/a.mpd")))
    }

    @Test fun resumeRoundTrips() {
        val u = DeepLinks.resumeUri("series", "tt0903747:1:2")
        assertTrue(u.startsWith("nebula://play?open=resume&"))
        assertEquals(LinkReq.Resume("series", "tt0903747:1:2"), DeepLinks.parse("nebula", "play", q("open" to "resume", "type" to "series", "id" to "tt0903747:1:2")))
    }

    @Test fun badFieldsAreRefused() {
        assertNull(DeepLinks.parse("nebula", "play", q("open" to "resume", "type" to "Series!", "id" to "tt1")))
        assertNull(DeepLinks.parse("nebula", "play", q("open" to "resume", "type" to "movie", "id" to "tt 1")))
        assertNull(DeepLinks.parse("nebula", "play", q("open" to "resume", "type" to "movie", "id" to "x".repeat(201))))
        assertNull(DeepLinks.parse("nebula", "play", q("open" to "launch", "type" to "movie", "id" to "tt1")))
    }

    @Test fun aTitleLinkKeepsOnlyAWebAddonAndAPlainName() {
        val t = DeepLinks.parse("nebula", "play", q("open" to "title", "type" to "series", "id" to "tt1", "addon" to "javascript:alert(1)", "n" to "The\u0000 Bear")) as LinkReq.Title
        assertEquals("", t.addon)
        assertEquals("The Bear", t.name)
        val ok = DeepLinks.parse("nebula", "play", q("open" to "title", "type" to "series", "id" to "tt1", "addon" to "https://a.test/manifest.json")) as LinkReq.Title
        assertEquals("https://a.test/manifest.json", ok.addon)
    }
}
