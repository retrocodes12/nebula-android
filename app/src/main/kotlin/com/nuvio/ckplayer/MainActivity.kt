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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ViewAgenda
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
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val pendingPlay = mutableStateOf<PlayReq?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        Cloud.load(this)
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
internal val Bg = Color(0xFF000000)
internal val SurfaceC = Color(0xFF1C1C1E)     // secondary system background
internal val Surface2 = Color(0xFF2C2C2E)     // tertiary
internal val LineC = Color(0x1AFFFFFF)        // hairline separator
internal val Line2 = Color(0x29FFFFFF)
internal val MutedC = Color(0x99EBEBF5)       // secondary label
internal val FaintC = Color(0x4DEBEBF5)       // tertiary label
private val FillC = Color(0x3D767680)        // control fill
internal val TextC = Color(0xFFFFFFFF)

// Three registers and nothing between: a display serif for titles, one
// grotesque for the interface, a mono for every number and label.
internal val Sans = FontFamily(Font(R.font.geist))
internal val Mono = FontFamily(Font(R.font.geistmono))
private val Serif = Sans                     // display and UI share one family, as on Apple platforms

/** Everything unstyled falls back to the interface grotesque, not the system face. */
private val NebulaTypography = Typography().run {
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
    data object SettingsHome : Screen
    data object Friends : Screen
    data object SettingsPlayback : Screen
    data object Profile : Screen
    data object SettingsParty : Screen
    data class Detail(val addon: Addon, val item: MetaItem) : Screen
    data class Catalog(val addon: Addon, val initial: CatalogRef? = null) : Screen
    data class Episodes(val addon: Addon, val item: MetaItem) : Screen
    data class Streams(val addon: Addon, val item: MetaItem, val startOver: Boolean = false) : Screen
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
    fun next(): Episode? = if (index >= 0) episodes.getOrNull(index + 1) else null
    fun label(ep: Episode): String =
        (if (name.isNotEmpty()) "$name · " else "") +
            "S${ep.season}" + (ep.episode?.let { "E$it" } ?: "") +
            (if (ep.name.isNotEmpty()) " · ${ep.name}" else "")
    fun clear() { episodes = emptyList(); index = -1; name = ""; addon = null }
}
private val seriesChain = SeriesChain()
// which item auto stream selection already fired for (survives the screen)
private var autoPlayedFor: String? = null

/** One catalog's worth of content, tagged with where it came from and, on
    Home, where it sits (see [HomeRows.orderIndex]). */
private class CatRow(val addon: Addon, val catalog: CatalogRef, val items: List<MetaItem>, val oi: Int = 0)

/** Session cache of addon manifests (Home and Search both need them). */
internal val manifestCache = mutableMapOf<String, ManifestInfo>()
internal suspend fun manifestFor(url: String): ManifestInfo =
    manifestCache[url] ?: Stremio.loadManifest(url).also { manifestCache[url] = it }

/** Session cache of full metas (the detail page enhances from these). */
private val metaFullCache = mutableMapOf<String, FullMeta>()

/** The newest still-in-progress episode of a series. Episode ids are
    "<seriesId>:…" where seriesId itself may contain colons (kitsu:12345),
    so membership is a prefix test — never a split on the first colon. */
private fun seriesResumeRec(ctx: Context, seriesId: String): ProgressRec? =
    Progress.all(ctx).values.filter {
        !it.done && !it.dismissed && it.type == "series" &&
            it.id.startsWith("$seriesId:") &&
            it.pos >= Progress.MIN_POS_MS && it.dur > 0 && it.pos <= it.dur - Progress.END_GAP_MS
    }.maxByOrNull { it.at }

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
    // Continue watching is read straight from local storage, so it paints
    // instantly and survives every add-on being unreachable.
    var continueRows by mutableStateOf<List<ProgressRec>>(emptyList())
    var continueKey by mutableStateOf(0)
    fun invalidate() { sig = null; refreshKey++ }
    fun invalidateContinue() { continueKey++ }
}

/** Search tab state, hoisted for the same reason. */
private class SearchUiState {
    var query by mutableStateOf("")
    var submitted by mutableStateOf("")
    var sections by mutableStateOf<List<CatRow>>(emptyList())
    var searching by mutableStateOf(false)
    var searchedFor: String? = null
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
            )
        }
    }.getOrDefault(emptyList())
}
/**
 * Resuming an episode from Continue watching bypasses the picker, so rebuild the
 * chain in the background — otherwise "next episode" would be dead there.
 * Stremio episode ids are "<seriesId>:<season>:<episode>".
 */
