package com.nuvio.ckplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private fun SavedRow.busy() = phase != DownloadPlan.Phase.DONE && phase != DownloadPlan.Phase.FAILED

/** A saved title's sheet: play it, stop or carry on, try again, or delete it. */
@Composable
private fun DownloadSheet(r: SavedRow, onPlay: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (title, rest) = DownloadPlan.nameParts(r.meta.name)
    CardSheet(
        title = title,
        sub = listOfNotNull(rest, Downloads.line(ctx, r)).joinToString("  ·  "),
        poster = r.meta.art ?: r.meta.poster,
        shape = r.meta.shape,
        actions = buildList {
            if (r.phase == DownloadPlan.Phase.DONE) add(SheetAction(Icons.Filled.PlayArrow, "Play") { onPlay() })
            if (r.phase == DownloadPlan.Phase.PAUSED) add(SheetAction(Icons.Filled.Download, "Resume download") { Downloads.resume(ctx, r) })
            else if (r.busy()) add(SheetAction(Icons.Filled.Pause, "Pause download") { Downloads.pause(ctx, r) })
            if (r.phase == DownloadPlan.Phase.FAILED) add(SheetAction(Icons.Filled.Refresh, "Try again") {
                scope.launch { Downloads.retry(ctx, r)?.let { Toasts.show(it) } }
            })
            add(SheetAction(Icons.Filled.Delete, if (r.phase == DownloadPlan.Phase.DONE) "Delete download" else "Cancel download", destructive = true) {
                Downloads.remove(ctx, r)
            })
        },
        onDismiss = onDismiss,
    )
}

/** What the arrow's glyph is: a ring while it runs, a tick when it is here. */
@Composable
private fun DownloadGlyph(r: SavedRow?, ink: Color, size: Int) {
    when {
        r == null -> Icon(Icons.Filled.Download, contentDescription = "Download", tint = ink, modifier = Modifier.size(size.dp))
        r.phase == DownloadPlan.Phase.DONE -> Icon(Icons.Filled.DownloadDone, contentDescription = "Downloaded", tint = ink, modifier = Modifier.size(size.dp))
        r.phase == DownloadPlan.Phase.FAILED -> Icon(Icons.Filled.ErrorOutline, contentDescription = "Download failed", tint = Red, modifier = Modifier.size(size.dp))
        r.percent > 0f -> CircularProgressIndicator(
            progress = { (r.percent / 100f).coerceIn(0f, 1f) },
            color = ink, trackColor = Color(0x38FFFFFF), strokeWidth = 2.dp, modifier = Modifier.size(size.dp),
        )
        else -> CircularProgressIndicator(color = ink, trackColor = Color(0x38FFFFFF), strokeWidth = 2.dp, modifier = Modifier.size(size.dp))
    }
}

/** The arrow at the end of an episode row. Pressed with nothing saved it asks for a source; otherwise it is the sheet. */
@Composable
internal fun DownloadMark(type: String, id: String, onPlay: () -> Unit, onDownload: () -> Unit) {
    val r = Downloads.find(type, id)
    var sheet by remember(type, id) { mutableStateOf(false) }
    if (sheet && r != null) DownloadSheet(r, onPlay) { sheet = false }
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable { if (r == null) onDownload() else sheet = true },
        contentAlignment = Alignment.Center,
    ) { DownloadGlyph(r, MutedC, 21) }
}

/** The title page's round button for a film. */
@Composable
internal fun DownloadAction(type: String, id: String, onPlay: () -> Unit, onDownload: () -> Unit) {
    val r = Downloads.find(type, id)
    var sheet by remember(type, id) { mutableStateOf(false) }
    if (sheet && r != null) DownloadSheet(r, onPlay) { sheet = false }
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color(0x1FFFFFFF))
            .clickable(onClickLabel = "Download") { if (r == null) onDownload() else sheet = true },
        contentAlignment = Alignment.Center,
    ) { DownloadGlyph(r, TextC, 21) }
}

/** Library › Downloads: every saved film and show of this profile, works with no connection at all. */
@Composable
internal fun DownloadsTab(onPlay: (SavedRow) -> Unit, onGoHome: () -> Unit) {
    val ctx = LocalContext.current
    val rows = Downloads.mine()
    if (rows.isEmpty()) {
        EmptyState(
            Icons.Filled.Download, "Nothing downloaded yet",
            "Press the download arrow on a film or an episode to keep it on this phone and watch it with no connection.",
            "Browse Home", onGoHome,
        )
        return
    }
    var confirm by remember { mutableStateOf(false) }
    if (confirm) ConfirmSheet(
        "Delete all downloads?", "Everything this profile saved leaves the phone. Your place in each title is kept.", "Delete all",
        onConfirm = { confirm = false; Downloads.removeMine(ctx) }, onDismiss = { confirm = false },
    )
    val free = remember(rows.size) { Downloads.freeBytes(ctx) }
    val used = rows.sumOf { it.bytes }
    LazyColumn(state = rememberKeptList("downloads"), contentPadding = PaddingValues(bottom = navPadBottom())) {
        item(key = "room") {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(DownloadPlan.sizeText(used) + " used · " + DownloadPlan.sizeText(free) + " free", Modifier.weight(1f))
                Chip("Delete all", false) { confirm = true }
            }
        }
        DownloadPlan.shelves(rows) { it.meta }.forEach { (key, shelf) ->
            val show = DownloadPlan.nameParts(shelf.first().meta.name)
            if (show.second != null) item(key = "show/$key") {
                Eyebrow(show.first, Modifier.padding(top = 18.dp, bottom = 4.dp))
            }
            items(shelf, key = { it.key }) { r -> DownloadRow(r, onPlay) }
        }
    }
}

@Composable
private fun DownloadRow(r: SavedRow, onPlay: (SavedRow) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember(r.key) { mutableStateOf(false) }
    if (sheet) DownloadSheet(r, { onPlay(r) }) { sheet = false }
    val (title, rest) = DownloadPlan.nameParts(r.meta.name)
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LineC))
        FocusCard(
            shape = RoundedCornerShape(12.dp), zoom = false, modifier = Modifier.fillMaxWidth(),
            onClick = {
                when (r.phase) {
                    DownloadPlan.Phase.DONE -> onPlay(r)
                    DownloadPlan.Phase.FAILED -> scope.launch { Downloads.retry(ctx, r)?.let { Toasts.show(it) } }
                    else -> sheet = true
                }
            },
            onLongClick = { sheet = true },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val thumb = Modifier.width(44.dp).height(66.dp).clip(RoundedCornerShape(8.dp)).background(Surface2)
                val art = r.meta.art?.let { java.io.File(it) } ?: r.meta.poster
                if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = thumb)
                else Box(thumb, contentAlignment = Alignment.Center) {
                    Text(title.take(1).uppercase(), color = FaintC, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        rest ?: title, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Downloads.line(ctx, r), color = MutedC, fontSize = 12.5.sp, fontFamily = Mono,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                    )
                    if (r.phase == DownloadPlan.Phase.RUNNING && r.percent > 0f) {
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(LineC)) {
                            Box(Modifier.fillMaxWidth((r.percent / 100f).coerceIn(0f, 1f)).fillMaxSize().background(Red))
                        }
                    }
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { sheet = true },
                    contentAlignment = Alignment.Center,
                ) { DownloadGlyph(r, MutedC, 21) }
            }
        }
    }
}
