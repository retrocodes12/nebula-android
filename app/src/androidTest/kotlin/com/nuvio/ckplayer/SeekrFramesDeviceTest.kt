package com.nuvio.ckplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Seekr scrub pictures on a device ("kind of slow to show in android … not instant and there's breaks", 2026-10-02).
 * The sheet is a 3200×1800 JPEG made by .github/workflows/device-tests.yml (ffmpeg testsrc2 + noise, about the size of
 * a real Seekr sheet), served to SeekrFrames by a fake fetch that takes 400 ms like a far CDN. Measured: the cost of a
 * whole-sheet decode (what 1.85.0 did on every new sheet) against one region; then a scrub across a 2.5 h film after
 * the background downloads — every step must have a picture (no blanks) and a jump must get its own crop fast.
 */
@RunWith(AndroidJUnit4::class)
class SeekrFramesDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun log(s: String) = Log.i("SEEKR-DEVICE", s)
    private val sheet: ByteArray by lazy { ins.context.assets.open("sheet.jpg").readBytes() }

    @Test fun regionDecodeIsFarCheaperThanAWholeSheet() {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val whole = (0 until 5).map {
            val t0 = SystemClock.elapsedRealtimeNanos()
            BitmapFactory.decodeByteArray(sheet, 0, sheet.size, opts)!!.recycle()
            (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
        }
        val t0 = SystemClock.elapsedRealtimeNanos()
        @Suppress("DEPRECATION") val d = BitmapRegionDecoder.newInstance(sheet, 0, sheet.size, false)!!
        val open = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
        val regions = (0 until 100).map { i ->
            val r = Rect((i % 10) * 320, (i / 10) * 180, (i % 10) * 320 + 320, (i / 10) * 180 + 180)
            val t1 = SystemClock.elapsedRealtimeNanos()
            val b = d.decodeRegion(r, opts)!!
            assertEquals(320, b.width); assertEquals(180, b.height); b.recycle()
            (SystemClock.elapsedRealtimeNanos() - t1) / 1e6
        }
        d.recycle()
        val wMed = whole.sorted()[2]; val rMed = regions.sorted()[50]; val rMax = regions.max()
        log("sheet ${sheet.size} B · whole-sheet decode median ${"%.1f".format(wMed)} ms (${whole.joinToString { "%.0f".format(it) }}) · " +
            "decoder open ${"%.1f".format(open)} ms · one region median ${"%.1f".format(rMed)} ms, max ${"%.1f".format(rMax)} ms")
        assertTrue("one picture should cost a fraction of a sheet: $rMed vs $wMed ms", rMed * 4 < wMed)
    }

    private fun film(sheets: Int): Seekr.Track {
        val cues = ArrayList<SeekrCue>()
        for (s in 0 until sheets) for (i in 0 until 100) {
            val n = s * 100 + i
            cues.add(SeekrCue(n * 10_000L, n * 10_000L + 10_000, "https://sprites.seekr.tv/t/sheet_%03d.jpg".format(s), (i % 10) * 320, (i / 10) * 180, 320, 180))
        }
        return Seekr.Track(cues, 1.0, System.currentTimeMillis())
    }

    @Test fun aScrubAcrossTheFilmNeverBlanksAndJumpsAreQuick() {
        val fetched = ArrayList<String>()
        val f = SeekrFrames(listOf("imdb_id" to "tt0"), get = { u -> synchronized(fetched) { fetched.add(u) }; Thread.sleep(400); 200 to sheet })
        val t = film(9)                                                    // 900 cues = 2 h 30 min, 9 sheets
        val startAt = 5_000_000L                                           // resumed at 1:23:20 → sheet 5 first
        ins.runOnMainSync { f.startWith(t, startAt) }
        // cold: the first touch near the playhead, before most sheets are in
        Thread.sleep(700)
        val coldCue = SeekrWire.cueIndex(t.cues, startAt, 1.0)
        val cold = waitFor(3000) { f.hasCrop(coldCue) }
        log("first sheet fetched: ${synchronized(fetched) { fetched.firstOrNull() }} · crop at the playhead after ${cold} ms more")
        assertTrue("the sheet at the playhead comes first", synchronized(fetched) { fetched.first() }.endsWith("sheet_005.jpg"))
        // let the rest download (9 × 400 ms)
        val all = waitFor(8000) { synchronized(fetched) { fetched.size } >= 9 }
        log("all 9 sheets in after ${all} ms more")
        // a finger dragged across the whole film: a position every 16 ms, 30 s apart
        var blanks = 0; var steps = 0; var seen = false; var exact = 0; var lagSum = 0; var lagMax = 0
        var pos = 0L
        while (pos < 9_000_000L) {
            val p = pos
            ins.runOnMainSync { f.request(p) }
            Thread.sleep(16)                                               // one frame of the finger
            val want = SeekrWire.cueIndex(t.cues, p, 1.0)
            if (f.frame.value == null) { if (seen) blanks++ } else {
                seen = true
                val lag = kotlin.math.abs(f.shownCue - want)
                if (lag == 0) exact++
                lagSum += lag; lagMax = maxOf(lagMax, lag)
            }
            steps++; pos += 30_000
        }
        ins.runOnMainSync { f.idle() }
        log("sweep: $steps steps · $blanks blank after the first picture · exact picture on $exact · " +
            "lag mean ${"%.2f".format(lagSum.toDouble() / steps)} cues, max $lagMax cues (a cue = 10 s)")
        assertEquals("a moving scrub never shows a blank tip", 0, blanks)
        // jumps: far positions, one at a time, each must get its own crop quickly
        val lat = listOf(100_000L, 8_500_000L, 2_000_000L, 6_660_000L, 4_440_000L, 333_000L, 7_777_000L).map { p ->
            val i = SeekrWire.cueIndex(t.cues, p, 1.0)
            val t0 = SystemClock.elapsedRealtime()
            ins.runOnMainSync { f.request(p) }
            val ms = waitFor(2000) { f.hasCrop(i) }
            ins.runOnMainSync { f.idle() }
            ms.also { assertTrue("jump to $p took $it ms", it in 0..400) }
        }
        log("jumps (ms to the exact picture): ${lat.joinToString()}")
        ins.runOnMainSync { f.release() }
    }

    /** ms until [cond] holds (polled every 5 ms), or -1 after [maxMs]. */
    private fun waitFor(maxMs: Long, cond: () -> Boolean): Long {
        val t0 = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - t0 < maxMs) { if (cond()) return SystemClock.elapsedRealtime() - t0; Thread.sleep(5) }
        return -1
    }
}
