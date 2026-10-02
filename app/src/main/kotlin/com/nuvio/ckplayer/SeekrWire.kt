package com.nuvio.ckplayer

import org.json.JSONObject

/** One thumbnail of a Seekr VTT: [startMs, endMs) in the SOURCE timebase, and the region of a sprite sheet. */
internal class SeekrCue(val startMs: Long, val endMs: Long, val sheet: String, val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * The plain-Kotlin half of Seekr previews (no Android here, so the JVM tests reach all of it): which lookup a content
 * id asks for, the VTT parser, the cue a position shows, the key's shape and the synced doc's newest-wins rule.
 * The shared web/TV player follows the same rules; the wire shapes (`seekr` doc {key, at}) are shared with it.
 */
internal object SeekrWire {
    /** The only key shape Seekr issues. */
    val KEY_RE = Regex("sk_live_[0-9a-f]{64}")
    private val IMDB = Regex("tt\\d{5,10}")
    private val IMDB_EP = Regex("(tt\\d{5,10}):(\\d{1,4}):(\\d{1,5})")
    private val TMDB = Regex("tmdb:([1-9]\\d{0,9})")
    private val TMDB_EP = Regex("tmdb:([1-9]\\d{0,9}):(\\d{1,4}):(\\d{1,5})")
    private val TIME = Regex("(?:(\\d{1,3}):)?([0-5]?\\d):([0-5]?\\d)(?:[.,](\\d{1,3}))?")
    private val XYWH = Regex("xywh=(\\d{1,6}),(\\d{1,6}),(\\d{1,5}),(\\d{1,5})")
    const val MAX_CUES = 20_000

    fun validKey(k: String) = KEY_RE.matches(k)

    /**
     * The id parameters of a sprite lookup, or null when this title gets none: `tt…` film → imdb_id, `tt…:S:E` episode
     * → show_imdb_id + season + episode, `tmdb:N` film → tmdb_id, `tmdb:N:S:E` → show_tmdb_id + season + episode.
     * Anything else (kitsu, a channel, an add-on's own id, no id) and any type but film/series asks nothing.
     */
    fun query(type: String?, id: String?): List<Pair<String, String>>? {
        val v = id?.trim().orEmpty()
        if (v.isEmpty()) return null
        val t = type?.trim()?.lowercase()
        if (t != null && t != "movie" && t != "series") return null
        if (t != "series") {
            if (IMDB.matches(v)) return listOf("imdb_id" to v)
            TMDB.matchEntire(v)?.let { return listOf("tmdb_id" to it.groupValues[1]) }
        }
        if (t != "movie") {
            IMDB_EP.matchEntire(v)?.let { m ->
                return listOf("show_imdb_id" to m.groupValues[1], "season" to num(m.groupValues[2]), "episode" to num(m.groupValues[3]))
            }
            TMDB_EP.matchEntire(v)?.let { m ->
                return listOf("show_tmdb_id" to m.groupValues[1], "season" to num(m.groupValues[2]), "episode" to num(m.groupValues[3]))
            }
        }
        return null
    }

    private fun num(s: String) = s.toInt().toString()        // "01" → "1"

    /** "01:02:03.450" / "02:03.450" / "02:03" → ms; null for anything else. */
    fun parseTime(s: String): Long? {
        val m = TIME.matchEntire(s.trim()) ?: return null
        val h = m.groupValues[1].ifEmpty { "0" }.toLong()
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val frac = m.groupValues[4].padEnd(3, '0').ifEmpty { "000" }.toLong()
        return ((h * 60 + min) * 60 + sec) * 1000 + frac
    }

    /**
     * Seekr's WEBVTT: a header, then per cue a timing line `START --> END` followed by exactly one line, the absolute
     * https tile URL ending `#xywh=x,y,w,h`. No cue ids are sent, but a line without `-->` is skipped anyway; a cue
     * whose payload is missing, not https or has no usable region is dropped. Sorted by start.
     */
    fun parseVtt(text: String): List<SeekrCue> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.isEmpty() || !lines[0].trimStart('﻿').trimStart().startsWith("WEBVTT")) return emptyList()
        val out = ArrayList<SeekrCue>()
        var i = 1
        while (i < lines.size && out.size < MAX_CUES) {
            val l = lines[i].trim()
            val arrow = l.indexOf("-->")
            if (arrow < 0) { i++; continue }
            val a = parseTime(l.substring(0, arrow))
            val b = parseTime(l.substring(arrow + 3).trim().split(' ', '\t').first())
            val payload = lines.getOrNull(i + 1)?.trim().orEmpty()
            // a cue with no payload line must not swallow the next cue's timing line
            i += if (payload.contains("-->")) 1 else 2
            if (a == null || b == null || b < a) continue
            cueOf(a, b, payload)?.let { out.add(it) }
        }
        out.sortBy { it.startMs }
        return out
    }

    private fun cueOf(a: Long, b: Long, payload: String): SeekrCue? {
        val hash = payload.lastIndexOf('#')
        if (hash <= 0) return null
        val url = payload.substring(0, hash)
        if (!url.startsWith("https://", ignoreCase = true) || url.any { it.isWhitespace() }) return null
        val m = XYWH.matchEntire(payload.substring(hash + 1)) ?: return null
        val (x, y, w, h) = m.destructured
        val wi = w.toInt(); val hi = h.toInt()
        if (wi <= 0 || hi <= 0) return null
        return SeekrCue(a, b, url, x.toInt(), y.toInt(), wi, hi)
    }

    /**
     * The cue to show for the PLAYER's position [posMs]: the source position is posMs / [scale] (Seekr's scale = our
     * length ÷ the source's), the cue is the last one starting at or before it, and past that cue's midpoint the next
     * one is preferred (the next picture is nearer). Before the first cue → the first. -1 when there are none.
     * No offset is ever derived from the two lengths — Seekr says that is wrong more often than right.
     */
    fun cueIndex(cues: List<SeekrCue>, posMs: Long, scale: Double): Int {
        if (cues.isEmpty()) return -1
        val s = if (scale.isFinite() && scale > 0) scale else 1.0
        val pos = posMs.coerceAtLeast(0L) / s
        var lo = 0; var hi = cues.size - 1; var at = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= pos) { at = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (at < 0) return 0
        val c = cues[at]
        return if (at + 1 < cues.size && pos > (c.startMs + c.endMs) / 2.0) at + 1 else at
    }

    /** The synced doc `seekr`: {"key": "sk_live_…" or "" (disconnected), "at": epoch ms}. */
    fun doc(key: String, at: Long): String = JSONObject().put("key", key).put("at", at).toString()

    /** A remote doc's (key, at) when it is well-formed (a valid key or "" and a stamp), else null. */
    fun read(remote: JSONObject): Pair<String, Long>? {
        val k = remote.opt("key") as? String ?: return null
        if (k.isNotEmpty() && !validKey(k)) return null
        val at = remote.optLong("at", 0L)
        if (at <= 0) return null
        return k to at
    }

    /** Newest `at` wins: (adopt the remote doc, push ours back). A malformed remote is overwritten by ours if we have one. */
    fun mergeDecision(localAt: Long, remote: JSONObject): Pair<Boolean, Boolean> {
        val r = read(remote) ?: return false to (localAt > 0)
        return if (r.second > localAt) true to false else false to (localAt > r.second)
    }
}
