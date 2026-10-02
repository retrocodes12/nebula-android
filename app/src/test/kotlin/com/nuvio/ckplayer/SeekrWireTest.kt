package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10-02 Seekr previews: the lookup a content id asks for, the VTT parser, the cue a position shows, the synced doc. */
class SeekrWireTest {
    private val key = "sk_live_" + "0123456789abcdef".repeat(4)

    @Test fun query_mapsFilmsAndEpisodes() {
        assertEquals(listOf("imdb_id" to "tt0133093"), SeekrWire.query("movie", "tt0133093"))
        assertEquals(listOf("tmdb_id" to "603"), SeekrWire.query("movie", "tmdb:603"))
        assertEquals(
            listOf("show_imdb_id" to "tt0903747", "season" to "1", "episode" to "2"),
            SeekrWire.query("series", "tt0903747:1:2"),
        )
        assertEquals(
            listOf("show_tmdb_id" to "1399", "season" to "3", "episode" to "9"),
            SeekrWire.query("series", "tmdb:1399:03:09"),
        )
        // no type (a deep link): the id's shape decides
        assertEquals(listOf("imdb_id" to "tt0133093"), SeekrWire.query(null, "tt0133093"))
        assertEquals("show_imdb_id", SeekrWire.query(null, "tt0903747:1:2")!![0].first)
    }

    @Test fun query_refusesEverythingElse() {
        for ((t, id) in listOf(
            "series" to "tt0903747",            // a series page, not an episode
            "movie" to "tt0903747:1:2",         // an episode id typed as a film
            "series" to "kitsu:1:5",
            "movie" to "kitsu:1",
            "tv" to "tt0133093",                // a channel
            "channel" to "sportsx:abc",
            "movie" to "ttabc",
            "movie" to "tmdb:0",
            "movie" to "tmdb:603:1",            // no episode number
            "series" to "tt0903747:1",
            "movie" to "",
        )) assertNull("$t $id", SeekrWire.query(t, id))
        assertNull(SeekrWire.query("movie", null))
        assertEquals(listOf("imdb_id" to "tt0133093"), SeekrWire.query("movie", " tt0133093 "))
    }

    @Test fun parseTime_formats() {
        assertEquals(3_723_450L, SeekrWire.parseTime("01:02:03.450"))
        assertEquals(123_450L, SeekrWire.parseTime("02:03.450"))
        assertEquals(123_000L, SeekrWire.parseTime("02:03"))
        assertEquals(10_500L, SeekrWire.parseTime("00:00:10.5"))
        assertNull(SeekrWire.parseTime("1:2:3:4"))
        assertNull(SeekrWire.parseTime("abc"))
    }

    private val vtt = """
        WEBVTT

        00:00:00.000 --> 00:00:10.000
        https://sprites.seekr.tv/a/s0.jpg?exp=1&sig=x#xywh=0,0,320,180

        00:00:10.000 --> 00:00:20.000
        https://sprites.seekr.tv/a/s0.jpg?exp=1&sig=x#xywh=320,0,320,180

        00:00:20.000 --> 00:00:30.000
        https://sprites.seekr.tv/a/s1.jpg?exp=1&sig=x#xywh=0,180,320,180
    """.trimIndent()

    @Test fun parseVtt_readsCues() {
        val c = SeekrWire.parseVtt(vtt.replace("\n", "\r\n"))
        assertEquals(3, c.size)
        assertEquals(10_000L, c[1].startMs); assertEquals(20_000L, c[1].endMs)
        assertEquals("https://sprites.seekr.tv/a/s0.jpg?exp=1&sig=x", c[1].sheet)
        assertEquals(320, c[1].x); assertEquals(0, c[1].y); assertEquals(320, c[1].w); assertEquals(180, c[1].h)
        assertEquals("https://sprites.seekr.tv/a/s1.jpg?exp=1&sig=x", c[2].sheet); assertEquals(180, c[2].y)
    }

