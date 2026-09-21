package com.nuvio.ckplayer

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The stall watchdog's memory and its search for a sibling — mirrors the shared player's stall / stallPick.
 * Three stalls inside ninety seconds (never a play's first buffering, nor the one a jump causes) and the row
 * after the playing one in the list — a different source; one this connection carries before one it cannot —
 * is offered on a pill. One press switches with the second kept (AppRoot.swapSource).
 */
internal class StallWatch {
    private val times = ArrayDeque<Long>()
    var total = 0; private set
    var seekAt = 0L
    var playedAt = 0L
    var offered = false
    var busy = false

    fun reset() { times.clear(); total = 0; seekAt = 0L; playedAt = 0L; offered = false; busy = false }

    /** A buffering state after playback began: true when this one makes three inside the window and nothing was offered yet. */
    fun note(now: Long = System.currentTimeMillis()): Boolean {
        if (playedAt == 0L) return false                 // nothing has played yet: that is the start, not a stall
        if (now - seekAt < 2500) return false            // a jump buffers by design
        val last = times.lastOrNull() ?: 0L
        if (now - last < 3000) return false              // one stall, not one per hiccup inside it
        times.addLast(now); total++
        while (times.isNotEmpty() && now - times.first() > 90_000) times.removeFirst()
        return times.size >= 3 && !offered && !busy
    }

    companion object {
        /** The list the streams page would build for (type, id): the origin add-on and every enabled one that serves it, arranged the same way. */
        suspend fun siblings(ctx: Context, type: String, id: String, originUrl: String?, runtime: String?): List<Pair<StreamItem, Addon>> {
            val all = activeAddons(ctx)
            val origin = all.firstOrNull { it.manifestUrl == originUrl }
            val order = listOfNotNull(origin) + all.filter { origin == null || it.manifestUrl != origin.manifestUrl }
            // every add-on at once, as the streams page asks them; the list keeps the page's order
            return coroutineScope {
                order.map { a ->
                    async<List<Pair<StreamItem, Addon>>> {
                        try {
                            if ((origin == null || a.manifestUrl != origin.manifestUrl) && !manifestFor(a.manifestUrl).canStream(type, id)) emptyList()
                            else arrangeStreams(Stremio.loadStreams(a.base, type, id), runtime).map { it to a }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        }

        /** The next row after the playing one, round the list: not its twin, and one this connection carries before one it cannot. */
        fun pick(cands: List<Pair<StreamItem, Addon>>, curUrl: String, runtime: String?): Pair<StreamItem, Addon>? {
            if (cands.isEmpty()) return null
            val at = cands.indexOfFirst { it.first.url == curUrl }
            val start = if (at >= 0) at + 1 else 0
            val curSig = if (at >= 0) StreamTwin.sig(cands[at].first, cands[at].second) else null
            for (pass in 0..1) {
                for (k in cands.indices) {
                    val (s, a) = cands[(start + k) % cands.size]
                    if (s.url == curUrl) continue
                    if (curSig != null && StreamTwin.isTwin(s, curSig, a)) continue
                    if (pass == 0 && StreamBadges.slow(s, runtime)) continue
                    return s to a
                }
            }
            return null
        }

        /** "Keeps stalling · Try Nebula HD": the row's own name, short enough for a pill. */
        fun name(s: StreamItem, from: Addon): String {
            val n = StreamBadges.cleanName(s.name, from.name).ifEmpty { from.name }
            return if (n.length > 30) n.take(29) + "…" else n
        }
    }
}
