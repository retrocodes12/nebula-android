@file:OptIn(ExperimentalComposeUiApi::class)

package com.nuvio.ckplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale

/** A subtitle an add-on offered, and the add-on's own name — the badge on its card. */
internal data class AddonSub(val track: SubTrack, val source: String)

/** A text track the stream carries: embedded in the file, or side-loaded with the stream. */
internal class EmbeddedSub(val group: Tracks.Group, val index: Int, val format: Format, val selected: Boolean)

/** One card of the middle column: where it comes from, what it is, whether it is showing, how to switch it on. */
private class SubChoice(val badge: String, val label: String, val active: Boolean, val apply: () -> Unit)

private val Scrim = Color(0xB8000000)          // rgba(0,0,0,.72), as the web's #subPanel
private val Hair = Color(0x1AFFFFFF)
private val Fill = Color(0x1FFFFFFF)
private val Label2 = Color(0x99EBEBF5)
private val Ink2 = Color(0xA8EBEBF5)
private val Card = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(50)

/**
 * The Subtitles panel: a dim over the still-playing video, "Subtitles" top-left, three hairline-
 * separated columns — LANGUAGES (Off, then every language the stream or the add-ons offer, with a
 * count), SUBTITLES (that language's tracks as cards with a "Built in" / add-on-name badge and a
 * check on the one showing; a tap or OK applies it and the panel stays), STYLE (the live preview,
 * timing nudges, the appearance rows, Reset). D-pad moves between columns; Back or Done closes it.
 */
@Composable
internal fun SubtitlesPanel(
    player: Player,
    embedded: List<EmbeddedSub>,
    addonSubs: List<AddonSub>,
    activeAddonSub: String?,          // url of the add-on subtitle that is showing
    streamSource: String?,            // the stream's add-on, for tracks it side-loaded
    searching: Boolean,               // add-ons still answering
    busy: Boolean,                    // a pick is downloading
    offsetMs: Long,
    canShift: Boolean,                // timing only moves add-on subtitles
    onNudge: (Long) -> Unit,
    onResetTiming: () -> Unit,
    onPickAddon: (SubTrack) -> Unit,
    onPickEmbedded: () -> Unit,       // a stream track took over from the add-on pick
    onOff: () -> Unit,
    onClose: () -> Unit,
) {
    fun textParams() = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)

    // every choice by language: the stream's own tracks first, then what the add-ons offered
    val byLang = LinkedHashMap<String, MutableList<SubChoice>>()
    embedded.forEach { e ->
        val f = e.format
        val id = f.id ?: ""
        if (id == "addon-pick") return@forEach                 // the add-on card below stands for it
        val lang = langLabel(f.language ?: "und")
        var label = f.label?.takeIf { it.isNotBlank() } ?: lang
        if ((f.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) label += " · SDH"
        if ((f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) label += " · Forced"
        val badge = if (id.startsWith("sub:")) (streamSource ?: "Add-on") else "Built in"
        byLang.getOrPut(lang) { mutableListOf() } += SubChoice(badge, label, e.selected) {
            player.trackSelectionParameters = textParams()
                .setOverrideForType(TrackSelectionOverride(e.group.mediaTrackGroup, e.index))
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            onPickEmbedded()
        }
    }
    addonSubs.forEach { a ->
        val lang = langLabel(a.track.lang)
        val list = byLang.getOrPut(lang) { mutableListOf() }
        val nth = list.count { it.badge == a.source } + 1
        list += SubChoice(a.source, if (nth == 1) lang else "$lang · $nth", activeAddonSub == a.track.url) { onPickAddon(a.track) }
    }
    val device = langLabel(Locale.getDefault().language)
    val langs = byLang.keys.sortedWith(compareBy<String>({ it != device }, { it != "English" }, { it }))
    val anyOn = byLang.values.any { l -> l.any { it.active } }
    val current = byLang.entries.firstOrNull { e -> e.value.any { it.active } }?.key
    var lang by remember { mutableStateOf(current) }
    val initial = remember { current }
    // opening lands on the language that is showing (or Off) — for a remote; a finger needs no focus
    val firstFocus = remember { FocusRequester() }
    val keys = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(Unit) { if (keys) runCatching { firstFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Scrim)
            // a tap on the dim closes; it is not focusable, so a remote never lands on it
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 480.dp
            val narrow = maxWidth < 640.dp
            Column(
                Modifier.fillMaxSize()
                    .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 14.dp else 28.dp)
                    .pointerInput(Unit) { detectTapGestures { } }                 // taps inside stay inside
                    // the D-pad stays in the panel: exit must be declared BEFORE focusGroup(), because
                    // focusProperties only reach the focus targets to their right in the chain
                    .focusProperties { exit = { FocusRequester.Cancel } }
                    .focusGroup(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Subtitles", color = Color.White, fontSize = if (compact) 24.sp else 30.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.weight(1f))
                    GlassPill("Done", onClick = onClose)
                }
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(top = if (compact) 10.dp else 18.dp)
                        // a portrait phone is narrower than three columns: they scroll sideways, still three
                        .then(if (narrow) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                ) {
                    val w1 = if (narrow) Modifier.width(220.dp) else Modifier.weight(0.9f)
                    val w2 = if (narrow) Modifier.width(300.dp) else Modifier.weight(1.3f)
                    val w3 = if (narrow) Modifier.width(290.dp) else Modifier.weight(1.1f)
                    LanguagesColumn(
                        w1.fillMaxHeight(), langs, byLang.mapValues { it.value.size }, lang, initial, firstFocus,
                        onSelect = { lang = it },
                        onOff = {
                            player.trackSelectionParameters = textParams().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            onOff()
                            lang = null
                        },
                    )
                    VDivider()
                    TracksColumn(w2.fillMaxHeight(), lang, lang?.let { byLang[it] } ?: emptyList(), searching, busy, anyOn)
                    VDivider()
                    StyleColumn(w3.fillMaxHeight(), offsetMs, canShift, onNudge, onResetTiming)
                }
            }
        }
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Hair))
}

