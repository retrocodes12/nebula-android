package com.nuvio.ckplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay

/** One add-on on the launch card: still being asked ([count] null), or how many rows it gave (0 = none). */
internal data class ApChip(val name: String, val count: Int?)

/** What the launch card shows. [forUrl] = the start it waits on (null while the add-ons are asked); [at] = uptime it went up. */
internal data class ApCard(
    val title: String, val sub: String?, val art: String?, val logo: String?,
    val line: String, val chips: List<ApChip> = emptyList(), val forUrl: String? = null,
    val at: Long = android.os.SystemClock.uptimeMillis(),
)

/**
 * One autoplay run (main thread only): the title's rows as the add-ons answer — asked in a scope of the run's own, so the
 * add-ons still answering after the pick keep feeding the queue a failed start draws from — the rows that did not play,
 * the tries spent, and the launch card (drawn by AppRoot over everything until the first picture).
 */
internal object AutoplayRun {
    var key: String? = null; private set
    var rows: List<Pair<StreamItem, Addon>> = emptyList(); private set
    private var runtime: String? = null
    val failed = HashSet<String>()
    var tries = 0; private set
    /** The row the run started (the AppRoot play it opens is an autoplay one). */
    var playing: String? = null
    var card by mutableStateOf<ApCard?>(null)
    /** The card is up over the streams page, still choosing: a key press hides it and is swallowed ([swallow]). */
    var asking = false
    private var job: Job? = null
    private var base: ApCard? = null
    private var swallowUp = -1

    /** A new run for [item]: the previous one stops. Returns the scope its add-on requests run in. */
    fun begin(item: MetaItem): CoroutineScope {
        end()
        key = item.type + "\n" + item.id
        runtime = item.runtime
        val parts = item.name.split(" · ")
        val tt = Regex("""^tt\d{5,10}""").find(item.id)?.value
        val c = ApCard(
            title = parts[0], sub = parts.drop(1).joinToString(" · ").ifEmpty { null },
            art = item.background ?: tt?.let { "https://images.metahub.space/background/large/$it/img" } ?: item.poster,
            // a series' logo: the episode's title page hands the show's art, so the show's id is the one to ask for
            logo = item.logo ?: tt?.let { "https://images.metahub.space/logo/medium/$it/img" },
            line = Autoplay.askLine(0, emptyList()),
        )
        base = c; card = c; asking = true
        return CoroutineScope(Dispatchers.Main.immediate + SupervisorJob().also { job = it })
    }
    /** The run is over: its requests stop, the card goes. */
    fun end() {
        job?.cancel(); job = null
        key = null; rows = emptyList(); failed.clear(); tries = 0; playing = null
        card = null; asking = false; base = null; swallowUp = -1
    }
    /** The run for this title, if it is the one going (a player leaving ends only its own). */
    fun endFor(type: String?, id: String?) { if (key != null && key == "$type\n$id") end() }
    fun offer(sections: List<Pair<Addon, List<StreamItem>>>) { rows = sections.flatMap { (a, l) -> l.map { it to a } } }
    fun ranked(cfg: Autoplay.Cfg, keepWaiting: Boolean = false): List<Pair<StreamItem, Addon>> =
        Autoplay.rank(rows, cfg, { StreamBadges.slow(it, runtime) }, { StreamBadges.stutter(it) }, keepWaiting)

    /** The add-ons are still being asked: the card's line and chips. */
    fun askCard(found: Int, chips: List<ApChip>) {
        val b = base ?: return
        if (!asking) return
        card = b.copy(line = Autoplay.askLine(found, chips.filter { it.count == null }.map { it.name }), chips = chips)
    }
    /** A start under way: "Starting 4K · Comet · instant" (or why the last one gave way), until its first picture. */
    fun startCard(s: StreamItem, from: Addon, why: String?) {
        val b = base ?: return
        asking = false
        card = b.copy(line = Autoplay.startLine(s, from.name, why), chips = card?.chips.orEmpty(), forUrl = s.url,
            at = android.os.SystemClock.uptimeMillis())
    }
    /** The start [url] showed its picture (or gave up): the card steps aside. */
    fun started(url: String) { if (card?.forUrl == url) card = null }

    /**
     * The next row after the one that did not start ([cur]): the ranked queue as it stands now (late add-ons included),
     * never a row that failed, within "If it won't start". Null = none left — the player's own handling takes over.
     */
    fun next(cur: String, cfg: Autoplay.Cfg): Pair<StreamItem, Addon>? {
        failed.add(cur)
        if (tries >= cfg.retry) return null
        val n = Autoplay.next(ranked(cfg), cur, failed) ?: return null
        tries++
        playing = n.first.url
        return n
    }

    /** MainActivity.dispatchKeyEvent: while the card chooses, a key (not Back, not the volume) only hides it. */
    fun swallow(e: android.view.KeyEvent): Boolean {
        val k = e.keyCode
        if (e.action == android.view.KeyEvent.ACTION_UP && k == swallowUp) { swallowUp = -1; return true }
        if (!asking || e.action != android.view.KeyEvent.ACTION_DOWN || e.repeatCount > 0) return false
        if (k == android.view.KeyEvent.KEYCODE_BACK || k == android.view.KeyEvent.KEYCODE_ESCAPE ||
            k == android.view.KeyEvent.KEYCODE_VOLUME_UP || k == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
            k == android.view.KeyEvent.KEYCODE_VOLUME_MUTE) return false
        asking = false; card = null; swallowUp = k
        return true
    }
}

