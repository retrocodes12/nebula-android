package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val file: String = "",        // the file name with the episode taken out (StreamTwin.releaseKey)
    val hash: String = "",        // the torrent it comes from — a season pack keeps it across episodes
)

object StreamTwin {
    private val RES_RANK = mapOf("4K" to 4, "1080" to 3, "720" to 2, "SD" to 1)
    private val RE_EPISODE = Regex("^s\\d+e\\d+$")

    private fun words(t: String): Set<String> =
        t.lowercase().split(Regex("[^a-z0-9]+")).filter { w ->
            w.length >= 3 && !w.all { it.isDigit() } && !RE_EPISODE.matches(w)   // episode-specific bits
        }.toSet()

    // A release's file name with the episode taken out: "Show.S01E03.Pilot.1080p.WEB.x265-GRP" and
    // "Show.S01E04.Other.Name.1080p.WEB.x265-GRP" give the same key — the show part before the episode
    // plus the release tail from its first quality word (episode names differ, so they never count).
    // A film keeps its whole name. "" when the row names no file. Mirrors the web player's releaseKey.
    private val RK_EXT = Regex("\\.(mkv|mp4|m4v|avi|webm|mov|ts|wmv|flv)$")
    private val RK_EP = Regex("(^|[^a-z0-9])(s\\d{1,2}[ ._-]?e\\d{1,3}(?:[-e]+\\d{1,3})?|\\d{1,2}x\\d{2,3}|(?:ep|episode)[ ._-]?\\d{1,3})(?=[^a-z0-9]|$)")
    private val RK_Q = Regex("^(2160p|1080p|720p|576p|480p|4k|uhd|web|webrip|webdl|web-dl|bluray|bdrip|brrip|hdtv|dvdrip|remux|hdr|hdr10|dv|x264|x265|h264|h265|hevc|avc|av1|10bit|amzn|nf|dsnp|hmax|atvp)$")
    private fun rkTokens(t: String) = t.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    fun releaseKey(name: String): String {
        var n = name.lowercase().trim()
        if (n.isEmpty()) return ""
        n = n.replace(Regex("^.*[/\\\\]"), "").replace(RK_EXT, "")
        val m = RK_EP.find(n)
        if (m == null) { val all = rkTokens(n); return if (all.size >= 3) "m:" + all.joinToString(".") else "" }
        val at = m.range.first + m.groupValues[1].length
        val head = rkTokens(n.substring(0, at))
        var tail = rkTokens(n.substring(at + m.groupValues[2].length))
        val q = tail.indexOfFirst { RK_Q.matches(it) }
        tail = if (q >= 0) tail.drop(q) else tail.takeLast(1)      // no quality word: the release group alone
        if (head.isEmpty() || tail.isEmpty()) return ""
        return "e:" + head.joinToString(".") + "|" + tail.joinToString(".")
    }

    /** The file a row plays, by name: the add-on's own filename hint, else a title line that reads like one. */
    fun fileName(s: StreamItem): String {
        if (s.fileName.isNotEmpty()) return s.fileName
        return s.title.lineSequence().map { it.trim() }.firstOrNull { l ->
            RK_EXT.containsMatchIn(l.lowercase()) || (!l.contains(' ') && l.split('.').size >= 4)
        }.orEmpty()
    }

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
            file = releaseKey(fileName(s)), hash = s.infoHash.lowercase(),
        )
    }

    /** How much `s` resembles the row picked last time; higher is closer. */
    fun score(s: StreamItem, sig: StreamSig, addon: Addon): Double {
        val c = sig(s, addon)
        var sc = 0.0
        if (sig.binge.isNotEmpty() && sig.binge == c.binge) sc += 100
        // the same file pattern is the same release, whatever the add-on calls its group; the same
        // torrent (a season pack) even more so. A different named file is a different release.
        if (sig.hash.isNotEmpty() && sig.hash == c.hash) sc += 200
        if (sig.file.isNotEmpty() && c.file.isNotEmpty()) sc += if (sig.file == c.file) 150 else -20
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

    fun isTwin(s: StreamItem, sig: StreamSig, addon: Addon): Boolean {
        // a row naming another file is another release, however alike the rest reads (one torrent aside)
        if (sig.file.isNotEmpty()) {
            val c = sig(s, addon)
            if (c.file.isNotEmpty() && c.file != sig.file && !(sig.hash.isNotEmpty() && sig.hash == c.hash)) return false
        }
        return score(s, sig, addon) >= 0.6 * max(sig)
    }

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
            .put("file", sig.file).put("hash", sig.hash)
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
            file = o.optString("file"), hash = o.optString("hash"),
        )
    }.getOrNull()

    class Ready(
        val id: String, val addon: Addon,
        val streams: List<StreamItem>?, val pick: StreamItem?,
        val failed: Boolean, val at: Long,
        val moved: Boolean = false,      // the same release, found at another add-on: the taste follows it there
    )
    var ready by mutableStateOf<Ready?>(null)
        private set

    /** The add-on to ask first: the one last picked from, else the series' own. */
    fun source(ctx: Context, origin: Addon?): Addon? {
        val pf = picked(ctx)
        if (pf != null) activeAddons(ctx).firstOrNull { it.manifestUrl == pf.addonUrl }?.let { return it }
        return origin
    }

    /** Ask `a` for the episode's streams; with none, ask the origin add-on too. When the last pick named
        its file and neither has that release, every other streaming add-on is asked for it (15 s each)
        — the add-on it came from may be down, or may have renamed its group. */
    suspend fun resolve(ctx: Context, a: Addon, origin: Addon?, type: String, id: String): Ready {
        val cands = listOf(a) + listOfNotNull(origin?.takeIf { it.manifestUrl != a.manifestUrl })
        val pf = picked(ctx)
        var best: Ready? = null
        for (c in cands) {
            val streams = runCatching { Stremio.loadStreams(c.base, type, id) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrDefault(emptyList())
            if (streams.isNotEmpty()) {
                val pick = StreamTwin.match(streams, pf, c)
                val res = Ready(id, c, streams, pick, false, System.currentTimeMillis())
                if (pf == null || pf.file.isEmpty() || (pick != null && StreamTwin.isTwin(pick, pf, c))) return res
                if (best == null) best = res
            }
        }
        if (pf != null && pf.file.isNotEmpty()) {
            val asked = cands.map { it.manifestUrl }.toSet()
            val others = activeAddons(ctx).filter { it.manifestUrl !in asked }.take(8)
            val hits = coroutineScope {
                others.map { o ->
                    async {
                        withTimeoutOrNull(15_000) {
                            runCatching {
                                if (!manifestFor(o.manifestUrl).canStream(type, id)) return@runCatching null
                                val streams = Stremio.loadStreams(o.base, type, id)
                                streams.firstOrNull { StreamTwin.releaseKey(StreamTwin.fileName(it)) == pf.file }
                                    ?.let { Ready(id, o, streams, it, false, System.currentTimeMillis(), moved = true) }
                            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
            hits.firstOrNull { it != null }?.let { return it }
        }
        return best ?: Ready(id, a, emptyList(), null, true, System.currentTimeMillis())
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
