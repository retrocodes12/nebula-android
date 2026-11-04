package com.nuvio.ckplayer

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The player's controls on a phone or tablet — Nuvio's layout, which the Founder called "so clean and premium"
 * (11-04, his screenshots): no glass at all. Bare white icons over a dim of the picture; the title top-left, Lock and
 * Back top-right; −10 · play · +10 in the middle; a thin full-width bar; one row of icons under it, the first few
 * shown and the rest behind a chevron, and the time at the right ("50:52 / 52:55"). A TV keeps TitleCardChrome's
 * glass — its focus rings need it.
 */

/** Bare icons read over any picture with a soft shadow under them, as text does over art. */
private val IconShadow = Shadow(Color(0x99000000), blurRadius = 8f)

/** A bare white icon, tappable over a 44 dp target. [on]: a lit toggle (sleep set, info open) gets a white disc. */
@Composable
private fun BareIcon(
    icon: ImageVector, label: String, modifier: Modifier = Modifier, size: Int = 24, on: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier.size((size + 20).dp)
            .then(if (on) Modifier.padding(4.dp).background(Color.White, CircleShape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Button, onClickLabel = label,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = label, tint = if (on) Color.Black else Color.White,
            modifier = Modifier.size(size.dp),
        )
    }
}

/** One toolbar entry: drawn as a bare icon, with a short value beside it when it says something (1.5×, 38 min). */
internal class PhoneTool(
    val icon: ImageVector?, val label: String, val value: String? = null, val on: Boolean = false,
    val focus: FocusRequester? = null, val onClick: () -> Unit,
)

