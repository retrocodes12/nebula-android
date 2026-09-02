package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Same source, same quality. The fingerprint of the stream row a viewer picked,
 * and how much another row resembles it — so the next episode can be chosen
 * from the same add-on at the same resolution without a trip through the list.
 * Mirrors the shared web player (streamSig / streamScore / matchStream).
 */
data class StreamSig(
    val addonUrl: String, val addonName: String,
    val binge: String,            // behaviorHints.bingeGroup when the add-on declares one
    val res: String,              // the plate: 4K / 1080 / 720 / SD / ""
    val provider: String,
    val badges: List<String>,     // every badge that fires on the row
    val words: Set<String>,       // release words that survive to the next episode
)

object StreamTwin {
    private val RES_RANK = mapOf("4K" to 4, "1080" to 3, "720" to 2, "SD" to 1)
    private val RE_EPISODE = Regex("^s\\d+e\\d+$")

    private fun words(t: String): Set<String> =
        t.lowercase().split(Regex("[^a-z0-9]+")).filter { w ->
            w.length >= 3 && !w.all { it.isDigit() } && !RE_EPISODE.matches(w)   // episode-specific bits
        }.toSet()

    fun sig(s: StreamItem, addon: Addon): StreamSig {
        val raw = s.name + "\n" + s.title
        val m = StreamBadges.match(raw)
        val f = StreamBadges.facts(s.videoSize, s.title, m.fired)
        return StreamSig(
            addonUrl = addon.manifestUrl, addonName = addon.name,
            binge = s.bingeGroup, res = StreamBadges.plate(raw)?.res ?: "",
            provider = (f.provider ?: "").lowercase(),
            badges = StreamBadges.allBadges(raw),
            words = words(StreamBadges.cleanName(s.name, addon.name) + " " + s.title.lineSequence().firstOrNull().orEmpty()),
        )
    }

    /** How much `s` resembles the row picked last time; higher is closer. */
    fun score(s: StreamItem, sig: StreamSig, addon: Addon): Double {
        val c = sig(s, addon)
        var sc = 0.0
        if (sig.binge.isNotEmpty() && sig.binge == c.binge) sc += 100
        if (sig.res.isNotEmpty()) {
            sc += when {
                c.res.isEmpty() -> -10.0
                sig.res == c.res -> 40.0
                else -> -15.0 * abs((RES_RANK[sig.res] ?: 0) - (RES_RANK[c.res] ?: 0))
            }
        }
        if (sig.provider.isNotEmpty() && c.provider.isNotEmpty()) sc += if (sig.provider == c.provider) 30 else -10
        val shared = sig.badges.count { it in c.badges }
        sc += shared * 6 - (sig.badges.size - shared) * 3 - (c.badges.size - shared)
        val both = sig.words.count { it in c.words }
        val either = (sig.words + c.words).size
        if (either > 0) sc += 12.0 * both / either
        return sc
    }

    /** What an identical row would score against `sig` — a twin gets most of it. */
    fun max(sig: StreamSig): Double =
        (if (sig.binge.isNotEmpty()) 100 else 0) + (if (sig.res.isNotEmpty()) 40 else 0) +
            (if (sig.provider.isNotEmpty()) 30 else 0) + sig.badges.size * 6 + 12.0

    fun isTwin(s: StreamItem, sig: StreamSig, addon: Addon): Boolean = score(s, sig, addon) >= 0.6 * max(sig)

    /** The stream closest to `sig` (the list's first when nothing was picked). */
    fun match(streams: List<StreamItem>, sig: StreamSig?, addon: Addon): StreamItem? {
        if (streams.isEmpty()) return null
        if (sig == null) return streams.first()
        var best = streams.first()
        var bestSc = Double.NEGATIVE_INFINITY
        streams.forEachIndexed { i, s ->
            val sc = score(s, sig, addon) - i * 0.01          // earlier rows win ties
            if (sc > bestSc) { best = s; bestSc = sc }
        }
        return best
    }

    /** "1080p · Torrentio" — the source line the up-next card shows. */
    fun label(sig: StreamSig): String {
        val r = if (sig.res == "1080" || sig.res == "720") sig.res + "p" else sig.res
        return listOf(r, sig.addonName).filter { it.isNotEmpty() }.joinToString(" · ")
    }
}

/**
 * The next episode's stream, chosen while this one is still playing. The add-on
 * the viewer last picked from is asked first (the origin add-on when nothing was
 * picked), and the row closest to that pick wins. File-level, like the series
 * chain: only the top of the nav stack is composed.
 */
