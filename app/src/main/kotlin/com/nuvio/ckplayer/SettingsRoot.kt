package com.nuvio.ckplayer

// Settings, the root page (2026-11-17, his "check how the whole settings page ui looks in [the reference] and implement
// it"): a big title, a search over every page, then one card per group (You, Look, Watch, Services, System) of rows
// that open their pages, then the supporters' thanks and the version. The rows themselves (SettingsGroup, SettingsRow,
// SettingsToggle) live in MainActivity.kt beside the pages they open.

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val PARTY_PREFS = "ckplayer"

/** One place a search can land: its name, the page that holds it, the words people may type for it. */
private class SetTarget(val title: String, val where: String, val words: String, val icon: ImageVector, val open: () -> Unit)

/** Every word of the query is somewhere in the name, the page or the words (case aside). */
internal fun settingsHit(title: String, where: String, words: String, query: String): Boolean {
    val hay = "$title $where $words".lowercase()
    val q = query.lowercase().split(' ', ',').filter { it.isNotBlank() }
    return q.isNotEmpty() && q.all { hay.contains(it) }
}

@Composable
internal fun SettingsScreen(
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
    onTracking: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var updSub by remember { mutableStateOf("You're on v$version · tap to check now") }
    var checking by remember { mutableStateOf(false) }
    fun checkUpdates() {
        if (checking) return
        checking = true; updSub = "Checking…"
        scope.launch {
            val r = runCatching { Updates.latest(early = Perks.early()) }.getOrNull()
            updSub = when {
                r == null -> "Could not reach the release feed. Try again later"
                Updates.isNewer(r.version, version) ->
                    "v${r.version}${if (r.beta) " (an early build)" else ""} is available. The update card is waiting on Home"
                else -> "You're up to date (v$version)"
            }
            checking = false
        }
    }
    fun privacy() {
        // many TVs have no browser: say where to read it rather than do nothing
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.rifflehq.in/privacy.html"))) }
            .onFailure { Toasts.show("No web browser on this device. Read it at play.rifflehq.in/privacy.html") }
    }
    // "3 add-ons, all on" — read on every visit
    val addonsSub = remember {
        val all = loadAddons(ctx)
        val off = all.count { !it.enabled }
        when {
            all.isEmpty() -> "Add, rank and manage your add-ons"
            off == 0 -> "${all.size} add-on${if (all.size > 1) "s" else ""}, all on"
            else -> "${all.size} add-on${if (all.size > 1) "s" else ""} · ${all.size - off} on · $off off"
        }
    }
    val who = remember(Viewers.version, Viewers.current) { Viewers.list(ctx) }
    var query by remember { mutableStateOf("") }

    // what the search can reach: every page, and the settings people look for by name
    val targets = listOfNotNull(
        SetTarget("Account", "You", "profile sign in sign out password handle sync devices delete", Icons.Filled.Person, onProfile),
        SetTarget("Who's watching", "You", "profiles switch pin kids picture people", Icons.Filled.People) { Viewers.open() },
        SetTarget("Watch party", "You", "party code together sync host join party name share my stream", Icons.Filled.Groups, onParty),
        SetTarget("Friends", "You", "friends recommend rate share handle", Icons.Filled.Favorite, onFriends),
        SetTarget("Appearance", "Look", "accent colour color surface black font text size cards corners theme motion picture", Icons.Filled.Palette, onLayout),
        SetTarget("Ratings on posters", "Look › Appearance", "imdb score rating cards", Icons.Filled.Palette, onLayout),
        SetTarget("Home", "Look", "featured hero carousel rows continue watching title pages cast genres add-on names", Icons.Filled.Home, onHome),
        SetTarget("Continue Watching", "Look › Home", "resume progress up next row", Icons.Filled.Home, onHome),
        SetTarget("Playback", "Watch", "player next episode autoplay skip intro languages audio subtitles pause board clock seek buffer", Icons.Filled.PlayArrow, onPlayback),
        SetTarget("Subtitles and languages", "Watch › Playback", "subtitle style size colour language audio track", Icons.Filled.PlayArrow, onPlayback),
        SetTarget("Auto-play next episode", "Watch › Playback", "next episode countdown binge skip intro outro", Icons.Filled.PlayArrow, onPlayback),
        SetTarget("Scrub previews", "Watch › Playback", "seekr thumbnails preview pictures timeline", Icons.Filled.PlayArrow, onPlayback),
        SetTarget("Streams", "Watch", "stream cards order quality minimum badges details p2p torrent debrid picking", Icons.Filled.FilterList, onStreams),
        SetTarget("Autoplay the best stream", "Watch › Streams", "auto pick best stream instant debrid 4k aim wait", Icons.Filled.FilterList, onStreams),
        SetTarget("Add-ons", "Watch", "addon install manage rank configure manifest catalog", Icons.Filled.Extension, onAddons),
        SetTarget("Tracking", "Services", "trakt simkl mdblist scrobble history watched", Icons.Filled.History, onTracking),
        if (Support.visible) SetTarget("Support Nebula", "Services", "support donate supporter code redeem plus founder monthly wall", Icons.Filled.VolunteerActivism, onSupport) else null,
        SetTarget("Show every option", "System", "essential everything advanced more options", Icons.Filled.Tune) { Prefs.setSetMode(ctx, if (Prefs.everything) "essential" else "all") },
        SetTarget("Advanced", "System", "reset clear cache artwork welcome start", Icons.Filled.Tune) {
            if (!Prefs.everything) Prefs.setSetMode(ctx, "all")
            onAdvanced()
        },
        SetTarget("Check for updates", "System", "update version early build", Icons.Filled.Download) { checkUpdates() },
        SetTarget("Privacy Policy", "System", "privacy data tracking", Icons.Filled.Shield) { privacy() },
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            // this screen already answered a remote: the requester only makes the first press land somewhere chosen
            .focusRequester(tvFirstFocus())
            .focusGroup(),
    ) {
        Text(
            "Settings", color = TextC, fontSize = 34.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp, modifier = Modifier.padding(top = 24.dp),
        )
        SettingsSearchField(query) { query = it }
        if (query.isNotBlank()) {
            val hits = targets.filter { settingsHit(it.title, it.where, it.words, query) }
            if (hits.isEmpty()) Text(
                "Nothing in Settings matches “${query.trim()}”.", color = MutedC, fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            ) else SettingsGroup {
                hits.forEachIndexed { i, t -> SettingsRow(t.icon, t.title, t.where, i < hits.lastIndex) { t.open() } }
            }
        } else {
            // You
            SettingsGroup {
                AccountRow(onProfile)
                SettingsRow(
                    Icons.Filled.People, "Who's watching",
                    if (who.size < 2) "Add up to six profiles for the people in the house"
                    else "Watching as ${Viewers.name(Viewers.cur(ctx))} · ${who.size} profiles",
                    true,
                ) { Viewers.open() }
                if (who.size >= 2) SettingsToggle(
                    "Ask who's watching when Nebula opens", "A profile with a PIN always asks",
                    Viewers.askAtLaunch(ctx),
                ) { Viewers.setAskAtLaunch(ctx, it) }
                SettingsRow(Icons.Filled.Groups, "Watch party", "Watch in sync with friends using a code", true, onParty)
                SettingsRow(Icons.Filled.Favorite, "Friends", "Rate, share and recommend", false, onFriends)
            }
            GroupGap()
            // Look
            SettingsGroup {
                SettingsRow(Icons.Filled.Palette, "Appearance", "Accent, surface, text, font and cards", true, onLayout)
                SettingsRow(Icons.Filled.Home, "Home", "Featured, Continue Watching, rows and title pages", false, onHome)
            }
            GroupGap()
            // Watch
            SettingsGroup {
                SettingsRow(Icons.Filled.PlayArrow, "Playback", "Player, next episode, languages and subtitles", true, onPlayback)
                SettingsRow(
                    Icons.Filled.FilterList, "Streams",
                    if (Prefs.everything) "Cards, order, quality floor and picking by itself" else "Cards, picking by itself and autoplaying the best one",
                    true, onStreams,
                )
                SettingsRow(Icons.Filled.Extension, "Add-ons", addonsSub, false, onAddons)
            }
            GroupGap()
            // Services
            SettingsGroup {
                SettingsRow(Icons.Filled.History, "Tracking", "Trakt, Simkl and MDBList: what you watch, kept there", Support.visible, onTracking)
                if (Support.visible) SettingsRow(
                    Icons.Filled.VolunteerActivism, "Support Nebula", "Free, no ads. Chip in if you like", false, onSupport,
                )
            }
            GroupGap()
            // System
            SettingsGroup {
                SettingsToggle(
                    "Show every option", "Off keeps the common choices on each page",
                    Prefs.everything,
                ) { Prefs.setSetMode(ctx, if (it) "all" else "essential") }
                if (Prefs.everything) SettingsRow(Icons.Filled.Tune, "Advanced", "Welcome message, reset all settings, clear caches", true, onAdvanced)
                SettingsRow(Icons.Filled.Download, "Check for updates", updSub, true) { checkUpdates() }
                SettingsRow(Icons.Filled.Shield, "Privacy Policy", "No email, no tracking. How Nebula handles data", false) { privacy() }
            }
            // the names on the supporters' wall, Founders first (Perks.kt); nothing while the wall is empty
            SupportersThanks()
            SettingsFooter(version)
        }
        Spacer(Modifier.height(110.dp))
    }
}

