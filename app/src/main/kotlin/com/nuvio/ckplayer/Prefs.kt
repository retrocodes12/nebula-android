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
 * app without a restart. Every key is device-local; new keys only ever ADD.
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

    // ---- the settings console itself ----
    var setMode by mutableStateOf("essential"); private set       // essential (the common rows) / all
    val everything: Boolean get() = setMode == "all"

    // ---- appearance ----
    var accent by mutableStateOf("nebula"); private set
    var surface by mutableStateOf("black"); private set           // black / pure (OLED) / graphite
    var textSize by mutableStateOf("standard"); private set       // compact / standard / large
    val textScale: Float get() = when (textSize) { "compact" -> 0.92f; "large" -> 1.12f; else -> 1f }
    var font by mutableStateOf("geist"); private set              // geist / system
    var focusZoom by mutableStateOf("standard"); private set      // subtle / standard / bold (D-pad focus growth)
    val focusScale: Float get() = when (focusZoom) { "subtle" -> 1.04f; "bold" -> 1.14f; else -> 1.09f }
    var motion by mutableStateOf("full"); private set             // full / reduced
    val reducedMotion: Boolean get() = motion == "reduced"
    var posterScale by mutableStateOf(1.0f); private set          // 0.85 / 1.0 / 1.18 — the card size
    var cardCorners by mutableStateOf("rounded"); private set     // square / rounded / round
    var posterLabels by mutableStateOf("both"); private set       // both (title and year) / title / none
    var ratings by mutableStateOf(true); private set              // the ★ figure on posters

    // ---- home ----
    var showHero by mutableStateOf(true); private set
    var heroSource by mutableStateOf("first"); private set        // first / all / <row key>
    var heroInterval by mutableStateOf(10); private set           // seconds between slides; 0 = never
    var showContinue by mutableStateOf(true); private set
    var cwStyle by mutableStateOf("art"); private set             // art (16:9) / poster
    var cwSort by mutableStateOf("recent"); private set           // recent / az
    var cwUnaired by mutableStateOf(false); private set           // offer an episode that is not out yet
    var rowSubline by mutableStateOf(true); private set           // the add-on eyebrow beside a row title
    var landscapeRows by mutableStateOf(false); private set       // every catalog row as 16:9 art
    var detailCast by mutableStateOf(true); private set
    var detailGenres by mutableStateOf(true); private set

    // ---- playback ----
    var autoPlayNext by mutableStateOf(true); private set         // up-next counts down by itself
    var upnextAt by mutableStateOf("25"); private set             // seconds before the end, or "credits"
    var countdown by mutableStateOf(8); private set               // seconds the up-next card counts
    var stillWatching by mutableStateOf(0); private set           // ask after N autoplayed episodes; 0 = never
    var resume by mutableStateOf("resume"); private set           // resume / ask / startover
    var seekStep by mutableStateOf(10); private set               // seconds: tap zones, D-pad, the transport
    var speedDefault by mutableStateOf("1"); private set          // 1 / 1.25 / 1.5 / last
    var lastSpeed by mutableStateOf(1.0f); private set
    var quality by mutableStateOf("auto"); private set            // auto / high (start high) / saver (≤720p)
    var maxRes by mutableStateOf(0); private set                  // 0 = no cap, else 720 / 1080 / 1440
    var audioLang by mutableStateOf(""); private set              // "" = device language
    var subLang by mutableStateOf(""); private set
    var subLang2 by mutableStateOf(""); private set               // "" = none
    var subStart by mutableStateOf("off"); private set            // off / preferred / always
    var timeDisplay by mutableStateOf("left"); private set        // left / total — the right time pill
    var clock by mutableStateOf(true); private set
    var pauseBoard by mutableStateOf(true); private set
    var controlsHide by mutableStateOf(3.5f); private set         // seconds before the chrome fades
    var skipIntro by mutableStateOf("button"); private set        // button (a pill to press) / auto / off
    var scrubFrames by mutableStateOf(true); private set          // a picture of where a scrub will land
    var holdSpeed by mutableStateOf(true); private set            // hold the player to speed up
    var holdRate by mutableStateOf(2.0f); private set
    var gestures by mutableStateOf(true); private set             // tap-zone skips + swipe seek

    // ---- streams ----
    var streamSort by mutableStateOf("listed"); private set       // listed / quality / size
    var minRes by mutableStateOf("any"); private set              // any / 720 / 1080 / 4k
    var streamFacts by mutableStateOf(true); private set          // size · bitrate · seeds column
    var streamBadges by mutableStateOf(true); private set
    var addonMark by mutableStateOf("initial"); private set       // initial / name / hidden
    var autoPick by mutableStateOf("off"); private set            // off / last (same as last time) / first
    val autoStream: Boolean get() = autoPick != "off"             // the old switch, kept for callers
    var pickWait by mutableStateOf(6); private set                // seconds auto-pick waits for slow add-ons

    // ---- advanced ----
    var welcome by mutableStateOf(true); private set              // the toast at start

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        setMode = p.getString("pref_setmode", "essential") ?: "essential"
        accent = p.getString("pref_accent", "nebula") ?: "nebula"
        surface = p.getString("pref_surface", "black") ?: "black"
        textSize = p.getString("pref_textsize", "standard") ?: "standard"
        font = p.getString("pref_font", "geist") ?: "geist"
        focusZoom = p.getString("pref_zoom", "standard") ?: "standard"
        motion = p.getString("pref_motion", "full") ?: "full"
        posterScale = p.getFloat("pref_poster", 1.0f)
        cardCorners = p.getString("pref_corners", "rounded") ?: "rounded"
        posterLabels = p.getString("pref_labels", "both") ?: "both"
        ratings = p.getBoolean("pref_ratings", true)
        showHero = p.getBoolean("pref_hero", true)
        heroSource = p.getString("pref_herosource", "first") ?: "first"
        heroInterval = p.getInt("pref_herointerval", 10)
        showContinue = p.getBoolean("pref_continue", true)
        cwStyle = p.getString("pref_cwstyle", "art") ?: "art"
        cwSort = p.getString("pref_cwsort", "recent") ?: "recent"
        cwUnaired = p.getBoolean("pref_cwunaired", false)
        rowSubline = p.getBoolean("pref_rowsubline", true)
        landscapeRows = p.getBoolean("pref_landscape", false)
        detailCast = p.getBoolean("pref_detailcast", true)
        detailGenres = p.getBoolean("pref_detailgenres", true)
        autoPlayNext = p.getBoolean("pref_autonext", true)
        upnextAt = p.getString("pref_upnextat", "25") ?: "25"
        countdown = p.getInt("pref_countdown", 8)
        stillWatching = p.getInt("pref_stillwatching", 0)
        resume = p.getString("pref_resume", "resume") ?: "resume"
        seekStep = p.getInt("pref_seekstep", 10)
        speedDefault = p.getString("pref_speeddefault", "1") ?: "1"
        lastSpeed = p.getFloat("pref_lastspeed", 1.0f)
        quality = p.getString("pref_quality", "auto") ?: "auto"
        maxRes = p.getInt("pref_maxres", 0)
        audioLang = p.getString("pref_audiolang", "") ?: ""
        subLang = p.getString("pref_sublang", "") ?: ""
        subLang2 = p.getString("pref_sublang2", "") ?: ""
        // Unset on a device that already chose a subtitle language (pre-1.56): keep subtitles appearing as before.
        subStart = p.getString("pref_substart", null) ?: (if (subLang.isNotEmpty()) "preferred" else "off")
        timeDisplay = p.getString("pref_timedisplay", "left") ?: "left"
        clock = p.getBoolean("pref_clock", true)
        pauseBoard = p.getBoolean("pref_pauseboard", true)
        controlsHide = p.getFloat("pref_controlshide", 3.5f)
        skipIntro = p.getString("pref_skip", "button") ?: "button"
        scrubFrames = p.getBoolean("pref_scrubframes", true)
        holdSpeed = p.getBoolean("pref_holdspeed", true)
        holdRate = p.getFloat("pref_holdrate", 2.0f)
        gestures = p.getBoolean("pref_gestures", true)
        streamSort = p.getString("pref_streamsort", "listed") ?: "listed"
        minRes = p.getString("pref_minres", "any") ?: "any"
        streamFacts = p.getBoolean("pref_streamfacts", true)
        streamBadges = p.getBoolean("pref_streambadges", true)
        addonMark = p.getString("pref_addonmark", "initial") ?: "initial"
        // the old on/off switch becomes "same as last time" once, then the new key is the truth
        autoPick = p.getString("pref_autopick", null) ?: (if (p.getBoolean("pref_autostream", false)) "last" else "off")
        pickWait = p.getInt("pref_pickwait", 6)
        welcome = p.getBoolean("pref_welcome", true)
    }

    private fun edit(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()

    fun setSetMode(ctx: Context, v: String) { setMode = v; edit(ctx).putString("pref_setmode", v).apply() }
    fun setAccent(ctx: Context, v: String) { accent = v; edit(ctx).putString("pref_accent", v).apply() }
    fun setSurface(ctx: Context, v: String) { surface = v; edit(ctx).putString("pref_surface", v).apply() }
    fun setTextSize(ctx: Context, v: String) { textSize = v; edit(ctx).putString("pref_textsize", v).apply() }
    fun setFont(ctx: Context, v: String) { font = v; edit(ctx).putString("pref_font", v).apply() }
    fun setFocusZoom(ctx: Context, v: String) { focusZoom = v; edit(ctx).putString("pref_zoom", v).apply() }
    fun setMotion(ctx: Context, v: String) { motion = v; edit(ctx).putString("pref_motion", v).apply() }
    fun setPosterScale(ctx: Context, v: Float) { posterScale = v; edit(ctx).putFloat("pref_poster", v).apply() }
    fun setCardCorners(ctx: Context, v: String) { cardCorners = v; edit(ctx).putString("pref_corners", v).apply() }
    fun setPosterLabels(ctx: Context, v: String) { posterLabels = v; edit(ctx).putString("pref_labels", v).apply() }
    fun setRatings(ctx: Context, v: Boolean) { ratings = v; edit(ctx).putBoolean("pref_ratings", v).apply() }
    fun setShowHero(ctx: Context, v: Boolean) { showHero = v; edit(ctx).putBoolean("pref_hero", v).apply() }
    fun setHeroSource(ctx: Context, v: String) { heroSource = v; edit(ctx).putString("pref_herosource", v).apply() }
    fun setHeroInterval(ctx: Context, v: Int) { heroInterval = v; edit(ctx).putInt("pref_herointerval", v).apply() }
    fun setShowContinue(ctx: Context, v: Boolean) { showContinue = v; edit(ctx).putBoolean("pref_continue", v).apply() }
    fun setCwStyle(ctx: Context, v: String) { cwStyle = v; edit(ctx).putString("pref_cwstyle", v).apply() }
    fun setCwSort(ctx: Context, v: String) { cwSort = v; edit(ctx).putString("pref_cwsort", v).apply() }
    fun setCwUnaired(ctx: Context, v: Boolean) { cwUnaired = v; edit(ctx).putBoolean("pref_cwunaired", v).apply() }
    fun setRowSubline(ctx: Context, v: Boolean) { rowSubline = v; edit(ctx).putBoolean("pref_rowsubline", v).apply() }
    fun setLandscapeRows(ctx: Context, v: Boolean) { landscapeRows = v; edit(ctx).putBoolean("pref_landscape", v).apply() }
    fun setDetailCast(ctx: Context, v: Boolean) { detailCast = v; edit(ctx).putBoolean("pref_detailcast", v).apply() }
    fun setDetailGenres(ctx: Context, v: Boolean) { detailGenres = v; edit(ctx).putBoolean("pref_detailgenres", v).apply() }
    fun setAutoPlayNext(ctx: Context, v: Boolean) { autoPlayNext = v; edit(ctx).putBoolean("pref_autonext", v).apply() }
    fun setUpnextAt(ctx: Context, v: String) { upnextAt = v; edit(ctx).putString("pref_upnextat", v).apply() }
    fun setCountdown(ctx: Context, v: Int) { countdown = v; edit(ctx).putInt("pref_countdown", v).apply() }
    fun setStillWatching(ctx: Context, v: Int) { stillWatching = v; edit(ctx).putInt("pref_stillwatching", v).apply() }
    fun setResume(ctx: Context, v: String) { resume = v; edit(ctx).putString("pref_resume", v).apply() }
    fun setSeekStep(ctx: Context, v: Int) { seekStep = v; edit(ctx).putInt("pref_seekstep", v).apply() }
    fun setSpeedDefault(ctx: Context, v: String) { speedDefault = v; edit(ctx).putString("pref_speeddefault", v).apply() }
    fun setLastSpeed(ctx: Context, v: Float) { lastSpeed = v; edit(ctx).putFloat("pref_lastspeed", v).apply() }
    fun setQuality(ctx: Context, v: String) { quality = v; edit(ctx).putString("pref_quality", v).apply() }
    fun setMaxRes(ctx: Context, v: Int) { maxRes = v; edit(ctx).putInt("pref_maxres", v).apply() }
    fun setAudioLang(ctx: Context, v: String) { audioLang = v; edit(ctx).putString("pref_audiolang", v).apply() }
    fun setSubLang(ctx: Context, v: String) { subLang = v; edit(ctx).putString("pref_sublang", v).apply() }
    fun setSubLang2(ctx: Context, v: String) { subLang2 = v; edit(ctx).putString("pref_sublang2", v).apply() }
    fun setSubStart(ctx: Context, v: String) { subStart = v; edit(ctx).putString("pref_substart", v).apply() }
    fun setTimeDisplay(ctx: Context, v: String) { timeDisplay = v; edit(ctx).putString("pref_timedisplay", v).apply() }
    fun setClock(ctx: Context, v: Boolean) { clock = v; edit(ctx).putBoolean("pref_clock", v).apply() }
    fun setPauseBoard(ctx: Context, v: Boolean) { pauseBoard = v; edit(ctx).putBoolean("pref_pauseboard", v).apply() }
    fun setControlsHide(ctx: Context, v: Float) { controlsHide = v; edit(ctx).putFloat("pref_controlshide", v).apply() }
    fun setSkipIntro(ctx: Context, v: String) { skipIntro = v; edit(ctx).putString("pref_skip", v).apply() }
    fun setScrubFrames(ctx: Context, v: Boolean) { scrubFrames = v; edit(ctx).putBoolean("pref_scrubframes", v).apply() }
    fun setHoldSpeed(ctx: Context, v: Boolean) { holdSpeed = v; edit(ctx).putBoolean("pref_holdspeed", v).apply() }
    fun setHoldRate(ctx: Context, v: Float) { holdRate = v; edit(ctx).putFloat("pref_holdrate", v).apply() }
    fun setGestures(ctx: Context, v: Boolean) { gestures = v; edit(ctx).putBoolean("pref_gestures", v).apply() }
    fun setStreamSort(ctx: Context, v: String) { streamSort = v; edit(ctx).putString("pref_streamsort", v).apply() }
    fun setMinRes(ctx: Context, v: String) { minRes = v; edit(ctx).putString("pref_minres", v).apply() }
    fun setStreamFacts(ctx: Context, v: Boolean) { streamFacts = v; edit(ctx).putBoolean("pref_streamfacts", v).apply() }
    fun setStreamBadges(ctx: Context, v: Boolean) { streamBadges = v; edit(ctx).putBoolean("pref_streambadges", v).apply() }
    fun setAddonMark(ctx: Context, v: String) { addonMark = v; edit(ctx).putString("pref_addonmark", v).apply() }
    /** Writes the old switch too, so nothing that still reads `pref_autostream` is surprised. */
    fun setAutoPick(ctx: Context, v: String) {
        autoPick = v
        edit(ctx).putString("pref_autopick", v).putBoolean("pref_autostream", v != "off").apply()
    }
    fun setAutoStream(ctx: Context, v: Boolean) = setAutoPick(ctx, if (v) "last" else "off")
    fun setPickWait(ctx: Context, v: Int) { pickWait = v; edit(ctx).putInt("pref_pickwait", v).apply() }
    fun setWelcome(ctx: Context, v: Boolean) { welcome = v; edit(ctx).putBoolean("pref_welcome", v).apply() }

    /**
     * Reset all settings: every `pref_*` key, the subtitle style, the Home arrangement
     * and the party name. Add-ons, progress, My List, ratings and the profile stay.
     */
    fun resetAll(ctx: Context) {
        val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val e = p.edit()
        p.all.keys.filter { it.startsWith("pref_") }.forEach { e.remove(it) }
        listOf("sub_style", "sub_style_at", "home_rowvis", "home_roworder", "party_name").forEach { e.remove(it) }
        e.apply()
        load(ctx)
    }

    /** The short list a picker offers; "" means follow the device. */
    val LANGS = listOf(
        "" to "Device", "en" to "English", "hi" to "Hindi", "ta" to "Tamil", "te" to "Telugu",
        "ml" to "Malayalam", "kn" to "Kannada", "bn" to "Bengali", "mr" to "Marathi",
        "es" to "Spanish", "fr" to "French", "de" to "German", "pt" to "Portuguese",
        "it" to "Italian", "ar" to "Arabic", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
    )
    /** The same list with "None" in front — for a second choice that may be left empty. */
    val LANGS_NONE = listOf("" to "None") + LANGS.drop(1)
    fun langLabel(code: String) = LANGS.firstOrNull { it.first == code }?.second ?: code
}
