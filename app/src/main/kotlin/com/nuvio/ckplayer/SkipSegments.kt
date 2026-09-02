package com.nuvio.ckplayer

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Where an episode's recap, opening titles and closing credits fall. Nebula Cloud
 * answers `/v1/skip?id=tt…:S:E` from a community timestamp database and caches it;
 * the request carries the episode id and nothing else. Asked once per episode,
 * only for series episodes with an IMDb-shaped id, and never when the setting is
 * Off. Mirrors the shared player's skipState.
 */
object SkipSegments {
    class Seg(val startMs: Long, val endMs: Long)
    class Segs(val recap: Seg?, val intro: Seg?, val outro: Seg?)

    private const val URL = "https://play.rifflehq.in/cloud/v1/skip?id="
    private val ID = Regex("^tt\\d+:\\d+:\\d+$")
    private val cache = HashMap<String, Segs>()          // this process; the cloud remembers for a day
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun eligible(type: String?, id: String?) = type == "series" && id != null && ID.matches(id)

    /** null when the cloud could not be reached — asked again next time. */
    suspend fun load(id: String): Segs? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[id] }?.let { return@withContext it }
        val got = runCatching {
            http.newCall(Request.Builder().url(URL + Uri.encode(id)).get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val o = JSONObject(r.body?.string() ?: "{}")
                fun seg(k: String): Seg? {
                    val s = o.optJSONObject(k) ?: return null
                    val a = s.optDouble("start", Double.NaN)
                    val b = s.optDouble("end", Double.NaN)
                    if (a.isNaN() || b.isNaN() || a < 0 || b - a < 1) return null
                    return Seg((a * 1000).toLong(), (b * 1000).toLong())
                }
                Segs(seg("recap"), seg("intro"), seg("outro"))
            }
        }.getOrNull()
        if (got != null) synchronized(cache) { cache[id] = got }
        got
    }

    /** The recap or intro `posMs` sits inside, with at least a second left to skip. */
    fun at(segs: Segs?, posMs: Long): Pair<String, Seg>? {
        segs ?: return null
        segs.recap?.let { if (posMs >= it.startMs && posMs < it.endMs - 1000) return "recap" to it }
        segs.intro?.let { if (posMs >= it.startMs && posMs < it.endMs - 1000) return "intro" to it }
        return null
    }

    /** The closing credits have started. */
    fun inOutro(segs: Segs?, posMs: Long): Boolean = segs?.outro?.let { posMs >= it.startMs } == true

    fun label(kind: String) = if (kind == "recap") "Skip recap" else "Skip intro"
    fun note(kind: String) = if (kind == "recap") "Skipped the recap" else "Skipped the intro"
}
