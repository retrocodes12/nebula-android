@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.onClick as a11yClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TrackSelectionDialogBuilder
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Is the remote's OK physically down right now? The Activity's own dispatch sees
 * the press that starts a long-press regardless of what is focused, which is the
 * thing a Compose key modifier cannot do — it only ever sees keys aimed at a
 * focused descendant, and a sheet that has just opened has nothing focused yet.
 * [CardSheet] reads this to know whether the press that opened it is still held.
 */
internal object KeyWatch {
    var okDown by mutableStateOf(false)
        private set
    /** When a navigation key last went down, on the uptime clock — "has the viewer moved on this page yet?"
        Volume and media keys do not count, as on the web (Tab, Enter and the arrows). */
    var lastDownAt = 0L
        private set
    /** When a finger last went down, on the same clock — the web's lastPointerAt: a hand on the list is choosing too. */
    var lastTouchAt = 0L
        private set

    fun noteTouch(event: android.view.MotionEvent) {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) lastTouchAt = event.eventTime
    }

    fun note(event: android.view.KeyEvent) {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && (event.keyCode == android.view.KeyEvent.KEYCODE_TAB ||
                event.keyCode == android.view.KeyEvent.KEYCODE_ENTER || event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                event.keyCode in android.view.KeyEvent.KEYCODE_DPAD_UP..android.view.KeyEvent.KEYCODE_DPAD_CENTER)) lastDownAt = event.eventTime
        val ok = event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
            event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!ok) return
        if (event.action == android.view.KeyEvent.ACTION_DOWN) okDown = true
        else if (event.action == android.view.KeyEvent.ACTION_UP) okDown = false
    }
}

/**
 * The player's remote (issue #1: "options only show at the beginning, then press up, down, left and right,
 * nothing"). The controls fade after a few seconds, and the button that had focus goes with them — so nothing is
 * focused, and a Compose key modifier only ever hears keys aimed at a focused node. Nothing heard the press
 * that should have brought them back. The Activity's own dispatch hears every key whatever is focused, which
 * is what the web player's document listener does; PlayerScreen installs its handler here while it is up.
 */
internal object PlayerKeys {
    @Volatile var handler: ((android.view.KeyEvent) -> Boolean)? = null
}

/** A remote's other buttons: each shows the player's controls (the web wakes them on any key) and still does its own job. */
private val REMOTE_WAKE_KEYS = setOf(
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, android.view.KeyEvent.KEYCODE_MEDIA_STOP,
    android.view.KeyEvent.KEYCODE_MEDIA_REWIND, android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    android.view.KeyEvent.KEYCODE_MEDIA_NEXT, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
    android.view.KeyEvent.KEYCODE_MEDIA_STEP_FORWARD, android.view.KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
    android.view.KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, android.view.KeyEvent.KEYCODE_CAPTIONS,
    android.view.KeyEvent.KEYCODE_MENU, android.view.KeyEvent.KEYCODE_INFO,
)

class MainActivity : ComponentActivity() {
    private val pendingPlay = mutableStateOf<PlayReq?>(null)

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        KeyWatch.note(event)
        if (PlayerKeys.handler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        KeyWatch.noteTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        Cloud.load(this)
        Support.restore(this)
        Social.load(this)
        // only honor the launch intent on a fresh start — a recreated activity
        // (process restore, config change) must not jump back into the player
        pendingPlay.value = if (savedInstanceState == null) parsePlayIntent(intent) else null
        setContent { AppRoot(pendingPlay.value) { pendingPlay.value = null } }
    }

    // singleTop: a deep link while the app is already open arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parsePlayIntent(intent)?.let { pendingPlay.value = it }
    }

    // nebula://play?mpd=<manifest url>&t=<title>
    private fun parsePlayIntent(intent: Intent?): PlayReq? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (!data.scheme.equals("nebula", ignoreCase = true)) return null
        val mpd = data.getQueryParameter("mpd")?.trim().orEmpty()
        if (mpd.isEmpty()) return null
        // any web page can fire this link: only a web address may come through it, never file:// or content://
        // (which would have the player read this app's own files, or another app's, on a stranger's say-so)
        val scheme = runCatching { Uri.parse(mpd).scheme }.getOrNull()?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val title = (data.getQueryParameter("t") ?: data.getQueryParameter("title") ?: "Nebula Sports").trim()
        return PlayReq(mpd, title)
    }

    // ---------- picture-in-picture ----------
    fun pipSupported(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipAspect(): Rational {
        val vs = activePipPlayer.value?.videoSize
        var w = vs?.width ?: 0
        var h = vs?.height ?: 0
        if (w <= 0 || h <= 0) { w = 16; h = 9 }
        // Android rejects PiP aspect ratios outside roughly 1:2.39 – 2.39:1
        val ratio = w.toFloat() / h
        return when {
            ratio > 2.35f -> Rational(235, 100)
            ratio < 0.43f -> Rational(43, 100)
            else -> Rational(w, h)
        }
    }

    fun enterPip(): Boolean {
        if (!pipSupported() || activePipPlayer.value == null) return false
        return runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(pipAspect()).build()
            )
        }.getOrDefault(false)
    }

    /** Keep the OS-level PiP params current: real aspect ratio + (API 31+) auto-enter on Home while playing. */
    fun refreshPipParams() {
        if (!pipSupported()) return
        runCatching {
            val b = PictureInPictureParams.Builder().setAspectRatio(pipAspect())
            if (Build.VERSION.SDK_INT >= 31) {
                b.setAutoEnterEnabled(activePipPlayer.value?.isPlaying == true)
            }
            setPictureInPictureParams(b.build())
        }
    }

    // Home press while a video plays → keep it going in a floating window.
    // API 31+ auto-enters via setAutoEnterEnabled; this covers API 26–30.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && activePipPlayer.value?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipMode.value = isInPictureInPictureMode
        // Left PiP while the activity stayed stopped = the floating window was dismissed
        // (not expanded back to full screen) — pause instead of playing on invisibly.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            activePipPlayer.value?.pause()
        }
    }
}

/** The ExoPlayer currently on screen (wired by PlayerScreen) so the activity can drive PiP. */
private val activePipPlayer = mutableStateOf<ExoPlayer?>(null)

/** True while the app is in picture-in-picture; PlayerScreen hides all chrome. */
private val inPipMode = mutableStateOf(false)

// ---------- Nebula palette (matches the web/webOS player: flat, restrained) ----------
// Editorial palette: warm cream ink on near-black, hairlines for structure,
// red kept to the mark and to progress. Mirrors the shared HTML player.
// The accent is a getter over Prefs state, so picking a new one in Settings
// recomposes everything that wears it — no restart, no plumbing.
internal val Red: Color get() = Prefs.accentColor
internal val OnAccent: Color get() = Prefs.onAccent
// Surface (Settings › Appearance): Black is today's palette; Pure black turns every panel off for
// OLED and leaves the hairlines to draw them; Graphite lifts the ground and the panels a step.
internal val Bg: Color get() = when (Prefs.surface) { "graphite" -> Color(0xFF0B0B0E); else -> Color(0xFF000000) }
internal val SurfaceC: Color get() = when (Prefs.surface) {          // secondary system background
    "pure" -> Color(0xFF000000); "graphite" -> Color(0xFF141418); else -> Color(0xFF1C1C1E)
}
internal val Surface2: Color get() = when (Prefs.surface) {          // tertiary
    "pure" -> Color(0xFF121214); "graphite" -> Color(0xFF1C1C22); else -> Color(0xFF2C2C2E)
}
internal val LineC = Color(0x1AFFFFFF)        // hairline separator
internal val Line2 = Color(0x29FFFFFF)
internal val MutedC = Color(0x99EBEBF5)       // secondary label
internal val FaintC = Color(0x4DEBEBF5)       // tertiary label
private val FillC: Color get() = if (Prefs.surface == "pure") Color(0x2E767680) else Color(0x3D767680)   // control fill
internal val TextC = Color(0xFFFFFFFF)

// Three registers and nothing between: a display serif for titles, one
// grotesque for the interface, a mono for every number and label.
private val Geist = FontFamily(Font(R.font.geist))
/** The interface face: Geist, or the device's own when Settings › Appearance says System. */
internal val Sans: FontFamily get() = if (Prefs.font == "system") FontFamily.Default else Geist
internal val Mono = FontFamily(Font(R.font.geistmono))

/** Everything unstyled falls back to the interface grotesque, not the system face. */
private val NebulaTypography: Typography get() = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Sans),
        displayMedium = displayMedium.copy(fontFamily = Sans),
        displaySmall = displaySmall.copy(fontFamily = Sans),
        headlineLarge = headlineLarge.copy(fontFamily = Sans),
        headlineMedium = headlineMedium.copy(fontFamily = Sans),
        headlineSmall = headlineSmall.copy(fontFamily = Sans),
        titleLarge = titleLarge.copy(fontFamily = Sans),
        titleMedium = titleMedium.copy(fontFamily = Sans),
        titleSmall = titleSmall.copy(fontFamily = Sans),
        bodyLarge = bodyLarge.copy(fontFamily = Sans),
        bodyMedium = bodyMedium.copy(fontFamily = Sans),
        bodySmall = bodySmall.copy(fontFamily = Sans),
        labelLarge = labelLarge.copy(fontFamily = Sans),
        labelMedium = labelMedium.copy(fontFamily = Sans),
        labelSmall = labelSmall.copy(fontFamily = Sans),
    )
}

/** Secondary label: same family, lighter colour, tight tracking. */
internal fun labelStyle(size: Int = 13, color: Color = MutedC) = TextStyle(
    fontFamily = Sans,
    fontSize = size.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = (-0.01 * size).sp,
    color = color,
)

// a getter, so the scheme follows the accent pref
private val DarkColors get() = darkColorScheme(
    primary = Red,
    background = Bg,
    surface = SurfaceC,
    onPrimary = OnAccent,
    onBackground = TextC,
    onSurface = TextC,
)

private sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object Addons : Screen
    data object Settings : Screen
    data object SettingsSubtitles : Screen
    data object SettingsLayout : Screen
    data object SettingsHome : Screen          // the rows manager
    data object SettingsHomeOpts : Screen      // Settings › Home
    data object SettingsStreams : Screen
    data object SettingsAdvanced : Screen
    data object Friends : Screen
    data object SettingsPlayback : Screen
    data object Profile : Screen
    data object SettingsParty : Screen
    data object SettingsSupport : Screen
    data class Detail(val addon: Addon, val item: MetaItem) : Screen
    data class Catalog(val addon: Addon, val initial: CatalogRef? = null) : Screen
    data class Episodes(val addon: Addon, val item: MetaItem) : Screen
    // decided: the viewer already chose Resume or Start over (a sheet), so "When you come back · Ask" asks nothing more
    data class Streams(val addon: Addon, val item: MetaItem, val startOver: Boolean = false, val decided: Boolean = false) : Screen
    data class Play(
        val url: String,
        val title: String,
        val subs: List<SubTrack> = emptyList(),
        // What is being played, for resume + Continue watching. Null for a deep
        // link or a party stream: nothing to resume, nothing worth remembering.
        val type: String? = null,
        val id: String? = null,
        val contentName: String? = null,
        val poster: String? = null,
        val addonUrl: String? = null,
        val description: String? = null,   // the synopsis the pause board shows
        val startOver: Boolean = false,     // skip the resume point this once
        val sourceLine: String? = null,     // "1080p · Torrentio": the chrome's source line, from the picked stream
        val startAtMs: Long = -1L,          // a source swap mid-play: the exact second to start from, under the resume floor or not
    ) : Screen
}

/**
 * The episode list the player walks for "next episode". File-level (like
 * partyUi) because only the top of the nav stack is composed, so it cannot live
 * inside the episode picker that produced it.
 */
private class SeriesChain {
    var type: String = "series"
    var name: String = ""
    var addon: Addon? = null
    var episodes: List<Episode> = emptyList()
    var index: Int = -1
    fun set(type: String, name: String, addon: Addon?, vids: List<Episode>) {
        this.type = type
        this.name = name
        this.addon = addon
        // playing order: seasons ascending, specials (0) last
        episodes = vids.sortedWith(compareBy({ it.season == 0 }, { it.season }, { it.episode ?: 0 }))
        index = -1
    }
    /** The episode after the current one — unless it is not out yet and Settings › Home hides those. */
    fun next(): Episode? {
        val n = if (index >= 0) episodes.getOrNull(index + 1) else null
        if (n != null && !Prefs.cwUnaired && isUnaired(n)) return null
        return n
    }
    fun label(ep: Episode): String =
        (if (name.isNotEmpty()) "$name · " else "") +
            "S${ep.season}" + (ep.episode?.let { "E$it" } ?: "") +
            (if (ep.name.isNotEmpty()) " · ${ep.name}" else "")
    fun clear() { episodes = emptyList(); index = -1; name = ""; addon = null }
}
private val seriesChain = SeriesChain()
/** An episode whose release date is still ahead of today. Undated episodes count as out. */
internal fun isUnaired(ep: Episode): Boolean {
    val d = ep.released?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() } ?: return false
    return d.isAfter(java.time.LocalDate.now())
}
/** The card radius Settings › Appearance chose: Square 4 · Rounded 12 · Round 18 (dp). */
internal fun cardRadius(): Int = when (Prefs.cardCorners) { "square" -> 4; "round" -> 18; else -> 12 }
internal fun cardShape() = RoundedCornerShape(cardRadius().dp)
// which item auto stream selection already fired for (survives the screen)
private var autoPlayedFor: String? = null

/** A protected stream's licence address per manifest address, for the session (thirty minutes, in case it carries a
    token): reading the manifest for it is a whole extra round trip before the player can start. */
private val licenceCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
private suspend fun licenceUrlFor(mpd: String): String? {
    licenceCache[mpd]?.let { (u, at) -> if (System.currentTimeMillis() - at < 1_800_000) return u }
    return Stremio.resolveClearKeyLicenseUri(mpd)?.also { licenceCache[mpd] = it to System.currentTimeMillis() }
}

/** A streams page's answers, kept for Back (StreamsScreen). */
private class StreamsMemo(val at: Long, val sections: List<Pair<Addon, List<StreamItem>>>, val status: String, val usual: String?)
private val streamsMemo = HashMap<String, StreamsMemo>()

/** One catalog's worth of content, tagged with where it came from and, on
    Home, where it sits (see [HomeRows.orderIndex]). */
private class CatRow(val addon: Addon, val catalog: CatalogRef, val items: List<MetaItem>, val oi: Int = 0)

/** Session cache of addon manifests (Home and Search both need them). Concurrent: the streams page,
    the subtitle search and the stall watchdog each ask every add-on at once. */
internal val manifestCache = java.util.concurrent.ConcurrentHashMap<String, ManifestInfo>()
internal suspend fun manifestFor(url: String): ManifestInfo =
    manifestCache[url] ?: Stremio.loadManifest(url).also { manifestCache[url] = it }

/** A title page's chosen season and list place per stack entry, for Back (the page leaves composition). */
private val keptSeasons = HashMap<Any, Int>()
private val keptDetailPlaces = HashMap<Any, Pair<Int, Int>>()

/** Session cache of full metas (the detail page enhances from these). */
private val metaFullCache = mutableMapOf<String, FullMeta>()
/** Settings › Advanced › Clear cached artwork and add-on lists: the manifests and the title pages. */
internal fun clearContentCaches() { manifestCache.clear(); metaFullCache.clear() }

/** The newest still-in-progress episode of a series. Episode ids are
    "<seriesId>:…" where seriesId itself may contain colons (kitsu:12345),
    so membership is a prefix test — never a split on the first colon. */
private fun seriesResumeRec(ctx: Context, seriesId: String): ProgressRec? =
    Progress.all(ctx).values.filter {
        !it.done && !it.dismissed && it.type == "series" &&
            it.id.startsWith("$seriesId:") &&
            it.pos >= Progress.MIN_POS_MS && it.dur > 0 && it.pos <= it.dur - Progress.END_GAP_MS
    }.maxByOrNull { it.at }

/** An episode's air date as "23 Jun 2022", or null when it has none or it will not parse.
    One copy: the row and the sheet must never disagree about a date. */
private fun epAirDate(ep: Episode): String? = ep.released?.let {
    runCatching {
        java.time.LocalDate.parse(it.take(10))
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.US))
    }.getOrNull()
}

/** How an episode was asked for: a plain row tap decides nothing, a sheet row does. */
private enum class PlayIntent { TAP, RESUME, START_OVER }

/**
 * Where the viewer is in a series. [upNext] is the episode that wears the Up next
 * ring — null when there is nothing left — and [seat] the episode whose SEASON the
 * page should open on, which still exists when the ring does not.
 *
 * Two kinds of record, read differently on purpose. Something PLAYED is a fact
 * about now, so the newest one says where the viewer is. A mark made by HAND says
 * "I saw THIS one" and nothing more — never "I saw everything up to here" — so all
 * it may do is push the cursor PAST episodes already ticked off, one at a time.
 * Tick something far ahead and it paints a tick and leaves your place alone; tick
 * the episode you were about to watch and the cursor steps over it. Reading a mark
 * as a position is what used to drag the cursor back to season 1, and reading the
 * furthest mark as a position is what used to teleport it past everything
 * unwatched in between. Mirrors the shared player's seriesCursor().
 */
internal data class SeriesCursor(val upNext: Episode?, val seat: Episode)

private fun seriesCursor(ctx: Context, type: String, videos: List<Episode>): SeriesCursor? {
    val all = Progress.all(ctx)
    return seriesCursorOf(videos) { id -> all[Progress.key(type, id)] }
}

/** [seriesCursor] over any record lookup (an episode id → its record), so the rules run on plain data in tests. */
internal fun seriesCursorOf(videos: List<Episode>, recOf: (String) -> ProgressRec?): SeriesCursor? {
    val flat = videos.sortedWith(compareBy({ it.season == 0 }, { it.season }, { it.episode ?: 0 }))
    if (flat.isEmpty()) return null
    val last = flat.size - 1
    var played: ProgressRec? = null
    var playedIdx = -1
    var anyHand = false
    var inSpecials = false
    flat.forEachIndexed { i, e ->
        val r = recOf(e.id) ?: return@forEachIndexed
        if (r.dismissed) return@forEachIndexed
        if (e.season == 0) inSpecials = true            // this viewer does watch the extras
        if (r.hand) { if (r.done) anyHand = true; return@forEachIndexed }
        if (!r.done && !(r.pos >= Progress.MIN_POS_MS && r.dur > 0)) return@forEachIndexed
        val cur = played
        if (cur == null || r.at > cur.at) { played = r; playedIdx = i }
    }
    if (playedIdx < 0 && !anyHand) return null
    // Specials sit at the END of flat — the sort puts season 0 last on purpose — so a
    // viewer who finishes the regular run walks straight into them and gets told a
    // behind-the-scenes extra is what to watch next.
    var lastReal = last
    while (lastReal > 0 && flat[lastReal].season == 0) lastReal--
    val hasReal = flat[lastReal].season != 0
    val seat = if (hasReal) lastReal else last
    var i = if (playedIdx < 0) 0 else if (played?.done == true) playedIdx + 1 else playedIdx
    while (i <= last) {
        val r = recOf(flat[i].id)
        if (r == null || !r.hand || !r.done) break
        i++
    }
    // nothing left to watch: no ring, but the season of the last real episode is still
    // where the viewer belongs. A viewer who HAS touched the specials keeps them in the
    // chain; one who never has is simply finished.
    if (i > last || (hasReal && !inSpecials && flat[i].season == 0)) return SeriesCursor(null, flat[seat])
    return SeriesCursor(flat[i], flat[i])
}

/** Just the ring target — null when the show is finished or nothing is started. */
private fun seriesUpNext(ctx: Context, type: String, videos: List<Episode>): Episode? =
    seriesCursor(ctx, type, videos)?.upNext

/** "Resume S2E4" from the id tail past the series prefix; kitsu-style single
    tails become "Resume E3"; anything else is plain "Resume". */
private fun resumeLabel(seriesId: String, epId: String): String {
    val rest = epId.removePrefix("$seriesId:").split(":")
    return when {
        rest.size == 2 -> "Resume S${rest[0]}E${rest[1]}"
        rest.size == 1 && rest[0].isNotEmpty() -> "Resume E${rest[0]}"
        else -> "Resume"
    }
}

/** Home tab state, hoisted to AppRoot so rows survive navigating into a stream. */
private class HomeUiState {
    var rows by mutableStateOf<List<CatRow>>(emptyList())
    var loading by mutableStateOf(false)
    var hasAddons by mutableStateOf(true)
    var hidden by mutableStateOf(0)          // rows switched off by hand — an empty Home is not an outage
    var wanted by mutableStateOf(0)          // rows Home asked for
    var refreshKey by mutableStateOf(0)
    var sig: String? = null
    var builtAt = 0L
    val listState = LazyListState()
    // each row's sideways place, kept like the column's: a card opened from the seventh place is still there on Back
    val rowStates = HashMap<String, LazyListState>()
    // Continue watching is read straight from local storage, so it paints
    // instantly and survives every add-on being unreachable.
    var continueRows by mutableStateOf<List<ProgressRec>>(emptyList())
    var continueKey by mutableStateOf(0)
    fun invalidate() { sig = null; refreshKey++ }
    fun invalidateContinue() { continueKey++ }
}

/** Search tab state, hoisted for the same reason. */
private class SearchUiState {
    val discover = DiscoverUiState()
    var query by mutableStateOf("")
    var submitted by mutableStateOf("")
    var sections by mutableStateOf<List<CatRow>>(emptyList())
    var searching by mutableStateOf(false)
    var searchedFor: String? = null
    val rowStates = HashMap<String, LazyListState>()          // each result row's sideways place, for Back
    val listState = LazyListState()
}

/**
 * Catalog screen state, hoisted to AppRoot: only the top of the nav stack is
 * composed, so anything remembered inside CatalogScreen dies the moment a
 * stream screen is pushed — search results, picked genre, and scroll position
 * were all lost on Back. Held here they survive until the catalog is popped.
 */
private class CatalogUiState {
    var catalogs by mutableStateOf<List<CatalogRef>>(emptyList())
    var current by mutableStateOf<CatalogRef?>(null)
    var genre by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var submitted by mutableStateOf("")
    var items by mutableStateOf<List<MetaItem>>(emptyList())
    var loading by mutableStateOf(true)
    var status by mutableStateOf("Loading…")
    // what (catalog, genre, query) the current items were fetched for; null after a failure so re-entering retries
    var loadedFor: Triple<CatalogRef?, String?, String>? = null
    // paging: how many the add-on has handed over, and whether there is more
    var fetched by mutableStateOf(0)
    var pageDone by mutableStateOf(false)
    var paging by mutableStateOf(false)
    // the See-all target already applied, so re-entering doesn't reset a user's catalog switch
    var appliedInitial: CatalogRef? = null
    val gridState = LazyGridState()
}

/** A play request arriving from a nebula://play deep link. */
data class PlayReq(val mpd: String, val title: String)

/**
 * Watch-party UI state, file-level (like manifestCache) so it survives the nav
 * stack: AppRoot owns the session + events, PlayerScreen drives sync through it.
 */
private class PartyUi {
    var session: PartySession? = null
    var code by mutableStateOf<String?>(null)
    var isHost by mutableStateOf(false)
    var count by mutableStateOf(1)
    var names by mutableStateOf<List<String>>(emptyList())
    val reactions = androidx.compose.runtime.mutableStateListOf<Triple<Long, String, String>>()
    fun react(emoji: String, name: String) {
        reactions.add(Triple(System.nanoTime(), emoji, name))
        if (reactions.size > 8) reactions.removeAt(0)
    }
    var status by mutableStateOf<String?>(null)
    @Volatile var lastState: PartyState? = null
    var lastSeekAt = 0L
    fun active() = code != null
    fun reset() {
        session?.leave(); session = null
        code = null; isHost = false; count = 1; lastState = null; names = emptyList()
    }
}
private val partyUi = PartyUi()

// ---------- add-on persistence ----------
private const val PREFS = "ckplayer"
internal fun loadAddons(ctx: Context): List<Addon> {
    val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("addons", "[]") ?: "[]"
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Addon(
                o.getString("url"),
                o.optString("name", "Add-on"),
                o.optString("base", Stremio.baseOf(o.getString("url"))),
                o.optString("logo").ifEmpty { null },
                enabled = !o.optBoolean("off", false),     // the web player's field: `off` = installed, feeding nothing
            )
        }
    }.getOrDefault(emptyList())
}
/** The add-ons that feed content — Home, search, streams, subtitles, metadata. A switched-off
    add-on stays installed and ranked but is never asked for anything. */
internal fun activeAddons(ctx: Context): List<Addon> = loadAddons(ctx).filter { it.enabled }
/**
 * The series behind an episode id. "tt123:1:2" → tt123 and "12345:1:2" → 12345 (an imdb or bare id is its own
 * first segment); "kitsu:12345:3" → kitsu:12345 and "tmdb:123:1:2" → tmdb:123 (a prefixed id is its first two).
 * A bare series id comes back as it is, so callers can tell the two apart. Mirrors the player's seriesIdOf.
 */
internal fun seriesIdOf(episodeId: String): String {
    val segs = episodeId.split(':')
    if (segs.size < 2) return episodeId
    return if (Regex("(?i)(tt)?\\d+").matches(segs[0])) segs[0] else segs.take(2).joinToString(":")
}
/** The season and episode numbers off an episode id's tail: (season, episode), or (null, episode) for an id
    that carries no season (kitsu:ID:3); null for a bare series id. */
internal fun episodeNumbersOf(episodeId: String): Pair<String?, String>? {
    val root = seriesIdOf(episodeId)
    if (root == episodeId) return null
    val tail = episodeId.removePrefix("$root:").split(':')
    return if (tail.size >= 2) tail[tail.size - 2] to tail[tail.size - 1] else tail[0].takeIf { it.isNotEmpty() }?.let { null to it }
}
/**
 * Resuming an episode from Continue watching bypasses the picker, so rebuild the
 * chain in the background — otherwise "next episode" would be dead there.
 * Stremio episode ids are "<seriesId>:<season>:<episode>".
 */
private suspend fun hydrateSeriesChain(ctx: Context, addon: Addon, type: String, episodeId: String) {
    val seriesId = seriesIdOf(episodeId)
    if (seriesId.isEmpty() || seriesId == episodeId) return
    val order = listOf(addon) + activeAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
    for (a in order) {
        val vids = runCatching {
            if (a.manifestUrl != addon.manifestUrl && !manifestFor(a.manifestUrl).canMeta(type, seriesId)) {
                return@runCatching emptyList()
            }
            Stremio.loadSeriesVideos(a.base, type, seriesId)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(emptyList())
        if (vids.isNotEmpty()) {
            seriesChain.set(type, seriesChain.name, addon, vids)
            seriesChain.index = seriesChain.episodes.indexOfFirst { it.id == episodeId }
            return
        }
    }
}

/** Write the list without sync side effects — what a sync merge itself uses. */
internal fun saveAddonsRaw(ctx: Context, list: List<Addon>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(
            JSONObject().put("url", it.manifestUrl).put("name", it.name)
                .put("base", it.base).put("logo", it.logo ?: "").put("off", !it.enabled)
        )
    }
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("addons", arr.toString()).apply()
}