object NextEp {
    private const val KEY = "pick_v1"
    private var loaded = false
    private var picked: StreamSig? = null

    /** The last hand-pick. It outlives the session, so tomorrow's episode is marked
        and pre-chosen the same way. Device-local on purpose: a TV picks 4K, a phone 720p. */
    fun picked(ctx: Context): StreamSig? {
        if (!loaded) { loaded = true; picked = load(ctx) }
        return picked
    }

    /** A hand-pick becomes the taste; an auto-pick only if the series is untouched. */
    fun notePick(ctx: Context, s: StreamItem, addon: Addon, byHand: Boolean) {
        val sig = StreamTwin.sig(s, addon)
        if (byHand) { picked = sig; loaded = true; save(ctx, sig) }
        else if (picked(ctx) == null) picked = sig
    }

    private fun save(ctx: Context, sig: StreamSig) {
        val o = JSONObject()
            .put("addonUrl", sig.addonUrl).put("addonName", sig.addonName)
            .put("binge", sig.binge).put("res", sig.res).put("provider", sig.provider)
            .put("badges", JSONArray(sig.badges)).put("words", JSONArray(sig.words.toList()))
        ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    private fun load(ctx: Context): StreamSig? = runCatching {
        val raw = ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val o = JSONObject(raw)
        val url = o.optString("addonUrl")
        if (url.isEmpty()) return null
        fun strings(k: String): List<String> {
            val a = o.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
        }
        StreamSig(
            addonUrl = url, addonName = o.optString("addonName"),
            binge = o.optString("binge"), res = o.optString("res"), provider = o.optString("provider"),
            badges = strings("badges"), words = strings("words").toSet(),
        )
    }.getOrNull()

    class Ready(
        val id: String, val addon: Addon,
        val streams: List<StreamItem>?, val pick: StreamItem?,
        val failed: Boolean, val at: Long,
    )
    var ready by mutableStateOf<Ready?>(null)
        private set

    /** The add-on to ask first: the one last picked from, else the series' own. */
    fun source(ctx: Context, origin: Addon?): Addon? {
        val pf = picked(ctx)
        if (pf != null) activeAddons(ctx).firstOrNull { it.manifestUrl == pf.addonUrl }?.let { return it }
        return origin
    }

    /** Ask `a` for the episode's streams; with none, ask the origin add-on too. */
    suspend fun resolve(ctx: Context, a: Addon, origin: Addon?, type: String, id: String): Ready {
        val cands = listOf(a) + listOfNotNull(origin?.takeIf { it.manifestUrl != a.manifestUrl })
        for (c in cands) {
            val streams = runCatching { Stremio.loadStreams(c.base, type, id) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrDefault(emptyList())
            if (streams.isNotEmpty()) {
                return Ready(id, c, streams, StreamTwin.match(streams, picked(ctx), c), false, System.currentTimeMillis())
            }
        }
        return Ready(id, a, emptyList(), null, true, System.currentTimeMillis())
    }

    /** Start choosing `next`'s stream now (once per episode). */
    fun prefetch(ctx: Context, scope: CoroutineScope, origin: Addon?, type: String, next: Episode) {
        if (ready?.id == next.id) return
        val a = source(ctx, origin) ?: return
        val rec = Ready(next.id, a, null, null, false, System.currentTimeMillis())
        ready = rec
        scope.launch {
            val res = resolve(ctx, a, origin, type, next.id)
            if (ready === rec) ready = res
        }
    }

    /** The pre-chosen stream for `id` if it is still fresh — resolver links
        expire, so a choice is trusted for ten minutes. Clears either way. */
    fun take(id: String): Ready? {
        val r = ready
        ready = null
        return r?.takeIf { it.id == id && it.pick != null && System.currentTimeMillis() - it.at < 600_000 }
    }

    /** What the up-next card says under the episode name. */
    fun sourceLine(ctx: Context, nextId: String): String? {
        val r = ready ?: return null
        if (r.id != nextId) return null
        val pick = r.pick
        return when {
            pick != null -> {
                // a twin of the last pick reads as ready; anything less says so
                val pf = picked(ctx)
                val same = pf == null || StreamTwin.isTwin(pick, pf, r.addon)
                (if (same) "" else "Closest match · ") + StreamTwin.label(StreamTwin.sig(pick, r.addon)) + (if (same) " · Ready" else "")
            }
            r.failed -> "No stream found yet — Play opens the list"
            else -> "Finding a stream…"
        }
    }
}
