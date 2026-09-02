package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Which catalogs make Home, and in what order. Device-local like the other
 * preferences, with the web player's semantics: `vis` holds the rows switched
 * on or off by hand (an unnamed row shows iff it is one of its add-on's first
 * three); `order` is the full arrangement once a row has been moved.
 */
object HomeRows {
    private const val P = "ckplayer"
    private const val K_VIS = "home_rowvis"
    private const val K_ORDER = "home_roworder"

    /** Bumps on every change so Home rebuilds the next time it is shown. */
    var version by mutableStateOf(0); private set
    private var loaded = false
    private var vis: MutableMap<String, Boolean>? = null
    private var order: MutableList<String>? = null

    fun key(a: Addon, c: CatalogRef) = a.manifestUrl + "|" + c.type + "|" + c.id

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        p.getString(K_VIS, null)?.let { s ->
            runCatching {
                val j = JSONObject(s)
                val m = LinkedHashMap<String, Boolean>()
                for (k in j.keys()) m[k] = j.optBoolean(k)
                vis = m
            }
        }
        p.getString(K_ORDER, null)?.let { s ->
            runCatching {
                val a = JSONArray(s)
                order = MutableList(a.length()) { a.getString(it) }
            }
        }
    }

    private fun save(ctx: Context) {
        val e = ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
        val v = vis
        if (v == null) e.remove(K_VIS) else e.putString(K_VIS, JSONObject(v as Map<*, *>).toString())
        val o = order
        if (o == null) e.remove(K_ORDER) else e.putString(K_ORDER, JSONArray(o).toString())
        e.apply()
        version++
    }

    /** Shown unless switched off by hand; untouched, a row shows iff it is one of its add-on's first three. */
    fun visible(ctx: Context, key: String, nat: Int): Boolean {
        load(ctx)
        return vis?.get(key) ?: (nat < 3)
    }

    /** Its place in the arrangement, else after every arranged row in add-on-then-catalog order. */
    fun orderIndex(ctx: Context, key: String, ai: Int, ci: Int): Int {
        load(ctx)
        val i = order?.indexOf(key) ?: -1
        return if (i >= 0) i else 1000 + ai * 100 + ci
    }

    fun customised(ctx: Context): Boolean { load(ctx); return vis != null || order != null }

    fun toggle(ctx: Context, key: String, nat: Int) {
        load(ctx)
        val m = vis ?: LinkedHashMap<String, Boolean>().also { vis = it }
        m[key] = !visible(ctx, key, nat)
        save(ctx)
    }

    /** A move rewrites the whole arrangement — the list on screen IS the order. */
    fun setOrder(ctx: Context, keys: List<String>) { load(ctx); order = keys.toMutableList(); save(ctx) }

    fun reset(ctx: Context) { load(ctx); vis = null; order = null; save(ctx) }
}

/** "Popular · Movies" — the type joins whenever the add-on's catalogs span more
    than one type or two share a name, so "Popular, Popular" can't happen. A
    lone catalog keeps its bare name. */
internal fun catalogLabel(c: CatalogRef, cats: List<CatalogRef>): String {
    val types = cats.map { it.type }.toSet()
    val dupe = cats.any { it !== c && it.name.equals(c.name, true) }
    return c.name + if ((dupe || types.size > 1) && c.type.isNotEmpty()) " · " + typeLabel(c.type) else ""
}

/** One arrangeable row: a browsable catalog, titled the way Home titles it. */
internal class HomeRowItem(
    val key: String, val addon: Addon, val catalog: CatalogRef,
    val ai: Int, val nat: Int, val name: String, val sub: String,
)

