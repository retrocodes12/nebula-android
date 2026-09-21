package com.nuvio.ckplayer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Android TV focus helpers (the 09-22 TV bug hunt). Everything here is inert under a finger: a phone never
 * focuses a card, so none of it changes what a phone shows.
 */

/** Is a remote (a TV, or any D-pad/keyboard now in use) driving? */
@Composable
internal fun remoteMode(): Boolean {
    val ctx = LocalContext.current
    val tv = remember(ctx) { Account.isTv(ctx) }
    return tv || LocalInputModeManager.current.inputMode == InputMode.Keyboard
}

/** The stack entry the composing screen belongs to (AppRoot provides it per screen). */
internal val LocalScreenEntry = staticCompositionLocalOf<Any?> { null }

/**
 * Back returns the remote to what it left. Every screen used to rebuild with focus wherever Compose re-seeded it —
 * usually the rail — so a title opened from the seventh card of the fourth row came back to the top-left. Each
 * returnable item notes itself when it gains focus ([returnTo]); AppRoot's pop() names the entry it returns to;
 * when that screen composes, the noted item claims focus, and the screen's own first-focus claim ([tvFirstFocus])
 * waits for it and steps in only if nothing did.
 */
internal object ReturnFocus {
    private val last = HashMap<Any, String>()
    private var since = 0L
    /** The entry Back (or a removal) is handing focus back to — until its item takes focus, anything navigates, or
        three seconds pass: a hand-back whose item never composes must not latch on the screen. */
    var returningTo by mutableStateOf<Any?>(null)
        private set
    fun aim(entry: Any?) { returningTo = entry; since = android.os.SystemClock.uptimeMillis() }
    fun clear() { returningTo = null }
    /** Is a hand-back to [entry] live? */
    fun pending(entry: Any?): Boolean =
        entry != null && returningTo == entry && android.os.SystemClock.uptimeMillis() - since < 3000
    fun note(entry: Any, key: String) {
        // while a hand-back is live here, whatever focus passes through on the way (the re-seed after a removal, a
        // screen's first frames) must not overwrite the place being handed back to
        if (pending(entry)) return
        last[entry] = key
    }
    fun keyFor(entry: Any): String? = last[entry]
    /** Is the screen composing now one Back returned to? Read once, at its first composition. */
    fun backTo(entry: Any?): Boolean = pending(entry)
    /** …and with an item noted there to hand focus back to. */
    fun returning(entry: Any?): Boolean = pending(entry) && last[entry] != null
}

/** Does the TV's side rail hold focus? (AppRoot keeps it.) A screen's fallback landing never pulls focus off it. */
internal object RailFocus {
    var has = false
}

/**
 * For a screen's own first-focus claim: did Back return here with a place noted? That item gets ~20 frames to take
 * focus; true when it did (leave focus alone), false when there was nothing to wait for or it never came (the item
 * is outside what the list composed) — then the screen lands as it would on a fresh visit.
 */
internal suspend fun yieldToReturn(entry: Any?): Boolean {
    if (!ReturnFocus.returning(entry)) return false
    repeat(20) {
        withFrameNanos {}
        if (ReturnFocus.returningTo != entry) return true
    }
    ReturnFocus.clear()
    return false
}

/**
 * Hand focus to the item keyed [key] on this screen as if Back had returned to it — for a removal, whose card takes
 * focus with it when it goes (the neighbour is named before the card is removed).
 */
internal fun ReturnFocus.handTo(entry: Any?, key: String) {
    if (entry == null) return
    clear()                 // so the note below is not refused as passing traffic
    note(entry, key)
    aim(entry)
}

/** Mark a focusable as a place Back can return to. [key] must be stable for the item across a rebuild of the screen. */
internal fun Modifier.returnTo(key: String): Modifier = composed {
    val entry = LocalScreenEntry.current
    val remote = remoteMode()
    val req = remember { FocusRequester() }
    // keyed on the hand-back too, so a removal naming this item after composition still reaches it
    if (entry != null) LaunchedEffect(entry, key, remote, ReturnFocus.returningTo == entry) {
        if (!remote || !ReturnFocus.pending(entry) || ReturnFocus.keyFor(entry) != key) return@LaunchedEffect
        // a lazy list composes its items a frame after itself and the screen fades in: retry, as tvFirstFocus does
        repeat(12) {
            withFrameNanos {}
            if (runCatching { req.requestFocus() }.getOrDefault(false)) { ReturnFocus.clear(); return@LaunchedEffect }
        }
    }
    this.onFocusChanged { if (entry != null && it.hasFocus) ReturnFocus.note(entry, key) }.focusRequester(req)
}

