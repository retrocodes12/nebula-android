package com.nuvio.ckplayer

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * My List — one record per saved title, keyed "<type>:<id>", stored as the SAME
 * JSON document the web player keeps (removed items stay as {removed:true,at}
 * tombstones so a removal beats a stale copy when devices sync).
 */
data class LibItem(
    val type: String,
    val id: String,
    val name: String,
    val poster: String?,
    val shape: String,
    val addonUrl: String,
    val at: Long,
)

object Library {
    private const val PREFS = "ckplayer"
    private const val KEY = "library"
    private const val MAX = 500

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The raw document, tombstones included — what sync pushes and merges. */
    internal fun all(ctx: Context): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString(KEY, "{}") ?: "{}") }.getOrDefault(JSONObject())

    internal fun replaceAll(ctx: Context, o: JSONObject) {
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }

    private fun persist(ctx: Context, o: JSONObject) {
        // tombstones purge only under space pressure, and old ones first — a
        // device offline for months may still hold the record a tombstone beats
        val keys = o.keys().asSequence().toList()
        if (keys.size > 200) {
            val cut = System.currentTimeMillis() - 180L * 24 * 3600_000
            keys.forEach { k ->
                val r = o.optJSONObject(k) ?: return@forEach
                if (r.optBoolean("removed") && r.optLong("at") < cut) o.remove(k)
            }
        }
        val live = o.keys().asSequence().toList().filter { o.optJSONObject(it)?.optBoolean("removed") == false }
        if (live.size > MAX) {
            live.sortedByDescending { o.getJSONObject(it).optLong("at") }.drop(MAX).forEach { o.remove(it) }
        }
        replaceAll(ctx, o)
        Cloud.noteChanged(ctx, "library")
    }

    fun inList(ctx: Context, type: String, id: String): Boolean {
        val r = all(ctx).optJSONObject("$type:$id") ?: return false
        return !r.optBoolean("removed")
    }

    /** Add or remove; returns true when the item is now saved. */
    fun toggle(ctx: Context, type: String, item: MetaItem, addonUrl: String): Boolean {
        val o = all(ctx)
        val k = "$type:${item.id}"
        val cur = o.optJSONObject(k)
        val nowIn: Boolean
        if (cur != null && !cur.optBoolean("removed")) {
            o.put(k, JSONObject().put("removed", true).put("at", System.currentTimeMillis()))
            nowIn = false
        } else {
            o.put(k, JSONObject()
                .put("type", type).put("id", item.id).put("name", item.name)
                .put("poster", item.poster ?: "").put("shape", item.posterShape)
                .put("addonUrl", addonUrl).put("at", System.currentTimeMillis()))
            nowIn = true
        }
        persist(ctx, o)
        return nowIn
    }

    /** Saved titles, newest first. */
    fun list(ctx: Context): List<LibItem> {
        val o = all(ctx)
        val out = mutableListOf<LibItem>()
        for (k in o.keys()) {
            val r = o.optJSONObject(k) ?: continue
            if (r.optBoolean("removed") || r.optString("id").isEmpty()) continue
            out.add(LibItem(
                type = r.optString("type"), id = r.optString("id"), name = r.optString("name"),
                poster = r.optString("poster").ifEmpty { null },
                shape = r.optString("shape", "poster"),
                addonUrl = r.optString("addonUrl"), at = r.optLong("at"),
            ))
        }
        return out.sortedByDescending { it.at }
    }

    // ---------- upcoming episodes for saved series ----------
    data class UpRow(val series: LibItem, val ep: Episode, val time: Long)

    private val isoDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parseReleased(s: String?): Long {
        if (s.isNullOrEmpty()) return 0L
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(s.take(19))?.time ?: 0L
        }.recoverCatching { isoDay.parse(s.take(10))?.time ?: 0L }.getOrDefault(0L)
    }

    /**
     * Episodes dated within the last week or the next 45 days across every saved
     * series — fetched four series at a time so a big library doesn't crawl.
     */
    suspend fun upcoming(ctx: Context): List<UpRow> {
        val series = list(ctx).filter { it.type == "series" }.take(25)
        if (series.isEmpty()) return emptyList()
        val addons = loadAddons(ctx)
        val floor = System.currentTimeMillis() - 8L * 24 * 3600_000
        val horizon = System.currentTimeMillis() + 45L * 24 * 3600_000
        val rows = mutableListOf<UpRow>()
        for (chunk in series.chunked(4)) {
            coroutineScope {
                chunk.map { s ->
                    async {
                        val origin = addons.firstOrNull { it.manifestUrl == s.addonUrl }
                        val order = (listOfNotNull(origin) + addons.filterNot { it.manifestUrl == s.addonUrl })
                        for (a in order) {
                            val vids = runCatching {
                                if (origin?.manifestUrl != a.manifestUrl &&
                                    !manifestFor(a.manifestUrl).canMeta("series", s.id)) return@runCatching emptyList()
                                Stremio.loadSeriesVideos(a.base, "series", s.id)
                            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                .getOrDefault(emptyList())
                            if (vids.isEmpty()) continue
                            synchronized(rows) {
                                vids.forEach { v ->
                                    val t = parseReleased(v.released)
                                    if (t in (floor + 1)..horizon) rows.add(UpRow(s, v, t))
                                }
                            }
                            break
                        }
                    }
                }.forEach { it.await() }
            }
        }
        return rows.sortedBy { it.time }.take(60)
    }

    /** "Today", "Tomorrow", "Yesterday", else "Mon 5 Oct". */
    fun dayLabel(t: Long): String {
        val day = SimpleDateFormat("yyyy-DDD", Locale.US)
        val now = System.currentTimeMillis()
        val d = day.format(t)
        return when (d) {
            day.format(now) -> "Today"
            day.format(now + 86_400_000) -> "Tomorrow"
            day.format(now - 86_400_000) -> "Yesterday"
            else -> SimpleDateFormat("EEE d MMM", Locale.US).format(t)
        }
    }
}
