package com.nuvio.ckplayer

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Automatic subtitle timing — the Android half of the shared player's "Sync automatically" (web 2026.09.25), the same
 * numbers line for line. A film's dialogue sits in its centre channel (5.1) or in what both sides share (stereo); that
 * signal's energy in the voice band every 10 ms, with the scene's own loudness taken out (a loud scene is not talk — only
 * speech starting and stopping is left), is compared with the add-on file's lines at every offset within a minute and at
 * the 23.976 ↔ 25 fps stretch. An answer is kept only once three tries 30 s apart agree. Tuned 2026-09-25 on seven films
 * in four languages: 0 wrong offsets in 360 trials, ~1 % wrong with the stretch, 2–3 minutes of speech on average.
 * The sound comes from SpeechSink (the player's audio sink, before the speakers) into a SpeechCollector.
 * Pure Kotlin: the unit tests run all of it on the JVM.
 */
internal object SubSync {
    const val HZ = 100
    const val STEP = 30
    const val MIN = 60
    const val MAX = 360
    const val AGREE = 3
    const val Z = 4.0
    const val RATIO = 1.3
    // ±20 s: an add-on file is seconds off, rarely more — every second searched past that is one more chance of a
    // coincidence (the Founder's Mentalist S4E2: the wrong answers sat 41–57 s away). Following (after the first answer):
    // the last 5 minutes, around the timing now, at its speed, a looser bar — a file cut from another broadcast steps a
    // few seconds at each ad break (+1 → +3 → +6.5 → +10 → +13 s over that episode)
    const val RANGE = 20
    const val FOLLOW_W = 300
    const val FZ = 3.5
    const val FTOL = 0.6
}

/** A sync in progress: what it hears, and the file's lines it matches them against. */
internal class SyncRun(val col: SpeechCollector, val lines: List<DoubleArray>)

internal class SyncResult(val off: Double, val scale: Double, val z: Double, val ratio: Double, val score: Double)

/** The voice band (RBJ biquads: high-pass 300 Hz, then low-pass 3000 Hz, Q 0.707), energy per 10 ms frame in dB. */
internal class SpeechVad(sr: Int) {
    private class Bq(hp: Boolean, f0: Double, sr: Int) {
        val b0: Double; val b1: Double; val b2: Double; val a1: Double; val a2: Double
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        init {
            val w = 2 * Math.PI * f0 / sr; val c = cos(w); val a = sin(w) / (2 * 0.7071); val a0 = 1 + a
            val n0 = if (hp) (1 + c) / 2 else (1 - c) / 2
            b0 = n0 / a0; b1 = (if (hp) -(1 + c) else 1 - c) / a0; b2 = n0 / a0; a1 = (-2 * c) / a0; a2 = (1 - a) / a0
        }
        fun step(v: Double): Double {
            val y = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = v; y2 = y1; y1 = y
            return y
        }
    }
    val hop: Double = sr.toDouble() / SubSync.HZ
    private val hp = Bq(true, 300.0, sr)
    private val lp = Bq(false, 3000.0, sr)
    private var acc = 0.0
    private var n = 0

    /** Mono samples in; [out] (energy in dB, index of the frame's last sample) once per finished frame. */
    inline fun push(x: FloatArray, count: Int, out: (Double, Int) -> Unit) {
        for (i in 0 until count) {
            val z = filter(x[i].toDouble())
            if (add(z)) out(take(), i)
        }
    }
    fun filter(v: Double): Double = lp.step(hp.step(v))
    fun add(z: Double): Boolean { acc += z * z; n++; return n >= hop }
    fun take(): Double { val e = 10 * log10(acc / n + 1e-12); acc = 0.0; n = 0; return e }
}

/** A line someone says: not empty, not only [door slams], (music) or ♪ notes. */
internal fun spokenLine(body: String): Boolean =
    body.replace(Regex("<[^>]*>"), "").replace(Regex("\\{[^}]*\\}"), "")
        .replace(Regex("\\[[^\\]]*\\]"), "").replace(Regex("\\([^)]*\\)"), "")
        .replace(Regex("[♪♫#*\\-\\s]"), "").isNotEmpty()

/**
 * [vals]: energies at SubSync.HZ from media second [t0], NaN where nothing was heard. [cues]: (start, end) seconds in the
 * file's own times. → shown = file time × scale + off, or null when there is too little to go on.
 */
internal fun syncSolve(t0: Double, vals: DoubleArray, cues: List<DoubleArray>, around: Double? = null, fixedScale: Double? = null): SyncResult? {
    val n = vals.size
    val heard = vals.filter { !it.isNaN() }.sorted()
    if (heard.size < SubSync.HZ * 20) return null
    val lo = heard[floor(heard.size * 0.10).toInt()]; val hi = heard[floor(heard.size * 0.97).toInt()]
    if (!(hi - lo > 6)) return null                 // flat: silence, or a sound the device would not share
    // 0..1 between the quiet and loud ends, then the local level (2 s) taken out; prefix sums make a line two lookups
    val w = SubSync.HZ
    val v = DoubleArray(n); val q = DoubleArray(n + 1); val h = DoubleArray(n + 1); val p = DoubleArray(n + 1)
    for (i in 0 until n) {
        val ok = !vals[i].isNaN()
        if (ok) v[i] = ((vals[i] - lo) / (hi - lo)).coerceIn(0.0, 1.0)
        q[i + 1] = q[i] + (if (ok) v[i] else 0.0); h[i + 1] = h[i] + (if (ok) 1.0 else 0.0)
    }
    for (i in 0 until n) {
        var d = 0.0
        if (!vals[i].isNaN()) {
            val l = if (i - w < 0) 0 else i - w; val r = if (i + w + 1 > n) n else i + w + 1; val hh = h[r] - h[l]
            d = v[i] - (if (hh > 0) (q[r] - q[l]) / hh else 0.0)
        }
        p[i + 1] = p[i] + d
    }
    val tEnd = t0 + n.toDouble() / SubSync.HZ; val range = SubSync.RANGE.toDouble(); val m = (range * SubSync.HZ).roundToInt(); val step = 1.0 / SubSync.HZ
    var best: SyncResult? = null; var one: SyncResult? = null; val base = around ?: 0.0
    for (sc in if (fixedScale != null) doubleArrayOf(fixedScale) else doubleArrayOf(1.0, 25 / 23.976, 23.976 / 25)) {
        // a speed mismatch pivots on the film's start: both begin together
        val sel = cues.map { doubleArrayOf(it[0] * sc + base, it[1] * sc + base) }.filter { it[1] + range > t0 && it[0] - range < tEnd }
        if (sel.size < 8) continue
        // a film-speed mismatch starts in step with the film, so its offset is small: a stretch with a big offset is only a
        // plain shift in disguise near the start (the Founder's Mentalist S4E2, 09-25: −53.5 s at 25/23.976, twice)
        val mk = if (sc == 1.0 || fixedScale != null) m else minOf(m, 10 * SubSync.HZ); val lo0 = m - mk; val hi0 = m + mk
        val scores = DoubleArray(2 * m + 1) { -1e18 }
        for (k in -mk..mk) {
            val sh = k * step; var s = 0.0
            for (c in sel) {
                val ia = ((c[0] + sh - t0) * SubSync.HZ).roundToLong(); val ib = ((c[1] + sh - t0) * SubSync.HZ).roundToLong()
                if (ib <= 0 || ia >= n) continue
                s += p[if (ib > n) n else ib.toInt()] - p[if (ia < 0) 0 else ia.toInt()]
            }
            scores[k + m] = s
        }
        // the peak, how far it stands above the whole search, and the best rival more than 1.5 s away
        var bk = 0; var bs = -1e18; var mu = 0.0; var sd = 0.0; var rival = -1e18; val gap = 1.5 * SubSync.HZ
        for (k in lo0..hi0) { mu += scores[k]; if (scores[k] > bs) { bs = scores[k]; bk = k } }
        mu /= (hi0 - lo0 + 1)
        for (k in lo0..hi0) sd += (scores[k] - mu) * (scores[k] - mu)
        sd = sqrt(sd / (hi0 - lo0 + 1)).let { if (it == 0.0) 1e-9 else it }
        for (k in lo0..hi0) if (abs(k - bk) > gap && scores[k] > rival) rival = scores[k]
        val res = SyncResult(base + (bk - m) * step, sc, (bs - mu) / sd, (bs - mu) / ((rival - mu).let { if (it == 0.0) 1e-9 else it }), bs)
        if (best == null || res.score > best.score) best = res
        if (sc == 1.0) one = res
    }
    // a stretch has to clearly beat none: every extra speed tried is another chance of a coincidence
    val b = best
    if (b != null && one != null && b !== one && !(b.score > one.score * 1.15 && b.z > one.z)) return one
    return b
}

/** Three agreeing answers in a row (same speed, within a quarter second) — a lone answer is not trusted. */
internal class SyncGate(private val z: Double = SubSync.Z, private val tol: Double = 0.25) {
    private val run = ArrayList<SyncResult>()
    fun offer(r: SyncResult?): SyncResult? {
        if (r == null || r.z < z || r.ratio < SubSync.RATIO) { run.clear(); return null }
        val last = run.lastOrNull()
        if (last != null && (abs(last.off - r.off) >= tol || last.scale != r.scale)) run.clear()
        run.add(r)
        return if (run.size >= SubSync.AGREE) r else null
    }
    fun clear() = run.clear()
}

/**
 * What the sink hears while a sync runs: energies by media time. Fed on the playback thread, read by the sync's loop.
 * A jump of more than 5 s (a seek) starts the collection again — [generation] tells the loop.
 */
internal class SpeechCollector {
    private val lock = Any()
    private var vad: SpeechVad? = null
    private var vadSr = 0
    private var base = -1L
    private var vals = DoubleArray(SubSync.HZ * 420) { Double.NaN }
    private var len = 0
    private var lastUs = Long.MIN_VALUE
    @Volatile var heard = 0
    @Volatile var loud = -200.0
    @Volatile var generation = 0
    @Volatile var passthrough = false                 // the sound goes out undecoded (a receiver): nothing to hear

    /** The sink was flushed (a seek, a new item): the filters start clean. */
    fun discontinuity() { vad = null }

    fun feed(mono: FloatArray, count: Int, sr: Int, startUs: Long) {
        if (startUs < 0 || count <= 0 || sr <= 0) return
        if (lastUs != Long.MIN_VALUE && abs(startUs - lastUs) > 5_000_000L) {
            synchronized(lock) { base = -1L; len = 0; vals.fill(Double.NaN); heard = 0; generation++ }
            vad = null
        }
        val v = vad?.takeIf { vadSr == sr } ?: SpeechVad(sr).also { vad = it; vadSr = sr }
        val t0 = startUs / 1e6
        v.push(mono, count) { e, i -> note(t0 + (i + 1 - v.hop) / sr, e) }   // at the frame's start: whole 10 ms, never a rounding tie
        lastUs = startUs + count * 1_000_000L / sr
    }

    private fun note(t: Double, e: Double) {
        val idx = (t * SubSync.HZ).roundToLong()
        synchronized(lock) {
            if (base < 0) base = idx
            var k = (idx - base).toInt()
            if (k < 0) return
            if (k >= SubSync.HZ * 1200) {           // following a whole film: keep the last 10 minutes
                val cut = SubSync.HZ * 600
                val keep = vals.copyOfRange(cut, maxOf(cut, len))
                vals = DoubleArray(SubSync.HZ * 1260) { Double.NaN }; keep.copyInto(vals)
                base += cut; len = keep.size; k -= cut
                if (k >= SubSync.HZ * 1200) return
            }
            if (k >= vals.size) vals = vals.copyOf(max(k + 1, vals.size * 2)).also { it.fill(Double.NaN, vals.size, it.size) }
            if (k >= len || vals[k].isNaN()) { heard++; if (e > loud) loud = e }
            vals[k] = e
            if (k + 1 > len) len = k + 1
        }
    }

    /** (media second of the first frame, the frames) — a copy, safe to solve on. */
    fun snapshot(): Pair<Double, DoubleArray>? = synchronized(lock) {
        if (base < 0 || len == 0) null else Pair(base.toDouble() / SubSync.HZ, vals.copyOf(len))
    }
}