    @Test fun parseVtt_dropsBadCues() {
        assertTrue(SeekrWire.parseVtt("NOT A VTT\n\n00:00:00.000 --> 00:00:10.000\nhttps://x/y.jpg#xywh=0,0,1,1").isEmpty())
        val c = SeekrWire.parseVtt(
            """
            WEBVTT

            00:00:00.000 --> 00:00:10.000
            http://plain.example/s.jpg#xywh=0,0,320,180

            00:00:10.000 --> 00:00:20.000
            https://sprites.seekr.tv/s.jpg#xywh=0,0,0,180

            00:00:20.000 --> 00:00:30.000
            00:00:30.000 --> 00:00:40.000
            https://sprites.seekr.tv/s.jpg#xywh=640,0,320,180

            00:00:50.000 --> 00:00:40.000
            https://sprites.seekr.tv/s.jpg#xywh=0,0,320,180
            """.trimIndent(),
        )
        // http, a zero-width region, a cue with no payload and an end before its start are dropped; the cue after the
        // payload-less one survives
        assertEquals(1, c.size)
        assertEquals(30_000L, c[0].startMs); assertEquals(640, c[0].x)
    }

    @Test fun cueIndex_midpointRule() {
        val c = SeekrWire.parseVtt(vtt)
        assertEquals(0, SeekrWire.cueIndex(c, 0, 1.0))
        assertEquals(0, SeekrWire.cueIndex(c, 4_999, 1.0))
        assertEquals(0, SeekrWire.cueIndex(c, 5_000, 1.0))      // at the midpoint: still this cue
        assertEquals(1, SeekrWire.cueIndex(c, 5_001, 1.0))      // past it: the next picture is nearer
        assertEquals(1, SeekrWire.cueIndex(c, 10_000, 1.0))
        assertEquals(2, SeekrWire.cueIndex(c, 26_000, 1.0))     // the last cue has no next
        assertEquals(2, SeekrWire.cueIndex(c, 99_000, 1.0))
        assertEquals(-1, SeekrWire.cueIndex(emptyList(), 1_000, 1.0))
    }

    @Test fun cueIndex_scale() {
        val c = SeekrWire.parseVtt(vtt)
        // our copy is 2× the source's length (scale 2.0): player 20 s = source 10 s
        assertEquals(1, SeekrWire.cueIndex(c, 20_000, 2.0))
        assertEquals(0, SeekrWire.cueIndex(c, 9_000, 2.0))      // source 4.5 s
        // our copy is shorter (scale 0.5): player 10 s = source 20 s
        assertEquals(2, SeekrWire.cueIndex(c, 10_000, 0.5))
        // a nonsense scale is read as 1
        assertEquals(1, SeekrWire.cueIndex(c, 12_000, 0.0))
        assertEquals(1, SeekrWire.cueIndex(c, 12_000, Double.NaN))
        // before the first cue (a VTT that starts late) → the first cue
        val late = SeekrWire.parseVtt("WEBVTT\n\n00:00:30.000 --> 00:00:40.000\nhttps://s.seekr.tv/a.jpg#xywh=0,0,320,180\n")
        assertEquals(0, SeekrWire.cueIndex(late, 1_000, 1.0))
    }

    @Test fun key_shape() {
        assertTrue(SeekrWire.validKey(key))
        assertFalse(SeekrWire.validKey(key.uppercase()))
        assertFalse(SeekrWire.validKey(key.dropLast(1)))
        assertFalse(SeekrWire.validKey("sk_test_" + "0".repeat(64)))
        assertFalse(SeekrWire.validKey(""))
    }

    @Test fun doc_newestWins() {
        val d = JSONObject(SeekrWire.doc(key, 100L))
        assertEquals(key, d.getString("key")); assertEquals(100L, d.getLong("at"))
        // newer remote → adopt
        assertEquals(true to false, SeekrWire.mergeDecision(100L, JSONObject(SeekrWire.doc(key, 200L))))
        // a newer disconnect is adopted too
        assertEquals(true to false, SeekrWire.mergeDecision(100L, JSONObject(SeekrWire.doc("", 200L))))
        assertEquals("" to 200L, SeekrWire.read(JSONObject(SeekrWire.doc("", 200L))))
        // older remote → keep ours and push it back
        assertEquals(false to true, SeekrWire.mergeDecision(300L, JSONObject(SeekrWire.doc(key, 200L))))
        // equal → nothing
        assertEquals(false to false, SeekrWire.mergeDecision(200L, JSONObject(SeekrWire.doc(key, 200L))))
        // malformed remote (bad key, no stamp) → ours goes over it when we have one
        assertEquals(false to true, SeekrWire.mergeDecision(5L, JSONObject().put("key", "nope").put("at", 900L)))
        assertEquals(false to false, SeekrWire.mergeDecision(0L, JSONObject().put("key", key)))
        assertNull(SeekrWire.read(JSONObject().put("key", 5).put("at", 9L)))
    }
}
