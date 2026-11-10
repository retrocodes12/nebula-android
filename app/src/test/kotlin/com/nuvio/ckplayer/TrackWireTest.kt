package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-10 tracking (Trakt · Simkl · MDBList): the synced doc, its newest-wins rule per service, what a play is to
    each service, and which call an event becomes — the web player's `track*` rules. */
class TrackWireTest {
    private val tok = "a".repeat(64)

    @Test fun doc_roundTripsAndDropsWhatIsMalformed() {
        val d = TrackDoc(TrackAuth(tok, "b".repeat(64), 1794374755000, "ana", 5), TrackAuth(at = 7), TrackKey("rigkey12345678", "rig", 9))
        val back = TrackWire.read(JSONObject(TrackWire.doc(d)))
        assertEquals(d, back)
        // the web player's doc, as it writes it
        val web = TrackWire.read(JSONObject("""{"trakt":{"a":"$tok","r":"","exp":1794374755000,"user":"an<b>a","at":3},"simkl":{"a":"","r":"","exp":0,"user":"","at":0},"mdbl":{"key":"","user":"","at":4}}"""))
        assertEquals(tok, web.trakt.a)
        assertEquals("anba", web.trakt.user)
        assertEquals("", web.simkl.a)
        assertEquals(4L, web.mdbl.at)
        // a token or key that cannot be one reads as not connected, its stamp kept
        val bad = TrackWire.read(JSONObject("""{"trakt":{"a":"has space","r":"x","exp":9,"user":"u","at":8},"mdbl":{"key":"!!","user":"u","at":2}}"""))
        assertEquals(TrackAuth(at = 8), bad.trakt)
        assertEquals(TrackKey(at = 2), bad.mdbl)
        assertEquals(TrackDoc(), TrackWire.read(null))
        assertEquals(TrackDoc(), TrackWire.read(JSONObject("{}")))
    }

    @Test fun merge_isNewestWinsPerService() {
        val local = TrackDoc(TrackAuth(tok, at = 10), TrackAuth(tok, at = 5), TrackKey("rigkey12345678", at = 1))
        val remote = TrackDoc(TrackAuth(at = 12), TrackAuth(tok, at = 3), TrackKey("rigkey12345678", at = 1))
        val (kept, changed, ours) = TrackWire.merge(local, remote)
        assertEquals("a disconnect made elsewhere later wins", "", kept.trakt.a)
        assertEquals(12L, kept.trakt.at)
        assertEquals("ours is newer for Simkl", 5L, kept.simkl.at)
        assertTrue(changed)
        assertTrue("and goes back up", ours)
        val (same, changed2, ours2) = TrackWire.merge(local, local)
        assertEquals(local, same)
        assertFalse(changed2); assertFalse(ours2)
        assertTrue(TrackDoc(mdbl = TrackKey(at = 3)).stamped)
        assertFalse(TrackDoc(mdbl = TrackKey(at = 3)).any)
        assertFalse(TrackDoc().stamped)
    }

    @Test fun item_mapsFilmsAndEpisodesForEachService() {
        val film = TrackWire.item("movie", "tt0111161")!!
        assertEquals("""{"movie":{"ids":{"imdb":"tt0111161"}}}""", film.trakt.toString())
        assertEquals("""{"movie":{"ids":{"imdb":"tt0111161"}}}""", film.simkl.toString())
        assertEquals("""{"movie":{"ids":{"imdb":"tt0111161"}}}""", film.mdbl.toString())
        val ep = TrackWire.item("series", "tt14452776:1:2")!!
        assertEquals("tt14452776", ep.trakt!!.getJSONObject("show").getJSONObject("ids").getString("imdb"))
        assertEquals(1, ep.trakt!!.getJSONObject("episode").getInt("season"))
        assertEquals(2, ep.trakt!!.getJSONObject("episode").getInt("number"))
        assertEquals(2, ep.simkl!!.getJSONObject("episode").getInt("number"))
        // MDBList keeps the season inside the show
        assertEquals(2, ep.mdbl!!.getJSONObject("show").getJSONObject("season").getJSONObject("episode").getInt("number"))
        assertEquals(1, ep.mdbl!!.getJSONObject("show").getJSONObject("season").getInt("number"))
        val tm = TrackWire.item("movie", "tmdb:278")!!
        assertEquals(278L, tm.trakt!!.getJSONObject("movie").getJSONObject("ids").getLong("tmdb"))
        assertEquals("278", tm.simkl!!.getJSONObject("movie").getJSONObject("ids").getString("tmdb"))
        // anime by its kitsu id: Simkl alone knows an episode, MDBList a film too
        val an = TrackWire.item("series", "kitsu:7442:4")!!
        assertNull(an.trakt); assertNull(an.mdbl)
        assertEquals("7442", an.simkl!!.getJSONObject("anime").getJSONObject("ids").getString("kitsu"))
        assertEquals(4, an.simkl!!.getJSONObject("episode").getInt("number"))
        val af = TrackWire.item("movie", "kitsu:7442")!!
        assertNull(af.trakt)
        assertEquals(7442L, af.mdbl!!.getJSONObject("movie").getJSONObject("ids").getLong("kitsu"))
    }

    @Test fun item_refusesEverythingElse() {
        assertNull(TrackWire.item("series", "tt14452776"))          // a show's bare id is not a play
        assertNull(TrackWire.item("tv", "channel:espn"))
        assertNull(TrackWire.item("movie", "yt:abc"))
        assertNull(TrackWire.item(null, "tt0111161"))
        assertNull(TrackWire.item("movie", null))
        assertNull(TrackWire.item("movie", "tt12"))
    }

    @Test fun body_carriesThePlaceAndLeavesTheItemAlone() {
        val film = TrackWire.item("movie", "tt0111161")!!
        val b = TrackWire.body(film.trakt!!, 28.6549)
        assertEquals(28.65, b.getDouble("progress"), 0.0001)
        assertFalse("the item is reused for the next event", film.trakt!!.has("progress"))
        assertEquals(100.0, TrackWire.body(film.trakt!!, 140.0).getDouble("progress"), 0.0)
        assertEquals(0.0, TrackWire.body(film.trakt!!, -3.0).getDouble("progress"), 0.0)
    }

    @Test fun trakt_hasNoPauseOfItsOwn() {
        assertEquals("start", TrackWire.traktCall("start", 0.0))
        assertEquals("a pause under 80 % is its stop (the place is kept)", "stop", TrackWire.traktCall("pause", 40.0))
        assertNull("a pause past 80 % says nothing: the stop that follows marks it watched", TrackWire.traktCall("pause", 85.0))
        assertNull("under 1 % Trakt refuses the call", TrackWire.traktCall("pause", 0.4))
        assertNull(TrackWire.traktCall("stop", 0.4))
        assertEquals("stop", TrackWire.traktCall("stop", 100.0))
        assertNull(TrackWire.traktCall("rewind", 50.0))
    }

    @Test fun shapes() {
        assertTrue(TrackWire.validToken("simkl_at_" + "x".repeat(34)))
        assertFalse(TrackWire.validToken("short"))
        assertTrue(TrackWire.validMdblKey("rigkey12345678"))
        assertFalse(TrackWire.validMdblKey("not a key!"))
        assertTrue(TrackWire.validClientId("rigtraktclientid0123456789"))
        assertFalse(TrackWire.validClientId(""))
    }
}
