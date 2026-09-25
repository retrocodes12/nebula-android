package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * The automatic subtitle timing (SubSync.kt) is a port of the shared player's (web 2026.09.25): these pin it to the
 * JavaScript's own answers on the same input. The expected numbers were computed by the player's code itself
 * (ssVadNew/ssVadPush/ssSolve, lifted out of webos-player/index.html) — a drift here is a drift between the platforms.
 */
class SubSyncTest {
    private class Fixture(val t0: Double, val vals: DoubleArray, val cues: List<DoubleArray>)
    private fun fixture(): Fixture {
        val text = javaClass.classLoader!!.getResourceAsStream("subsync-tos.txt")!!.bufferedReader().readText()
        var t0 = 0.0; var vals = DoubleArray(0); val cues = ArrayList<DoubleArray>()
        text.lines().forEach { l ->
            when {
                l.startsWith("T0 ") -> t0 = l.substring(3).toDouble()
                l.startsWith("VALS ") -> vals = l.substring(5).split(',').map { it.toDouble() / 100 }.toDoubleArray()
                l.startsWith("CUE ") -> l.substring(4).split(' ').let { cues.add(doubleArrayOf(it[0].toDouble(), it[1].toDouble())) }
            }
        }
        return Fixture(t0, vals, cues)
    }

    @Test fun theDetector_measuresWhatThePlayerMeasures() {
        // 48 kHz: a 440 Hz + 1500 Hz mix (the 1500 Hz fading after 0.25 s) under a Park–Miller hiss, 0.5 s
        var seed = 12345L
        fun lcg(): Double { seed = (seed * 16807) % 2147483647; return seed / 2147483647.0 - 0.5 }
        val x = FloatArray(24000) { i ->
            (0.3 * sin(2 * PI * 440 * i / 48000) + 0.2 * sin(2 * PI * 1500 * i / 48000) * (if (i < 12000) 1.0 else 0.1) + 0.05 * lcg()).toFloat()
        }
        val got = ArrayList<Pair<Double, Int>>()
        SpeechVad(48000).push(x, x.size) { e, j -> got.add(e to j) }
        assertEquals(50, got.size)
        assertEquals(-12.793797844, got[0].first, 1e-4); assertEquals(479, got[0].second)
        assertEquals(-12.46251636, got[1].first, 1e-4); assertEquals(959, got[1].second)
        assertEquals(-12.720403724, got[24].first, 1e-4); assertEquals(11999, got[24].second)
        assertEquals(-14.388898072, got[49].first, 1e-4); assertEquals(23999, got[49].second)
    }

    @Test fun linesSixSecondsLate_moveSixEarlier_asOnTheWeb() {
        val f = fixture()
        val r = syncSolve(f.t0, f.vals, f.cues.map { doubleArrayOf(it[0] + 6, it[1] + 6) })
        assertNotNull(r); r!!
        assertEquals(-5.91, r.off, 0.0005)
        assertEquals(1.0, r.scale, 0.0)
        assertEquals(6.577229, r.z, 1e-4)
        assertEquals(2.682027, r.ratio, 1e-4)
    }

    @Test fun aFileForA25fpsCut_isStretchedBack_asOnTheWeb() {
        val f = fixture(); val k = 25 / 23.976
        val r = syncSolve(f.t0, f.vals, f.cues.map { doubleArrayOf((it[0] - 2) / k, (it[1] - 2) / k) })
        assertNotNull(r); r!!
        assertEquals(k, r.scale, 1e-12)
        assertEquals(2.09, r.off, 0.0005)
        assertEquals(6.688502, r.z, 1e-4)
    }

    @Test fun silence_orTooLittle_givesNoAnswer() {
        val f = fixture()
        assertNull(syncSolve(0.0, DoubleArray(6000) { -120.0 }, f.cues))            // flat: a muted or unheard source
        assertNull(syncSolve(f.t0, f.vals.copyOf(1500), f.cues))                      // 15 s is too little
    }

    @Test fun theGate_wantsThreeAgreeingAnswersInARow() {
        val g = SyncGate()
        fun r(off: Double, z: Double = 6.0, ratio: Double = 2.0, sc: Double = 1.0) = SyncResult(off, sc, z, ratio, 1.0)
        assertNull(g.offer(r(-6.0))); assertNull(g.offer(r(-6.1)))
        assertNotNull(g.offer(r(-6.05)))
        g.clear()
        assertNull(g.offer(r(-6.0))); assertNull(g.offer(r(-6.0))); assertNull(g.offer(r(30.0)))   // a rival resets the run
        assertNull(g.offer(r(30.0))); assertNotNull(g.offer(r(30.0)))
        g.clear()
        assertNull(g.offer(r(-6.0))); assertNull(g.offer(r(-6.0, z = 3.0)))                      // a weak answer resets it
        assertNull(g.offer(r(-6.0))); assertNull(g.offer(r(-6.0)))
        assertNull(g.offer(r(-6.0, sc = 25 / 23.976)))                                           // so does another speed
    }

    @Test fun theCollector_placesFramesByMediaTime_andAJumpStartsAgain() {
        val c = SpeechCollector()
        val tone = FloatArray(16000) { i -> (0.2 * sin(2 * PI * 1000 * i / 16000)).toFloat() }
        c.feed(tone, tone.size, 16000, 100_000_000L)      // one second from 100 s
        c.feed(tone, tone.size, 16000, 101_000_000L)
        val s = c.snapshot()!!
        assertEquals(100.0, s.first, 0.02)
        assertEquals(200, c.heard); assertEquals(200, s.second.size)
        assertTrue(c.loud > -30)
        c.feed(tone, tone.size, 16000, 400_000_000L)      // a seek: the stretch heard so far is dropped
        assertEquals(1, c.generation); assertEquals(100, c.heard)
        assertEquals(400.0, c.snapshot()!!.first, 0.02)
    }

    @Test fun onlySpokenLinesCount() {
        assertTrue(spokenLine("You're a jerk, Thom."))
        assertTrue(spokenLine("<i>Look Celia</i>"))
        assertFalse(spokenLine("[door slams]"))
        assertFalse(spokenLine("♪ ♪"))
        assertFalse(spokenLine("(MUSIC PLAYING)"))
        assertFalse(spokenLine("  "))
    }
}
