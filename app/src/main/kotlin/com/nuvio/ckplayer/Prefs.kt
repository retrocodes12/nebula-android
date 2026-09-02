package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * User preferences. Each value lives twice: SharedPreferences is the truth,
 * and a Compose state mirror makes every composable that reads one recompose
 * the moment it changes — which is how picking an accent restyles the whole
 * app without a restart.
 */
object Prefs {
    private const val P = "ckplayer"

    /** key → label → colour. Shared palette with the web player's Settings. */
    val ACCENTS = listOf(
        Triple("nebula", "Nebula Red", Color(0xFFE50914)),
        Triple("white", "White", Color(0xFFF2F2F7)),
        Triple("cobalt", "Cobalt", Color(0xFF0A84FF)),
        Triple("emerald", "Emerald", Color(0xFF30D158)),
        Triple("violet", "Violet", Color(0xFFBF5AF2)),
        Triple("amber", "Amber", Color(0xFFFF9F0A)),
        Triple("rose", "Rose", Color(0xFFFF375F)),
    )

    // A light accent (White) needs dark ink on top of it; every other accent
    // carries white. Buttons, chips and badges all draw from this pair.
    val accentColor: Color get() = ACCENTS.firstOrNull { it.first == accent }?.third ?: ACCENTS[0].third
    val onAccent: Color get() = if (accent == "white") Color(0xFF111114) else Color.White

    var accent by mutableStateOf("nebula"); private set
    var showHero by mutableStateOf(true); private set
    var showContinue by mutableStateOf(true); private set
    var posterScale by mutableStateOf(1.0f); private set          // 0.85 / 1.0 / 1.18
    var autoPlayNext by mutableStateOf(true); private set         // up-next counts down by itself
    var autoStream by mutableStateOf(false); private set          // play the best stream without asking
    var holdSpeed by mutableStateOf(true); private set            // hold the player to speed up
    var holdRate by mutableStateOf(2.0f); private set
    var gestures by mutableStateOf(true); private set             // tap-zone skips + swipe seek
    var audioLang by mutableStateOf(""); private set              // "" = device language
    var subLang by mutableStateOf(""); private set
    var quality by mutableStateOf("auto"); private set            // auto / high (start high) / saver (≤720p)
    var skipIntro by mutableStateOf("button"); private set       // button (a pill to press) / auto / off
    var scrubFrames by mutableStateOf(true); private set           // a picture of where a scrub will land

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        accent = p.getString("pref_accent", "nebula") ?: "nebula"
        showHero = p.getBoolean("pref_hero", true)
        showContinue = p.getBoolean("pref_continue", true)
        posterScale = p.getFloat("pref_poster", 1.0f)
        autoPlayNext = p.getBoolean("pref_autonext", true)
        autoStream = p.getBoolean("pref_autostream", false)
        holdSpeed = p.getBoolean("pref_holdspeed", true)
        holdRate = p.getFloat("pref_holdrate", 2.0f)
        gestures = p.getBoolean("pref_gestures", true)
        audioLang = p.getString("pref_audiolang", "") ?: ""
        subLang = p.getString("pref_sublang", "") ?: ""
        quality = p.getString("pref_quality", "auto") ?: "auto"
        skipIntro = p.getString("pref_skip", "button") ?: "button"
        scrubFrames = p.getBoolean("pref_scrubframes", true)
    }

    private fun edit(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()

    fun setAccent(ctx: Context, v: String) { accent = v; edit(ctx).putString("pref_accent", v).apply() }
    fun setShowHero(ctx: Context, v: Boolean) { showHero = v; edit(ctx).putBoolean("pref_hero", v).apply() }
    fun setShowContinue(ctx: Context, v: Boolean) { showContinue = v; edit(ctx).putBoolean("pref_continue", v).apply() }
    fun setPosterScale(ctx: Context, v: Float) { posterScale = v; edit(ctx).putFloat("pref_poster", v).apply() }
    fun setAutoPlayNext(ctx: Context, v: Boolean) { autoPlayNext = v; edit(ctx).putBoolean("pref_autonext", v).apply() }
    fun setAutoStream(ctx: Context, v: Boolean) { autoStream = v; edit(ctx).putBoolean("pref_autostream", v).apply() }
    fun setHoldSpeed(ctx: Context, v: Boolean) { holdSpeed = v; edit(ctx).putBoolean("pref_holdspeed", v).apply() }
    fun setHoldRate(ctx: Context, v: Float) { holdRate = v; edit(ctx).putFloat("pref_holdrate", v).apply() }
    fun setGestures(ctx: Context, v: Boolean) { gestures = v; edit(ctx).putBoolean("pref_gestures", v).apply() }
    fun setAudioLang(ctx: Context, v: String) { audioLang = v; edit(ctx).putString("pref_audiolang", v).apply() }
    fun setSubLang(ctx: Context, v: String) { subLang = v; edit(ctx).putString("pref_sublang", v).apply() }
    fun setQuality(ctx: Context, v: String) { quality = v; edit(ctx).putString("pref_quality", v).apply() }
    fun setSkipIntro(ctx: Context, v: String) { skipIntro = v; edit(ctx).putString("pref_skip", v).apply() }
    fun setScrubFrames(ctx: Context, v: Boolean) { scrubFrames = v; edit(ctx).putBoolean("pref_scrubframes", v).apply() }

    /** The short list a picker offers; "" means follow the device. */
    val LANGS = listOf(
        "" to "Device", "en" to "English", "hi" to "Hindi", "ta" to "Tamil", "te" to "Telugu",
        "ml" to "Malayalam", "kn" to "Kannada", "bn" to "Bengali", "mr" to "Marathi",
        "es" to "Spanish", "fr" to "French", "de" to "German", "pt" to "Portuguese",
        "it" to "Italian", "ar" to "Arabic", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
    )
    fun langLabel(code: String) = LANGS.firstOrNull { it.first == code }?.second ?: code
}