@Composable
internal fun PhoneChrome(
    visible: Boolean,
    title: String,
    episodeTag: String?,
    sourceLine: String?,
    isPlaying: Boolean,
    isLive: Boolean,
    clock: PlayerClock,
    partyBadge: String?,
    showClock: Boolean,
    dimTitle: Boolean,
    tools: List<PhoneTool>,
    seekStepMs: Long,
    scrubFrame: State<Bitmap?>?,
    playFocus: FocusRequester?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrub: (Long?) -> Unit,
    onGoLive: () -> Unit,
) {
    val ctx = LocalContext.current
    var showTotal by remember { mutableStateOf(Prefs.timeDisplay == "total") }
    // the row opens past its first few icons on the chevron, and stays as the viewer left it for this play
    var open by rememberSaveable { mutableStateOf(false) }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 480.dp
            val side = if (compact) 28.dp else 20.dp
            // the whole picture dims a little under the controls (Nuvio's), deeper at the edges where the words are
            Box(Modifier.fillMaxSize().background(Color(0x47000000)))
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp)
                .background(Brush.verticalGradient(0f to Color(0x8C000000), 1f to Color(0x00000000))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(0f to Color(0x00000000), 1f to Color(0xA6000000))))

            // top: the title (left) · party code · LIVE · Lock · Back (right)
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = side, end = side - 10.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).alpha(if (dimTitle) 0f else 1f)) {
                    Text(
                        title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = IconShadow),
                    )
                    val line = listOfNotNull(episodeTag?.takeIf { it.isNotBlank() }, sourceLine?.takeIf { it.isNotBlank() })
                        .joinToString("  •  ")
                    if (line.isNotEmpty()) Text(
                        line, color = Color(0xB3FFFFFF), fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                        style = TextStyle(shadow = IconShadow),
                    )
                    if (showClock) Text(
                        clockLine(ctx, clock.dur - clock.pos, clock.speed, live = isLive || clock.dur <= 0),
                        color = Color(0x99FFFFFF), fontFamily = Mono, fontSize = 11.sp, maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                partyBadge?.let {
                    Text(
                        it, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp).background(Color.White, Pill).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (isLive) Text(
                    "LIVE", color = OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp).background(Red, Pill)
                        .clickable { onGoLive() }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
                BareIcon(Icons.Filled.Lock, "Lock the screen", onClick = onLock)
                BareIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            }

            // the middle: bare transport icons, the play glyph large
            val stepS = (seekStepMs / 1000L).toInt()
            val backIcon = when (stepS) { 5 -> Icons.Filled.Replay5; 30 -> Icons.Filled.Replay30; 10 -> Icons.Filled.Replay10; else -> Icons.Filled.FastRewind }
            val fwdIcon = when (stepS) { 5 -> Icons.Filled.Forward5; 30 -> Icons.Filled.Forward30; 10 -> Icons.Filled.Forward10; else -> Icons.Filled.FastForward }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                BareIcon(backIcon, "Back $stepS seconds", size = 36) { onSeekBy(-seekStepMs) }
                Spacer(Modifier.width(if (compact) 120.dp else 48.dp))
                BareIcon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", size = 60,
                    modifier = if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier,
                    onClick = onPlayPause,
                )
                Spacer(Modifier.width(if (compact) 120.dp else 48.dp))
                BareIcon(fwdIcon, "Forward $stepS seconds", size = 36) { onSeekBy(seekStepMs) }
            }

            // the foot: the thin bar, then the icon row with the time at its right
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = side, end = side, bottom = if (compact) 8.dp else 14.dp)) {
                Scrubber(
                    positionMs = clock.pos, durationMs = clock.dur, bufferedMs = clock.buf,
                    isLive = isLive, liveOffsetMs = clock.liveOff, frame = scrubFrame,
                    onSeekBy = onSeekBy, onSeekTo = onSeekTo, onScrub = onScrub, stepMs = seekStepMs, thin = true,
                )
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    // a phone held upright has room for four before the chevron, on its side for five
                    val first = if (maxWidth < 600.dp) 4 else 5
                    val shown = if (open || tools.size <= first + 1) tools else tools.take(first)
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()).animateContentSize()
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(0.dp))
                        shown.forEach { t ->
                            // a party reaction is its own glyph, sent on the wire as it is
                                if (t.icon == null) Text(
                                    t.label, fontSize = 20.sp,
                                    modifier = Modifier.clickable { t.onClick() }.padding(horizontal = 8.dp, vertical = 8.dp),
                                ) else Row(verticalAlignment = Alignment.CenterVertically) {
                                BareIcon(
                                    t.icon, t.label + (t.value?.let { " $it" } ?: ""), on = t.on,
                                    modifier = Modifier.padding(start = 0.dp).then(if (t.focus != null) Modifier.focusRequester(t.focus) else Modifier),
                                    onClick = t.onClick,
                                )
                                if (t.value != null && !t.on) Text(
                                    t.value, color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                        }
                        if (tools.size > first + 1) BareIcon(
                            if (open) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            if (open) "Fewer controls" else "More controls",
                        ) { open = !open }
                    }
                    val pos = clock.pos
                    val dur = clock.dur
                    val off = clock.liveOff
                    Text(
                        when {
                            isLive -> if (off > 12_000) "−" + fmtTime(off) + " behind live" else "Live"
                            dur <= 0 -> fmtTime(pos)
                            showTotal -> fmtTime(pos) + " / " + fmtTime(dur)
                            else -> fmtTime(pos) + " / −" + fmtTime((dur - pos).coerceAtLeast(0L))
                        },
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        style = TextStyle(shadow = IconShadow, fontFeatureSettings = "tnum"),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) {
                            if (isLive) onGoLive()
                            else { showTotal = !showTotal; Prefs.setTimeDisplay(ctx, if (showTotal) "total" else "left") }
                        }.padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** The tools the phone row offers, in Nuvio's order (next, words, sound, sources first), from TitleCardChrome's inputs. */
internal fun phoneTools(
    hasNext: Boolean, showSubtitles: Boolean, showAudio: Boolean, qualityLabel: String?, speedLabel: String,
    fitLabel: String?, sleepLabel: String?, partyActive: Boolean, canInvite: Boolean, infoOn: Boolean,
    canPip: Boolean, canFullscreen: Boolean, subtitlesFocus: FocusRequester?, sleepFocus: FocusRequester?,
    onNext: () -> Unit, onSubtitles: () -> Unit, onAudio: () -> Unit, onQuality: () -> Unit,
    onStreams: (() -> Unit)?, onSpeedCycle: () -> Unit, onFitCycle: () -> Unit, onSleep: () -> Unit,
    onParty: () -> Unit, onInvite: () -> Unit, onReact: (String) -> Unit, onInfo: () -> Unit, onPip: () -> Unit,
    onFullscreen: () -> Unit,
): List<PhoneTool> = buildList {
    if (hasNext) add(PhoneTool(Icons.Filled.SkipNext, "Next episode", onClick = onNext))
    if (showSubtitles) add(PhoneTool(Icons.Outlined.ClosedCaption, "Subtitles", focus = subtitlesFocus, onClick = onSubtitles))
    if (showAudio) add(PhoneTool(Icons.Outlined.Audiotrack, "Audio", onClick = onAudio))
    if (onStreams != null) add(PhoneTool(Icons.Outlined.VideoLibrary, "Streams", onClick = onStreams))
    if (qualityLabel != null) add(PhoneTool(Icons.Outlined.HighQuality, "Quality", onClick = onQuality))
    add(PhoneTool(Icons.Outlined.Speed, "Speed", speedLabel.takeIf { it != "1.0×" }, onClick = onSpeedCycle))
    if (fitLabel != null) add(PhoneTool(Icons.Outlined.AspectRatio, "Picture: $fitLabel", onClick = onFitCycle))
    add(PhoneTool(Icons.Outlined.Bedtime, "Sleep timer", sleepLabel, on = sleepLabel != null, focus = sleepFocus, onClick = onSleep))
    add(PhoneTool(Icons.Outlined.Groups, if (partyActive) "Leave party" else "Watch party", on = partyActive, onClick = onParty))
    if (partyActive && canInvite) add(PhoneTool(Icons.Outlined.PersonAdd, "Invite", onClick = onInvite))
    if (partyActive) listOf("\uD83D\uDC4D", "\uD83D\uDE02", "\u2764\uFE0F", "\uD83D\uDD25").forEach { e ->
        add(PhoneTool(null, e) { onReact(e) })
    }
    add(PhoneTool(Icons.Outlined.Info, "Playback info", on = infoOn, onClick = onInfo))
    if (canPip) add(PhoneTool(Icons.Outlined.PictureInPictureAlt, "Picture-in-picture", onClick = onPip))
    if (canFullscreen) add(PhoneTool(Icons.Filled.Fullscreen, "Fullscreen", onClick = onFullscreen))
}

/**
 * Up next on a phone (Nuvio's "Next Episode" card): the episode's still, "Next Episode", "S1 E8 • Its name", an
 * outlined Play that counts down while autoplay waits, and a small × to say no.
 */
@Composable
internal fun NextEpisodeCard(
    ep: Episode, fallbackArt: String?, kicker: String, counting: Int?, sourceLine: String?,
    playFocus: FocusRequester, onPlay: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    Row(
        modifier.widthIn(max = 420.dp)
            .background(Color(0xEB1C1C1E), shape)
            .border(1.dp, Color(0x24FFFFFF), shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = ep.thumbnail ?: fallbackArt
        Box(
            Modifier.width(108.dp).aspectRatio(16f / 9f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2E)),
        ) {
            if (art != null) coil.compose.AsyncImage(
                model = art, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(kicker, color = Color(0xB3FFFFFF), fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "S${ep.season} E${ep.episode ?: "?"}" + (if (ep.name.isNotEmpty()) "  •  " + ep.name.uppercase() else ""),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
            )
            if (sourceLine != null) Text(
                sourceLine, color = Color(0x80FFFFFF), fontFamily = Mono, fontSize = 10.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            Modifier.focusRequester(playFocus)
                .clip(Pill).border(1.dp, Color(0x8CFFFFFF), Pill)
                .clickable { onPlay() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(
                if (counting != null) "Play ($counting)" else "Play", color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp),
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        BareIcon(Icons.Filled.Close, "Dismiss", size = 18, onClick = onDismiss)
    }
}
