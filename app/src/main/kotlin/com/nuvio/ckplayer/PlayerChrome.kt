package com.nuvio.ckplayer

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple TV player layout: frosted-glass circles for back, info, PiP, fullscreen and the
 * centre transport; the title with its mono source line and episode line over a thick
 * fully-rounded scrubber; glass time pills at both ends of the bar; and ONE centred glass
 * toolbar of icon+label items (Next · Subtitles · Audio · Quality · Speed · Sleep · Party)
 * under it. A clock with the end time sits top-right. Nuvio's layout, Nebula's material.
 */

private val Glass = Color(0x6B505058)
private val GlassHot = Color(0x8C6E6E78)
internal val Ink = Color.White
internal val DimInk = Color(0xA8EBEBF5)
internal val BarGlass = Color(0xC72C2C2E)      // the web player's .qmenu material, rgba(44,44,46,.78)
internal val Hairline = Color(0x1AFFFFFF)
internal val Pill = RoundedCornerShape(50)

@Composable
private fun GlassCircle(
    icon: ImageVector,
    label: String,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    on: Boolean = false,                 // a lit toggle (the info HUD) inverts to white
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = if (on) (if (focused) Color.White else Color(0xEBFFFFFF)) else if (focused) GlassHot else Glass
    Box(
        Modifier.size(size)
            .background(bg, CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (on) Color.Black else Ink, modifier = Modifier.size(iconSize))
    }
}

@Composable
internal fun GlassPill(label: String, value: String? = null, on: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // an "on" pill inverts, the way the web player's round buttons do
    val bg = if (on) (if (focused) Color.White else Color(0xEBFFFFFF)) else if (focused) GlassHot else Glass
    val ink = if (on) Color.Black else Ink
    Row(
        modifier.background(bg, Pill)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (value != null) Text(
            value, color = if (on) Color(0x99000000) else DimInk, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp), maxLines = 1,
        )
    }
}

/** One toolbar item: an outline icon and a short label (+ a dim value). Focus, like "on",
    inverts it to a white pill with black ink — what the web player's .pui-btn:focus does. */
@Composable
private fun ToolItem(
    icon: ImageVector?, label: String, value: String? = null, on: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val lit = focused || on
    val ink = if (lit) Color.Black else Ink
    Row(
        modifier.background(if (lit) Color.White else Color.Transparent, Pill)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (value != null) Text(
            value, color = if (lit) Color(0x99000000) else DimInk, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp), maxLines = 1,
        )
    }
}

