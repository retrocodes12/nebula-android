package com.nuvio.ckplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Title Card playback chrome — the same design the web/TV/desktop player ships:
 * a didone in letterspaced caps carries the title, an italic script line speaks
 * the player's state, controls are whispered small-caps words (no icons), the
 * seek is a hairline with a diamond knob, and an ornamental frame borders the
 * stage. No accent hue: the film supplies every colour.
 */

internal val Playfair = FontFamily(
    Font(R.font.playfair, FontWeight.Normal),
    Font(R.font.playfair_italic, FontWeight.Normal, FontStyle.Italic),
)

private val Ink = Color.White
private val DimInk = Color(0xA8FFFFFF)
private val FaintInk = Color(0x6BFFFFFF)
private val Hairline = Color(0x61FFFFFF)

/** A whispered small-caps control word; focus draws the underline. */
@Composable
private fun Word(
    label: String,
    modifier: Modifier = Modifier,
    bright: Boolean = false,
    value: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            color = if (bright || focused) Ink else DimInk,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.em,
            textDecoration = if (focused) TextDecoration.Underline else TextDecoration.None,
            maxLines = 1,
        )
        if (value != null) Text(
            value.uppercase(),
            color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.18.em,
            modifier = Modifier.padding(start = 10.dp), maxLines = 1,
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
    onSubtitles: () -> Unit,
    onSubStyle: () -> Unit,
    onAudio: () -> Unit,
    onQuality: () -> Unit,
    onSpeedCycle: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            // legibility scrim, then the ornamental frame
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(230.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color(0x000C0A09), 1f to Color(0xEB0C0A09),
                        )
                    )
            )
            Box(Modifier.fillMaxSize().padding(12.dp).border(1.dp, Hairline))

            // top row: back square · window words · badges
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                val backFocused by backInteraction.collectIsFocusedAsState()
                Box(
                    Modifier.size(38.dp)
                        .border(1.dp, if (backFocused) Ink else Hairline)
                        .background(Color(0x590C0A09))
                        .clickable(interactionSource = backInteraction, indication = null) { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Ink, fontSize = 20.sp) }
                Spacer(Modifier.weight(1f))
                if (canPip) Word("PiP", onClick = onPip)
                Spacer(Modifier.width(14.dp))
                Word("Fullscreen", onClick = onFullscreen)
                if (isLive) Text(
                    "LIVE", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.24.em,
                    modifier = Modifier.padding(start = 18.dp).border(1.dp, Hairline)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                partyBadge?.let {
                    Text(
                        it.uppercase(), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.em,
                        modifier = Modifier.padding(start = 14.dp).border(1.dp, Ink)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // the lower third
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, bottom = 66.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f).padding(end = 24.dp)) {
                    Text(
                        if (isPlaying) "Now Playing" else "Paused",
                        fontFamily = Playfair, fontStyle = FontStyle.Italic,
                        fontSize = 15.sp, color = DimInk,
                    )
                    val big = title.length <= 17
                    val mid = title.length in 18..34
                    Text(
                        title.uppercase(),
                        fontFamily = Playfair,
                        fontSize = if (big) 26.sp else if (mid) 19.sp else 15.sp,
                        letterSpacing = if (big) 0.2.em else 0.12.em,
                        color = Ink, lineHeight = 30.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Box(Modifier.padding(top = 10.dp, bottom = 8.dp).widthIn(max = 320.dp)
                        .fillMaxWidth(0.6f).height(1.dp).background(Hairline))
                    Row {
                        Word("Rewind") { onSeekBy(-10_000) }
                        Spacer(Modifier.width(18.dp))
                        Word(if (isPlaying) "Pause" else "Play", bright = true, onClick = onPlayPause)
                        Spacer(Modifier.width(18.dp))
                        Word("Forward") { onSeekBy(10_000) }
                        Spacer(Modifier.width(18.dp))
                        Word(if (partyActive) "Leave Party" else "Party", onClick = onParty)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (hasNext) Word("Next Episode", bright = true, onClick = onNext)
                    if (showSubtitles) Word("Subtitles", onClick = onSubtitles)
                    if (showSubtitles) Word("Style", onClick = onSubStyle)
                    if (showAudio) Word("Audio", onClick = onAudio)
                    if (qualityLabel != null) Word("Quality", value = qualityLabel, onClick = onQuality)
                    Word("Speed", value = speedLabel, onClick = onSpeedCycle)
                }
            }

            // the seek — a hairline along the bottom of the stage
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, bottom = 18.dp),
            ) {
                var trackWidth by remember { mutableStateOf(1) }
                var dragFrac by remember { mutableStateOf<Float?>(null) }
                val seekInteraction = remember { MutableInteractionSource() }
                val seekFocused by seekInteraction.collectIsFocusedAsState()
                val frac = (dragFrac
                    ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f).coerceIn(0f, 1f)
                val bufFrac = (if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(22.dp)
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
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x4DFFFFFF)))
                    Box(Modifier.fillMaxWidth(bufFrac).height(1.dp).background(Color(0x80FFFFFF)))
                    Box(Modifier.fillMaxWidth(frac).height(2.dp).background(Ink))
                    if (dragFrac != null || seekFocused) {
                        Box(
                            Modifier.offset {
                                androidx.compose.ui.unit.IntOffset(
                                    (trackWidth * frac).toInt() - 4.dp.roundToPx(), 0,
                                )
                            }.size(8.dp).rotate(45f).background(Ink)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isLive) "At the edge" else fmtTime(positionMs).uppercase(),
                        color = DimInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.em,
                    )
                    Spacer(Modifier.weight(1f))
                    episodeTag?.let {
                        Text(it.uppercase(), color = FaintInk, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.em, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        if (isLive) "LIVE" else fmtTime(durationMs).uppercase(),
                        color = DimInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.em,
                    )
                }
            }
        }
    }
}
