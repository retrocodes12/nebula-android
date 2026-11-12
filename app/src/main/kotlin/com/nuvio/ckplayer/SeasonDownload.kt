package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A whole season to download (his 11-04 ask; the per-episode arrow stays). Not one archive: every episode is its own
 * download — playable the moment it is done, removable on its own, resumed on its own after a failure, and no double
 * space for packing/unpacking (video does not compress further anyway). The viewer picks a source ONCE, for the first
 * episode; every other aired episode not already saved gets the same release from the same add-on, found the way
 * "next episode keeps your source" finds it (StreamTwin: file name / binge group / quality / provider). Each one goes
 * through [Downloads.add], so its room check, Wi-Fi rule and private cache are the single-episode ones.
 */
internal object SeasonDownload {
    /** The episodes waiting for the viewer's pick on the Streams page (the first one is the page's own). */
    data class Plan(val firstId: String, val season: Int, val rest: List<MetaItem>)

    var pending: Plan? = null

    /** "Season 2 · 4 of 10" while it looks the episodes up; null when idle. */
    var progress by mutableStateOf<String?>(null)
        private set

    /** Aired episodes of [season] not already saved or saving, in order. */
    fun candidates(eps: List<Episode>, season: Int, today: String): List<Episode> =
        eps.filter { it.season == season && aired(it, today) && Downloads.find("series", it.id) == null }

    fun aired(ep: Episode, today: String): Boolean {
        val r = ep.released?.take(10) ?: return true          // no date: assume it is out
        return r.length < 10 || r <= today
    }

    /** After the first episode's download started with [picked] from [from]: the rest of the season, one by one. Returns
        what to tell the viewer. */
    suspend fun queueRest(ctx: Context, picked: StreamItem, from: Addon, plan: Plan): String {
        val sig = StreamTwin.sig(picked, from)
        var queued = 1
        val missing = ArrayList<String>()
        val total = plan.rest.size + 1
        try {
            plan.rest.forEachIndexed { i, item ->
                progress = "Season ${plan.season} · ${i + 2} of $total"
                if (Downloads.find("series", item.id) != null) { queued++; return@forEachIndexed }
                val streams = runCatching { Stremio.loadStreams(from.base, "series", item.id) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    .getOrDefault(emptyList()).filter { Downloads.savable(it) }
                val pick = pickTwin(streams, sig, from)
                if (pick == null) { missing.add(item.name); return@forEachIndexed }
                val why = Downloads.add(ctx, pick, from, item, StreamTwin.label(StreamTwin.sig(pick, from)).ifEmpty { null })
                if (why == null) queued++ else { missing.add(item.name); if (why.contains("room", true) || why.contains("space", true)) return "$queued of $total downloading · $why" }
            }
        } finally { progress = null }
        return if (missing.isEmpty()) "Season ${plan.season}: all $queued episodes downloading · Library › Downloads"
        else "$queued of $total downloading · no matching source for ${missing.size} (${missing.take(2).joinToString(", ")}${if (missing.size > 2) "…" else ""}). Use their own download arrow"
    }

    /** The same release for this episode: a twin of the picked row, else a row of the same quality from the same add-on. */
    fun pickTwin(streams: List<StreamItem>, sig: StreamSig, from: Addon): StreamItem? {
        if (streams.isEmpty()) return null
        val m = StreamTwin.match(streams, sig, from) ?: return null
        if (StreamTwin.isTwin(m, sig, from)) return m
        return if (sig.res.isNotEmpty() && StreamTwin.sig(m, from).res == sig.res) m else null
    }
}
