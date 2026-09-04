package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The customisation pass's new Settings pages — Home, Streams, Advanced — plus the two small sheets
 * it needs (the Resume-or-start-over question, and a destructive confirm). Same rows as the pages in
 * MainActivity.kt: SettingsToggle / SettingsChips / SettingsRow inside SettingsGroup cards, every row
 * a title and one line, and every choice taking effect the moment it changes. Essential shows the
 * rows people reach for; Everything (Prefs.everything) shows the rest.
 */

/** "Featured from" choices: the first row, every row, then each row Home shows (from the manifests
    already loaded for Home — Settings is only ever reached after Home has been drawn). */
private fun heroSourceOptions(ctx: Context): List<Pair<String, String>> {
    val out = mutableListOf("first" to "First row", "all" to "All rows")
    activeAddons(ctx).forEach { a ->
        val m = manifestCache[a.manifestUrl] ?: return@forEach
        val cats = m.catalogs.filter { it.browsable }
        cats.forEachIndexed { ci, c ->
            val k = HomeRows.key(a, c)
            if (HomeRows.visible(ctx, k, ci) && out.size < 14) {
                out.add(k to (if (cats.size > 1) catalogLabel(c, m.catalogs) else a.name))
            }
        }
    }
    return out
}

// ---------- Settings › Home ----------
@Composable
internal fun SettingsHomeScreen(onBack: () -> Unit, onRows: () -> Unit) {
    val ctx = LocalContext.current
    val all = Prefs.everything
    val heroOptions = remember { heroSourceOptions(ctx) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Home", null, onBack)
        SettingsHeader("FEATURED", "The full-bleed showcase at the top of Home")
        SettingsGroup {
            SettingsToggle("Featured carousel", "The full-bleed showcase at the top of Home", Prefs.showHero, divider = all) { Prefs.setShowHero(ctx, it) }
            if (all) {
                SettingsChips("Featured from", "Which rows feed the carousel", heroOptions, Prefs.heroSource) { Prefs.setHeroSource(ctx, it) }
                SettingsChips(
                    "Featured changes every", "How long each title stays before the next slides in",
                    listOf("0" to "Off", "6" to "6 s", "10" to "10 s", "20" to "20 s"), Prefs.heroInterval.toString(), divider = false,
                ) { Prefs.setHeroInterval(ctx, it.toInt()) }
            }
        }
        SettingsHeader("CONTINUE WATCHING", "Picking up where you left off")
        SettingsGroup {
            SettingsToggle("Continue Watching", "Pick up where you left off, right on Home", Prefs.showContinue, divider = all) { Prefs.setShowContinue(ctx, it) }
            if (all) {
                SettingsChips(
                    "Continue Watching cards", "Wide art with the episode on it, or posters with the bar",
                    listOf("art" to "Art", "poster" to "Poster"), Prefs.cwStyle,
                ) { Prefs.setCwStyle(ctx, it) }
                SettingsChips(
                    "Continue Watching order", "On Home and in the Library tab",
                    listOf("recent" to "Most recent", "az" to "A to Z"), Prefs.cwSort,
                ) { Prefs.setCwSort(ctx, it) }
                SettingsToggle(
                    "Show episodes not out yet", "Offer the next episode even before its release date",
                    Prefs.cwUnaired, divider = false,
                ) { Prefs.setCwUnaired(ctx, it) }
            }
        }
        SettingsHeader("ROWS", "What the catalog rows show, and in what order")
        SettingsGroup {
            SettingsRow(Icons.Filled.ViewAgenda, "Rows", "Choose which catalogs make Home, and arrange them", all, onRows)
            if (all) {
                SettingsToggle("Add-on names under rows", "The small name beside a row's title", Prefs.rowSubline) { Prefs.setRowSubline(ctx, it) }
                SettingsToggle(
                    "Wide cards everywhere", "Every row uses 16:9 art instead of posters",
                    Prefs.landscapeRows, divider = false,
                ) { Prefs.setLandscapeRows(ctx, it) }
            }
        }
        if (all) {
            SettingsHeader("TITLE PAGES", "What a title's own page lists")
            SettingsGroup {
                SettingsToggle("Cast on title pages", "The people in it, as a row of names", Prefs.detailCast) { Prefs.setDetailCast(ctx, it) }
                SettingsToggle("Genres on title pages", "The genre pills under the synopsis", Prefs.detailGenres, divider = false) { Prefs.setDetailGenres(ctx, it) }
            }
        }
    }
}