// ---------- Settings → Layout → Home rows ----------
// One list, in Home's order: every browsable catalog of every add-on, a check
// on the ones Home shows. A row switched off keeps its place in the order, so
// switching it back on returns it to where it was.
@Composable
internal fun SettingsHomeRowsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val addons = remember { loadAddons(ctx) }
    var items by remember { mutableStateOf<List<HomeRowItem>?>(null) }   // null while the manifests load
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    val customised = HomeRows.version.let { HomeRows.customised(ctx) }

    LaunchedEffect(attempt) {
        val out = mutableListOf<HomeRowItem>()
        var reached = 0
        addons.forEachIndexed { ai, a ->
            val m = runCatching { manifestFor(a.manifestUrl) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull() ?: return@forEachIndexed
            reached++
            val all = m.catalogs.filter { it.browsable }
            val name = a.name.ifEmpty { m.addon.name }
            all.forEachIndexed { ci, c ->
                val label = catalogLabel(c, m.catalogs)
                out.add(HomeRowItem(HomeRows.key(a, c), a, c, ai, ci, if (all.size > 1) label else name, if (all.size > 1) name else label))
            }
        }
        failed = addons.isNotEmpty() && reached == 0
        items = out.sortedBy { HomeRows.orderIndex(ctx, it.key, it.ai, it.nat) }
    }

    // ---- arranging ----
    // Same arithmetic as the add-on ranking: rows are one height, so a drag is
    // "how many rows have I passed".
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var liftIndex by remember { mutableStateOf(-1) }      // picked up with the D-pad
    var rowSpanPx by remember { mutableStateOf(0f) }
    fun move(from: Int, to: Int): Int {
        val list = items ?: return from
        if (from < 0 || from >= list.size || to < 0 || to >= list.size || from == to) return from
        val next = list.toMutableList()
        next.add(to, next.removeAt(from))
        items = next
        HomeRows.setOrder(ctx, next.map { it.key })
        return to
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                BackBar("Home rows", null, onBack)
                Text(
                    "Every catalog your add-ons offer, in Home’s order. Tap a row to show or hide it" +
                        (if ((items?.size ?: 0) > 1) "; drag by the handle to arrange." else "."),
                    color = MutedC, fontSize = 14.sp, lineHeight = 20.sp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (note.isNotEmpty()) Text(note, color = MutedC, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    else Box(Modifier.weight(1f))
                    if (customised) TextAction("Reset") {
                        HomeRows.reset(ctx)
                        items = items?.sortedBy { HomeRows.orderIndex(ctx, it.key, it.ai, it.nat) }
                        note = "Home is back to its original rows."
                    }
                }
            }
        }
        val list = items
        when {
            addons.isEmpty() -> item { Text("Add an add-on first — its catalogs become rows here.", color = MutedC, fontSize = 14.sp) }
            list == null -> item { Text("Loading your add-ons…", color = MutedC, fontSize = 14.sp) }
            failed -> item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn’t reach your add-ons right now.", color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                    Chip("Retry", false) { items = null; attempt++ }
                }
            }
            list.isEmpty() -> item { Text("Nothing to arrange yet — your add-ons have no catalogs.", color = MutedC, fontSize = 14.sp) }
            else -> itemsIndexed(list, key = { _, r -> r.key }) { i, row ->
                // read from composition, so a toggle repaints this row
                val on = HomeRows.version.let { _ -> HomeRows.visible(ctx, row.key, row.nat) }
                val dragging = i == dragIndex
                val lifted = i == liftIndex
                val raised = dragging || lifted
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
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
                        .onSizeChanged { if (rowSpanPx == 0f) rowSpanPx = it.height + with(density) { 10.dp.toPx() } }
                        .then(if (dragging) Modifier else Modifier.animateItem()),
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (raised) Surface2 else SurfaceC)
                            .border(1.dp, if (focused) Color.White else if (raised) Color(0x3DFFFFFF) else LineC, RoundedCornerShape(12.dp))
                            .clickable(interactionSource = interaction, indication = null) { HomeRows.toggle(ctx, row.key, row.nat) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (list.size > 1) {
                            val grip = remember { MutableInteractionSource() }
                            val gripFocused by grip.collectIsFocusedAsState()
                            Box(
                                Modifier.size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (gripFocused) TextC else Color.Transparent)
                                    // A remote can't drag, so OK picks the row up and the D-pad walks it.
                                    .onKeyEvent { ev ->
                                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                        val here = items?.indexOfFirst { r -> r.key == row.key } ?: -1
                                        if (here < 0) return@onKeyEvent false
                                        when (ev.key) {
                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                liftIndex = if (liftIndex == here) -1 else here
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                true
                                            }
                                            Key.DirectionUp -> if (liftIndex == here) { liftIndex = move(here, here - 1); true } else false
                                            Key.DirectionDown -> if (liftIndex == here) { liftIndex = move(here, here + 1); true } else false
                                            Key.Back -> if (liftIndex >= 0) { liftIndex = -1; true } else false
                                            else -> false
                                        }
                                    }
                                    // clickable, not focusable: it swallows the tap that would
                                    // otherwise toggle the row, and gives the D-pad a landing
                                    .clickable(interactionSource = grip, indication = null) {}
                                    .pointerInput(row.key) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragIndex = items?.indexOfFirst { r -> r.key == row.key } ?: -1
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
                                                    val landed = move(dragIndex, dragIndex + steps)
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
                                    Icons.Filled.DragHandle, contentDescription = "Reorder this row",
                                    tint = if (gripFocused) Color.Black else if (raised) TextC else FaintC,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(row.name, color = if (on) TextC else MutedC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.sub, color = if (on) MutedC else FaintC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .background(if (on) Red else Color.Transparent)
                                .border(if (on) 0.dp else 1.5.dp, if (on) Color.Transparent else Line2, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Filled.Check, contentDescription = "Shown on Home", tint = OnAccent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