@Composable
private fun Note(text: String, top: Dp = 0.dp) {
    Text(text, color = Label2, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = top, end = 4.dp))
}

/** Off, then the languages with a count badge each; the chosen one fills the middle column. */
@Composable
private fun LanguagesColumn(
    modifier: Modifier, langs: List<String>, counts: Map<String, Int>, selected: String?,
    initial: String?, firstFocus: FocusRequester, onSelect: (String) -> Unit, onOff: () -> Unit,
) {
    Column(modifier.padding(end = 12.dp)) {
        Eyebrow("Languages", color = Label2)
        Column(Modifier.padding(top = 8.dp).verticalScroll(rememberScrollState())) {
            PanelRow("Off", null, selected == null, if (initial == null) Modifier.focusRequester(firstFocus) else Modifier, onOff)
            langs.forEach { l ->
                PanelRow(l, counts[l], selected == l, if (initial == l) Modifier.focusRequester(firstFocus) else Modifier) { onSelect(l) }
            }
        }
    }
}

@Composable
private fun PanelRow(label: String, count: Int?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ink = if (focused) Color.Black else Color.White
    Row(
        modifier.fillMaxWidth()
            // focus is the tvOS white lozenge; the chosen language keeps a quiet fill
            .background(if (focused) Color.White else if (selected) Fill else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, color = ink, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (count != null) Text(
            count.toString(), color = if (focused) Color(0x99000000) else Ink2, fontFamily = Mono, fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp)
                .border(1.dp, if (focused) Color(0x33000000) else Hair, PillShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The chosen language's tracks as cards; a tap or OK switches one on and the panel stays. */
@Composable
private fun TracksColumn(modifier: Modifier, lang: String?, choices: List<SubChoice>, searching: Boolean, busy: Boolean, anyOn: Boolean) {
    Column(modifier.padding(horizontal = 12.dp)) {
        Eyebrow("Subtitles" + (if (busy) " · loading…" else ""), color = Label2)
        Column(
            Modifier.padding(top = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                lang == null -> Note(if (anyOn) "Choose a language on the left." else "Subtitles are off. Choose a language to turn them on.")
                choices.isEmpty() -> Note(if (searching) "Searching add-ons…" else "Nothing found in $lang.")
                else -> choices.forEach { c -> TrackCard(c) }
            }
            if (lang != null && choices.isNotEmpty() && searching) Note("Searching add-ons for more…")
        }
    }
}

@Composable
private fun TrackCard(c: SubChoice) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ink = if (focused) Color.Black else Color.White
    Row(
        Modifier.fillMaxWidth()
            .background(if (focused) Color.White else Fill, Card)
            .border(1.dp, if (focused) Color.Transparent else Hair, Card)
            .clickable(interactionSource = interaction, indication = null) { c.apply() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                c.badge.uppercase(), color = if (focused) Color(0x99000000) else Label2, fontFamily = Mono, fontSize = 10.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.border(1.dp, if (focused) Color(0x33000000) else Hair, PillShape).padding(horizontal = 7.dp, vertical = 2.dp),
            )
            Text(
                c.label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (c.active) Icon(Icons.Filled.Check, contentDescription = "Showing", tint = ink, modifier = Modifier.padding(start = 10.dp).size(20.dp))
    }
}

/** A small mono nudge (−0.5 / +0.1 …) for the timing block. */
@Composable
private fun Nudge(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        label, color = if (focused) Color.Black else Color.White, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(if (focused) Color.White else Fill, PillShape)
            .border(1.dp, if (focused) Color.Transparent else Hair, PillShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Preview, timing, the appearance rows, Reset — the whole column scrolls. */
@Composable
private fun StyleColumn(modifier: Modifier, offsetMs: Long, canShift: Boolean, onNudge: (Long) -> Unit, onResetTiming: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_EXPRESSION") SubStyle.version.value   // recompose on cycle
    val style = SubStyle.get(ctx)
    Column(modifier.padding(start = 12.dp)) {
        Eyebrow("Style", color = Label2)
        Column(Modifier.padding(top = 8.dp).verticalScroll(rememberScrollState())) {
            SubStylePreview(style)
            Eyebrow("Timing", Modifier.padding(top = 14.dp), Label2)
            if (canShift) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fmtSubOffset(offsetMs), color = Color.White, fontFamily = Mono, fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(end = 12.dp),
                    )
                    GlassPill("Back in sync", on = offsetMs == 0L, onClick = onResetTiming)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Nudge("−0.5") { onNudge(-500) }
                    Nudge("−0.1") { onNudge(-100) }
                    Nudge("+0.1") { onNudge(100) }
                    Nudge("+0.5") { onNudge(500) }
                }
                Note("If the words arrive before the voices, choose +.", top = 6.dp)
            } else Note("Timing can be nudged for add-on subtitles; the stream's own tracks cannot be shifted.", top = 6.dp)
            Eyebrow("Appearance", Modifier.padding(top = 14.dp), Label2)
            SubStyleRows(ctx, style, Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
                GlassPill("Reset to defaults", onClick = { SubStyle.reset(ctx) })
            }
        }
    }
}
