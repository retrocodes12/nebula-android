package com.nuvio.ckplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Which Library tab is up — file-level so it survives leaving the tab (only the top screen is composed). */
private var libTab by mutableStateOf(0)

/**
 * Library: My List · Continue Watching · Upcoming as one segmented pill, each with a real empty state
 * (a glyph, one line, one sentence, one button that goes somewhere useful) instead of a bare sentence.
 * Saved titles and upcoming episodes are the same data as before; Continue Watching is Home's records,
 * full width, with the same long-press sheet.
 */
@Composable
internal fun LibraryScreen(
    version: Int,
    onOpen: (LibItem) -> Unit,
    onPlayEpisode: (LibItem, Episode) -> Unit,
    onResume: (ProgressRec) -> Unit = {},
    onSheetResume: (ProgressRec) -> Unit = onResume,     // Resume chosen on the sheet: already a decision
    onStartOver: (ProgressRec) -> Unit = {},
    onDetails: (ProgressRec) -> Unit = {},
    onGoHome: () -> Unit = {},
    onGoSearch: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var items by remember(version) { mutableStateOf(Library.list(ctx)) }
    var upcoming by remember(version) { mutableStateOf<List<Library.UpRow>?>(null) }
    var continueRows by remember(version) { mutableStateOf(Progress.continueList(ctx)) }
    var sheetFor by remember { mutableStateOf<LibItem?>(null) }
    var cwSheet by remember { mutableStateOf<ProgressRec?>(null) }

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
                    upcoming = null      // the date list is rebuilt from the saved series when Upcoming is next opened
                },
            ),
            onDismiss = { sheetFor = null },
        )
    }
    cwSheet?.let { r ->
        val parts = r.name.split(" · ")
        val isEp = parts.size > 1 && Regex("""^S\d+E\d+${'$'}""", RegexOption.IGNORE_CASE).matches(parts[1].trim())
        CardSheet(
            title = if (isEp) parts[0] else r.name,
            sub = listOfNotNull(parts.getOrNull(1)?.takeIf { isEp }?.trim()?.uppercase(), (r.dur - r.pos).takeIf { it > 0 }?.let { fmtLeft(it) })
                .joinToString("  ·  ").ifEmpty { null },
            poster = r.poster,
            shape = "landscape",
            actions = listOf(
                SheetAction(Icons.Filled.PlayArrow, "Resume") { onSheetResume(r) },
                SheetAction(Icons.Filled.Replay, "Start over") { onStartOver(r) },
                SheetAction(Icons.Filled.Info, "View details") { onDetails(r) },
                SheetAction(Icons.Filled.Delete, "Remove from Continue watching", destructive = true) {
                    Progress.clear(ctx, r.type, r.id)
                    continueRows = Progress.continueList(ctx)
                },
            ),
            onDismiss = { cwSheet = null },
        )
    }

    // the date list costs a meta call per saved series, so it is fetched when the tab is opened
    LaunchedEffect(version, items.size, libTab) {
        if (libTab != 2 || upcoming != null) return@LaunchedEffect
        upcoming = if (items.none { it.type == "series" }) emptyList()
        else runCatching { Library.upcoming(ctx) }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        Text("Library", color = TextC, fontSize = 34.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp, modifier = Modifier.padding(bottom = 12.dp))
        // three chips must sit inside one pill without scrolling; 360dp phones get the short middle label
        val narrow = LocalConfiguration.current.screenWidthDp < 400
        Segmented {
            Chip("My List", libTab == 0, inSeg = true) { libTab = 0 }
            Chip(if (narrow) "Continue" else "Continue Watching", libTab == 1, inSeg = true) { libTab = 1 }
            Chip("Upcoming", libTab == 2, inSeg = true) { libTab = 2 }
        }
        Spacer(Modifier.height(14.dp))
        when (libTab) {
            0 -> MyListTab(items, onOpen, onLong = { sheetFor = it }, onGoHome = onGoHome)
            1 -> ContinueTab(continueRows, onResume, onLong = { cwSheet = it }, onGoSearch = onGoSearch)
            else -> UpcomingTab(items, upcoming, onPlayEpisode, onGoHome)
        }
    }
}

/** A real empty state: a glyph, one line, one sentence, and one pill that goes somewhere useful. */
@Composable
internal fun EmptyState(icon: ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MutedC, modifier = Modifier.size(28.dp))
        Text(
            title, color = TextC, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = Sans,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text, color = MutedC, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).widthIn(max = 320.dp),
        )
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 11.dp),
            modifier = Modifier.padding(top = 18.dp),
        ) { Text(action, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun MyListTab(items: List<LibItem>, onOpen: (LibItem) -> Unit, onLong: (LibItem) -> Unit, onGoHome: () -> Unit) {
    if (items.isEmpty()) {
        EmptyState(
            Icons.Filled.Bookmark, "Nothing saved yet",
            "Open any title and press + My List to keep it here.",
            "Browse Home", onGoHome,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 104.dp),
    ) {
        items(items, key = { it.type + ":" + it.id }) { li ->
            MetaCard(
                MetaItem(li.id, li.type, li.name, li.poster, li.shape),
                Modifier.fillMaxWidth(),
                onLongClick = { onLong(li) },
            ) { onOpen(li) }
        }
    }
}

@Composable
private fun ContinueTab(rows: List<ProgressRec>, onResume: (ProgressRec) -> Unit, onLong: (ProgressRec) -> Unit, onGoSearch: () -> Unit) {
    if (rows.isEmpty()) {
        EmptyState(
            Icons.Filled.PlayArrow, "Nothing in progress",
            "Start something and it shows up here to pick up later.",
            "Find something to watch", onGoSearch,
        )
        return
    }
    LazyVerticalGrid(
        // 16:9 art cards, or poster columns like My List when Settings › Home says Poster
        columns = GridCells.Adaptive(minSize = if (Prefs.cwStyle == "poster") 118.dp else 300.dp),
        horizontalArrangement = Arrangement.spacedBy(if (Prefs.cwStyle == "poster") 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 104.dp),
    ) {
        items(rows, key = { Progress.key(it.type, it.id) }) { r ->
            ContinueCard(r, Modifier.fillMaxWidth(), onClick = { onResume(r) }, onLongClick = { onLong(r) })
        }
    }
}

@Composable
private fun UpcomingTab(items: List<LibItem>, up: List<Library.UpRow>?, onPlayEpisode: (LibItem, Episode) -> Unit, onGoHome: () -> Unit) {
    if (items.none { it.type == "series" }) {
        EmptyState(
            Icons.Filled.Event, "No series saved",
            "Save a series and its new episodes are listed here by date.",
            "Browse Home", onGoHome,
        )
        return
    }
    if (up == null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { SkeletonRow(44.dp, 66.dp, circle = false) } }
        return
    }
    if (up.isEmpty()) {
        EmptyState(
            Icons.Filled.Event, "Nothing scheduled",
            "No dated episodes are coming up for your saved series.",
            "Browse Home", onGoHome,
        )
        return
    }
    val shows = up.map { it.series.id }.distinct().size
    LazyColumn(contentPadding = PaddingValues(bottom = 104.dp)) {
        item(key = "uphead") { RowHeader("Upcoming", "$shows series", null) }
        var lastDay = ""
        var firstOfDay = false
        up.forEach { row ->
            val day = Library.dayLabel(row.time)
            if (day != lastDay) {
                lastDay = day
                firstOfDay = true
                item(key = "day/" + row.time) { Eyebrow(day, Modifier.padding(top = 18.dp, bottom = 4.dp)) }
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
    }
}