private suspend fun hydrateSeriesChain(ctx: Context, addon: Addon, type: String, episodeId: String) {
    val seriesId = episodeId.substringBefore(':')
    if (seriesId.isEmpty() || seriesId == episodeId) return
    val order = listOf(addon) + loadAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
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
                .put("base", it.base).put("logo", it.logo ?: "")
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

@Composable
fun AppRoot(playReq: PlayReq? = null, onConsumed: () -> Unit = {}) {
    MaterialTheme(colorScheme = DarkColors, typography = NebulaTypography) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
            val catalogStates = remember { HashMap<String, CatalogUiState>() }
            val homeState = remember { HomeUiState() }
            val searchState = remember { SearchUiState() }
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            fun push(s: Screen) { stack = stack + s }
            fun pop() {
                if (stack.size > 1) {
                    // a viewer backing out of playback leaves the party (the host keeps it alive)
                    if (stack.last() is Screen.Play && partyUi.active() && !partyUi.isHost) {
                        partyUi.reset(); partyUi.status = "Left the party"
                    }
                    stack = stack.dropLast(1)
                }
            }
            fun setTab(s: Screen) { stack = listOf(s) }

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
                        ev.stream?.let { st -> stack = listOf(Screen.Home, Screen.Play(st.url, st.title, st.subs, st.type, st.id, st.name ?: st.title, st.poster, st.addonUrl)) }
                    }
                    is PartyEvent.State -> partyUi.lastState = ev.state
                    is PartyEvent.StreamSwitch -> {
                        if (!partyUi.isHost) {
                            partyUi.lastState = null
                            partyUi.status = "Host switched streams"
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
                    android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show()
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
                    if ("addons" in keys) { manifestCache.clear(); homeState.invalidate(); addonsVersion++ }
                    if ("progress" in keys) homeState.invalidateContinue()
                    if ("library" in keys) libraryVersion++
                }
                Cloud.onSignedOut = {
                    android.widget.Toast.makeText(ctx, "This device was signed out of your profile. Nothing on it was deleted.", android.widget.Toast.LENGTH_LONG).show()
                }
                Account.boot(ctx)            // pre-profile installs trade the master secret for a device token
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
                    stack = listOf(Screen.Home, Screen.Play(playReq.mpd, playReq.title))
                    onConsumed()
                }
            }

            // Every title opens on its own page first — art, facts, actions.
            fun openMeta(a: Addon, item: MetaItem) {
                push(Screen.Detail(a, item))
            }

            /** Resume something from the Continue watching row (or start it over). */
            fun openProgress(r: ProgressRec, fresh: Boolean = false) {
                val addons = loadAddons(ctx)
                val a = addons.firstOrNull { it.manifestUrl == r.addonUrl } ?: addons.firstOrNull() ?: return
                seriesChain.clear()
                push(Screen.Streams(a, MetaItem(r.id, r.type, r.name, r.poster, r.shape), startOver = fresh))
                if (r.type == "series") scope.launch { hydrateSeriesChain(ctx, a, r.type, r.id) }
            }

            /** The title page behind a Continue watching card — the series, not the
                episode, since the card's id is whichever episode was last played. */
            fun openProgressDetails(r: ProgressRec) {
                val addons = loadAddons(ctx)
                val a = addons.firstOrNull { it.manifestUrl == r.addonUrl } ?: addons.firstOrNull() ?: return
                val id = if (r.type == "series") r.id.substringBefore(':') else r.id
                push(Screen.Detail(a, MetaItem(id, r.type, r.name.split(" · ").first(), r.poster, r.shape)))
            }

            /** Play a specific episode of the current chain from the stream chosen
                ahead of time (or chosen now, the same way), replacing the player in
                place so Back doesn't have to walk back through every episode. */
            fun playEpisode(ep: Episode) {
                val origin = seriesChain.addon ?: return
                val label = seriesChain.label(ep)
                seriesChain.index = seriesChain.episodes.indexOfFirst { it.id == ep.id }
                val poster = (stack.lastOrNull() as? Screen.Play)?.poster
                scope.launch {
                    val res = NextEp.take(ep.id) ?: NextEp.source(ctx, origin)?.let { a ->
                        NextEp.resolve(ctx, a, origin, seriesChain.type, ep.id)
                    }
                    val pick = res?.pick
                    val rest = stack.dropLast(1)
                    stack = rest + if (res != null && pick != null) {
                        // an untouched series adopts the first auto-pick as its taste; a hand-pick is never overwritten
                        NextEp.notePick(ctx, pick, res.addon, byHand = false)
                        Screen.Play(pick.url, pick.name, pick.subtitles, seriesChain.type, ep.id, label, poster, res.addon.manifestUrl)
                    } else {
                        // nothing auto-playable — fall back to the picker
                        Screen.Streams(origin, MetaItem(ep.id, seriesChain.type, label, poster))
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
            }
            // anything but Home is its own destination and must not wait on catalogues
            LaunchedEffect(stack.last()) { if (stack.last() !is Screen.Home) booting = false }

            Box(Modifier.fillMaxSize()) {
                val current = stack.last()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        when (val s = current) {
                            is Screen.Home -> HomeScreen(
                                homeState,
                                onOpen = { a, item -> openMeta(a, item) },
                                onSeeAll = { a, c -> push(Screen.Catalog(a, c)) },
                                onGoAddons = { push(Screen.Addons) },
                                onResume = { r -> openProgress(r) },
                                onStartOver = { r -> openProgress(r, fresh = true) },
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
                                onAddonsChanged = { manifestCache.clear(); homeState.invalidate() },
                            )
                            is Screen.Settings -> SettingsScreen(
                                onAddons = { push(Screen.Addons) },
                                onLayout = { push(Screen.SettingsLayout) },
                                onPlayback = { push(Screen.SettingsPlayback) },
                                onProfile = { push(Screen.Profile) },
                                onParty = { push(Screen.SettingsParty) },
                                onFriends = { push(Screen.Friends) },
                            )
                            is Screen.SettingsLayout -> SettingsLayoutScreen(onBack = { pop() }, onRows = { push(Screen.SettingsHome) })
                            is Screen.SettingsHome -> SettingsHomeRowsScreen(onBack = { pop() })
                            is Screen.Friends -> FriendsScreen(
                                onBack = { pop() },
                                onProfile = { push(Screen.Profile) },
                                onOpen = { m ->
                                    val a = loadAddons(ctx).firstOrNull() ?: return@FriendsScreen
                                    openMeta(a, m)
                                },
                            )
                            is Screen.SettingsPlayback -> SettingsPlaybackScreen(
                                onBack = { pop() },
                                onSubtitles = { push(Screen.SettingsSubtitles) },
                            )
                            is Screen.SettingsSubtitles -> SettingsSubtitlesScreen(onBack = { pop() })
                            is Screen.Profile -> ProfileScreen(onBack = { pop() })
                            is Screen.SettingsParty -> SettingsPartyScreen(onBack = { pop() }, onJoin = { partyJoin(it) })
                            is Screen.Library -> LibraryScreen(
                                version = libraryVersion,
                                onOpen = { li ->
                                    val addons = loadAddons(ctx)
                                    val a = addons.firstOrNull { it.manifestUrl == li.addonUrl } ?: addons.firstOrNull()
                                    if (a == null) partyUi.status = "Add an add-on first"
                                    else push(Screen.Detail(a, MetaItem(li.id, li.type, li.name, li.poster, li.shape)))
                                },
                                onPlayEpisode = { li, ep ->
                                    val addons = loadAddons(ctx)
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
                                    push(Screen.Streams(s.addon, s.item))
                                },
                                onResumeEpisode = { r -> openProgress(r) },
                                onPlayEpisode = { ep ->
                                    seriesChain.index = seriesChain.episodes.indexOfFirst { it.id == ep.id }
                                    val label = seriesChain.label(ep)
                                    push(Screen.Streams(s.addon, MetaItem(
                                        ep.id, "series", label, s.item.poster,
                                        // the streams header is a landscape banner — hand it the
                                        // backdrop, not a portrait poster to crop
                                        background = s.item.background ?: ep.thumbnail,
                                    )))
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
                                onPlayEpisode = { ep ->
                                    seriesChain.index = seriesChain.episodes.indexOfFirst { it.id == ep.id }
                                    val label = seriesChain.label(ep)
                                    push(Screen.Streams(s.addon, MetaItem(
                                        ep.id, "series", label, s.item.poster,
                                        // the streams header is a landscape banner — hand it the
                                        // backdrop, not a portrait poster to crop
                                        background = s.item.background ?: ep.thumbnail,
                                    )))
                                },
                                // no episode data anywhere → replace this screen with the flat stream list
                                onFallback = { stack = stack.dropLast(1) + Screen.Streams(s.addon, s.item) },
                            )
                            is Screen.Streams -> StreamsScreen(
                                s.addon, s.item,
                                onBack = { pop() },
                                fresh = s.startOver,
                                onPlay = { st, from, byHand, fresh ->
                                    // remembered so the next episode keeps this source and quality
                                    NextEp.notePick(ctx, st, from, byHand)
                                    push(
                                        Screen.Play(
                                            st.url, st.name, st.subtitles,
                                            s.item.type, s.item.id, s.item.name,
                                            s.item.poster, s.addon.manifestUrl,
                                            description = s.item.description,
                                            startOver = fresh,
                                        )
                                    )
                                },
                            )
                            is Screen.Play -> PlayerScreen(
                                s.url, s.title, s.subs,
                                contentType = s.type, contentId = s.id, contentName = s.contentName,
                                poster = s.poster, addonUrl = s.addonUrl,
                                description = s.description,
                                startOver = s.startOver,
                                currentEpisode = seriesChain.episodes.getOrNull(seriesChain.index),
                                nextEpisode = seriesChain.next(),
                                onPlayNext = { ep -> playEpisode(ep) },
                                onPrefetchNext = { prefetchNext() },
                                onProgressSaved = { homeState.invalidateContinue() },
                                onPartyStart = { partyStart(it) },
                                onPartyLeave = { partyLeave() },
                            )
                        }
                    }
                    if (current == Screen.Home || current == Screen.Search ||
                        current == Screen.Library || current == Screen.Settings) {
                        BottomBar(current, onTab = { setTab(it) }, modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
                // outside the screen Box so it covers the nav bar too
                AnimatedVisibility(visible = booting, enter = fadeIn(tween(0)), exit = fadeOut(tween(320))) {
                    BootScreen()
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
private fun FocusCard(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val haptics = LocalHapticFeedback.current
    val zoom by animateFloatAsState(if (focused) 1.09f else 1f, tween(300), label = "zoom")
    val lift by animateFloatAsState(if (focused) 22f else 0f, tween(300), label = "lift")
    Box(
        modifier
            .scale(zoom)
            .shadow(lift.dp, shape, clip = false)
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClickLabel = "More options",
                // the buzz is the whole affordance here — nothing on the card
                // itself advertises that a hold does anything
                onLongClick = onLongClick?.let {
                    { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() }
                },
                onClick = onClick,
            )
    ) { content() }
}

/** One row inside a [CardSheet]. */
private data class SheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The long-press sheet that stands in for per-card buttons. A delete cross
 * parked on the artwork reads as a defect rather than a feature — and it is the
 * one control you never want the easiest to hit — so every card action lives in
 * here instead, reached by holding the card.
 */
@Composable
private fun CardSheet(
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
                    Modifier.fillMaxSize().background(Color(0xB8000000)).clickable(
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
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFF141418))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                ) {
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
                    actions.forEach { a ->
                        val tint = if (a.destructive) Color(0xFFFF5A5F) else TextC
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { close(); a.onClick() }
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
        FocusCard(shape = RoundedCornerShape(50), onClick = onBack) {
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
internal fun Chip(text: String, on: Boolean, inSeg: Boolean = false, onClick: () -> Unit) {
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
        Modifier
            .clip(pill)
            .background(if (on) Color.White else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, line, pill)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = if (inSeg) 14.dp else 15.dp, vertical = if (inSeg) 7.dp else 8.dp)
    ) {
        Text(text, color = if (on) Bg else MutedC, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** A row of chips inside one hairline pill — the settings segmented control. */
@Composable
internal fun Segmented(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.border(1.dp, LineC, RoundedCornerShape(50)).padding(3.dp)
            .horizontalScroll(rememberScrollState()),
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
private fun MetaCard(
    m: MetaItem,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    FocusCard(shape = RoundedCornerShape(12.dp), modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        Column(Modifier.padding(2.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(thumbRatio(m.posterShape))
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceC)
                    .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (m.poster != null) {
                    AsyncImage(
                        model = m.poster, contentDescription = m.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        m.name.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "••" },
                        color = Color(0xFF3A3A45), fontSize = 22.sp, fontWeight = FontWeight.Black,
                    )
                }
                m.imdbRating?.let {
                    Text(
                        "★ $it", color = Color.White, fontFamily = Mono, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .background(Color(0x9E000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                m.name, color = TextC, fontSize = 13.sp, fontFamily = Sans, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
            )
            m.releaseInfo?.let {
                Text(it, style = labelStyle(11, FaintC), maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp))
            }
        }
    }
}

/** A Continue watching card: poster, how much is left, and a resume bar. */
@Composable
private fun ContinueCard(r: ProgressRec, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    // episode identity reads off the artwork itself — a wrapped two-line title
    // under a poster was the single biggest source of visual noise on Home
    val parts = r.name.split(" · ")
    val tag = parts.getOrNull(1)?.takeIf { Regex("""^S\d+E\d+${'$'}""", RegexOption.IGNORE_CASE).matches(it.trim()) }
        ?.let { it.trim().replace(Regex("""(?i)^s(\d+)e(\d+)${'$'}"""), "S$1 E$2") }
    val title = if (tag != null) parts[0] else r.name
    val sub = if (tag != null) parts.drop(2).joinToString(" · ").ifEmpty { null } else null
    val left = r.dur - r.pos
    FocusCard(shape = RoundedCornerShape(14.dp), modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceC)
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp)),
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
                0f to Color(0x00000000), 0.45f to Color(0x40000000), 1f to Color(0xE6000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 11.dp, bottom = 10.dp)) {
                val kicker = listOfNotNull(tag, left.takeIf { it > 0 }?.let { fmtTime(it) + " left" })
                    .joinToString("  ·  ")
                if (kicker.isNotEmpty()) Text(
                    kicker, color = Color(0xE0EBEBF5), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp, maxLines = 1,
                )
                Text(
                    title, color = Color.White, fontSize = 15.sp, fontFamily = Sans,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
                if (sub != null) Text(
                    sub, color = Color(0xB3EBEBF5), fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val pct = if (r.dur > 0) (r.pos.toFloat() / r.dur).coerceIn(0f, 1f) else 0f
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxWidth(pct).fillMaxSize().background(Red))
            }
        }
    }
}

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
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
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
private fun RecommendSheet(type: String, item: MetaItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(Unit) {
        val fr = Social.friends(ctx)
        friends = (0 until (fr?.length() ?: 0)).mapNotNull { i ->
            val f = fr!!.optJSONObject(i) ?: return@mapNotNull null
            if (Social.friendKey(f).isEmpty()) null else f
        }
    }
    val list = friends ?: return
    CardSheet(
        title = item.name, sub = "Recommend to…", poster = item.poster, shape = item.posterShape,
        actions = if (list.isEmpty()) listOf(
            SheetAction(Icons.Filled.Groups, "No friends yet — add one in Friends") {},
        ) else list.take(8).map { f ->
            val fName = Social.friendLabel(f)
            SheetAction(Icons.Filled.Favorite, fName) {
                scope.launch {
                    val ok = Social.recommend(ctx, f, type, item)
                    android.widget.Toast.makeText(
                        ctx,
                        if (ok) "Recommended to $fName" else "Could not send that",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        onDismiss = onDismiss,
    )
}

/** Profile monogram: one of the eight Nebula colours behind the first letter of the name. */
internal fun avatarColor(hex: String): Color =
    if (Regex("^#[0-9A-Fa-f]{6}$").matches(hex)) Color(android.graphics.Color.parseColor(hex)) else Color(0xFF636366)

@Composable
internal fun Avatar(hex: String, name: String, size: Dp, dim: Boolean = false) {
    val bg = if (dim) Surface2 else avatarColor(hex)
    val ink = if (dim) MutedC else if (hex.equals("#F2F2F7", ignoreCase = true)) Bg else Color.White
    Box(Modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().removePrefix("@").ifEmpty { "?" }.take(1).uppercase(), color = ink,
            fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, fontFamily = Sans,
        )
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
    var openCode by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload, Social.on) {
        if (!Social.on) return@LaunchedEffect
        Social.publishSoon(ctx)
        friends = null
        val fr = Social.friends(ctx)
        friends = (0 until (fr?.length() ?: 0)).mapNotNull { fr!!.optJSONObject(it) }
        val ib = Social.inbox(ctx)
        inbox = (0 until (ib?.length() ?: 0)).mapNotNull { ib!!.optJSONObject(it) }.reversed()
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
                            "My List — with friends you add by @handle, and no one else.",
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
            Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = codeIn, onValueChange = { codeIn = it.take(24) },
                    placeholder = { Text("Friend’s @handle", color = MutedC) },
                    singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
                Button(
                    onClick = {
                        scope.launch {
                            val (name, err) = Social.addFriend(ctx, codeIn)
                            status = err ?: "You and $name are now friends."
                            if (err == null) { codeIn = ""; reload++ }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(start = 10.dp),
                ) { Text("Add", fontWeight = FontWeight.SemiBold) }
            }
        }
        if (inbox.isNotEmpty()) {
            item(key = "inboxhead") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Recommended to you", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Clear", color = MutedC, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { scope.launch { Social.inboxClear(ctx); reload++ } }
                            .padding(6.dp))
                }
            }
            items(inbox.take(10), key = { it.optLong("at").toString() + it.optString("c") }) { rec ->
                val it2 = rec.optJSONObject("i") ?: return@items
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
            else -> if (fl.isEmpty()) {
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
                                .background(SurfaceC, RoundedCornerShape(14.dp))
                                .border(1.dp, if (openCode == fCode) Line2 else LineC, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { openCode = if (openCode == fCode) null else fCode }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(f.optString("avatar"), f.optString("name").ifEmpty { f.optString("handle") }, 40.dp)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(Social.friendLabel(f), color = TextC, fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val handle = f.optString("handle")
                                Text(
                                    (if (handle.isNotEmpty()) "@$handle · " else "") + "${prof.optJSONArray("ratings")?.length() ?: 0} rated",
                                    color = MutedC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(if (openCode == fCode) "Hide" else "View", color = MutedC, fontSize = 13.sp)
                        }
                        if (openCode == fCode) {
                            FriendRow("Watched recently", profItems(prof.optJSONArray("recent")).map { r ->
                                if (r.optString("type") == "series")
                                    JSONObject(r.toString()).put("id", r.optString("id").substringBefore(':'))
                                else r
                            }, onOpen)
                            FriendRow("Rated", profItems(prof.optJSONArray("ratings")), onOpen)
                            FriendRow("Their list", profItems(prof.optJSONArray("list")), onOpen)
                        }
                    }
                }
                item(key = "froff") {
                    Text("Turn off Friends", color = MutedC, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { scope.launch { Social.disable(ctx); reload++ } }
                            .padding(6.dp))
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.take(15), key = { it.optString("type") + it.optString("id") }) { r ->
            Column(Modifier.width(96.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f)
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
    Box(modifier.clip(shape).background(Surface2.copy(alpha = shimmerAlpha())))
}

/** Placeholder shaped like a stream or episode row: leading block, two text lines. */
@Composable
private fun SkeletonRow(leadingWidth: Dp, leadingHeight: Dp, circle: Boolean) {
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
private fun SkeletonCell(modifier: Modifier = Modifier) {
    Column(modifier) {
        SkelBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(12.dp))
        SkelBox(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(12.dp))
    }
}

/** Row header on Home/Search/Library: the title, a mono eyebrow beside it for
    where the row comes from (or how big it is), and a See-all chip. */
@Composable
private fun RowHeader(title: String, sub: String?, seeAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextC, fontSize = 20.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (sub != null) Eyebrow(sub, Modifier.padding(start = 12.dp).weight(1f))
        else Spacer(Modifier.weight(1f))
        if (seeAll != null) Chip("See all ›", false, onClick = seeAll)
    }
}

/** Floating pill tab bar: Home / Search / Library / Settings. */
@Composable
private fun BottomBar(current: Screen, onTab: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xF0141419))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(34.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TabItem("Home", Icons.Filled.Home, current == Screen.Home) { onTab(Screen.Home) }
        TabItem("Search", Icons.Filled.Search, current == Screen.Search) { onTab(Screen.Search) }
        TabItem("Library", Icons.Filled.Bookmark, current == Screen.Library) { onTab(Screen.Library) }
        TabItem("Settings", Icons.Filled.Settings, current == Screen.Settings) { onTab(Screen.Settings) }
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
            .padding(horizontal = 13.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(32.dp).background(if (on) Surface2 else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp)) }
        Text(label, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun shimmerAlpha(): Float {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "a",
    )
    return a
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
 * "Update available" banner shown on Home. Auto-downloads the APK in the
 * background as soon as it appears (cached per version), then Install is one tap.
 */
@Composable
private fun UpdateCard(version: String, notes: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0) }
    var apk by remember { mutableStateOf<File?>(null) }
    var phase by remember { mutableStateOf("idle") } // idle · downloading · ready · failed
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun download() {
        phase = "downloading"; progress = 0; message = null
        val f = Updates.downloadApk(ctx, version) { progress = it }
        if (f != null) { apk = f; phase = "ready" } else { phase = "failed"; message = "Download failed — tap Retry" }
    }
    // Kick the download off automatically; reuse a completed one if already cached.
    LaunchedEffect(version) {
        val cached = Updates.cachedApk(ctx, version)
        if (cached != null) { apk = cached; phase = "ready" } else download()
    }

    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFFE50914), RoundedCornerShape(12.dp))
            .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Update available · v$version", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                message ?: when (phase) {
                    "downloading" -> "Downloading… $progress%"
                    "ready" -> "Ready — tap Install."
                    else -> notes.ifEmpty { "A new version is available." }
                },
                color = Color(0xFFFFE0E0), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Button(
            onClick = {
                when (phase) {
                    "downloading" -> {}
                    "ready" -> {
                        val f = apk
                        if (f != null && !Updates.installApk(ctx, f)) message = "Allow installs, then tap Install"
                    }
                    else -> scope.launch { download() }
                }
            },
            enabled = phase != "downloading",
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Red),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                when (phase) { "ready" -> "Install"; "downloading" -> "···"; "failed" -> "Retry"; else -> "Update" },
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color(0xFFFFD9D9))
        }
    }
}

/** Featured hero: full-bleed backdrop, the title's own logo art, cycling
    through the top five of the first catalogue. The scrim exists only so the
    type stays legible over the artwork. */
@Composable
private fun HeroHeader(rows: List<CatRow>, onOpen: (Addon, MetaItem) -> Unit) {
    val ctx = LocalContext.current
    val first = rows.firstOrNull() ?: return
    val picks = remember(first) { first.items.filter { it.background != null || it.poster != null }.take(6) }
    if (picks.isEmpty()) return
    var idx by remember(picks) { mutableStateOf(0) }
    LaunchedEffect(picks) {
        while (true) { delay(12_000); idx = (idx + 1) % picks.size }
    }
    val m = picks[idx]
    var inList by remember(m.id) { mutableStateOf(Library.inList(ctx, m.type, m.id)) }
    // the board owns the top of the screen edge to edge and dissolves into it
    val heroH = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    Box(Modifier.fillMaxWidth().height(heroH).clickable { onOpen(first.addon, m) }) {
        AsyncImage(
            model = m.background ?: m.poster, contentDescription = m.name,
            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0x8A000000), 0.22f to Color(0x1A000000),
                    0.58f to Color(0x8A000000), 0.82f to Color(0xE0000000), 1f to Color(0xFF000000),
                )
            )
        )
        Row(
            Modifier.align(Alignment.TopStart).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◆ ", color = Red, fontSize = 18.sp)
            Text("Nebula", color = TextC, fontSize = 22.sp, fontFamily = Sans,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        }
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
            val facts = listOfNotNull(
                if (m.type == "series") "Series" else "Movie",
                m.releaseInfo,
                m.imdbRating?.let { "\u2605 $it" },
            ).joinToString("  \u2022  ")
            Text(
                facts, color = Color(0xD1EBEBF5), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onOpen(first.addon, m) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                ) { Text("View Details", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = {
                        inList = Library.toggle(ctx, m.type, m, first.addon.manifestUrl)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3DFFFFFF)),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) { Text(if (inList) "\u2713 Saved" else "+ My List", color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
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
    onStartOver: (ProgressRec) -> Unit = {},
    onDetails: (ProgressRec) -> Unit = {},
    onCustomise: () -> Unit = {},
) {
    val ctx = LocalContext.current
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
                SheetAction(Icons.Filled.PlayArrow, "Resume") { onResume(r) },
                SheetAction(Icons.Filled.Replay, "Start over") { onStartOver(r) },
                SheetAction(Icons.Filled.Info, "View details") { onDetails(r) },
                SheetAction(Icons.Filled.Delete, "Remove from Continue watching", destructive = true) {
                    Progress.clear(ctx, r.type, r.id)
                    st.continueRows = Progress.continueList(ctx)
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
        val addons = loadAddons(ctx)
        st.hasAddons = addons.isNotEmpty()
        val sig = addons.joinToString("|") { it.manifestUrl } + "#" + HomeRows.version
        if (st.sig == sig && st.rows.isNotEmpty() && System.currentTimeMillis() - st.builtAt < 300_000) return@LaunchedEffect
        st.sig = sig
        st.loading = true
        st.hidden = 0; st.wanted = 0
        val rows = mutableListOf<CatRow>()
        st.rows = emptyList()
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
                        val items = Stremio.loadCatalog(a.base, c, null).take(15)
                        if (items.isNotEmpty()) {
                            rows.add(CatRow(a, c, items, oi)); rows.sortBy { it.oi }
                            st.rows = rows.toList()
                        }
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
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
                ) { Text("Add an add-on", fontWeight = FontWeight.SemiBold) }
            }
            st.rows.isEmpty() && st.loading && st.continueRows.isEmpty() -> {
                val a = shimmerAlpha()
                Column {
                    repeat(2) {
                        Box(Modifier.padding(top = 18.dp, bottom = 10.dp).width(180.dp).height(16.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Surface2.copy(alpha = a)))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(4) {
                                Box(Modifier.width(210.dp).aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp)).background(Surface2.copy(alpha = a)))
                            }
                        }
                    }
                }
            }
            st.rows.isEmpty() && st.continueRows.isEmpty() -> Box(Modifier.padding(top = 30.dp)) { HomeNoRows(st, onCustomise) }
            else -> LazyColumn(state = st.listState, contentPadding = PaddingValues(bottom = 104.dp)) {
                if (st.rows.isNotEmpty() && Prefs.showHero) item(key = "hero") { HeroHeader(st.rows, onOpen) }
                if (st.continueRows.isNotEmpty() && Prefs.showContinue) item(key = "continue") {
                    Column {
                        Box(Modifier.padding(horizontal = 16.dp)) { RowHeader("Continue watching", null, null) }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(st.continueRows, key = { Progress.key(it.type, it.id) }) { r ->
                                ContinueCard(
                                    r,
                                    Modifier.width(250.dp * Prefs.posterScale),
                                    onClick = { onResume(r) },
                                    onLongClick = { sheetFor = r },
                                )
                            }
                        }
                    }
                }
                items(st.rows, key = { it.addon.manifestUrl + "/" + it.catalog.type + "/" + it.catalog.id }) { r ->
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
                            RowHeader(title, if (multi) r.addon.name else null) {
                                onSeeAll(r.addon, r.catalog)
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(r.items) { m ->
                                MetaCard(m, Modifier.width((if (m.posterShape == "landscape") 210.dp else 124.dp) * Prefs.posterScale)) { onOpen(r.addon, m) }
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
        if (q.isEmpty()) { st.sections = emptyList(); st.searchedFor = null; return@LaunchedEffect }
        if (q == st.searchedFor && st.sections.isNotEmpty()) return@LaunchedEffect
        st.searching = true
        val out = mutableListOf<CatRow>()
        st.sections = emptyList()
        for (a in loadAddons(ctx)) {
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        Text("Search", color = TextC, fontSize = 34.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.padding(bottom = 12.dp))
        OutlinedTextField(
            value = st.query,
            onValueChange = { st.query = it },
            placeholder = { Text("Search across your add-ons", color = MutedC) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MutedC) },
            trailingIcon = {
                if (st.query.isNotEmpty() || st.submitted.isNotEmpty()) {
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        when {
            st.searching && st.sections.isEmpty() -> Column {
                SkelBox(Modifier.padding(top = 18.dp, bottom = 10.dp).width(170.dp).height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { SkeletonCell(Modifier.width(210.dp)) }
                }
            }
            st.submitted.isNotBlank() && st.sections.isEmpty() ->
                Text("No matches for “${st.submitted.trim()}”.", color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            st.submitted.isBlank() -> SearchIdle(
                ctx, remember { loadAddons(ctx) },
                onRecent = { q -> st.query = q; st.submitted = q; RecentSearches.note(ctx, q) },
                onAddon = onAddon,
            )
            else -> LazyColumn(state = st.listState, contentPadding = PaddingValues(bottom = 104.dp)) {
                items(st.sections, key = { it.addon.manifestUrl + "/" + it.catalog.id }) { r ->
                    Column {
                        RowHeader(r.addon.name, "${r.items.size} result" + (if (r.items.size > 1) "s" else ""), null)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(r.items) { m ->
                                MetaCard(m, Modifier.width((if (m.posterShape == "landscape") 210.dp else 124.dp) * Prefs.posterScale)) {
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

    // ---- ranking ----
    // Rows are a fixed height, so a drag is just "how many rows have I passed",
    // which keeps this to arithmetic instead of a layout-info crawl.
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var liftIndex by remember { mutableStateOf(-1) }      // picked up with the D-pad
    var rowSpanPx by remember { mutableStateOf(0f) }
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
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                        onClick = {
                            val u = url.trim()
                            if (!Regex("manifest\\.json").containsMatchIn(u)) {
                                status = "Enter a manifest URL (…/manifest.json)"; statusErr = true; return@Button
                            }
                            status = "Adding…"; statusErr = false
                            scope.launch {
                                runCatching { Stremio.loadManifest(u).addon }.onSuccess { a ->
                                    val list = (addons.filterNot { it.manifestUrl == a.manifestUrl } + a)
                                    saveAddons(ctx, list); addons = list; url = ""; onAddonsChanged()
                                    status = "Added ${a.name}"; statusErr = false
                                }.onFailure { status = "Could not load: ${it.message}"; statusErr = true }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Add add-on", fontWeight = FontWeight.SemiBold) }
                }
                if (status.isNotEmpty()) {
                    Text(status, color = if (statusErr) Color(0xFFFF6B6B) else Color(0xFF7CFC7C), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
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
                FocusCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), onClick = { onOpen(a) }) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (raised) Surface2 else SurfaceC, RoundedCornerShape(12.dp))
                            .border(1.dp, if (raised) Color(0x3DFFFFFF) else LineC, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (a.logo != null) {
                            AsyncImage(
                                model = a.logo, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black),
                            )
                        } else {
                            Box(
                                Modifier.size(48.dp)
                                    .background(Red, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(a.name.take(1).uppercase(), color = OnAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(a.name, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                a.manifestUrl.removePrefix("https://").removePrefix("http://"),
                                color = MutedC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
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
                        IconButton(onClick = {
                            val list = addons.filterNot { it.manifestUrl == a.manifestUrl }
                            saveAddons(ctx, list); addons = list; onAddonsChanged()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MutedC, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                }
            }
        }
    }
}


// ---------- settings ----------
@Composable
private fun SettingsHeader(text: String) {
    Text(
        text, color = MutedC, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
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
private fun SettingsRow(
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
                .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier)
                .background(if (focused) Color(0x14FFFFFF) else Color.Transparent)
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
    onPlayback: () -> Unit,
    onProfile: () -> Unit,
    onParty: () -> Unit,
    onFriends: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var updSub by remember { mutableStateOf("You're on v$version — tap to check now") }
    var checking by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "Settings", color = TextC, fontSize = 34.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp, modifier = Modifier.padding(top = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        ProfileCard(onProfile)
        SettingsHeader("GENERAL")
        SettingsGroup {
            SettingsRow(Icons.Filled.Palette, "Layout", "Theme, Home rows and poster size", true, onLayout)
            SettingsRow(Icons.Filled.PlayArrow, "Playback", "Player, subtitles, languages and auto-play", true, onPlayback)
            SettingsRow(Icons.Filled.Extension, "Add-ons", "Add, rank and manage your add-ons", true, onAddons)
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
                OutlinedTextField(
                    value = pname,
                    onValueChange = { v ->
                        pname = v.take(40)
                        ctx2.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("party_name", pname.trim()).apply()
                    },
                    placeholder = { Text(Cloud.profile?.name?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL.take(24), color = MutedC) },
                    singleLine = true,
                    modifier = Modifier.width(150.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
            }
        }
        SettingsHeader("ABOUT")
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
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.rifflehq.in/privacy.html"))) }
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
private fun SettingsToggle(title: String, sub: String, checked: Boolean, divider: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }
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
private fun SettingsChips(title: String, sub: String?, options: List<Pair<String, String>>, selected: String, divider: Boolean = true, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = MutedC, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Box(Modifier.padding(top = 10.dp)) {
            Segmented {
                options.forEach { o -> Chip(o.second, selected == o.first, inSeg = true) { onPick(o.first) } }
            }
        }
    }
    if (divider) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(LineC))
}

@Composable
private fun SettingsLayoutScreen(onBack: () -> Unit, onRows: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Layout", null, onBack)
        SettingsHeader("THEME")
        SettingsGroup {
            // swatch grid: three per row, tick on the current one
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Prefs.ACCENTS.chunked(3).forEachIndexed { ri, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = if (ri == 0) 0.dp else 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { (key, label, color) ->
                            val on = Prefs.accent == key
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(84.dp).clip(RoundedCornerShape(14.dp))
                                    .clickable { Prefs.setAccent(ctx, key) }.padding(vertical = 6.dp),
                            ) {
                                Box(
                                    Modifier.size(52.dp).clip(CircleShape).background(color)
                                        .border(if (on) 3.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Text("✓", color = if (key == "white") Color.Black else Color.White,
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
            }
        }
        SettingsHeader("HOME")
        SettingsGroup {
            SettingsToggle("Featured carousel", "The full-bleed showcase at the top of Home", Prefs.showHero) { Prefs.setShowHero(ctx, it) }
            SettingsToggle("Continue watching", "Pick up where you left off, right on Home", Prefs.showContinue) { Prefs.setShowContinue(ctx, it) }
            SettingsRow(Icons.Filled.ViewAgenda, "Rows", "Choose which catalogs make Home, and arrange them", true, onRows)
            SettingsChips(
                "Poster size", "How large cards render everywhere",
                listOf("0.85" to "Compact", "1.0" to "Standard", "1.18" to "Large"),
                when (Prefs.posterScale) { 0.85f -> "0.85"; 1.18f -> "1.18"; else -> "1.0" },
                divider = false,
            ) { Prefs.setPosterScale(ctx, it.toFloat()) }
        }
    }
}

@Composable
private fun SettingsPlaybackScreen(onBack: () -> Unit, onSubtitles: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Playback", null, onBack)
        SettingsHeader("PLAYER")
        SettingsGroup {
            SettingsToggle(
                "Touch gestures",
                "Double-tap the edges to skip 10s, swipe to seek",
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
                "Picture quality",
                "Auto adapts to your connection · Start high opens at the best rendition · Data saver caps at 720p",
                listOf("auto" to "Auto", "high" to "Start high", "saver" to "Data saver"),
                Prefs.quality,
                divider = false,
            ) { Prefs.setQuality(ctx, it) }
        }
        SettingsHeader("LANGUAGES")
        SettingsGroup {
            SettingsChips(
                "Audio language", "Preferred track when a stream carries several",
                Prefs.LANGS, Prefs.audioLang,
            ) { Prefs.setAudioLang(ctx, it) }
            SettingsChips(
                "Subtitle language", "Preferred captions when a stream carries several",
                Prefs.LANGS, Prefs.subLang,
                divider = false,
            ) { Prefs.setSubLang(ctx, it) }
        }
        SettingsHeader("SUBTITLES")
        SettingsGroup {
            SettingsRow(Icons.Filled.ClosedCaption, "Subtitle style", "Size, colour, background and font of captions", false, onSubtitles)
        }
        SettingsHeader("STREAMS")
        SettingsGroup {
            SettingsToggle(
                "Auto stream selection",
                "Skip the stream list: play the source closest to your last pick from your highest-ranked add-on",
                Prefs.autoStream,
            ) { Prefs.setAutoStream(ctx, it) }
            SettingsChips(
                "Skip intros and recaps",
                "A pill appears over an episode's recap and opening titles, or Auto jumps past them for you · Timestamps come from a community database, looked up by episode",
                listOf("button" to "Show button", "auto" to "Auto", "off" to "Off"),
                Prefs.skipIntro,
            ) { Prefs.setSkipIntro(ctx, it) }
            SettingsToggle(
                "Auto-play next episode",
                "Count down and roll into the next episode when one ends",
                Prefs.autoPlayNext,
                divider = false,
            ) { Prefs.setAutoPlayNext(ctx, it) }
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
                modifier = Modifier.weight(1f),
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
    onPlayEpisode: (Episode) -> Unit = { },
) {
    val ctx = LocalContext.current
    val ck = item.type + ":" + item.id
    var full by remember(ck) { mutableStateOf(metaFullCache[ck]) }
    var metaTried by remember(ck) { mutableStateOf(metaFullCache[ck] != null) }
    var inList by remember(ck) { mutableStateOf(Library.inList(ctx, item.type, item.id)) }
    var recOpen by remember(ck) { mutableStateOf(false) }
    if (recOpen) RecommendSheet(item.type, item) { recOpen = false }

    LaunchedEffect(ck) {
        if (full != null) return@LaunchedEffect
        val order = listOf(addon) + loadAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
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

    val resume = remember(ck, full) {
        if (item.type == "series") seriesResumeRec(ctx, item.id)
        else Progress.get(ctx, item.type, item.id)?.takeIf {
            !it.done && !it.dismissed && it.pos >= Progress.MIN_POS_MS && it.dur > 0 && it.pos <= it.dur - Progress.END_GAP_MS
        }
    }

    Box(Modifier.fillMaxSize()) {
        // full-bleed backdrop; the scrim exists only so type stays legible
        Box(Modifier.fillMaxWidth().height(430.dp)) {
            val art = full?.background ?: item.background ?: item.poster
            if (art != null) {
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x52000000), 0.32f to Color(0x1F000000),
                        0.7f to Color(0xC7000000), 1f to Color(0xFF000000),
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
                val order = listOf(addon) + loadAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
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
            val flat = found.sortedWith(compareBy({ it.season == 0 }, { it.season }, { it.episode ?: 0 }))
            val all = Progress.all(ctx)
            var newest: ProgressRec? = null
            var newestIdx = -1
            flat.forEachIndexed { i, e ->
                val r = all[Progress.key(item.type, e.id)] ?: return@forEachIndexed
                if (r.dismissed) return@forEachIndexed
                if (!r.done && !(r.pos >= Progress.MIN_POS_MS && r.dur > 0)) return@forEachIndexed
                val cur = newest
                if (cur == null || r.at > cur.at) { newest = r; newestIdx = i }
            }
            val up = if (newestIdx >= 0) flat[if (newest?.done == true) minOf(newestIdx + 1, flat.size - 1) else newestIdx] else null
            upNextId = up?.id
            selectedSeason = up?.season
                ?: found.map { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it })).firstOrNull()
        }
        val bySeason = episodes.groupBy { it.season }
        val seasons = bySeason.keys.sortedWith(compareBy({ it == 0 }, { it }))
        val currentSeason = selectedSeason ?: seasons.firstOrNull()
        val eps = (bySeason[currentSeason] ?: emptyList()).sortedBy { it.episode ?: 0 }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
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
        ).joinToString("   ·   ")
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
        full?.genres?.takeIf { it.isNotEmpty() }?.let { gs ->
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                items(gs.take(6).size) { i ->
                    Text(
                        gs[i], color = TextC, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        full?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
            Eyebrow("Cast", Modifier.padding(top = 16.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cast.take(8).size) { i ->
                    Text(
                        cast[i], color = MutedC, fontSize = 13.sp,
                        modifier = Modifier.border(1.dp, LineC, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Row(
            Modifier.padding(top = 16.dp, bottom = 24.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            val first = episodes.sortedWith(compareBy({ it.season == 0 }, { it.season }, { it.episode ?: 0 })).firstOrNull()
                            if (first != null) onPlayEpisode(first) else onEpisodes()
                        }
                        else -> onPlayMovie()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    when {
                        item.type == "series" && resume != null -> resumeLabel(item.id, resume.id)
                        resume != null -> "Resume"
                        else -> "Play"
                    },
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp),
                )
            }
            // the secondary actions are ghosts: one outline, no fill
            Button(
                onClick = { inList = Library.toggle(ctx, item.type, item, addon.manifestUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0x47FFFFFF)),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (inList) "✓ In My List" else "+ My List", color = TextC, fontWeight = FontWeight.SemiBold) }
            // Surprise me: a random aired episode for a comfort show (web parity)
            val pool = if (item.type == "series") surprisePool(episodes) else emptyList()
            if (pool.size >= 2) Button(
                onClick = { surprisePick(pool)?.let { onPlayEpisode(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0x47FFFFFF)),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Surprise me", color = TextC, fontWeight = FontWeight.SemiBold) }
            if (Social.on) Button(
                onClick = { recOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0x47FFFFFF)),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Recommend", color = TextC, fontWeight = FontWeight.SemiBold) }
        }
        RatingStars(item)
        } }   // header item

        if (item.type == "series") {
            if (seasons.size > 1) item(key = "seasons") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                    items(seasons.size) { i ->
                        val sn = seasons[i]
                        Chip(if (sn == 0) "Specials" else "Season $sn", sn == currentSeason) { selectedSeason = sn }
                    }
                }
            }
            if (epsLoading) items(4) { SkeletonRow(112.dp, 63.dp, circle = false) }
            items(eps.size, key = { eps[it].id }) { i ->
                val ep = eps[i]
                EpisodeRow(itemType = item.type, ep = ep, upNext = ep.id == upNextId, first = i == 0, onClick = { onPlayEpisode(ep) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

/** One episode in a list: thumb, a mono eyebrow (number · air date), the name,
    the overview beneath. Rows are transparent and divided by hairlines — a
    list, not a stack of tiles — and only the target row wears a mark. */
@Composable
private fun EpisodeRow(itemType: String, ep: Episode, upNext: Boolean, first: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(LineC))
        FocusCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), onClick = onClick) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val pr = Progress.get(ctx, itemType, ep.id)
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
                        ) { Text("✓", color = Color(0xFF46D369), fontSize = 11.sp, fontWeight = FontWeight.Black) }
                    } else if (pr != null && pr.pos > 0 && pr.dur > 0) {
                        Box(Modifier.align(Alignment.BottomStart).width(112.dp).height(4.dp).background(Color(0x8C000000))) {
                            Box(Modifier.fillMaxWidth((pr.pos.toFloat() / pr.dur).coerceIn(0f, 1f)).fillMaxSize().background(Red))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    // "Episode 3 · 23 Jun 2022"; the number is dropped when the
                    // name is only "Episode 3" already
                    val date = ep.released?.let {
                        runCatching {
                            java.time.LocalDate.parse(it.take(10))
                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
                        }.getOrNull()
                    }
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

// ---------- library (My List + upcoming episodes) ----------
@Composable
private fun LibraryScreen(
    version: Int,
    onOpen: (LibItem) -> Unit,
    onPlayEpisode: (LibItem, Episode) -> Unit,
) {
    val ctx = LocalContext.current
    var items by remember(version) { mutableStateOf(Library.list(ctx)) }
    var upcoming by remember(version) { mutableStateOf<List<Library.UpRow>?>(null) }
    var sheetFor by remember { mutableStateOf<LibItem?>(null) }

    sheetFor?.let { li ->
        CardSheet(
            title = li.name,
            sub = if (li.type == "series") "Series" else "Movie",
            poster = li.poster,
            shape = li.shape,
            actions = listOf(
                SheetAction(Icons.Filled.Info, "View details") { onOpen(li) },
                SheetAction(Icons.Filled.BookmarkRemove, "Remove from My List", destructive = true) {
                    Library.toggle(ctx, li.type, MetaItem(li.id, li.type, li.name, li.poster, li.shape), li.addonUrl)
                    items = Library.list(ctx)
                },
            ),
            onDismiss = { sheetFor = null },
        )
    }

    LaunchedEffect(version, items.size) {
        upcoming = if (items.none { it.type == "series" }) emptyList()
        else runCatching { Library.upcoming(ctx) }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        Text("Library", color = TextC, fontSize = 34.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp, modifier = Modifier.padding(bottom = 12.dp))
        if (items.isEmpty()) {
            Text(
                "Nothing saved yet — open any title and press + My List. New episodes of saved series appear here too.",
                color = MutedC, fontSize = 14.sp, lineHeight = 21.sp,
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 104.dp)) {
            item(key = "grid") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.type + ":" + it.id }) { li ->
                        MetaCard(
                            MetaItem(li.id, li.type, li.name, li.poster, li.shape),
                            Modifier.width(if (li.shape == "landscape") 210.dp else 124.dp),
                            onLongClick = { sheetFor = li },
                        ) { onOpen(li) }
                    }
                }
            }
            when (val up = upcoming) {
                null -> item(key = "upsk") {
                    Column {
                        RowHeader("Upcoming", null, null)
                        SkeletonRow(44.dp, 66.dp, circle = false)
                    }
                }
                else -> if (up.isNotEmpty()) {
                    val shows = up.map { it.series.id }.distinct().size
                    item(key = "uphead") { RowHeader("Upcoming", "$shows series", null) }
                    var lastDay = ""
                    var firstOfDay = false
                    up.forEach { row ->
                        val day = Library.dayLabel(row.time)
                        if (day != lastDay) {
                            lastDay = day
                            firstOfDay = true
                            item(key = "day/" + row.time) {
                                Eyebrow(day, Modifier.padding(top = 18.dp, bottom = 4.dp))
                            }
                        }
                        val first = firstOfDay
                        firstOfDay = false
                        item(key = "up/" + row.series.id + "/" + row.ep.id) {
                            // a list, like the episodes: hairlines between rows, no tiles
                            Column {
                                if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(LineC))
                            FocusCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                                onClick = { onPlayEpisode(row.series, row.ep) }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    val thumbMod = Modifier.width(44.dp).height(66.dp).clip(RoundedCornerShape(8.dp)).background(Surface2)
                                    if (row.series.poster != null) {
                                        AsyncImage(model = row.series.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = thumbMod)
                                    } else {
                                        Box(thumbMod, contentAlignment = Alignment.Center) {
                                            Text(row.series.name.take(1).uppercase(), color = FaintC, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(row.series.name, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val tag = "S${row.ep.season}" + (row.ep.episode?.let { "E$it" } ?: "")
                                        val notOut = row.time > System.currentTimeMillis()
                                        Text(
                                            tag + (if (row.ep.name.isNotEmpty()) "  ${row.ep.name}" else "") +
                                                (if (notOut) " — not out yet" else ""),
                                            color = MutedC, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            fontFamily = Mono, modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                } else item(key = "upempty") {
                    Column {
                        RowHeader("Upcoming", null, null)
                        Text("No dated episodes coming up for your saved series.", color = MutedC, fontSize = 13.sp)
                    }
                }
            }
        }
    }
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
            .onFailure { status = "Failed: ${it.message}"; loading = false }
    }
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
                .onFailure { status = "Failed: ${it.message}"; loading = false; st.loadedFor = null }
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
                .onFailure { status = "Failed: ${it.message}"; loading = false; st.loadedFor = null; st.pageDone = true }
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
    LaunchedEffect(reachedEnd, st.pageDone, st.paging, submitted, current, genre) {
        if (!reachedEnd || st.pageDone || st.paging || loading) return@LaunchedEffect
        if (submitted.trim().isNotEmpty()) return@LaunchedEffect      // search results aren't paged
        val c = current ?: return@LaunchedEffect
        if (items.size >= 1000) { st.pageDone = true; return@LaunchedEffect }   // ceiling for TV memory
        st.paging = true
        runCatching { Stremio.loadCatalog(addon.base, c, genre, null, st.fetched) }
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
        st.paging = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        BackBar(addon.name, status, onBack)
        if (catalogs.any { it.search }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ${addon.name}", color = MutedC) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MutedC) },
                trailingIcon = {
                    if (query.isNotEmpty() || submitted.isNotEmpty()) {
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }
        if (catalogs.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                items(catalogs) { c -> Chip(c.name, c == current && submitted.isEmpty()) { current = c; genre = null; query = ""; submitted = "" } }
            }
        }
        if (submitted.isEmpty()) current?.genres?.take(20)?.let { gs ->
            if (gs.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                items(gs) { g -> Chip(g, genre == g) { genre = if (genre == g) null else g } }
            }
        }
        if (loading && items.isEmpty()) {
            val a = shimmerAlpha()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp * Prefs.posterScale),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                items(12) {
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface2.copy(alpha = a)))
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(12.dp)).background(Surface2.copy(alpha = a)))
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp * Prefs.posterScale),
                state = st.gridState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                items(items) { m -> MetaCard(m) { onOpen(m) } }
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
    onPlayEpisode: (Episode) -> Unit,
    onFallback: () -> Unit,
) {
    val ctx = LocalContext.current
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var status by remember { mutableStateOf("Loading episodes…") }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var upNextId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        val order = listOf(addon) + loadAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
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
        val flat = found.sortedWith(compareBy({ it.season == 0 }, { it.season }, { it.episode ?: 0 }))
        val all = Progress.all(ctx)
        var newest: ProgressRec? = null
        var newestIdx = -1
        flat.forEachIndexed { i, e ->
            val r = all[Progress.key(item.type, e.id)] ?: return@forEachIndexed
            if (r.dismissed) return@forEachIndexed
            if (!r.done && !(r.pos >= Progress.MIN_POS_MS && r.dur > 0)) return@forEachIndexed
            val cur = newest
            if (cur == null || r.at > cur.at) { newest = r; newestIdx = i }
        }
        val up = if (newestIdx >= 0) flat[if (newest?.done == true) minOf(newestIdx + 1, flat.size - 1) else newestIdx] else null
        upNextId = up?.id
        selectedSeason = up?.season
            ?: found.map { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it })).firstOrNull()
    }

    val bySeason = episodes.groupBy { it.season }
    val seasons = bySeason.keys.sortedWith(compareBy({ it == 0 }, { it }))
    val current = selectedSeason ?: seasons.firstOrNull()
    val eps = (bySeason[current] ?: emptyList()).sortedBy { it.episode ?: 0 }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        BackBar(item.name, status, onBack)
        if (seasons.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(seasons, key = { it }) { s ->
                    Chip(if (s == 0) "Specials" else "Season $s", s == current) { selectedSeason = s }
                }
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(current, upNextId, eps.size) {
            val i = eps.indexOfFirst { it.id == upNextId }
            if (i >= 0) listState.scrollToItem(maxOf(0, i - 1))
        }
        LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 20.dp)) {
            if (episodes.isEmpty()) items(6) { SkeletonRow(112.dp, 63.dp, circle = false) }
            items(eps.size, key = { eps[it].id }) { i ->
                val ep = eps[i]
                EpisodeRow(itemType = item.type, ep = ep, upNext = ep.id == upNextId, first = i == 0, onClick = { onPlayEpisode(ep) })
            }
        }
    }
}

// Stremio semantics: catalog add-ons and stream add-ons are separate. Ask the
// add-on the item came from PLUS every other installed add-on whose manifest
// serves streams for this type/id, and show the answers grouped per add-on.
@Composable
private fun StreamsScreen(addon: Addon, item: MetaItem, onBack: () -> Unit, fresh: Boolean = false, onPlay: (StreamItem, Addon, Boolean, Boolean) -> Unit) {
    var sections by remember { mutableStateOf<List<Pair<Addon, List<StreamItem>>>>(emptyList()) }
    var status by remember { mutableStateOf("Loading streams…") }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var usualUrl by remember { mutableStateOf<String?>(null) }   // the row that matches the last pick
    val ctx = LocalContext.current
    // Start over: a one-play choice for anything already begun (web parity)
    val savedAt = remember(item.id) { Progress.resumeAt(ctx, item.type, item.id) }
    var startOver by remember(item.id) { mutableStateOf(fresh && savedAt > 0) }
    LaunchedEffect(item, reload) {
        loading = true
        usualUrl = null
        val order = listOf(addon) + loadAddons(ctx).filterNot { it.manifestUrl == addon.manifestUrl }
        val out = mutableListOf<Pair<Addon, List<StreamItem>>>()
        var failures = 0
        val pf = NextEp.picked(ctx)
        for (a in order) {
            runCatching {
                // origin is always asked; others only if their manifest matches
                if (a.manifestUrl != addon.manifestUrl &&
                    !manifestFor(a.manifestUrl).canStream(item.type, item.id)) return@runCatching
                val streams = Stremio.loadStreams(a.base, item.type, item.id)
                if (streams.isNotEmpty()) {
                    // the row that matches what was picked last time heads its
                    // section — buried at row 30 of 40 it would help nobody
                    var list = streams
                    if (pf != null && pf.addonUrl == a.manifestUrl) {
                        val twin = StreamTwin.match(streams, pf, a)?.takeIf { StreamTwin.isTwin(it, pf, a) }
                        if (twin != null) { usualUrl = twin.url; list = listOf(twin) + streams.filter { it !== twin } }
                    }
                    out.add(a to list)
                    sections = out.toList()
                    val n = out.sumOf { it.second.size }
                    status = "$n stream${if (n > 1) "s" else ""}" + (if (out.size > 1) " from ${out.size} add-ons" else "")
                    // Auto selection: the row closest to the last pick from the
                    // highest-priority add-on that answered (its first row when
                    // nothing was ever picked). Guarded by id, not screen state —
                    // this screen's state dies while the player is up, and
                    // re-firing on the way back would trap the user in playback forever.
                    if (Prefs.autoStream && autoPlayedFor != item.id) {
                        autoPlayedFor = item.id
                        onPlay(StreamTwin.match(list, pf, a) ?: list.first(), a, false, startOver)
                        return@LaunchedEffect
                    }
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; failures++ }
        }
        if (sections.isEmpty()) {
            status = if (failures == order.size) "Failed to load streams." else "No playable streams right now."
        }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item(key = "hero") {
                // episode context header — art, S/E kicker, episode title, series name
                val art = item.background ?: item.poster
                val segs = item.id.split(":")
                val kick = if (item.type == "series" && segs.size >= 3)
                    "SEASON ${segs[segs.size - 2]} · EPISODE ${segs[segs.size - 1]}" else null
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
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(40.dp)
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
                        if (startOver) "Starting from the beginning" else "Resuming from ${fmtTime(savedAt)}",
                        color = MutedC, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    StreamFilterChip(if (startOver) "Resume from ${fmtTime(savedAt)}" else "Start over", startOver) { startOver = !startOver }
                }
            }
            if (sections.size > 1) item(key = "filters") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item { StreamFilterChip("↻", false) { filter = null; reload++ } }
                    item { StreamFilterChip("All", filter == null) { filter = null } }
                    items(sections.size) { i ->
                        val nm = sections[i].first.name
                        StreamFilterChip(nm, filter == nm) { filter = nm }
                    }
                }
            }
            if (loading && sections.isEmpty()) items(4) { Box(Modifier.padding(horizontal = 16.dp)) { SkeletonRow(42.dp, 42.dp, circle = true) } }
            sections.filter { filter == null || it.first.name == filter }.forEachIndexed { sectionIndex, (from, streams) ->
                if (sections.size > 1) item(key = "head/$sectionIndex") {
                    Text(
                        from.name, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp),
                    )
                }
            items(streams) { s ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    StreamRow(s, from.name, item.name, usual = s.url == usualUrl, onPlay = { onPlay(it, from, true, startOver) })
                }
            }
            }
        }
    }
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
private fun StreamRow(s: StreamItem, addonName: String, pageTitle: String, usual: Boolean = false, onPlay: (StreamItem) -> Unit) {
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
    FocusCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), onClick = { onPlay(s) }) {
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
                // the row that matches what you picked last time — same source, same quality
                if (usual) Text(
                    "SAME AS LAST TIME", color = Red, fontFamily = Mono, fontSize = 9.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp, modifier = Modifier.padding(bottom = 3.dp),
                )
                Text(name, color = TextC, fontSize = 15.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.badges.isNotEmpty()) {
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
            if (f.size != null || f.bitrate != null || f.seeds != null) {
                Column(horizontalAlignment = Alignment.End) {
                    f.size?.let { Text(it, color = TextC, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    val line2 = listOfNotNull(f.bitrate, f.seeds?.let { "$it seeds" }).joinToString(" · ")
                    if (line2.isNotEmpty()) Text(line2, color = MutedC, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
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
private fun SubMenuRow(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .background(if (focused) Color(0x14FFFFFF) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextC, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (active) Text("✓", color = Color(0xFF46D369), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

private fun langLabel(code: String): String = runCatching {
    val c = code.trim().lowercase()
    if (c.isEmpty() || c == "und") "Unknown"
    else java.util.Locale(c.take(3)).getDisplayLanguage(java.util.Locale.ENGLISH)
        .ifEmpty { c.uppercase() }.replaceFirstChar { it.uppercase() }
}.getOrDefault(code.uppercase())

/** Add-on subtitle links carry no extension and are sometimes CP1252 — download,
    sniff the format, re-encode as UTF-8 and hand ExoPlayer a local file. */
private suspend fun cachedSubFile(ctx: Context, st: SubTrack): Pair<Uri, String>? = runCatching {
    val raw = Stremio.httpGetBytes(st.url)
    var text = String(raw, Charsets.UTF_8)
    if (text.isBlank() || text.contains('\uFFFD')) text = String(raw, charset("windows-1252"))
    text = text.removePrefix("\uFEFF")
    if (text.isBlank()) return@runCatching null
    val vtt = text.trimStart().startsWith("WEBVTT")
    val f = File(ctx.cacheDir, "sub-" + Integer.toHexString(st.url.hashCode()) + (if (vtt) ".vtt" else ".srt"))
    f.writeText(text)
    Pair(Uri.fromFile(f), if (vtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
}.getOrNull()

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
    currentEpisode: Episode? = null,
    nextEpisode: Episode? = null,
    onPlayNext: (Episode) -> Unit = {},
    onPrefetchNext: () -> Unit = {},
    onProgressSaved: () -> Unit = {},
    onPartyStart: (PartyStreamDesc) -> Unit = {},
    onPartyLeave: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hostDirty by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var videoQualityCount by remember { mutableStateOf(0) }
    var audioTrackCount by remember { mutableStateOf(0) }
    var textTrackCount by remember { mutableStateOf(0) }
    var subStyleOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var addonSubs by remember { mutableStateOf<List<SubTrack>>(emptyList()) }
    var subsMenuOpen by remember { mutableStateOf(false) }
    var subBusy by remember { mutableStateOf(false) }
    var activeAddonSub by remember { mutableStateOf<String?>(null) }
    // the Title Card chrome replaces Media3's controller entirely
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeTouchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var isPlayingState by remember { mutableStateOf(false) }
    var isLiveState by remember { mutableStateOf(false) }
    var posMs by remember { mutableStateOf(0L) }
    var durMs by remember { mutableStateOf(0L) }
    var bufMs by remember { mutableStateOf(0L) }
    var qualityLabel by remember { mutableStateOf<String?>(null) }
    var speedLabel by remember { mutableStateOf("1.0×") }
    var isFullscreen by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var skipFlash by remember { mutableStateOf<Triple<Int, Int, Long>?>(null) } // zone (-1/+1), total secs, stamp
    var heldSpeed by remember { mutableStateOf<Float?>(null) }                  // speed to restore after hold-to-speed
    var dragSeek by remember { mutableStateOf<Pair<Long, Long>?>(null) }        // target ms, delta ms
    var pauseBoardOn by remember { mutableStateOf(false) }
    var pausedSince by remember { mutableStateOf(0L) }
    var pinfoOn by remember { mutableStateOf(false) }
    var infoRows by remember { mutableStateOf<List<InfoRow>>(emptyList()) }
    var subOffsetMs by remember { mutableStateOf(0L) }
    var subTimingOpen by remember { mutableStateOf(false) }
    var subBaseFile by remember { mutableStateOf<Pair<Uri, String>?>(null) }   // unshifted add-on subtitle, mime
    var subAppliedMs by remember { mutableStateOf(0L) }                          // offset the player currently has
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
    val exo = remember {
        ExoPlayer.Builder(context)
            .setBandwidthMeter(bandwidth)
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
            .build()
            .apply {
                playWhenReady = true
                // "" means follow the device, which is ExoPlayer's own default
                if (Prefs.audioLang.isNotEmpty() || Prefs.subLang.isNotEmpty() || Prefs.quality == "saver") {
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .apply {
                            if (Prefs.audioLang.isNotEmpty()) setPreferredAudioLanguage(Prefs.audioLang)
                            if (Prefs.subLang.isNotEmpty()) setPreferredTextLanguage(Prefs.subLang)
                            if (Prefs.quality == "saver") setMaxVideoSize(Int.MAX_VALUE, 720)   // Data saver
                        }
                        .build()
                }
            }
    }
    // "Show · S1E2 · Episode name" is how the chain labels an episode; take it apart again
    val nameParts = remember(contentName) { (contentName ?: "").split(" · ") }
    val showName = nameParts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: title
    val episodeName = if (contentType == "series" && nameParts.size >= 3) nameParts.drop(2).joinToString(" · ") else null
    val episodeTag = contentId?.takeIf { contentType == "series" }?.split(":")
        ?.takeIf { it.size >= 3 }?.let { "S" + it[it.size - 2] + " · E" + it[it.size - 1] }
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
        seekBy(zone * 10_000L)
        val now = System.currentTimeMillis()
        val prev = skipFlash
        // Rapid re-taps on the same side accumulate (10s, 20s, 30s…) like YouTube.
        val total = if (prev != null && prev.first == zone && now - prev.third < 1200) prev.second + 10 else 10
        skipFlash = Triple(zone, total, now)
    }
    LaunchedEffect(skipFlash) { if (skipFlash != null) { delay(800); skipFlash = null } }

    // ---- resume point ----
    var upnextOpen by remember { mutableStateOf(false) }
    var upnextCounting by remember { mutableStateOf(false) }
    var upnextLeft by remember { mutableStateOf(8) }
    var upnextDismissed by remember { mutableStateOf(false) }
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
        sleepAt = if (mode == "min") System.currentTimeMillis() + minutes * 60_000L else 0L
        sleepFired = false
        if (mode == "ep") { upnextOpen = false; upnextCounting = false }
        sleepRender()
        sleepMenuOpen = false
        chromeTouchedAt = System.currentTimeMillis()
        android.widget.Toast.makeText(
            context,
            when (mode) { "" -> "Sleep timer off"; "ep" -> "Stopping when this episode ends"; else -> "Pausing in ${sleepText(minutes * 60_000L)}" },
            android.widget.Toast.LENGTH_SHORT,
        ).show()
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

    LaunchedEffect(url) {
        // a fresh episode starts with the up-next card closed and undismissed
        upnextOpen = false; upnextCounting = false; upnextDismissed = false
        runCatching {
            val b = MediaItem.Builder().setUri(url)
            when {
                Regex("\\.mpd(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) -> {
                    b.setMimeType(MimeTypes.APPLICATION_MPD)
                    val laurl = Stremio.resolveClearKeyLicenseUri(url)
                    if (laurl != null) {
                        b.setDrmConfiguration(
                            MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).setLicenseUri(laurl).build()
                        )
                    }
                }
                Regex("\\.m3u8", RegexOption.IGNORE_CASE).containsMatchIn(url) -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            if (subs.isNotEmpty()) {
                b.setSubtitleConfigurations(
                    subs.map { st ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(st.url))
                            .setLanguage(st.lang)
                            .setMimeType(
                                if (Regex("\\.srt(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(st.url))
                                    MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT
                            )
                            .build()
                    }
                )
            }
            // Resume where this exact item was left off; handing the offset to
            // ExoPlayer starts buffering there rather than loading at 0 and seeking.
            val saved = if (contentType != null && contentId != null) {
                Progress.resumeAt(context, contentType, contentId)
            } else 0L
            // Start over is a one-play choice: the saved point is skipped, not erased
            val resume = if (startOver) 0L else saved
            if (resume > 0) exo.setMediaItem(b.build(), resume) else exo.setMediaItem(b.build())
            exo.prepare()
            if (resume > 0 || (startOver && saved > 0)) {
                android.widget.Toast.makeText(
                    context,
                    if (resume > 0) "Resumed from ${fmtTime(resume)}" else "Starting from the beginning",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }.onFailure { error = it.message }
    }

    // Ask every subtitle-capable add-on what it has for this title (web parity).
    LaunchedEffect(contentId) {
        if (contentType == null || contentId == null) return@LaunchedEffect
        val found = mutableListOf<SubTrack>()
        for (a in loadAddons(context)) {
            runCatching {
                if (!manifestFor(a.manifestUrl).canSubs(contentType, contentId)) return@runCatching
                found += Stremio.loadSubtitles(a.base, contentType, contentId)
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
        // a couple per language is plenty for a picker
        addonSubs = found.groupBy { it.lang.lowercase() }.toSortedMap()
            .values.flatMap { it.take(2) }.take(24)
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
            if (remain > 500 && (remain <= 25_000 || credits)) upnextOpen = true
        }
    }
    // the card opening (near the end, on ENDED, or after a seek) is the other cue
    LaunchedEffect(upnextOpen) { if (upnextOpen) onPrefetchNext() }
    LaunchedEffect(upnextCounting) {
        if (!upnextCounting) return@LaunchedEffect
        upnextLeft = 8
        while (upnextLeft > 0) { delay(1000); upnextLeft-- }
        nextEpisode?.let { onPlayNext(it) }
    }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = "Playback error ${e.errorCodeName} (${e.errorCode})" }
            override fun onTracksChanged(tracks: Tracks) {
                var v = 0
                var au = 0
                var tx = 0
                for (g in tracks.groups) {
                    when (g.type) {
                        C.TRACK_TYPE_VIDEO -> for (i in 0 until g.length) {
                            if (g.isTrackSupported(i) && g.getTrackFormat(i).height > 0) v++
                        }
                        C.TRACK_TYPE_AUDIO -> if (g.length > 0) au++
                        C.TRACK_TYPE_TEXT -> if (g.length > 0) tx++
                    }
                }
                videoQualityCount = v
                audioTrackCount = au
                textTrackCount = tx
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                (activity as? MainActivity)?.refreshPipParams()
                if (partyUi.active() && partyUi.isHost) hostDirty = true
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                (activity as? MainActivity)?.refreshPipParams()
                if (videoSize.height > 0) qualityLabel = "${videoSize.height}p"
            }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK && partyUi.active() && partyUi.isHost) hostDirty = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED) return
                snapshotProgress(done = true)          // ticks it off the episode list
                if (sleepMode == "ep") {
                    // the night ends here: no up-next, the board says why
                    sleepMode = ""; sleepAt = 0L; sleepFired = true; sleepRender()
                    return
                }
                if (nextEpisode != null && !upnextDismissed) { upnextOpen = true; upnextCounting = Prefs.autoPlayNext }
            }
        }
        exo.addListener(l)
        activePipPlayer.value = exo
        (activity as? MainActivity)?.refreshPipParams()
        onDispose {
            // last word on the resume point before the player goes away
            runCatching { snapshotProgress() }
            runCatching { Social.publishSoon(context) }   // friends see the freshly watched title
            exo.removeListener(l); runCatching { session?.release() }; exo.release()
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
    LaunchedEffect(SubStyle.version.value, chromeVisible) {
        playerViewRef?.subtitleView?.let {
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
            val now = System.currentTimeMillis()
            if (chromeVisible && exo.isPlaying && !subStyleOpen && !subTimingOpen &&
                now - chromeTouchedAt > 3500) chromeVisible = false
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
            // The pause board: a moment after pausing (or at the end), when nothing
            // else is open, and only once something has actually played.
            val resting = !exo.isPlaying && (exo.playbackState == Player.STATE_ENDED ||
                (exo.playbackState == Player.STATE_READY && !exo.playWhenReady))
            if (!resting) pausedSince = 0L else if (pausedSince == 0L) pausedSince = now
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
            pauseBoardOn = resting && !inPipMode.value && !subStyleOpen && !subsMenuOpen && !subTimingOpen && !sleepMenuOpen &&
                !upnextOpen && exo.currentPosition > 1000 && now - pausedSince > 1600 && now - chromeTouchedAt > 1600
            if (pinfoOn) infoRows = playbackInfoRows(exo, bandwidth, subOffsetMs)
        }
    }

    /** Re-feed the active add-on subtitle with every cue moved by subOffsetMs. */
    fun applySubOffset() {
        val base = subBaseFile ?: return
        val cur = exo.currentMediaItem ?: return
        val uri = if (subOffsetMs == 0L) base.first else shiftedSubFile(context, base.first, subOffsetMs) ?: return
        val keep = cur.localConfiguration?.subtitleConfigurations?.filter { it.id != "addon-pick" } ?: emptyList()
        val cfg = MediaItem.SubtitleConfiguration.Builder(uri)
            .setId("addon-pick").setMimeType(base.second)
            .setLanguage(cur.localConfiguration?.subtitleConfigurations?.firstOrNull { it.id == "addon-pick" }?.language)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val pos = exo.currentPosition
        val wasPlaying = exo.playWhenReady
        exo.setMediaItem(cur.buildUpon().setSubtitleConfigurations(keep + cfg).build(), pos)
        exo.prepare()
        exo.playWhenReady = wasPlaying
        subAppliedMs = subOffsetMs
    }
    // several quick nudges become one re-prepare
    LaunchedEffect(subOffsetMs) {
        if (subBaseFile != null && subOffsetMs != subAppliedMs) { delay(600); applySubOffset() }
    }

    // In picture-in-picture only the video shows — no chrome, no gestures.
    val pip = inPipMode.value
    LaunchedEffect(pip) { if (pip) chromeVisible = false }

    // A party host arriving on a new stream takes the whole room along (mirrors the web player).
    LaunchedEffect(Unit) {
        if (partyUi.active() && partyUi.isHost) {
            partyUi.session?.sendStream(PartyStreamDesc(url, title, subs))
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false          // the Title Card chrome is the controller
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    playerViewRef = this
                    subtitleView?.let { SubStyle.apply(ctx, it) }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Touch gestures: double-tap left/right = ±10s, horizontal swipe = seek,
        // plain tap = toggle the chrome. The chrome's own controls sit above
        // this layer, so they stay tappable; remote/D-pad (TV) is unaffected.
        if (!pip) {
            Box(
                Modifier
                    .fillMaxSize()
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
        if (pauseBoardOn && !pip) Box(Modifier.fillMaxSize().background(Color(0x7A000000)))
        if (!pip) TitleCardChrome(
            visible = chromeVisible,
            title = showName,
            isPlaying = isPlayingState,
            isLive = isLiveState,
            positionMs = posMs, durationMs = durMs, bufferedMs = bufMs,
            episodeTag = episodeTag?.let { t -> if (episodeName != null) "$t · $episodeName" else t },
            dimTitle = pauseBoardOn,
            infoOn = pinfoOn,
            onInfo = { pinfoOn = !pinfoOn; chromeTouchedAt = System.currentTimeMillis() },
            showTiming = subBaseFile != null,
            onTiming = { subTimingOpen = !subTimingOpen; subsMenuOpen = false; subStyleOpen = false; sleepMenuOpen = false; chromeTouchedAt = System.currentTimeMillis() },
            sleepLabel = sleepLabel,
            onSleep = { sleepMenuOpen = !sleepMenuOpen; subsMenuOpen = false; subStyleOpen = false; subTimingOpen = false; chromeTouchedAt = System.currentTimeMillis() },
            qualityLabel = qualityLabel?.takeIf { videoQualityCount >= 1 },
            speedLabel = speedLabel,
            partyActive = partyUi.active(),
            partyBadge = partyUi.code?.let { c -> c + " · " + partyUi.count },
            hasNext = nextEpisode != null,
            canPip = (activity as? MainActivity)?.pipSupported() == true,
            showSubtitles = textTrackCount >= 1 || subs.isNotEmpty() || addonSubs.isNotEmpty(),
            showAudio = audioTrackCount >= 2,
            onBack = { (activity as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() },
            onPlayPause = {
                if (exo.isPlaying) exo.pause() else exo.play()
                chromeTouchedAt = System.currentTimeMillis()
            },
            onSeekBy = { d -> seekBy(d); chromeTouchedAt = System.currentTimeMillis() },
            onSeekTo = { t -> exo.seekTo(t.coerceAtLeast(0L)); chromeTouchedAt = System.currentTimeMillis() },
            onNext = { nextEpisode?.let { upnextCounting = false; onPlayNext(it) } },
            onParty = {
                if (partyUi.active()) onPartyLeave()
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
                if (addonSubs.isEmpty()) {
                    runCatching {
                        TrackSelectionDialogBuilder(context, "Subtitles", exo, C.TRACK_TYPE_TEXT)
                            .setShowDisableOption(true).build().show()
                    }
                } else {
                    subsMenuOpen = !subsMenuOpen
                    subTimingOpen = false
                    sleepMenuOpen = false
                }
                chromeTouchedAt = System.currentTimeMillis()
            },
            onSubStyle = { subStyleOpen = !subStyleOpen; subTimingOpen = false; sleepMenuOpen = false; chromeTouchedAt = System.currentTimeMillis() },
            onAudio = {
                runCatching {
                    TrackSelectionDialogBuilder(context, "Audio", exo, C.TRACK_TYPE_AUDIO)
                        .setShowDisableOption(false).build().show()
                }
            },
            onQuality = {
                runCatching {
                    TrackSelectionDialogBuilder(context, "Quality", exo, C.TRACK_TYPE_VIDEO)
                        .setAllowAdaptiveSelections(true).setShowDisableOption(false).build().show()
                }
            },
            onSpeedCycle = {
                val rates = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                val next = rates[(rates.indexOfFirst { it == exo.playbackParameters.speed }
                    .takeIf { it >= 0 } ?: 2).let { (it + 1) % rates.size }]
                exo.setPlaybackSpeed(next)
                speedLabel = (if (next == 1f) "1.0" else next.toString().trimEnd('0').trimEnd('.')) + "×"
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
        )
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
                    val ends = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date(System.currentTimeMillis() + (remain / exo.playbackParameters.speed).toLong()))
                    meta += "Ends $ends" to false
                }
            }
            nextEpisode?.let { n ->
                meta += ("Up next · S${n.season}" + (n.episode?.let { "E$it" } ?: "") +
                    (if (n.name.isNotEmpty()) " · ${n.name}" else "")) to true
            }
            PauseBoard(
                visible = pauseBoardOn,
                kicker = (if (sleepFired) "Sleep timer · " else "") +
                    when { ended -> "Finished"; isLiveState -> "Live · Paused"; else -> "Paused" },
                title = showName,
                sub = episodeTag?.let { t -> if (episodeName != null) "$t · $episodeName" else t },
                desc = currentEpisode?.overview?.takeIf { it.isNotBlank() } ?: description,
                meta = meta,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 84.dp),
            )
            if (pinfoOn) PlaybackInfoHud(infoRows, Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 76.dp))
            if (subTimingOpen) SubTimingPanel(
                offsetMs = subOffsetMs,
                onNudge = { d -> subOffsetMs += d; chromeTouchedAt = System.currentTimeMillis() },
                onReset = { subOffsetMs = 0L; chromeTouchedAt = System.currentTimeMillis() },
                onDone = { subTimingOpen = false; chromeTouchedAt = System.currentTimeMillis() },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
            )
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
                SubMenuRow("Off", sleepMode.isEmpty()) { sleepSet("") }
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    SubMenuRow("In ${sleepText(m * 60_000L)}", false) { sleepSet("min", m) }
                }
                if (nextEpisode != null) SubMenuRow("When this episode ends", sleepMode == "ep") { sleepSet("ep") }
                Text(
                    if (sleepMode == "min") "Pausing in ${sleepText(sleepAt - System.currentTimeMillis())}."
                    else "Playback pauses when the time is up.",
                    color = MutedC, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // Subtitle style panel (the Style pill toggles it)
        if (subStyleOpen && !pip) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
                SubStylePanel(onDone = { subStyleOpen = false })
            }
        }
        // Add-on subtitle picker
        if (subsMenuOpen && !pip) {
            fun applyPick(st: SubTrack) {
                subBusy = true
                scope.launch {
                    val r = cachedSubFile(context, st)
                    subBusy = false
                    if (r == null) {
                        android.widget.Toast.makeText(context, "Could not load that subtitle", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val (uri, mime) = r
                    val cur = exo.currentMediaItem ?: return@launch
                    val keep = cur.localConfiguration?.subtitleConfigurations?.filter { it.id != "addon-pick" } ?: emptyList()
                    val cfg = MediaItem.SubtitleConfiguration.Builder(uri)
                        .setId("addon-pick")
                        .setMimeType(mime)
                        .setLanguage(st.lang)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    val pos = exo.currentPosition
                    exo.setMediaItem(cur.buildUpon().setSubtitleConfigurations(keep + cfg).build(), pos)
                    exo.prepare(); exo.play()
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(st.lang)
                        .build()
                    activeAddonSub = st.url
                    subBaseFile = Pair(uri, mime)
                    subOffsetMs = 0L; subAppliedMs = 0L
                    subsMenuOpen = false
                }
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
                    .width(280.dp)
                    .background(SurfaceC, RoundedCornerShape(14.dp))
                    .border(1.dp, Line2, RoundedCornerShape(14.dp))
                    .padding(vertical = 8.dp)
                    .heightIn(max = 420.dp),
            ) {
                Text(
                    "SUBTITLES" + (if (subBusy) " · LOADING…" else ""),
                    color = MutedC, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                LazyColumn {
                    item {
                        SubMenuRow("Off", activeAddonSub == null && textTrackCount == 0) {
                            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            activeAddonSub = null
                            subBaseFile = null
                            subOffsetMs = 0L; subAppliedMs = 0L
                            subsMenuOpen = false
                        }
                    }
                    if (textTrackCount >= 1) item {
                        SubMenuRow("Embedded tracks…", false) {
                            subsMenuOpen = false
                            runCatching {
                                TrackSelectionDialogBuilder(context, "Subtitles", exo, C.TRACK_TYPE_TEXT)
                                    .setShowDisableOption(true).build().show()
                            }
                        }
                    }
                    items(addonSubs.size) { i ->
                        val st = addonSubs[i]
                        SubMenuRow(langLabel(st.lang), activeAddonSub == st.url) { applyPick(st) }
                    }
                }
            }
        }
        // Skip intro / recap: a glass pill above the pills row, at the bottom edge when the chrome
        // is away. With the chrome hidden it takes focus, so OK on a remote is the skip.
        skipKind?.let { kind ->
            if (pip) return@let
            val skipFocus = remember { FocusRequester() }
            GlassPill(
                SkipSegments.label(kind),
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (chromeVisible) 150.dp else 40.dp)
                    .focusRequester(skipFocus),
            ) { skipNow(); chromeTouchedAt = System.currentTimeMillis() }
            LaunchedEffect(kind, chromeVisible) { if (!chromeVisible) runCatching { skipFocus.requestFocus() } }
        }
        // Up next: offered near the end, counts down and autoplays once the episode ends.
        if (upnextOpen && nextEpisode != null && !pip) {
            Column(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 150.dp)
                    .width(300.dp)
                    .background(Color(0xE62C2C2E), RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                Text("Up next", color = Color(0xA8EBEBF5), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { upnextCounting = false; onPlayNext(nextEpisode) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(50),
                    ) { Text(if (upnextCounting) "Play now ($upnextLeft)" else "Play now", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { upnextCounting = false; upnextOpen = false; upnextDismissed = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x6B505058)),
                        shape = RoundedCornerShape(50),
                    ) { Text("Dismiss", color = TextC) }
                }
            }
        }
        // Hold-to-speed chip: shown for exactly as long as the finger is down.
        if (heldSpeed != null) Text(
            "▶▶ ${if (Prefs.holdRate % 1f == 0f) Prefs.holdRate.toInt().toString() else Prefs.holdRate.toString()}×",
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp)
                .background(Color(0x8C000000), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
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
            Text(
                (if (f.first > 0) "⏩ " else "⏪ ") + "${f.second}s",
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(if (f.first > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 44.dp)
                    .background(Color(0x8C000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
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

/** Which double-tap zone an x position falls in: -1 left, +1 right, 0 middle (dead zone). */
private fun tapZone(x: Float, width: Int): Int = when {
    x < width * 0.35f -> -1
    x > width * 0.65f -> 1
    else -> 0
}

internal fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
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
