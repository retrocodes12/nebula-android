package com.nuvio.ckplayer

import androidx.media3.common.MediaItem
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The subtitle sync's ear on a real device (the Founder's phone set a timing "completely off", 2026-09-25): the player
 * built as PlayerScreen builds it (listeningRenderers + an attached DecoderManager), media from this test APK's assets
 * (Tears of Steel, CC BY — made by .github/workflows/device-tests.yml, never committed), started part-way in as a resume
 * is. Two things are measured: where the tap says it is against exo.currentPosition (it may only run a little AHEAD —
 * the sink's buffer — never by a constant), and the sync's answer for the official subtitles shifted 6 s late (−6).
 */
@RunWith(AndroidJUnit4::class)
class SpeechSinkDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()

    private fun cues(shift: Double): List<DoubleArray> {
        val text = ins.context.assets.open("tos.srt").bufferedReader().readText().replace("\r", "")
        val re = Regex("(\\d+):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d+):(\\d{2}):(\\d{2})[,.](\\d{3})")
        fun sec(h: String, m: String, s: String, ms: String) = h.toInt() * 3600 + m.toInt() * 60 + s.toInt() + ms.toInt() / 1000.0
        return text.split(Regex("\\n\\s*\\n")).mapNotNull { b ->
            val lines = b.split('\n'); val i = lines.indexOfFirst { re.containsMatchIn(it) }
            if (i < 0 || !spokenLine(lines.drop(i + 1).joinToString(" "))) return@mapNotNull null
            val g = re.find(lines[i])!!.groupValues
            doubleArrayOf(sec(g[1], g[2], g[3], g[4]) + shift, sec(g[5], g[6], g[7], g[8]) + shift)
        }
    }

    private class Heard(val leads: List<Double>, val snap: Pair<Double, DoubleArray>?, val col: SpeechCollector, val log: String)

    private fun listen(asset: String, startMs: Long, secs: Int, speed: Float): Heard {
        val col = SpeechCollector()
        SpeechTap.collector = col
        lateinit var exo: ExoPlayer
        val dm = DecoderManager()
        ins.runOnMainSync {
            exo = ExoPlayer.Builder(ins.targetContext)
                .setRenderersFactory(listeningRenderers(ins.targetContext).setDecoderManager(dm))
                .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory { AssetDataSource(ins.context) }))   // asset:/// = this test APK's assets (its context has no application context for DefaultDataSource)
                .build().apply {
                    dm.attach(this)
                    volume = 0f                                  // the ear sits before the volume
                    setMediaItem(MediaItem.fromUri("asset:///$asset"), startMs)
                    setPlaybackSpeed(speed)
                    prepare(); playWhenReady = true
                }
        }
        val leads = ArrayList<Double>(); val log = StringBuilder()
        val deadline = System.currentTimeMillis() + (secs / speed * 1000).toLong() + 90_000
        while (col.heard < secs * SubSync.HZ && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            var pos = 0L; var playing = false; var err: String? = null
            ins.runOnMainSync {
                pos = exo.currentPosition; playing = exo.isPlaying
                err = exo.playerError?.let { e ->
                    var c: Throwable? = e; val chain = StringBuilder(e.errorCodeName)
                    while (c != null) { chain.append(" <- ").append(c.toString()).append(" @ ").append(c.stackTrace.take(5).joinToString(" | ")); c = c.cause }
                    chain.toString()
                }
            }
            if (err != null) { log.append("error: ").append(err); break }
            val s = col.snapshot() ?: continue
            val last = s.first + s.second.size.toDouble() / SubSync.HZ
            if (playing) leads.add(last - pos / 1000.0)
            if (leads.size % 20 == 1) log.append(String.format(java.util.Locale.US, "pos %.1f heard-to %.1f lead %.2f | ", pos / 1000.0, last, last - pos / 1000.0))
        }
        ins.runOnMainSync { dm.detach(); exo.release() }
        SpeechTap.collector = null
        return Heard(leads, col.snapshot(), col, log.toString())
    }

    private fun check(asset: String, startMs: Long) {
        val h = listen(asset, startMs, secs = 150, speed = 2f)
        val sorted = h.leads.sorted()
        val median = if (sorted.isEmpty()) Double.NaN else sorted[sorted.size / 2]
        println("SUBSYNC-DEVICE $asset: heard ${h.col.heard / SubSync.HZ} s, lead median ${"%.2f".format(median)} s, ${h.log}")
        assertFalse("$asset: passthrough", h.col.passthrough)
        assertTrue("$asset: heard ${h.col.heard / SubSync.HZ} s — ${h.log}", h.col.heard >= 120 * SubSync.HZ)
        // the tap hears what the sink is handed, which is at most a buffer ahead of what plays — never a constant away
        assertTrue("$asset: the tap is ${"%.2f".format(median)} s off the playhead — ${h.log}", median > -0.5 && median < 4.0)
        val snap = h.snap!!
        assertEquals("$asset: first heard second vs the start", startMs / 1000.0, snap.first, 3.0)
        val shifted = cues(6.0)
        val answers = listOf(90, 120, 150).mapNotNull { s ->
            val n = minOf(snap.second.size, s * SubSync.HZ)
            syncSolve(snap.first, snap.second.copyOf(n), shifted)
        }
        println("SUBSYNC-DEVICE $asset answers: " + answers.joinToString { "off %.2f scale %.4f z %.1f ratio %.2f".format(it.off, it.scale, it.z, it.ratio) })
        val last = answers.last()
        assertEquals("$asset: the sync's offset", -6.0, last.off, 0.35)
        assertEquals("$asset: the sync's speed", 1.0, last.scale, 0.0)
    }

    @Test fun stereoMatroska_resumedAt2Minutes() = check("tos-stereo.mkv", 120_000)
    @Test fun surroundMp4_resumedAt3Minutes() = check("tos-51.mp4", 180_000)
    @Test fun hlsSegments_resumedAt2Minutes() = check("tos-hls/index.m3u8", 120_000)
}