private fun saveAddons(ctx: Context, list: List<Addon>) {
    val prev = loadAddons(ctx)
    saveAddonsRaw(ctx, list)
    Cloud.noteAddonsDiff(ctx, prev, list)
    Cloud.noteChanged(ctx, "addons")
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppRoot(playReq: PlayReq? = null, onConsumed: () -> Unit = {}) {
    // Text size (Settings › Appearance) scales every sp in the app on top of the device's own setting
    val baseDensity = LocalDensity.current
    val density = Density(baseDensity.density, baseDensity.fontScale * Prefs.textScale)
    CompositionLocalProvider(LocalDensity provides density) {
    MaterialTheme(colorScheme = DarkColors, typography = NebulaTypography) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
            val catalogStates = remember { HashMap<String, CatalogUiState>() }
            val homeState = remember { HomeUiState() }
            val searchState = remember { SearchUiState() }
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            // the next-episode hop in flight (playEpisode): Back, a tab or the Up next card's Dismiss calls it off
            var hopJob by remember { mutableStateOf<Job?>(null) }
            fun cancelHop() { hopJob?.cancel(); hopJob = null }
            fun push(s: Screen) { ReturnFocus.clear(); stack = stack + s }
            fun pop() {
                cancelHop()
                if (stack.size > 1) {
                    // the screen beneath gets the remote back where it left it (TvFocus.kt)
                    ReturnFocus.aim(stack[stack.size - 2])
                    // a viewer backing out of playback leaves the party (the host keeps it alive)
                    if (stack.last() is Screen.Play && partyUi.active() && !partyUi.isHost) {
                        partyUi.reset(); partyUi.status = "Left the party"
                    }
                    stack = stack.dropLast(1)
                }
            }
            fun setTab(s: Screen) { cancelHop(); ReturnFocus.clear(); stack = listOf(s) }

            // ---- watch party wiring ----
            fun partyEvent(ev: PartyEvent) {
                when (ev) {
                    is PartyEvent.Created -> {
                        partyUi.code = ev.code; partyUi.isHost = true; partyUi.count = 1
                        partyUi.status = "Party started — code ${ev.code}. Friends: Add-ons tab, Join party."
                    }
                    is PartyEvent.Joined -> {
                        partyUi.code = ev.code; partyUi.isHost = false; partyUi.count = ev.count
                        partyUi.lastState = ev.state
                        partyUi.status = "Joined party ${ev.code}"
                        ev.stream?.let { st ->
                            // the host's stream, not a series of ours: no Next, no Up next, no autoplay into our last show
                            cancelHop(); seriesChain.clear()
                            stack = listOf(Screen.Home, Screen.Play(st.url, st.title, st.subs, st.type, st.id, st.name ?: st.title, st.poster, st.addonUrl))
                        }
                    }
                    is PartyEvent.State -> partyUi.lastState = ev.state
                    is PartyEvent.StreamSwitch -> {
                        if (!partyUi.isHost) {
                            partyUi.lastState = null
                            partyUi.status = "Host switched streams"
                            cancelHop(); seriesChain.clear()
                            stack = listOf(Screen.Home, Screen.Play(ev.stream.url, ev.stream.title, ev.stream.subs, ev.stream.type, ev.stream.id, ev.stream.name ?: ev.stream.title, ev.stream.poster, ev.stream.addonUrl))
                        }
                    }
                    is PartyEvent.Peers -> {
                        val old = partyUi.names
                        partyUi.count = ev.count
                        partyUi.names = ev.names
                        if (old.isNotEmpty() && ev.names.isNotEmpty()) {
                            val gone = old.toMutableList()
                            ev.names.forEach { n -> if (!gone.remove(n)) partyUi.status = "$n joined — ${ev.count} watching" }
                            gone.forEach { n -> partyUi.status = "$n left — ${ev.count} watching" }
                        }
                    }
                    is PartyEvent.React -> partyUi.react(ev.emoji, ev.name)
                    PartyEvent.Promoted -> { partyUi.isHost = true; partyUi.status = "You are now the party host" }
                    is PartyEvent.Ended -> { partyUi.reset(); partyUi.status = ev.reason }
                    is PartyEvent.Error -> partyUi.status = ev.message
                    PartyEvent.Disconnected -> { partyUi.reset(); partyUi.status = "Party connection lost" }
                }
            }
            fun partyStart(stream: PartyStreamDesc) {
                partyUi.reset()
                partyUi.status = "Starting party…"
                partyUi.session = PartySession(scope) { partyEvent(it) }
                    .also { it.displayName = partyDisplayName(ctx); it.create(stream) }
            }
            fun partyJoin(codeRaw: String) {
                val code = codeRaw.trim().replace(" ", "").uppercase()
                if (code.length < 4) { partyUi.status = "Enter the party code first"; return }
                partyUi.reset()
                partyUi.status = "Joining party…"
                partyUi.session = PartySession(scope) { partyEvent(it) }
                    .also { it.displayName = partyDisplayName(ctx); it.join(code) }
            }
            fun partyLeave() { partyUi.reset(); partyUi.status = "Left the party" }
            LaunchedEffect(partyUi.status) {
                partyUi.status?.let {
                    Toasts.show(it)
                    partyUi.status = null
                }
            }
            // Back pops the stack; from a non-Home tab root it returns to Home.
            BackHandler(enabled = stack.size > 1 || stack.last() != Screen.Home) {
                if (stack.size > 1) pop() else setTab(Screen.Home)
            }

            // Drop catalog state once its screen is no longer anywhere in the stack.
            LaunchedEffect(stack) {
                val live = stack.filterIsInstance<Screen.Catalog>().map { it.addon.manifestUrl }.toSet()
                catalogStates.keys.retainAll(live)
            }

            // ---- first-run seeding: the screen must open full, like Stremio does ----
            LaunchedEffect(Unit) {
                val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (!p.getBoolean("seeded_v1", false)) {
                    p.edit().putBoolean("seeded_v1", true).apply()
                    if (loadAddons(ctx).isEmpty()) {
                        val cin = Addon("https://v3-cinemeta.strem.io/manifest.json", "Cinemeta", "https://v3-cinemeta.strem.io", null)
                        saveAddonsRaw(ctx, listOf(cin))
                        Cloud.stampSeed(ctx, cin.manifestUrl)
                        homeState.invalidate()
                    }
                }
                // v2: an older install that already had add-ons when seeding first
                // ran never got Cinemeta — add it once, unless deliberately removed
                if (!p.getBoolean("seeded_v2", false)) {
                    p.edit().putBoolean("seeded_v2", true).apply()
                    val cinUrl = "https://v3-cinemeta.strem.io/manifest.json"
                    val cur2 = loadAddons(ctx)
                    val removed2 = Cloud.addonsSync(ctx).getJSONObject("removed")
                    if (cur2.none { it.manifestUrl == cinUrl } && !removed2.has(cinUrl)) {
                        saveAddonsRaw(ctx, cur2 + Addon(cinUrl, "Cinemeta", "https://v3-cinemeta.strem.io", null))
                        Cloud.stampSeed(ctx, cinUrl)
                        homeState.invalidate()
                    }
                }
                // OpenSubtitles was never seeded on Android (web always had it) —
                // one-time add so subtitles exist out of the box here too
                if (!p.getBoolean("seeded_subs_v1", false)) {
                    p.edit().putBoolean("seeded_subs_v1", true).apply()
                    val osUrl = "https://opensubtitles-v3.strem.io/manifest.json"
                    val cur = loadAddons(ctx)
                    if (cur.none { it.manifestUrl == osUrl }) {
                        val os = Addon(osUrl, "OpenSubtitles v3", "https://opensubtitles-v3.strem.io", null)
                        saveAddonsRaw(ctx, cur + os)
                        Cloud.stampSeed(ctx, os.manifestUrl)
                    }
                }
            }

            // ---- cross-device sync wiring ----
            var libraryVersion by remember { mutableStateOf(0) }
            var addonsVersion by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                Cloud.onApplied = { keys ->
                    if ("addons" in keys) { manifestCache.clear(); homeState.invalidate(); searchState.discover.optionsLoaded = false; addonsVersion++ }
                    if ("progress" in keys) homeState.invalidateContinue()
                    if ("library" in keys) libraryVersion++
                }
                Cloud.onSignedOut = {
                    Toasts.show("This device was signed out of your profile. Nothing on it was deleted.")
                }
                Account.boot(ctx)            // pre-profile installs trade the master secret for a device token
                Support.load(ctx)            // is there a link to show, and who is on the wall
                Cloud.pullAll(ctx)
                while (true) { delay(300_000); Cloud.pullAll(ctx) }
            }
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, e ->
                    when (e) {
                        Lifecycle.Event.ON_STOP -> Cloud.flushNow(ctx)
                        Lifecycle.Event.ON_START -> scope.launch { Cloud.pullAll(ctx) }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }

            // A deep-link play request jumps straight to the player; Back returns Home.
            LaunchedEffect(playReq) {
                if (playReq != null) {
                    // a bare address (often a live channel) belongs to no series: whatever chain the last show left goes
                    cancelHop(); seriesChain.clear()
                    stack = listOf(Screen.Home, Screen.Play(playReq.mpd, playReq.title))
                    onConsumed()
                }
            }

            // Every title opens on its own page first — art, facts, actions.
            fun openMeta(a: Addon, item: MetaItem) {
                push(Screen.Detail(a, item))
            }

            /** Resume something from the Continue watching row (or start it over). `decided` = the
                viewer picked Resume/Start over on a sheet, so the streams page does not ask again. */
            fun openProgress(r: ProgressRec, fresh: Boolean = false, decided: Boolean = false) {
                val addons = activeAddons(ctx)
                val a = addons.firstOrNull { it.manifestUrl == r.addonUrl } ?: addons.firstOrNull() ?: return
                seriesChain.clear()
                push(Screen.Streams(a, MetaItem(r.id, r.type, r.name, r.poster, r.shape), startOver = fresh, decided = decided))
                if (r.type == "series") scope.launch { hydrateSeriesChain(ctx, a, r.type, r.id) }
            }

            /** The title page behind a Continue watching card — the series, not the
                episode, since the card's id is whichever episode was last played. */
            fun openProgressDetails(r: ProgressRec) {
                val addons = activeAddons(ctx)
                val a = addons.firstOrNull { it.manifestUrl == r.addonUrl } ?: addons.firstOrNull() ?: return
                val id = if (r.type == "series") seriesIdOf(r.id) else r.id
                push(Screen.Detail(a, MetaItem(id, r.type, r.name.split(" · ").first(), r.poster, r.shape)))
            }

            // A torrent row (P2p.kt) carries no address until the engine has found the file in the
            // swarm and pulled the first pieces in, which can take most of a minute on a cold magnet.
            // Everything else plays exactly as before: `then` is called on the spot.
            var p2pFor by remember { mutableStateOf<String?>(null) }
            var p2pJob by remember { mutableStateOf<Job?>(null) }
            fun withP2p(st: StreamItem, then: (String) -> Unit) {
                if (!P2p.isRow(st.url)) { then(st.url); return }
                p2pJob?.cancel()                 // two rows tapped in a row: only the second one is wanted
                p2pFor = st.name.lineSequence().firstOrNull()?.take(90)?.takeIf { it.isNotBlank() } ?: "P2P stream"
                p2pJob = scope.launch {
                    val answer = P2p.open(ctx, st)
                    p2pFor = null; p2pJob = null
                    if (answer.url != null) then(answer.url)
                    else Toasts.show(answer.problem ?: "Could not start that stream.")
                }
            }

            /** The stall watchdog's pick (StallWatch.kt): the same title from another row, replacing the
                player in place from the same second; the next episodes follow the source that worked. */
            fun swapSource(st: StreamItem, from: Addon, atMs: Long) {
                val cur = stack.lastOrNull() as? Screen.Play ?: return
                NextEp.notePick(ctx, st, from, byHand = true)
                withP2p(st) { address ->
                    stack = stack.dropLast(1) + cur.copy(
                        url = address, title = st.name, subs = st.subtitles, addonUrl = from.manifestUrl,
                        startOver = false, startAtMs = atMs,
                        sourceLine = StreamTwin.label(StreamTwin.sig(st, from)).ifEmpty { null },
                    )
                    Toasts.show("Switched to " + StallWatch.name(st, from))
                }
            }

            /** Play a specific episode of the current chain from the stream chosen
                ahead of time (or chosen now, the same way), replacing the player in
                place so Back doesn't have to walk back through every episode. */
            fun playEpisode(ep: Episode) {
                val origin = seriesChain.addon ?: return
                // the player this hop replaces: if the viewer has left it by the time a stream is found
                // (Back, a tab, a party, a deep link), the answer is dropped instead of reopening the player
                val from = stack.lastOrNull() as? Screen.Play ?: return
                val label = seriesChain.label(ep)
                val poster = from.poster
                fun landed(): Boolean {
                    if (stack.lastOrNull() !== from) return false
                    // the chain moves on only when the hop lands: a cancelled one leaves this episode current
                    seriesChain.index = seriesChain.episodes.indexOfFirst { it.id == ep.id }
                    return true
                }
                cancelHop()   // a second tap on Play now replaces the first hop, it does not race it
                hopJob = scope.launch {
                    val res = NextEp.take(ep.id) ?: NextEp.source(ctx, origin)?.let { a ->
                        NextEp.resolve(ctx, a, origin, seriesChain.type, ep.id)
                    }
                    if (stack.lastOrNull() !== from) return@launch
                    val pick = res?.pick
                    if (res != null && pick != null) {
                        // an untouched series adopts the first auto-pick as its taste; a hand-pick is never overwritten
                        NextEp.notePick(ctx, pick, res.addon, byHand = false)
                        withP2p(pick) { address ->
                            if (!landed()) return@withP2p
                            stack = stack.dropLast(1) + Screen.Play(
                                address, pick.name, pick.subtitles, seriesChain.type, ep.id, label, poster, res.addon.manifestUrl,
                                sourceLine = StreamTwin.label(StreamTwin.sig(pick, res.addon)).ifEmpty { null },
                            )
                        }
                    } else if (landed()) {
                        // nothing auto-playable — fall back to the picker
                        stack = stack.dropLast(1) + Screen.Streams(origin, MetaItem(ep.id, seriesChain.type, label, poster))
                    }
                }
            }
            /** Choose the next episode's stream while this one still plays. */
            fun prefetchNext() {
                val next = seriesChain.next() ?: return
                NextEp.prefetch(ctx, scope, seriesChain.addon, seriesChain.type, next)
            }

            // Home used to appear the instant the shell was ready and then fill
            // in row by row, so the first thing anyone saw was an empty screen.
            // This holds it back until a catalogue has actually landed.
            var booting by remember { mutableStateOf(true) }
            if (booting) LaunchedEffect(Unit) {
                val started = System.currentTimeMillis()
                // whichever comes first: real rows, a verdict, or the cap
                while (System.currentTimeMillis() - started < BOOT_MAX_MS) {
                    val settled = homeState.rows.isNotEmpty() ||
                        !homeState.hasAddons ||
                        (!homeState.loading && homeState.sig != null)
                    if (settled && System.currentTimeMillis() - started >= BOOT_MIN_MS) break
                    delay(50)
                }
                val held = System.currentTimeMillis() - started
                if (held < BOOT_MIN_MS) delay(BOOT_MIN_MS - held)  // never a flicker
                booting = false
                // the web player's boot toast, behind Settings › Advanced › Welcome message at start
                if (Prefs.welcome && stack.last() is Screen.Home) {
                    Toasts.show(if (homeState.hasAddons) "Welcome back." else "Ready. Add your add-on to begin.")
                }
            }
            // anything but Home is its own destination and must not wait on catalogues
            LaunchedEffect(stack.last()) { if (stack.last() !is Screen.Home) booting = false }
            // Under a finger nothing claims a hand-back (focus is a remote's), so it is cleared once the screen Back
            // returned to has read it at its first composition — a live one lingering would put a kept place back on
            // any later rebuild of that screen (a Library tab switch).
            val navKeys = LocalInputModeManager.current.inputMode == InputMode.Keyboard
            LaunchedEffect(stack) {
                if (Account.isTv(ctx) || navKeys) return@LaunchedEffect
                withFrameNanos {}; withFrameNanos {}
                ReturnFocus.clear()
            }

            Box(Modifier.fillMaxSize()) {
                val current = stack.last()
                // the five screens the nav belongs to; everything else is full-bleed
                val onNav = current == Screen.Home || current == Screen.Search ||
                    current == Screen.Library || current == Screen.Settings || current == Screen.Profile
                // a television gets the rail laid out BESIDE the content, a phone the pill over it
                val isTv = remember(ctx) { Account.isTv(ctx) }
                val rail = onNav && isTv
                Row(Modifier.fillMaxSize()) {
                    if (rail) SideRail(current, onTab = { setTab(it) })
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(
                            targetState = current,
                            modifier = Modifier.fillMaxSize(),
                            // one 180 ms cross-fade per screen change; the in-place episode hop (Play → Play)
                            // keeps the same player composition, so it is not a change here
                            contentKey = { s -> if (s is Screen.Play) "play" else s },
                            // Motion: Reduced cuts between screens instead of fading
                            transitionSpec = {
                                val ms = if (Prefs.reducedMotion) 0 else 180
                                fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
                            },
                            label = "screen",
                        ) { s ->
                            // per screen: its entry (Back's return, TvFocus.kt) and its landing slot (the fallback landing)
                            val landing = remember { LandingSlot() }
                            LandingFallback(landing, s)
                            CompositionLocalProvider(LocalScreenEntry provides s, LocalLandingSlot provides landing) {
                            Box(Modifier.fillMaxSize().onFocusChanged { landing.hasFocus = it.hasFocus }) {
                            when (s) {
                                is Screen.Home -> HomeScreen(
                                    homeState,
                                    onOpen = { a, item -> openMeta(a, item) },
                                    onSeeAll = { a, c -> push(Screen.Catalog(a, c)) },
                                    onGoAddons = { push(Screen.Addons) },
                                    onResume = { r -> openProgress(r) },
                                    onSheetResume = { r -> openProgress(r, decided = true) },
                                    onStartOver = { r -> openProgress(r, fresh = true, decided = true) },
                                    onDetails = { r -> openProgressDetails(r) },
                                    onCustomise = { push(Screen.SettingsHome) },
                                )
                                is Screen.Search -> SearchScreen(
                                    searchState,
                                    onOpen = { a, item -> openMeta(a, item) },
                                    onAddon = { a -> push(Screen.Catalog(a)) },
                                )
                                is Screen.Addons -> AddonsScreen(
                                    version = addonsVersion,
                                    onBack = { pop() },
                                    onOpen = { push(Screen.Catalog(it)) },
                                    // an add-on off takes its titles out of Continue watching, and its catalogs out of Discover's pickers
                                    onAddonsChanged = { manifestCache.clear(); homeState.invalidate(); homeState.invalidateContinue(); searchState.discover.optionsLoaded = false },
                                )
                                is Screen.Settings -> SettingsScreen(
                                    onAddons = { push(Screen.Addons) },
                                    onLayout = { push(Screen.SettingsLayout) },
                                    onSupport = { push(Screen.SettingsSupport) },
                                    onHome = { push(Screen.SettingsHomeOpts) },
                                    onPlayback = { push(Screen.SettingsPlayback) },
                                    onStreams = { push(Screen.SettingsStreams) },
                                    onProfile = { push(Screen.Profile) },
                                    onParty = { push(Screen.SettingsParty) },
                                    onFriends = { push(Screen.Friends) },
                                    onAdvanced = { push(Screen.SettingsAdvanced) },
                                )
                                is Screen.SettingsLayout -> SettingsLayoutScreen(
                                    onBack = { pop() },
                                    onSupport = { push(Screen.SettingsSupport) },
                                )
                                is Screen.SettingsHomeOpts -> SettingsHomeScreen(onBack = { pop() }, onRows = { push(Screen.SettingsHome) })
                                is Screen.SettingsStreams -> SettingsStreamsScreen(onBack = { pop() })
                                is Screen.SettingsAdvanced -> SettingsAdvancedScreen(
                                    onBack = { pop() },
                                    onClearCache = { clearContentCaches(); homeState.invalidate() },
                                    onReset = { homeState.invalidate() },
                                )
                                is Screen.SettingsHome -> SettingsHomeRowsScreen(onBack = { pop() })
                                is Screen.Friends -> FriendsScreen(
                                    onBack = { pop() },
                                    onProfile = { push(Screen.Profile) },
                                    onOpen = { m ->
                                        val a = activeAddons(ctx).firstOrNull() ?: return@FriendsScreen
                                        openMeta(a, m)
                                    },
                                )
                                is Screen.SettingsPlayback -> SettingsPlaybackScreen(
                                    onBack = { pop() },
                                    onSubtitles = { push(Screen.SettingsSubtitles) },
                                )
                                is Screen.SettingsSubtitles -> SettingsSubtitlesScreen(onBack = { pop() })
                                // reached from the nav's last item as a root, or pushed from Settings
                                is Screen.Profile -> ProfileScreen(onBack = { if (stack.size > 1) pop() else setTab(Screen.Home) })
                                is Screen.SettingsParty -> SettingsPartyScreen(onBack = { pop() }, onJoin = { partyJoin(it) })
                                is Screen.SettingsSupport -> SettingsSupportScreen(
                                    onBack = { pop() },
                                    onProfile = { push(Screen.Profile) },
                                )
                                is Screen.Library -> LibraryScreen(
                                    version = libraryVersion,
                                    onResume = { r -> openProgress(r) },
                                    onSheetResume = { r -> openProgress(r, decided = true) },
                                    onStartOver = { r -> openProgress(r, fresh = true, decided = true) },
                                    onDetails = { r -> openProgressDetails(r) },
                                    onGoHome = { setTab(Screen.Home) },
                                    onGoSearch = { setTab(Screen.Search) },
                                    onOpen = { li ->
                                        val addons = activeAddons(ctx)
                                        val a = addons.firstOrNull { it.manifestUrl == li.addonUrl } ?: addons.firstOrNull()
                                        if (a == null) partyUi.status = "Add an add-on first"
                                        else push(Screen.Detail(a, MetaItem(li.id, li.type, li.name, li.poster, li.shape)))
                                    },
                                    onPlayEpisode = { li, ep ->
                                        val addons = activeAddons(ctx)
                                        val a = addons.firstOrNull { it.manifestUrl == li.addonUrl } ?: addons.firstOrNull()
                                        if (a == null) partyUi.status = "Add an add-on first"
                                        else {
                                            seriesChain.clear()
                                            val tag = "S${ep.season}" + (ep.episode?.let { "E$it" } ?: "")
                                            val label = li.name + " · " + tag + (if (ep.name.isNotEmpty()) " · ${ep.name}" else "")
                                            push(Screen.Streams(a, MetaItem(ep.id, "series", label, li.poster, li.shape)))
                                            scope.launch { hydrateSeriesChain(ctx, a, "series", ep.id) }
                                        }
                                    },
                                )
                                is Screen.Detail -> DetailScreen(
                                    s.addon, s.item,
                                    onBack = { pop() },
                                    onEpisodes = { push(Screen.Episodes(s.addon, s.item)) },
                                    onPlayMovie = {
                                        seriesChain.clear()
                                        push(Screen.Streams(s.addon, s.item.copy(runtime = metaFullCache[s.item.type + ":" + s.item.id]?.runtime)))
                                    },
                                    onResumeEpisode = { r -> openProgress(r) },
                                    onPlayEpisode = { ep, intent ->
                                        seriesChain.index = seriesChain.episodes.indexOfFirst { it.id == ep.id }
                                        val label = seriesChain.label(ep)
                                        push(Screen.Streams(s.addon, MetaItem(
                                            ep.id, "series", label, s.item.poster,
                                            // the streams header is a landscape banner — hand it the
                                            // backdrop, not a portrait poster to crop
                                            background = s.item.background ?: ep.thumbnail,
                                            runtime = metaFullCache[s.item.type + ":" + s.item.id]?.runtime,   // sizes become rates against it
                                        ),
                                            startOver = intent == PlayIntent.START_OVER,
                                            // a sheet row is a deliberate choice, so "When you come back · Ask"
                                            // must not ask again; a plain row tap has decided nothing
                                            decided = intent != PlayIntent.TAP))
                                    },
                                )
                                is Screen.Catalog -> CatalogScreen(
                                    s.addon, s.initial,
                                    catalogStates.getOrPut(s.addon.manifestUrl) { CatalogUiState() },
                                    onBack = { pop() },
                                    onOpen = { openMeta(s.addon, it) },
                                )
                                is Screen.Episodes -> EpisodesScreen(
                                    s.addon, s.item,
                                    onBack = { pop() },
                                    onPlayEpisode = { ep, intent ->
                                        seriesChain.index = seriesChain.episodes.indexOfFirst { it.id == ep.id }
                                        val label = seriesChain.label(ep)
                                        push(Screen.Streams(s.addon, MetaItem(
                                            ep.id, "series", label, s.item.poster,
                                            // the streams header is a landscape banner — hand it the
                                            // backdrop, not a portrait poster to crop
                                            background = s.item.background ?: ep.thumbnail,
                                            runtime = metaFullCache[s.item.type + ":" + s.item.id]?.runtime,   // sizes become rates against it
                                        ),
                                            startOver = intent == PlayIntent.START_OVER,
                                            // a sheet row is a deliberate choice, so "When you come back · Ask"
                                            // must not ask again; a plain row tap has decided nothing
                                            decided = intent != PlayIntent.TAP))
                                    },
                                    // no episode data anywhere → replace this screen with the flat stream list
                                    onFallback = { stack = stack.dropLast(1) + Screen.Streams(s.addon, s.item) },
                                )
                                is Screen.Streams -> StreamsScreen(
                                    s.addon, s.item,
                                    onBack = { pop() },
                                    fresh = s.startOver,
                                    decided = s.decided,
                                    onPlay = { st, from, byHand, fresh ->
                                        // remembered so the next episode keeps this source and quality
                                        NextEp.notePick(ctx, st, from, byHand)
                                        withP2p(st) { address ->
                                            push(
                                                Screen.Play(
                                                    address, st.name, st.subtitles,
                                                    s.item.type, s.item.id, s.item.name,
                                                    s.item.poster, s.addon.manifestUrl,
                                                    description = s.item.description,
                                                    startOver = fresh,
                                                    sourceLine = StreamTwin.label(StreamTwin.sig(st, from)).ifEmpty { null },
                                                )
                                            )
                                        }
                                    },
                                )
                                is Screen.Play -> PlayerScreen(
                                    s.url, s.title, s.subs,
                                    contentType = s.type, contentId = s.id, contentName = s.contentName,
                                    poster = s.poster, addonUrl = s.addonUrl,
                                    description = s.description,
                                    startOver = s.startOver,
                                    sourceLine = s.sourceLine,
                                    startAtMs = s.startAtMs,
                                    currentEpisode = seriesChain.episodes.getOrNull(seriesChain.index),
                                    nextEpisode = seriesChain.next(),
                                    onPlayNext = { ep -> playEpisode(ep) },
                                    onCancelNext = { cancelHop() },
                                    onSwapSource = { st, from, at -> swapSource(st, from, at) },
                                    onPrefetchNext = { prefetchNext() },
                                    onProgressSaved = { homeState.invalidateContinue() },
                                    onPartyStart = { partyStart(it) },
                                    onPartyLeave = { partyLeave() },
                                    // the chrome's Back button: what AppRoot's Back does, reached directly, because the
                                    // player's own Back handler (peeling the controls away on a remote) sits in front of it
                                    onExit = { if (stack.size > 1) pop() else setTab(Screen.Home) },
                                )
                            }
                            }
                            }
                        }
                        if (onNav && !rail) {
                            BottomBar(current, onTab = { setTab(it) }, modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
                // outside the screen Box so it covers the nav bar too
                AnimatedVisibility(visible = booting, enter = fadeIn(tween(0)), exit = fadeOut(tween(320))) {
                    BootScreen()
                }
                // finding a torrent in the swarm: held up until it answers, or the viewer stops it
                p2pFor?.let { name ->
                    P2pSheet(name) { p2pJob?.cancel(); p2pJob = null; p2pFor = null }
                }
                // toasts: a small pill under the status bar, over whatever screen is up
                ToastHost(Modifier.align(Alignment.TopCenter).padding(horizontal = 24.dp))
            }
        }
    }
    }
}

// ---------- shared pieces ----------

private const val BOOT_MIN_MS = 550L    // below this it reads as a flicker, not a screen
private const val BOOT_MAX_MS = 3200L   // a stuck add-on must never strand anyone here

/** What you look at while the first catalogue is on its way. */
@Composable
private fun BootScreen() {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(30.dp)) {
                    // the same diamond the rail wears
                    val c = size.minDimension / 2f
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(c, 0f); lineTo(size.width, c); lineTo(c, size.height); lineTo(0f, c); close()
                        },
                        Red,
                    )
                }
                Text(
                    "Nebula", color = TextC, fontSize = 30.sp, fontFamily = Sans,
                    fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp,
                    modifier = Modifier.padding(start = 11.dp),
                )
            }
            // An indeterminate sweep, not a percentage — we cannot know how long
            // an add-on will take, and a fake bar that stalls is worse than none.
            val sweep = rememberInfiniteTransition(label = "boot")
            val x by sweep.animateFloat(
                initialValue = -1.05f, targetValue = 3.55f,
                animationSpec = infiniteRepeatable(tween(1050), RepeatMode.Restart),
                label = "sweep",
            )
            Box(
                Modifier.padding(top = 26.dp).width(132.dp).height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0x1AFFFFFF)),
            ) {
                Box(
                    Modifier.fillMaxWidth(0.4f).fillMaxHeight()
                        .graphicsLayer { translationX = x * size.width }
                        .clip(RoundedCornerShape(2.dp)).background(Red),
                )
            }
        }
    }
}

/** Card wrapper: scales up + white border when focused (TV D-pad) or pressed. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun FocusCard(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    // a full-width row does not grow: at 1.09 it gained ~40 dp a side and was cut at the screen's edges (its plate,
    // its thumbnail) — it wears the ring instead
    zoom: Boolean = true,
    // the caller's own source, when the content restyles itself on focus (a stream row's plate)
    interactionSource: MutableInteractionSource? = null,
    // may this card be the screen's fallback landing (TvFocus.kt)? Never a Back button
    landing: Boolean = true,
    content: @Composable () -> Unit,
) {
    val own = remember { MutableInteractionSource() }
    val interaction = interactionSource ?: own
    val focused by interaction.collectIsFocusedAsState()
    val haptics = LocalHapticFeedback.current
    // Focus zoom (Settings › Appearance) sets how much a card grows; Motion: Reduced grows nothing
    // and draws the white ring instead, so focus stays visible from the couch
    val reduced = Prefs.reducedMotion || !zoom
    val zoom by animateFloatAsState(if (focused && !reduced) Prefs.focusScale else 1f, tween(if (reduced) 0 else 300), label = "zoom")
    val lift by animateFloatAsState(if (focused && !reduced) 22f else 0f, tween(if (reduced) 0 else 300), label = "lift")
    Box(
        modifier
            .then(if (landing) Modifier.landingSlot() else Modifier)
            .scale(zoom)
            .shadow(lift.dp, shape, clip = false)
            .then(if (reduced) Modifier.border(2.dp, if (focused) Color.White else Color.Transparent, shape) else Modifier)
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClickLabel = "More options",
                // combinedClickable buzzes on its own for a touch hold, so leaving it
                // on gave a phone two buzzes ~0 ms apart; the key path does NOT buzz
                // by itself, which is why the manual one stays
                hapticFeedbackEnabled = false,
                // the buzz is the whole affordance here — nothing on the card
                // itself advertises that a hold does anything
                onLongClick = onLongClick?.let {
                    { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() }
                },
                onClick = onClick,
            )
    ) { content() }
}

/**
 * Somewhere for the remote to land.
 *
 * Compose focuses nothing when a screen opens, so on a television the first D-pad press goes
 * wherever the default traversal decides — which is why Home, Search and Library read as dead
 * while Settings and Profile answer (`nebula-android#1`, "i cant scrool thru menus, only
 * settings, profile"). The shared player has `focusFirst()` for exactly this; this is its
 * Android half. Attach the requester to the screen's first control, or to a `focusGroup()`
 * wrapping its list, and it is claimed once [ready] says the content is really composed.
 *
 * Nothing happens under a finger: a pre-lit control on a phone reads as already chosen, which
 * is the same reason [CardSheet] gates its own first focus on the input mode. A remote plugged
 * into a phone counts, hence the input-mode arm beside the television one.
 */
@Composable
internal fun tvFirstFocus(ready: Boolean = true, key: Any? = Unit): FocusRequester {
    val req = remember { FocusRequester() }
    val ctx = LocalContext.current
    val tv = remember(ctx) { Account.isTv(ctx) }
    val keys = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val entry = LocalScreenEntry.current
    // Once per visit, and only while the viewer has not moved: [ready] can turn true again later (Home's list pivots
    // back to the top when the viewer walks up to it; slow rows land after a cold start while the viewer browses
    // Continue watching) and a second claim would pull focus out from under a hand.
    val openedAt = remember(key) { android.os.SystemClock.uptimeMillis() }
    var claimed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(tv || keys, ready, key) {
        if (!(tv || keys) || !ready || claimed) return@LaunchedEffect
        if (KeyWatch.lastDownAt > openedAt) { claimed = true; return@LaunchedEffect }
        // Back returned here: the item the viewer left takes focus instead (Modifier.returnTo); this lands only if
        // that never happens
        if (yieldToReturn(entry)) { claimed = true; return@LaunchedEffect }
        // requestFocus() RETURNS whether it took; runCatching only guards the throw from a
        // requester with no node attached yet. A list's first item is composed a frame after
        // the list itself and an AnimatedContent screen fades in over ~180 ms, so retry across
        // a few frames rather than guess one wait — the silent failure here is an inert screen,
        // which is the whole defect being fixed.
        repeat(10) {
            withFrameNanos {}
            if (runCatching { req.requestFocus() }.getOrDefault(false)) { claimed = true; return@LaunchedEffect }
        }
    }
    return req
}

/**
 * The floating pill nav's footprint at the foot of every root list. A television has the rail
 * beside the content instead, so nothing is parked over it there and the padding would just be
 * dead screen.
 */
@Composable
internal fun navPadBottom(): Dp = if (Account.isTv(LocalContext.current)) 24.dp else 104.dp

/**
 * A round icon button for the title page's action row. Four of these fit where two
 * ghost pills did — the row is the only place on the page with real estate to
 * spare, and every pixel it gives back is a pixel of Cast above the fold.
 * [on] fills it white the way the Play pill is filled, so a state you have set
 * reads at a glance rather than needing its label.
 */
@Composable
internal fun RoundAction(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        // Material's focus tint is 10 % of the content colour — invisible on the white "on" state (My List, Watched)
        Modifier.focusRing(CircleShape).size(48.dp).clip(CircleShape)
            .background(if (on) Color.White else Color(0x1FFFFFFF))
            .then(if (on) Modifier else Modifier.border(1.dp, Color(0x38FFFFFF), CircleShape))
            .clickable(onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (on) Color.Black else TextC, modifier = Modifier.size(21.dp))
    }
}

/** One row inside a [CardSheet]. A row closes the sheet, unless [keepOpen] (a retry that fills the same sheet). */
internal data class SheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val keepOpen: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The long-press sheet that stands in for per-card buttons. A delete cross
 * parked on the artwork reads as a defect rather than a feature — and it is the
 * one control you never want the easiest to hit — so every card action lives in
 * here instead, reached by holding the card.
 */
