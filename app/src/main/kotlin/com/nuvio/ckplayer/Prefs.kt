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

    /** Three more, for supporters (Support.kt). Same shape, locked until the mark is there. */
    val SUP_ACCENTS = listOf(
        Triple("gold", "Gold", Color(0xFFE0B24A)),
        Triple("ice", "Ice", Color(0xFF64D2FF)),
        Triple("mint", "Mint", Color(0xFF66D4CF)),
    )
    /** Six more for Supporter Plus (tiers, 2026-09-19) — and any colour at all as a `#RRGGBB` key. */
    val PLUS_ACCENTS = listOf(
        Triple("coral", "Coral", Color(0xFFFF7A5C)),
        Triple("lavender", "Lavender", Color(0xFFB08CFF)),
        Triple("lime", "Lime", Color(0xFFB4E33D)),
        Triple("sky", "Sky", Color(0xFF5AC8FA)),
        Triple("peach", "Peach", Color(0xFFFFB07A)),
        Triple("slate", "Slate", Color(0xFF8E9AAF)),
    )
    /** Light accents need dark ink on top of them; every other one carries white. */
    private val DARK_INK = setOf("white", "gold", "ice", "mint", "lime", "sky", "peach")
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

    /** Which tier a stored accent needs: 0 anyone, 1 the supporter colours, 2 the Plus palette or a colour of one's own. */
    internal fun accentRank(key: String): Int = when {
        ACCENTS.any { it.first == key } -> 0
        SUP_ACCENTS.any { it.first == key } -> 1
        else -> 2
    }

    /**
     * The accent actually in force. A colour above the profile's tier (signed out, revoked)
     * falls back to the default WITHOUT the stored pref being rewritten, so signing back in
     * brings the chosen colour straight back.
     */
    internal val activeAccent: String
        get() = if (accentRank(accent) > Support.rank()) ACCENTS[0].first else accent

    private fun colorOf(key: String): Color? =
        (ACCENTS + SUP_ACCENTS + PLUS_ACCENTS).firstOrNull { it.first == key }?.third
            ?: if (HEX.matches(key)) Color(0xFF000000L or key.substring(1).toLong(16)) else null

    // Buttons, chips and badges all draw from this pair.
    val accentColor: Color get() = colorOf(activeAccent) ?: ACCENTS[0].third
    val onAccent: Color
        get() {
            val key = activeAccent
            if (key in DARK_INK) return Color(0xFF111114)
            val c = colorOf(key) ?: return Color.White
            // a colour of one's own: dark ink on a light one
            val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            return if (!HEX.matches(key) || lum < 0.6f) Color.White else Color(0xFF111114)
        }

    // ---- the settings console itself ----
    var setMode by mutableStateOf("essential"); private set       // essential (the common rows) / all
    // ---- Search › Discover: the last picked type / catalog key / genre, so the grid reopens where it was ----
    var discType by mutableStateOf(""); private set
    var discCatalog by mutableStateOf(""); private set               // manifestUrl|type|id, "" = first available
    var discGenre by mutableStateOf(""); private set                 // "" = All genres
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
    var buffer by mutableStateOf(0); private set                  // seconds loaded ahead: 0 = the engine's own 50 s, else 60 / 120 / 240
    var relay by mutableStateOf("auto"); private set              // TV only: auto / off — play through a computer sharing on the network (Relay.kt)
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
    var slowMark by mutableStateOf("move"); private set           // move / mark / off — rows this connection cannot carry
    // what this device measured its connection to be, in bits per second, while something played (0 = nothing yet);
    // a measurement, not a choice — it lives outside the pref_ keys so Reset all settings leaves it alone
    var bw by mutableStateOf(0L); private set
    // P2P (P2p.kt): off until someone turns it on — BitTorrent shows your address to the whole swarm
    var p2p by mutableStateOf(false); private set                 // list and play torrent streams
    var p2pKeep by mutableStateOf(false); private set             // leave a download on the phone after watching
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
        buffer = p.getInt("pref_buffer", 0)
        relay = p.getString("pref_relay", "auto") ?: "auto"
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
        slowMark = p.getString("pref_slowmark", "move") ?: "move"
        bw = if (System.currentTimeMillis() - p.getLong("bw_at", 0L) < 30L * 86_400_000L) p.getLong("bw_bps", 0L) else 0L
        // the old on/off switch becomes "same as last time" once, then the new key is the truth
        p2p = p.getBoolean("pref_p2p", false)
        p2pKeep = p.getBoolean("pref_p2pkeep", false)
        autoPick = p.getString("pref_autopick", null) ?: (if (p.getBoolean("pref_autostream", false)) "last" else "off")
        pickWait = p.getInt("pref_pickwait", 6)
        welcome = p.getBoolean("pref_welcome", true)
        discType = p.getString("pref_disctype", "") ?: ""
        discCatalog = p.getString("pref_disccat", "") ?: ""
        discGenre = p.getString("pref_discgenre", "") ?: ""
    }

    private fun edit(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()

    fun setSetMode(ctx: Context, v: String) { setMode = v; edit(ctx).putString("pref_setmode", v).apply() }
    fun setDiscover(ctx: Context, type: String, catalog: String, genre: String) {
        discType = type; discCatalog = catalog; discGenre = genre
        edit(ctx).putString("pref_disctype", type).putString("pref_disccat", catalog).putString("pref_discgenre", genre).apply()
    }
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
    fun setBuffer(ctx: Context, v: Int) { buffer = v; edit(ctx).putInt("pref_buffer", v).apply() }
    fun setRelay(ctx: Context, v: String) { relay = v; edit(ctx).putString("pref_relay", v).apply() }
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
    fun setSlowMark(ctx: Context, v: String) { slowMark = v; edit(ctx).putString("pref_slowmark", v).apply() }
    /** One sample of the engine's bandwidth estimate: weighted toward the newest play, never a single reading. */
    fun noteBandwidth(ctx: Context, bps: Long) {
        val v = if (bw > 0) (bw * 0.6 + bps * 0.4).toLong() else bps
        bw = v
        edit(ctx).putLong("bw_bps", v).putLong("bw_at", System.currentTimeMillis()).apply()
    }
    fun setP2p(ctx: Context, v: Boolean) { p2p = v; edit(ctx).putBoolean("pref_p2p", v).apply() }
    fun setP2pKeep(ctx: Context, v: Boolean) { p2pKeep = v; edit(ctx).putBoolean("pref_p2pkeep", v).apply() }
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

    /**
     * The list a picker offers; "" means follow the device.
     *
     * It was seventeen languages and it was asked to be longer (`nebula-android#1`, "can you add
     * more subtitle languages for autoload") — the first seventeen covered India and the biggest
     * five of Europe and nothing else, so most of Europe could not name its own language here.
     * Order is the eye's, not the alphabet's: Device first, then English, then the rest grouped
     * by region, because a picker you scroll is read in blocks. Codes are ISO 639-1, which is
     * what an add-on's subtitle `lang` field carries and what [langLabel] matches on.
     *
     * The shared player's PREF_LANGS is the SAME list in the same order, on purpose — the
     * preference syncs between surfaces, so a code one of them cannot name is a setting that
     * reads as broken on the other.
     */
    val LANGS = listOf(
        "" to "Device", "en" to "English",
        // Europe — west and north
        "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
        "pt" to "Portuguese", "nl" to "Dutch", "sv" to "Swedish", "da" to "Danish",
        "no" to "Norwegian", "fi" to "Finnish", "is" to "Icelandic",
        // Europe — central, east and south
        "pl" to "Polish", "cs" to "Czech", "sk" to "Slovak", "hu" to "Hungarian",
        "ro" to "Romanian", "bg" to "Bulgarian", "el" to "Greek", "hr" to "Croatian",
        "sr" to "Serbian", "sl" to "Slovenian", "et" to "Estonian", "lv" to "Latvian",
        "lt" to "Lithuanian", "ru" to "Russian", "uk" to "Ukrainian", "tr" to "Turkish",
        // Middle East
        "ar" to "Arabic", "he" to "Hebrew", "fa" to "Persian",
        // South Asia
        "hi" to "Hindi", "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam",
        "kn" to "Kannada", "bn" to "Bengali", "mr" to "Marathi", "gu" to "Gujarati",
        "pa" to "Punjabi", "ur" to "Urdu",
        // East and South-East Asia
        "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "th" to "Thai",
        "vi" to "Vietnamese", "id" to "Indonesian", "ms" to "Malay", "tl" to "Filipino",
        // elsewhere
        "sw" to "Swahili",
    )
    /** The same list with "None" in front — for a second choice that may be left empty. */
    val LANGS_NONE = listOf("" to "None") + LANGS.drop(1)
    fun langLabel(code: String) = LANGS.firstOrNull { it.first == code }?.second ?: code
}
