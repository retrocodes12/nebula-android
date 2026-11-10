package com.nuvio.ckplayer

import org.json.JSONObject
import kotlin.math.roundToLong

/** One tracking service's sign-in on a profile: the token ([a]), the one that renews it ([r]), when the token runs out
    ([exp], ms), the account's name, and when this record was written ([at] — the newest wins across devices). */
internal data class TrackAuth(val a: String = "", val r: String = "", val exp: Long = 0L, val user: String = "", val at: Long = 0L)

/** MDBList has no sign-in: the person's own API key. */
internal data class TrackKey(val key: String = "", val user: String = "", val at: Long = 0L)

/** The synced doc `track` (`track_b` for the second profile) — the shape the web/TV player writes. */
internal data class TrackDoc(val trakt: TrackAuth = TrackAuth(), val simkl: TrackAuth = TrackAuth(), val mdbl: TrackKey = TrackKey()) {
    val any: Boolean get() = trakt.a.isNotEmpty() || simkl.a.isNotEmpty() || mdbl.key.isNotEmpty()
    val stamped: Boolean get() = trakt.at > 0 || simkl.at > 0 || mdbl.at > 0
}

/**
 * The plain-Kotlin half of tracking (no Android here, so the JVM tests reach all of it): the synced doc's shape and its
 * newest-wins rule per service, what a play is to each service, and which call an event becomes. The shared web/TV
 * player follows the same rules (its `track*` block).
 */
internal object TrackWire {
    private val TOKEN = Regex("[A-Za-z0-9._~+/=-]{16,2048}")
    private val MDBL_KEY = Regex("[A-Za-z0-9_-]{8,128}")
    private val CLIENT_ID = Regex("[A-Za-z0-9_-]{16,128}")
    private val IMDB = Regex("tt\\d{5,10}")
    private val IMDB_EP = Regex("(tt\\d{5,10}):(\\d{1,3}):(\\d{1,5})")
    private val TMDB = Regex("tmdb:(\\d{1,9})")
    private val TMDB_EP = Regex("tmdb:(\\d{1,9}):(\\d{1,3}):(\\d{1,5})")
    private val KITSU = Regex("kitsu:(\\d{1,9})")
    private val KITSU_EP = Regex("kitsu:(\\d{1,9}):(\\d{1,5})")

    fun validToken(v: String) = TOKEN.matches(v)
    fun validMdblKey(v: String) = MDBL_KEY.matches(v)
    fun validClientId(v: String) = CLIENT_ID.matches(v)
    fun cleanName(v: String) = v.filter { it >= ' ' && it != '<' && it != '>' }.take(60)

    private fun auth(o: JSONObject?): TrackAuth {
        if (o == null) return TrackAuth()
        val at = o.optLong("at")
        val a = o.optString("a")
        if (!validToken(a)) return TrackAuth(at = at)          // a disconnect is a record too
        val r = o.optString("r")
        return TrackAuth(a, if (validToken(r)) r else "", o.optLong("exp"), cleanName(o.optString("user")), at)
    }

    /** A doc as stored or as the cloud holds it; anything malformed in it reads as not connected. */
    fun read(o: JSONObject?): TrackDoc {
        if (o == null) return TrackDoc()
        val m = o.optJSONObject("mdbl")
        val key = m?.optString("key").orEmpty()
        val mdbl = if (validMdblKey(key)) TrackKey(key, cleanName(m!!.optString("user")), m.optLong("at")) else TrackKey(at = m?.optLong("at") ?: 0L)
        return TrackDoc(auth(o.optJSONObject("trakt")), auth(o.optJSONObject("simkl")), mdbl)
    }

    private fun json(a: TrackAuth) = JSONObject().put("a", a.a).put("r", a.r).put("exp", a.exp).put("user", a.user).put("at", a.at)

    fun doc(d: TrackDoc): String = JSONObject()
        .put("trakt", json(d.trakt)).put("simkl", json(d.simkl))
        .put("mdbl", JSONObject().put("key", d.mdbl.key).put("user", d.mdbl.user).put("at", d.mdbl.at))
        .toString()