// ---------- Settings › Streams ----------
@Composable
internal fun SettingsStreamsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all = Prefs.everything
    val scope = rememberCoroutineScope()
    // how much of the phone the engine is holding — measured off the main thread, it walks a folder
    var p2pCache by remember { mutableStateOf(0L) }
    LaunchedEffect(Prefs.p2p) { if (Prefs.p2p) p2pCache = withContext(Dispatchers.IO) { P2p.cacheBytes(ctx) } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Streams", null, onBack)
        SettingsHeader("P2P", "Torrents, played on this device")
        SettingsGroup {
            if (!P2p.available) SettingsRow(
                Icons.Filled.CloudOff, "P2P streams",
                "This device cannot play torrents, so those streams stay hidden", false, null,
            ) else {
                SettingsToggle(
                    "P2P streams",
                    "Plays torrents \u00b7 while one plays, everyone else sharing that file can see this device's address",
                    Prefs.p2p, divider = Prefs.p2p && all,
                ) {
                    Prefs.setP2p(ctx, it)
                    if (!it) P2p.leave(ctx)          // anything running stops the moment it is switched off
                }
                if (Prefs.p2p && all) {
                    SettingsToggle(
                        "Keep downloads", "Leave a watched torrent on the phone instead of clearing it when the player closes",
                        Prefs.p2pKeep,
                    ) { Prefs.setP2pKeep(ctx, it) }
                    SettingsRow(
                        Icons.Filled.DeleteSweep, "Clear P2P downloads",
                        if (p2pCache > 0L) "${P2p.fmtSize(p2pCache)} on this device" else "Nothing is stored right now",
                        false,
                    ) {
                        scope.launch(Dispatchers.IO) {
                            P2p.clearCache(ctx)
                            p2pCache = P2p.cacheBytes(ctx)
                        }
                        Toasts.show("P2P downloads cleared.")
                    }
                }
            }
        }
        if (all) {
            SettingsHeader("THE LIST", "How each add-on's streams are shown")
            SettingsGroup {
                SettingsChips(
                    "Order streams", "Within each add-on",
                    listOf("listed" to "As listed", "quality" to "Best quality first", "size" to "Smallest first"), Prefs.streamSort,
                ) { Prefs.setStreamSort(ctx, it) }
                SettingsChips(
                    "Minimum quality", "Hide streams below this · streams that do not say stay",
                    listOf("any" to "Any", "720" to "720p", "1080" to "1080p", "4k" to "4K"), Prefs.minRes,
                ) { Prefs.setMinRes(ctx, it) }
                SettingsToggle("Stream details", "Size, bitrate and seeds at the end of a row", Prefs.streamFacts) { Prefs.setStreamFacts(ctx, it) }
                SettingsToggle("Badges", "The small picture and sound marks under a name", Prefs.streamBadges) { Prefs.setStreamBadges(ctx, it) }
                SettingsChips(
                    "Add-on on each row", "Where a stream comes from",
                    listOf("initial" to "Initial", "name" to "Name", "hidden" to "Hidden"), Prefs.addonMark, divider = false,
                ) { Prefs.setAddonMark(ctx, it) }
            }
        }
        SettingsHeader("PICKING", "Skipping the list")
        SettingsGroup {
            SettingsChips(
                "Play the best stream by itself", "Skips the list · Same as last time follows your last pick; First stream takes the top row",
                listOf("off" to "Off", "last" to "Same as last time", "first" to "First stream"), Prefs.autoPick, divider = all,
            ) { Prefs.setAutoPick(ctx, it) }
            if (all) SettingsChips(
                "Wait for add-ons before choosing", "How long the pick waits for slow add-ons to answer",
                listOf("3" to "3 s", "6" to "6 s", "10" to "10 s"), Prefs.pickWait.toString(), divider = false,
            ) { Prefs.setPickWait(ctx, it.toInt()) }
        }
    }
}

