@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Where the player's sound goes while a subtitle sync listens (SubSync.kt); null the rest of the time. */
internal object SpeechTap {
    @Volatile var collector: SpeechCollector? = null
}

/**
 * The player's audio sink with an ear on it: while a sync runs, every decoded buffer's voices — the centre channel of a
 * 5.1 (or 3.0) track, what both sides share in stereo — go to [SpeechTap.collector] with the buffer's own media time, so
 * nothing depends on how far ahead of the speakers the sink is. Otherwise it only passes buffers on.
 * The same buffer is handed in again until the sink takes it all: it is heard once.
 */
internal class SpeechSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private var pcm = false
    private var sr = 0
    private var ch = 0
    private var enc = C.ENCODING_INVALID
    private var offsetUs = C.TIME_UNSET
    private var lastBuf: ByteBuffer? = null
    private var lastPts = Long.MIN_VALUE
    private var mono = FloatArray(0)

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val f = audioSinkConfig.format
        pcm = f.sampleMimeType == MimeTypes.AUDIO_RAW
        sr = f.sampleRate; ch = f.channelCount; enc = f.pcmEncoding
        super.configure(audioSinkConfig)
    }

    // media time + this = the presentation time handed to handleBuffer (the renderer's offset)
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        offsetUs = outputStreamOffsetUs
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun flush() {
        lastBuf = null
        SpeechTap.collector?.discontinuity()
        super.flush()
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val c = SpeechTap.collector
        if (c != null && (buffer !== lastBuf || presentationTimeUs != lastPts)) {
            lastBuf = buffer; lastPts = presentationTimeUs
            try { hear(c, buffer, presentationTimeUs) } catch (_: Throwable) {}   // listening must never cost the film its sound
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    private fun hear(c: SpeechCollector, buffer: ByteBuffer, pts: Long) {
        if (!pcm) { c.passthrough = true; return }
        if (offsetUs == C.TIME_UNSET || ch <= 0 || sr <= 0) return
        val bytes = when (enc) { C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_FLOAT -> 4; else -> return }
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        val start = b.position(), frames = b.remaining() / (bytes * ch)
        if (frames <= 0) return
        if (mono.size < frames) mono = FloatArray(frames)
        val pick = if (ch == 3 || ch >= 5) 2 else -1       // the centre; -1 = the average of the front pair (or the one channel)
        for (i in 0 until frames) {
            val at = start + i * ch * bytes
            mono[i] = if (bytes == 2) {
                if (pick >= 0) b.getShort(at + pick * 2) / 32768f
                else if (ch >= 2) (b.getShort(at) + b.getShort(at + 2)) / 65536f
                else b.getShort(at) / 32768f
            } else {
                if (pick >= 0) b.getFloat(at + pick * 4)
                else if (ch >= 2) (b.getFloat(at) + b.getFloat(at + 4)) * 0.5f
                else b.getFloat(at)
            }
        }
        c.feed(mono, frames, sr, pts - offsetUs)
    }
}

/** nextlib's factory (the chip first, FFmpeg as the fallback) with its one audio sink wrapped in a SpeechSink — both the
 *  platform's audio renderer and FFmpeg's are built on it (NextRenderersFactory.createRenderers → buildAudioSink). */
internal fun listeningRenderers(context: Context): NextRenderersFactory = object : NextRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink? =
        super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)?.let { SpeechSink(it) }
}

/** The overlay's lines as the sync reads them: (start, end) seconds in the file's own times, spoken lines only. */
internal fun syncLines(all: List<SubCue>): List<DoubleArray> =
    all.filter { c -> spokenLine(c.cues.joinToString(" ") { it.text?.toString() ?: "" }) }
        .map { doubleArrayOf(it.startUs / 1e6, it.endUs / 1e6) }