/** A glass pill of tabular numerals at either end of the scrubber. */
@Composable
private fun TimePill(text: String, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text, color = if (focused) Color.Black else Ink, fontFamily = Mono, fontSize = 12.sp,
        fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.background(if (focused) Color.White else Glass, Pill)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

/** The title, the mono source line ("1080P · TORRENTIO") and the episode line ("S1 E1 · Pilot"). */
@Composable
private fun TitleBlock(title: String, sourceLine: String?, episodeTag: String?, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title, color = Ink, fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (!sourceLine.isNullOrEmpty()) Text(
            sourceLine.uppercase(), color = Color(0x99EBEBF5), fontFamily = Mono, fontSize = 11.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (!episodeTag.isNullOrEmpty()) Text(
            episodeTag, color = DimInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The one toolbar: a pill of glass, centred when it fits, scrolling sideways when a phone is
    narrower than its items (D-pad focus drags it along). */
@Composable
private fun PlayerToolbar(
    hasNext: Boolean, showSubtitles: Boolean, showAudio: Boolean,
    qualityLabel: String?, speedLabel: String, sleepLabel: String?, partyActive: Boolean,
    subtitlesFocus: FocusRequester?,
    onNext: () -> Unit, onSubtitles: () -> Unit, onAudio: () -> Unit, onQuality: () -> Unit,
    onSpeedCycle: () -> Unit, onSleep: () -> Unit, onParty: () -> Unit, onInvite: () -> Unit, onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(Pill).background(BarGlass, Pill).border(1.dp, Hairline, Pill)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasNext) ToolItem(Icons.Filled.SkipNext, "Next ›", onClick = onNext)
            if (showSubtitles) ToolItem(
                Icons.Outlined.Subtitles, "Subtitles",
                modifier = if (subtitlesFocus != null) Modifier.focusRequester(subtitlesFocus) else Modifier,
                onClick = onSubtitles,
            )
            if (showAudio) ToolItem(Icons.Outlined.Audiotrack, "Audio", onClick = onAudio)
            if (qualityLabel != null) ToolItem(Icons.Outlined.HighQuality, "Quality", qualityLabel, onClick = onQuality)
            ToolItem(Icons.Outlined.Speed, "Speed", speedLabel, onClick = onSpeedCycle)
            ToolItem(Icons.Outlined.Bedtime, "Sleep", sleepLabel, on = sleepLabel != null, onClick = onSleep)
            ToolItem(Icons.Outlined.Groups, if (partyActive) "Leave party" else "Party", onClick = onParty)
            if (partyActive) {
                ToolItem(Icons.Outlined.PersonAdd, "Invite", onClick = onInvite)
                // the reactions are the party's own vocabulary, sent on the wire as they are
                listOf("\uD83D\uDC4D", "\uD83D\uDE02", "\u2764\uFE0F", "\uD83D\uDD25").forEach { e ->
                    ToolItem(null, e, onClick = { onReact(e) })
                }
            }
        }
    }
}

/**
 * What the screen says after a moment paused: a kicker (Paused / Live · Paused /
 * Finished), the title, the episode line, a short synopsis and a row of facts —
 * time left, when it ends, how far behind live, what plays next. Mirrors the
 * shared player's #pauseBoard.
 */
@Composable
internal fun PauseBoard(
    visible: Boolean,
    kicker: String,
    title: String,
    sub: String?,
    desc: String?,
    meta: List<Pair<String, Boolean>>,   // text, isNext
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(Modifier.fillMaxWidth(0.62f)) {
            Text(kicker, color = DimInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
            Text(
                title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp,
                letterSpacing = (-0.6).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (!sub.isNullOrEmpty()) Text(
                sub, color = DimInk, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
            if (!desc.isNullOrEmpty()) Text(
                desc, color = Color(0xCCEBEBF5), fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 3,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
            )
            if (meta.isNotEmpty()) Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                meta.forEach { (t, next) ->
                    Text(
                        t, color = if (next) Color.Black else Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(if (next) Color(0xEBFFFFFF) else Glass, Pill)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Picture, codec, rates, buffer, dropped frames — read like a camera HUD, not a debug dump. */
@Composable
internal fun PlaybackInfoHud(rows: List<InfoRow>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(270.dp)
            .background(Color(0xD91C1C1E), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("PLAYBACK INFO", color = Color(0x8CEBEBF5), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 8.dp else 5.dp), verticalAlignment = Alignment.Top) {
                Text(r.k, color = DimInk, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                // a sentence ("Unavailable — host refused …") wraps instead of running off the card
                Text(
                    r.v, color = if (r.warn) Color(0xFFFFB340) else Ink, fontSize = 12.5.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1.5f).padding(start = 8.dp),
                )
            }
        }
        if (rows.isEmpty()) Text("Waiting for the stream…", color = DimInk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun TitleCardChrome(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    isLive: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    episodeTag: String?,
    qualityLabel: String?,
    speedLabel: String,
    partyActive: Boolean,
    partyBadge: String?,
    hasNext: Boolean,
    canPip: Boolean,
    showSubtitles: Boolean,
    showAudio: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    onParty: () -> Unit,
    onInvite: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onSubtitles: () -> Unit,
    onSubStyle: () -> Unit = {},        // kept for callers; Style lives in the Subtitles panel now
    onAudio: () -> Unit,
    onQuality: () -> Unit,
    onSpeedCycle: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
    dimTitle: Boolean = false,          // the pause board is saying it already
    infoOn: Boolean = false,
    onInfo: () -> Unit = {},
    showTiming: Boolean = false,        // kept for callers; Timing lives in the Subtitles panel now
    onTiming: () -> Unit = {},
    sleepLabel: String? = null,         // "38 min" / "End of episode" while a sleep timer is set
    onSleep: () -> Unit = {},
    sourceLine: String? = null,         // "1080p · Torrentio" — the playing stream's signature
    clockLine: String? = null,          // "9:41 pm · Ends 11:12 pm" (just the clock on live)
    liveOffsetMs: Long = 0L,            // how far behind the live edge, for the left pill
    subtitlesFocus: FocusRequester? = null,   // so the panel can hand focus back to its opener
    scrubFrame: State<Bitmap?>? = null,       // the scrub preview's frame for the position being previewed
    onScrub: (Long?) -> Unit = {},            // a preview position is up (finger or remote); null when it ends
) {
    // remaining ↔ total on the right pill, for the sitting (outside the fade, so a hidden chrome forgets nothing)
    var showTotal by remember { mutableStateOf(false) }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // a phone on its side has no room for a title above the bar: it sits beside Back instead
            val compact = maxHeight < 480.dp
            val narrow = maxWidth < 600.dp
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(0f to Color(0x8C000000), 1f to Color(0x00000000))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (compact) 200.dp else 260.dp)
                .background(Brush.verticalGradient(0f to Color(0x00000000), 1f to Color(0xC7000000))))

            // top: back · (the title, on a short screen) · clock · badges · info · window controls
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                if (compact) TitleBlock(
                    title, sourceLine, episodeTag, compact = true,
                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp).alpha(if (dimTitle) 0f else 1f),
                )
                // a narrow screen keeps the clock and drops the end time; the clock gives way, never the round buttons
                val clock = if (clockLine != null && narrow) clockLine.substringBefore(" · ") else clockLine
                if (clock != null) Text(
                    clock, color = DimInk, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                    modifier = (if (compact) Modifier else Modifier.weight(1f)).padding(end = 14.dp),
                ) else if (!compact) Spacer(Modifier.weight(1f))
                partyBadge?.let {
                    Text(
                        it, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                            .background(Color.White, Pill)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (isLive) Text(
                    "LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 12.dp).background(Color(0xFFE50914), Pill).padding(horizontal = 12.dp, vertical = 6.dp),
                )
                GlassCircle(Icons.Outlined.Info, "Playback info", on = infoOn, onClick = onInfo)
                Spacer(Modifier.width(12.dp))
                if (canPip) {
                    GlassCircle(Icons.Filled.PictureInPictureAlt, "Picture-in-picture", onClick = onPip)
                    Spacer(Modifier.width(12.dp))
                }
                GlassCircle(Icons.Filled.Fullscreen, "Fullscreen", onClick = onFullscreen)
            }

            // centre transport
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(Icons.Filled.Replay10, "Back 10 seconds", size = 56.dp, iconSize = 26.dp) { onSeekBy(-10_000) }
                Spacer(Modifier.width(34.dp))
                GlassCircle(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play/Pause", size = 80.dp, iconSize = 40.dp, onClick = onPlayPause,
                )
                Spacer(Modifier.width(34.dp))
                GlassCircle(Icons.Filled.Forward10, "Forward 10 seconds", size = 56.dp, iconSize = 26.dp) { onSeekBy(10_000) }
            }

            // bottom: the title block (tall screens), the scrubber, the time pills, the toolbar
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (!compact) TitleBlock(
                    title, sourceLine, episodeTag, compact = false,
                    modifier = Modifier.fillMaxWidth(0.72f).padding(bottom = 2.dp).alpha(if (dimTitle) 0f else 1f),
                )

                // the scrubber with its scrub preview (ScrubPreview.kt): drag or ←/→ show a ghost knob and a tip
                Scrubber(
                    positionMs = positionMs, durationMs = durationMs, bufferedMs = bufferedMs,
                    isLive = isLive, liveOffsetMs = liveOffsetMs, frame = scrubFrame,
                    onSeekBy = onSeekBy, onSeekTo = onSeekTo, onScrub = onScrub,
                )
                // elapsed at the left end, remaining (or, on a tap, the total) at the right — glass pills
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimePill(
                        when {
                            !isLive -> fmtTime(positionMs)
                            liveOffsetMs > 12_000 -> "−" + fmtTime(liveOffsetMs) + " behind live"
                            else -> "At the live edge"
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    if (isLive) TimePill("LIVE")
                    else TimePill(
                        if (showTotal) fmtTime(durationMs) else "−" + fmtTime((durationMs - positionMs).coerceAtLeast(0L))
                    ) { showTotal = !showTotal }
                }
                PlayerToolbar(
                    hasNext = hasNext, showSubtitles = showSubtitles, showAudio = showAudio,
                    qualityLabel = qualityLabel, speedLabel = speedLabel, sleepLabel = sleepLabel, partyActive = partyActive,
                    subtitlesFocus = subtitlesFocus,
                    onNext = onNext, onSubtitles = onSubtitles, onAudio = onAudio, onQuality = onQuality,
                    onSpeedCycle = onSpeedCycle, onSleep = onSleep, onParty = onParty, onInvite = onInvite, onReact = onReact,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
