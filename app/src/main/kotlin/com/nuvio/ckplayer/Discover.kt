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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException

/**
 * Search › Discover: browse one add-on catalog by type, catalog and genre
 * without typing anything. Three pills pick the source, a grid shows the page,
 * scrolling walks the pages with `skip`. The last choice is kept (Prefs), so
 * the section reopens where it was. Held on SearchUiState so a title opened
 * from the grid comes back to the same grid.
 */
internal class DiscoverUiState {
    var options by mutableStateOf<List<DiscoverCatalog>>(emptyList())
    var optionsLoaded by mutableStateOf(false)
    var current by mutableStateOf<DiscoverCatalog?>(null)
    var genre by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<MetaItem>>(emptyList())
    var loading by mutableStateOf(false)
    var status by mutableStateOf("")
    var loadedFor: Pair<String, String?>? = null
    var fetched = 0
    var pageDone by mutableStateOf(true)
    var paging by mutableStateOf(false)
    val gridState = LazyGridState()
}

/** One browsable catalog of one add-on — the unit the pickers choose between. */
internal data class DiscoverCatalog(val addon: Addon, val catalog: CatalogRef) {
    val key: String get() = addon.manifestUrl + "|" + catalog.type + "|" + catalog.id
}

/** Every browsable catalog of every enabled add-on, in add-on order. */
internal suspend fun discoverOptions(ctx: Context): List<DiscoverCatalog> {
    val out = mutableListOf<DiscoverCatalog>()
    for (a in activeAddons(ctx)) {
        val m = runCatching { manifestFor(a.manifestUrl) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: continue
        for (c in m.catalogs) if (c.browsable && c.id.isNotEmpty()) out.add(DiscoverCatalog(a, c))
    }
    return out
}

/** A pill that opens a picker: the current value, then a chevron. */
@Composable
private fun PickPill(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(pill)
            .background(SurfaceC)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else LineC, pill)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(start = 15.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One line, always: a Row hands the last pill whatever width is left, and a
        // wrapping Text then stacked "All genres" a syllable per line. Long catalog
        // names end in an ellipsis instead; the row scrolls when three don't fit.
        Text(
            label, color = TextC, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MutedC, modifier = Modifier.padding(start = 2.dp).size(18.dp))
    }
}

