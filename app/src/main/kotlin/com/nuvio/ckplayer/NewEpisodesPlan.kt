package com.nuvio.ckplayer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Which episodes are new, for the new-episode alerts (NewEpisodes.kt). Pure, for the tests.
 *
 * A show is followed when it is in a profile's My List or in its Continue watching. Each followed show remembers when
 * it was last checked; an episode is new when its release time is in the past and after that check. A show checked for
 * the first time only records the time — turning alerts on, or saving a show, never floods the phone with its back
 * catalogue.
 */
object NewEpisodesPlan {
    data class Show(val profile: String, val type: String, val id: String, val name: String, val addonUrl: String) {
        /** Remembered by profile + show: both profiles may follow one show. */
        val key: String get() = "$profile|$type:$id"
    }

    /** An episode's release time in ms (ISO date-time, or a bare date = that day's start in UTC), or null. */
    fun releasedAt(s: String?): Long? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return null
        runCatching { return java.time.Instant.parse(t).toEpochMilli() }
        runCatching { return java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        return runCatching {
            java.time.LocalDate.parse(t.take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** The episodes out since [since] and by [now], oldest first; none on a first check ([since] 0). Specials (season 0)
        and episodes this profile already finished are left out. */
    fun fresh(eps: List<Episode>, since: Long, now: Long, watched: Set<String> = emptySet()): List<Episode> {
        if (since <= 0L) return emptyList()
        return eps.filter { e ->
            val at = releasedAt(e.released) ?: return@filter false
            e.season > 0 && at > since && at <= now && e.id !in watched
        }.distinctBy { it.id }.sortedWith(compareBy({ it.season }, { it.episode ?: 0 }))
    }

    /** One profile's followed shows from its stores (the raw documents): My List's series and Continue watching's. */
    fun shows(profile: String, progress: JSONObject, library: JSONObject): List<Show> {
        val out = LinkedHashMap<String, Show>()
        for (k in library.keys()) {
            val r = library.optJSONObject(k) ?: continue
            if (r.optBoolean("removed") || r.optString("type") != "series") continue
            val id = r.optString("id")
            if (id.isEmpty()) continue
            out.getOrPut(id) { Show(profile, "series", id, r.optString("name"), r.optString("addonUrl")) }
        }
        for (k in progress.keys()) {
            val r = progress.optJSONObject(k) ?: continue
            if (r.optString("type") != "series" || r.optBoolean("done") || r.optBoolean("dismissed")) continue
            val pos = r.optLong("pos"); val dur = r.optLong("dur")
            if (pos < Progress.MIN_POS_MS || dur <= 0 || pos > dur - Progress.END_GAP_MS) continue
            val ep = r.optString("id")
            if (ep.isEmpty()) continue
            val id = seriesIdOf(ep)
            if (id == ep) continue
            out.getOrPut(id) { Show(profile, "series", id, r.optString("name").split(" · ").first(), r.optString("addonUrl")) }
        }
        return out.values.toList()
    }

    /** The episode ids a profile has finished (its progress store's `done` records). */
    fun watched(progress: JSONObject): Set<String> {
        val out = HashSet<String>()
        for (k in progress.keys()) {
            val r = progress.optJSONObject(k) ?: continue
            if (r.optBoolean("done")) out += r.optString("id")
        }
        return out
    }

    fun tag(e: Episode): String = "S${e.season}" + (e.episode?.let { "E$it" } ?: "")

    /** The notification's two lines: "New episode · Show" and "S2E5 · Title", "… and 2 more", "· for <profile>" when
        the account holds two profiles ([profileName] null otherwise). */
    fun lines(showName: String, eps: List<Episode>, profileName: String?): Pair<String, String> {
        val last = eps.last()
        val head = (if (eps.size > 1) "New episodes · " else "New episode · ") + showName.ifBlank { "A show you follow" }
        var text = tag(last) + (if (last.name.isNotBlank()) " · " + last.name.trim() else "")
        if (eps.size > 1) text += " and ${eps.size - 1} more"
        if (profileName != null) text += " · for $profileName"
        return head to text
    }

    /** The checks remembered from last time, kept only for shows still followed. */
    fun keep(last: Map<String, Long>, shows: List<Show>): Map<String, Long> {
        val live = shows.map { it.key }.toSet()
        return last.filterKeys { it in live }
    }

    /** An add-on list document (the `addons` store): (manifest url, base, on). */
    fun addons(raw: String?): List<Triple<String, String, Boolean>> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url")
            if (url.isEmpty()) null else Triple(url, o.optString("base").ifEmpty { Stremio.baseOf(url) }, !o.optBoolean("off", false))
        }
    }.getOrDefault(emptyList())
}
