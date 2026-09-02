package com.nuvio.ckplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple TV player layout: frosted-glass circles for back, PiP, fullscreen and
 * the centre transport; the title in clean semibold sans above a thick
 * fully-rounded scrubber; elapsed at the left end, remaining at the right;
 * glass pills for subtitles, audio, quality, speed and the party.
 */

private val Glass = Color(0x6B505058)
private val GlassHot = Color(0x8C6E6E78)
private val Ink = Color.White
private val DimInk = Color(0xA8EBEBF5)

@Composable
private fun GlassCircle(
    icon: ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier.size(size)
            .background(if (focused) GlassHot else Glass, CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Ink, modifier = Modifier.size(iconSize))
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
        modifier.background(bg, RoundedCornerShape(50))
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
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                meta.forEach { (t, next) ->
                    Text(
                        t, color = if (next) Color.Black else Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(if (next) Color(0xEBFFFFFF) else Glass, RoundedCornerShape(50))
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
            .width(250.dp)
            .background(Color(0xD91C1C1E), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("PLAYBACK INFO", color = Color(0x8CEBEBF5), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 8.dp else 5.dp)) {
                Text(r.k, color = DimInk, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Text(
                    r.v, color = if (r.warn) Color(0xFFFFB340) else Ink, fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                )
            }
        }
        if (rows.isEmpty()) Text("Waiting for the stream…", color = DimInk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Subtitle timing: one big number and four nudges, like a camera's exposure offset. */
@Composable
internal fun SubTimingPanel(offsetMs: Long, onNudge: (Long) -> Unit, onReset: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(280.dp)
            .background(Color(0xD91C1C1E), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SUBTITLE TIMING", color = Color(0x8CEBEBF5), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
            GlassPill("Done", onClick = onDone)
        }
        Text(
            fmtSubOffset(offsetMs), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            when { offsetMs > 0 -> "Subtitles later"; offsetMs < 0 -> "Subtitles earlier"; else -> "In sync" },
            color = DimInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp).align(Alignment.CenterHorizontally),
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            GlassPill("−0.5", onClick = { onNudge(-500) })
            GlassPill("−0.1", onClick = { onNudge(-100) })
            GlassPill("+0.1", onClick = { onNudge(100) })
            GlassPill("+0.5", onClick = { onNudge(500) })
        }
        Row(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)) {
            GlassPill("Back in sync", on = offsetMs == 0L, onClick = onReset)
        }
        Text(
            "If the words arrive before the voices, choose +.",
            color = Color(0x8CEBEBF5), fontSize = 11.5.sp, lineHeight = 15.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
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
    onSubStyle: () -> Unit,
    onAudio: () -> Unit,
    onQuality: () -> Unit,
    onSpeedCycle: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
    dimTitle: Boolean = false,          // the pause board is saying it already
    infoOn: Boolean = false,
    onInfo: () -> Unit = {},
    showTiming: Boolean = false,
    onTiming: () -> Unit = {},
    sleepLabel: String? = null,         // "38 min" / "End of episode" while a sleep timer is set
    onSleep: () -> Unit = {},
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(140.dp)
                    .background(Brush.verticalGradient(0f to Color(0x8C000000), 1f to Color(0x00000000)))
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp)
                    .background(Brush.verticalGradient(0f to Color(0x00000000), 1f to Color(0xC7000000)))
            )

            // top: back · badges · window controls
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.weight(1f))
                partyBadge?.let {
                    Text(
                        it, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                            .background(Color.White, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (isLive) Text(
                    "LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 12.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                if (canPip) {
                    GlassCircle(Icons.Filled.PictureInPictureAlt, "Picture-in-picture", onClick = onPip)
                    Spacer(Modifier.width(12.dp))
                }
                GlassCircle(Icons.Filled.Fullscreen, "Fullscreen", onClick = onFullscreen)
            }

            // centre transport
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircle(Icons.Filled.Replay10, "Back 10 seconds", size = 56.dp, iconSize = 26.dp) { onSeekBy(-10_000) }
                Spacer(Modifier.width(34.dp))
                GlassCircle(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play/Pause", size = 80.dp, iconSize = 40.dp, onClick = onPlayPause,
                )
                Spacer(Modifier.width(34.dp))
                GlassCircle(Icons.Filled.Forward10, "Forward 10 seconds", size = 56.dp, iconSize = 26.dp) { onSeekBy(10_000) }
            }

            // bottom: title + pills, then the scrubber, then the times
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f).padding(end = 16.dp).alpha(if (dimTitle) 0f else 1f)) {
                        Text(
                            title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        episodeTag?.let {
                            Text(it, color = DimInk, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
                        }
                    }
                }
                // more pills than a phone is wide: the row scrolls, and D-pad focus drags it along
                Row(
                    Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    if (hasNext) GlassPill("Next ›", onClick = onNext)
                    if (showSubtitles) GlassPill("Subtitles", onClick = onSubtitles)
                    if (showSubtitles) GlassPill("Style", onClick = onSubStyle)
                    if (showTiming) GlassPill("Timing", onClick = onTiming)
                    if (showAudio) GlassPill("Audio", onClick = onAudio)
                    if (qualityLabel != null) GlassPill("Quality", qualityLabel, onClick = onQuality)
                    GlassPill("Speed", speedLabel, onClick = onSpeedCycle)
                    GlassPill("Sleep", sleepLabel, on = sleepLabel != null, onClick = onSleep)
                    GlassPill("Info", on = infoOn, onClick = onInfo)
                    GlassPill(if (partyActive) "Leave party" else "Party", onClick = onParty)
                    if (partyActive) {
                        GlassPill("Invite", onClick = onInvite)
                        listOf("\uD83D\uDC4D", "\uD83D\uDE02", "\u2764\uFE0F", "\uD83D\uDD25").forEach { e ->
                            GlassPill(e, onClick = { onReact(e) })
                        }
                    }
                }

                var trackWidth by remember { mutableStateOf(1) }
                var dragFrac by remember { mutableStateOf<Float?>(null) }
                val seekInteraction = remember { MutableInteractionSource() }
                val seekFocused by seekInteraction.collectIsFocusedAsState()
                val frac = (dragFrac
                    ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f).coerceIn(0f, 1f)
                val bufFrac = (if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(26.dp).padding(top = 12.dp)
                        .onSizeChanged { trackWidth = it.width.coerceAtLeast(1) }
                        .focusable(interactionSource = seekInteraction)
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (e.key) {
                                Key.DirectionLeft -> { onSeekBy(-10_000); true }
                                Key.DirectionRight -> { onSeekBy(10_000); true }
                                else -> false
                            }
                        }
                        .pointerInput(durationMs) {
                            detectTapGestures { off ->
                                if (durationMs > 0) onSeekTo((off.x / trackWidth * durationMs).toLong())
                            }
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { off -> dragFrac = off.x / trackWidth },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    dragFrac = (change.position.x / trackWidth).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    dragFrac?.let { if (durationMs > 0) onSeekTo((it * durationMs).toLong()) }
                                    dragFrac = null
                                },
                                onDragCancel = { dragFrac = null },
                            )
                        },
                ) {
                    // the Apple TV scrubber: thick, fully rounded, brightens under focus
                    val h = if (seekFocused || dragFrac != null) 12.dp else 9.dp
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(h)
                        .background(Color(0x3DFFFFFF), RoundedCornerShape(50)))
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(bufFrac).height(h)
                        .background(Color(0x61FFFFFF), RoundedCornerShape(50)))
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(frac).height(h)
                        .background(Ink, RoundedCornerShape(50)))
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        if (isLive) "At the live edge" else fmtTime(positionMs),
                        color = DimInk, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (isLive) "LIVE" else "−" + fmtTime((durationMs - positionMs).coerceAtLeast(0L)),
                        color = DimInk, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
