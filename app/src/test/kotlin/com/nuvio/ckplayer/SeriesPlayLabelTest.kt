package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/** The title page's one series target in the Play pill: compact words and regular episodes before specials. */
class SeriesPlayLabelTest {
    private fun ep(id: String, season: Int, episode: Int?) = Episode(id, season, episode, "", null, null)

    @Test fun freshSeries_startsAtTheFirstEpisode() {
        assertEquals("Play S1E1", seriesPlayLabel("show", null, "show:1:1", 1, 1))
    }

    @Test fun firstTwoDone_labelsTheNextEpisode() {
        val episodes = listOf(ep("show:1:1", 1, 1), ep("show:1:2", 1, 2), ep("show:1:3", 1, 3))
        val done = setOf("show:1:1", "show:1:2")
        val next = seriesCursorOf(episodes) { id ->
            if (id in done) ProgressRec("series", id, done = true, at = id.substringAfterLast(':').toLong()) else null
        }?.upNext ?: error("done episodes should leave an up-next episode")
        assertEquals("Play S1E3", seriesPlayLabel("show", null, next.id, next.season, next.episode))
    }

    @Test fun inProgress_prefersResumeWords() {
        assertEquals("Resume S2E3", seriesPlayLabel("show", "show:2:3", null))
    }

    @Test fun kitsuEpisode_hasNoInventedSeason() {
        assertEquals("Play E7", seriesPlayLabel("kitsu:12345", null, "kitsu:12345:7", 1, 7))
    }

    @Test fun noTarget_saysPlay() {
        assertEquals("Play", seriesPlayLabel("show", null, null))
    }

    @Test fun episodeWithoutNumbers_hasNoEpisodeWords() {
        assertEquals("Play", seriesPlayLabel("show", null, "show:finale", 1, null))
    }

    @Test fun specials_areAfterRegularEpisodes() {
        val regular = ep("show:1:1", 1, 1)
        val special = ep("show:0:1", 0, 1)
        assertEquals(regular.id, seriesFirstEpisode(listOf(special, regular))?.id)
        assertEquals("Play S1E1", seriesPlayLabel("show", null, regular.id, regular.season, regular.episode))
    }
}