/**
 * A scroll or list position kept across the screen leaving composition, restored only when Back returns to it
 * (a fresh visit starts at the top as it always has). Keyed by [key] within the screen's entry.
 */
private object KeptPlace {
    val scroll = HashMap<String, Int>()
    val list = HashMap<String, Pair<Int, Int>>()
}

private fun placeKey(entry: Any?, key: String) = (entry?.hashCode() ?: 0).toString() + "/" + key

@Composable
internal fun rememberKeptScroll(key: String): ScrollState {
    val entry = LocalScreenEntry.current
    val k = placeKey(entry, key)
    val back = remember { ReturnFocus.backTo(entry) }
    val st = rememberScrollState(if (back) KeptPlace.scroll[k] ?: 0 else 0)
    DisposableEffect(k) { onDispose { KeptPlace.scroll[k] = st.value } }
    return st
}

/** [ready]: the rows the kept place points into exist — a lazy list clamps a start index past what it holds (a page
    that rebuilds with only skeletons would land on those), so the place is put back once they are there. */
@Composable
internal fun rememberKeptList(key: String, ready: Boolean = true): LazyListState {
    val entry = LocalScreenEntry.current
    val k = placeKey(entry, key)
    val at = remember { if (ReturnFocus.backTo(entry)) KeptPlace.list[k] else null }
    val st = rememberLazyListState()
    var placed by remember { mutableStateOf(at == null) }
    LaunchedEffect(ready, placed) {
        if (placed || !ready || at == null) return@LaunchedEffect
        runCatching { st.scrollToItem(at.first, at.second) }
        placed = true
    }
    // a page left before it was put back keeps the place it was given, not the top it briefly showed
    DisposableEffect(k) {
        onDispose { KeptPlace.list[k] = if (placed) st.firstVisibleItemIndex to st.firstVisibleItemScrollOffset else at ?: (0 to 0) }
    }
    return st
}

@Composable
internal fun rememberKeptGrid(key: String): LazyGridState {
    val entry = LocalScreenEntry.current
    val k = placeKey(entry, key)
    val back = remember { ReturnFocus.backTo(entry) }
    val at = if (back) KeptPlace.list[k] else null
    val st = rememberLazyGridState(at?.first ?: 0, at?.second ?: 0)
    DisposableEffect(k) { onDispose { KeptPlace.list[k] = st.firstVisibleItemIndex to st.firstVisibleItemScrollOffset } }
    return st
}

/**
 * A text field on a TV types only after OK. Compose starts the keyboard the moment a writable field takes focus,
 * and on a TV the keyboard then covers the screen and takes the D-pad — so arriving on Search, or merely walking
 * past Party name in Settings, threw the keyboard up. Read-only until OK (Compose starts the keyboard when the
 * field turns writable while focused), and read-only again once focus leaves; Compose's own D-pad handling still
 * walks focus out of a field. A phone is untouched.
 */
internal class TvTyping(val readOnly: Boolean, val modifier: Modifier)

/** A form's Next (the keyboard's own key) carries typing into the next field — it is the keyboard moving on, not the
    remote walking past, so that field opens writable and the keyboard stays up. */
internal object TypingCarry {
    var until = 0L
    fun next() { until = android.os.SystemClock.uptimeMillis() + 800 }
}

@Composable
internal fun tvTyping(): TvTyping {
    val ctx = LocalContext.current
    val tv = remember(ctx) { Account.isTv(ctx) }
    var editing by remember { mutableStateOf(false) }
    if (!tv) return TvTyping(false, Modifier)
    return TvTyping(
        readOnly = !editing,
        modifier = Modifier
            .onFocusChanged {
                if (!it.hasFocus) editing = false
                else if (!editing && android.os.SystemClock.uptimeMillis() < TypingCarry.until) { TypingCarry.until = 0L; editing = true }
            }
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (editing || !ok) false
                else {
                    if (e.type == KeyEventType.KeyDown) editing = true
                    true
                }
            },
    )
}

