package com.nuvio.ckplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/* The speed test's faces (branch `speedtest`; the measuring is SpeedTest.kt): the Connection speed row in Settings ›
   Playback, and the line a tested stream row wears. */

/** The verdict's colours, always beside its words (never colour alone): enough, only just, not enough. */
internal val OkC = Color(0xFF30D158)
internal val AmberC = Color(0xFFFF9F0A)
internal val BadC = Color(0xFFFF453A)

/** A stream row's speed test, for the Streams page's life (and its 10-minute Back memo). */
internal sealed interface RowSpeed {
    /** Under way; [live] = the rate read so far (0 before the first bytes). */
    data class Testing(val live: Long = 0L) : RowSpeed
    data class Done(val bps: Long, val need: Long) : RowSpeed
    data object Failed : RowSpeed
    data object Unsupported : RowSpeed
}

internal fun SpeedTest.Result.toRow(): RowSpeed = when {
    unsupported -> RowSpeed.Unsupported
    failed -> RowSpeed.Failed
    else -> RowSpeed.Done(bps, need)
}

/** The row's words: "48 Mbps · plays smoothly", "Testing… 23 Mbps", "Couldn't reach it", "Can't test this kind of stream". */
internal fun RowSpeed.words(): String = when (this) {
    is RowSpeed.Testing -> if (live > 0) "Testing… " + SpeedTest.mbps(live) else "Testing…"
    is RowSpeed.Done -> SpeedTest.line(bps, need)
    RowSpeed.Failed -> "Couldn’t reach it"
    RowSpeed.Unsupported -> "Can’t test this kind of stream"
}

/** The colour beside the words: the verdict's, or none (a test under way, an unknown need, no answer). */
internal fun RowSpeed.tone(): Color? = when (this) {
    is RowSpeed.Done -> when (SpeedTest.verdict(bps, need)) {
        SpeedTest.Verdict.SMOOTH -> OkC
        SpeedTest.Verdict.SHOULD -> AmberC
        SpeedTest.Verdict.STALL -> BadC
        SpeedTest.Verdict.UNKNOWN -> null
    }
    RowSpeed.Failed -> BadC
    else -> null
}

/** The line under a tested stream row: a dot in the verdict's colour (a spinner while it runs) and the words. */
@Composable
internal fun SpeedLine(r: RowSpeed, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        val tone = r.tone()
        when {
            r is RowSpeed.Testing -> CircularProgressIndicator(Modifier.size(10.dp), color = MutedC, strokeWidth = 1.5.dp, trackColor = Color(0x1FFFFFFF))
            tone != null -> Box(Modifier.size(8.dp).background(tone, CircleShape))
        }
        Text(
            r.words(), color = if (r is RowSpeed.Done) TextC else MutedC, fontFamily = Mono, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago". */
internal fun agoText(at: Long, now: Long = System.currentTimeMillis()): String {
    val s = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86_400 -> "${s / 3600} h ago"
        s < 172_800 -> "yesterday"
        else -> "${s / 86_400} days ago"
    }
}

/**
 * Settings › Playback › Connection speed: Test → ~8 s against Cloudflare's speed endpoint, the rate shown as it runs,
 * then "92 Mbps · 18 ms · just now". The answer becomes the measured connection (Prefs.setSpeedTest), so the Streams
 * page's "may stall here" follows it. Leaving the page stops a test under way.
 */
@Composable
internal fun ConnectionSpeedRow(divider: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var live by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }     // "5 min ago" moves on while the page is open
    val now = remember(tick, Prefs.speedAt) { System.currentTimeMillis() }
    val running = job != null
    val status = when {
        running -> if (live > 0) "Testing… " + SpeedTest.mbps(live) else "Testing…"
        failed -> "Couldn’t reach the speed test — check the connection"
        Prefs.speedAt > 0 -> SpeedTest.mbps(Prefs.speedBps) + " · ${Prefs.speedPing} ms · " + agoText(Prefs.speedAt, now)
        else -> "Not tested yet"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Connection speed", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(status, color = if (failed && !running) BadC else MutedC, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            TextAction(if (running) "Stop" else "Test") {
                if (running) {
                    job?.cancel()
                    job = null
                } else {
                    failed = false
                    live = 0L
                    val pulse = AtomicLong()
                    job = scope.launch {
                        val ticker = launch { while (true) { delay(250); live = pulse.get() } }
                        try {
                            val line = SpeedTest.testConnection(ctx, pulse)
                            Prefs.setSpeedTest(ctx, line.bps, line.pingMs)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed = true
                        } finally {
                            ticker.cancel()
                            if (job === coroutineContext.job) job = null
                        }
                    }
                }
            }
        }
        Text(
            "Streams faster than this are marked “may stall here”.",
            color = FaintC, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (divider) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(LineC))
}
