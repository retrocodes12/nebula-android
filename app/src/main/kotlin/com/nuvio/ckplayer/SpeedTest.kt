package com.nuvio.ckplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * Speed tests (since 1.88.0): the connection itself (Settings › Playback ›
 * Connection speed, against Cloudflare's speed endpoint) and the host of one stream row (the Streams page's Test speeds,
 * and a row's hold sheet).
 *
 * A row is measured the way the player would fetch it: the player's identity (its User-Agent, X-Nebula-Client, the
 * shared cookie store — [MediaHttp]), `Accept-Encoding: identity`, HTTP/1.1 as the player's own HttpURLConnection
 * speaks, and never through Play through your PC (the relay is the player's, not this test's). The add-ons'
 * `behaviorHints.proxyHeaders` are not sent: StreamItem does not carry them and the player never sends them, so a host
 * that needs them answers this test the way it answers playback. A file is read from its start (a Range read) for about
 * five seconds; HLS is fetched through its highest-BANDWIDTH variant, init and first segments in order; DASH through its
 * highest-bandwidth video Representation (a SegmentTemplate's init and first segments, or one file read as a range).
 * Encrypted segments are bytes like any other. The playlists and manifests are read in SpeedPlans.kt; the pure parts of
 * both are unit-tested (SpeedTestTest).
 */
internal object SpeedTest {

    // ---------- the verdict ----------

    enum class Verdict { SMOOTH, SHOULD, STALL, UNKNOWN }

    /** Against what the stream needs: half as much again plays smoothly, a tenth more should play, less may stall
        (in whole numbers: 1.1 × in floating point is a hair above 1.1, and a row at exactly 1.1 × read "may stall"). */
    fun verdict(bps: Long, need: Long): Verdict = when {
        need <= 0L -> Verdict.UNKNOWN
        bps * 2 >= need * 3 -> Verdict.SMOOTH
        bps * 10 >= need * 11 -> Verdict.SHOULD
        else -> Verdict.STALL
    }

    /** "92 Mbps", "6.4 Mbps", "6 Mbps", "under 0.1 Mbps". */
    fun mbps(bps: Long): String {
        val m = bps / 1_000_000.0
        if (m >= 9.95) return "${m.roundToLong()} Mbps"
        if (m < 0.05) return "under 0.1 Mbps"
        val tenths = (m * 10).roundToLong()
        return if (tenths % 10 == 0L) "${tenths / 10} Mbps" else String.format(Locale.US, "%.1f Mbps", tenths / 10.0)
    }

    /** A tested row's line: "48 Mbps · plays smoothly", "14 Mbps · should play", "6 Mbps · may stall", or "31 Mbps". */
    fun line(bps: Long, need: Long): String = mbps(bps) + when (verdict(bps, need)) {
        Verdict.SMOOTH -> " · plays smoothly"
        Verdict.SHOULD -> " · should play"
        Verdict.STALL -> " · may stall"
        Verdict.UNKNOWN -> ""
    }

    // ---------- throughput over a window ----------

    /**
     * Bits per second from [fromMs] to the last sample. [samples] are (ms since the start, bytes so far) in time order;
     * the bytes at [fromMs] are read off the line between the samples around it (nothing had arrived at 0 ms). 0 when
     * the window is empty.
     */
    fun windowBps(samples: List<Pair<Long, Long>>, fromMs: Long): Long {
        if (samples.isEmpty()) return 0L
        val (tEnd, bEnd) = samples.last()
        if (tEnd <= fromMs) return 0L
        val bFrom = bytesAt(samples, fromMs)
        return ((bEnd - bFrom) * 8_000.0 / (tEnd - fromMs)).roundToLong().coerceAtLeast(0L)
    }

    internal fun bytesAt(samples: List<Pair<Long, Long>>, t: Long): Long {
        var prev = 0L to 0L
        for (s in samples) {
            if (s.first >= t) {
                if (s.first == prev.first) return s.second
                val f = (t - prev.first).toDouble() / (s.first - prev.first)
                return (prev.second + f * (s.second - prev.second)).roundToLong()
            }
            prev = s
        }
        return prev.second
    }

    /** The bytes a test has read and when, shared by the threads reading them: one sample per 25 ms, each the newest
        (time, bytes so far) of its 25 ms. */
    internal class Meter {
        private val t0 = System.nanoTime()
        private val list = ArrayList<Pair<Long, Long>>()
        private var total = 0L
        private var stampedAt = -1L
        /** When the first byte arrived (ms since the start), −1 until then. */
        @Volatile var firstMs = -1L; private set
        fun ms(): Long = (System.nanoTime() - t0) / 1_000_000
        @Synchronized fun add(n: Long) {
            val now = ms()
            if (firstMs < 0) firstMs = now
            total += n
            if (stampedAt < 0 || now - stampedAt >= 25) { list.add(now to total); stampedAt = now } else list[list.size - 1] = now to total
        }
        @Synchronized fun total(): Long = total
        @Synchronized fun bps(fromMs: Long): Long = windowBps(list.toList(), fromMs)
    }

    // ---------- the network ----------

    /** The answer for one row: what it carried, what it needs (0 = unknown), how long its first byte took. */
    data class Result(val bps: Long = 0L, val need: Long = 0L, val ttfbMs: Long = -1L, val failed: Boolean = false, val unsupported: Boolean = false)

    private class Unsupported : Exception()

    private const val WINDOW_NS = 5_000_000_000L
    private const val FILE_CAP = 64L * 1024 * 1024
    private const val TEXT_CAP = 4L * 1024 * 1024

    private val http: OkHttpClient by lazy {
        MediaHttp.client.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun request(ctx: Context, url: String, range: String? = null): Request = Request.Builder().url(url)
        .header("User-Agent", MediaHttp.userAgent(ctx))
        .header("Accept", "*/*")
        .header("Accept-Encoding", "identity")
        .apply { MediaHttp.HEADERS.forEach { (k, v) -> header(k, v) }; if (range != null) header("Range", range) }
        .build()

    /** [block] on the IO threads with [call]; cancelling the coroutine cancels the call, so a blocked read ends at once. */
    private suspend fun <T> blocking(call: Call, block: (Call) -> T): T = coroutineScope {
        val guard = launch { try { awaitCancellation() } finally { call.cancel() } }
        try { withContext(Dispatchers.IO) { block(call) } } finally { guard.cancel() }
    }

    /** Reads [res]'s body until [untilNs] or [cap] bytes, into [meter]; [live] gets the rate so far about four times a second. */
    private fun drain(res: Response, meter: Meter, untilNs: Long, cap: Long, live: AtomicLong?) {
        val src = res.body?.source() ?: return
        val sink = okio.Buffer()
        var got = 0L
        var shownAt = 0L
        while (System.nanoTime() < untilNs && got < cap) {
            val n = src.read(sink, 64L * 1024)
            if (n < 0) break
            sink.clear()
            got += n
            meter.add(n)
            val now = System.nanoTime()
            if (live != null && now - shownAt > 250_000_000L) { shownAt = now; live.set(meter.bps(meter.firstMs)) }
        }
    }

    /** What the row is: P2P has no web address to test, so only web addresses are. */
    fun testable(s: StreamItem): Boolean =
        !s.isTorrent && !s.url.startsWith(P2p.SCHEME + ":") && (s.url.startsWith("https://") || s.url.startsWith("http://"))

    /** Measures one row (~5 s). [live] gets the rate while it reads; a host that cannot be reached is [Result.failed]. */
    suspend fun testStream(ctx: Context, s: StreamItem, runtime: String?, live: AtomicLong? = null): Result {
        val textNeed = StreamBadges.bps(s.videoSize, s.name + "\n" + s.title, runtime)
        if (!testable(s)) return Result(need = textNeed, unsupported = true)
        return try {
            measure(ctx, s.url, textNeed, live)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Unsupported) {
            Result(need = textNeed, unsupported = true)
        } catch (e: Exception) {
            Result(need = textNeed, failed = true)
        }
    }

    private class Probe(val text: String?, val kind: String, val base: String, val result: Result?)

    private suspend fun measure(ctx: Context, url: String, textNeed: Long, live: AtomicLong?): Result {
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        val p = when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> text(ctx, url, "hls")
            path.endsWith(".mpd") -> text(ctx, url, "dash")
            else -> file(ctx, url, textNeed, live, sniff = true)
        }
        p.result?.let { return it }
        val body = p.text ?: throw IOException("no text")
        return if (p.kind == "hls") hls(ctx, body, p.base, textNeed, live) else dash(ctx, body, p.base, textNeed, live)
    }

    /** A playlist or manifest as text, with the address it came from after redirects (its own addresses resolve there). */
    private suspend fun text(ctx: Context, url: String, kind: String): Probe {
        val call = http.newCall(request(ctx, url))
        return blocking(call) { c ->
            c.execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                Probe(readText(res), kind, res.request.url.toString(), null)
            }
        }
    }

    private fun readText(res: Response): String {
        val src = res.body?.source() ?: return ""
        src.request(TEXT_CAP)
        return src.buffer.readUtf8(minOf(src.buffer.size, TEXT_CAP))
    }

    /** A file read from its start for ~5 s (64 MB at most), counted from its first byte. With [sniff], a host that answers
        with a playlist or a manifest gets that text back instead. */
    private suspend fun file(ctx: Context, url: String, need: Long, live: AtomicLong?, sniff: Boolean): Probe {
        val meter = Meter()
        val call = http.newCall(request(ctx, url, "bytes=0-"))
        return blocking(call) { c ->
            c.execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                val ttfb = meter.ms()
                val type = res.header("Content-Type").orEmpty().lowercase(Locale.US)
                if (sniff && "mpegurl" in type) return@use Probe(readText(res), "hls", res.request.url.toString(), null)
                if (sniff && "dash+xml" in type) return@use Probe(readText(res), "dash", res.request.url.toString(), null)
                // …or a generic type over a playlist or a manifest: its first bytes say so (draining one as a "file" read
                // a few kilobytes and gave a rate that meant nothing)
                if (sniff) {
                    val kind = SpeedPlans.sniffKind(runCatching { res.peekBody(1024).string() }.getOrDefault(""))
                    if (kind != null) return@use Probe(readText(res), kind, res.request.url.toString(), null)
                }
                drain(res, meter, System.nanoTime() + WINDOW_NS, FILE_CAP, live)
                if (meter.total() == 0L) throw IOException("empty")
                Probe(null, "file", url, Result(meter.bps(meter.firstMs), need, ttfb))
            }
        }
    }

    /** Init and segments, one after another as a player fetches them, until ~5 s after the first byte. */
    private suspend fun parts(ctx: Context, list: List<SpeedPlans.Seg>, need: Long, live: AtomicLong?): Result {
        val meter = Meter()
        var ttfb = -1L
        var until = Long.MAX_VALUE
        for (p in list) {
            if (System.nanoTime() >= until) break
            val range = if (p.len >= 0) "bytes=${p.from}-${p.from + p.len - 1}" else null
            val call = http.newCall(request(ctx, p.url, range))
            blocking(call) { c ->
                c.execute().use { res ->
                    if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                    if (ttfb < 0) { ttfb = meter.ms(); until = System.nanoTime() + WINDOW_NS }
                    drain(res, meter, until, FILE_CAP - meter.total(), live)
                }
            }
        }
        if (meter.total() == 0L) throw IOException("empty")
        return Result(meter.bps(meter.firstMs), need, ttfb)
    }

    private suspend fun hls(ctx: Context, master: String, base: String, textNeed: Long, live: AtomicLong?): Result {
        var need = textNeed
        var mediaText = master
        var mediaBase = base
        val variants = SpeedPlans.hlsVariants(master, base)
        if (variants.isNotEmpty()) {
            val best = variants.maxBy { it.bandwidth }
            if (best.bandwidth > 0) need = best.bandwidth
            val t = text(ctx, best.url, "hls")
            mediaText = t.text.orEmpty()
            mediaBase = t.base
        }
        val m = SpeedPlans.hlsMedia(mediaText, mediaBase)
        if (m.segs.isEmpty()) throw Unsupported()
        val segs = if (m.live) m.segs.takeLast(6) else m.segs.take(6)
        return parts(ctx, listOfNotNull(m.init) + segs, need, live)
    }

    private suspend fun dash(ctx: Context, xml: String, base: String, textNeed: Long, live: AtomicLong?): Result {
        val plan = try { SpeedPlans.dashPlan(xml, base, android.util.Xml.newPullParser()) } catch (e: Exception) { null } ?: throw Unsupported()
        val need = if (plan.need > 0) plan.need else textNeed
        plan.single?.let { return file(ctx, it, need, live, sniff = false).result ?: throw IOException("no result") }
        return parts(ctx, listOfNotNull(plan.init?.let { SpeedPlans.Seg(it) }) + plan.media.map { SpeedPlans.Seg(it) }, need, live)
    }

    // ---------- the connection ----------

    data class Line(val bps: Long, val pingMs: Long)

    private const val CF_DOWN = "https://speed.cloudflare.com/__down?bytes=25000000"
    private const val CF_PING = "https://speed.cloudflare.com/__down?bytes=0"

    /** The median of [ms], 0 when empty. */
    internal fun median(ms: List<Long>): Long {
        if (ms.isEmpty()) return 0L
        val s = ms.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /**
     * The connection: the round trip first, on a quiet line (the median of five tiny requests — the first pays for the
     * handshake), then ~8 s of three downloads at once from Cloudflare's speed endpoint, the first half second (TCP's
     * ramp) left out. [live] gets the rate while it runs. Throws when Cloudflare cannot be reached at all.
     */
    suspend fun testConnection(ctx: Context, live: AtomicLong? = null): Line = coroutineScope {
        val pings = ArrayList<Long>()
        repeat(5) {
            val t0 = System.nanoTime()
            val ok = try {
                blocking(http.newCall(request(ctx, CF_PING))) { c -> c.execute().use { r -> r.isSuccessful } }
            } catch (e: IOException) { false }
            if (ok) pings.add((System.nanoTime() - t0) / 1_000_000)
        }
        val meter = Meter()
        val end = System.nanoTime() + 8_000_000_000L
        val ticker = launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                if (meter.ms() > 600) live?.set(meter.bps(500))
            }
        }
        (0 until 3).map {
            launch {
                while (System.nanoTime() < end) {
                    try {
                        blocking(http.newCall(request(ctx, CF_DOWN))) { c ->
                            c.execute().use { res ->
                                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                                drain(res, meter, end, Long.MAX_VALUE, null)
                            }
                        }
                    } catch (e: IOException) {
                        break
                    }
                }
            }
        }.joinAll()
        ticker.cancel()
        if (meter.total() == 0L) throw IOException("the speed test could not be reached")
        Line(meter.bps(500), median(pings))
    }
}