/** The space between two groups on the root page. */
@Composable
private fun GroupGap() { Spacer(Modifier.height(14.dp)) }

/** The search under the title: a remote opens the keyboard only on OK (tvTyping). */
@Composable
private fun SettingsSearchField(query: String, onChange: (String) -> Unit) {
    val typing = tvTyping()
    OutlinedTextField(
        value = query,
        onValueChange = { onChange(it.take(60)) },
        placeholder = { Text("Search settings", color = MutedC) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MutedC) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear", tint = MutedC) }
        },
        singleLine = true,
        readOnly = typing.readOnly,
        modifier = typing.modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp).returnTo("settings-search"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White, unfocusedBorderColor = LineC, cursorColor = Red,
            focusedTextColor = TextC, unfocusedTextColor = TextC,
            focusedContainerColor = SurfaceC, unfocusedContainerColor = SurfaceC,
        ),
    )
}

/** The account at the head of "You": the profile's picture where the other rows have an icon. */
@Composable
private fun AccountRow(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val p = Cloud.profile
    val state = Cloud.state(ctx)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column {
        Row(
            Modifier.fillMaxWidth()
                .returnTo("row/account").landingSlot()
                .clickable(interactionSource = interaction, indication = null) { onOpen() }
                .then(rowLit(focused))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(p?.avatar ?: "", p?.let { it.name.ifEmpty { it.handle } } ?: "?", 40.dp, dim = p == null, ring = Support.rank() >= 3)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            p != null -> p.name.ifEmpty { "@${p.handle}" }
                            state == "legacy" -> "Add a profile"
                            else -> "Account"
                        },
                        color = TextC, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (p?.sup == true) SupporterMark(mark = Support.myMark())
                }
                val n = Cloud.devices.size
                Text(
                    when {
                        p != null -> "@${p.handle}" + (if (n > 1) " · $n devices" else "") + " · add-ons, progress and My List everywhere"
                        state == "legacy" -> "This device syncs with a link code from an earlier version"
                        else -> "Sign in to keep add-ons, progress and My List on every device"
                    },
                    color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FaintC, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(1.dp).background(LineC))
    }
}

