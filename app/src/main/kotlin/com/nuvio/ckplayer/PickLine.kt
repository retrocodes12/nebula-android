package com.nuvio.ckplayer

import java.util.Locale
import kotlin.math.abs

/**
 * "Pick the line you just heard" (2026-10-03, the shared player's `lp*` block ported): right after a line is said the
 * viewer opens a list of the showing add-on file's lines around that moment (the anchor) and taps the one just said;
 * the timing moves so that line ENDS at the anchor — a line is recognised once it has been said, and a subtitle lingers
 * about as long as it takes to reach for the remote. The nudges fine-tune it. Replaced the automatic sync, which
 * listened for minutes, missed on files cut from another broadcast and never ran on a TV.
 *
 * Times are in ms. The timing convention is the overlay's and the nudges': shown = file time + offset (+ = later).
 * Everything here is plain Kotlin so PickLineTest runs it on the JVM.
 */

/** One line of the showing file, in the file's own times. */
internal class PickLine(val startMs: Long, val endMs: Long, val text: String)

/** One timed piece of a parsed file: its span and the text of each cue in it (PlayerScreen maps a SubCue to this). */
internal class PickSeg(val startMs: Long, val endMs: Long, val texts: List<String>)

/**
 * The list while it is up: the anchor, whether opening it paused the video, the lines, [here] (the last line whose
 * shown start is at or before the anchor), the window [from, to) on screen, the row the remote lands on, and what the
 * lines came from ([source] = the overlay's cue list, [url] = the address) so a change of either ends it.
 */
internal data class PickLineState(
    val anchorMs: Long,
    val paused: Boolean,
    val lines: List<PickLine>,
    val here: Int,
    val from: Int,
    val to: Int,
    val focus: Int,
    val source: Any?,
    val url: String,
)

internal object PickLines {
    /** Lines each side of the anchor, and each "Earlier lines" / "Later lines" step. */
    const val SPAN = 30

    private val TAG = Regex("<[^>]*>")
    private val BRACE = Regex("\\{[^}]*\\}")
    private val BREAK = Regex("\\\\[Nn]")
    private val SPACE = Regex("[\\s\\u00A0]+")

    /** A line's words: markup tags, {…} override blocks and \N breaks gone, whitespace collapsed. */
    fun clean(s: String?): String =
        SPACE.replace(BREAK.replace(BRACE.replace(TAG.replace(s ?: "", ""), ""), " "), " ").trim()

    /**
     * The file's lines, in start order. A WebVTT file's overlapping cues come out of the parser as pieces of time that
     * each carry every cue on screen then; a cue that runs on into the next piece with the same words is one line again.
     */
    fun lines(segs: List<PickSeg>): List<PickLine> {
        class Open(val start: Long, var end: Long, val text: String)
        val out = ArrayList<Open>()
        val last = HashMap<String, Open>()
        for (seg in segs.sortedBy { it.startMs }) {
            if (seg.endMs <= seg.startMs) continue
            val seen = HashSet<String>()
            for (raw in seg.texts) {
                val t = clean(raw)
                if (t.isEmpty() || !seen.add(t)) continue
                val prev = last[t]
                if (prev != null && prev.end >= seg.startMs - 1) {
                    if (seg.endMs > prev.end) prev.end = seg.endMs
                } else {
                    val o = Open(seg.startMs, seg.endMs, t)
                    out += o; last[t] = o
                }
            }
        }
        return out.filter { it.end > it.start }.sortedBy { it.start }.map { PickLine(it.start, it.end, it.text) }
    }

    /** The last line whose SHOWN start is at or before the anchor; 0 when none is. */
    fun here(lines: List<PickLine>, anchorMs: Long, offsetMs: Long): Int {
        var i = 0
        while (i < lines.size && lines[i].startMs + offsetMs <= anchorMs) i++
        return if (i > 0) i - 1 else 0
    }

    /** The first window: [here − SPAN, here + SPAN], as from (inclusive) and to (exclusive). */
    fun window(here: Int, size: Int): Pair<Int, Int> = maxOf(0, here - SPAN) to minOf(size, here + SPAN + 1)

    /** "Earlier lines": SPAN more above. */
    fun earlier(from: Int): Int = maxOf(0, from - SPAN)

    /** "Later lines": SPAN more below. */
    fun later(to: Int, size: Int): Int = minOf(size, to + SPAN)

    /** A line's badge against the anchor, in the timing as it is: "On screen", "12 s ago", "in 4 s", "1:05 ago". */
    fun badge(line: PickLine, anchorMs: Long, offsetMs: Long): String {
        val start = line.startMs + offsetMs
        val end = line.endMs + offsetMs
        if (anchorMs in start..end) return "On screen"
        val d = (start - anchorMs) / 1000.0
        val n = abs(d)
        var s = if (n < 60) "${Math.round(n)} s" else {
            val t = Math.round(n)
            String.format(Locale.US, "%d:%02d", t / 60, t % 60)
        }
        if (s == "0 s") s = "1 s"
        return if (d < 0) "$s ago" else "in $s"
    }

    /** The timing that makes [line] end at the anchor, to the nearest 0.1 s (under 0.05 s is none). */
    fun offsetFor(anchorMs: Long, line: PickLine): Long = round100(anchorMs - line.endMs)

    /** A timing to the nearest 0.1 s, as the shared player's setSubOffset rounds it. */
    fun round100(ms: Long): Long {
        val r = Math.round(ms / 100.0) * 100
        return if (abs(r) < 50) 0L else r
    }

    /** The toast after a pick, from how far the timing moved. */
    fun movedText(deltaMs: Long): String =
        if (abs(deltaMs) < 50) "The subtitles were already in time."
        else String.format(Locale.US, "Subtitles moved %.1f s %s.", abs(deltaMs) / 1000.0, if (deltaMs > 0) "later" else "earlier")

    /** The note over the list. */
    fun note(paused: Boolean): String =
        (if (paused) "Paused here. " else "") + "Pick the line that was just said — the subtitles move to match it."
}
