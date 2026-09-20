package com.nuvio.ckplayer

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import java.io.File
import java.util.Locale

/**
 * The bits of the player that are plain functions: the playback-info rows,
 * codec names people recognise, and subtitle timing. Mirrors the shared web
 * player's pinfoRender / codecName / applySubOffset.
 */

/** One HUD line: label, value, and whether the value deserves the warning tint. */
internal data class InfoRow(val k: String, val v: String, val warn: Boolean = false)

/** "avc1.640028" → "H.264"; unknown codecs come back as-is, never blank. */
internal fun codecName(codecs: String?, mime: String?): String? {
    val c = (codecs ?: "").lowercase(Locale.ROOT)
    val m = (mime ?: "").lowercase(Locale.ROOT)
    return when {
        c.startsWith("avc") || c.startsWith("h264") || m == MimeTypes.VIDEO_H264 -> "H.264"
        c.startsWith("hvc") || c.startsWith("hev") || c.startsWith("h265") || m == MimeTypes.VIDEO_H265 -> "HEVC"
        c.startsWith("av01") || m == MimeTypes.VIDEO_AV1 -> "AV1"
        c.startsWith("vp09") || c.startsWith("vp9") || m == MimeTypes.VIDEO_VP9 -> "VP9"
        c.startsWith("mp4a") || m == MimeTypes.AUDIO_AAC -> "AAC"
        c.startsWith("ec-3") || m == MimeTypes.AUDIO_E_AC3 || m == MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus"
        c.startsWith("ac-3") || m == MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        c.startsWith("opus") || m == MimeTypes.AUDIO_OPUS -> "Opus"
        m == MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
        m == MimeTypes.AUDIO_DTS || m == MimeTypes.AUDIO_DTS_HD -> "DTS"
        c.isNotEmpty() -> codecs
        m.isNotEmpty() -> m.substringAfter('/')
        else -> null
    }
}

internal fun fmtBits(bps: Long): String =
    if (bps >= 1_000_000) String.format(Locale.US, "%.1f Mb/s", bps / 1e6)
    else "${(bps / 1000).coerceAtLeast(1)} kb/s"

private fun Format.isHdr(): Boolean {
    val t = colorInfo?.colorTransfer ?: return false
    return t == C.COLOR_TRANSFER_ST2084 || t == C.COLOR_TRANSFER_HLG
}

/** Settings › Buffer ahead: seconds loaded ahead → seconds gathered again after a stall before playback resumes. */
internal val BUFFER_STEPS = mapOf(60 to 3, 120 to 4, 240 to 5)

/** 240 → "4 min", 50 → "50 s": the words Settings and the HUD use for a buffer length. */
internal fun bufferLabel(secs: Int): String = if (secs >= 60) "${secs / 60} min" else "$secs s"

/**
 * The load control for Settings › Buffer ahead, or null for Auto (the engine's stock 50 s). The stock byte cap
 * (~140 MB) is what actually ends loading on a high-bitrate stream, so it grows with the seconds — but never past
 * half of what this process may allocate: the buffer is plain byte arrays on the Java heap, and 4 min of a 4K
 * stream would want ~600 MB. What does not fit simply buffers less, and the HUD shows what it got.
 */
@UnstableApi
internal fun bufferLoadControl(secs: Int): LoadControl? {
    val resume = BUFFER_STEPS[secs] ?: return null
    val stock = DefaultLoadControl.DEFAULT_VIDEO_BUFFER_SIZE.toLong() + DefaultLoadControl.DEFAULT_AUDIO_BUFFER_SIZE
    val want = stock * secs * 1000 / DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
    val room = Runtime.getRuntime().maxMemory() / 2
    val bytes = maxOf(stock, minOf(want, room)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            secs * 1000, secs * 1000,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            maxOf(DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, resume * 1000),
        )
        .setTargetBufferBytes(bytes)
        .build()
}

