package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which episodes a new-episode alert names (NewEpisodesPlan.kt). */
class NewEpisodesTest {
    private val day = 24L * 3600_000
    private val now = NewEpisodesPlan.releasedAt("2026-10-29T12:00:00.000Z")!!
    private fun ep(s: Int, e: Int, released: String?, name: String = "Ep $e") = Episode("tt1:$s:$e", s, e, name, null, null, released)

    @Test fun releaseTimesInTheirShapes() {
        assertEquals(now, NewEpisodesPlan.releasedAt("2026-10-29T12:00:00Z"))
        assertEquals(now - 12 * 3600_000, NewEpisodesPlan.releasedAt("2026-10-29"))
        assertEquals(now, NewEpisodesPlan.releasedAt("2026-10-29T14:00:00+02:00"))
        assertNull(NewEpisodesPlan.releasedAt(""))
        assertNull(NewEpisodesPlan.releasedAt("soon"))
    }

    @Test fun theFirstCheckOnlyRecords() {
        val eps = listOf(ep(1, 1, "2020-01-01T00:00:00.000Z"), ep(1, 2, "2026-10-29T00:00:00.000Z"))
        assertTrue(NewEpisodesPlan.fresh(eps, since = 0L, now = now).isEmpty())
    }

    @Test fun outSinceTheLastCheckAndNotAhead() {
        val eps = listOf(
            ep(2, 4, "2026-10-20T00:00:00.000Z"),             // before the last check
            ep(2, 5, "2026-10-28T08:00:00.000Z"),             // new
            ep(2, 6, "2026-10-29T06:00:00.000Z"),             // new
            ep(2, 7, "2026-11-05T00:00:00.000Z"),             // still ahead
            ep(0, 1, "2026-10-28T09:00:00.000Z"),             // a special
            ep(2, 8, null),                                    // undated
        )
        val got = NewEpisodesPlan.fresh(eps, since = now - 2 * day, now = now)
        assertEquals(listOf("tt1:2:5", "tt1:2:6"), got.map { it.id })
    }

    @Test fun anEpisodeAlreadyWatchedIsNotNews() {
        val eps = listOf(ep(2, 5, "2026-10-28T08:00:00.000Z"))
        assertTrue(NewEpisodesPlan.fresh(eps, now - 2 * day, now, watched = setOf("tt1:2:5")).isEmpty())
    }

    @Test fun followedShowsFromMyListAndContinueWatching() {
        val library = JSONObject()
            .put("series:tt1", JSONObject().put("type", "series").put("id", "tt1").put("name", "The Bear").put("addonUrl", "https://a.test/manifest.json"))
            .put("movie:tt9", JSONObject().put("type", "movie").put("id", "tt9").put("name", "Arrival"))
            .put("series:tt2", JSONObject().put("removed", true).put("at", 5))
        val progress = JSONObject()
            .put("series:tt3:1:2", JSONObject().put("type", "series").put("id", "tt3:1:2").put("name", "Severance · S1E2 · Half Loop").put("pos", 600_000).put("dur", 3_000_000))
            .put("series:tt4:1:1", JSONObject().put("type", "series").put("id", "tt4:1:1").put("done", true))
            .put("series:tt1:1:3", JSONObject().put("type", "series").put("id", "tt1:1:3").put("name", "The Bear · S1E3").put("pos", 600_000).put("dur", 3_000_000))
        val shows = NewEpisodesPlan.shows("b", progress, library)
        assertEquals(listOf("tt1", "tt3"), shows.map { it.id })
        assertEquals("Severance", shows[1].name)
        assertEquals("b|series:tt1", shows[0].key)
        assertEquals(setOf("tt4:1:1"), NewEpisodesPlan.watched(progress))
    }

    @Test fun theNotificationLines() {
        val one = NewEpisodesPlan.lines("The Bear", listOf(ep(2, 5, null, "Pasta")), null)
        assertEquals("New episode · The Bear" to "S2E5 · Pasta", one)
        val two = NewEpisodesPlan.lines("The Bear", listOf(ep(2, 5, null), ep(2, 6, null, "Bolognese")), "Kids")
        assertEquals("New episodes · The Bear" to "S2E6 · Bolognese and 1 more · for Kids", two)
    }

    @Test fun checksKeptOnlyForShowsStillFollowed() {
        val shows = listOf(NewEpisodesPlan.Show("a", "series", "tt1", "The Bear", ""))
        assertEquals(mapOf("a|series:tt1" to 7L), NewEpisodesPlan.keep(mapOf("a|series:tt1" to 7L, "b|series:tt1" to 8L), shows))
    }

    @Test fun addonDocuments() {
        val got = NewEpisodesPlan.addons("""[{"url":"https://a.test/manifest.json","name":"A"},{"url":"https://b.test/manifest.json","off":true},{"name":"no url"}]""")
        assertEquals(listOf(Triple("https://a.test/manifest.json", "https://a.test", true), Triple("https://b.test/manifest.json", "https://b.test", false)), got)
        assertTrue(NewEpisodesPlan.addons("not json").isEmpty())
    }
}
