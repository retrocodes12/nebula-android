package com.nuvio.ckplayer

import android.media.audiofx.DynamicsProcessing
import android.os.Build

/**
 * Night mode (Settings › Playback and the player's Audio list; `pref_night`, this device only, off by default): loud
 * peaks quieter, quiet dialogue lifted. A [DynamicsProcessing] effect on the player's audio session — ExoPlayer makes a
 * real session id when it is built, and nextlib's renderers (the chip's and the FFmpeg audio decoder) play through
 * Media3's own audio sink, which opens its AudioTrack on that session, so the effect hears every decoder.
 *
 * One broadband compressor (a single multi-band band covering every frequency): above −24 dB the level rises a
 * quarter as fast (ratio 4, soft 6 dB knee, 5 ms attack, 150 ms release), then +6 dB of make-up gain; a limiter at
 * −1 dB (ratio 10, 1 ms attack) after it keeps the make-up from ever clipping. A −12 dB peak comes out at −15, a −30 dB
 * whisper at −24. Android 9 (API 28) and later only — the option is hidden below that: LoudnessEnhancer only lifts,
 * it does not even out. Sound sent to the TV as it is (passthrough) is not processed: the effect is skipped by Android.
 */
class NightMode {
    private var fx: DynamicsProcessing? = null
    private var session = 0
    /** The effect is attached and working on this play. */
    var active = false; private set

    /** Attach, re-attach (a new session) or release, to match [on]. Never throws. */
    fun apply(sessionId: Int, on: Boolean) {
        if (!on || !available || sessionId <= 0) { release(); return }
        if (fx != null && session == sessionId) {
            runCatching { fx?.setEnabled(true) }
            active = runCatching { fx?.getEnabled() == true }.getOrDefault(false)
            return
        }
        release()
        fx = runCatching { DynamicsProcessing(0, sessionId, config()).also { it.setEnabled(true) } }.getOrNull()
        session = sessionId
        active = fx != null
    }

    fun release() {
        runCatching { fx?.setEnabled(false) }
        runCatching { fx?.release() }
        fx = null; session = 0; active = false
    }

    companion object {
        /** The info panel's Sound row: null while night mode is off. */
        fun soundLine(on: Boolean, active: Boolean, can: Boolean = available): String? = when {
            !on || !can -> null
            active -> "Night mode"
            else -> "Night mode could not start on this device"
        }

        val available: Boolean get() = Build.VERSION.SDK_INT >= 28

        private fun config(): DynamicsProcessing.Config {
            // two channels described; Android repeats the description for every channel the session really carries
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                false, 0,      // no pre-EQ
                true, 1,       // one compressor band over everything
                false, 0,      // no post-EQ
                true,          // the limiter
            ).build()
            val band = DynamicsProcessing.MbcBand(
                true, 20_000f,     // enabled, up to 20 kHz = every frequency
                5f, 150f,          // attack, release (ms)
                4f, -24f, 6f,      // ratio, threshold (dB), knee (dB)
                -90f, 1f,          // noise gate threshold (dB) and expander ratio: off
                0f, 6f,            // pre-gain, make-up gain (dB)
            )
            cfg.setMbcBandAllChannelsTo(0, band)
            cfg.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(
                true, true, 0,     // in use, enabled, link group
                1f, 60f,           // attack, release (ms)
                10f, -1f, 0f,      // ratio, threshold (dB), post-gain (dB)
            ))
            return cfg
        }
    }
}