/** A bottom sheet listing choices; the current one carries a check. Same material as CardSheet. */
@Composable
internal fun PickSheet(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    // A remote opens it ON the current choice, as CardSheet opens on its first row: it opened with nothing lit (the
    // first press only landed) and at the top of the list — Vietnamese in the subtitle list was fifty presses away.
    val remote = remoteMode()
    val here = options.indexOfFirst { it.second == current }.coerceAtLeast(0)
    val hereFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (here - 2).coerceAtLeast(0))
    val shown = remember { MutableTransitionState(false) }
    var closing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { shown.targetState = true }
    LaunchedEffect(closing, shown.isIdle) {
        if (closing && shown.isIdle && !shown.currentState) { picked?.let(onPick); onDismiss() }
    }
    val close: (String?) -> Unit = { v -> picked = v; closing = true; shown.targetState = false }
    Dialog(onDismissRequest = { close(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(shown, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
                Box(Modifier.fillMaxSize().background(Color(0xB8000000)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { close(null) })
            }
            AnimatedVisibility(
                shown, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(300)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(190)) { it } + fadeOut(tween(150)),
            ) {
                val top = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                Column(
                    Modifier.fillMaxWidth().clip(top).background(Color(0xFF141418)).border(1.dp, Color(0x14FFFFFF), top)
                        .navigationBarsPadding().padding(top = 22.dp, bottom = 10.dp),
                ) {
                    Text(title, color = TextC, fontSize = 19.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 0.dp).padding(bottom = 10.dp))
                    // inside the composed content, as CardSheet's: AnimatedVisibility composes it only once it enters
                    if (remote) LaunchedEffect(Unit) {
                        repeat(10) {
                            withFrameNanos {}
                            if (runCatching { hereFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
                        itemsIndexed(options) { i, (label, value) ->
                            val on = value == current
                            val interaction = remember { MutableInteractionSource() }
                            val focused by interaction.collectIsFocusedAsState()
                            // the lit row is the tvOS lozenge (white, black ink) — an 8 % tint did not read from a sofa
                            val lit = focused && remote
                            Row(
                                Modifier.fillMaxWidth()
                                    .then(if (i == here) Modifier.focusRequester(hereFocus) else Modifier)
                                    .background(if (lit) Color.White else if (focused) Color(0x14FFFFFF) else Color.Transparent)
                                    .clickable(interactionSource = interaction, indication = null) { close(value) }
                                    .padding(horizontal = 22.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    label, color = if (lit) Color.Black else if (on) TextC else MutedC, fontSize = 15.sp,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.weight(1f),
                                )
                                if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = if (lit) Color.Black else Red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Search page's idle body: [header] (recents and the add-on chips) on top,
 * then the Discover pickers and the poster grid, all in one scrolling grid so
 * the page moves as one piece.
 */
@Composable
internal fun DiscoverSection(
    ctx: Context,
    st: DiscoverUiState,
    modifier: Modifier = Modifier,
    onOpen: (Addon, MetaItem) -> Unit,
    header: @Composable () -> Unit,
) {
    // the add-ons' catalogs, once per session (manifests are cached)
    LaunchedEffect(Unit) {
        if (st.optionsLoaded) return@LaunchedEffect
        st.options = discoverOptions(ctx)
        st.optionsLoaded = true
        // reopen where it was: the saved catalog, else the saved type's first, else the first
        val saved = st.options.firstOrNull { it.key == Prefs.discCatalog }
            ?: st.options.firstOrNull { it.catalog.type == Prefs.discType }
            ?: st.options.firstOrNull()
        st.current = saved
        st.genre = Prefs.discGenre.takeIf { g -> g.isNotEmpty() && saved?.catalog?.genres?.contains(g) == true }
    }
    val current = st.current
    val types = remember(st.options) { st.options.map { it.catalog.type }.distinct() }
    val catalogsOfType = remember(st.options, current) { st.options.filter { it.catalog.type == current?.catalog?.type } }

    // the first page for the current pick; skipped when the items on hand already match
    LaunchedEffect(current, st.genre) {
        val c = current ?: run { st.items = emptyList(); st.status = ""; return@LaunchedEffect }
        val want = Pair(c.key, st.genre)
        if (st.loadedFor == want) return@LaunchedEffect
        st.loading = true; st.items = emptyList(); st.status = ""
        st.fetched = 0; st.pageDone = true
        runCatching { Stremio.loadCatalog(c.addon.base, c.catalog, st.genre) }
            .onSuccess {
                // deduped as the paging path is: a catalogue that lists a title twice crashed the keyed grid
                st.items = it.distinctBy { m -> m.type + ":" + m.id }
                st.fetched = it.size
                st.pageDone = it.isEmpty() || !c.catalog.skip
                st.status = if (it.isEmpty()) "Nothing here." else ""
                st.loading = false; st.loadedFor = want
            }
            .onFailure {
                if (it is CancellationException) throw it
                st.status = "Couldn’t load this catalog."; st.loading = false; st.loadedFor = null
            }
    }
    // the next page when the grid nears its end
    val reachedEnd by remember {
        derivedStateOf {
            val last = st.gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= st.gridState.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(reachedEnd, st.pageDone, st.paging, current, st.genre) {
        if (!reachedEnd || st.pageDone || st.paging || st.loading) return@LaunchedEffect
        val c = current ?: return@LaunchedEffect
        if (st.items.size >= 1000) { st.pageDone = true; return@LaunchedEffect }
        st.paging = true
        runCatching { Stremio.loadCatalog(c.addon.base, c.catalog, st.genre, null, st.fetched) }
            .onSuccess { page ->
                st.fetched += page.size
                val seen = st.items.mapTo(HashSet()) { it.type + ":" + it.id }
                val fresh = page.filter { seen.add(it.type + ":" + it.id) }
                if (fresh.isNotEmpty()) st.items = st.items + fresh
                st.pageDone = page.isEmpty() || fresh.isEmpty()
            }
            .onFailure { if (it is CancellationException) throw it; st.pageDone = true }
        st.paging = false
    }

    var picker by remember { mutableStateOf<String?>(null) }     // "type" | "catalog" | "genre"
    fun choose(next: DiscoverCatalog?, genre: String?) {
        st.current = next; st.genre = genre
        Prefs.setDiscover(ctx, next?.catalog?.type ?: "", next?.key ?: "", genre ?: "")
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp * Prefs.posterScale),
        state = st.gridState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = navPadBottom()),
        modifier = modifier.fillMaxWidth(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                header()
                if (st.options.isNotEmpty() && current != null) {
                    Text("Discover", color = TextC, fontSize = 28.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp,
                        modifier = Modifier.padding(top = 30.dp, bottom = 12.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PickPill(typeLabel(current.catalog.type)) { picker = "type" }
                        PickPill(current.catalog.name.ifEmpty { "Catalog" }) { picker = "catalog" }
                        if (current.catalog.genres.isNotEmpty()) PickPill(st.genre ?: "All genres") { picker = "genre" }
                    }
                    Text(
                        current.addon.name.ifEmpty { "Add-on" } + " • " + typeLabel(current.catalog.type),
                        color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                    )
                    if (st.status.isNotEmpty()) Text(st.status, color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
        }
        if (st.loading && st.items.isEmpty()) items(9) { SkeletonCell() }
        items(st.items, key = { it.type + ":" + it.id }) { m ->
            MetaCard(m, Modifier.returnTo("d/${m.type}:${m.id}")) { current?.let { onOpen(it.addon, m) } }
        }
        if (st.paging) items(6) { SkeletonCell() }
    }

    when (picker) {
        "type" -> PickSheet("Type", types.map { typeLabel(it) to it }, current?.catalog?.type ?: "", onPick = { t ->
            if (t != current?.catalog?.type) choose(st.options.firstOrNull { it.catalog.type == t }, null)
        }) { picker = null }
        "catalog" -> PickSheet(
            "Catalog",
            catalogsOfType.map { (it.catalog.name.ifEmpty { "Catalog" } + if (catalogsOfType.count { o -> o.catalog.name == it.catalog.name } > 1) " · " + it.addon.name else "") to it.key },
            current?.key ?: "",
            onPick = { k -> if (k != current?.key) choose(st.options.firstOrNull { it.key == k }, null) },
        ) { picker = null }
        "genre" -> PickSheet(
            "Genre",
            listOf("All genres" to "") + (current?.catalog?.genres ?: emptyList()).map { it to it },
            st.genre ?: "",
            onPick = { g -> choose(current, g.ifEmpty { null }) },
        ) { picker = null }
    }
}
