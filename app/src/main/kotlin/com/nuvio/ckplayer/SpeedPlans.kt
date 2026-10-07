package com.nuvio.ckplayer

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.roundToLong

/**
 * What a stream speed test fetches (SpeedTest.kt; branch `speedtest`): an HLS master playlist's variants, a media
 * playlist's init and segments, a DASH manifest's best video Representation and its addresses. Pure, so unit-tested
 * (SpeedTestTest).
 */
internal object SpeedPlans {

    /** What an address's first bytes say it is when its Content-Type does not (octet-stream, text/plain — some add-ons
        proxy their playlists under no extension at all): "hls" for "#EXTM3U" after an optional byte-order mark or white
        space, "dash" for a manifest ("<MPD" in its head), null for anything else — a file to read. */
    fun sniffKind(head: String): String? {
        val t = head.trimStart('﻿', ' ', '\t', '\r', '\n')
        return when {
            t.startsWith("#EXTM3U") -> "hls"
            head.contains("<MPD") -> "dash"
            else -> null
        }
    }

    // ---------- HLS ----------

    data class Variant(val bandwidth: Long, val url: String)

    /** A master playlist's variants (BANDWIDTH and the address on the line after), resolved; empty for a media playlist. */
    fun hlsVariants(text: String, base: String): List<Variant> {
        val out = ArrayList<Variant>()
        var pending: Long? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-STREAM-INF:")) { pending = attr(line.substringAfter(':'), "BANDWIDTH")?.toLongOrNull() ?: 0L; continue }
            if (line.startsWith("#")) continue
            val bw = pending ?: continue
            resolve(base, line)?.let { out.add(Variant(bw, it)) }
            pending = null
        }
        return out
    }

    /** One segment to fetch; [len] ≥ 0 when it is a byte range of its address. */
    data class Seg(val url: String, val from: Long = 0L, val len: Long = -1L)
    data class HlsMedia(val init: Seg?, val segs: List<Seg>, val live: Boolean)

    /** A media playlist's init (EXT-X-MAP) and segments in order (with their byte ranges); live without EXT-X-ENDLIST. */
    fun hlsMedia(text: String, base: String): HlsMedia {
        var init: Seg? = null
        val segs = ArrayList<Seg>()
        var live = true
        var nextLen = -1L
        var nextFrom = -1L
        var lastEnd = 0L
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXT-X-ENDLIST") -> live = false
                line.startsWith("#EXT-X-MAP:") -> {
                    val a = line.substringAfter(':')
                    val u = attr(a, "URI")?.let { resolve(base, it) }
                    if (u != null) {
                        val r = attr(a, "BYTERANGE")?.let { byteRange(it, 0L) }
                        init = if (r != null) Seg(u, r.second, r.first) else Seg(u)
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> byteRange(line.substringAfter(':'), lastEnd)?.let { (n, o) -> nextLen = n; nextFrom = o }
                line.startsWith("#") -> {}
                else -> {
                    val u = resolve(base, line)
                    if (u != null) {
                        if (nextLen >= 0) { segs.add(Seg(u, nextFrom, nextLen)); lastEnd = nextFrom + nextLen } else segs.add(Seg(u))
                    }
                    nextLen = -1L; nextFrom = -1L
                }
            }
        }
        return HlsMedia(init, segs, live)
    }

    /** "n@o" → (n, o); "n" → (n, [after]) — the byte range starts where the previous one ended. */
    private fun byteRange(s: String, after: Long): Pair<Long, Long>? {
        val p = s.trim().split('@')
        val n = p[0].trim().toLongOrNull() ?: return null
        val o = if (p.size > 1) p[1].trim().toLongOrNull() ?: return null else after
        return n to o
    }

    /** One attribute of an HLS attribute list (`BANDWIDTH=…,CODECS="avc1,mp4a"`): a quoted value may hold commas. */
    internal fun attr(list: String, name: String): String? {
        var i = 0
        while (i < list.length) {
            val eq = list.indexOf('=', i)
            if (eq < 0) return null
            val key = list.substring(i, eq).trim()
            var j = eq + 1
            val value: String
            if (j < list.length && list[j] == '"') {
                val end = list.indexOf('"', j + 1).let { if (it < 0) list.length else it }
                value = list.substring(j + 1, end)
                j = end + 1
            } else {
                val end = list.indexOf(',', j).let { if (it < 0) list.length else it }
                value = list.substring(j, end)
                j = end
            }
            if (key == name) return value
            val comma = list.indexOf(',', j)
            if (comma < 0) return null
            i = comma + 1
        }
        return null
    }

    /** [ref] against [base], as a player resolves a playlist's or a manifest's addresses; null when it is not a web address. */
    fun resolve(base: String, ref: String): String? = base.toHttpUrlOrNull()?.resolve(ref.trim())?.toString()

    // ---------- DASH ----------

    /** The best video Representation's [need] (its bandwidth) and what to fetch: [init] and [media] (a SegmentTemplate),
        or [single], one file read from its start (SegmentBase, or a lone BaseURL). */
    data class DashPlan(val need: Long, val init: String?, val media: List<String>, val single: String?)

    private class Tpl(val a: Map<String, String>, val timeline: List<LongArray>?)
    private class Rep(val id: String, val bandwidth: Long, val video: Boolean, val base: String, val tpl: Tpl?, val list: Boolean)

    /**
     * A manifest's plan: the highest-bandwidth video Representation (in the first Period, the last on a live one), its
     * SegmentTemplate (`$Number$`, `$Time$`, `$RepresentationID$`, `$Bandwidth$`, `%0Nd` widths; startNumber,
     * duration/timescale or a SegmentTimeline) → its init and first [count] media addresses, resolved through the BaseURL
     * chain; a live one's newest segments instead. No template but a BaseURL → that file. Anything else (a SegmentList,
     * nothing to fetch) → null: "Can't test this kind of stream". [parser] is the platform's (android.util.Xml) on a
     * device, kXML in the unit tests.
     */
    fun dashPlan(xml: String, mpdUrl: String, parser: XmlPullParser, nowMs: Long = System.currentTimeMillis(), count: Int = 6): DashPlan? {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        var dynamic = false
        var astMs = -1L
        var mpdBase = mpdUrl
        var periodBase = mpdUrl
        var setBase = mpdUrl
        var repBase = mpdUrl
        var periodStartMs = 0L
        var periodTpl: Tpl? = null
        var setTpl: Tpl? = null
        var repTpl: Tpl? = null
        var setVideo = false
        var setAudioOrText = false
        var setList = false
        var repId = ""; var repBw = 0L; var repVideo = false; var repList = false
        val baseSeen = HashSet<Int>()           // the depths whose first BaseURL was taken (more are alternatives)
        var tplAttrs: Map<String, String>? = null
        var tplLevel = ""
        var timeline: ArrayList<LongArray>? = null
        val periods = ArrayList<List<Rep>>()
        var reps = ArrayList<Rep>()
        val stack = ArrayList<String>()
        fun a(n: String): String? = parser.getAttributeValue(null, n)
        fun merge(parent: Tpl?, own: Tpl): Tpl = Tpl((parent?.a ?: emptyMap()) + own.a, own.timeline ?: parent?.timeline)
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val name = parser.name.substringAfter(':')
                val parent = stack.lastOrNull()
                stack.add(name)
                when (name) {
                    "MPD" -> {
                        dynamic = a("type") == "dynamic"
                        astMs = a("availabilityStartTime")?.let { isoMs(it) } ?: -1L
                    }
                    "Period" -> { periodBase = mpdBase; periodTpl = null; periodStartMs = a("start")?.let { durationMs(it) } ?: 0L; reps = ArrayList() }
                    "AdaptationSet" -> {
                        setBase = periodBase; setTpl = periodTpl; setList = false
                        val mime = a("mimeType").orEmpty(); val ct = a("contentType").orEmpty()
                        setVideo = mime.startsWith("video/") || ct == "video"
                        setAudioOrText = mime.startsWith("audio/") || mime.startsWith("text/") || mime.startsWith("application/") ||
                            ct == "audio" || ct == "text"
                    }
                    "Representation" -> {
                        repBase = setBase; repTpl = setTpl; repList = setList
                        repId = a("id").orEmpty(); repBw = a("bandwidth")?.toLongOrNull() ?: 0L
                        val mime = a("mimeType").orEmpty()
                        repVideo = when {
                            mime.startsWith("video/") -> true
                            mime.isNotEmpty() -> false
                            else -> setVideo || (!setAudioOrText && (a("width") != null || a("height") != null))
                        }
                    }
                    "BaseURL" -> {
                        val depth = stack.size - 1
                        val text = parser.nextText().trim()
                        stack.removeAt(stack.size - 1)      // nextText() stands on the end tag: the loop never sees it
                        if (text.isNotEmpty() && baseSeen.add(depth)) when (parent) {
                            "MPD" -> mpdBase = resolve(mpdBase, text) ?: mpdBase
                            "Period" -> periodBase = resolve(periodBase, text) ?: periodBase
                            "AdaptationSet" -> setBase = resolve(setBase, text) ?: setBase
                            "Representation" -> repBase = resolve(repBase, text) ?: repBase
                        }
                    }
                    "SegmentTemplate" -> {
                        tplLevel = parent.orEmpty(); timeline = null
                        tplAttrs = (0 until parser.attributeCount).associate { parser.getAttributeName(it).substringAfter(':') to parser.getAttributeValue(it) }
                    }
                    "SegmentTimeline" -> if (tplAttrs != null) timeline = ArrayList()
                    "S" -> timeline?.add(longArrayOf(
                        a("t")?.toLongOrNull() ?: -1L, a("d")?.toLongOrNull() ?: 0L, a("r")?.toLongOrNull() ?: 0L,
                    ))
                    "SegmentList" -> when (parent) { "Representation" -> repList = true; else -> setList = true }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                val name = stack.removeAt(stack.size - 1)
                baseSeen.removeAll { it >= stack.size + 1 }
                when (name) {
                    "SegmentTemplate" -> {
                        val own = Tpl(tplAttrs ?: emptyMap(), timeline)
                        when (tplLevel) {
                            "Period" -> periodTpl = merge(null, own)
                            "AdaptationSet" -> setTpl = merge(periodTpl, own)
                            "Representation" -> repTpl = merge(repTpl, own)
                        }
                        tplAttrs = null; timeline = null
                    }
                    "Representation" -> reps.add(Rep(repId, repBw, repVideo, repBase, repTpl, repList))
                    "Period" -> periods.add(reps)
                }
            }
            ev = parser.next()
        }
        val pool = (if (dynamic) periods.lastOrNull() else periods.firstOrNull()) ?: reps
        val best = pool.filter { it.video }.maxByOrNull { it.bandwidth } ?: return null
        val t = best.tpl
        val media = t?.a?.get("media")
        if (t != null && media != null) {
            val scale = t.a["timescale"]?.toLongOrNull()?.takeIf { it > 0 } ?: 1L
            val start = t.a["startNumber"]?.toLongOrNull() ?: 1L
            val dur = t.a["duration"]?.toLongOrNull()?.takeIf { it > 0 }
            val nums: List<Pair<Long, Long>> = when {
                t.timeline != null -> {
                    val all = timelineSegments(t.timeline, start, if (dynamic) 100_000 else count)
                    if (dynamic) all.takeLast(count) else all
                }
                dur != null && !dynamic -> (0 until count).map { k -> (start + k) to k * dur }
                dur != null && astMs >= 0 -> {
                    // live, numbered by time since availabilityStartTime: the newest few, two short of the edge
                    val segMs = dur * 1000.0 / scale
                    val edge = start + ((nowMs - astMs - periodStartMs) / segMs).toLong()
                    ((edge - count - 1)..(edge - 2)).filter { it >= start }.map { n -> n to (n - start) * dur }
                }
                else -> return null
            }
            if (nums.isEmpty()) return null
            val init = t.a["initialization"]?.let { resolve(best.base, fill(it, best.id, best.bandwidth, null, null)) }
            val urls = nums.mapNotNull { (n, time) -> resolve(best.base, fill(media, best.id, best.bandwidth, n, time)) }
            return DashPlan(best.bandwidth, init, urls, null)
        }
        if (best.list) return null
        return if (best.base != mpdUrl) DashPlan(best.bandwidth, null, emptyList(), best.base) else null
    }

    /** A SegmentTimeline's (number, time) pairs from its S rows (t, d, r; r = −1 repeats to the end — taken once here). */
    internal fun timelineSegments(rows: List<LongArray>, startNumber: Long, cap: Int): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        var t = 0L
        var n = startNumber
        for (row in rows) {
            if (row[0] >= 0) t = row[0]
            val reps = if (row[2] < 0) 1L else row[2] + 1
            var k = 0L
            while (k < reps && out.size < cap) { out.add(n to t); n++; t += row[1]; k++ }
            if (out.size >= cap) break
        }
        return out
    }

    private val TEMPLATE = Regex("""\$(RepresentationID|Number|Bandwidth|Time)(?:%0(\d+)[dxX])?\$|\$\$""")

    /** A SegmentTemplate address with its identifiers filled in (`$$` is a dollar sign). */
    internal fun fill(tpl: String, id: String, bandwidth: Long, number: Long?, time: Long?): String =
        TEMPLATE.replace(tpl) { m ->
            if (m.value == "$$") return@replace "$"
            val width = m.groupValues[2].toIntOrNull() ?: 0
            val v = when (m.groupValues[1]) {
                "RepresentationID" -> return@replace id
                "Bandwidth" -> bandwidth
                "Number" -> number ?: return@replace m.value
                else -> time ?: return@replace m.value
            }
            v.toString().padStart(width, '0')
        }

    /** xs:duration ("PT1H2M3.5S", "P0Y0M0DT0H0M0S", "P1D") → ms; 0 when it does not read. */
    internal fun durationMs(s: String): Long {
        val m = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$""").matchEntire(s.trim()) ?: return 0L
        fun g(i: Int) = m.groupValues[i].toDoubleOrNull() ?: 0.0
        val days = g(1) * 365 + g(2) * 30 + g(3)
        return ((days * 86_400 + g(4) * 3600 + g(5) * 60 + g(6)) * 1000).roundToLong()
    }

    /** availabilityStartTime → epoch ms ("2026-10-07T01:00:00Z", an offset, or no zone at all, which is UTC); null when it does not read. */
    internal fun isoMs(s: String): Long? = runCatching { java.time.OffsetDateTime.parse(s.trim()).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(s.trim()).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}