// ---------- Settings › Advanced ----------
@Composable
internal fun SettingsAdvancedScreen(onBack: () -> Unit, onClearCache: () -> Unit, onReset: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    if (confirm) ConfirmSheet(
        title = "Reset all settings?",
        text = "Every choice in Settings goes back to how it came. Your add-ons, progress, My List, ratings and profile stay.",
        action = "Reset all settings",
        onConfirm = {
            Prefs.resetAll(ctx)
            SubStyle.reset(ctx)
            HomeRows.reload()
            onReset()
            Toasts.show("Settings are back to how they came.")
        },
        onDismiss = { confirm = false },
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Advanced", null, onBack)
        SettingsHeader("START", "What happens when Nebula opens")
        SettingsGroup {
            SettingsToggle(
                "Welcome message at start", "The short greeting under the top edge once Home is ready",
                Prefs.welcome, divider = false,
            ) { Prefs.setWelcome(ctx, it) }
        }
        SettingsHeader("RESET", "Settings and caches only — nothing you added or watched")
        SettingsGroup {
            SettingsRow(Icons.Filled.RestartAlt, "Reset all settings", "Every choice in Settings, back to how it came · asks first", true) { confirm = true }
            SettingsRow(Icons.Filled.DeleteSweep, "Clear cached artwork and add-on lists", "Pictures and add-on catalog lists are fetched fresh", false) {
                onClearCache()
                runCatching { ctx.imageLoader.memoryCache?.clear() }
                scope.launch(Dispatchers.IO) { runCatching { ctx.imageLoader.diskCache?.clear() } }
                Toasts.show("Cleared. Artwork and add-on lists load fresh.")
            }
        }
    }
}

// ---------- sheets ----------

/** "When you come back · Ask": the two-option sheet before the first play of something already begun. */
@Composable
internal fun ResumeAskSheet(
    title: String, poster: String?, shape: String, savedAt: Long,
    onResume: () -> Unit, onStartOver: () -> Unit, onDismiss: () -> Unit,
) {
    CardSheet(
        title = title, sub = "Where do you want to start?", poster = poster, shape = shape,
        actions = listOf(
            SheetAction(Icons.Filled.PlayArrow, "Resume from ${fmtTime(savedAt)}") { onResume() },
            SheetAction(Icons.Filled.Replay, "Start over") { onStartOver() },
        ),
        onDismiss = onDismiss,
    )
}

/** A destructive confirm in the CardSheet's material: a title, one sentence, Cancel and the action. */
@Composable
internal fun ConfirmSheet(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val shown = remember { MutableTransitionState(false) }
    var closing by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown.targetState = true }
    LaunchedEffect(closing, shown.isIdle) {
        if (closing && shown.isIdle && !shown.currentState) { if (confirmed) onConfirm(); onDismiss() }
    }
    val close: (Boolean) -> Unit = { ok -> confirmed = ok; closing = true; shown.targetState = false }
    Dialog(onDismissRequest = { close(false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(shown, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
                Box(Modifier.fillMaxSize().background(Color(0xB8000000)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { close(false) })
            }
            AnimatedVisibility(
                shown, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(300)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(190)) { it } + fadeOut(tween(150)),
            ) {
                val top = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                Column(
                    Modifier.fillMaxWidth().clip(top).background(Color(0xFF141418)).border(1.dp, Color(0x14FFFFFF), top)
                        .navigationBarsPadding().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 22.dp),
                ) {
                    Text(title, color = TextC, fontSize = 19.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                    Text(text, color = MutedC, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Cancel", false) { close(false) }
                        TextAction(action, danger = true) { close(true) }
                    }
                }
            }
        }
    }
}