/** The page's foot: the name and the version, quiet. */
@Composable
private fun SettingsFooter(version: String) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nebula", color = MutedC, fontSize = 18.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        Text(
            "Version $version · Android", color = FaintC, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** How you appear to friends in a party (moved here from the old Settings root). */
@Composable
internal fun PartyNameRow() {
    val ctx = LocalContext.current
    var pname by remember {
        mutableStateOf(ctx.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).getString("party_name", "") ?: "")
    }
    val typing = tvTyping()
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp)
            .background(SurfaceC, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Party name", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("How you appear to friends in a party", color = MutedC, fontSize = 13.sp)
        }
        OutlinedTextField(
            value = pname,
            onValueChange = { v ->
                pname = v.take(40)
                ctx.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putString("party_name", pname.trim()).apply()
            },
            placeholder = { Text(Cloud.profile?.name?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL.take(24), color = MutedC) },
            singleLine = true,
            // walking past this row threw the keyboard up on a TV (tvTyping)
            readOnly = typing.readOnly,
            modifier = typing.modifier.width(150.dp).returnTo("party-name"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                focusedTextColor = TextC, unfocusedTextColor = TextC,
            ),
        )
    }
}

/** Trakt, Simkl and MDBList on a page of their own (they sat at the foot of Playback). */
@Composable
internal fun SettingsTrackingScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackBar("Tracking", null, onBack)
        LandOnFirstControl()
        TrackSettings()
        Spacer(Modifier.height(80.dp))
    }
}