@Composable
internal fun CardSheet(
    title: String,
    sub: String?,
    poster: String?,
    shape: String,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
) {
    val shown = remember { MutableTransitionState(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown.targetState = true }
    val firstFocus = remember { FocusRequester() }
    val keys = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    // The OK that opened this sheet is usually STILL DOWN — a long-press fires at
    // ~500 ms and the remote goes on sending ACTION_DOWN. A row activates on the
    // release, and Compose's "did this node see the press" guard is per-node, so a
    // repeat registers a fresh press on whatever we just focused and the release
    // fires it. Nothing counts until that press has been let go.
    //
    // The gate only works if the RELEASE is seen, and a Compose key modifier only
    // sees keys aimed at a focused descendant — so row 0 is focused immediately
    // (from inside the composed content, see below) and this Column's preview
    // handler catches the release as its ancestor. Deliberately NOT gated on
    // `armed`: if the release were ever missed the viewer loses one press, which is
    // a bad day, where an unfocused sheet is inert to OK entirely.
    //
    // The opening state comes from [KeyWatch], read once here: the Activity's own
    // dispatch sees the press whatever is focused. It does NOT see the matching UP,
    // because by then the dialog owns the window — harmless, since nothing reads
    // `okDown` after this initialiser and it self-clears on the next card press.
    var armed by remember { mutableStateOf(!keys || !KeyWatch.okDown) }
    // let the slide-out finish before the dialog goes, so it doesn't blink away
    LaunchedEffect(closing, shown.isIdle) {
        if (closing && shown.isIdle && !shown.currentState) onDismiss()
    }
    val close: () -> Unit = { closing = true; shown.targetState = false }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // kill the platform dim so the scrim below can fade in with the sheet
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(shown, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
                Box(
                    // `clickable` brings its own `focusable`, and this fills the screen —
                    // on a TV a directional press lands on the scrim and OK then dismisses
                    // the sheet instead of running the row the viewer was aiming at
                    Modifier.fillMaxSize().background(Color(0xB8000000))
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) { close() }
                )
            }
            AnimatedVisibility(
                shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(300)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(190)) { it } + fadeOut(tween(150)),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        // swallow every OK belonging to the press that opened us;
                        // its release is what arms the sheet (see `armed` above)
                        .onPreviewKeyEvent { ev ->
                            if (armed) return@onPreviewKeyEvent false
                            val ok = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                            if (!ok) return@onPreviewKeyEvent false
                            if (ev.type == KeyEventType.KeyUp) armed = true
                            true
                        }
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFF141418))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                ) {
                    // INSIDE the content on purpose. AnimatedVisibility does not compose
                    // this Column until the enter transition starts, so an effect declared
                    // beside `shown.targetState = true` fires against a requester with no
                    // node attached, fails silently, and with a Unit key never retries —
                    // which is exactly how the last attempt at this came to do nothing.
                    // A finger needs no focus, and a pre-lit row on a phone reads as
                    // already chosen, so this is for a remote only.
                    if (keys) LaunchedEffect(Unit) {
                        // requestFocus() RETURNS whether it worked, and runCatching only
                        // guards a throw — the gate that can legitimately fail here is the
                        // owner-focus check, which is on the path every time because nothing
                        // in the dialog is focused yet, and it returns false rather than
                        // throwing when WindowManager has not granted the dialog window focus.
                        // That depends on window focus, not on frames, so retry across a few
                        // rather than guess a longer wait. A silent failure here is the inert
                        // sheet that has already cost two rounds.
                        repeat(10) {
                            withFrameNanos {}
                            if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) {
                                return@LaunchedEffect
                            }
                        }
                    }
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
                            .width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color(0x33FFFFFF))
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(if (shape == "landscape") 82.dp else 48.dp)
                                .aspectRatio(thumbRatio(shape))
                                .clip(RoundedCornerShape(9.dp)).background(SurfaceC),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (poster != null) AsyncImage(
                                model = poster, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            ) else Text(
                                title.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "••" },
                                color = Color(0xFF3A3A45), fontSize = 15.sp, fontWeight = FontWeight.Black,
                            )
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(
                                title, color = TextC, fontSize = 17.sp, fontFamily = Sans,
                                fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            if (sub != null) Text(
                                sub, color = MutedC, fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
                    actions.forEachIndexed { i, a ->
                        val tint = if (a.destructive) Color(0xFFFF5A5F) else TextC
                        // the lit row reads from a sofa (Material's focus tint did not); a finger keeps its ripple
                        val rowSrc = remember { MutableInteractionSource() }
                        val rowFocused by rowSrc.collectIsFocusedAsState()
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                .clickable(interactionSource = rowSrc, indication = LocalIndication.current) { if (!a.keepOpen) close(); a.onClick() }
                                .then(rowLit(rowFocused))
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(a.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                            Text(
                                a.label, color = tint, fontSize = 15.sp, fontFamily = Sans,
                                fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackBar(title: String, sub: String?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FocusCard(shape = RoundedCornerShape(50), onClick = onBack, landing = false) {
            Box(
                Modifier.size(42.dp).background(Surface2, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontSize = 28.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrEmpty()) Text(sub, style = labelStyle(13), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Outlined choice chip: a hairline at rest, white when chosen. Inside a
    [Segmented] control the rest state drops its own outline. */
@Composable
internal fun Chip(text: String, on: Boolean, inSeg: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pill = RoundedCornerShape(50)
    val line = when {
        focused -> if (on) Bg else Color.White
        on -> Color.White
        inSeg -> Color.Transparent
        else -> LineC
    }
    Box(
        modifier
            // chosen AND lit was a 2 dp black line inside a white pill on a black screen — nothing a sofa could see,
            // and exactly where Settings and Library land (their first chip is the chosen one): a white ring outside
            .outerRing(focused && on, pill)
            .clip(pill)
            .background(if (on) Color.White else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, line, pill)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = if (inSeg) 14.dp else 15.dp, vertical = if (inSeg) 7.dp else 8.dp)
    ) {
        Text(text, color = if (on) Bg else MutedC, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Room at a chip row's ends for the ring a lit chip wears outside itself — a scroller clips at its edges. TV only. */
@Composable
internal fun chipEdge(): Dp = if (remoteMode()) 6.dp else 0.dp

/** A row of chips inside one hairline pill — the settings segmented control. */
@Composable
internal fun Segmented(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    // under a remote the scrolled strip keeps a few dp at its ends: a scroller clips at its edges, and the ring a lit
    // chosen chip wears outside itself lost its outer side on the first and last chip
    val edge = if (remoteMode()) 5.dp else 0.dp
    Row(
        modifier.border(1.dp, LineC, RoundedCornerShape(50)).padding(3.dp)
            .horizontalScroll(rememberScrollState()).padding(horizontal = edge),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The second type register: a mono micro-caps eyebrow over a title or a fact. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = FaintC, maxLines: Int = 1) {
    Text(
        text.uppercase(), color = color, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

/** One poster/landscape card — used by the catalog grid, Home rows, and Search. */
@Composable
internal fun MetaCard(
    m: MetaItem,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    // Wide cards everywhere (Settings › Home) turns every poster row into 16:9 art
    val wide = Prefs.landscapeRows || m.posterShape == "landscape"
    val shape = cardShape()
    FocusCard(shape = shape, modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        Column(Modifier.padding(2.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else thumbRatio(m.posterShape))
                    .clip(shape)
                    .background(SurfaceC)
                    .border(1.dp, Color(0x0FFFFFFF), shape),
                contentAlignment = Alignment.Center,
            ) {
                val art = if (wide) (m.background ?: m.poster) else m.poster
                if (art != null) {
                    AsyncImage(
                        model = art, contentDescription = m.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        m.name.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "••" },
                        color = Color(0xFF3A3A45), fontSize = 22.sp, fontWeight = FontWeight.Black,
                    )
                }
                if (Prefs.ratings) m.imdbRating?.let {
                    Text(
                        "★ $it", color = Color.White, fontFamily = Mono, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .background(Color(0x9E000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            // Under posters (Settings › Appearance): title and year, the title alone, or nothing
            if (Prefs.posterLabels != "none") Text(
                m.name, color = TextC, fontSize = 13.sp, fontFamily = Sans, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
            )
            if (Prefs.posterLabels == "both") m.releaseInfo?.let {
                Text(it, style = labelStyle(11, FaintC), maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp))
            }
        }
    }
}

/** The card width a row gives a title: 16:9 art is wider than a poster; Card size scales both. */
internal fun rowCardWidth(m: MetaItem): Dp =
    (if (Prefs.landscapeRows || m.posterShape == "landscape") 210.dp else 124.dp) * Prefs.posterScale

/** "1h 2m left" for the Continue watching chip; never under a minute. */
internal fun fmtLeft(ms: Long): String {
    val mins = (ms / 60_000L).coerceAtLeast(1L)
    val h = mins / 60
    val m = mins % 60
    return (if (h > 0) "${h}h ${m}m" else "${m}m") + " left"
}

/** A Continue watching card: 16:9 art, the episode's eyebrow, title and name on the art, what is
    left as a glass chip in the corner, and a resume bar along the bottom edge. */
@Composable
internal fun ContinueCard(r: ProgressRec, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    // episode identity reads off the artwork itself — a wrapped two-line title
    // under a poster was the single biggest source of visual noise on Home
    val parts = r.name.split(" · ")
    val tag = parts.getOrNull(1)?.takeIf { Regex("""^S\d+E\d+${'$'}""", RegexOption.IGNORE_CASE).matches(it.trim()) }
        ?.let { it.trim().replace(Regex("""(?i)^s(\d+)e(\d+)${'$'}"""), "S$1 E$2") }
    val title = if (tag != null) parts[0] else r.name
    val sub = if (tag != null) parts.drop(2).joinToString(" · ").ifEmpty { null } else null
    val left = r.dur - r.pos
    val shape = RoundedCornerShape((cardRadius() + 2).dp)
    if (Prefs.cwStyle == "poster") {
        ContinuePosterCard(r, title, tag, left, modifier, onClick, onLongClick)
        return
    }
    FocusCard(shape = shape, modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(shape)
                .background(SurfaceC)
                .border(1.dp, Color(0x14FFFFFF), shape),
        ) {
            if (r.poster != null) {
                AsyncImage(
                    model = r.poster, contentDescription = r.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        r.name.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "••" },
                        color = Color(0xFF3A3A45), fontSize = 22.sp, fontWeight = FontWeight.Black,
                    )
                }
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color(0x00000000), 0.4f to Color(0x33000000), 1f to Color(0xE6000000))))
            // how much is left, as a glass chip in the corner
            if (left > 0) Text(
                fmtLeft(left), color = Ink, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = Sans, maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .background(BarGlass, Pill).border(1.dp, Hairline, Pill)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                if (tag != null) Eyebrow(tag, color = Color(0xD1EBEBF5))
                Text(
                    title, color = Color.White, fontSize = 16.sp, fontFamily = Sans,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (tag != null) 3.dp else 0.dp),
                )
                if (sub != null) Text(
                    sub, color = Color(0xB3EBEBF5), fontSize = 12.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp),
                )
            }
            val pct = if (r.dur > 0) (r.pos.toFloat() / r.dur).coerceIn(0f, 1f) else 0f
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxWidth(pct).fillMaxSize().background(Red))
            }
        }
    }
}

/** Continue Watching as a poster (Settings › Home › Continue Watching cards · Poster): the 2:3 art
    with the resume bar along its foot, the episode eyebrow and title beneath. */
@Composable
private fun ContinuePosterCard(
    r: ProgressRec, title: String, tag: String?, left: Long,
    modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val shape = cardShape()
    FocusCard(shape = shape, modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        Column(Modifier.padding(2.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(SurfaceC).border(1.dp, Color(0x0FFFFFFF), shape)) {
                if (r.poster != null) AsyncImage(
                    model = r.poster, contentDescription = r.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                ) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(r.name.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "••" },
                        color = Color(0xFF3A3A45), fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                if (left > 0) Text(
                    fmtLeft(left), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = Sans, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(BarGlass, Pill).border(1.dp, Hairline, Pill).padding(horizontal = 7.dp, vertical = 3.dp),
                )
                val pct = if (r.dur > 0) (r.pos.toFloat() / r.dur).coerceIn(0f, 1f) else 0f
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66000000))) {
                    Box(Modifier.fillMaxWidth(pct).fillMaxSize().background(Red))
                }
            }
            if (tag != null) Eyebrow(tag, Modifier.padding(top = 7.dp, start = 2.dp))
            Text(
                title, color = TextC, fontSize = 13.sp, fontFamily = Sans, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                modifier = Modifier.padding(top = if (tag != null) 2.dp else 7.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

/** How wide a Continue Watching card is in the Home row: 16:9 art, or a poster the width of the other rows. */
internal fun continueCardWidth(): Dp = (if (Prefs.cwStyle == "poster") 124.dp else 250.dp) * Prefs.posterScale

// ---------- ratings + friends (experimental) ----------

/** Star row on a title page: tap to rate 1–5, tap the same star to clear. */
@Composable
private fun RatingStars(item: MetaItem) {
    val ctx = LocalContext.current
    var cur by remember(item.id) { mutableStateOf(Ratings.get(ctx, item.type, item.id)) }
    // one hairline pill, so the stars read as a control and not a second headline
    Row(
        Modifier.padding(top = 14.dp).border(1.dp, LineC, RoundedCornerShape(50))
            .padding(start = 6.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        for (i in 1..5) {
            Icon(
                Icons.Filled.Star, contentDescription = "$i star${if (i > 1) "s" else ""}",
                tint = if (i <= cur) Red else Color(0x40EBEBF5),
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)).size(30.dp).clip(RoundedCornerShape(8.dp))
                    .clickable {
                        cur = if (i == cur) 0 else i
                        Ratings.set(ctx, item.type, item, cur)
                    }
                    .padding(4.dp),
            )
        }
        Eyebrow(if (cur > 0) "Your rating" else "Rate it", Modifier.padding(start = 10.dp))
    }
}

/** Pick a friend to send this title to. */
@Composable
private fun RecommendSheet(type: String, item: MetaItem, scope: CoroutineScope, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    // `scope` is the title page's: a pick closes this sheet, and the sheet's own scope would be cancelled ~190 ms
    // later, mid-request, turning a sent recommendation into "Could not send that"
    var friends by remember { mutableStateOf<List<JSONObject>?>(null) }
    // a list that could not be fetched is not an empty one: it says so and offers a retry, in the same sheet
    var failed by remember { mutableStateOf(false) }
    var tries by remember { mutableStateOf(0) }
    var trying by remember { mutableStateOf(false) }
    LaunchedEffect(tries) {
        trying = true
        val fr = Social.friends(ctx)
        trying = false
        failed = fr == null
        friends = (0 until (fr?.length() ?: 0)).mapNotNull { i ->
            val f = fr!!.optJSONObject(i) ?: return@mapNotNull null
            // only a friendship both sides made carries a recommendation
            if (Social.friendKey(f).isEmpty() || Social.friendPending(f)) null else f
        }
    }
    val list = friends ?: return
    CardSheet(
        title = item.name, sub = "Recommend to…", poster = item.poster, shape = item.posterShape,
        actions = if (failed) listOf(
            // Try again first: a remote lands on the first row, and the sentence below it can do nothing
            SheetAction(Icons.Filled.Refresh, if (trying) "Trying again…" else "Try again", keepOpen = true) {
                if (!trying) tries++
            },
            SheetAction(Icons.Filled.CloudOff, "Could not reach Friends — check the connection") {},
        ) else if (list.isEmpty()) listOf(
            SheetAction(Icons.Filled.Groups, "No friends yet — add one in Friends") {},
        ) else list.take(8).map { f ->
            val fName = Social.friendLabel(f)
            SheetAction(Icons.Filled.Favorite, fName) {
                scope.launch {
                    val ok = Social.recommend(ctx, f, type, item)
                    Toasts.show(if (ok) "Recommended to $fName" else "Could not send that")
                }
            }
        },
        onDismiss = onDismiss,
    )
}

/** Profile monogram: one of the eight Nebula colours behind the first letter of the name. */
/** A colour's hue in degrees, for the Any colour slider (a `#RRGGBB` accent of one's own). */
internal fun hueOf(hex: String): Float {
    val v = runCatching { hex.removePrefix("#").toLong(16) }.getOrDefault(0L)
    val r = ((v shr 16) and 0xFF) / 255f; val g = ((v shr 8) and 0xFF) / 255f; val b = (v and 0xFF) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return if (h < 0f) h + 360f else h
}
internal fun hexOf(c: Color): String =
    "#%02X%02X%02X".format(java.util.Locale.US, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

internal fun avatarColor(hex: String): Color =
    if (Regex("^#[0-9A-Fa-f]{6}$").matches(hex)) Color(android.graphics.Color.parseColor(hex)) else Color(0xFF636366)

@Composable
internal fun Avatar(hex: String, name: String, size: Dp, dim: Boolean = false, ring: Boolean = false) {
    val bg = if (dim) Surface2 else avatarColor(hex)
    val ink = if (dim) MutedC else if (hex.equals("#F2F2F7", ignoreCase = true)) Bg else Color.White
    // a Founder wears a gold ring, everywhere the avatar is drawn
    val ringMod = if (ring) Modifier.border(2.dp, Color(0xFFE0B24A), CircleShape) else Modifier
    Box(Modifier.size(size).then(ringMod).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        val initial = name.trim().removePrefix("@").take(1).uppercase()
        // nobody signed in: a person glyph, not a "?" (it read as an error)
        if (initial.isEmpty() || initial == "?") Icon(Icons.Filled.Person, contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.5f))
        else Text(initial, color = ink, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, fontFamily = Sans)
    }
}

/** One friend-profile item parsed out of their (untrusted) published doc. */
private fun profItems(a: org.json.JSONArray?): List<JSONObject> =
    (0 until (a?.length() ?: 0)).mapNotNull { i ->
        val o = a!!.optJSONObject(i) ?: return@mapNotNull null
        if (o.optString("id").isEmpty() || o.optString("name").isEmpty()) null else o
    }

@Composable
private fun FriendsScreen(onBack: () -> Unit, onProfile: () -> Unit, onOpen: (MetaItem) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var codeIn by remember { mutableStateOf("") }
    var friends by remember { mutableStateOf<List<JSONObject>?>(null) }
    var inbox by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var asks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var openCode by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    // the list could not be fetched — said as such, never as "No friends yet"
    var friendsFailed by remember { mutableStateOf(false) }
    // why Turn off Friends did not (shown beside it; Friends stays on until the server agrees)
    var offErr by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload, Social.on) {
        if (!Social.on) return@LaunchedEffect
        Social.publishSoon(ctx)
        friends = null
        val fr = Social.friends(ctx)
        friendsFailed = fr == null
        friends = (0 until (fr?.length() ?: 0)).mapNotNull { fr!!.optJSONObject(it) }
        val ib = Social.inbox(ctx)
        inbox = (0 until (ib?.length() ?: 0)).mapNotNull { ib!!.optJSONObject(it) }.reversed()
        val ak = Social.asks(ctx)
        asks = (0 until (ak?.length() ?: 0)).mapNotNull { ak!!.optJSONObject(it) }
            .filter { Social.friendKey(it).isNotEmpty() }.distinctBy { Social.friendKey(it) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 110.dp),
    ) {
        item(key = "top") {
            Column {
                BackBar("Friends", if (Social.on) Social.myKey else "Experimental", onBack)
                if (status.isNotEmpty()) Text(status, color = MutedC, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        if (!Social.on) {
            item(key = "pitch") {
                Column(
                    Modifier.fillMaxWidth().background(SurfaceC, RoundedCornerShape(16.dp))
                        .border(1.dp, LineC, RoundedCornerShape(16.dp)).padding(18.dp),
                ) {
                    Text("Rate. Share. Recommend.", color = TextC, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Rate what you watch, see what your friends are watching, and trade " +
                            "recommendations. Turning it on shares your ratings, recent watches and " +
                            "My List with your friends — people you add by @handle who add you back — and no one else.",
                        color = MutedC, fontSize = 14.sp, lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
                    )
                    val hasProfile = Cloud.profile != null
                    Button(
                        onClick = {
                            if (busy) return@Button
                            if (!hasProfile) { onProfile(); return@Button }
                            busy = true; status = "Setting up…"
                            scope.launch {
                                status = Social.enable(ctx) ?: ""
                                busy = false; reload++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.focusRing(RoundedCornerShape(12.dp)),
                    ) { Text(if (hasProfile) "Turn on Friends" else "Sign in to use Friends", fontWeight = FontWeight.SemiBold) }
                    if (!hasProfile) Text(
                        "Friends find each other by @handle, so Friends needs a Nebula Profile.",
                        color = FaintC, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "add") {
            val typing = tvTyping()
            val keyboard = LocalSoftwareKeyboardController.current
            fun addFriend() {
                scope.launch {
                    val (name, err) = Social.addFriend(ctx, codeIn)
                    status = err ?: if (Social.lastAddPending) "Asked $name. Once they add you back, you see each other’s watching."
                        else "You and $name are now friends."
                    if (err == null) { codeIn = ""; reload++ }
                }
            }
            Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = codeIn, onValueChange = { codeIn = it.take(24) },
                    placeholder = { Text("Friend’s @handle", color = MutedC) },
                    singleLine = true, readOnly = typing.readOnly, modifier = typing.modifier.weight(1f),
                    // Done adds, as the button does (it did nothing)
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { addFriend(); keyboard?.hide() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
                Button(
                    onClick = { addFriend() },
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(start = 10.dp).focusRing(RoundedCornerShape(12.dp)),
                ) { Text("Add", fontWeight = FontWeight.SemiBold) }
            }
        }
        if (asks.isNotEmpty()) {
            item(key = "askshead") {
                Text("Want to be friends", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            items(asks.take(20), key = { "ask/" + Social.friendKey(it) }) { f ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .background(SurfaceC, RoundedCornerShape(14.dp))
                        .border(1.dp, LineC, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(Social.jstr(f, "avatar"), Social.jstr(f, "name").ifEmpty { Social.jstr(f, "handle") }, 40.dp, ring = Social.friendFounder(f))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(Social.friendLabel(f), color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val h = Social.jstr(f, "handle")
                        Text(if (h.isNotEmpty()) "@$h added you" else "Added you", color = MutedC, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                status = Social.acceptAsk(ctx, f) ?: "You and ${Social.friendLabel(f)} are now friends."
                                reload++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(start = 8.dp).focusRing(RoundedCornerShape(50)),
                    ) { Text("Add back", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1) }
                    Text("Dismiss", color = MutedC, fontSize = 13.sp, maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp).focusRing(RoundedCornerShape(8.dp), landing = false)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { scope.launch { Social.dismissAsk(ctx, f); reload++ } }
                            .padding(8.dp))
                }
            }
        }
        if (inbox.isNotEmpty()) {
            item(key = "inboxhead") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Recommended to you", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Clear", color = MutedC, fontSize = 13.sp,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp), landing = false).clip(RoundedCornerShape(8.dp))
                            .clickable { scope.launch { Social.inboxClear(ctx); reload++ } }
                            .padding(6.dp))
                }
            }
            items(inbox.take(10), key = { it.optLong("at").toString() + it.optString("c") }) { rec ->
                val it2 = rec.optJSONObject("i") ?: return@items
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .focusRing(RoundedCornerShape(12.dp))
                        .background(SurfaceC, RoundedCornerShape(12.dp))
                        .border(1.dp, LineC, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onOpen(MetaItem(it2.optString("id"), if (it2.optString("type") == "series") "series" else "movie",
                                it2.optString("name"), it2.optString("poster").ifEmpty { null }))
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val poster = it2.optString("poster")
                    if (poster.startsWith("http")) AsyncImage(
                        model = poster, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(34.dp).height(50.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black),
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            "${rec.optString("f").ifEmpty { rec.optString("h").ifEmpty { null }?.let { "@$it" } ?: "A friend" }} recommends ${it2.optString("name")}",
                            color = TextC, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        val note = rec.optString("n")
                        if (note.isNotEmpty()) Text("“$note”", color = MutedC, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item(key = "frhead") { Text("Friends", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) }
        when (val fl = friends) {
            null -> item(key = "frload") { Text("Loading…", color = MutedC, fontSize = 14.sp) }
            else -> if (friendsFailed) {
                item(key = "frfail") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Could not reach Friends — check the connection.",
                            color = MutedC, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                        Text("Try again", color = TextC, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp).focusRing(RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                                .clickable { reload++ }
                                .padding(6.dp))
                    }
                }
            } else if (fl.isEmpty()) {
                item(key = "frempty") {
                    Text("No friends yet — add one by @handle and their watching shows up here.",
                        color = MutedC, fontSize = 14.sp, lineHeight = 20.sp)
                }
            } else {
                items(fl, key = { Social.friendKey(it) }) { f ->
                    val fCode = Social.friendKey(f)
                    val prof = runCatching { JSONObject(f.optString("profile").ifEmpty { "{}" }) }.getOrDefault(JSONObject())
                    Column(Modifier.padding(bottom = 10.dp)) {
                        Row(
                            Modifier.fillMaxWidth()
                                .focusRing(RoundedCornerShape(14.dp))
                                .background(SurfaceC, RoundedCornerShape(14.dp))
                                .border(1.dp, if (openCode == fCode) Line2 else LineC, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { openCode = if (openCode == fCode) null else fCode }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(Social.jstr(f, "avatar"), Social.jstr(f, "name").ifEmpty { Social.jstr(f, "handle") }, 40.dp, ring = Social.friendFounder(f))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(Social.friendLabel(f), color = TextC, fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false))
                                    if (Social.friendSup(f)) SupporterMark(mark = Social.friendMark(f))
                                }
                                val handle = Social.jstr(f, "handle")
                                Text(
                                    (if (handle.isNotEmpty()) "@$handle · " else "") +
                                        if (Social.friendPending(f)) "Waiting for them to add you back" else "${prof.optJSONArray("ratings")?.length() ?: 0} rated",
                                    color = MutedC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(if (openCode == fCode) "Hide" else "View", color = MutedC, fontSize = 13.sp)
                        }
                        if (openCode == fCode && Social.friendPending(f)) {
                            Text("They have not added you back yet — once they do, you see each other’s watching here.",
                                color = MutedC, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
                        } else if (openCode == fCode) {
                            FriendRow("Watched recently", profItems(prof.optJSONArray("recent")).map { r ->
                                if (r.optString("type") == "series")
                                    JSONObject(r.toString()).put("id", seriesIdOf(r.optString("id")))
                                else r
                            }, onOpen)
                            FriendRow("Rated", profItems(prof.optJSONArray("ratings")), onOpen)
                            FriendRow("Their list", profItems(prof.optJSONArray("list")), onOpen)
                        }
                    }
                }
            }
        }
        // offered whatever the list holds — empty, or not loaded — or someone with no friends could never switch it off
        if (friends != null) {
            item(key = "froff") {
                Column {
                    Text("Turn off Friends", color = MutedC, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp).focusRing(RoundedCornerShape(8.dp), landing = false).clip(RoundedCornerShape(8.dp))
                            .clickable { scope.launch { offErr = Social.disable(ctx); reload++ } }
                            .padding(6.dp))
                    // the Profile page's error red
                    offErr?.let { Text(it, color = Color(0xFFFF453A), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 6.dp)) }
                }
            }
        }
    }
}

/** A titled poster row from a friend's profile. */
@Composable
private fun FriendRow(title: String, items: List<JSONObject>, onOpen: (MetaItem) -> Unit) {
    if (items.isEmpty()) return
    Text(title, color = MutedC, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    // Keyed type:id, and deduped on it first: a profile published before the per-show dedupe
    // carries one record per episode, and two of one show would share a key and crash the row.
    val shown = items.distinctBy { it.optString("type") + ":" + it.optString("id") }.take(15)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(shown, key = { it.optString("type") + ":" + it.optString("id") }) { r ->
            Column(Modifier.width(96.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                        .focusRing(RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp)).background(SurfaceC)
                        .clickable {
                            onOpen(MetaItem(r.optString("id"), if (r.optString("type") == "series") "series" else "movie",
                                r.optString("name"), r.optString("poster").ifEmpty { null }))
                        },
                ) {
                    val poster = r.optString("poster")
                    if (poster.startsWith("http")) AsyncImage(
                        model = poster, contentDescription = r.optString("name"),
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                    val stars = r.optInt("rating", 0)
                    if (stars > 0) Text(
                        "★ $stars", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(5.dp)
                            .background(Color(0xB8000000), RoundedCornerShape(7.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(r.optString("name"), color = MutedC, fontSize = 12.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

// ---------- skeletons ----------
// One shimmer for every placeholder shape, so a list that is loading reads the
// same whether it is posters, streams or episodes.

/** A single shimmering block. */
@Composable
private fun SkelBox(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp)) {
    Box(modifier.clip(shape).background(shimmerBrush()))
}

/** Placeholder shaped like a stream or episode row: leading block, two text lines. */
@Composable
internal fun SkeletonRow(leadingWidth: Dp, leadingHeight: Dp, circle: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceC, RoundedCornerShape(12.dp))
            .border(1.dp, LineC, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkelBox(
            Modifier.width(leadingWidth).height(leadingHeight),
            if (circle) RoundedCornerShape(50) else RoundedCornerShape(12.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SkelBox(Modifier.fillMaxWidth().height(13.dp))
            SkelBox(Modifier.fillMaxWidth(0.42f).height(13.dp))
        }
    }
}

/** Placeholder shaped like a poster/landscape card. */
@Composable
internal fun SkeletonCell(modifier: Modifier = Modifier) {
    Column(modifier) {
        SkelBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(12.dp))
        SkelBox(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(12.dp))
    }
}

/** Row header on Home/Search/Library: the title, a mono eyebrow beside it for
    where the row comes from (or how big it is), and a See-all chip. */
@Composable
internal fun RowHeader(title: String, sub: String?, seeAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextC, fontSize = 20.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (sub != null) Eyebrow(sub, Modifier.padding(start = 12.dp).weight(1f))
        else Spacer(Modifier.weight(1f))
        if (seeAll != null) Chip("See all ›", false, onClick = seeAll)
    }
}

/** Floating pill tab bar: Home / Search / Library / Settings, and who is signed in as the last item. */
@Composable
private fun BottomBar(current: Screen, onTab: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xF0141419))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(34.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TabItem("Home", Icons.Filled.Home, current == Screen.Home) { onTab(Screen.Home) }
        TabItem("Search", Icons.Filled.Search, current == Screen.Search) { onTab(Screen.Search) }
        TabItem("Library", Icons.Filled.Bookmark, current == Screen.Library) { onTab(Screen.Library) }
        TabItem("Settings", Icons.Filled.Settings, current == Screen.Settings) { onTab(Screen.Settings) }
        ProfileTab(current == Screen.Profile) { onTab(Screen.Profile) }
    }
}

/**
 * The television's nav.
 *
 * A pill floating at the bottom of the screen is right for a thumb and wrong for a remote: it
 * sits OVER the content, so the only way back out of it is a blind upward press into whatever
 * Compose finds there. The rail is the shape the design ruling names and the shared player has
 * had since the chrome pass, and because it is laid out BESIDE the content rather than on top
 * of it, Right walks off it into the rows and Left comes back — which is the whole of what
 * `nebula-android#1` was asking for.
 */
@Composable
private fun SideRail(current: Screen, onTab: (Screen) -> Unit) {
    // a rail taken away while it held focus (a deep link or a party replacing the stack) says so on its way out
    DisposableEffect(Unit) { onDispose { RailFocus.has = false } }
    Column(
        Modifier.fillMaxHeight().width(104.dp)
            // a screen's fallback landing leaves a viewer on the rail alone (TvFocus.kt)
            .onFocusChanged { RailFocus.has = it.hasFocus }
            .background(Color(0xF014141A))
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("◆", color = Red, fontSize = 20.sp, modifier = Modifier.padding(bottom = 28.dp))
        TabItem("Home", Icons.Filled.Home, current == Screen.Home) { onTab(Screen.Home) }
        Spacer(Modifier.height(12.dp))
        TabItem("Search", Icons.Filled.Search, current == Screen.Search) { onTab(Screen.Search) }
        Spacer(Modifier.height(12.dp))
        TabItem("Library", Icons.Filled.Bookmark, current == Screen.Library) { onTab(Screen.Library) }
        Spacer(Modifier.height(12.dp))
        TabItem("Settings", Icons.Filled.Settings, current == Screen.Settings) { onTab(Screen.Settings) }
        Spacer(Modifier.height(12.dp))
        ProfileTab(current == Screen.Profile) { onTab(Screen.Profile) }
    }
}

/** The nav's last item: the signed-in profile's initial on the accent, a person glyph when nobody is. */
@Composable
private fun ProfileTab(on: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val p = Cloud.profile
    val tint = if (on || focused) Color.White else MutedC
    Column(
        Modifier
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(26.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(32.dp)
                .border(1.5.dp, if (on) Color.White else Color.Transparent, CircleShape)
                .padding(3.dp)
                .background(if (p != null) Red else Surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // signed out: a person glyph, not a "?" (read as an error on the rail and the pill)
            if (p == null) Icon(Icons.Filled.Person, contentDescription = null, tint = MutedC, modifier = Modifier.size(18.dp))
            else Text(
                p.name.ifEmpty { p.handle }.trim().removePrefix("@").take(1).uppercase().ifEmpty { "?" },
                color = OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Sans,
            )
        }
        // one line whatever the font scale: squeezed, a label wrapped a letter at a time down the pill
        Text("Profile", color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun TabItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tint = if (on || focused) Color.White else MutedC
    Column(
        Modifier
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(26.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(32.dp).background(if (on) Surface2 else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp)) }
        // one line whatever the font scale (the pill and the TV rail both use this): squeezed, it wrapped a letter at a time
        Text(label, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The skeleton material: a soft band sweeping left to right every 1.2 s over the tertiary surface. */
@Composable
internal fun shimmerBrush(): Brush {
    if (Prefs.reducedMotion) return Brush.linearGradient(listOf(Surface2, Surface2))   // a still placeholder, no sweep
    val t = rememberInfiniteTransition(label = "sk")
    val x by t.animateFloat(
        initialValue = -600f, targetValue = 1800f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "x",
    )
    return Brush.linearGradient(
        colors = listOf(Surface2, Color(0xFF3A3A40), Surface2),
        start = Offset(x, 0f), end = Offset(x + 600f, 0f),
    )
}

private fun thumbRatio(shape: String): Float = when (shape) {
    "landscape" -> 16f / 9f
    "square" -> 1f
    else -> 2f / 3f
}

/** A Stremio content type as a plural word for a row title. */
internal fun typeLabel(type: String): String = when (type) {
    "movie" -> "Movies"
    "series" -> "Series"
    "channel" -> "Channels"
    "tv" -> "TV"
    else -> type.replaceFirstChar { it.uppercase() }
}

/**
 * "Update available" banner shown on Home. Downloads the APK in the background as
 * soon as it appears, unless on metered data (cached per version, one download per
 * process — Updates.startDownload), then Install is one tap.
 * The version line carries the download's size, and ⓘ opens the release's whole text
 * (the card itself has room for its first line only) — the Founder, 09-22, pointing at
 * Nuvio's update bar: "our player don't show this so add it".
 */
@Composable
private fun UpdateCard(version: String, notes: String, apkUrl: String = Updates.APK_URL, size: Long = 0L, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var notesOpen by remember { mutableStateOf(false) }
    if (notesOpen) ReleaseNotesSheet(version, notes) { notesOpen = false }
    val firstLine = remember(notes) { notes.lineSequence().map { it.trim().removePrefix("• ") }.firstOrNull { it.isNotEmpty() }?.take(140).orEmpty() }
    val mb = if (size > 0) " · " + (size / 1_000_000.0).roundToInt() + " MB" else ""
    var message by remember { mutableStateOf<String?>(null) }
    // The download is the process's own (Updates.startDownload): a later visit to Home, or a second card, watches the
    // same one instead of starting another writer. A file already cached from an earlier session is ready as it is.
    val dl = Updates.download?.takeIf { it.version == version }
    val cached = remember(version, dl?.phase) { if (dl == null) Updates.cachedApk(ctx, version) else null }
    val phase = dl?.phase ?: if (cached != null) "ready" else "idle"      // idle · downloading · ready · failed
    val progress = dl?.progress ?: 0
    val apk = dl?.file ?: cached
    fun download() { message = null; Updates.startDownload(ctx, version, apkUrl) }
    // Fetched in the background as soon as the card appears — but not on metered data (a phone on mobile data):
    // there the card waits for a tap on Update.
    LaunchedEffect(version) {
        if (Updates.download?.version != version && Updates.cachedApk(ctx, version) == null && !Updates.metered(ctx)) download()
    }

    // The sheets' material (a panel with a hairline), not a slab of red: one accent, on the button that acts.
    Row(
        Modifier.fillMaxWidth()
            .background(SurfaceC, RoundedCornerShape(12.dp))
            .border(1.dp, LineC, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Update available · v$version$mb", color = TextC, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                message ?: when (phase) {
                    "downloading" -> "Downloading… $progress%"
                    "ready" -> "Ready — tap Install."
                    "failed" -> "Download failed — tap Retry"
                    else -> firstLine.ifEmpty { "A new version is available." }
                },
                color = MutedC, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Button(
            onClick = {
                when (phase) {
                    "downloading" -> {}
                    "ready" -> {
                        val f = apk
                        if (f != null) Updates.installApk(ctx, f)?.let { message = it }
                    }
                    else -> download()
                }
            },
            enabled = phase != "downloading",
            // the accent's own ink (dark on White, Gold, Ice…), so the label reads on every accent
            colors = ButtonDefaults.buttonColors(
                containerColor = Red, contentColor = OnAccent,
                disabledContainerColor = Red.copy(alpha = .6f), disabledContentColor = OnAccent.copy(alpha = .8f),
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.focusRing(RoundedCornerShape(12.dp), landing = false),
        ) {
            Text(
                when (phase) { "ready" -> "Install"; "downloading" -> "···"; "failed" -> "Retry"; else -> "Update" },
                fontWeight = FontWeight.Bold,
            )
        }
        // the whole of what changed, one press away (nothing to show when the release carries no text)
        if (notes.isNotBlank()) IconButton(onClick = { notesOpen = true }, modifier = Modifier.focusRing(CircleShape, landing = false)) {
            Icon(Icons.Filled.Info, contentDescription = "Release notes", tint = TextC)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.focusRing(CircleShape, landing = false)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = MutedC)
        }
    }
}

/**
 * "Release notes": a centred card (Nuvio's layout, the Founder's reference) in the sheets' own material — the title,
 * the version in the mono register, a hairline, then the release's text, scrolling when long. Under a remote the text
 * takes focus and ↑/↓ scroll it (a plain scrolled Text is nothing the D-pad can reach), ↑ at the top moves to the close
 * button, and Back closes as it does any sheet.
 */
@Composable
private fun ReleaseNotesSheet(version: String, notes: String, onDismiss: () -> Unit) {
    val remote = remoteMode()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val textFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { onDismiss() }, contentAlignment = Alignment.Center) {
            val card = RoundedCornerShape(24.dp)
            Column(
                Modifier.fillMaxWidth(0.88f).widthIn(max = 480.dp)
                    .clip(card).background(Color(0xFF141418)).border(1.dp, Color(0x14FFFFFF), card)
                    // a tap inside stays inside
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 24.dp),
            ) {
                if (remote) LaunchedEffect(Unit) {
                    repeat(10) {
                        withFrameNanos {}
                        if (runCatching { textFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        Text("Release notes", color = TextC, fontSize = 20.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                        Text(version, color = MutedC, fontFamily = Mono, fontSize = 13.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.focusRing(CircleShape, landing = false)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextC)
                    }
                }
                Box(Modifier.padding(top = 16.dp, bottom = 16.dp, end = 12.dp).fillMaxWidth().height(1.dp).background(LineC))
                Text(
                    notes, color = Color(0xCCEBEBF5), fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(end = 12.dp).heightIn(max = 380.dp)
                        .then(if (remote) Modifier.focusRing(RoundedCornerShape(8.dp), landing = false)
                            .focusRequester(textFocus)
                            .onKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (e.key) {
                                    Key.DirectionDown -> { if (scroll.canScrollForward) scope.launch { scroll.animateScrollBy(220f) }; true }
                                    Key.DirectionUp -> if (scroll.canScrollBackward) { scope.launch { scroll.animateScrollBy(-220f) }; true } else false
                                    else -> false
                                }
                            }
                            .focusable() else Modifier)
                        .verticalScroll(scroll),
                )
            }
        }
    }
}

/** Featured hero: full-bleed backdrop, the title's own logo art, cycling
    through the top five of the first catalogue. The scrim exists only so the
    type stays legible over the artwork. */
@Composable
private fun HeroHeader(rows: List<CatRow>, onOpen: (Addon, MetaItem) -> Unit, detailsModifier: Modifier = Modifier) {
    // Featured from (Settings › Home): the first row, every row taking turns, or one chosen row
    val source = Prefs.heroSource
    val feed: List<CatRow> = remember(rows, source) {
        when (source) {
            "all" -> rows
            "first" -> rows.take(1)
            else -> rows.filter { HomeRows.key(it.addon, it.catalog) == source }.ifEmpty { rows.take(1) }
        }
    }
    val first = feed.firstOrNull() ?: return
    val picks: List<Pair<Addon, MetaItem>> = remember(feed) {
        val out = mutableListOf<Pair<Addon, MetaItem>>()
        val seen = HashSet<String>()
        // several rows: one title from each in turn, so the carousel reads as the whole Home
        for (n in 0 until 6) for (r in feed) {
            val m = r.items.filter { it.background != null || it.poster != null }.getOrNull(n) ?: continue
            if (out.size < 6 && seen.add(m.type + ":" + m.id)) out.add(r.addon to m)
        }
        out
    }
    if (picks.isEmpty()) return
    var idx by remember(picks) { mutableStateOf(0) }
    val every = Prefs.heroInterval
    LaunchedEffect(picks, every) {
        if (every <= 0) return@LaunchedEffect                  // Featured changes every · Off
        while (true) { delay(every * 1000L); idx = (idx + 1) % picks.size }
    }
    val (from, m) = picks[idx]
    // the board owns the top of the screen edge to edge and dissolves into it
    val heroH = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    val heroCtx = LocalContext.current
    val heroTv = remember(heroCtx) { Account.isTv(heroCtx) }
    Box(
        Modifier.fillMaxWidth().height(heroH)
            // 58% of the screen is a poor focus target and it draws NOTHING when it takes
            // focus, so on a remote the press that lands here reads as a dead one — which is
            // half of what `nebula-android#1` reported. Let focus fall through to View Details,
            // which does show it. A finger still taps the artwork.
            .then(if (heroTv) Modifier.focusProperties { canFocus = false } else Modifier)
            .clickable { onOpen(from, m) }
    ) {
        // a slide change dissolves one picture into the next rather than cutting (a cut under reduced motion)
        Crossfade(targetState = m, animationSpec = tween(if (Prefs.reducedMotion) 0 else 400), label = "heroArt", modifier = Modifier.matchParentSize().padding(bottom = 2.dp).clipToBounds()) { pick ->
            AsyncImage(
                model = pick.background ?: pick.poster, contentDescription = pick.name,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0x8A000000), 0.22f to Color(0x1A000000),
                    0.58f to Color(0x8A000000), 0.82f to Color(0xE0000000), 0.97f to Bg, 1f to Bg,
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (m.logo != null) {
                AsyncImage(
                    model = m.logo, contentDescription = m.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.heightIn(max = 104.dp).fillMaxWidth(0.78f).padding(bottom = 12.dp),
                )
            } else {
                Text(
                    m.name, color = TextC, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp, lineHeight = 35.sp, maxLines = 2,
                    textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            // one meta line — type · first genre · year(s); the rating stays on the poster badge
            val facts = listOfNotNull(
                if (m.type == "series") "Series" else "Movie",
                m.genres.firstOrNull(),
                m.releaseInfo?.replace('-', '\u2013'),
            ).joinToString("  \u00B7  ")
            Text(
                facts, color = Color(0xD1EBEBF5), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // one white pill; My List lives on the title page
            // a white pill cannot wear a white focus ring, so it grows the way a card does —
            // the remote's landing spot on Home has to be unmistakable from the couch
            val heroBtn = remember { MutableInteractionSource() }
            val heroFocused by heroBtn.collectIsFocusedAsState()
            Button(
                onClick = { onOpen(from, m) },
                interactionSource = heroBtn,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 13.dp),
                modifier = Modifier.padding(top = 14.dp)
                    .returnTo("hero").then(detailsModifier)
                    .scale(if (heroFocused && !Prefs.reducedMotion) 1.08f else 1f),
            ) { Text("View Details", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                picks.forEachIndexed { i, _ ->
                    Box(
                        Modifier.height(4.dp)
                            .width(if (i == idx) 20.dp else 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == idx) Color.White else Color(0x59FFFFFF))
                    )
                }
            }
        }
    }
}

// ---------- home (content rows, Stremio-style) ----------
@Composable
private fun HomeScreen(
    st: HomeUiState,
    onOpen: (Addon, MetaItem) -> Unit,
    onSeeAll: (Addon, CatalogRef) -> Unit,
    onGoAddons: () -> Unit,
    onResume: (ProgressRec) -> Unit = {},
    onSheetResume: (ProgressRec) -> Unit = onResume,     // Resume chosen on the sheet: already a decision
    onStartOver: (ProgressRec) -> Unit = {},
    onDetails: (ProgressRec) -> Unit = {},
    onCustomise: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val screenEntry = LocalScreenEntry.current
    var update by remember { mutableStateOf<Updates.Release?>(null) }
    // the card held down, if any — Home's only long-press surface is Continue watching
    var sheetFor by remember { mutableStateOf<ProgressRec?>(null) }

    sheetFor?.let { r ->
        val parts = r.name.split(" · ")
        val isEp = parts.size > 1 && Regex("""^S\d+E\d+${'$'}""", RegexOption.IGNORE_CASE).matches(parts[1].trim())
        val left = (r.dur - r.pos).takeIf { it > 0 }?.let { fmtTime(it) + " left" }
        CardSheet(
            title = if (isEp) parts[0] else r.name,
            sub = listOfNotNull(parts.getOrNull(1)?.takeIf { isEp }?.trim()?.uppercase(), left)
                .joinToString("  ·  ").ifEmpty { null },
            poster = r.poster,
            shape = "landscape",
            actions = listOf(
                SheetAction(Icons.Filled.PlayArrow, "Resume") { onSheetResume(r) },
                SheetAction(Icons.Filled.Replay, "Start over") { onStartOver(r) },
                SheetAction(Icons.Filled.Info, "View details") { onDetails(r) },
                SheetAction(Icons.Filled.Delete, "Remove from Continue watching", destructive = true) {
                    // the card takes focus with it when it goes: name the neighbour first (the hero if it was the last)
                    val i = st.continueRows.indexOfFirst { it.type == r.type && it.id == r.id }
                    val nb = st.continueRows.getOrNull(i + 1) ?: st.continueRows.getOrNull(i - 1)
                    Progress.clear(ctx, r.type, r.id)
                    st.continueRows = Progress.continueList(ctx)
                    ReturnFocus.handTo(screenEntry, if (nb != null) "cw/" + Progress.key(nb.type, nb.id) else "hero")
                },
            ),
            onDismiss = { sheetFor = null },
        )
    }

    // Re-read on every entry (this screen leaves composition when one is pushed),
    // so finishing an episode is reflected the moment you come back.
    LaunchedEffect(st.continueKey) { st.continueRows = Progress.continueList(ctx) }

    // Best-effort update check against GitHub Releases, once per Home entry.
    LaunchedEffect(Unit) {
        val current = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull().orEmpty()
        if (current.isEmpty()) return@LaunchedEffect
        val dismissed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("updateDismissed", "").orEmpty()
        val rel = Updates.latest() ?: return@LaunchedEffect
        if (Updates.isNewer(rel.version, current) && rel.version != dismissed) update = rel
    }

    // Build content rows: the catalogs Home is set to show (each add-on's first
    // three until arranged in Settings), shown as they load and slotted into
    // Home's order. Kept unless the add-on list or the arrangement changed, or
    // the rows are older than 5 minutes.
    LaunchedEffect(st.refreshKey) {
        val addons = activeAddons(ctx)
        st.hasAddons = addons.isNotEmpty()
        val sig = addons.joinToString("|") { it.manifestUrl } + "#" + HomeRows.version
        if (st.sig == sig && st.rows.isNotEmpty() && System.currentTimeMillis() - st.builtAt < 300_000) return@LaunchedEffect
        st.loading = true
        st.hidden = 0; st.wanted = 0
        val rows = mutableListOf<CatRow>()
        // A first build shows rows as they load. A REBUILD (the rows went stale while the viewer was away watching)
        // keeps the old ones on screen until the new set is whole: blanking them first took away the card Back was
        // returning to and dropped the list's place.
        val rebuilding = st.rows.isNotEmpty()
        // …but a row whose add-on was just removed or switched off, or which was just hidden, goes at once
        if (rebuilding) {
            val keep = st.rows.filter { r ->
                val a = addons.firstOrNull { it.manifestUrl == r.addon.manifestUrl } ?: return@filter false
                val ci = runCatching {
                    manifestFor(a.manifestUrl).catalogs.filter { it.browsable }
                        .indexOfFirst { it.type == r.catalog.type && it.id == r.catalog.id }
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(-1)
                ci < 0 || HomeRows.visible(ctx, HomeRows.key(a, r.catalog), ci)
            }
            if (keep.size != st.rows.size) st.rows = keep
        }
        for ((ai, a) in addons.withIndex()) {
            runCatching {
                val all = manifestFor(a.manifestUrl).catalogs.filter { it.browsable }
                val seenCat = HashSet<String>()
                val wanted = mutableListOf<Pair<CatalogRef, Int>>()
                all.forEachIndexed { ci, c ->
                    if (!seenCat.add(c.type + "/" + c.id)) return@forEachIndexed
                    val k = HomeRows.key(a, c)
                    if (HomeRows.visible(ctx, k, ci)) wanted.add(c to HomeRows.orderIndex(ctx, k, ai, ci)) else st.hidden++
                }
                st.wanted += wanted.size
                for ((c, oi) in wanted) {
                    runCatching {
                        // one card per title: the row is keyed by it (a catalogue listing a title twice would crash it)
                        val items = Stremio.loadCatalog(a.base, c, null).distinctBy { it.type + ":" + it.id }.take(15)
                        if (items.isNotEmpty()) {
                            rows.add(CatRow(a, c, items, oi)); rows.sortBy { it.oi }
                            if (!rebuilding) st.rows = rows.toList()
                        }
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
        if (rebuilding) st.rows = rows.toList()
        // stamped only once the set is whole: a build cut off by leaving Home must run again, not pass for done
        st.sig = sig
        st.builtAt = System.currentTimeMillis()
        st.loading = false
    }

    val heroing = st.rows.isNotEmpty() && st.hasAddons && Prefs.showHero
    Column(Modifier.fillMaxSize().padding(top = if (heroing) 0.dp else 16.dp)) {
        // with a hero the brand rides on the artwork instead of pushing it down
        if (!heroing) Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        ) {
            Text("◆ ", color = Red, fontSize = 18.sp)
            Text("Nebula", color = TextC, fontSize = 22.sp, fontFamily = Sans,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        }
        update?.let { rel ->
            UpdateCard(
                version = rel.version,
                notes = rel.notes,
                apkUrl = rel.apkUrl,
                size = rel.size,
                onDismiss = {
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("updateDismissed", rel.version).apply()
                    update = null
                },
            )
        }
        when {
            !st.hasAddons -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 36.dp)
                    .background(SurfaceC, RoundedCornerShape(12.dp))
                    .border(1.dp, LineC, RoundedCornerShape(12.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing here yet", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Add an add-on and its catalogs fill this screen.",
                    color = MutedC, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                Button(
                    onClick = onGoAddons,
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.focusRing(RoundedCornerShape(12.dp)),
                ) { Text("Add an add-on", fontWeight = FontWeight.SemiBold) }
            }
            st.rows.isEmpty() && st.loading && st.continueRows.isEmpty() -> {
                // the page's shape before its pictures: the hero's footprint, then two rows of cards
                val br = shimmerBrush()
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * 0.34f).dp).background(br))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        repeat(2) { i ->
                            Box(Modifier.padding(top = 22.dp, bottom = 10.dp).width(160.dp).height(18.dp)
                                .clip(RoundedCornerShape(9.dp)).background(br))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(if (i == 0) 3 else 5) {
                                    Box(Modifier.width(if (i == 0) 210.dp else 124.dp).aspectRatio(if (i == 0) 16f / 9f else 2f / 3f)
                                        .clip(RoundedCornerShape(12.dp)).background(br))
                                }
                            }
                        }
                    }
                }
            }
            st.rows.isEmpty() && st.continueRows.isEmpty() -> Box(Modifier.padding(top = 30.dp)) { HomeNoRows(st, onCustomise) }
            // The remote's landing spot, on a real control: View Details on the hero, else the first Continue watching
            // card, else the first card — once the rows have settled. (A focusGroup's own Enter search started from
            // the list's top-left and picked the first card UNDER the hero; the pivot scroll then cropped two thirds
            // of the hero off.) Only from the top, and that is deliberate: coming back from a title page the list is
            // where the viewer left it, and the card they left takes focus instead (Modifier.returnTo).
            else -> {
            val heroOn = st.rows.isNotEmpty() && Prefs.showHero
            val cwOn = st.continueRows.isNotEmpty() && Prefs.showContinue
            val land = tvFirstFocus(
                ready = st.listState.firstVisibleItemIndex == 0 && st.listState.firstVisibleItemScrollOffset == 0 &&
                    (st.rows.isNotEmpty() || !st.loading),
            )
            LazyColumn(
                state = st.listState,
                contentPadding = PaddingValues(bottom = navPadBottom()),
                modifier = Modifier.focusGroup(),
            ) {
                // the hero's slot is ALWAYS the first item, empty until the rows arrive: Continue watching paints
                // first, and a hero inserted above it later left the list anchored on Continue watching — the hero
                // sat off-screen above, for anyone with something in progress
                // (1 dp, not nothing: a list treats a zero-height first item as off-screen and anchors on the next one)
                item(key = "hero") {
                    if (heroOn) HeroHeader(st.rows, onOpen, detailsModifier = Modifier.focusRequester(land))
                    else Spacer(Modifier.fillMaxWidth().height(1.dp))
                }
                if (cwOn) item(key = "continue") {
                    Column {
                        Box(Modifier.padding(horizontal = 16.dp)) { RowHeader("Continue watching", null, null) }
                        LazyRow(
                            state = st.rowStates.getOrPut("continue") { LazyListState() },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            itemsIndexed(st.continueRows, key = { _, rec -> Progress.key(rec.type, rec.id) }) { ci, r ->
                                ContinueCard(
                                    r,
                                    Modifier.returnTo("cw/" + Progress.key(r.type, r.id))
                                        .then(if (!heroOn && ci == 0) Modifier.focusRequester(land) else Modifier)
                                        .width(continueCardWidth()),
                                    onClick = { onResume(r) },
                                    onLongClick = { sheetFor = r },
                                )
                            }
                        }
                    }
                }
                itemsIndexed(st.rows, key = { _, row -> row.addon.manifestUrl + "/" + row.catalog.type + "/" + row.catalog.id }) { ri, r ->
                    val rowKey = r.addon.manifestUrl + "/" + r.catalog.type + "/" + r.catalog.id
                    val mine = st.rows.filter { it.addon.manifestUrl == r.addon.manifestUrl }
                    val multi = mine.size > 1
                    Column {
                        // one catalog: the add-on's name is the row. Several: the
                        // catalog leads and the add-on becomes the eyebrow, with the
                        // type appended only when the same name serves two types
                        val sameName = mine.count { it.catalog.name.equals(r.catalog.name, true) } > 1
                        val title = if (!multi) r.addon.name
                            else r.catalog.name + (if (sameName) " · " + typeLabel(r.catalog.type) else "")
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            // the add-on eyebrow beside the title: Settings › Home › Add-on names under rows
                            RowHeader(title, if (multi && Prefs.rowSubline) r.addon.name else null) {
                                onSeeAll(r.addon, r.catalog)
                            }
                        }
                        LazyRow(
                            state = st.rowStates.getOrPut(rowKey) { LazyListState() },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            // keyed by title: a rebuild that reorders a catalogue keeps focus on the same title
                            itemsIndexed(r.items, key = { _, m -> m.type + ":" + m.id }) { mi, m ->
                                MetaCard(
                                    m,
                                    Modifier.returnTo("row/$rowKey/${m.type}:${m.id}")
                                        .then(if (!heroOn && !cwOn && ri == 0 && mi == 0) Modifier.focusRequester(land) else Modifier)
                                        .width(rowCardWidth(m)),
                                ) { onOpen(r.addon, m) }
                            }
                        }
                    }
                }
                // add-ons unreachable but Continue watching kept the screen useful
                if (st.rows.isEmpty() && !st.loading) item(key = "retry") {
                    Box(Modifier.padding(top = 24.dp)) { HomeNoRows(st, onCustomise) }
                }
                // the way into the arrangement, at the foot of the rows it arranges
                if (st.rows.isNotEmpty()) item(key = "foot") {
                    Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
                        TextAction("Customise Home", onClick = onCustomise)
                    }
                }
            }
            }
        }
    }
}

/** Home with nothing to show: every row switched off by hand is not an outage. */
@Composable
private fun HomeNoRows(st: HomeUiState, onCustomise: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (st.hidden > 0 && st.wanted == 0) {
            Text("Every row is switched off.", color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
            Chip("Customise Home", false, onClick = onCustomise)
        } else {
            Text("Couldn’t reach your add-ons right now.", color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
            Chip("Retry", false) { st.invalidate() }
        }
    }
}

// ---------- search (one query, every add-on) ----------
@Composable
private fun SearchScreen(st: SearchUiState, onOpen: (Addon, MetaItem) -> Unit, onAddon: (Addon) -> Unit = {}) {
    // results as you type: the effect restarts on every keystroke, so the
    // delay only survives once typing pauses
    LaunchedEffect(st.query) {
        val q = st.query.trim()
        if (q.isEmpty()) { st.submitted = ""; return@LaunchedEffect }
        if (q.length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(450)
        if (q != st.submitted) st.submitted = q
    }
    val ctx = LocalContext.current
    LaunchedEffect(st.submitted) {
        val q = st.submitted.trim()
        // cleared mid-search: the run this key change cancelled never reached its `searching = false`,
        // and a stuck flag would hold the skeleton up in place of Discover
        if (q.isEmpty()) { st.sections = emptyList(); st.searchedFor = null; st.searching = false; return@LaunchedEffect }
        if (q == st.searchedFor && st.sections.isNotEmpty() && !st.searching) return@LaunchedEffect
        // the sections on screen belong to no finished query until this one ends: a refine cancelled mid-way left another
        // query's partial rows standing under this one's name, and the early return above then kept them
        st.searching = true; st.searchedFor = null
        val out = mutableListOf<CatRow>()
        st.sections = emptyList()
        for (a in activeAddons(ctx)) {
            runCatching {
                // An add-on usually advertises search on several catalogs (Cinemeta
                // has one for movies and one for series) — query them all, or a
                // search for a show only ever returns films.
                val cats = manifestFor(a.manifestUrl).catalogs.filter { it.search }.take(4)
                if (cats.isEmpty()) return@runCatching
                val merged = mutableListOf<MetaItem>()
                val seen = HashSet<String>()
                for (sc in cats) {
                    val items = runCatching { Stremio.loadCatalog(a.base, sc, null, q) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        .getOrDefault(emptyList())
                    for (m in items) if (seen.add(m.type + ":" + m.id)) merged.add(m)
                    if (merged.isNotEmpty()) {
                        // one section per add-on, refreshed as its catalogs answer
                        val row = CatRow(a, cats.first(), merged.toList())
                        val idx = out.indexOfFirst { it.addon.manifestUrl == a.manifestUrl }
                        if (idx >= 0) out[idx] = row else out.add(row)
                        st.sections = out.toList()
                    }
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
        st.searchedFor = q
        st.searching = false
    }
    val typing = tvTyping()
    val tvBox = remember(ctx) { Account.isTv(ctx) }
    val clearable = st.query.isNotEmpty() || st.submitted.isNotEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        Text("Search", color = TextC, fontSize = 34.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = st.query,
            onValueChange = { st.query = it },
            placeholder = { Text("Search across your add-ons", color = MutedC) },
            singleLine = true,
            // a TV types after OK (tvTyping): the remote landing here no longer throws the keyboard over the screen
            readOnly = typing.readOnly,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MutedC) },
            // inside the field the D-pad can never reach it (a child of the focused field), so a TV gets it beside
            trailingIcon = {
                if (clearable && !tvBox) {
                    IconButton(onClick = { st.query = ""; st.submitted = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = MutedC)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                st.submitted = st.query.trim()
                RecentSearches.note(ctx, st.submitted)
            }),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                focusedTextColor = TextC, unfocusedTextColor = TextC,
            ),
            // the remote lands on the box the screen exists for; Down from it reaches Discover
            modifier = typing.modifier.weight(1f)
                .returnTo("search-field")
                .focusRequester(tvFirstFocus()),
        )
        if (clearable && tvBox) IconButton(
            onClick = { st.query = ""; st.submitted = "" },
            modifier = Modifier.padding(start = 8.dp).focusRing(CircleShape, landing = false),
        ) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextC) }
        }
        when {
            st.searching && st.sections.isEmpty() -> Column {
                SkelBox(Modifier.padding(top = 18.dp, bottom = 10.dp).width(170.dp).height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { SkeletonCell(Modifier.width(210.dp)) }
                }
            }
            st.submitted.isNotBlank() && st.sections.isEmpty() ->
                Text("No matches for “${st.submitted.trim()}”.", color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            st.submitted.isBlank() -> DiscoverSection(
                ctx, st.discover, Modifier.weight(1f),
                onOpen = onOpen,
            ) {
                SearchIdle(
                    ctx, remember { activeAddons(ctx) },
                    onRecent = { q -> st.query = q; st.submitted = q; RecentSearches.note(ctx, q) },
                    onAddon = onAddon,
                )
            }
            else -> LazyColumn(state = st.listState, contentPadding = PaddingValues(bottom = navPadBottom())) {
                items(st.sections, key = { it.addon.manifestUrl + "/" + it.catalog.id }) { r ->
                    Column {
                        RowHeader(r.addon.name, "${r.items.size} result" + (if (r.items.size > 1) "s" else ""), null)
                        LazyRow(
                            state = st.rowStates.getOrPut(r.addon.manifestUrl + "/" + r.catalog.id) { LazyListState() },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(r.items) { m ->
                                MetaCard(m, Modifier.returnTo("s/${r.addon.manifestUrl}/${m.type}:${m.id}").width(rowCardWidth(m))) {
                                    // a result opened straight from the as-you-type list counts as a search worth keeping
                                    RecentSearches.note(ctx, st.submitted)
                                    onOpen(r.addon, m)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- add-ons (manage sources) ----------
@Composable
private fun AddonsScreen(version: Int, onBack: () -> Unit, onOpen: (Addon) -> Unit, onAddonsChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var addons by remember(version) { mutableStateOf(loadAddons(ctx)) }
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var statusErr by remember { mutableStateOf(false) }
    var statusDone by remember { mutableStateOf(false) }     // "Added X": wears a check

    // ---- ranking ----
    // Rows are a fixed height, so a drag is just "how many rows have I passed",
    // which keeps this to arithmetic instead of a layout-info crawl.
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var liftIndex by remember { mutableStateOf(-1) }      // picked up with the D-pad
    var rowSpanPx by remember { mutableStateOf(0f) }
    val screenEntry = LocalScreenEntry.current
    val urlTyping = tvTyping()
    val keyboard = LocalSoftwareKeyboardController.current
    // a remote lands on the first add-on (the page opened with nothing lit, and the first press was spent)
    val firstRow = tvFirstFocus(ready = addons.isNotEmpty())
    /** Add the add-on in the field — the button, or Done on the keyboard (Enter did nothing). */
    fun addNow() {
        // a stremio:// install link is the manifest address behind a scheme only Stremio registers
        val u = url.trim().replace(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")
        if (!Regex("manifest\\.json").containsMatchIn(u)) {
            status = "Enter a manifest URL (…/manifest.json)"; statusErr = true; statusDone = false; return
        }
        status = "Adding…"; statusErr = false; statusDone = false
        scope.launch {
            runCatching { Stremio.loadManifest(u).addon }.onSuccess { a ->
                val list = (addons.filterNot { it.manifestUrl == a.manifestUrl } + a)
                saveAddons(ctx, list); addons = list; url = ""; onAddonsChanged()
                status = "Added ${a.name}"; statusErr = false; statusDone = true
            }.onFailure { status = "Could not load: ${it.message}"; statusErr = true; statusDone = false }
        }
    }
    /** Move an add-on and persist the new ranking. Returns where it landed. */
    fun rankMove(from: Int, to: Int): Int {
        if (from < 0 || from >= addons.size || to < 0 || to >= addons.size || from == to) return from
        val next = addons.toMutableList()
        next.add(to, next.removeAt(from))
        Cloud.noteAddonOrder(ctx)
        saveAddons(ctx, next)
        addons = next
        onAddonsChanged()
        return to
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                BackBar("Add-ons", null, onBack)
                Text(
                    "Add an add-on by its manifest URL and its catalogs show up on Home.",
                    color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(SurfaceC, RoundedCornerShape(12.dp))
                    .border(1.dp, LineC, RoundedCornerShape(12.dp))
                    .padding(18.dp)
            ) {
                Text("ADD-ON MANIFEST URL", color = MutedC, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    placeholder = { Text("https://your-addon/…/manifest.json", color = MutedC) },
                    singleLine = true,
                    readOnly = urlTyping.readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { addNow(); keyboard?.hide() }),
                    modifier = urlTyping.modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Line2,
                        cursorColor = Red,
                        focusedTextColor = TextC,
                        unfocusedTextColor = TextC,
                    ),
                )
                Row(Modifier.padding(top = 12.dp)) {
                    Button(
                        onClick = { addNow() },
                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.focusRing(RoundedCornerShape(12.dp)),
                    ) { Text("Add add-on", fontWeight = FontWeight.SemiBold) }
                }
                if (status.isNotEmpty()) {
                    // the palette's own ink and a check, not an off-palette green
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (statusDone) Icon(Icons.Filled.Check, contentDescription = null, tint = TextC, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                        Text(status, color = when { statusErr -> Color(0xFFFF6B6B); statusDone -> TextC; else -> MutedC }, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp)) {
                Text("Your add-ons", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (addons.size > 1) Text(
                    "Drag by the handle to rank them. Home rows follow this order, and streams, " +
                        "artwork and subtitles are taken from the highest add-on that has them.",
                    color = MutedC, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (addons.isEmpty()) {
            item { Text("No add-ons yet — paste a manifest URL above.", color = MutedC, fontSize = 14.sp) }
        } else {
            itemsIndexed(addons, key = { _, a -> a.manifestUrl }) { i, a ->
                val dragging = i == dragIndex
                val lifted = i == liftIndex
                val raised = dragging || lifted
                Box(
                    Modifier
                        .zIndex(if (raised) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            val s = if (lifted) 1.02f else 1f
                            scaleX = s; scaleY = s
                            shadowElevation = if (raised) 18.dp.toPx() else 0f
                            shape = RoundedCornerShape(12.dp)
                            clip = false
                        }
                        .onSizeChanged {
                            if (rowSpanPx == 0f) rowSpanPx = it.height + with(density) { 10.dp.toPx() }
                        }
                        .then(if (dragging) Modifier else Modifier.animateItem()),
                ) {
                // A container, not one big button: the part that opens the add-on, On/Off, the grip and Remove are
                // SIBLINGS, so the D-pad walks between them. Inside one clickable card they were its children, and a
                // focused node's children are out of the arrows' reach — on a TV only "open" ever worked.
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (raised) Surface2 else SurfaceC, RoundedCornerShape(12.dp))
                        .border(1.dp, if (raised) Color(0x3DFFFFFF) else LineC, RoundedCornerShape(12.dp))
                        .padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FocusCard(
                        shape = RoundedCornerShape(10.dp), zoom = false,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                            .returnTo("addon/" + a.manifestUrl)
                            .then(if (i == 0) Modifier.focusRequester(firstRow) else Modifier),
                        onClick = { onOpen(a) },
                    ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (a.logo != null) {
                            AsyncImage(
                                model = a.logo, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                                    .alpha(if (a.enabled) 1f else 0.45f),
                            )
                        } else {
                            Box(
                                Modifier.size(44.dp)
                                    .background(if (a.enabled) Red else Surface2, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(a.name.take(1).uppercase(), color = if (a.enabled) OnAccent else MutedC, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Column(Modifier.weight(1f).alpha(if (a.enabled) 1f else 0.55f)) {
                            Text(a.name, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (a.enabled) a.manifestUrl.removePrefix("https://").removePrefix("http://")
                                else "Off · feeds nothing until switched on",
                                color = MutedC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                        // On/Off without removing: a switched-off add-on stays installed and ranked but
                        // feeds no rows, search results, streams or subtitles
                        Chip(if (a.enabled) "On" else "Off", a.enabled, inSeg = true) {
                            val list = addons.map { if (it.manifestUrl == a.manifestUrl) it.copy(enabled = !it.enabled) else it }
                            saveAddons(ctx, list); addons = list; onAddonsChanged()
                        }
                        if (addons.size > 1) {
                            val grip = remember { MutableInteractionSource() }
                            val gripFocused by grip.collectIsFocusedAsState()
                            Box(
                                Modifier.size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (gripFocused) TextC else Color.Transparent)
                                    // A remote can't drag, so OK picks the row up
                                    // and the D-pad walks it.
                                    .onKeyEvent { ev ->
                                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                        val here = addons.indexOfFirst { it.manifestUrl == a.manifestUrl }
                                        if (here < 0) return@onKeyEvent false
                                        when (ev.key) {
                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                liftIndex = if (liftIndex == here) -1 else here
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                true
                                            }
                                            Key.DirectionUp ->
                                                if (liftIndex == here) { liftIndex = rankMove(here, here - 1); true } else false
                                            Key.DirectionDown ->
                                                if (liftIndex == here) { liftIndex = rankMove(here, here + 1); true } else false
                                            Key.Back ->
                                                if (liftIndex >= 0) { liftIndex = -1; true } else false
                                            else -> false
                                        }
                                    }
                                    // clickable, not focusable: it swallows the tap
                                    // that would otherwise open the add-on, and still
                                    // gives the D-pad something to land on
                                    .clickable(interactionSource = grip, indication = null) {}
                                    .pointerInput(a.manifestUrl) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                // read the index now: the row may have
                                                // moved since this gesture was wired up
                                                dragIndex = addons.indexOfFirst { it.manifestUrl == a.manifestUrl }
                                                dragOffset = 0f
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                if (dragIndex < 0) return@detectDragGesturesAfterLongPress
                                                dragOffset += amount.y
                                                val span = rowSpanPx
                                                if (span <= 0f) return@detectDragGesturesAfterLongPress
                                                val steps = (dragOffset / span).roundToInt()
                                                if (steps != 0) {
                                                    val landed = rankMove(dragIndex, dragIndex + steps)
                                                    if (landed != dragIndex) {
                                                        dragOffset -= (landed - dragIndex) * span
                                                        dragIndex = landed
                                                    }
                                                }
                                            },
                                            onDragEnd = { dragIndex = -1; dragOffset = 0f },
                                            onDragCancel = { dragIndex = -1; dragOffset = 0f },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.DragHandle, contentDescription = "Reorder",
                                    tint = if (gripFocused) Color.Black else if (raised) TextC else FaintC,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                // the row takes focus with it: hand it to the next add-on (or the one before)
                                val at = addons.indexOfFirst { it.manifestUrl == a.manifestUrl }
                                val nb = addons.getOrNull(at + 1) ?: addons.getOrNull(at - 1)
                                val list = addons.filterNot { it.manifestUrl == a.manifestUrl }
                                saveAddons(ctx, list); addons = list; onAddonsChanged()
                                if (nb != null) ReturnFocus.handTo(screenEntry, "addon/" + nb.manifestUrl)
                            },
                            modifier = Modifier.size(36.dp).focusRing(CircleShape, landing = false),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MutedC, modifier = Modifier.size(18.dp))
                        }
                }
                }
            }
        }
    }
}


// ---------- settings ----------
/** A section's eyebrow plus the one-line subtitle under it, as the TV console reads. */
@Composable
internal fun SettingsHeader(text: String, sub: String? = null) {
    Column(Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp)) {
        Text(text, color = MutedC, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        if (sub != null) Text(sub, color = FaintC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(SurfaceC, RoundedCornerShape(18.dp))
            .border(1.dp, LineC, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)),
    ) { content() }
}

/** One settings row: icon tile, title, subtitle. Pass onClick = null for a plain fact row. */
@Composable
internal fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    divider: Boolean,
    onClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.returnTo("row/$title").landingSlot() else Modifier)
                .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier)
                .then(rowLit(focused))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(Surface2, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (divider) Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(1.dp).background(LineC))
    }
}

@Composable
private fun SettingsScreen(
    onAddons: () -> Unit,
    onLayout: () -> Unit,
    onSupport: () -> Unit,
    onHome: () -> Unit,
    onPlayback: () -> Unit,
    onStreams: () -> Unit,
    onProfile: () -> Unit,
    onParty: () -> Unit,
    onFriends: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var updSub by remember { mutableStateOf("You're on v$version — tap to check now") }
    var checking by remember { mutableStateOf(false) }
    // "3 add-ons · 2 on · 1 off" — the list is read on every entry to this screen
    val addonsSub = remember {
        val all = loadAddons(ctx)
        val off = all.count { !it.enabled }
        when {
            all.isEmpty() -> "Add, rank and manage your add-ons"
            off == 0 -> "${all.size} add-on${if (all.size > 1) "s" else ""}, all on · Add, rank and manage"
            else -> "${all.size} add-on${if (all.size > 1) "s" else ""} · ${all.size - off} on · $off off"
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            // this screen already answered a remote — the requester only makes the first press
            // land somewhere chosen rather than wherever the default traversal decides
            .focusRequester(tvFirstFocus())
            .focusGroup(),
    ) {
        Text(
            "Settings", color = TextC, fontSize = 34.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp, modifier = Modifier.padding(top = 24.dp),
        )
        // Essential · Everything: the same pages either way; the pages hide their rarer rows in Essential
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp)) {
            Segmented {
                Chip("Essential", !Prefs.everything, inSeg = true) { Prefs.setSetMode(ctx, "essential") }
                Chip("Everything", Prefs.everything, inSeg = true) { Prefs.setSetMode(ctx, "all") }
            }
            Text(
                "Essential keeps the common choices. Everything shows every option.",
                color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        ProfileCard(onProfile)
        SettingsHeader("GENERAL", "How Nebula looks, plays and connects")
        SettingsGroup {
            SettingsRow(Icons.Filled.Palette, "Appearance", "Accent, surface, text, font and cards", true, onLayout)
            SettingsRow(Icons.Filled.Home, "Home", "Featured, Continue Watching, rows and title pages", true, onHome)
            SettingsRow(Icons.Filled.PlayArrow, "Playback", "Player, next episode, languages and subtitles", true, onPlayback)
            // the Streams page holds Everything rows only (no E mark in the brief), so Essential does not list it
            if (Prefs.everything) SettingsRow(Icons.Filled.FilterList, "Streams", "Order, quality floor, details and picking by itself", true, onStreams)
            SettingsRow(Icons.Filled.Extension, "Add-ons", addonsSub, true, onAddons)
            SettingsRow(Icons.Filled.Groups, "Watch party", "Watch in sync with friends using a code", true, onParty)
            SettingsRow(Icons.Filled.Favorite, "Friends", "Rate, share and recommend — experimental", true, onFriends)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Party name", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("How you appear to friends in a party", color = MutedC, fontSize = 13.sp)
                }
                val ctx2 = LocalContext.current
                var pname by remember {
                    mutableStateOf(ctx2.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("party_name", "") ?: "")
                }
                val pnameTyping = tvTyping()
                OutlinedTextField(
                    value = pname,
                    onValueChange = { v ->
                        pname = v.take(40)
                        ctx2.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("party_name", pname.trim()).apply()
                    },
                    placeholder = { Text(Cloud.profile?.name?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL.take(24), color = MutedC) },
                    singleLine = true,
                    // walking past this row on the way to Advanced threw the keyboard up on a TV (tvTyping)
                    readOnly = pnameTyping.readOnly,
                    modifier = pnameTyping.modifier.width(150.dp).returnTo("party-name"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
            }
        }
        if (Prefs.everything) {
            SettingsHeader("ADVANCED", "The welcome message, a full reset, caches")
            SettingsGroup {
                SettingsRow(Icons.Filled.Tune, "Advanced", "Welcome message, reset all settings, clear caches", false, onAdvanced)
            }
        }
        // Hidden until the server has somewhere to send people (or this profile is already a supporter)
        if (Support.visible) {
            SettingsHeader("SUPPORT", "Free, no ads — chip in if you like")
            SettingsGroup {
                SettingsRow(
                    Icons.Filled.VolunteerActivism, "Support Nebula",
                    "Chip in if you like — a mark by your name, three more accents, the wall",
                    false, onSupport,
                )
            }
        }
        SettingsHeader("ABOUT", "Version, updates and privacy")
        SettingsGroup {
            SettingsRow(Icons.Filled.Download, "Check for updates", updSub, true) {
                if (!checking) {
                    checking = true; updSub = "Checking…"
                    scope.launch {
                        val r = runCatching { Updates.latest() }.getOrNull()
                        updSub = when {
                            r == null -> "Could not reach the release feed — try again later"
                            Updates.isNewer(r.version, version) -> "v${r.version} is available — the update card is waiting on Home"
                            else -> "You're up to date (v$version)"
                        }
                        checking = false
                    }
                }
            }
            SettingsRow(Icons.Filled.Shield, "Privacy Policy", "No email, no tracking — how Nebula handles data", true) {
                // many TVs have no browser: say where to read it rather than do nothing
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.rifflehq.in/privacy.html"))) }
                    .onFailure { Toasts.show("No web browser on this device — read it at play.rifflehq.in/privacy.html") }
            }
            SettingsRow(Icons.Filled.Info, "Nebula for Android", "v$version · plays every add-on format, on-device", false, null)
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun SettingsSubtitlesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackBar("Subtitle style", null, onBack)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            SubStylePanel(onDone = onBack)
        }
    }
}

/** A titled switch row for the settings pages. */
@Composable
internal fun SettingsToggle(title: String, sub: String, checked: Boolean, divider: Boolean = true, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth()
            .returnTo("toggle/$title").landingSlot()
            // a finger keeps its ripple; the remote's lit state is rowLit's
            .clickable(interactionSource = interaction, indication = LocalIndication.current) { onChange(!checked) }
            .then(rowLit(focused))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccent, checkedTrackColor = Red,
                uncheckedThumbColor = Color(0xFF8E8E93), uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
    if (divider) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(LineC))
}

/** A titled segmented control: the choices sit inside one hairline pill. */
@Composable
internal fun SettingsChips(title: String, sub: String?, options: List<Pair<String, String>>, selected: String, divider: Boolean = true, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = MutedC, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        // return and landing sit on the CHOSEN chip (on the strip they reached only its leftmost, through the
        // scroller's own focus search)
        Box(Modifier.padding(top = 10.dp)) {
            Segmented {
                options.forEach { o ->
                    val on = selected == o.first
                    Chip(o.second, on, inSeg = true, modifier = if (on) Modifier.returnTo("chips/$title").landingSlot() else Modifier) { onPick(o.first) }
                }
            }
        }
    }
    if (divider) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(LineC))
}

/**
 * A setting with more answers than a chip strip can hold.
 *
 * Seventeen languages fitted in a scrolling row of chips. The real list does not: on a remote a
 * forty-chip strip is half a minute of pressing Right to reach Vietnamese, which is a worse
 * answer to "can you add more subtitle languages" than not adding them. This is the Discover
 * picker — a sheet with a scrolling list and a tick on the current row — so the long lists
 * behave the same way everywhere, and the row itself says what is chosen without opening it.
 *
 * [options] are `value to label`, the shape the prefs already use; PickSheet wants them the
 * other way round.
 */
@Composable
internal fun SettingsPick(
    title: String,
    sub: String?,
    options: List<Pair<String, String>>,
    selected: String,
    divider: Boolean = true,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val label = options.firstOrNull { it.first == selected }?.second
        ?: selected.ifEmpty { options.firstOrNull()?.second.orEmpty() }
    if (open) PickSheet(
        title = title,
        options = options.map { it.second to it.first },
        current = selected,
        onPick = onPick,
        onDismiss = { open = false },
    )
    Column(
        Modifier.fillMaxWidth()
            .returnTo("pick/$title").landingSlot()
            .clickable(interactionSource = interaction, indication = null) { open = true }
            .then(rowLit(focused))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(label, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        if (sub != null) Text(sub, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
    }
    if (divider) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(LineC))
}

@Composable
private fun SettingsLayoutScreen(onBack: () -> Unit, onSupport: () -> Unit) {
    val ctx = LocalContext.current
    val all = Prefs.everything
    val rank = Support.rank()
    val sup = rank >= 1
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Appearance", null, onBack)
        SettingsHeader("ACCENT", "The one colour across the app")
        SettingsGroup {
            // swatch grid: three per row, tick on the current one
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                // the three supporter colours ride at the end of the same grid, locked until the mark is there
                // Supporter Plus's six more ride behind them once one is a supporter (locked below plus)
                (Prefs.ACCENTS + Prefs.SUP_ACCENTS + (if (sup) Prefs.PLUS_ACCENTS else emptyList())).chunked(3).forEachIndexed { ri, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = if (ri == 0) 0.dp else 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { (key, label, color) ->
                            val need = Prefs.accentRank(key)
                            val locked = need > rank
                            val what = if (need >= 2) "a Supporter Plus colour" else "a supporter colour"
                            // a locked colour still stored as the pref shows the fallback ticked, not itself
                            val on = Prefs.activeAccent == key
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(84.dp).focusRing(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (locked) { Toasts.show("$label is $what."); onSupport() }
                                        else Prefs.setAccent(ctx, key)
                                    }.padding(vertical = 6.dp),
                            ) {
                                Box(
                                    Modifier.size(52.dp).clip(CircleShape)
                                        .background(if (locked) color.copy(alpha = .45f) else color)
                                        .border(if (on) 3.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (locked) Icon(
                                        Icons.Filled.Lock, contentDescription = "Supporter colour",
                                        tint = Color.White, modifier = Modifier.size(20.dp),
                                    )
                                    else if (on) Text("✓", color = if (key == "white") Color.Black else Color.White,
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    label, color = if (on) TextC else MutedC, fontSize = 12.sp,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1, modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        // keep a short last row left-packed at the same rhythm
                        repeat(3 - row.size) { Box(Modifier.width(84.dp)) }
                    }
                }
                if (!sup) Text(
                    "Gold, Ice and Mint are supporter colours.",
                    color = FaintC, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
                // any colour at all (Supporter Plus): a hue slider, the swatch beside it shows the pick
                if (rank >= 2) {
                    val own = Prefs.accent.startsWith("#")
                    var hue by remember { mutableStateOf(if (own) hueOf(Prefs.accent) else 0f) }
                    Text("Any colour", color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color.hsv(hue, 0.82f, 0.96f))
                                .border(if (own) 3.dp else 0.dp, if (own) Color.White else Color.Transparent, CircleShape),
                        )
                        Slider(
                            value = hue, onValueChange = { hue = it },
                            onValueChangeFinished = { Prefs.setAccent(ctx, hexOf(Color.hsv(hue, 0.82f, 0.96f))) },
                            valueRange = 0f..359f,
                            // this Material version's slider takes focus but no keys: left and right walk the hue here
                            // (round the wheel), each step taken at once, since a remote has no "finger lifted"
                            modifier = Modifier.weight(1f).focusRing(RoundedCornerShape(50)).onKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                val d = when (e.key) { Key.DirectionLeft -> -12f; Key.DirectionRight -> 12f; else -> return@onKeyEvent false }
                                hue = ((hue + d) % 360f + 360f) % 360f
                                if (hue > 359f) hue = 359f
                                Prefs.setAccent(ctx, hexOf(Color.hsv(hue, 0.82f, 0.96f)))
                                true
                            },
                        )
                    }
                }
            }
        }
        if (all) {
            SettingsHeader("LOOK", "Surfaces, type and motion")
            SettingsGroup {
                SettingsChips(
                    "Surface", "How dark the app's panels are · Pure black suits OLED screens",
                    listOf("black" to "Black", "pure" to "Pure black", "graphite" to "Graphite"), Prefs.surface,
                ) { Prefs.setSurface(ctx, it) }
                SettingsChips(
                    "Text size", "Everything scales with it",
                    listOf("compact" to "Compact", "standard" to "Standard", "large" to "Large"), Prefs.textSize,
                ) { Prefs.setTextSize(ctx, it) }
                SettingsChips(
                    "Font", "Nebula's own face, or the one your device uses",
                    listOf("geist" to "Geist", "system" to "System"), Prefs.font,
                ) { Prefs.setFont(ctx, it) }
                SettingsChips(
                    "Focus zoom", "How much a card grows under a remote's focus",
                    listOf("subtle" to "Subtle", "standard" to "Standard", "bold" to "Bold"), Prefs.focusZoom,
                ) { Prefs.setFocusZoom(ctx, it) }
                SettingsChips(
                    "Motion", "Fades and card growth · Reduced cuts and keeps still",
                    listOf("full" to "Full", "reduced" to "Reduced"), Prefs.motion, divider = false,
                ) { Prefs.setMotion(ctx, it) }
            }
        }
        SettingsHeader("CARDS", "Posters everywhere: Home, search, catalogs and My List")
        SettingsGroup {
            if (all) {
                SettingsChips(
                    "Card size", "How large cards render everywhere",
                    listOf("0.85" to "Small", "1.0" to "Standard", "1.18" to "Large"),
                    when (Prefs.posterScale) { 0.85f -> "0.85"; 1.18f -> "1.18"; else -> "1.0" },
                ) { Prefs.setPosterScale(ctx, it.toFloat()) }
                SettingsChips(
                    "Card corners", "How rounded the artwork is cut",
                    listOf("square" to "Square", "rounded" to "Rounded", "round" to "Round"), Prefs.cardCorners,
                ) { Prefs.setCardCorners(ctx, it) }
                SettingsChips(
                    "Under posters", "What a card says beneath its artwork",
                    listOf("both" to "Title and year", "title" to "Title only", "none" to "Nothing"), Prefs.posterLabels,
                ) { Prefs.setPosterLabels(ctx, it) }
            }
            SettingsToggle("Ratings on posters", "The star figure in the corner of a card", Prefs.ratings, divider = false) { Prefs.setRatings(ctx, it) }
        }
    }
}

@Composable
private fun SettingsPlaybackScreen(onBack: () -> Unit, onSubtitles: () -> Unit) {
    val ctx = LocalContext.current
    val all = Prefs.everything
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Playback", null, onBack)
        SettingsHeader("PLAYER", if (all) "Gestures, speed, the controls, picture quality and the buffer" else "Picture quality and the buffer")
        SettingsGroup {
            // Essential keeps Picture quality alone here; every other row is an Everything row (the brief's E marks)
            if (all) {
                SettingsToggle(
                    "Touch gestures",
                    "Double-tap the edges to skip ${Prefs.seekStep}s, swipe to seek",
                    Prefs.gestures,
                ) { Prefs.setGestures(ctx, it) }
                SettingsToggle(
                    "Hold to speed",
                    "Hold the player surface to race ahead; release to resume",
                    Prefs.holdSpeed,
                ) { Prefs.setHoldSpeed(ctx, it) }
                SettingsChips(
                    "Hold speed", null,
                    listOf("1.5" to "1.5×", "2.0" to "2×", "3.0" to "3×"),
                    when (Prefs.holdRate) { 1.5f -> "1.5"; 3.0f -> "3.0"; else -> "2.0" },
                ) { Prefs.setHoldRate(ctx, it.toFloat()) }
                SettingsChips(
                    "Skip by", "Tap zones, the transport buttons and remote steps",
                    listOf("5" to "5 s", "10" to "10 s", "15" to "15 s", "30" to "30 s"), Prefs.seekStep.toString(),
                ) { Prefs.setSeekStep(ctx, it.toInt()) }
                SettingsChips(
                    "Speed when a video starts", "Last used remembers the speed you chose",
                    listOf("1" to "1×", "1.25" to "1.25×", "1.5" to "1.5×", "last" to "Last used"), Prefs.speedDefault,
                ) { Prefs.setSpeedDefault(ctx, it) }
                SettingsChips(
                    "Controls hide after", "How long the controls stay once you stop touching",
                    listOf("2" to "2 s", "3.5" to "3.5 s", "6" to "6 s"),
                    when (Prefs.controlsHide) { 2f -> "2"; 6f -> "6"; else -> "3.5" },
                ) { Prefs.setControlsHide(ctx, it.toFloat()) }
                SettingsToggle("Clock while controls show", "The time, and when the video ends, top right", Prefs.clock) { Prefs.setClock(ctx, it) }
                SettingsToggle("Pause board", "Title and synopsis a moment after you pause", Prefs.pauseBoard) { Prefs.setPauseBoard(ctx, it) }
                SettingsChips(
                    "Right time pill", "What the pill at the end of the bar counts · a tap on it flips this too",
                    listOf("left" to "Time left", "total" to "Total"), Prefs.timeDisplay,
                ) { Prefs.setTimeDisplay(ctx, it) }
            }
            if (all) SettingsToggle(
                "Preview frames while scrubbing",
                "A picture of where you are about to jump · Turn it off if playback stutters on your TV",
                Prefs.scrubFrames,
            ) { Prefs.setScrubFrames(ctx, it) }
            SettingsChips(
                "Picture quality",
                "Auto adapts to your connection · Start high opens at the best rendition · Data saver caps at 720p",
                listOf("auto" to "Auto", "high" to "Start high", "saver" to "Data saver"),
                Prefs.quality,
            ) { Prefs.setQuality(ctx, it) }
            if (all) SettingsChips(
                "Resolution cap", "Never fetch a picture taller than this, whatever the connection allows",
                listOf("0" to "No cap", "720" to "720p", "1080" to "1080p", "1440" to "1440p"), Prefs.maxRes.toString(),
            ) { Prefs.setMaxRes(ctx, it.toInt()) }
            // the one knob for the spinner, so it shows in Essential too; the load control is built with the player (PlayerExtras.kt)
            SettingsChips(
                "Buffer ahead", "How much video is loaded ahead of you · Longer rides out a shaky connection but takes more memory · From the next video on",
                listOf("0" to "Auto", "60" to "1 min", "120" to "2 min", "240" to "4 min"), Prefs.buffer.toString(),
                divider = Account.isTv(ctx),
            ) { Prefs.setBuffer(ctx, it.toInt()) }
            // Play through your PC (Relay.kt): the TV only — a computer on the network holds the video for it
            if (Account.isTv(ctx)) {
                LaunchedEffect(Prefs.relay) { Relay.refresh(ctx) }
                SettingsChips(
                    "Play through your PC",
                    Relay.status.ifEmpty { "A computer on your network that is sharing fetches and holds the video for this TV — far more than the TV's own memory can" },
                    listOf("auto" to "Auto", "off" to "Off"), Prefs.relay,
                    divider = false,
                ) { Prefs.setRelay(ctx, it) }
            }
        }
        SettingsHeader("NEXT EPISODE", "Rolling on, skipping ahead and coming back")
        SettingsGroup {
            SettingsToggle(
                "Auto-play next episode",
                "Count down and roll into the next episode when one ends",
                Prefs.autoPlayNext,
            ) { Prefs.setAutoPlayNext(ctx, it) }
            if (all) {
                SettingsChips(
                    "Offer the next episode", "When the card appears",
                    listOf("25" to "25 s before the end", "45" to "45 s", "60" to "60 s", "credits" to "Only in the credits"), Prefs.upnextAt,
                ) { Prefs.setUpnextAt(ctx, it) }
                SettingsChips(
                    "Autoplay countdown", "How long the card counts before it plays",
                    listOf("5" to "5 s", "8" to "8 s", "15" to "15 s"), Prefs.countdown.toString(),
                ) { Prefs.setCountdown(ctx, it.toInt()) }
                SettingsChips(
                    "Still watching?", "Pause the run after a few episodes in a row",
                    listOf("0" to "Off", "3" to "After 3", "5" to "After 5"), Prefs.stillWatching.toString(),
                ) { Prefs.setStillWatching(ctx, it.toInt()) }
            }
            SettingsChips(
                "Skip intros and recaps",
                "A pill appears over an episode's recap and opening titles, or Auto jumps past them for you · Timestamps come from a community database, looked up by episode",
                listOf("button" to "Show button", "auto" to "Auto", "off" to "Off"),
                Prefs.skipIntro,
                divider = all,
            ) { Prefs.setSkipIntro(ctx, it) }
            if (all) SettingsChips(
                "When you come back", "Starting point for something you have started · Ask offers both before the first play",
                listOf("resume" to "Resume", "ask" to "Ask", "startover" to "Start over"), Prefs.resume,
                divider = false,
            ) { Prefs.setResume(ctx, it) }
        }
        if (all) {
            SettingsHeader("LANGUAGES", "Preferred audio and subtitle tracks")
            SettingsGroup {
                SettingsPick(
                    "Audio language", "Preferred track when a stream carries several",
                    Prefs.LANGS, Prefs.audioLang,
                ) { Prefs.setAudioLang(ctx, it) }
                SettingsPick(
                    "Subtitle language", "Preferred captions when a stream carries several",
                    Prefs.LANGS, Prefs.subLang,
                ) { Prefs.setSubLang(ctx, it) }
                SettingsPick(
                    "Second choice", "Used when the first language is missing",
                    Prefs.LANGS_NONE, Prefs.subLang2,
                ) { Prefs.setSubLang2(ctx, it) }
                SettingsChips(
                    "Subtitles when a video starts",
                    "Off until you pick · your preferred language when the stream has it · Always shows the first track when nothing matches",
                    listOf("off" to "Off", "preferred" to "Preferred language", "always" to "Always"), Prefs.subStart,
                    divider = false,
                ) { Prefs.setSubStart(ctx, it) }
            }
        }
        SettingsHeader("SUBTITLES", "How captions look on screen")
        SettingsGroup {
            SettingsRow(Icons.Filled.ClosedCaption, "Subtitle style", "Size, colour, background, edge, font, position and bold", false, onSubtitles)
        }
    }
}

@Composable
private fun SettingsPartyScreen(onBack: () -> Unit, onJoin: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackBar("Watch party", null, onBack)
        PartyPanel(onJoin)
    }
}

@Composable
private fun PartyPanel(onJoin: (String) -> Unit) {
    var partyCode by remember { mutableStateOf("") }
    val typing = tvTyping()
    Column(
        Modifier.fillMaxWidth()
            .background(SurfaceC, RoundedCornerShape(12.dp))
            .border(1.dp, LineC, RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        Text("WATCH PARTY", color = MutedC, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = partyCode, onValueChange = { partyCode = it },
                placeholder = { Text("Party code", color = MutedC) },
                singleLine = true,
                readOnly = typing.readOnly,
                // Done joins, as the button does (it did nothing)
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done, autoCorrectEnabled = false,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                ),
                keyboardActions = KeyboardActions(onDone = { onJoin(partyCode) }),
                modifier = typing.modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Line2,
                    cursorColor = Red,
                    focusedTextColor = TextC,
                    unfocusedTextColor = TextC,
                ),
            )
            Button(
                onClick = { onJoin(partyCode) },
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.focusRing(RoundedCornerShape(12.dp)),
            ) { Text("Join", fontWeight = FontWeight.SemiBold) }
        }
        Text(
            "To start one: play a stream, then tap the party button in the player. Friends enter your code here and watch in sync.",
            color = MutedC, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Episodes Surprise me can land on: aired ones, from the regular seasons
    unless specials are all the show has (web parity). */
private fun surprisePool(eps: List<Episode>): List<Episode> {
    val today = java.time.LocalDate.now()
    val aired = eps.filter { e ->
        val d = e.released?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() }
        d == null || !d.isAfter(today)
    }
    return aired.filter { it.season != 0 }.ifEmpty { aired }
}
private var lastSurprise: String? = null
/** A random episode for a comfort show — never the same one twice running. */
private fun surprisePick(pool: List<Episode>): Episode? {
    val p = if (pool.size > 1) pool.filter { it.id != lastSurprise } else pool
    val v = p.randomOrNull()
    lastSurprise = v?.id
    return v
}

// ---------- detail (one title's page: art, facts, actions) ----------
@Composable
private fun DetailScreen(
    addon: Addon,
    item: MetaItem,
    onBack: () -> Unit,
    onEpisodes: () -> Unit,
    onPlayMovie: () -> Unit,
    onResumeEpisode: (ProgressRec) -> Unit,
    onPlayEpisode: (Episode, PlayIntent) -> Unit = { _, _ -> },
) {
    val ctx = LocalContext.current
    val ck = item.type + ":" + item.id
    val detailTv = remember(ctx) { Account.isTv(ctx) }
    val screenEntry = LocalScreenEntry.current
    // Back from Streams or the player comes back to the season the viewer was on (not the cursor's) and the list where
    // it was — the page leaves composition, and it used to rebuild at the top of the cursor's season every time
    val backHere = remember { ReturnFocus.backTo(screenEntry) }
    var full by remember(ck) { mutableStateOf(metaFullCache[ck]) }
    var metaTried by remember(ck) { mutableStateOf(metaFullCache[ck] != null) }
    var inList by remember(ck) { mutableStateOf(Library.inList(ctx, item.type, item.id)) }
    var recOpen by remember(ck) { mutableStateOf(false) }
    var moreOpen by remember(ck) { mutableStateOf(false) }
    val pageScope = rememberCoroutineScope()
    if (recOpen) RecommendSheet(item.type, item, pageScope) { recOpen = false }

    LaunchedEffect(ck) {
        if (full != null) return@LaunchedEffect
        val order = listOf(addon) + activeAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
        for (a in order) {
            val m = runCatching {
                if (a.manifestUrl != addon.manifestUrl &&
                    !manifestFor(a.manifestUrl).canMeta(item.type, item.id)) return@runCatching null
                Stremio.loadFullMeta(a.base, item.type, item.id)
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            if (m != null) { metaFullCache[ck] = m; full = m; break }
        }
        metaTried = true
    }

    // bumped whenever an episode is marked by hand, so everything that reads the
    // progress store on this page is re-read — the hero button's LABEL above all,
    // which otherwise goes on offering "Resume S1E3" for an episode just ticked off
    var marks by remember(ck) { mutableIntStateOf(0) }
    val resume = remember(ck, full, marks, Progress.syncVersion) {
        if (item.type == "series") seriesResumeRec(ctx, item.id)
        else Progress.get(ctx, item.type, item.id)?.takeIf {
            !it.done && !it.dismissed && it.pos >= Progress.MIN_POS_MS && it.dur > 0 && it.pos <= it.dur - Progress.END_GAP_MS
        }
    }

    Box(Modifier.fillMaxSize()) {
        val detailList = rememberLazyListState()
        // full-bleed backdrop; the scrim exists only so type stays legible
        // The art stops 2 dp short of the scrim's solid bottom: 430 dp is a fractional pixel height, and the last row,
        // half-covered by both layers, let the picture bleed through as a hairline seam under the header.
        // It leaves with the page: drifting up at half the scroll and dissolving by the time the header has scrolled its
        // own height (seen on an emulator: held still, the Play pill, the cast and the stars slid over the picture).
        Box(Modifier.fillMaxWidth().height(430.dp).clipToBounds().graphicsLayer {
            val off = if (detailList.firstVisibleItemIndex == 0) detailList.firstVisibleItemScrollOffset.toFloat() else size.height
            if (!Prefs.reducedMotion) translationY = -off * 0.5f
            alpha = (1f - off / size.height).coerceIn(0f, 1f)
        }) {
            val art = full?.background ?: item.background ?: item.poster
            if (art != null) {
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(428.dp))
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x52000000), 0.32f to Color(0x1F000000),
                        0.7f to Color(0xC7000000), 0.96f to Bg, 1f to Bg,   // the page's colour (Graphite is not black)
                    )
                )
            )
        }
        // series episodes live inline on this page (one scroll, like the big apps)
        var episodes by remember(ck) { mutableStateOf<List<Episode>>(emptyList()) }
        var selectedSeason by remember(ck) { mutableStateOf<Int?>(null) }
        var upNextId by remember(ck) { mutableStateOf<String?>(null) }
        var epsLoading by remember(ck) { mutableStateOf(item.type == "series") }
        if (item.type == "series") LaunchedEffect(ck, full, metaTried) {
            // the full meta comes from the same /meta endpoint and already carries
            // the videos — re-fetching them was a second identical request per open
            var found: List<Episode> = full?.videos.orEmpty()
            if (found.isEmpty()) {
                if (!metaTried) return@LaunchedEffect            // still loading — wait for it
                val order = listOf(addon) + activeAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
                for (a in order) {
                    val ok = runCatching {
                        if (a.manifestUrl != addon.manifestUrl && !manifestFor(a.manifestUrl).canMeta(item.type, item.id)) return@runCatching false
                        val vids = Stremio.loadSeriesVideos(a.base, item.type, item.id)
                        if (vids.isNotEmpty()) { found = vids; true } else false
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                    if (ok) break
                }
            }
            epsLoading = false
            if (found.isEmpty()) return@LaunchedEffect
            episodes = found
            seriesChain.set(item.type, item.name, addon, found)
            // open at the viewer's place in the show, next-up highlighted
            // `seat` not `upNext`: a finished show has no ring but still belongs on
            // its last season, not back on season 1
            val cur = seriesCursor(ctx, item.type, found)
            upNextId = cur?.upNext?.id
            val kept = if (backHere) screenEntry?.let { keptSeasons[it] }?.takeIf { k -> found.any { it.season == k } } else null
            selectedSeason = kept ?: cur?.seat?.season
                ?: found.map { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it })).firstOrNull()
        }
        LaunchedEffect(selectedSeason) { val sn = selectedSeason; if (sn != null && screenEntry != null) keptSeasons[screenEntry] = sn }
        // a pull that applies progress while this page is open must move the ring too
        LaunchedEffect(Progress.syncVersion) {
            if (episodes.isNotEmpty()) upNextId = seriesUpNext(ctx, item.type, episodes)?.id
        }
        val bySeason = episodes.groupBy { it.season }
        val seasons = bySeason.keys.sortedWith(compareBy({ it == 0 }, { it }))
        val currentSeason = selectedSeason ?: seasons.firstOrNull()
        val eps = (bySeason[currentSeason] ?: emptyList()).sortedBy { it.episode ?: 0 }

        // the list's place, put back once the episodes are there to scroll to (the page rebuilds with only its header)
        val keptPlace = remember { if (backHere) screenEntry?.let { keptDetailPlaces[it] } else null }
        var placed by remember { mutableStateOf(keptPlace == null) }
        LaunchedEffect(eps.size, epsLoading) {
            if (placed || keptPlace == null) return@LaunchedEffect
            if (item.type == "series" && eps.isEmpty() && epsLoading) return@LaunchedEffect
            runCatching { detailList.scrollToItem(keptPlace.first, keptPlace.second) }
            placed = true
        }
        DisposableEffect(Unit) {
            onDispose { if (screenEntry != null) keptDetailPlaces[screenEntry] = detailList.firstVisibleItemIndex to detailList.firstVisibleItemScrollOffset }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp), state = detailList) {
        item { Column {
        BackBar("", null, onBack)
        Spacer(Modifier.height(160.dp))
        val logoArt = full?.logo ?: item.logo
        if (logoArt != null) {
            AsyncImage(
                model = logoArt, contentDescription = item.name,
                modifier = Modifier.height(96.dp).padding(bottom = 10.dp),
                alignment = Alignment.CenterStart,
            )
        } else {
            Text(
                item.name, color = TextC, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp, lineHeight = 37.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val facts = listOfNotNull(
            full?.releaseInfo ?: item.releaseInfo,
            full?.runtime,
            full?.videos?.map { it.season }?.filter { it > 0 }?.distinct()?.size
                ?.takeIf { it > 0 }?.let { "$it season" + (if (it > 1) "s" else "") },
        ).joinToString(" · ")   // (the web's "   ·   " is collapsed by HTML; spelled out here it read as three gaps)
        // facts in the mono register; the IMDb figure is a plate, not a badge
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (facts.isNotEmpty()) Eyebrow(facts, color = Color(0xD1FFFFFF))
            (full?.imdbRating ?: item.imdbRating)?.let { r ->
                Text(
                    "★ $r", color = Color.White, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        (full?.description ?: item.description)?.let {
            Text(it, color = Color(0xCCFFFFFF), fontSize = 14.sp, lineHeight = 21.sp, maxLines = 7,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        }
        // Genres and cast on title pages: Settings › Home
        full?.genres?.takeIf { it.isNotEmpty() && Prefs.detailGenres }?.let { gs ->
            NamePills(gs.take(6), TextC, Color(0x38FFFFFF), detailTv, Modifier.padding(top = 14.dp), FontWeight.Medium)
        }
        // The Play pill and the round buttons share one height and one geometry
        // (a full pill beside full circles, as the Home hero's pill is): a 40dp
        // 12dp-cornered button beside 48dp circles read as a different control
        // that had wandered in from another screen (the Founder's phone, 09-16).
        val playPill = remember { MutableInteractionSource() }
        val playFocused by playPill.collectIsFocusedAsState()
        Row(
            Modifier.padding(top = 16.dp, bottom = 24.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    // recompute at click time — the remembered copy can lag a
                    // just-finished episode when returning from playback
                    val r = if (item.type == "series") seriesResumeRec(ctx, item.id) else null
                    when {
                        item.type == "series" && r != null -> onResumeEpisode(
                            if (r.addonUrl.isEmpty()) r.copy(addonUrl = addon.manifestUrl) else r
                        )
                        item.type == "series" -> {
                            // nothing half-watched, but episodes may still be ticked off —
                            // continue the show from up next rather than restarting it at episode 1
                            val go = seriesUpNext(ctx, item.type, episodes)
                                ?: episodes.sortedWith(compareBy({ it.season == 0 }, { it.season }, { it.episode ?: 0 })).firstOrNull()
                            if (go != null) onPlayEpisode(go, PlayIntent.TAP) else onEpisodes()
                        }
                        else -> onPlayMovie()
                    }
                },
                interactionSource = playPill,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                // the remote's landing spot on a title page, and a white pill cannot wear a
                // white focus ring — it grows the way a card does instead
                modifier = Modifier.height(48.dp)
                    .returnTo("play")
                    .focusRequester(tvFirstFocus())
                    .scale(if (playFocused && !Prefs.reducedMotion) 1.06f else 1f),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    when {
                        item.type == "series" && resume != null -> resumeLabel(item.id, resume.id)
                        resume != null -> "Resume"
                        else -> "Play"
                    },
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp),
                )
            }
            // The secondary actions are round icons rather than a row of ghost pills:
            // four of them fit where two pills did, which is what keeps Cast above
            // the fold. Marking watched has a VISIBLE control here at last — until
            // now the only way to it was holding an episode row, which nothing
            // advertised.
            val watched = remember(ck, marks, Progress.syncVersion) {
                item.type == "movie" && Progress.get(ctx, item.type, item.id)?.done == true
            }
            if (item.type == "movie") RoundAction(
                if (watched) Icons.Filled.CheckCircle else Icons.Filled.CheckCircleOutline,
                if (watched) "Mark as not watched" else "Mark as watched",
                on = watched,
            ) {
                if (watched) Progress.markUnwatched(ctx, item.type, item.id)
                else Progress.markWatched(ctx, item.type, item.id)
                marks++
                Toasts.show(if (watched) "Marked as not watched." else "Marked as watched.")
            }
            RoundAction(
                if (inList) Icons.Filled.Check else Icons.Filled.Add,
                if (inList) "Remove from My List" else "Add to My List",
                on = inList,
            ) { inList = Library.toggle(ctx, item.type, item, addon.manifestUrl) }
            val pool = if (item.type == "series") surprisePool(episodes) else emptyList()
            if (pool.size >= 2 || Social.on) RoundAction(Icons.Filled.MoreVert, "More") { moreOpen = true }
        }
        // the cast comes AFTER the actions: above them it pushed Play under the floating nav pill on a 360 dp phone
        full?.cast?.takeIf { it.isNotEmpty() && Prefs.detailCast }?.let { cast ->
            Eyebrow("Cast", Modifier.padding(top = 4.dp, bottom = 8.dp))
            NamePills(cast.take(8), MutedC, LineC, detailTv, Modifier.padding(bottom = 16.dp))
        }
        if (moreOpen) {
            val pool = if (item.type == "series") surprisePool(episodes) else emptyList()
            CardSheet(
                title = item.name, sub = null, poster = full?.poster ?: item.poster,
                shape = item.posterShape,
                actions = buildList {
                    // a random aired episode for a comfort show (web parity)
                    if (pool.size >= 2) add(SheetAction(Icons.Filled.Shuffle, "Surprise me") {
                        surprisePick(pool)?.let { onPlayEpisode(it, PlayIntent.TAP) }
                    })
                    if (Social.on) add(SheetAction(Icons.Filled.Favorite, "Recommend to a friend") { recOpen = true })
                },
                onDismiss = { moreOpen = false },
            )
        }
        RatingStars(item)

        // Trailers. A catalogue only ever names a video id, never a file, so the
        // card is a thumbnail built from that id and the tap hands the link to
        // whatever the phone opens it with — we are not a trailer player.
        full?.trailers?.takeIf { it.isNotEmpty() }?.let { trailers ->
            Eyebrow("Trailers", Modifier.padding(top = 20.dp, bottom = 10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(trailers.size) { i ->
                    val t = trailers[i]
                    Column(Modifier.width(174.dp).focusRing(RoundedCornerShape(10.dp)).clickable {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + t.key))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure { Toasts.show("Nothing on this device opens video links.") }
                    }) {
                        AsyncImage(
                            model = "https://img.youtube.com/vi/" + t.key + "/hqdefault.jpg",
                            contentDescription = t.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp)).background(SurfaceC),
                        )
                        Text(
                            t.title, color = MutedC, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        // Movie Details: the facts that do not fit the line under the title, as a
        // plain two-column table with a hairline between rows.
        val details = listOfNotNull(
            (full?.releaseInfo ?: item.releaseInfo)?.let { "Release Info" to it },
            full?.runtime?.let { "Runtime" to it },
            full?.country?.let { "Origin Country" to it },
            full?.director?.takeIf { it.isNotEmpty() }?.let { "Director" to it.joinToString(", ") },
            full?.writer?.takeIf { it.isNotEmpty() }?.let { "Writer" to it.joinToString(", ") },
            full?.genres?.takeIf { it.isNotEmpty() }?.let { "Genres" to it.take(4).joinToString(", ") },
        )
        // On a TV the table is one focusable block: with trailers above it, its last rows sat below the last thing
        // the remote could reach, and a list only scrolls as far as focus goes
        if (details.isNotEmpty()) Column(
            Modifier.fillMaxWidth().then(if (detailTv) Modifier.focusRing(RoundedCornerShape(12.dp)).focusable() else Modifier),
        ) {
            Text(
                if (item.type == "series") "Show Details" else "Movie Details",
                color = TextC, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            details.forEachIndexed { i, (k, v) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LineC))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(k, color = MutedC, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        v, color = TextC, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        } }   // header item

        if (item.type == "series") {
            if (seasons.size > 1) item(key = "seasons") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = chipEdge(), end = chipEdge(), bottom = 14.dp)) {
                    items(seasons.size) { i ->
                        val sn = seasons[i]
                        Chip(if (sn == 0) "Specials" else "Season $sn", sn == currentSeason, modifier = Modifier.returnTo("season/$sn")) { selectedSeason = sn }
                    }
                }
            }
            if (epsLoading) items(4) { SkeletonRow(112.dp, 63.dp, circle = false) }
            items(eps.size, key = { eps[it].id }) { i ->
                val ep = eps[i]
                EpisodeRow(
                    itemType = item.type, ep = ep, upNext = ep.id == upNextId, first = i == 0,
                    modifier = Modifier.returnTo("ep/" + ep.id),
                    seriesPoster = full?.poster ?: item.poster,
                    onClick = { onPlayEpisode(ep, PlayIntent.TAP) },
                    onResume = { onPlayEpisode(ep, PlayIntent.RESUME) },
                    onStartOver = { onPlayEpisode(ep, PlayIntent.START_OVER) },
                    // a mark moves "Up next"; the season on screen deliberately stays put
                    onMarked = { marks++; upNextId = seriesUpNext(ctx, item.type, episodes)?.id },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

/** Genre and cast names as outlined pills: a sideways row under a finger, wrapped lines on a TV — plain text in a lazy
    row can never be scrolled to by a remote, so the names past the edge were out of reach there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NamePills(names: List<String>, ink: Color, line: Color, wrap: Boolean, modifier: Modifier = Modifier, weight: FontWeight? = null) {
    val pill: @Composable (String) -> Unit = { n ->
        Text(
            n, color = ink, fontSize = 13.sp, fontWeight = weight,
            modifier = Modifier.border(1.dp, line, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
    if (wrap) FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { names.forEach { pill(it) } }
    else LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(names.size) { i -> pill(names[i]) } }
}

/** One episode in a list: thumb, a mono eyebrow (number · air date), the name,
    the overview beneath. Rows are transparent and divided by hairlines — a
    list, not a stack of tiles — and only the target row wears a mark. */
@Composable
private fun EpisodeRow(
    itemType: String,
    ep: Episode,
    upNext: Boolean,
    first: Boolean,
    modifier: Modifier = Modifier,
    seriesPoster: String? = null,
    onClick: () -> Unit,
    onResume: () -> Unit = onClick,
    onStartOver: () -> Unit = onClick,
    onMarked: () -> Unit = {},
) {
    val ctx = LocalContext.current
    // the tick is read here rather than passed in, so a mark made in the sheet
    // repaints this row without the whole list being rebuilt
    var stamp by remember(itemType, ep.id) { mutableIntStateOf(0) }
    var sheet by remember(itemType, ep.id) { mutableStateOf(false) }
    if (sheet) EpisodeSheet(
        itemType = itemType, ep = ep, seriesPoster = seriesPoster,
        // the sheet's own Play/Resume is a deliberate choice, unlike a row tap
        onPlay = onResume,
        onStartOver = onStartOver,
        onChanged = { stamp++; onMarked() },
        onDismiss = { sheet = false },
    )
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(LineC))
        // a full-width row wears the ring rather than growing past the screen's edges
        // (both screens that list episodes land on their own row, so this is never the fallback landing)
        FocusCard(
            shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth(), zoom = false, landing = false,
            onClick = onClick, onLongClick = { sheet = true },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val pr = remember(stamp, Progress.syncVersion, itemType, ep.id) { Progress.get(ctx, itemType, ep.id) }
                Box {
                    val thumbMod = Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceC)
                    if (ep.thumbnail != null) {
                        AsyncImage(model = ep.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = thumbMod)
                    } else {
                        Box(thumbMod, contentAlignment = Alignment.Center) {
                            Text(ep.episode?.toString() ?: "•", color = FaintC, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                    if (pr?.done == true) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                .background(Color(0xD10B0B0F), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Check, contentDescription = "Watched", tint = TextC, modifier = Modifier.size(13.dp)) }
                    } else if (pr != null && pr.pos > 0 && pr.dur > 0) {
                        Box(Modifier.align(Alignment.BottomStart).width(112.dp).height(4.dp).background(Color(0x8C000000))) {
                            Box(Modifier.fillMaxWidth((pr.pos.toFloat() / pr.dur).coerceIn(0f, 1f)).fillMaxSize().background(Red))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    // "Episode 3 · 23 Jun 2022"; the number is dropped when the
                    // name is only "Episode 3" already
                    val date = epAirDate(ep)
                    val generic = Regex("""^episode\s*\d+$""", RegexOption.IGNORE_CASE).matches(ep.name.trim())
                    val kick = listOfNotNull(ep.episode?.takeIf { !generic }?.let { "Episode $it" }, date).joinToString(" · ")
                    if (kick.isNotEmpty()) Eyebrow(kick, Modifier.padding(bottom = 3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ep.name.ifEmpty { "Episode ${ep.episode ?: ""}".trim() },
                            color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (upNext) Text(
                            "Up next", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                                .background(FillC, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (!ep.overview.isNullOrEmpty()) {
                        Text(ep.overview, color = MutedC, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

/**
 * Held on an episode: play it, or tick it off by hand. A row that has never
 * been played can only be marked watched; one with a tick or a part-way
 * position can be put back. The mark written is deliberately the same record
 * playing to the end leaves, so the list, the series cursor and Continue
 * watching all agree afterwards.
 */
@Composable
private fun EpisodeSheet(
    itemType: String,
    ep: Episode,
    seriesPoster: String?,
    onPlay: () -> Unit,
    onStartOver: () -> Unit,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val pr = remember(itemType, ep.id) { Progress.get(ctx, itemType, ep.id) }
    val resumable = remember(pr) {
        pr != null && !pr.done && !pr.dismissed && pr.pos >= Progress.MIN_POS_MS &&
            pr.dur > 0 && pr.pos <= pr.dur - Progress.END_GAP_MS
    }
    val tag = "S${ep.season}" + (ep.episode?.let { "E$it" } ?: "")
    val kick = "Season ${ep.season}" + (ep.episode?.let { " · Episode $it" } ?: "")
    val left = if (resumable && pr != null) fmtLeft(pr.dur - pr.pos) else null
    val actions = buildList {
        add(SheetAction(Icons.Filled.PlayArrow, if (resumable) "Resume" else "Play") { onPlay() })
        if (resumable) add(SheetAction(Icons.Filled.Replay, "Start over") { onStartOver() })
        if (pr?.done != true) add(SheetAction(Icons.Filled.CheckCircle, "Mark as watched") {
            Progress.markWatched(ctx, itemType, ep.id); onChanged()
            Toasts.show("$tag marked as watched.")
        })
        // Only worth offering against something to undo. Two names for one call on
        // purpose: against a tick it means "not watched"; against a part-way episode
        // it THROWS AWAY a position, and a label that does not say so is a trap.
        val ticked = pr?.done == true
        if (ticked || resumable) add(SheetAction(
            Icons.Filled.RemoveCircleOutline,
            if (ticked) "Mark as not watched" else "Forget my place",
        ) {
            Progress.markUnwatched(ctx, itemType, ep.id); onChanged()
            Toasts.show(if (ticked) "$tag marked as not watched." else "$tag back to the start.")
        })
    }
    CardSheet(
        title = ep.name.ifEmpty { "Episode ${ep.episode ?: ""}".trim() },
        sub = listOfNotNull(kick, epAirDate(ep), left).joinToString(" · "),
        // a thumbnail-less episode falls back to the show's own poster rather than
        // the two-letter placeholder box
        poster = ep.thumbnail ?: seriesPoster,
        shape = if (ep.thumbnail != null) "landscape" else "poster",
        actions = actions,
        onDismiss = onDismiss,
    )
}

// ---------- catalog ----------
@Composable
private fun CatalogScreen(addon: Addon, initial: CatalogRef?, st: CatalogUiState, onBack: () -> Unit, onOpen: (MetaItem) -> Unit) {
    var catalogs by st::catalogs
    var current by st::current
    var genre by st::genre
    var query by st::query
    var submitted by st::submitted
    // results as you type — the restart-on-keystroke delay is the debounce
    LaunchedEffect(st.query) {
        val q = st.query.trim()
        if (q.isEmpty()) { if (st.submitted.isNotEmpty()) st.submitted = ""; return@LaunchedEffect }
        if (q.length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(450)
        if (q != st.submitted) st.submitted = q
    }
    var items by st::items
    var loading by st::loading
    var status by st::status

    LaunchedEffect(addon, initial) {
        if (catalogs.isNotEmpty()) {
            // arriving via a "See all" that targets a different catalog of this addon —
            // apply it once, so coming back from a stream keeps the user's own switch
            if (initial != null && initial != st.appliedInitial) {
                st.appliedInitial = initial
                val want = catalogs.firstOrNull { it.type == initial.type && it.id == initial.id }
                if (want != null && current != want) { current = want; genre = null; query = ""; submitted = "" }
            }
            return@LaunchedEffect
        }
        runCatching { Stremio.loadManifest(addon.manifestUrl).catalogs }
            .onSuccess {
                catalogs = it
                st.appliedInitial = initial
                current = initial?.let { i -> it.firstOrNull { c -> c.type == i.type && c.id == i.id } } ?: it.firstOrNull()
                if (it.isEmpty()) { status = "No catalogs."; loading = false }
            }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; status = "Failed: ${it.message}"; loading = false }
    }
    // A run cancelled by a newer genre, catalog or query must not write over it: every onFailure below rethrows
    // cancellation, or a stale run would print "Failed: … was cancelled" and stop paging on the new list.
    LaunchedEffect(current, genre, submitted) {
        val q = submitted.trim()
        // Re-entering the screen (back from streams) relaunches this effect; if the
        // items on hand already match the wanted state, keep them instead of refetching.
        val want = Triple(current, genre, q)
        if (st.loadedFor == want) return@LaunchedEffect
        if (q.isNotEmpty()) {
            val sc = if (current?.search == true) current else catalogs.firstOrNull { it.search }
            if (sc == null) { items = emptyList(); status = "Search isn’t available here."; loading = false; return@LaunchedEffect }
            loading = true; status = "Searching…"; items = emptyList()
            runCatching { Stremio.loadCatalog(addon.base, sc, null, q) }
                .onSuccess { items = it; status = if (it.isEmpty()) "No matches for “$q”." else "${it.size} result${if (it.size > 1) "s" else ""} for “$q”"; loading = false; st.loadedFor = want }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; status = "Failed: ${it.message}"; loading = false; st.loadedFor = null }
        } else {
            val c = current ?: return@LaunchedEffect
            loading = true; status = "Loading…"; items = emptyList()
            st.fetched = 0; st.pageDone = false
            runCatching { Stremio.loadCatalog(addon.base, c, genre) }
                .onSuccess {
                    items = it
                    st.fetched = it.size
                    // stop paging on an empty page, or when the catalog never advertised `skip`
                    st.pageDone = it.isEmpty() || !c.skip
                    status = if (it.isEmpty()) "No items." else "${it.size} items" + (if (st.pageDone) "" else " — scroll for more")
                    loading = false; st.loadedFor = want
                }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; status = "Failed: ${it.message}"; loading = false; st.loadedFor = null; st.pageDone = true }
        }
    }

    // Catalogs arrive a page at a time; `skip` walks them. Everything past the
    // first page used to be unreachable.
    val reachedEnd by remember {
        androidx.compose.runtime.derivedStateOf {
            val last = st.gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= st.gridState.layoutInfo.totalItemsCount - 12
        }
    }
    // ONE collector per (catalog, genre, query), which never cancels itself and asks for one page at a time. Keyed
    // effects that bailed out on `paging`/`loading` (neither a key) could relaunch while a cancelled page's finally still
    // waited on its blocking call, return early, and not ask for page 2 until reachedEnd flipped again; keyed on the
    // flag itself, setting it cancelled the request. The flow does not say `true` twice running, so while the grid
    // still ends here once the new page is laid out (a short page), the loop asks again itself. reachedEnd reads the
    // LAST layout, so each check waits two frames for the grid to lay out what just landed.
    LaunchedEffect(current, genre, submitted) {
        if (submitted.trim().isNotEmpty()) return@LaunchedEffect      // search results aren't paged
        val c = current ?: return@LaunchedEffect
        snapshotFlow { reachedEnd && !st.pageDone && !st.paging && !loading }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                withFrameNanos {}; withFrameNanos {}
                while (reachedEnd && !st.pageDone && !st.paging && !loading) {
                    if (items.size >= 1000) { st.pageDone = true; break }   // ceiling for TV memory
                    st.paging = true
                    // cancelled mid-page (a new genre or query), the flag must still come down, or paging stays off for good
                    try { runCatching { Stremio.loadCatalog(addon.base, c, genre, null, st.fetched) }
                        .onSuccess { page ->
                            st.fetched += page.size
                            val seen = items.mapTo(HashSet()) { it.type + ":" + it.id }
                            val fresh = page.filter { seen.add(it.type + ":" + it.id) }
                            if (fresh.isNotEmpty()) items = items + fresh
                            st.pageDone = page.isEmpty() || fresh.isEmpty()
                            status = "${items.size} items" + (if (st.pageDone) "" else " — scroll for more")
                        }
                        .onFailure {
                            if (it is kotlinx.coroutines.CancellationException) throw it
                            st.pageDone = true
                        }
                    } finally { st.paging = false }
                    withFrameNanos {}; withFrameNanos {}
                }
            }
    }

    val typing = tvTyping()
    val ctx = LocalContext.current
    val tvBox = remember(ctx) { Account.isTv(ctx) }
    val clearable = query.isNotEmpty() || submitted.isNotEmpty()
    // the skeleton's shimmer, only while there is a skeleton (the grid's builder is not a composable)
    val br = if (loading && items.isEmpty()) shimmerBrush() else null
    // search and the chip rows: on a TV they ride at the top of the grid and scroll away with it (fixed above it they
    // took ~226 dp of the 540, leaving one row of posters with the focused one cut off); a phone keeps them fixed
    val header: @Composable () -> Unit = {
        Column {
            if (catalogs.any { it.search }) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search ${addon.name}", color = MutedC) },
                    singleLine = true,
                    readOnly = typing.readOnly,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MutedC) },
                    trailingIcon = {
                        if (clearable && !tvBox) {
                            IconButton(onClick = { query = ""; submitted = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = MutedC)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                    modifier = typing.modifier.weight(1f).returnTo("catalog-search"),
                )
                // inside the field the D-pad can never reach it, so a TV gets it beside
                if (clearable && tvBox) IconButton(
                    onClick = { query = ""; submitted = "" },
                    modifier = Modifier.padding(start = 8.dp).focusRing(CircleShape, landing = false),
                ) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextC) }
            }
            if (catalogs.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = chipEdge()), modifier = Modifier.padding(bottom = 8.dp)) {
                    items(catalogs) { c ->
                        Chip(c.name, c == current && submitted.isEmpty(), modifier = Modifier.returnTo("cat/${c.type}/${c.id}")) {
                            current = c; genre = null; query = ""; submitted = ""
                        }
                    }
                }
            }
            if (submitted.isEmpty()) current?.genres?.take(20)?.let { gs ->
                if (gs.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = chipEdge()), modifier = Modifier.padding(bottom = 10.dp)) {
                    items(gs) { g -> Chip(g, genre == g, modifier = Modifier.returnTo("genre/$g")) { genre = if (genre == g) null else g } }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        BackBar(addon.name, status, onBack)
        if (!tvBox) header()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp * Prefs.posterScale),
            state = st.gridState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (loading && items.isEmpty()) 12.dp else 14.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (tvBox) item(key = "head", span = { GridItemSpan(maxLineSpan) }) { header() }
            if (br != null) {
                items(12) {
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(br))
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(12.dp)).background(br))
                    }
                }
            } else {
                items(items) { m -> MetaCard(m, Modifier.returnTo("c/${m.type}:${m.id}")) { onOpen(m) } }
                // a further page is in flight — tail the grid with placeholders
                if (st.paging) items(6) { SkeletonCell() }
            }
        }
    }
}

// ---------- streams ----------
// Series episode picker: fetch the series' meta `videos`, group by season, and
// let the user pick an episode (whose streams are then loaded like any item).
@Composable
private fun EpisodesScreen(
    addon: Addon,
    item: MetaItem,
    onBack: () -> Unit,
    onPlayEpisode: (Episode, PlayIntent) -> Unit,
    onFallback: () -> Unit,
) {
    val ctx = LocalContext.current
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var status by remember { mutableStateOf("Loading episodes…") }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var upNextId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        val order = listOf(addon) + activeAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
        var found: List<Episode> = emptyList()
        for (a in order) {
            val ok = runCatching {
                // origin add-on is always asked; others only if they advertise meta for this type/id
                if (a.manifestUrl != addon.manifestUrl && !manifestFor(a.manifestUrl).canMeta(item.type, item.id)) return@runCatching false
                val vids = Stremio.loadSeriesVideos(a.base, item.type, item.id)
                if (vids.isNotEmpty()) { found = vids; true } else false
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
            if (ok) break
        }
        if (found.isEmpty()) { onFallback(); return@LaunchedEffect }
        episodes = found
        // hand the chain to the player so it can offer the next episode
        seriesChain.set(item.type, item.name, addon, found)
        status = "${found.size} episodes"
        // open at the viewer's place in the show: the season of the newest
        // watched/in-progress episode; a finished episode advances to the next
        // `seat` not `upNext`: a finished show has no ring but still belongs on its
        // last season, not back on season 1
        val cur = seriesCursor(ctx, item.type, found)
        upNextId = cur?.upNext?.id
        selectedSeason = cur?.seat?.season
            ?: found.map { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it })).firstOrNull()
    }

    // a pull that applies progress while this screen is open must move the ring too
    LaunchedEffect(Progress.syncVersion) {
        if (episodes.isNotEmpty()) upNextId = seriesUpNext(ctx, item.type, episodes)?.id
    }
    val bySeason = episodes.groupBy { it.season }
    val seasons = bySeason.keys.sortedWith(compareBy({ it == 0 }, { it }))
    val current = selectedSeason ?: seasons.firstOrNull()
    val eps = (bySeason[current] ?: emptyList()).sortedBy { it.episode ?: 0 }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        BackBar(item.name, status, onBack)
        if (seasons.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = chipEdge(), end = chipEdge(), bottom = 12.dp)) {
                items(seasons, key = { it }) { s ->
                    Chip(if (s == 0) "Specials" else "Season $s", s == current) { selectedSeason = s }
                }
            }
        }
        val listState = rememberLazyListState()
        // Land on "Up next" when the list first fills and when the season changes —
        // but NOT when a mark moves the marker under the reader's thumb, so the
        // scroll key deliberately leaves upNextId out.
        var scrolledFor by remember { mutableStateOf<Pair<Int?, Int>?>(null) }
        LaunchedEffect(current, upNextId, eps.size) {
            val k = current to eps.size
            if (scrolledFor == k) return@LaunchedEffect
            if (eps.isEmpty()) return@LaunchedEffect          // still filling — do not claim the key
            scrolledFor = k
            // a season with no up-next in it starts at the top: one LazyListState is
            // shared across seasons, so leaving it alone lands the next season
            // wherever the last one was scrolled to
            val i = eps.indexOfFirst { it.id == upNextId }
            listState.scrollToItem(if (i >= 0) maxOf(0, i - 1) else 0)
        }
        // a remote lands on Up next (else the first episode) once the list is there — the screen opened with nothing lit
        val land = tvFirstFocus(ready = eps.isNotEmpty())
        val landAt = eps.indexOfFirst { it.id == upNextId }.coerceAtLeast(0)
        LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 20.dp)) {
            if (episodes.isEmpty()) items(6) { SkeletonRow(112.dp, 63.dp, circle = false) }
            items(eps.size, key = { eps[it].id }) { i ->
                val ep = eps[i]
                EpisodeRow(
                    itemType = item.type, ep = ep, upNext = ep.id == upNextId, first = i == 0,
                    modifier = Modifier.returnTo("ep/" + ep.id).then(if (i == landAt) Modifier.focusRequester(land) else Modifier),
                    seriesPoster = item.poster,
                    onClick = { onPlayEpisode(ep, PlayIntent.TAP) },
                    onResume = { onPlayEpisode(ep, PlayIntent.RESUME) },
                    onStartOver = { onPlayEpisode(ep, PlayIntent.START_OVER) },
                    // a mark moves "Up next"; the season on screen deliberately stays put
                    onMarked = { upNextId = seriesUpNext(ctx, item.type, episodes)?.id },
                )
            }
        }
    }
}

// Stremio semantics: catalog add-ons and stream add-ons are separate. Ask the
// add-on the item came from PLUS every other installed add-on whose manifest
// serves streams for this type/id, and show the answers grouped per add-on.
@Composable
private fun StreamsScreen(addon: Addon, item: MetaItem, onBack: () -> Unit, fresh: Boolean = false, decided: Boolean = false, onPlay: (StreamItem, Addon, Boolean, Boolean) -> Unit) {
    var sections by remember { mutableStateOf<List<Pair<Addon, List<StreamItem>>>>(emptyList()) }
    var status by remember { mutableStateOf("Loading streams…") }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var usualUrl by remember { mutableStateOf<String?>(null) }   // the row that matches the last pick
    val ctx = LocalContext.current
    // Start over: a one-play choice for anything already begun (web parity). "When you come back"
    // (Settings › Playback) sets the starting point: Resume, Start over, or Ask — a two-option sheet
    // before the first play, unless the viewer already chose on a Continue Watching sheet.
    val savedAt = remember(item.id) { Progress.resumeAt(ctx, item.type, item.id) }
    val asking = Prefs.resume == "ask" && savedAt > 0 && !decided
    var startOver by remember(item.id) { mutableStateOf((fresh || (Prefs.resume == "startover" && !decided)) && savedAt > 0) }
    var chosen by remember(item.id) { mutableStateOf(!asking) }                   // Resume or Start over settled
    var pending by remember { mutableStateOf<Pair<StreamItem, Addon>?>(null) }    // a play waiting on the sheet
    var pendingByHand by remember { mutableStateOf(true) }
    fun play(s: StreamItem, from: Addon, byHand: Boolean) {
        // a row chosen by hand settles it for this title: auto-pick never overrides that choice on the way back
        if (byHand) autoPlayedFor = item.id
        if (!chosen) { pending = s to from; pendingByHand = byHand; return }
        onPlay(s, from, byHand, startOver)
    }
    pending?.let { (s, from) ->
        ResumeAskSheet(
            title = item.name.split(" · ").first(), poster = item.poster, shape = item.posterShape, savedAt = savedAt,
            onResume = { chosen = true; startOver = false; pending = null; onPlay(s, from, pendingByHand, false) },
            onStartOver = { chosen = true; startOver = true; pending = null; onPlay(s, from, pendingByHand, true) },
            onDismiss = { pending = null },
        )
    }
    val openedAt = remember(item.id, reload) { android.os.SystemClock.uptimeMillis() }   // "has the remote moved here yet?"
    // Back returned here (from the player, most often) — read at the first composition, before anything claims focus
    val streamsEntry = LocalScreenEntry.current
    val backHere = remember { ReturnFocus.backTo(streamsEntry) }
    LaunchedEffect(item, reload) {
        loading = true
        usualUrl = null
        // the origin add-on and every enabled add-on that serves streams for this id — a switched-off origin feeds nothing either;
        // one entry per address, because the rows below are keyed by it
        val order = (listOf(addon) + activeAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl })
            .filter { it.enabled }.distinctBy { it.manifestUrl }
        // one slot per add-on, in that order, filled as each answers — the list never reorders under the remote
        val slots = arrayOfNulls<List<StreamItem>>(order.size)
        // Play through your PC (a TV): find the sharing computer while the add-ons answer, so the play that follows
        // does not wait up to 2.6 s for it (Relay.resolve takes one lookup at a time; the player reads this one)
        if (Relay.wanted(ctx)) launch {
            try { Relay.resolve(ctx) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { }
        }
        var failures = 0
        var floored = false        // an add-on answered, and Minimum quality hid every row of it
        val pf = NextEp.picked(ctx)
        // Play the best stream by itself (Settings › Streams): decide once — when every add-on has
        // answered, or when the wait for slow ones runs out, whichever comes first. Guarded by id, not
        // screen state — this screen's state dies while the player is up, and re-firing on the way
        // back would trap the viewer in playback forever.
        var picked = false
        fun decide() {
            if (picked || Prefs.autoPick == "off" || autoPlayedFor == item.id) return
            // a viewer already walking the list — remote or finger — is choosing by hand (web: autoPickNow's lastKeyAt
            // and lastPointerAt); the list now arrives well inside the wait, so this matters more than it did
            if (KeyWatch.lastDownAt > openedAt || KeyWatch.lastTouchAt > openedAt) return
            val secs = sections
            if (secs.isEmpty()) return
            picked = true
            autoPlayedFor = item.id
            // Same as last time: the add-on picked last (if it answered) and the row closest to that pick;
            // First stream: the top row of the highest-ranked add-on that answered
            val (a, list) = if (Prefs.autoPick == "last") (secs.firstOrNull { it.first.manifestUrl == pf?.addonUrl } ?: secs.first()) else secs.first()
            // never a row this connection cannot carry while another is there (Settings › Streams)
            val pick = if (Prefs.autoPick == "last") (StreamTwin.match(list, pf, a) ?: list.first())
                else (list.firstOrNull { !StreamBadges.slow(it, item.runtime) } ?: list.first())
            play(pick, a, false)
        }
        // Back from the player shows the list it left (kept ten minutes per title, the window NextEp trusts a pick
        // for): it used to ask every add-on again and wait for all of them — issue #1's "slow" a second time, for a
        // viewer who only wanted the row below the one that failed. Only on Back: a fresh visit (a live event, whose
        // rows change by the minute) always asks, and ↻ always asks afresh. Never an auto-pick from it — the viewer
        // just came back from playing; throwing them straight into the top row would be a bounce.
        val memoKey = listOf(item.type, item.id, order.joinToString("|") { it.manifestUrl },
            Prefs.minRes, Prefs.streamSort, Prefs.slowMark, Prefs.p2p.toString()).joinToString("\n")
        val memo = if (reload == 0 && backHere) streamsMemo[memoKey]?.takeIf { System.currentTimeMillis() - it.at < 600_000 } else null
        if (memo != null) {
            sections = memo.sections; status = memo.status; usualUrl = memo.usual
            loading = false
            return@LaunchedEffect
        }
        val timer = if (Prefs.autoPick != "off" && autoPlayedFor != item.id) launch { delay(Prefs.pickWait * 1000L); decide() } else null
        // Every add-on at once (web parity). They were asked one after another, so the page waited for the SUM of
        // every add-on's answer time and one slow add-on held up all the ones after it — the "very slow" on issue #1.
        // Each answer is still handled here on the main thread, so the counters and slots need no locking.
        order.mapIndexed { i, a ->
            launch {
                try {
                    // origin is always asked; others only if their manifest matches
                    if (a.manifestUrl != addon.manifestUrl &&
                        !manifestFor(a.manifestUrl).canStream(item.type, item.id)) return@launch
                    val raw = Stremio.loadStreams(a.base, item.type, item.id)
                    val streams = arrangeStreams(raw, item.runtime)
                    if (raw.isNotEmpty() && streams.isEmpty()) floored = true
                    if (streams.isNotEmpty()) {
                        // the row that matches what was picked last time heads its
                        // section — buried at row 30 of 40 it would help nobody
                        var list = streams
                        if (pf != null && pf.addonUrl == a.manifestUrl) {
                            val twin = StreamTwin.match(streams, pf, a)?.takeIf { StreamTwin.isTwin(it, pf, a) }
                            if (twin != null) { usualUrl = twin.url; list = listOf(twin) + streams.filter { it !== twin } }
                        }
                        slots[i] = list
                        val now = order.indices.mapNotNull { k -> slots[k]?.let { order[k] to it } }
                        sections = now
                        val n = now.sumOf { it.second.size }
                        status = "$n stream${if (n > 1) "s" else ""}" + (if (now.size > 1) " from ${now.size} add-ons" else "")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures++
                }
            }
        }.forEach { it.join() }
        timer?.cancel()
        decide()
        if (sections.isEmpty()) {
            status = when {
                order.isEmpty() -> "Every add-on is switched off."
                failures == order.size -> "Failed to load streams."
                floored -> "Every stream is below your minimum quality."
                else -> "No playable streams right now."
            }
        }
        if (sections.isNotEmpty()) {
            streamsMemo[memoKey] = StreamsMemo(System.currentTimeMillis(), sections, status, usualUrl)
            while (streamsMemo.size > 24) streamsMemo.remove(streamsMemo.minByOrNull { it.value.at }!!.key)
        }
        loading = false
    }
    val shown = sections.filter { filter == null || it.first.name == filter }
    // A remote lands on the top row as soon as there is one (web parity) and follows it while a higher-ranked add-on
    // answers above — but only until the viewer presses something here; focus is never pulled out from under a hand.
    val remote = remember(ctx) { Account.isTv(ctx) } || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val firstRow = remember { FocusRequester() }
    val landOn = shown.firstOrNull()?.let { it.first.manifestUrl + "\n" + it.second.first().url }
    val screenEntry = LocalScreenEntry.current
    LaunchedEffect(landOn) {
        if (!remote || landOn == null || KeyWatch.lastDownAt > openedAt) return@LaunchedEffect
        // back from the player: the row that was played takes focus (Modifier.returnTo), not the top one
        if (yieldToReturn(screenEntry)) return@LaunchedEffect
        repeat(10) {
            withFrameNanos {}
            if (runCatching { firstRow.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Column(Modifier.fillMaxSize()) {
        // kept across the player, so the row played is still composed to take focus again — put back once the rows
        // exist (a list clamps a start index past its skeletons)
        LazyColumn(
            Modifier.fillMaxWidth(), state = rememberKeptList("streams", ready = sections.isNotEmpty()),
            verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item(key = "hero") {
                // episode context header — art, S/E kicker, episode title, series name
                val art = item.background ?: item.poster
                val kick = if (item.type == "series") episodeNumbersOf(item.id)?.let { (s, e) ->
                    if (s != null) "SEASON $s · EPISODE $e" else "EPISODE $e" } else null
                val parts = item.name.split(" · ")
                val heroTitle = if (parts.size >= 3) parts.drop(2).joinToString(" · ") else parts.lastOrNull() ?: item.name
                val heroSub = if (parts.size >= 2) parts[0] else null
                Box(Modifier.fillMaxWidth().height(260.dp).background(SurfaceC)) {
                    if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                        0f to Color(0x8A000000), 0.26f to Color(0x1F000000),
                        0.62f to Color(0x8A000000), 0.86f to Color(0xE6000000), 1f to Color(0xFF000000))))
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp).focusRing(CircleShape, landing = false).size(40.dp)
                            .background(Color(0x8A000000), CircleShape),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                        if (kick != null) Text(kick, color = Color(0xE6EBEBF5), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                        Text(heroTitle, color = TextC, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.8).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp))
                        if (heroSub != null) Text(heroSub, color = Color(0xB8EBEBF5), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        Text(status, color = FaintC, fontFamily = Mono, fontSize = 11.sp,
                            letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (savedAt > 0) item(key = "resume") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            !chosen -> "You will be asked where to start"
                            startOver -> "Starting from the beginning"
                            else -> "Resuming from ${fmtTime(savedAt)}"
                        },
                        color = MutedC, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    if (chosen) StreamFilterChip(if (startOver) "Resume from ${fmtTime(savedAt)}" else "Start over", startOver) { startOver = !startOver }
                    else {
                        // Ask: nothing preselected — either chip settles it for this page
                        StreamFilterChip("Resume", false) { chosen = true; startOver = false }
                        Spacer(Modifier.width(6.dp))
                        StreamFilterChip("Start over", false) { chosen = true; startOver = true }
                    }
                }
            }
            // ↻ whenever the page has answered, even with one add-on (a live event's rows change by the minute, and
            // that page had no way to ask again); the add-on filters only when there is more than one to choose
            if (!loading || sections.isNotEmpty()) item(key = "filters") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item { StreamFilterChip("↻", false) { filter = null; reload++ } }
                    if (sections.size > 1) {
                        item { StreamFilterChip("All", filter == null) { filter = null } }
                        items(sections.size) { i ->
                            val nm = sections[i].first.name
                            StreamFilterChip(nm, filter == nm) { filter = nm }
                        }
                    }
                }
            }
            if (loading && sections.isEmpty()) items(4) { Box(Modifier.padding(horizontal = 16.dp)) { SkeletonRow(42.dp, 42.dp, circle = true) } }
            // keyed by add-on and place, not by position: add-ons answer in any order and a slower one lands ABOVE a
            // faster one, so position keys would slide a different stream under the row the remote is on
            shown.forEachIndexed { sectionIndex, (from, streams) ->
                if (sections.size > 1) item(key = "head/${from.manifestUrl}") {
                    Text(
                        from.name, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp),
                    )
                }
            items(streams.size, key = { "row/${from.manifestUrl}/$it" }) { i ->
                val s = streams[i]
                Box(Modifier.padding(horizontal = 16.dp)) {
                    StreamRow(
                        s, from.name, item.name, usual = s.url == usualUrl, slow = StreamBadges.slow(s, item.runtime),
                        modifier = Modifier.returnTo("stream/" + from.manifestUrl + "/" + s.url)
                            .then(if (sectionIndex == 0 && i == 0) Modifier.focusRequester(firstRow) else Modifier),
                        onPlay = { play(it, from, true) },
                    )
                }
            }
            }
        }
    }
}

/** Settings › Streams applied to one add-on's answer: the quality floor first (rows with no
    resolution plate stay), then the order — as listed, best quality first, or smallest first. */
internal fun arrangeStreams(raw: List<StreamItem>, runtime: String? = null): List<StreamItem> {
    val floor = when (Prefs.minRes) { "720" -> 2; "1080" -> 3; "4k" -> 4; else -> 0 }
    val kept = if (floor == 0) raw else raw.filter { s ->
        val r = StreamBadges.resRank(s.name + "\n" + s.title)
        r == 0 || r >= floor
    }
    val ordered = when (Prefs.streamSort) {
        "quality" -> kept.sortedByDescending { StreamBadges.resRank(it.name + "\n" + it.title) }
        "size" -> kept.sortedBy { StreamBadges.sizeBytes(it.videoSize, it.title).let { b -> if (b <= 0L) Long.MAX_VALUE else b } }
        else -> kept
    }
    // Streams your connection cannot carry = Mark and move down: the rows this device cannot keep up with sink under
    // the ones that fit, whatever the order above chose (StreamBadges.slow; the row itself carries the mark)
    if (Prefs.slowMark != "move" || Prefs.bw <= 0) return ordered
    val (slow, fits) = ordered.partition { StreamBadges.slow(it, runtime) }
    return fits + slow
}

internal fun partyDisplayName(ctx: Context): String {
    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return p.getString("party_name", null)?.takeIf { it.isNotBlank() }
        ?: Cloud.profile?.name?.takeIf { it.isNotBlank() }?.take(40)
        ?: android.os.Build.MODEL.take(24).ifBlank { "Android" }
}

/** Quiet outlined filter — only the active one carries fill. */
@Composable
private fun StreamFilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        label,
        color = if (on) Color.Black else if (focused) TextC else MutedC,
        fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            // the active chip changed NOTHING when lit ("All", "Start over"): a ring outside it
            .outerRing(focused && on, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (on) Color.White else Color.Transparent)
            .border(1.dp, if (on) Color.Transparent else if (focused) Color.White else LineC, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

/** A stream row: resolution plate, release name, badges, and a right-hand
    spec column — the parts you actually choose by, nothing said twice. */
@Composable
private fun StreamRow(s: StreamItem, addonName: String, pageTitle: String, usual: Boolean = false, slow: Boolean = false, modifier: Modifier = Modifier, onPlay: (StreamItem) -> Unit) {
    val raw = remember(s.url) { s.name + "\n" + s.title }
    val plate = remember(s.url) { StreamBadges.plate(raw) }
    val m = remember(s.url) { StreamBadges.match(raw, if (plate != null) "resolution" else null) }
    val f = remember(s.url) { StreamBadges.facts(s.videoSize, s.title, m.fired) }
    val name = remember(s.url) {
        StreamBadges.cleanName(s.name, addonName).ifEmpty { f.provider ?: "Stream" }
    }
    val sub = remember(s.url) {
        listOfNotNull(
            f.provider?.takeIf { !name.contains(it, true) },
            f.langs.takeIf { it.isNotEmpty() },
            StreamBadges.cleanDesc(f.desc, addonName, pageTitle).takeIf { it.isNotEmpty() },
        ).joinToString("  ·  ")
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // the card's own source is this one, so the plate below does light on focus (the source was never passed, so it
    // never did); a full-width row wears the ring instead of growing past the screen's edges
    FocusCard(
        shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth(), onClick = { onPlay(s) },
        zoom = false, interactionSource = interaction, landing = false,
    ) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceC, RoundedCornerShape(14.dp))
                .border(1.dp, if (usual) Line2 else LineC, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (plate != null) {
                Column(
                    Modifier.width(62.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (focused) Red else Surface2)
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(plate.res, color = TextC, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (plate.tag.isNotEmpty()) Text(
                        plate.tag, color = MutedC, fontFamily = Mono, fontSize = 8.5.sp,
                        letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                Box(Modifier.size(42.dp).background(Red, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                // the row that matches what you picked last time — same source, same quality — and
                // whether this one comes from other viewers rather than a server
                val eyebrow = listOfNotNull(
                    "SAME AS LAST TIME".takeIf { usual },
                    "P2P".takeIf { s.isTorrent },
                ).joinToString(" · ")
                if (eyebrow.isNotEmpty()) Text(
                    eyebrow, color = if (usual) Red else MutedC, fontFamily = Mono, fontSize = 9.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp, modifier = Modifier.padding(bottom = 3.dp),
                )
                Text(name, color = TextC, fontSize = 15.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (Prefs.streamBadges && m.badges.isNotEmpty()) {
                    Row(
                        Modifier.padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        m.badges.take(5).forEach { b ->
                            AsyncImage(model = b, contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.height(14.dp).widthIn(max = 70.dp))
                        }
                    }
                }
                if (sub.isNotEmpty()) Text(sub, color = MutedC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
            // Stream details (Settings › Streams): size, bitrate, seeds as the right-hand column — and under them the word
            // that this row is faster than the connection this device has measured (StreamBadges.slow)
            val facts = Prefs.streamFacts && (f.size != null || f.bitrate != null || f.seeds != null)
            if (facts || slow) {
                Column(horizontalAlignment = Alignment.End) {
                    if (facts) f.size?.let { Text(it, color = TextC, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    val line2 = if (facts) listOfNotNull(f.bitrate, f.seeds?.let { "$it seeds" }).joinToString(" · ") else ""
                    if (line2.isNotEmpty()) Text(line2, color = MutedC, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    if (slow) Text("may stall here", color = FaintC, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            // Add-on on each row: its initial in an accent ring, its name in the mono register, or nothing
            when (Prefs.addonMark) {
                "initial" -> Box(Modifier.size(24.dp).border(1.5.dp, Red, CircleShape), contentAlignment = Alignment.Center) {
                    Text(addonName.trim().take(1).uppercase().ifEmpty { "•" }, color = TextC, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                "name" -> Text(
                    addonName.uppercase(), color = FaintC, fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.2.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 72.dp),
                )
            }
        }
    }
}

/** One party reaction, floating up and fading like the web player's. */
@Composable
private fun BoxScope.ReactionFloat(emoji: String, name: String) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(
            0f, 1f, animationSpec = androidx.compose.animation.core.tween(2600)
        ) { v, _ -> t = v }
    }
    val x = remember { (12 + (0..70).random()) / 100f }
    Column(
        Modifier.align(Alignment.BottomStart)
            .fillMaxWidth(x)
            .padding(bottom = 140.dp)
            .offset(y = (-(t * 280)).dp)
            .alpha(if (t < 0.12f) t / 0.12f else if (t > 0.78f) (1f - t) / 0.22f else 1f),
        horizontalAlignment = Alignment.End,
    ) {
        Text(emoji, fontSize = 40.sp)
        if (name.isNotEmpty()) Text(name, color = Color.White, fontSize = 11.sp)
    }
}

/** One row of the in-player subtitle picker. */
@Composable
private fun SubMenuRow(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier.fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .background(if (focused) Color(0x14FFFFFF) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextC, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (active) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = TextC, modifier = Modifier.size(16.dp))
    }
}

internal fun langLabel(code: String): String = runCatching {
    val c = code.trim().lowercase()
    if (c.isEmpty() || c == "und") "Unknown"
    else java.util.Locale(c.take(3)).getDisplayLanguage(java.util.Locale.ENGLISH)
        .ifEmpty { c.uppercase() }.replaceFirstChar { it.uppercase() }
}.getOrDefault(code.uppercase())

/** Add-on subtitle links carry no extension and are sometimes CP1252 — download one and hand back its text as
    UTF-8 (parseSubCues sniffs SRT or WebVTT from it). A cancelled download stays cancelled: a player left while a
    subtitle loaded used to say "Could not load that subtitle" over whatever came next. */
private suspend fun subText(st: SubTrack): String? {
    val raw = try {
        Stremio.httpGetBytes(st.url)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        return null                              // an address that fails, or serves something far too big to hold
    }
    var text = String(raw, Charsets.UTF_8)
    if (text.isBlank() || text.contains('\uFFFD')) text = String(raw, charset("windows-1252"))
    return text.removePrefix("\uFEFF").takeIf { it.isNotBlank() }
}

// ---------- player (unchanged behavior) ----------
@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    url: String,
    title: String = "Nebula",
    subs: List<SubTrack> = emptyList(),
    contentType: String? = null,
    contentId: String? = null,
    contentName: String? = null,
    poster: String? = null,
    addonUrl: String? = null,
    description: String? = null,
    startOver: Boolean = false,          // ignore the resume point this once
    sourceLine: String? = null,          // "1080p · Torrentio" under the title
    startAtMs: Long = -1L,               // a source swap: start from this second, whatever the resume point says
    currentEpisode: Episode? = null,
    nextEpisode: Episode? = null,
    onPlayNext: (Episode) -> Unit = {},
    onCancelNext: () -> Unit = {},       // the Up next card was dismissed: a hop already asked for is called off
    onSwapSource: (StreamItem, Addon, Long) -> Unit = { _, _, _ -> },
    onPrefetchNext: () -> Unit = {},
    onProgressSaved: () -> Unit = {},
    onPartyStart: (PartyStreamDesc) -> Unit = {},
    onPartyLeave: () -> Unit = {},
    onExit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hostDirty by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var videoQualityCount by remember { mutableStateOf(0) }
    var audioTrackCount by remember { mutableStateOf(0) }
    var textTrackCount by remember { mutableStateOf(0) }
    var embeddedSubs by remember { mutableStateOf<List<EmbeddedSub>>(emptyList()) }   // the stream's own text tracks
    val scope = rememberCoroutineScope()
    var addonSubs by remember { mutableStateOf<List<AddonSub>>(emptyList()) }
    var subSearching by remember { mutableStateOf(false) }
    var subPanelOpen by remember { mutableStateOf(false) }       // the one Subtitles panel: languages · tracks · style
    val subsFocus = remember { FocusRequester() }                 // the toolbar's Subtitles item, to hand focus back to
    var subBusy by remember { mutableStateOf(false) }
    var activeAddonSub by remember { mutableStateOf<String?>(null) }
    // the Title Card chrome replaces Media3's controller entirely
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeTouchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var trackListOpen by remember { mutableStateOf(false) }   // the Audio or Quality list is up over the video
    var isPlayingState by remember { mutableStateOf(false) }
    var isLiveState by remember { mutableStateOf(false) }
    var posMs by remember { mutableStateOf(0L) }
    var durMs by remember { mutableStateOf(0L) }
    var bufMs by remember { mutableStateOf(0L) }
    var qualityLabel by remember { mutableStateOf<String?>(null) }
    // Speed when a video starts (Settings › Playback): a fixed rate, or the one chosen last time
    val startSpeed = remember { if (Prefs.speedDefault == "last") Prefs.lastSpeed else (Prefs.speedDefault.toFloatOrNull() ?: 1f) }
    var speedLabel by remember { mutableStateOf(speedText(startSpeed)) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var skipFlash by remember { mutableStateOf<Triple<Int, Int, Long>?>(null) } // zone (-1/+1), total secs, stamp
    var heldSpeed by remember { mutableStateOf<Float?>(null) }                  // speed to restore after hold-to-speed
    var dragSeek by remember { mutableStateOf<Pair<Long, Long>?>(null) }        // target ms, delta ms
    // the stall watchdog (StallWatch.kt): its memory, the sibling on offer and when the offer went up
    val stallWatch = remember { StallWatch() }
    var swapOffer by remember { mutableStateOf<Pair<StreamItem, Addon>?>(null) }
    var swapWhy by remember { mutableStateOf("Keeps stalling") }         // what the pill says the trouble is
    var swapShownAt by remember { mutableStateOf(0L) }
    val bwTick = remember { IntArray(1) }
    var pauseBoardOn by remember { mutableStateOf(false) }
    var pausedSince by remember { mutableStateOf(0L) }
    var pinfoOn by remember { mutableStateOf(false) }
    var infoRows by remember { mutableStateOf<List<InfoRow>>(emptyList()) }
    var viaRelay by remember { mutableStateOf<Relay.Live?>(null) }             // Play through your PC: the sharing computer this play goes through
    var subOffsetMs by remember { mutableStateOf(0L) }
    var liveOffMs by remember { mutableStateOf(0L) }                            // behind the live edge, for the left pill
    // the add-on subtitle showing: its cues, drawn by our own overlay over the video (null = the stream's tracks, or off)
    var overlayCues by remember { mutableStateOf<List<SubCue>?>(null) }
    var overlaySubView by remember { mutableStateOf<SubtitleView?>(null) }
    val pickSerial = remember { IntArray(1) }                                    // the latest choice wins over a slow download
    var pickLang by remember { mutableStateOf<String?>(null) }                  // a hand-picked add-on language, carried to the next title
    var tracksFor by remember { mutableStateOf<String?>(null) }                 // the address whose tracks are known
    var autoSubDone by remember { mutableStateOf<String?>(null) }               // the address the autoload has settled
    var subChosenFor by remember { mutableStateOf<String?>(null) }              // the title whose subtitles were chosen by hand
    var subsTitle by remember { mutableStateOf<String?>(null) }                 // the title the subtitle state belongs to
    var autoSubTry by remember { mutableStateOf(0) }                            // bumped when an autoload file would not load
    val autoSubFailed = remember { mutableSetOf<String>() }                     // files that would not load, not tried again
    val autoSubBusy = remember { arrayOfNulls<String>(1) }                      // the address an autoload download is for
    val textOwed = remember { BooleanArray(1) }                                 // the stream's tracks are to come back on
    // Scrub preview (ScrubPreview.kt): a second, silent reader of a progressive file hands the tip its frames;
    // rebuilt per URL, released with the player. While a preview is up the chrome stays awake.
    val scrubPreview = remember(url) { if (ScrubPreview.eligible(url)) ScrubPreview(url, context) else null }
    DisposableEffect(scrubPreview) { onDispose { scrubPreview?.release() } }
    // a few seconds into playback, once the length is known, the reader opens and sweeps frames across
    // the film in the background — so a phone's one-second drag has a picture at once (ScrubPreview.warm)
    LaunchedEffect(scrubPreview, durMs > 0, isPlayingState) {
        if (scrubPreview == null || durMs <= 0 || !isPlayingState || !Prefs.scrubFrames) return@LaunchedEffect
        // not on a TV: up to 120 frames through a second reader competes with the film for a weak box's one decoder
        // and its memory, and a one-connection host may refuse either reader (the web gave frames up on webOS for the
        // same reason). A TV still gets a frame for the position it asks for.
        if (Account.isTv(context)) return@LaunchedEffect
        delay(4000)
        scrubPreview.warm(context, durMs)      // eligible() already rules out manifests, and keys ride on .mpd here
    }
    var scrubbing by remember { mutableStateOf(false) }
    // Our own meter so the HUD can read the estimate; Start high seeds it so the
    // first segments are fetched at the best rendition, Data saver seeds it low.
    val bandwidth = remember {
        DefaultBandwidthMeter.Builder(context).apply {
            when (Prefs.quality) {
                "high" -> setInitialBitrateEstimate(30_000_000L)
                "saver" -> setInitialBitrateEstimate(1_500_000L)
            }
        }.build()
    }
    // Which decoder draws the picture (1.73.0): the chip first; a stream it refuses is retried on the processor
    // (nextlib's FFmpeg renderer) once, per stream — softDecode remembers the retry so a second failure is final.
    val decoders = remember { DecoderManager() }
    var softDecode by remember { mutableStateOf(false) }
    val exo = remember {
        ExoPlayer.Builder(context)
            .setBandwidthMeter(bandwidth)
            .setRenderersFactory(NextRenderersFactory(context).setDecoderManager(decoders))
            // Settings › Buffer ahead, and a memory ceiling for every setting including Auto (PlayerExtras.kt)
            .setLoadControl(bufferLoadControl(Prefs.buffer))
            // one HTTP identity (UA, X-Nebula-Client, cookies) shared with the scrub-frame reader — MediaHttp.kt
            .setMediaSourceFactory(MediaHttp.mediaSourceFactory(context))
            // Without these the app behaves as if it were the only thing on the
            // phone: a call or another app's audio would play *over* the film,
            // and pulling the headphones out would blast it from the speaker.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Skip by (Settings › Playback) for media keys and the session too, not only the on-screen transport
            .setSeekBackIncrementMs(Prefs.seekStep * 1000L)
            .setSeekForwardIncrementMs(Prefs.seekStep * 1000L)
            .build()
            .apply {
                decoders.attach(this)                       // before any prepare; needs the default track selector
                playWhenReady = true
                // languages, the resolution cap and subtitles-at-start, from Settings › Playback
                trackSelectionParameters = trackParams(trackSelectionParameters)
                if (startSpeed != 1f) setPlaybackSpeed(startSpeed)
            }
    }
    var subForced by remember { mutableStateOf(false) }      // "Always" already switched a text track on for this item
    // "Show · S1E2 · Episode name" is how the chain labels an episode; take it apart again
    val nameParts = remember(contentName) { (contentName ?: "").split(" · ") }
    val showName = nameParts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: title
    val episodeName = if (contentType == "series" && nameParts.size >= 3) nameParts.drop(2).joinToString(" · ") else null
    // "S1 E1 · Pilot" when the name is known, else "Season 1 · Episode 1" — the web player's wording
    val episodeTag = contentId?.takeIf { contentType == "series" }?.let { episodeNumbersOf(it) }?.let { (s, e) ->
        val short = if (s != null) "S$s E$e" else "E$e"
        val long = if (s != null) "Season $s · Episode $e" else "Episode $e"
        if (episodeName != null) "$short · $episodeName" else long
    }
    // the stream's add-on, named on the subtitle cards it side-loaded
    val streamSource = remember(addonUrl, subs) {
        if (subs.isEmpty()) null else addonUrl?.let { u -> loadAddons(context).firstOrNull { it.manifestUrl == u }?.name }
    }
    // Publishes to the platform so the lock screen, the output switcher and the
    // play/pause button on a headset all reach this player. Released alongside
    // the player below, and deliberately before it — a session outliving its
    // player is a crash. The id is stamped because a screen replacement can
    // briefly overlap two players, and duplicate session ids throw.
    val session = remember(exo) {
        runCatching {
            MediaSession.Builder(context, exo)
                .setId("nebula-" + System.currentTimeMillis())
                .apply {
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launch != null) setSessionActivity(
                        PendingIntent.getActivity(
                            context, 0, launch,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    )
                }
                .build()
        }.getOrNull()
    }

    fun seekBy(deltaMs: Long) {
        var t = exo.currentPosition + deltaMs
        if (t < 0) t = 0
        val dur = exo.duration
        if (dur != C.TIME_UNSET && t > dur) t = dur
        exo.seekTo(t)
    }
    fun doSkip(zone: Int) {
        // Skip by (Settings › Playback): the tap zones, the transport and the remote share one step
        val step = Prefs.seekStep
        seekBy(zone * step * 1000L)
        val now = System.currentTimeMillis()
        val prev = skipFlash
        // Rapid re-taps on the same side accumulate (10s, 20s, 30s…) like YouTube.
        val total = if (prev != null && prev.first == zone && now - prev.third < 1200) prev.second + step else step
        skipFlash = Triple(zone, total, now)
    }
    LaunchedEffect(skipFlash) { if (skipFlash != null) { delay(800); skipFlash = null } }

    // ---- resume point ----
    var upnextOpen by remember { mutableStateOf(false) }
    var upnextCounting by remember { mutableStateOf(false) }
    var upnextLeft by remember { mutableStateOf(Prefs.countdown) }
    var upnextDismissed by remember { mutableStateOf(false) }
    // Still watching? (Settings › Playback): episodes autoplayed in a row this sitting (the player is not
    // re-keyed per episode, so this survives the in-place hop), and whether the card is asking
    var autoRun by remember { mutableStateOf(0) }
    var stillAsk by remember { mutableStateOf(false) }
    // Skip intro / recap: where this episode's segments fall, the pill on screen, what Auto already
    // took. Keyed on the episode so the hop into the next one starts clean. Off never asks.
    var skipSegs by remember(contentId) { mutableStateOf<SkipSegments.Segs?>(null) }
    var skipKind by remember(contentId) { mutableStateOf<String?>(null) }
    val skipAuto = remember(contentId) { HashSet<String>() }
    var skipNote by remember { mutableStateOf<Pair<String, Long>?>(null) }
    if (contentId != null) LaunchedEffect(contentId, Prefs.skipIntro) {
        skipSegs = null; skipKind = null
        if (Prefs.skipIntro != "off" && SkipSegments.eligible(contentType, contentId)) skipSegs = SkipSegments.load(contentId)
    }
    LaunchedEffect(skipNote) { if (skipNote != null) { delay(1600); skipNote = null } }
    fun skipNow() {
        val hit = SkipSegments.at(skipSegs, exo.currentPosition)
        skipKind = null
        if (hit == null) return
        exo.seekTo(hit.second.endMs)
        skipNote = SkipSegments.note(hit.first) to System.currentTimeMillis()
    }

    // ---- sleep timer (web parity) ----
    // For the last episode of the night: playback pauses when the minutes run
    // out, or stops at the end of this episode instead of rolling into the next.
    // Kept for the sitting only — it rides along into the next episode, and goes
    // with the player when you leave.
    var sleepMode by remember { mutableStateOf("") }         // "" | "min" | "ep"
    var sleepMins by remember { mutableStateOf(0) }           // the minutes chosen, so the menu can mark that row
    var sleepAt by remember { mutableStateOf(0L) }            // epoch ms the minutes run out
    var sleepFired by remember { mutableStateOf(false) }      // the pause board says why it stopped
    var sleepMenuOpen by remember { mutableStateOf(false) }
    var sleepLabel by remember { mutableStateOf<String?>(null) }
    fun sleepText(ms: Long): String {
        val m = ((ms + 59_999) / 60_000).coerceAtLeast(1L)   // the last seconds still read "1 min"
        return if (m >= 60) "${m / 60} h" + (if (m % 60 > 0L) " ${m % 60} min" else "") else "$m min"
    }
    fun sleepRender() {
        sleepLabel = when (sleepMode) {
            "ep" -> "End of episode"
            "min" -> sleepText(sleepAt - System.currentTimeMillis())
            else -> null
        }
    }
    fun sleepSet(mode: String, minutes: Int = 0) {
        sleepMode = mode
        sleepMins = if (mode == "min") minutes else 0
        sleepAt = if (mode == "min") System.currentTimeMillis() + minutes * 60_000L else 0L
        sleepFired = false
        if (mode == "ep") { upnextOpen = false; upnextCounting = false }
        sleepRender()
        sleepMenuOpen = false
        chromeTouchedAt = System.currentTimeMillis()
        Toasts.show(when (mode) { "" -> "Sleep timer off"; "ep" -> "Stopping when this episode ends"; else -> "Pausing in ${sleepText(minutes * 60_000L)}" })
    }

    /** Write the current position into the store. Live streams have nothing to resume. */
    fun snapshotProgress(done: Boolean = false) {
        val id = contentId ?: return
        val type = contentType ?: return
        if (exo.isCurrentMediaItemLive) return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        Progress.note(
            context,
            ProgressRec(
                type = type, id = id, name = contentName ?: title, poster = poster,
                addonUrl = addonUrl.orEmpty(),
                pos = exo.currentPosition.coerceAtLeast(0L), dur = dur, done = done,
            ),
        )
        onProgressSaved()
    }
    /** Three stalls in ninety seconds: find the row after this one and put it on the pill (a party viewer follows the host instead). */
    fun offerSwap(why: String = "Keeps stalling") {
        swapWhy = why
        if (partyUi.active() && !partyUi.isHost) return
        if (P2p.isLocal(url)) return                     // a P2P play: the row behind it is not this address
        val type = contentType ?: return
        val id = contentId ?: return
        stallWatch.busy = true
        scope.launch {
            val c = runCatching { StallWatch.pick(StallWatch.siblings(context, type, id, addonUrl, null), url, null) }.getOrNull()
            stallWatch.busy = false; stallWatch.offered = true
            if (c != null) { swapOffer = c; swapShownAt = System.currentTimeMillis() }
            else Toasts.show(when (why) {
                "Keeps stalling" -> "This source keeps stalling — another stream from the list may do better."
                "Decoding too slowly" -> "This device cannot decode this source smoothly — another stream from the list may."
                else -> "This device cannot decode this source — another stream from the list may."
            })
        }
    }

    LaunchedEffect(url) {
        // a fresh episode starts with the up-next card closed and undismissed, the stall watchdog at zero, and no red line
        // left over from the address before it (a swap after "Cannot decode" played under the old error for good)
        stallWatch.reset(); swapOffer = null; error = null
        if (softDecode) { softDecode = false; runCatching { decoders.selectVideoDecoder(DecoderMode.AUTO) } }
        upnextOpen = false; upnextCounting = false; upnextDismissed = false; stillAsk = false; subForced = false
        // and without the last title's add-on subtitle — a swap to another source of the SAME title keeps it, its timing
        // and a pick still downloading: the add-on subtitle is drawn over whichever source plays, so nothing is fed
        // into the new item
        val sameTitle = contentId != null && contentId == subsTitle
        subsTitle = contentId
        tracksFor = null
        // owed until the new item is set — a hop cancelled during the relay lookup must not lose it
        if (!sameTitle && overlayCues != null) textOwed[0] = true
        if (!sameTitle) {
            pickSerial[0]++; subBusy = false
            activeAddonSub = null; overlayCues = null; subOffsetMs = 0L
        }
        val isMpd = Regex("\\.mpd(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)
        // A protected stream's licence address used to be read from its manifest HERE, before the item was set — a
        // whole extra read of a manifest the player then fetched again (the sports backend answers in 1.3–2.6 s), on
        // every start: issue #1's "slow" for every sports channel. The player reads the address from the manifest
        // itself (Media3's DASH parser takes clearkey:Laurl / dashif:Laurl into the key request), so the item carries
        // the ClearKey configuration with no address and nothing is read first. A manifest that names it some other
        // way fails the key request instead; onPlayerError then reads it the old way, once, and prepares again — and
        // the answer is kept, so that address starts with it from then on.
        val laurl = if (isMpd) licenceCache[url]?.takeIf { System.currentTimeMillis() - it.second < 1_800_000 }?.first else null
        // Play through your PC (Relay.kt, the TV only): the sharing computer, when one answers — settled before the item is set
        Relay.via = null; viaRelay = null
        if (url.startsWith("http://") || url.startsWith("https://")) {
            val l = Relay.resolve(context)
            if (l != null) { Relay.via = l; viaRelay = l }
        }
        runCatching {
            val b = MediaItem.Builder().setUri(url)
                // what the system's now-playing card, the TV's assistant and a headset announce: the title and the
                // episode (it was nameless, then briefly the stream row's own label)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(showName)
                        .apply { episodeTag?.let { setSubtitle(it) } }
                        .apply { poster?.let { runCatching { setArtworkUri(Uri.parse(it)) } } }
                        .build()
                )
            when {
                isMpd -> {
                    b.setMimeType(MimeTypes.APPLICATION_MPD)
                    // an unencrypted manifest carries no protection, so no licence session is ever opened for it
                    b.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).apply { if (laurl != null) setLicenseUri(laurl) }.build()
                    )
                }
                Regex("\\.m3u8", RegexOption.IGNORE_CASE).containsMatchIn(url) -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            val streamSubs = subs.mapIndexed { i, st ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(st.url))
                    .setId("sub:$i")                         // the panel badges these with the stream's add-on
                    .setLanguage(st.lang)
                    .setMimeType(
                        if (Regex("\\.srt(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(st.url))
                            MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT
                    )
                    .build()
            }
            if (streamSubs.isNotEmpty()) b.setSubtitleConfigurations(streamSubs)
            // Resume where this exact item was left off; handing the offset to
            // ExoPlayer starts buffering there rather than loading at 0 and seeking.
            val saved = if (contentType != null && contentId != null) {
                Progress.resumeAt(context, contentType, contentId)
            } else 0L
            // Start over is a one-play choice: the saved point is skipped, not erased
            val resume = if (startAtMs >= 0) startAtMs else if (startOver) 0L else saved   // a source swap lands on its own second
            // the add-on subtitle had the stream's own tracks off; the new title has them back as the settings say (on, in
            // the hand-picked language first, after a pick) — now, as the item changes, not while the last one still
            // plays through a slow relay lookup
            if (textOwed[0]) {
                textOwed[0] = false
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, Prefs.subStart == "off" && pickLang == null).build()
            }
            if (resume > 0) exo.setMediaItem(b.build(), resume) else exo.setMediaItem(b.build())
            exo.prepare()
            // the pill is drawn inside the app now, so it stays quiet inside a picture-in-picture window
            if (startAtMs < 0 && !inPipMode.value && (resume > 0 || (startOver && saved > 0))) {
                Toasts.show(if (resume > 0) "Resumed from ${fmtTime(resume)}" else "Starting from the beginning")
            }
        }.onFailure { error = it.message }
    }

    // Ask every subtitle-capable add-on what it has for this title (web parity).
    LaunchedEffect(contentId) {
        addonSubs = emptyList()                      // the last title's offers are not this one's
        if (contentType == null || contentId == null) return@LaunchedEffect
        subSearching = true
        // every add-on at once, each answer ADDED in the order it arrives: a card once shown is never pushed out or
        // moved by a later one (under the remote the panel's lit card would change or vanish). A handful per language
        // is plenty; the language list itself is not capped, so no later language can crowd one out either.
        val addons = activeAddons(context)
        val found = mutableListOf<AddonSub>()
        try {
            addons.map { a ->
                launch {
                    val got = try {
                        if (!manifestFor(a.manifestUrl).canSubs(contentType, contentId)) return@launch
                        Stremio.loadSubtitles(a.base, contentType, contentId).map { AddonSub(it, a.name) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        return@launch
                    }
                    val perLang = found.groupingBy { it.track.lang.lowercase() }.eachCount().toMutableMap()
                    for (sub in got) {
                        val k = sub.track.lang.lowercase()
                        val n = perLang[k] ?: 0
                        if (n < 6) { found += sub; perLang[k] = n + 1 }
                    }
                    addonSubs = found.toList()
                }
            }.forEach { it.join() }
        } finally { subSearching = false }
    }

    // Keep the resume point roughly current while playing.
    if (contentId != null) LaunchedEffect(contentId) {
        while (true) {
            delay(5000)
            if (exo.isPlaying) snapshotProgress()
        }
    }

    // Offer the next episode in the last 25s; the countdown itself starts on ENDED.
    // Its stream is chosen from 90s out, so Play now has nothing left to fetch.
    if (nextEpisode != null) LaunchedEffect(nextEpisode.id) {
        while (true) {
            delay(500)
            if (upnextDismissed || upnextOpen) continue
            if (sleepMode == "ep") continue                   // stopping here: nothing to offer or prefetch
            if (exo.isCurrentMediaItemLive || !exo.isPlaying) continue
            val dur = exo.duration
            if (dur == C.TIME_UNSET || dur <= 0) continue
            val remain = dur - exo.currentPosition
            // the closing credits count as the end when the database knows where they start
            val credits = SkipSegments.inOutro(skipSegs, exo.currentPosition)
            if (remain <= 90_000 || credits) onPrefetchNext()
            // Offer the next episode (Settings › Playback): N s before the end, or only once the credits
            // roll — 25 s before the end when the database has no credits mark for this episode
            val at = Prefs.upnextAt
            val soon = if (at == "credits") (if (skipSegs?.outro != null) credits else remain <= 25_000)
                else remain <= (at.toLongOrNull() ?: 25L) * 1000L || credits
            if (remain > 500 && soon) upnextOpen = true
        }
    }
    // the card opening (near the end, on ENDED, or after a seek) is the other cue
    LaunchedEffect(upnextOpen) { if (upnextOpen) onPrefetchNext() }
    LaunchedEffect(upnextCounting) {
        if (!upnextCounting) return@LaunchedEffect
        upnextLeft = Prefs.countdown                       // Autoplay countdown (Settings › Playback)
        while (upnextLeft > 0) { delay(1000); upnextLeft-- }
        nextEpisode?.let { autoRun++; onPlayNext(it) }
    }

    val behindLiveAt = remember { LongArray(1) }
    val drmAsked = remember { HashSet<String>() }           // addresses whose licence was read after a key request failed
    // The app leaving the screen pauses the film: Home on a TV without picture-in-picture, the box going to standby,
    // a phone's power button. Nothing did — the audio played on under the launcher, and a box left running autoplayed
    // episode after episode all night, ticking each one watched. Picture-in-picture pauses the Activity without
    // stopping it, so it plays on there; dismissing its window stops it, which pauses here as it already did.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) {
                runCatching { snapshotProgress() }
                upnextCounting = false
                exo.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                // A live stream that fell behind its window (a long pause, a long stall): rejoin at the edge, as every
                // player does — it was a final red line. Once per 10 s, so a feed that keeps failing still says so.
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && exo.currentMediaItem != null &&
                    System.currentTimeMillis() - behindLiveAt[0] > 10_000) {
                    behindLiveAt[0] = System.currentTimeMillis()
                    exo.seekToDefaultPosition(); exo.prepare()
                    return
                }
                // A protected stream whose licence address the player did not find in the manifest (named some other way):
                // read it ourselves — the old way — once per address, and prepare again with it (see LaunchedEffect(url))
                val drmItem = exo.currentMediaItem
                val drmUri = drmItem?.localConfiguration?.uri?.toString()
                val drmConf = drmItem?.localConfiguration?.drmConfiguration
                if (e.errorCode in 6000..6999 && drmItem != null && drmUri != null && drmConf != null &&
                    drmConf.licenseUri == null && drmAsked.add(drmUri)) {
                    val pos = exo.currentPosition
                    val live = exo.isCurrentMediaItemLive
                    val wanted = exo.playWhenReady          // a viewer who paused while the key failed stays paused
                    scope.launch {
                        val la = licenceUrlFor(drmUri)
                        if (la == null || exo.currentMediaItem?.localConfiguration?.uri?.toString() != drmUri) {
                            if (la == null) error = "Playback error ${e.errorCodeName} (${e.errorCode})"
                            return@launch
                        }
                        val next = drmItem.buildUpon()
                            .setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).setLicenseUri(la).build())
                            .build()
                        if (!live && pos > 0) exo.setMediaItem(next, pos) else exo.setMediaItem(next)
                        exo.prepare(); exo.playWhenReady = wanted
                    }
                    return
                }
                // Play through your PC: the sharing computer stopped answering — the same item again, direct, from where it
                // was. Only a reading failure counts (Media3's 2xxx codes): a decoder that cannot play this file fails
                // the same way direct, and dropping the relay for it would hide the real error behind a silent restart.
                val item = exo.currentMediaItem
                if (Relay.via != null && item != null && e.errorCode in 2000..2999) {
                    Relay.drop(context); viaRelay = null
                    val pos = exo.currentPosition
                    if (!exo.isCurrentMediaItemLive && pos > 0) exo.setMediaItem(item, pos) else exo.setMediaItem(item)
                    exo.prepare()
                    Toasts.show("Your PC stopped answering — playing direct")
                    return
                }
                // The chip could not decode this picture (a 10-bit H.264 feed, an odd profile): the same item again on the
                // processor, once. A live stream rejoins at the edge; a film keeps its place. Media3 does not do this by
                // itself — a decoder that dies mid-stream is a final error to it (nextlib's notes say the same).
                val decodeFail = e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                    || e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                    || e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                    || e.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
                // Not where the processor cannot help: Dolby Vision decoded as plain HEVC plays in purple and green, and
                // a TV box's processor turns a picture above 1080p into a slideshow that never errors — so there the
                // pill offers the next source at once.
                val fmt = (e as? androidx.media3.exoplayer.ExoPlaybackException)?.rendererFormat ?: exo.videoFormat
                val hopeless = fmt != null && (fmt.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION ||
                    (Account.isTv(context) && (fmt.height > 1088 || fmt.width > 1920)))
                if (decodeFail && !softDecode && item != null && !hopeless) {
                    softDecode = true
                    val pos = exo.currentPosition
                    runCatching { decoders.selectVideoDecoder(DecoderMode.FFMPEG) }
                    if (!exo.isCurrentMediaItemLive && pos > 0) exo.setMediaItem(item, pos) else exo.setMediaItem(item)
                    exo.prepare(); exo.play()
                    Toasts.show("This device's video chip could not decode this picture — decoding on the processor instead")
                    // The processor may not keep up (a TV box with 1080p60 in software) and dropped frames are no error,
                    // so nothing would ever say so: look after a few seconds of play, and offer the next source when a
                    // fifth or more of the frames are being dropped.
                    // Every 8 s while it lasts, on the frames of that stretch alone: one look at 8 s missed a stream that
                    // spent it buffering or paused, and the slideshow after never said so.
                    scope.launch {
                        var seenDropped = 0
                        var seenAll = 0
                        while (true) {
                            delay(8_000)
                            if (!softDecode || swapOffer != null) return@launch
                            val c = exo.videoDecoderCounters ?: continue
                            c.ensureUpdated()
                            val dropped = c.droppedBufferCount
                            val all = dropped + c.renderedOutputBufferCount
                            val d = dropped - seenDropped
                            val n = all - seenAll
                            seenDropped = dropped; seenAll = all
                            if (n >= 60 && d * 5 >= n) { offerSwap("Decoding too slowly"); return@launch }
                        }
                    }
                    return
                }
                error = "Playback error ${e.errorCodeName} (${e.errorCode})"
                // the processor could not either (or it is not a decoder error): the row after this one, on the pill
                if (decodeFail) offerSwap("Cannot decode")
            }
            override fun onTracksChanged(tracks: Tracks) {
                var v = 0
                var au = 0
                var tx = 0
                val tt = mutableListOf<EmbeddedSub>()
                for (g in tracks.groups) {
                    when (g.type) {
                        C.TRACK_TYPE_VIDEO -> for (i in 0 until g.length) {
                            if (g.isTrackSupported(i) && g.getTrackFormat(i).height > 0) v++
                        }
                        C.TRACK_TYPE_AUDIO -> if (g.length > 0) au++
                        C.TRACK_TYPE_TEXT -> if (g.length > 0) {
                            tx++
                            for (i in 0 until g.length) if (g.isTrackSupported(i)) tt += EmbeddedSub(g, i, g.getTrackFormat(i), g.isTrackSelected(i))
                        }
                    }
                }
                videoQualityCount = v
                audioTrackCount = au
                textTrackCount = tx
                embeddedSubs = tt
                if (!tracks.isEmpty) tracksFor = exo.currentMediaItem?.localConfiguration?.uri?.toString()
                // Subtitles when a video starts · Always: no language matched, so the first text track goes on
                // (not under an add-on subtitle: the overlay has the stream's tracks off on purpose)
                if (Prefs.subStart == "always" && !subForced && overlayCues == null && tt.isNotEmpty() && tt.none { it.selected }) {
                    subForced = true
                    val first = tt.first()
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(first.group.mediaTrackGroup, first.index)).build()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // The screen stays on while something plays and only then (nothing asked before: a TV's screensaver
                // came down over a long film and a phone went dark mid-scene); a paused film lets it rest.
                playerViewRef?.keepScreenOn = isPlaying
                if (isPlaying) error = null        // whatever went wrong before, this plays now
                (activity as? MainActivity)?.refreshPipParams()
                if (partyUi.active() && partyUi.isHost) hostDirty = true
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                (activity as? MainActivity)?.refreshPipParams()
                if (videoSize.height > 0) qualityLabel = "${videoSize.height}p"
            }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) stallWatch.seekAt = System.currentTimeMillis()
                if (reason == Player.DISCONTINUITY_REASON_SEEK && partyUi.active() && partyUi.isHost) hostDirty = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                scrubPreview?.busy(state == Player.STATE_BUFFERING)   // the frame sweep stands aside while the film buffers
                // the stall watchdog: a buffering state after playback began counts; three in ninety seconds offer a sibling
                if (state == Player.STATE_READY && stallWatch.playedAt == 0L) stallWatch.playedAt = System.currentTimeMillis()
                if (state == Player.STATE_BUFFERING && exo.playWhenReady && stallWatch.note()) offerSwap()
                if (state != Player.STATE_ENDED) return
                snapshotProgress(done = true)          // ticks it off the episode list
                if (sleepMode == "ep") {
                    // the night ends here: no up-next, the board says why
                    sleepMode = ""; sleepAt = 0L; sleepFired = true; sleepRender()
                    return
                }
                if (nextEpisode != null && !upnextDismissed) {
                    // Still watching?: after N autoplayed episodes the card waits for a hand instead of counting
                    val ask = Prefs.stillWatching > 0 && autoRun >= Prefs.stillWatching
                    stillAsk = ask
                    upnextOpen = true; upnextCounting = Prefs.autoPlayNext && !ask
                }
            }
        }
        exo.addListener(l)
        activePipPlayer.value = exo
        (activity as? MainActivity)?.refreshPipParams()
        onDispose {
            // last word on the resume point before the player goes away
            runCatching { snapshotProgress() }
            runCatching { Social.publishSoon(context) }   // friends see the freshly watched title
            exo.removeListener(l); runCatching { session?.release() }; runCatching { decoders.detach() }; exo.release()
            Relay.via = null            // the next play asks again
            P2p.leave(context)          // engine off, download cleared — on its own thread, stopping it blocks
            if (activePipPlayer.value === exo) activePipPlayer.value = null
            // Clears (API 31+) auto-enter so backing out of the player can't PiP the browse UI.
            (activity as? MainActivity)?.refreshPipParams()
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                setImmersive(it, false)
            }
        }
    }

    // Restyle live whenever the subtitle appearance changes — a tap in the
    // panel here, or a synced-in choice made on another device. While the
    // chrome is up, cues lift clear of the lower third.
    LaunchedEffect(SubStyle.version.value, chromeVisible, overlaySubView) {
        listOfNotNull(playerViewRef?.subtitleView, overlaySubView).forEach {
            SubStyle.apply(context, it)
            if (chromeVisible) it.setBottomPaddingFraction(0.32f)
        }
    }

    // one clock drives the chrome: position, buffered, playing, live
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            isPlayingState = exo.isPlaying
            isLiveState = exo.isCurrentMediaItemLive
            posMs = exo.currentPosition.coerceAtLeast(0L)
            durMs = if (exo.duration == C.TIME_UNSET) 0L else exo.duration
            bufMs = exo.bufferedPosition.coerceAtLeast(0L)
            liveOffMs = if (exo.isCurrentMediaItemLive && exo.currentLiveOffset != C.TIME_UNSET) exo.currentLiveOffset.coerceAtLeast(0L) else 0L
            val now = System.currentTimeMillis()
            // what this line carries (Settings › Streams): the engine's estimate, 20 s in, every ~5 s of play — a P2P
            // play would measure the loopback, so it is left out
            if (exo.isPlaying && stallWatch.playedAt > 0 && now - stallWatch.playedAt > 20_000 && !P2p.isLocal(url)) {
                bwTick[0]++
                if (bwTick[0] % 12 == 0) bandwidth.bitrateEstimate.let { if (it > 0) Prefs.noteBandwidth(context, it) }
            }
            if (swapOffer != null && now - swapShownAt > 60_000) swapOffer = null   // an offer nobody took lapses once playback has settled
            // The pause board: a moment after pausing (or at the end), when nothing
            // else is open, and only once something has actually played.
            val resting = !exo.isPlaying && (exo.playbackState == Player.STATE_ENDED ||
                (exo.playbackState == Player.STATE_READY && !exo.playWhenReady))
            if (!resting) pausedSince = 0L else if (pausedSince == 0L) pausedSince = now
            // the board's own conditions, less its two waits — read BEFORE the hide below, which depends on them
            val boardCan = Prefs.pauseBoard && resting && !inPipMode.value && !subPanelOpen && !sleepMenuOpen &&
                !upnextOpen && exo.currentPosition > 1000
            // Controls hide after (Settings › Playback)
            // (a phone on its side, paused, too: there the board and the controls cannot share the screen, and the board
            // waits — but only when the board WILL come; at 0:00, in picture-in-picture or under Up next it never does,
            // and hiding the controls there left a bare picture)
            // Never while TalkBack explores the screen: a reader walking through the controls cannot race a fade.
            val pausedShort = boardCan && context.resources.configuration.screenHeightDp < 480
            if (chromeVisible && (exo.isPlaying || pausedShort) && !subPanelOpen && !sleepMenuOpen && !scrubbing && !trackListOpen &&
                !touchExploring(context) &&
                now - chromeTouchedAt > (Prefs.controlsHide * 1000f).toLong()) chromeVisible = false
            // Skip intro / recap: offer the pill, or take it on Auto once per segment a sitting
            // (a scrub back into the titles is taken as meant); a party viewer follows the host.
            val hit = if (exo.isCurrentMediaItemLive || (partyUi.active() && !partyUi.isHost)) null
                else SkipSegments.at(skipSegs, exo.currentPosition)
            if (hit == null) skipKind = null
            else if (Prefs.skipIntro == "auto" && hit.first !in skipAuto) {
                if (exo.isPlaying) {
                    skipAuto.add(hit.first); skipKind = null
                    exo.seekTo(hit.second.endMs)
                    skipNote = SkipSegments.note(hit.first) to now
                }
            } else skipKind = hit.first
            // Sleep timer: keep the pill's minutes current; pause when the time is up.
            if (sleepMode == "min") {
                if (now >= sleepAt) {
                    sleepMode = ""; sleepAt = 0L; sleepFired = true
                    upnextOpen = false; upnextCounting = false
                    exo.pause()
                    chromeVisible = true; chromeTouchedAt = now
                }
                sleepRender()
            }
            if (sleepFired && exo.isPlaying) sleepFired = false     // played on: the board reads Paused again
            pauseBoardOn = boardCan && now - pausedSince > 1600 && now - chromeTouchedAt > 1600
            if (pinfoOn) infoRows = playbackInfoRows(
                exo, bandwidth, subOffsetMs,
                scrubStatusLine(
                    Prefs.scrubFrames, isLiveState,
                    // the tracks' own protection: every DASH item carries a ClearKey configuration, protected or not
                    exo.videoFormat?.drmInitData != null || exo.audioFormat?.drmInitData != null,
                    scrubPreview?.status?.value,
                ),
                p2pLine = if (P2p.isLocal(url)) P2p.line() else null,
                decoderLine = if (softDecode) "on the processor (the chip refused this picture)" else null,
                stalls = stallWatch.total,
                viaLine = viaRelay?.let { "${it.name} on your network" },
            )
        }
    }

    // The overlay's clock: the add-on cues on at the film's position less the timing nudge (+ = later), redrawn only
    // when that set changes. A nudge counts from the next tick — nothing is re-fed or re-prepared.
    LaunchedEffect(overlayCues) {
        val all = overlayCues
        if (all == null) { overlaySubView?.setCues(null); return@LaunchedEffect }
        val on = ArrayList<Int>()
        var shown: List<Int>? = null
        var shownOn: SubtitleView? = null
        while (true) {
            cuesAt(all, (exo.currentPosition - subOffsetMs) * 1000L, on)
            val v = overlaySubView
            if (on != shown || v !== shownOn) {
                shown = ArrayList(on); shownOn = v
                v?.setCues(on.flatMap { all[it].cues })
            }
            delay(80)
        }
    }

    // the settings' subtitle languages, as trackParams reads them
    fun prefLangs() = listOf(Prefs.subLang.ifEmpty { java.util.Locale.getDefault().language }, Prefs.subLang2).filter { it.isNotEmpty() }
    /**
     * Download an add-on subtitle and draw it over the video; the stream's own tracks stand aside. Nothing about the
     * stream changes, so it never restarts (issue #1). [auto] is the autoload's pick: quiet, and not a choice by hand.
     * true = showing, false = it would not load, null = something newer was chosen meanwhile.
     */
    suspend fun loadPick(st: SubTrack, auto: Boolean): Boolean? {
        val serial = ++pickSerial[0]
        val cues = subText(st)?.let { t -> withContext(Dispatchers.Default) { parseSubCues(t) } }
        if (serial != pickSerial[0]) return null
        if (!auto) subBusy = false
        if (cues == null) return false
        // and the stream's tracks, when they come back (Off, a stream track, the next title), prefer a hand-picked
        // language, then the settings' (the pick's raw code used to replace them: "pob" matched nothing)
        val prefer = (listOf(subLangKey(st.lang).ifEmpty { st.lang }) + (if (Prefs.subStart != "off") prefLangs() else emptyList())).distinct()
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .apply { if (!auto) setPreferredTextLanguages(*prefer.toTypedArray()) }
            .build()
        overlayCues = cues
        activeAddonSub = st.url
        subOffsetMs = 0L
        subForced = true
        if (!auto) pickLang = st.lang
        return true
    }
    fun applyPick(st: SubTrack) {
        subBusy = true
        subChosenFor = contentId
        scope.launch { if (loadPick(st, auto = false) == false) Toasts.show("Could not load that subtitle") }
    }
    /** Subtitles off, or a stream track took over: the add-on pick, its overlay and its timing are gone. */
    fun subsOff() {
        pickSerial[0]++
        subBusy = false
        activeAddonSub = null; overlayCues = null
        subOffsetMs = 0L
        pickLang = null
        subChosenFor = contentId
    }

    // Subtitles when a video starts, for the add-ons' subtitles too (issue #1: "the chosen subtitles for autoload don't
    // show up" — only the stream's own tracks were ever switched on). Once this address's tracks are known, the languages
    // are walked in order: one the stream already shows ends it; else the first add-on file in it goes on by itself; a
    // language nobody has offered yet waits while add-ons are still answering. A file that will not load gives way to
    // the next. Always, with nothing in a preferred language and no text in the stream at all: the first add-on file.
    // Once per address, never over a choice made by hand for this title.
    LaunchedEffect(url, addonSubs, tracksFor, subSearching, autoSubTry) {
        if (autoSubBusy[0] == url || tracksFor != url || autoSubDone == url || contentId == null || subChosenFor == contentId || overlayCues != null) return@LaunchedEffect
        val langs = (listOfNotNull(pickLang) + (if (Prefs.subStart != "off") prefLangs() else emptyList()))
            .map { subLangKey(it) }.filter { it.isNotEmpty() }.distinct()
        // off in the settings, or turned off by hand on an earlier title (the stream's tracks stay off too)
        if (langs.isEmpty() || C.TRACK_TYPE_TEXT in exo.trackSelectionParameters.disabledTrackTypes) { autoSubDone = url; return@LaunchedEffect }
        var pick: AddonSub? = null
        for (k in langs) {
            // the stream shows this language already (Media3 chose it from the same list) — a forced-only track, signs
            // and foreign lines, is not subtitles in it
            if (embeddedSubs.any { it.selected && (it.format.selectionFlags and C.SELECTION_FLAG_FORCED) == 0 && subLangKey(it.format.language) == k }) {
                autoSubDone = url; return@LaunchedEffect
            }
            pick = addonSubs.firstOrNull { subLangKey(it.track.lang) == k && it.track.url !in autoSubFailed }
            if (pick != null) break
            if (subSearching) return@LaunchedEffect           // add-ons still answering may have this language
        }
        if (pick == null && Prefs.subStart == "always" && embeddedSubs.isEmpty()) pick = addonSubs.firstOrNull { it.track.url !in autoSubFailed }
        val p = pick ?: run { if (!subSearching) autoSubDone = url; return@LaunchedEffect }
        val cid = contentId
        val u = url
        autoSubBusy[0] = u                                      // a download left over from the last title blocks nothing here
        scope.launch {
            val r = try { if (subChosenFor == cid) null else loadPick(p.track, auto = true) } finally { if (autoSubBusy[0] == u) autoSubBusy[0] = null }
            if (r == true) autoSubDone = u
            else {
                if (r == false) autoSubFailed += p.track.url
                autoSubTry++                                    // look again: the next file, the next language, or nothing
            }
        }
    }

    // In picture-in-picture only the video shows — no chrome, no gestures.
    val pip = inPipMode.value
    LaunchedEffect(pip) { if (pip) chromeVisible = false }

    // A party host arriving on a new stream takes the whole room along (mirrors the web player).
    // Keyed to the address: the next-episode hop and a source swap replace the stream in THIS composition,
    // and they must reach the room too. The whole description goes, so viewers get subtitles and progress.
    // Not a P2P stream: its address is this phone's own loopback engine and means nothing anywhere else.
    LaunchedEffect(url) {
        if (partyUi.active() && partyUi.isHost && !P2p.isLocal(url)) {
            partyUi.session?.sendStream(PartyStreamDesc(url, title, subs, contentType, contentId, contentName, poster, addonUrl))
        }
    }
    // Watch-party sync loop: hosts broadcast state, viewers glide to the host's position.
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(1000)
            n++
            if (!partyUi.active()) continue
            if (partyUi.isHost) {
                if (hostDirty || n % 4 == 0) {
                    hostDirty = false
                    val live = exo.isCurrentMediaItemLive
                    val pos = if (live) exo.currentLiveOffset.coerceAtLeast(0L) / 1000.0
                              else exo.currentPosition.coerceAtLeast(0L) / 1000.0
                    partyUi.session?.sendState(exo.isPlaying, pos, live)
                }
                continue
            }
            val s = partyUi.lastState ?: continue
            if (!s.playing) {
                if (exo.isPlaying) exo.pause()
                exo.setPlaybackSpeed(1f)
                if (!s.live && abs(exo.currentPosition / 1000.0 - s.pos) > 1) exo.seekTo((s.pos * 1000).toLong())
                continue
            }
            if (!exo.isPlaying && exo.playbackState == Player.STATE_READY) exo.play()
            val err: Double = if (s.live) {
                if (!exo.isCurrentMediaItemLive) continue
                exo.currentLiveOffset.coerceAtLeast(0L) / 1000.0 - s.pos
            } else {
                s.pos + (System.currentTimeMillis() - s.atLocal) / 1000.0 - exo.currentPosition / 1000.0
            }
            when {
                abs(err) > 1.25 -> {
                    if (System.currentTimeMillis() - partyUi.lastSeekAt > 4000) {
                        partyUi.lastSeekAt = System.currentTimeMillis()
                        exo.seekTo((exo.currentPosition + err * 1000).toLong().coerceAtLeast(0L))
                    }
                    exo.setPlaybackSpeed(1f)
                }
                err > 0.4 -> exo.setPlaybackSpeed(1.06f)
                err < -0.4 -> exo.setPlaybackSpeed(0.94f)
                abs(err) < 0.15 -> exo.setPlaybackSpeed(1f)
            }
        }
    }

    // ---- the remote (Android TV, or any keyboard) — issue #1, and the shared player's #pui key rules ----
    // With the controls asleep, a remote's press wakes them (volume aside): ←/→ open the preview on the seek bar (OK
    // jumps, Back drops it), the other arrows and OK light Play/Pause — except OK on a floating button (Skip intro,
    // Try another source, Up next), which is that button — and the media buttons show them while doing their own
    // job. While they are up every press keeps them up, and Back puts them away before it leaves the player. None
    // of it runs for a finger: a phone never sends a D-pad key, and the other buttons wait for a remote.
    val inputModes = LocalInputModeManager.current
    val tvBox = remember { Account.isTv(context) }
    val remoteNow = tvBox || inputModes.inputMode == InputMode.Keyboard
    val playFocus = remember { FocusRequester() }
    val sleepFocus = remember { FocusRequester() }
    val sleepRowFocus = remember { FocusRequester() }
    val upnextFocus = remember { FocusRequester() }
    var chromeHasFocus by remember { mutableStateOf(false) }     // a control inside the chrome is lit
    var rootHasFocus by remember { mutableStateOf(false) }       // anything on this screen is lit
    var upnextHasFocus by remember { mutableStateOf(false) }
    var scrubKick by remember { mutableStateOf<Pair<Int, Int>?>(null) }   // (direction, serial) for the seek bar
    var wakeSerial by remember { mutableStateOf(0) }
    /** The controls stay up under the Audio / Quality list (the web keeps them for its menus), so the remote
        comes back to the button it left from rather than to a chrome that faded while the list was open. */
    fun android.app.Dialog.keepChrome(): android.app.Dialog = apply {
        // raised when it is SHOWN: a show() that throws must not leave the chrome held up for the rest of the play
        setOnShowListener { trackListOpen = true }
        setOnDismissListener { trackListOpen = false; chromeTouchedAt = System.currentTimeMillis() }
    }
    fun openTrackList(label: String, type: Int) {
        runCatching {
            TrackSelectionDialogBuilder(context, label, exo, type)
                .setShowDisableOption(false)
                .apply { if (type == C.TRACK_TYPE_VIDEO) setAllowAdaptiveSelections(true) }
                .build().keepChrome().show()
        }
    }
    val landing = remember { LongArray(1) }                  // until when a wake's focus is still on its way
    val swallowUp = remember { IntArray(1) { -1 } }          // the release of a press this screen took for itself
    fun wake(dir: Int) {
        chromeVisible = true
        val now = System.currentTimeMillis()
        chromeTouchedAt = now
        landing[0] = now + 500
        if (dir != 0) scrubKick = dir to ((scrubKick?.second ?: 0) + 1) else wakeSerial++
    }
    // the chrome fades in, so Play/Pause is composed a frame or two after the press: retry across a few frames
    LaunchedEffect(wakeSerial) {
        if (wakeSerial == 0) return@LaunchedEffect
        repeat(10) {
            withFrameNanos {}
            if (runCatching { playFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    fun remoteDown(code: Int, repeat: Int): Boolean {
        val side = code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        val arrow = side || code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN
        val ok = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER ||
            code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
        val now = System.currentTimeMillis()
        if (!arrow && !ok) {
            if (!remoteNow) return false          // a phone's headset button and the like: exactly as before
            // Space is play/pause, as on the web — once per press: a held key's repeats are eaten, not toggled
            if (code == android.view.KeyEvent.KEYCODE_SPACE && !subPanelOpen && !sleepMenuOpen) {
                if (repeat == 0) Util.handlePlayPauseButtonAction(exo)
                chromeVisible = true; chromeTouchedAt = now; swallowUp[0] = code
                return true
            }
            // the buttons that name a control go straight to it: Captions opens Subtitles, Audio the audio list, Next
            // the next episode (the session has one item, so it had nothing to skip to)
            when (code) {
                android.view.KeyEvent.KEYCODE_CAPTIONS -> {
                    sleepMenuOpen = false; subPanelOpen = true; chromeVisible = true; chromeTouchedAt = now
                    swallowUp[0] = code; return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> if (audioTrackCount >= 1 && !subPanelOpen) {
                    chromeVisible = true; chromeTouchedAt = now; openTrackList("Audio", C.TRACK_TYPE_AUDIO)
                    swallowUp[0] = code; return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> nextEpisode?.let { n ->
                    if (repeat == 0) { upnextCounting = false; autoRun = 0; onPlayNext(n) }
                    swallowUp[0] = code; return true
                }
            }
            // the remote's other buttons (play/pause, rewind, fast-forward, next, menu, info, captions) show the
            // controls, so what they did is on screen; the key itself goes on — media keys to the session, as before.
            // Back has its own handler below, and volume stays the system's.
            if (code in REMOTE_WAKE_KEYS) { chromeVisible = true; chromeTouchedAt = now }
            return false
        }
        // the Subtitles panel and the sleep menu own the keys while they are up; the chrome waits behind them
        if (subPanelOpen || sleepMenuOpen) { chromeTouchedAt = now; return false }
        // a wake's focus has not landed yet: a held key's repeats belong to the wake, or they would land on nothing
        if (now < landing[0] && !chromeHasFocus) return true
        if (chromeVisible) {
            chromeTouchedAt = now                 // moving about the controls keeps them up
            if (rootHasFocus) return false        // something is lit: Compose moves focus as usual
            // up, but nothing lit (the player just opened, the Audio list just closed): the first press lights Play/Pause
            wake(0); swallowUp[0] = code
            return true
        }
        // asleep but still fading, a control still lit: the press only brings them back, focus where it was —
        // otherwise OK would press that control (Play/Pause, say) behind a chrome still on its way out
        if (chromeHasFocus) { chromeVisible = true; chromeTouchedAt = now; swallowUp[0] = code; return true }
        // asleep: OK on a floating button is that button, and ←/→ walk the Up next card's two buttons
        if (rootHasFocus && (ok || (side && upnextOpen && upnextHasFocus))) return false
        wake(when (code) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> -1
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> 0
        })
        swallowUp[0] = code
        return true
    }
    val onRemoteKey by rememberUpdatedState<(android.view.KeyEvent) -> Boolean>({ ev ->
        when {
            // the release of a press taken here is taken too, or it would click whatever the press just lit
            ev.action == android.view.KeyEvent.ACTION_UP ->
                if (ev.keyCode == swallowUp[0]) { swallowUp[0] = -1; true } else false
            ev.action != android.view.KeyEvent.ACTION_DOWN || inPipMode.value -> false
            else -> remoteDown(ev.keyCode, ev.repeatCount)
        }
    })
    DisposableEffect(Unit) {
        val mine: (android.view.KeyEvent) -> Boolean = { onRemoteKey(it) }
        PlayerKeys.handler = mine
        // a screen replacement briefly overlaps two players: only the one that installed it takes it away
        onDispose { if (PlayerKeys.handler === mine) PlayerKeys.handler = null }
    }
    // Back peels one layer on a remote, as on the web: the sleep menu, then the Up next card, then the controls —
    // and only then leaves. A phone's back gesture still leaves at once. The Subtitles panel and a seek-bar preview
    // register their own Back later, so they are peeled first.
    BackHandler(enabled = remoteNow && !pip && (sleepMenuOpen || upnextOpen || chromeVisible)) {
        when {
            sleepMenuOpen -> { sleepMenuOpen = false; chromeTouchedAt = System.currentTimeMillis() }
            upnextOpen -> { upnextCounting = false; upnextOpen = false; upnextDismissed = true; onCancelNext() }
            else -> chromeVisible = false
        }
    }
    // The sleep menu floats at the right edge, away from the toolbar the D-pad walks: opening it
    // lights its chosen row, and closing it (a pick or Back) hands the remote back to the Sleep button.
    val sleepWasOpen = remember { BooleanArray(1) }
    LaunchedEffect(sleepMenuOpen) {
        val target = when {
            sleepMenuOpen && remoteNow -> sleepRowFocus
            !sleepMenuOpen && sleepWasOpen[0] && remoteNow && chromeVisible -> sleepFocus
            else -> null
        }
        sleepWasOpen[0] = sleepMenuOpen
        if (target != null) repeat(10) {
            withFrameNanos {}
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    // With the chrome asleep the Up next card takes the remote (after Skip and the source pill, which already do),
    // so OK near the end is Play now — as the web card owns OK.
    LaunchedEffect(upnextOpen, chromeVisible, skipKind == null, swapOffer == null) {
        if (!upnextOpen || chromeVisible || !remoteNow || skipKind != null || swapOffer != null) return@LaunchedEffect
        repeat(10) {
            withFrameNanos {}
            if (runCatching { upnextFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    // On a phone on its side the chrome keeps the title beside Back; on a TV-height screen it sits above the
    // scrubber, so the cards that float above the chrome (Skip, Up next) start higher there.
    val shortScreen = LocalConfiguration.current.screenHeightDp < 480
    val aboveChrome = if (!chromeVisible) 40.dp else if (shortScreen) 150.dp else 214.dp
    // A phone turned on its side is watching: the status and navigation bars go (a swipe shows them for a moment)
    // without a trip to the Fullscreen button, and come back upright; leaving the player restores them (onDispose).
    // The button still locks landscape. Not on a TV (no bars to hide) and not in picture-in-picture.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(landscape, isFullscreen, pip) {
        if (tvBox || pip) return@LaunchedEffect
        activity?.let { setImmersive(it, isFullscreen || landscape) }
    }
    // a phone on its side has no room for both: the board would sit over the centre transport, so while the
    // controls are up there it steps aside (with its scrim and the title it hides), and comes back when they fade
    val boardUp = pauseBoardOn && !(shortScreen && chromeVisible)
    Box(Modifier.fillMaxSize().background(Color.Black).onFocusChanged { rootHasFocus = it.hasFocus }) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false          // the Title Card chrome is the controller
                    // and never a place for the remote to land: a focused PlayerView would swallow the D-pad
                    isFocusable = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    playerViewRef = this
                    subtitleView?.let { SubStyle.apply(ctx, it) }
                    // add-on subtitles (loadPick) draw on a second SubtitleView just above the stream's own, inside the
                    // picture's frame — so they size and sit exactly as those do (spread over the whole screen they
                    // were 3.6x larger on an upright phone, and sat in a wide film's black bar)
                    val own = subtitleView
                    val frame = own?.parent as? android.view.ViewGroup ?: this
                    val over = SubtitleView(ctx).apply { isFocusable = false; SubStyle.apply(ctx, this) }
                    frame.addView(
                        over, if (own != null && own.parent === frame) frame.indexOfChild(own) + 1 else frame.childCount,
                        android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                    overlaySubView = over
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Touch gestures: double-tap left/right = ±10s, horizontal swipe = seek,
        // plain tap = toggle the chrome. The chrome's own controls sit above
        // this layer, so they stay tappable; remote/D-pad (TV) is unaffected.
        // Under TalkBack the gestures are out of reach, so the layer is also one
        // named action: a double-tap on it shows or hides the controls.
        if (!pip) {
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics {
                        a11yClick(label = if (chromeVisible) "Hide controls" else "Show controls") {
                            chromeVisible = !chromeVisible; chromeTouchedAt = System.currentTimeMillis(); true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { pos ->
                                val zone = if (Prefs.gestures) tapZone(pos.x, size.width) else 0
                                val f = skipFlash
                                if (zone != 0 && f != null && f.first == zone &&
                                    System.currentTimeMillis() - f.third < 900
                                ) doSkip(zone) // a quick 3rd/4th tap keeps skipping
                                else { chromeVisible = !chromeVisible; chromeTouchedAt = System.currentTimeMillis() }
                            },
                            onDoubleTap = { pos ->
                                val zone = if (Prefs.gestures) tapZone(pos.x, size.width) else 0
                                if (zone != 0) doSkip(zone)
                                else { chromeVisible = !chromeVisible; chromeTouchedAt = System.currentTimeMillis() }
                            },
                            // Hold the surface to race ahead; letting go restores the
                            // speed you actually chose, not a hardcoded 1×.
                            onLongPress = {
                                if (Prefs.holdSpeed) {
                                    heldSpeed = exo.playbackParameters.speed
                                    exo.setPlaybackSpeed(Prefs.holdRate)
                                }
                            },
                            onPress = {
                                tryAwaitRelease()
                                heldSpeed?.let { exo.setPlaybackSpeed(it); heldSpeed = null }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        var base = 0L
                        var accum = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { base = exo.currentPosition; accum = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accum += dragAmount
                                var t = base + (accum / size.width * 90_000f).toLong() // full-width swipe ≈ 90s
                                if (t < 0) t = 0
                                val dur = exo.duration
                                if (dur != C.TIME_UNSET && t > dur) t = dur
                                dragSeek = Pair(t, t - base)
                            },
                            onDragEnd = { dragSeek?.let { exo.seekTo(it.first) }; dragSeek = null },
                            onDragCancel = { dragSeek = null }
                        )
                    }
            )
        }
        // the Title Card chrome (see PlayerChrome.kt)
        // a scrim under the pause board, so the words read over any picture
        if (boardUp && !pip) Box(Modifier.fillMaxSize().background(Color(0x7A000000)))
        if (!pip) Box(Modifier.fillMaxSize().onFocusChanged { chromeHasFocus = it.hasFocus }) { TitleCardChrome(
            visible = chromeVisible,
            title = showName,
            isPlaying = isPlayingState,
            isLive = isLiveState,
            positionMs = posMs, durationMs = durMs, bufferedMs = bufMs,
            episodeTag = episodeTag,
            sourceLine = sourceLine,
            clockLine = if (Prefs.clock) clockLine(context, durMs - posMs, exo.playbackParameters.speed, live = isLiveState || durMs <= 0) else null,
            seekStepMs = Prefs.seekStep * 1000L,
            liveOffsetMs = liveOffMs,
            subtitlesFocus = subsFocus,
            playFocus = playFocus,
            sleepFocus = sleepFocus,
            scrubKick = scrubKick,
            onScrubKickTaken = { k -> if (scrubKick?.second == k) scrubKick = null },
            onGoLive = { exo.seekToDefaultPosition(); chromeTouchedAt = System.currentTimeMillis() },
            canFullscreen = !tvBox,
            canInvite = !tvBox,
            dimTitle = boardUp,
            infoOn = pinfoOn,
            onInfo = { pinfoOn = !pinfoOn; chromeTouchedAt = System.currentTimeMillis() },
            sleepLabel = sleepLabel,
            onSleep = { sleepMenuOpen = !sleepMenuOpen; subPanelOpen = false; chromeTouchedAt = System.currentTimeMillis() },
            qualityLabel = qualityLabel?.takeIf { videoQualityCount >= 1 },
            speedLabel = speedLabel,
            partyActive = partyUi.active(),
            partyBadge = partyUi.code?.let { c -> c + " · " + partyUi.count },
            hasNext = nextEpisode != null,
            canPip = (activity as? MainActivity)?.pipSupported() == true,
            // the panel explains itself when there is nothing to show; one audio track is still worth naming
            showSubtitles = true,
            showAudio = audioTrackCount >= 1,
            // a press on Back LEAVES: sending it through the dispatcher would reach the peel handler below first, and
            // since the button only exists while the chrome is up, on a remote it could only ever hide the chrome
            onBack = { onExit?.invoke() ?: (activity as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() },
            onPlayPause = {
                // Media3's own rule: after an error it prepares again, at the end it starts over — play() alone did
                // nothing in either state, so Play after a network drop was a dead button
                Util.handlePlayPauseButtonAction(exo)
                chromeTouchedAt = System.currentTimeMillis()
            },
            onSeekBy = { d -> seekBy(d); chromeTouchedAt = System.currentTimeMillis() },
            onSeekTo = { t -> exo.seekTo(t.coerceAtLeast(0L)); chromeTouchedAt = System.currentTimeMillis() },
            scrubFrame = if (Prefs.scrubFrames) scrubPreview?.frame else null,
            onScrub = { t ->
                if (t != null || scrubbing) chromeTouchedAt = System.currentTimeMillis()
                scrubbing = t != null
                // frames only for a plain file: never live, never an encrypted item, never with the setting off
                if (t == null) scrubPreview?.idle()
                else if (Prefs.scrubFrames && !isLiveState && exo.currentMediaItem?.localConfiguration?.drmConfiguration == null) scrubPreview?.request(t)
            },
            onNext = { nextEpisode?.let { upnextCounting = false; autoRun = 0; onPlayNext(it) } },
            onParty = {
                if (partyUi.active()) onPartyLeave()
                else if (P2p.isLocal(url)) Toasts.show("A P2P stream plays only on this device — a party cannot follow it.")
                else onPartyStart(PartyStreamDesc(url, title, subs, contentType, contentId, contentName, poster, addonUrl))
                chromeTouchedAt = System.currentTimeMillis()
            },
            onInvite = {
                partyUi.code?.let { c ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        setType("text/plain")
                        putExtra(Intent.EXTRA_TEXT, "Watch with me on Nebula — join party $c: https://play.rifflehq.in/?party=$c")
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Invite to watch party")) }
                }
            },
            onReact = { e ->
                partyUi.session?.sendReact(e)
                partyUi.react(e, "")
                chromeTouchedAt = System.currentTimeMillis()
            },
            onSubtitles = {
                subPanelOpen = !subPanelOpen
                sleepMenuOpen = false
                chromeTouchedAt = System.currentTimeMillis()
            },
            onAudio = { openTrackList("Audio", C.TRACK_TYPE_AUDIO) },
            onQuality = { openTrackList("Quality", C.TRACK_TYPE_VIDEO) },
            onSpeedCycle = {
                val rates = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                val next = rates[(rates.indexOfFirst { it == exo.playbackParameters.speed }
                    .takeIf { it >= 0 } ?: 2).let { (it + 1) % rates.size }]
                exo.setPlaybackSpeed(next)
                speedLabel = speedText(next)
                Prefs.setLastSpeed(context, next)             // "Speed when a video starts · Last used" reads this
                chromeTouchedAt = System.currentTimeMillis()
            },
            onPip = { (activity as? MainActivity)?.enterPip() },
            onFullscreen = {
                isFullscreen = !isFullscreen
                activity?.let {
                    it.requestedOrientation =
                        if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    setImmersive(it, isFullscreen)
                }
            },
        ) }
        // The pause board (top-left, under the back button) and the playback HUD (top-right)
        if (!pip) {
            val remain = (durMs - posMs).coerceAtLeast(0L)
            val ended = exo.playbackState == Player.STATE_ENDED
            val meta = mutableListOf<Pair<String, Boolean>>()
            if (!ended) {
                if (isLiveState) {
                    val off = exo.currentLiveOffset
                    meta += (if (off != C.TIME_UNSET && off > 12_000) fmtTime(off) + " behind live" else "At the live edge") to false
                } else if (durMs > 0) {
                    meta += (if (remain >= 60_000) "${remain / 60_000} min left" else "Under a minute left") to false
                    // the same clock as the controls' top line (the device's 12/24-hour setting), so the two never disagree
                    meta += "Ends " + clockAt(context, System.currentTimeMillis() + (remain / exo.playbackParameters.speed.coerceAtLeast(0.1f)).toLong()) to false
                }
            }
            nextEpisode?.let { n ->
                meta += ("Up next · S${n.season}" + (n.episode?.let { "E$it" } ?: "") +
                    (if (n.name.isNotEmpty()) " · ${n.name}" else "")) to true
            }
            PauseBoard(
                visible = boardUp,
                kicker = (if (sleepFired) "Sleep timer · " else "") +
                    when { ended -> "Finished"; isLiveState -> "Live · Paused"; else -> "Paused" },
                title = showName,
                sub = episodeTag,
                desc = currentEpisode?.overview?.takeIf { it.isNotBlank() } ?: description,
                meta = meta,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 84.dp),
            )
            if (pinfoOn) PlaybackInfoHud(infoRows, Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 76.dp))
        }
        // Party reactions float up from the bottom
        partyUi.reactions.forEach { r ->
            key(r.first) { ReactionFloat(r.second, r.third) }
        }
        // Sleep timer menu (the Sleep pill toggles it): by minutes, or at the end of this episode
        if (sleepMenuOpen && !pip) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
                    .width(280.dp)
                    .background(SurfaceC, RoundedCornerShape(14.dp))
                    .border(1.dp, Line2, RoundedCornerShape(14.dp))
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "SLEEP TIMER",
                    color = MutedC, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                // the row that is set is marked, and the remote lands on it — never on Off while a timer runs, where one
                // reflex OK would cancel it
                val minRow = if (sleepMode == "min") sleepMins else 0
                val epRow = sleepMode == "ep" && nextEpisode != null
                fun land(here: Boolean) = if (here) Modifier.focusRequester(sleepRowFocus) else Modifier
                SubMenuRow("Off", sleepMode.isEmpty(), land(!epRow && minRow == 0)) { sleepSet("") }
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    SubMenuRow("In ${sleepText(m * 60_000L)}", minRow == m, land(minRow == m)) { sleepSet("min", m) }
                }
                if (nextEpisode != null) SubMenuRow("When this episode ends", sleepMode == "ep", land(epRow)) { sleepSet("ep") }
                Text(
                    if (sleepMode == "min") "Pausing in ${sleepText(sleepAt - System.currentTimeMillis())}."
                    else "Playback pauses when the time is up.",
                    color = MutedC, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // The Subtitles panel (SubtitlesPanel.kt): languages · that language's tracks · style, over the
        // still-playing video. One layer: Back closes it, not the player; focus goes back to its opener.
        if (subPanelOpen && !pip) {
            val keysMode = LocalInputModeManager.current.inputMode == InputMode.Keyboard
            fun closePanel() {
                subPanelOpen = false
                chromeTouchedAt = System.currentTimeMillis()
                if (keysMode) runCatching { subsFocus.requestFocus() }
            }
            BackHandler { closePanel() }
            SubtitlesPanel(
                player = exo,
                embedded = embeddedSubs,
                addonSubs = addonSubs,
                activeAddonSub = activeAddonSub,
                streamSource = streamSource,
                searching = subSearching,
                busy = subBusy,
                offsetMs = subOffsetMs,
                canShift = overlayCues != null,
                onNudge = { d -> subOffsetMs += d },
                onResetTiming = { subOffsetMs = 0L },
                onPickAddon = { st -> applyPick(st) },
                onPickEmbedded = { subsOff() },
                onOff = { subsOff() },
                onClose = { closePanel() },
            )
        }
        // Skip intro / recap: a glass pill above the pills row, at the bottom edge when the chrome
        // is away. With the chrome hidden it takes focus, so OK on a remote is the skip.
        skipKind?.let { kind ->
            if (pip) return@let
            val skipFocus = remember { FocusRequester() }
            GlassPill(
                SkipSegments.label(kind),
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = aboveChrome)
                    .focusRequester(skipFocus),
            ) { skipNow(); chromeTouchedAt = System.currentTimeMillis() }
            LaunchedEffect(kind, chromeVisible) { if (!chromeVisible) runCatching { skipFocus.requestFocus() } }
        }
        // A source that keeps stalling (StallWatch.kt): the row after it, on a pill one step above the skip's; one press
        // switches with the second kept. With the chrome hidden it takes focus, so OK on a remote is the switch.
        swapOffer?.let { (st, from) ->
            if (pip) return@let
            val swapFocus = remember { FocusRequester() }
            GlassPill(
                swapWhy + " · Try " + StallWatch.name(st, from),
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = aboveChrome + 54.dp)
                    .focusRequester(swapFocus),
            ) {
                swapOffer = null
                runCatching { snapshotProgress() }
                onSwapSource(st, from, if (exo.isCurrentMediaItemLive) -1L else exo.currentPosition.coerceAtLeast(0L))
            }
            LaunchedEffect(st.url, chromeVisible) { if (!chromeVisible && skipKind == null) runCatching { swapFocus.requestFocus() } }
        }
        // Up next: offered near the end, counts down and autoplays once the episode ends.
        if (upnextOpen && nextEpisode != null && !pip) {
            Column(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (chromeVisible) aboveChrome else 150.dp)
                    .onFocusChanged { upnextHasFocus = it.hasFocus }
                    .width(300.dp)
                    // the player's glass (the pills' and the info panel's material) with its 1 dp hairline
                    .background(BarGlass, RoundedCornerShape(16.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                Text(if (stillAsk) "Still watching?" else "Up next", color = Color(0xA8EBEBF5), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                // what it will play from — chosen while this one still runs
                val srcLine = NextEp.sourceLine(context, nextEpisode.id)
                Text(
                    "S${nextEpisode.season}" + (nextEpisode.episode?.let { "E$it" } ?: "") +
                        (if (nextEpisode.name.isNotEmpty()) " · ${nextEpisode.name}" else ""),
                    color = TextC, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 5.dp, bottom = if (srcLine != null) 5.dp else 14.dp),
                )
                if (srcLine != null) Text(
                    srcLine, color = MutedC, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.6.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 14.dp),
                )
                // Under a remote the lit button is the white one and the other is glass (tvOS): with Play now always white,
                // the two read the same whichever was lit. A finger keeps Play now white as the primary.
                val playNowSrc = remember { MutableInteractionSource() }
                val dismissSrc = remember { MutableInteractionSource() }
                val playNowLit by playNowSrc.collectIsFocusedAsState()
                val dismissLit by dismissSrc.collectIsFocusedAsState()
                val keysLit = LocalInputModeManager.current.inputMode == InputMode.Keyboard && (playNowLit || dismissLit)
                val playNowWhite = !keysLit || playNowLit
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        // a hand on Play now ends the autoplay run the Still watching? count is about
                        onClick = { upnextCounting = false; autoRun = 0; onPlayNext(nextEpisode) },
                        modifier = Modifier.focusRequester(upnextFocus),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (playNowWhite) Color.White else Color(0x6B505058),
                            contentColor = if (playNowWhite) Color.Black else TextC,
                        ),
                        shape = RoundedCornerShape(50),
                        interactionSource = playNowSrc,
                    ) { Text(if (upnextCounting) "Play now ($upnextLeft)" else "Play now", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { upnextCounting = false; upnextOpen = false; upnextDismissed = true; onCancelNext() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (keysLit && dismissLit) Color.White else Color(0x6B505058),
                            contentColor = if (keysLit && dismissLit) Color.Black else TextC,
                        ),
                        shape = RoundedCornerShape(50),
                        interactionSource = dismissSrc,
                    ) { Text("Dismiss") }
                }
            }
        }
        // Hold-to-speed chip: shown for exactly as long as the finger is down.
        // (an icon, not "▶▶": emoji draw in the font's own colours and size, and §9 says SVG icons)
        if (heldSpeed != null) Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp)
                .background(Color(0x8C000000), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                "${if (Prefs.holdRate % 1f == 0f) Prefs.holdRate.toInt().toString() else Prefs.holdRate.toString()}×",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        // A word after a skip, where the hold-to-speed chip sits.
        skipNote?.let { n ->
            Text(
                n.first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp)
                    .background(Color(0x8C000000), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // Transient ±10s indicator on the tapped side.
        skipFlash?.let { f ->
            Row(
                Modifier
                    .align(if (f.first > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 44.dp)
                    .background(Color(0x8C000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (f.first > 0) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp),
                )
                Text(
                    "${f.second}s", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        // Live seek preview while swiping: target position + signed delta.
        dragSeek?.let { d ->
            Text(
                fmtTime(d.first) + "  (" + (if (d.second >= 0) "+" else "−") + fmtTime(abs(d.second)) + ")",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x8C000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
        error?.let {
            Text(it, color = Color(0xFFFF6B6B), modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

/** "1.0×" / "1.25×" — the speed as the toolbar shows it. */
private fun speedText(v: Float): String = (if (v == 1f) "1.0" else v.toString().trimEnd('0').trimEnd('.')) + "×"

/**
 * Settings › Playback applied to Media3's track choice: the preferred audio language, the subtitle
 * language and its second choice ("" follows the device), the resolution cap on top of the picture
 * policy (Data saver is a 720p cap of its own), and whether text starts disabled (Off) or on.
 */
@OptIn(UnstableApi::class)
private fun trackParams(base: TrackSelectionParameters): TrackSelectionParameters {
    val b = base.buildUpon()
    if (Prefs.audioLang.isNotEmpty()) b.setPreferredAudioLanguage(Prefs.audioLang)
    val device = java.util.Locale.getDefault().language
    val langs = listOf(Prefs.subLang.ifEmpty { device }, Prefs.subLang2).filter { it.isNotEmpty() }.distinct()
    b.setPreferredTextLanguages(*langs.toTypedArray())
    var cap = if (Prefs.quality == "saver") 720 else Int.MAX_VALUE
    if (Prefs.maxRes > 0) cap = minOf(cap, Prefs.maxRes)
    b.setMaxVideoSize(Int.MAX_VALUE, cap)
    b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, Prefs.subStart == "off")
    return b.build()
}

/** Which double-tap zone an x position falls in: -1 left, +1 right, 0 middle (dead zone). */
private fun tapZone(x: Float, width: Int): Int = when {
    x < width * 0.35f -> -1
    x > width * 0.65f -> 1
    else -> 0
}

internal fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    // Locale.US: the device's locale would print Arabic-Indic or Bengali digits into the time pills
    return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
}

private fun setImmersive(activity: Activity, on: Boolean) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, !on)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (on) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
