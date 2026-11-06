package com.nuvio.ckplayer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The watch party's rules, the same numbers as the shared player (brief party-2026-11-06.md): where the party should
 * be now, how to glide there, who said what, how far behind someone is. Pure — PlayerScreen does the playing.
 */
object PartySync {
    const val SEEK = 1.5            // s off → jump
    const val NUDGE_ON = 0.3        // s off → nudge the speed…
    const val NUDGE_OFF = 0.1       // …until this close
    const val NUDGE = 0.05          // ±5 %
    const val PAUSED_TOL = 0.5      // s off while paused → jump
    const val REJOIN = 2.0          // a viewer this close to the party is with it again
    const val WAIT_MAX = 15_000L    // the party waits this long for someone buffering
    const val RESUME_GRACE = 3_000L // …but not right after it started again
    const val HEARTBEAT = 4_000L    // the host's word this often

    /** The shortest round trip wins; a stale best gives way after 5 minutes. */
    class Clock {
        @Volatile var off: Long = 0
            private set
        private var rtt: Long = -1
        private var at: Long = 0
        fun sample(sentAt: Long, server: Long, now: Long) {
            if (sentAt <= 0 || server <= 0) return
            val r = max(0L, now - sentAt)
            if (rtt < 0 || r <= rtt + 30 || now - at > 300_000) {
                if (rtt < 0 || r <= rtt || now - at > 300_000) { rtt = r; at = now }
                off = server + r / 2 - now
            }
        }
    }

    fun keyOf(type: String?, id: String?, url: String): String =
        if (!type.isNullOrEmpty() && !id.isNullOrEmpty()) "$type:$id" else url

    /** A show's own id from an episode's ("tt1:2:3" → "tt1"; "kitsu:1:5" → "kitsu:1"). */
    fun seriesIdOf(id: String): String {
        val p = id.split(':')
        return if (p.size >= 2 && !p[0].startsWith("tt") && p[0].all { it.isLetter() }) p.take(2).joinToString(":") else p[0]
    }
    fun sameShow(aType: String?, aId: String?, bType: String?, bId: String?): Boolean =
        !aId.isNullOrEmpty() && !bId.isNullOrEmpty() && aType == bType && (aId == bId || seriesIdOf(aId) == seriesIdOf(bId))

    /** Where the party is at [nowMs] (VOD seconds). */
    fun expect(s: PartyState, nowMs: Long): Double =
        if (s.playing) s.pos + max(0L, nowMs - s.at) / 1000.0 * s.rate else s.pos

    sealed interface Move {
        data class Hold(val rate: Double) : Move          // in sync: play at the party's speed
        data class Nudge(val rate: Double) : Move
        data class Seek(val byS: Double, val rate: Double) : Move
    }
    /** [err] = seconds this device is behind the party (negative = ahead). */
    fun move(err: Double, rate: Double, nudging: Boolean): Move {
        val a = abs(err)
        return when {
            a > SEEK -> Move.Seek(err, rate)
            a > NUDGE_ON || (nudging && a > NUDGE_OFF) -> Move.Nudge(rate * (if (err > 0) 1 + NUDGE else 1 - NUDGE))
            else -> Move.Hold(rate)
        }
    }
    fun isNudge(r: Double, partyRate: Double): Boolean =
        abs(r - partyRate * (1 + NUDGE)) < 0.001 || abs(r - partyRate * (1 - NUDGE)) < 0.001

    /** "Ana paused" — from someone else's state, compared with the one before it. */
    fun notice(prev: PartyState?, s: PartyState): String? {
        if (prev == null || prev.waiting || s.waiting || s.name.isEmpty() || prev.live != s.live) return null
        return when {
            prev.playing != s.playing -> s.name + if (s.playing) " resumed" else " paused"
            abs(prev.rate - s.rate) > 0.01 -> s.name + " changed the speed to " + fmtRate(s.rate) + "×"
            !s.live && abs(s.pos - expect(prev, s.at)) > 2 -> s.name + " skipped to " + fmtTime(s.pos)
            else -> null
        }
    }
    fun fmtRate(r: Double): String = if (r == r.toInt().toDouble()) r.toInt().toString() else String.format(java.util.Locale.US, "%s", r).trimEnd('0').trimEnd('.')
    fun fmtTime(sec: Double): String {
        val t = max(0, sec.toInt())
        val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) else String.format(java.util.Locale.US, "%d:%02d", m, s)
    }
    /** "40 s", "3 min", "1 h 5 min". */
    fun gapLabel(sec: Double): String {
        val s = max(0, sec.roundToInt())
        if (s < 60) return "$s s"
        val m = (s / 60.0).roundToInt()
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }
    /** What a status reports as behind: whole 5 s steps, at least 5. */
    fun behindReported(gap: Double): Int = max(5, (max(0.0, gap) / 5).roundToInt() * 5)

    fun statusLabel(m: PartyMember): String = when (m.s) {
        "ready" -> "Watching"; "buffering" -> "Buffering"; "loading" -> "Loading"
        "choosing" -> "Choosing a source"; "away" -> "Watching something else"
        "behind" -> gapLabel((m.behind ?: 0).toDouble()) + " behind"
        else -> ""
    }

    /** The members the party waits for: on its title, still loading or buffering (not me, not ignored). */
    fun waiters(members: List<PartyMember>, me: String, key: String, ignore: Set<String>): List<PartyMember> =
        members.filter { it.id != me && it.k == key && (it.s == "loading" || it.s == "buffering") && it.id !in ignore }

    /** The same file in another add-on's answer: the torrent, the release, then the file name. */
    fun matchOwn(rows: List<StreamItem>, file: String?, hash: String?): StreamItem? {
        val h = hash?.lowercase()?.takeIf { it.length == 40 }
        if (h != null) rows.firstOrNull { it.infoHash.lowercase() == h }?.let { return it }
        val f = file?.takeIf { it.isNotBlank() } ?: return null
        val key = StreamTwin.releaseKey(f)
        if (key.isNotEmpty()) rows.firstOrNull { StreamTwin.fileName(it).let { n -> n.isNotEmpty() && StreamTwin.releaseKey(n) == key } }?.let { return it }
        val bare = f.substringAfterLast('/').lowercase()
        return rows.firstOrNull { StreamTwin.fileName(it).substringAfterLast('/').lowercase() == bare }
    }
}
