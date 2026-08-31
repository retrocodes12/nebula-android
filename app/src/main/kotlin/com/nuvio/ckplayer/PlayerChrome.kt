package com.nuvio.ckplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
private fun GlassPill(label: String, value: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier.background(if (focused) GlassHot else Glass, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (value != null) Text(
            value, color = DimInk, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp), maxLines = 1,
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
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        episodeTag?.let {
                            Text(it, color = DimInk, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
                        }
                    }
                }
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    if (hasNext) GlassPill("Next ›", onClick = onNext)
                    if (showSubtitles) GlassPill("Subtitles", onClick = onSubtitles)
                    if (showSubtitles) GlassPill("Style", onClick = onSubStyle)
                    if (showAudio) GlassPill("Audio", onClick = onAudio)
                    if (qualityLabel != null) GlassPill("Quality", qualityLabel, onClick = onQuality)
                    GlassPill("Speed", speedLabel, onClick = onSpeedCycle)
                    GlassPill(if (partyActive) "Leave party" else "Party", onClick = onParty)
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