/** A ring drawn [gap] outside [shape] — for a control whose own focused look is lost on its fill (white on white). */
internal fun Modifier.outerRing(on: Boolean, shape: Shape, width: Dp = 2.dp, gap: Dp = 3.dp, color: Color = Color.White): Modifier =
    if (!on) this else this.drawWithContent {
        drawContent()
        val w = width.toPx()
        val grow = gap.toPx() + w / 2
        val outline = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
        translate(-grow, -grow) { drawOutline(outline, color, style = Stroke(w)) }
    }

/**
 * The focus ring for controls that had none a sofa can see — Material's own focus is a 10 % tint of the content
 * colour, invisible on white and barely there on glass. Put it BEFORE the control's clickable (or in a Material
 * control's modifier): it watches the focus of what follows. Only under a remote.
 */
internal fun Modifier.focusRing(shape: Shape, landing: Boolean = true): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val remote = remoteMode()
    this.onFocusChanged { focused = it.hasFocus }.outerRing(focused && remote, shape)
        .then(if (landing) Modifier.landingSlot() else Modifier)
}

/**
 * A screen's landing when it names none of its own (issue #1's hunt: Add-ons, Catalog, Episodes, Friends, Profile,
 * every Settings page and Subtitle style opened with nothing lit, so the first press was spent finding focus). The
 * first control composed on the page that registers ([landingSlot]: settings rows, cards, the ringed buttons — never
 * a Back button) holds the slot; AppRoot gives each screen one and, if after a moment nothing on the screen has
 * focus, puts it there. A screen with its own landing (Home, Search, Library, Settings, a title page, Streams) has
 * taken focus by then and is left alone.
 */
internal class LandingSlot {
    val req = FocusRequester()
    var taken = false
    var hasFocus = false
}

internal val LocalLandingSlot = staticCompositionLocalOf<LandingSlot?> { null }

internal fun Modifier.landingSlot(): Modifier = composed {
    val slot = LocalLandingSlot.current
    val mine = remember(slot) {
        if (slot == null || slot.taken) false else { slot.taken = true; true }
    }
    // a registrant that leaves (a row hidden by a switch) frees the slot for whatever composes next
    DisposableEffect(slot, mine) { onDispose { if (mine) slot?.taken = false } }
    if (mine && slot != null) this.focusRequester(slot.req) else this
}

/** AppRoot, per screen: if a remote is in use and after ~40 frames (0.65 s) nothing on the screen is lit — no own
    landing, no return, no press — land on the slot; a page still loading gets its first control when it arrives
    (for up to ~6 s, and only while nothing else has taken focus). A return nobody claimed by then is given up. */
@Composable
internal fun LandingFallback(slot: LandingSlot, entry: Any?) {
    val remote = remoteMode()
    LaunchedEffect(slot, remote) {
        if (!remote) return@LaunchedEffect
        repeat(40) {
            withFrameNanos {}
            if (slot.hasFocus || RailFocus.has) return@LaunchedEffect
        }
        if (entry != null && ReturnFocus.returningTo == entry) ReturnFocus.clear()
        repeat(360) {
            // the viewer on the rail (picking a tab, walking it) is never pulled into the page
            if (slot.hasFocus || RailFocus.has) return@LaunchedEffect
            if (slot.taken && runCatching { slot.req.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos {}
        }
    }
}

/** A settings-style row's lit state: under a remote a lighter fill and a white ring (an 8 % tint did not read from a
    sofa); under a finger it stays the quiet tint it was. */
@Composable
internal fun rowLit(focused: Boolean): Modifier {
    val remote = remoteMode()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    return when {
        focused && remote -> Modifier.background(Color(0x1FFFFFFF), shape).border(2.dp, Color.White, shape)
        focused -> Modifier.background(Color(0x14FFFFFF))
        else -> Modifier
    }
}