/** The player's Streams list: this title's rows best first (15 at most), the playing one marked. */
internal fun autoplayMenu(cur: String, cfg: Autoplay.Cfg, rows: List<Pair<StreamItem, Addon>>): List<Pair<String, String>> {
    val ranked = rows.take(400).let { Autoplay.rank(it, cfg, { false }, { false }, keepWaiting = true) }.take(15)
    fun base(s: StreamItem, a: Addon): String {
        val bytes = StreamBadges.sizeBytes(s.videoSize, s.name + "\n" + s.title)
        return listOf(
            Autoplay.qualityWord(s), a.name, if (Autoplay.instant(s) > 0) "Instant" else "",
            when { bytes >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9); bytes > 0 -> "${bytes / 1_000_000} MB"; else -> "" },
        ).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    val labels = ranked.map { (s, a) -> base(s, a) }
    return ranked.mapIndexed { i, (s, _) ->
        var label = labels[i].ifEmpty { "Stream" }
        // look-alikes say where they come from
        if (labels.count { it == labels[i] } > 1) runCatching { java.net.URI(s.url).host?.removePrefix("www.") }.getOrNull()
            ?.takeIf { it.isNotEmpty() }?.let { label += " · $it" }
        label += when {
            s.url != cur && s.url in AutoplayRun.failed -> " — did not play"
            Autoplay.debrid(s) == Autoplay.Debrid.WAIT -> " — not ready yet"
            else -> ""
        }
        label to s.url
    }
}

/** The Streams list as a sheet; [rows] null = still being asked. */
@Composable
internal fun AutoplayStreamsSheet(cur: String, rows: List<Pair<StreamItem, Addon>>?, onPick: (Pair<StreamItem, Addon>) -> Unit, onDismiss: () -> Unit) {
    val options = if (rows == null) listOf("Looking for streams…" to "") else autoplayMenu(cur, Autoplay.cfg(), rows)
        .ifEmpty { listOf("No other streams for this title." to "") }
    PickSheet("Streams", options, cur, onPick = { u ->
        if (u.isEmpty() || u == cur) return@PickSheet
        rows?.firstOrNull { it.first.url == u }?.let(onPick)
    }, onDismiss = onDismiss)
}

/**
 * The launch card: the title over its backdrop, who is being asked, then what starts — full screen, over the list and
 * then over the player until its first picture. It takes no focus; on the player any key sends it away (the streams page
 * swallows the key instead, [AutoplayRun.swallow]). On a phone a tap does the same and goes no further — it would
 * otherwise land on the list or the player chrome underneath; the streams page reads the touch as the viewer taking over.
 */
@Composable
internal fun AutoplayCardHost() {
    val c = AutoplayRun.card ?: return
    LaunchedEffect(c) {
        if (c.forUrl == null) return@LaunchedEffect
        while (true) {
            delay(150)
            if (KeyWatch.anyDownAt > c.at) { if (AutoplayRun.card === c) AutoplayRun.card = null; return@LaunchedEffect }
        }
    }
    val ctx = LocalContext.current
    val touch = remember(ctx) { !Account.isTv(ctx) }
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val tap = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Bg).then(if (touch) Modifier.clickable(tap, indication = null) {
        AutoplayRun.asking = false; if (AutoplayRun.card === c) AutoplayRun.card = null
    } else Modifier)) {
        if (c.art != null) AsyncImage(model = c.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF2000000), 0.55f to Color(0x8C000000), 1f to Color(0x33000000))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6000000))))
        Column(Modifier.align(Alignment.BottomStart)
            .padding(start = if (narrow) 24.dp else 64.dp, end = if (narrow) 24.dp else 64.dp, bottom = if (narrow) 40.dp else 56.dp)
            .widthIn(max = 760.dp)) {
            var logoOk by remember(c.logo) { mutableStateOf(false) }
            Box(contentAlignment = Alignment.BottomStart) {
                if (c.logo != null) AsyncImage(
                    model = c.logo, contentDescription = c.title, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                    modifier = Modifier.height(if (narrow) 72.dp else 110.dp).widthIn(max = if (narrow) 300.dp else 460.dp).alpha(if (logoOk) 1f else 0f),
                    // a wide wordmark only — a square badge reads worse than the title in type
                    onSuccess = { st -> val sz = st.painter.intrinsicSize; logoOk = sz.height > 0f && sz.width / sz.height >= 1.6f },
                )
                if (!logoOk) Text(c.title, color = TextC, fontFamily = Sans, fontSize = if (narrow) 30.sp else 40.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (c.sub != null) Text(c.sub, color = Color(0xB8EBEBF5), fontFamily = Sans, fontSize = 17.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = TextC, strokeWidth = 2.dp, trackColor = Color(0x2EFFFFFF))
                Spacer(Modifier.width(12.dp))
                Text(c.line, color = TextC, fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            val shown = if (narrow) 3 else 6
            if (c.chips.isNotEmpty()) Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.chips.take(shown).forEach { ch -> ApChipView(ch) }
                if (c.chips.size > shown) Text("+${c.chips.size - shown}", color = MutedC, fontFamily = Mono, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun ApChipView(ch: ApChip) {
    val pill = RoundedCornerShape(50)
    Row(
        Modifier.border(1.dp, if (ch.count == null) Color(0x33FFFFFF) else LineC, pill).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // a dot that is lit while the add-on answers, solid once it has rows, hollow when it had none
        Box(Modifier.size(7.dp).background(when {
            ch.count == null -> Color(0x99FFFFFF); ch.count > 0 -> TextC; else -> Color.Transparent
        }, CircleShape).border(1.dp, Color(0x66FFFFFF), CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(ch.name, color = if (ch.count == 0) MutedC else TextC, fontFamily = Sans, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        if (ch.count != null) Text(if (ch.count > 0) ch.count.toString() else "none", color = MutedC, fontFamily = Mono,
            fontSize = 12.sp, modifier = Modifier.padding(start = 7.dp))
    }
}