/** Reads the player once and returns the rows the HUD shows, top to bottom. */
@UnstableApi
internal fun playbackInfoRows(
    exo: ExoPlayer,
    bandwidth: DefaultBandwidthMeter,
    subOffsetMs: Long,
    scrubStatus: String? = null,
    p2pLine: String? = null,
    stalls: Int = 0,
    viaLine: String? = null,
    decoderLine: String? = null,
): List<InfoRow> {
    val rows = mutableListOf<InfoRow>()
    val vf = exo.videoFormat
    val af = exo.audioFormat
    if (vf != null && vf.width > 0 && vf.height > 0) {
        var pic = "${vf.width}×${vf.height}"
        if (vf.frameRate > 0f) pic += " · " + (if (vf.frameRate % 1f == 0f) vf.frameRate.toInt().toString()
            else String.format(Locale.US, "%.2f", vf.frameRate)) + " fps"
        if (vf.isHdr()) pic += " · HDR"
        rows += InfoRow("Picture", pic)
    }
    val codecs = listOfNotNull(codecName(vf?.codecs, vf?.sampleMimeType), codecName(af?.codecs, af?.sampleMimeType))
    if (codecs.isNotEmpty()) rows += InfoRow("Codec", codecs.joinToString(" / "))
    var streamBps = 0L
    if (vf != null && vf.bitrate > 0) streamBps += vf.bitrate
    if (af != null && af.bitrate > 0) streamBps += af.bitrate
    val est = bandwidth.bitrateEstimate
    if (streamBps > 0) rows += InfoRow("Stream", fmtBits(streamBps))
    if (est > 0) rows += InfoRow("Connection", fmtBits(est), warn = streamBps > 0 && est < streamBps * 1.15)
    val buf = exo.totalBufferedDuration / 1000.0
    val goal = if (Prefs.buffer > 0) Prefs.buffer else DefaultLoadControl.DEFAULT_MAX_BUFFER_MS / 1000
    rows += InfoRow("Buffer", String.format(Locale.US, "%.1f s ahead · aims for %s", buf, bufferLabel(goal)), warn = exo.isPlaying && buf < 3)
    if (stalls > 0) rows += InfoRow("Stalls", "$stalls this play", warn = stalls > 2)
    exo.videoDecoderCounters?.let { dc ->
        val shown = dc.renderedOutputBufferCount + dc.droppedBufferCount
        if (shown > 0) rows += InfoRow(
            "Dropped frames", "${dc.droppedBufferCount} of $shown",
            warn = shown > 100 && dc.droppedBufferCount > shown / 100,
        )
    }
    if (exo.isCurrentMediaItemLive) {
        val off = exo.currentLiveOffset
        if (off != C.TIME_UNSET && off >= 0) rows += InfoRow("Behind live", String.format(Locale.US, "%.1f s", off / 1000.0))
    }
    val speed = exo.playbackParameters.speed
    if (speed != 1f) rows += InfoRow("Speed", speed.toString().trimEnd('0').trimEnd('.') + "×")
    if (exo.currentMediaItem?.localConfiguration?.drmConfiguration != null) rows += InfoRow("Encryption", "Decrypted on device")
    if (viaLine != null) rows += InfoRow("Via", viaLine)                    // Play through your PC: the sharing computer
    if (decoderLine != null) rows += InfoRow("Decoding", decoderLine)      // 1.73.0: the picture is on the processor, not the chip
    if (subOffsetMs != 0L) rows += InfoRow("Subtitle timing", fmtSubOffset(subOffsetMs))
    // so a screenshot of the panel says why the scrub tip had no picture
    if (scrubStatus != null) rows += InfoRow("Preview frames", scrubStatus, warn = scrubStatus.startsWith(ScrubPreview.UNAVAILABLE))
    // the swarm behind a P2P stream: peers, how fast they are sending, how much of the file is here
    if (p2pLine != null) rows += InfoRow("P2P", p2pLine, warn = P2p.stalled())
    return rows
}

/** The Info panel's "Preview frames" value: the setting first, then the stream's shape, then the reader's own word. */
internal fun scrubStatusLine(prefOn: Boolean, live: Boolean, encrypted: Boolean, reader: String?): String = when {
    !prefOn -> "Off"
    live -> ScrubPreview.UNAVAILABLE + "live stream"
    encrypted -> ScrubPreview.UNAVAILABLE + "encrypted stream"
    reader == null -> ScrubPreview.UNAVAILABLE + "not a plain video file"
    else -> reader
}

/** "+0.5 s" / "−0.1 s" / "0.0 s" — the readout the timing panel and HUD share. */
internal fun fmtSubOffset(ms: Long): String {
    val s = ms / 1000.0
    return (if (ms > 0) "+" else if (ms < 0) "−" else "") + String.format(Locale.US, "%.1f s", kotlin.math.abs(s))
}

/** "9:41 pm · Ends 11:12 pm" for the chrome's top-right; just the clock on live or while the
    end is unknown. Follows the device's 12/24-hour setting; the end accounts for the speed. */
internal fun clockLine(ctx: Context, remainMs: Long, speed: Float, live: Boolean): String {
    val fmt = android.text.format.DateFormat.getTimeFormat(ctx)
    fun at(ms: Long) = fmt.format(java.util.Date(ms)).replace("AM", "am").replace("PM", "pm")
    val now = System.currentTimeMillis()
    if (live || remainMs <= 0) return at(now)
    return at(now) + " · Ends " + at(now + (remainMs / speed.coerceAtLeast(0.1f)).toLong())
}

private val SUB_TIME = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})([,.])(\\d{3})|(\\d{1,2}):(\\d{2})\\.(\\d{3})")

/**
 * Writes a copy of an SRT/VTT file with every cue moved by offsetMs (never below
 * zero) and returns it. Media3 has no live offset for text, so the shifted
 * file is fed back as the subtitle source instead.
 */
internal fun shiftedSubFile(ctx: Context, src: Uri, offsetMs: Long): Uri? = runCatching {
    val path = src.path ?: return@runCatching null
    val text = File(path).readText()
    val out = StringBuilder(text.length + 64)
    for (line in text.split("\n")) {
        if (line.contains("-->")) {
            out.append(SUB_TIME.replace(line) { m ->
                val g = m.groupValues
                val (ms, sep, long) = if (g[1].isNotEmpty()) {
                    Triple(g[1].toLong() * 3_600_000 + g[2].toLong() * 60_000 + g[3].toLong() * 1000 + g[5].toLong(), g[4], true)
                } else {
                    Triple(g[6].toLong() * 60_000 + g[7].toLong() * 1000 + g[8].toLong(), ".", false)
                }
                val t = (ms + offsetMs).coerceAtLeast(0L)
                if (long || t >= 3_600_000) String.format(
                    Locale.US, "%02d:%02d:%02d%s%03d", t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, sep, t % 1000,
                ) else String.format(Locale.US, "%02d:%02d.%03d", t / 60_000, (t / 1000) % 60, t % 1000)
            })
        } else out.append(line)
        out.append('\n')
    }
    val f = File(ctx.cacheDir, File(path).nameWithoutExtension + "-shift" + offsetMs + "." + File(path).extension)
    f.writeText(out.toString())
    Uri.fromFile(f)
}.getOrNull()