    /** Each service's record on its own, the newest wins: (the doc to keep, it changed here, ours is newer somewhere —
        push it back). */
    fun merge(local: TrackDoc, remote: TrackDoc): Triple<TrackDoc, Boolean, Boolean> {
        var changed = false
        var ours = false
        val trakt = when {
            remote.trakt.at > local.trakt.at -> { changed = true; remote.trakt }
            else -> { if (local.trakt.at > remote.trakt.at) ours = true; local.trakt }
        }
        val simkl = when {
            remote.simkl.at > local.simkl.at -> { changed = true; remote.simkl }
            else -> { if (local.simkl.at > remote.simkl.at) ours = true; local.simkl }
        }
        val mdbl = when {
            remote.mdbl.at > local.mdbl.at -> { changed = true; remote.mdbl }
            else -> { if (local.mdbl.at > remote.mdbl.at) ours = true; local.mdbl }
        }
        return Triple(TrackDoc(trakt, simkl, mdbl), changed, ours)
    }

    /** What a play is to each service; a null field = that service has no id for it. */
    class Item(val trakt: JSONObject?, val simkl: JSONObject?, val mdbl: JSONObject?)

    private fun ids(k: String, v: Any) = JSONObject().put("ids", JSONObject().put(k, v))
    private fun episode(s: Int, e: Int) = JSONObject().put("season", s).put("number", e)

    /**
     * Films and episodes with an IMDb or TMDB id go to all three; kitsu anime to Simkl (and a kitsu film to MDBList).
     * A channel, an add-on's own id, a series' bare id: null — nothing is reported.
     */
    fun item(type: String?, id: String?): Item? {
        val v = id?.trim().orEmpty()
        if (v.isEmpty() || type.isNullOrEmpty()) return null
        val film = type == "movie"
        if (film && IMDB.matches(v)) return Item(
            JSONObject().put("movie", ids("imdb", v)), JSONObject().put("movie", ids("imdb", v)), JSONObject().put("movie", ids("imdb", v)),
        )
        IMDB_EP.matchEntire(v)?.let { m ->
            val s = m.groupValues[2].toInt()
            val e = m.groupValues[3].toInt()
            val show = m.groupValues[1]
            return Item(
                JSONObject().put("show", ids("imdb", show)).put("episode", episode(s, e)),
                JSONObject().put("show", ids("imdb", show)).put("episode", episode(s, e)),
                JSONObject().put("show", ids("imdb", show).put("season", JSONObject().put("number", s).put("episode", JSONObject().put("number", e)))),
            )
        }
        if (film) TMDB.matchEntire(v)?.let { m ->
            val n = m.groupValues[1]
            return Item(
                JSONObject().put("movie", ids("tmdb", n.toLong())), JSONObject().put("movie", ids("tmdb", n)), JSONObject().put("movie", ids("tmdb", n.toLong())),
            )
        }
        TMDB_EP.matchEntire(v)?.let { m ->
            val n = m.groupValues[1]
            val s = m.groupValues[2].toInt()
            val e = m.groupValues[3].toInt()
            return Item(
                JSONObject().put("show", ids("tmdb", n.toLong())).put("episode", episode(s, e)),
                JSONObject().put("show", ids("tmdb", n)).put("episode", episode(s, e)),
                JSONObject().put("show", ids("tmdb", n.toLong()).put("season", JSONObject().put("number", s).put("episode", JSONObject().put("number", e)))),
            )
        }
        KITSU_EP.matchEntire(v)?.let { m ->
            return Item(null, JSONObject().put("anime", ids("kitsu", m.groupValues[1])).put("episode", JSONObject().put("number", m.groupValues[2].toInt())), null)
        }
        if (film) KITSU.matchEntire(v)?.let { m ->
            return Item(null, JSONObject().put("movie", ids("kitsu", m.groupValues[1])), JSONObject().put("movie", ids("kitsu", m.groupValues[1].toLong())))
        }
        return null
    }

    /** How far through, as the services take it: 0–100 with two decimals. */
    fun pct(v: Double): Double = (v.coerceIn(0.0, 100.0) * 100).roundToLong() / 100.0

    /** A copy of [item] with the place in it (the item itself is reused for every event of a play). */
    fun body(item: JSONObject, pct: Double): JSONObject = JSONObject(item.toString()).put("progress", pct(pct))

    /**
     * Trakt has no pause of its own: a stop under 80 % IS its pause (the place is kept), so a pause past 80 % says
     * nothing — the stop that follows marks the title watched — and under 1 % it refuses the call. Null = send nothing.
     */
    fun traktCall(event: String, pct: Double): String? = when (event) {
        "start" -> "start"
        "pause" -> if (pct >= 1.0 && pct < 80.0) "stop" else null
        "stop" -> if (pct >= 1.0) "stop" else null
        else -> null
    }
}
