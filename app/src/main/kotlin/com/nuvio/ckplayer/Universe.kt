package com.nuvio.ckplayer

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A title's Universe: what it follows, what follows it, its spin-offs, what it spun off
 * from, its remakes. Nebula Cloud answers `/v1/universe?id=tt…` from a film database and
 * keeps each answer a week, so a title costs the server one lookup a week; this process
 * keeps it too, so a title page asks at most once per run. The request carries the title
 * id and nothing else. Mirrors the shared player's detUniverse.
 */
object Universe {
    class Item(val rel: String, val meta: MetaItem)

    private const val URL = "https://play.rifflehq.in/cloud/v1/universe?id="
    private val TT = Regex("^(tt\\d{5,10})(?::.*)?$")
    private val cache = HashMap<String, List<Item>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** The title's own tt… id (an episode id gives its show's), or null when it has none. */
    fun idOf(id: String?): String? = id?.let { TT.find(it)?.groupValues?.get(1) }

    fun label(rel: String) = when (rel) {
        "follows" -> "Follows"
        "followed_by" -> "Followed by"
        "spin_off_from" -> "Spin-off from"
        "spin_off" -> "Spin-off"
        "remake_of" -> "Remake of"
        "remade_as" -> "Remade as"
        else -> ""
    }

    fun cached(id: String): List<Item>? = synchronized(cache) { cache[id] }

    /** null when the cloud could not be reached — asked again next time the page opens. */
    suspend fun load(id: String): List<Item>? = withContext(Dispatchers.IO) {
        cached(id)?.let { return@withContext it }
        val got = runCatching {
            http.newCall(Request.Builder().url(URL + Uri.encode(id)).get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val arr = JSONObject(r.body?.string() ?: "{}").optJSONArray("items") ?: return@use null
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val tid = o.optString("id")
                    val name = o.optString("name")
                    if (idOf(tid) != tid || name.isEmpty()) return@mapNotNull null
                    val type = if (o.optString("type") == "series") "series" else "movie"
                    val year = o.optInt("year", 0)
                    val end = o.optInt("end", 0)
                    val span = when {
                        year <= 0 -> null
                        type == "series" && end > 0 && end != year -> "$year–$end"
                        else -> year.toString()
                    }
                    Item(o.optString("rel"), MetaItem(tid, type, name, (if (o.isNull("poster")) "" else o.optString("poster")).ifEmpty { null }, releaseInfo = span))
                }
            }
        }.getOrNull()
        if (got != null) synchronized(cache) {
            if (cache.size >= 300) cache.clear()
            cache[id] = got
        }
        got
    }
}
