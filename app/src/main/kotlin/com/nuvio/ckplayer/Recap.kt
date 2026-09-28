package com.nuvio.ckplayer

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Your September on Nebula" (Settings › Support, the Plus level): the month this device played — hours, the days
 * with at least a minute, the three biggest titles and the busiest day — from WatchLog, which never leaves the device.
 * The two months before are a chip away when they hold anything.
 */
@Composable
internal fun RecapCard() {
    val ctx = LocalContext.current
    val remote = remoteMode()
    val saved = WatchLog.version                                   // a save while the page is open redraws it
    val now = remember { WatchLog.nowKey() }
    val doc = remember(saved) { WatchLog.doc(ctx) }
    val months = remember(saved) { WatchLog.monthsShown(doc, now) }
    var pick by remember { mutableStateOf(now) }
    val r = remember(saved, pick) { WatchLog.recap(doc, pick) }
    SupportPanel {
        Eyebrow("Your month")
        Text(
            "Your ${WatchLog.monthName(pick)} on Nebula", color = TextC, fontSize = 19.sp, fontFamily = Sans,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp),
        )
        if (months.size > 1) Box(Modifier.padding(top = 12.dp)) {
            Segmented {
                months.forEach { m -> Chip(WatchLog.monthShort(m), pick == m, inSeg = true, modifier = Modifier.returnTo("recap/$m")) { pick = m } }
            }
        }
        // under a remote the figures take focus, so the D-pad can scroll down to them
        Column(
            Modifier.fillMaxWidth().padding(top = 14.dp)
                .then(if (remote) Modifier.focusRing(RoundedCornerShape(12.dp), landing = false).focusable() else Modifier),
        ) {
            if (r.totalSecs < 60L) {
                Text(
                    if (pick == now) "Nothing watched yet this month. It fills in as you watch." else "Nothing watched in ${WatchLog.monthName(pick)}.",
                    color = MutedC, fontSize = 14.sp, lineHeight = 20.sp,
                )
            } else {
                val (hv, hl) = WatchLog.hoursStat(r.totalSecs)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(hv, hl, Modifier.weight(1f))
                    Stat(r.days.toString(), if (r.days == 1) "day you watched" else "days you watched", Modifier.weight(1f))
                    if (r.busiestDay > 0) Stat(WatchLog.dayLabel(pick, r.busiestDay), "busiest day · " + WatchLog.fmtDuration(r.busiestSecs), Modifier.weight(1f))
                }
                if (r.top.isNotEmpty()) {
                    Eyebrow("Top titles", Modifier.padding(top = 18.dp, bottom = 4.dp))
                    r.top.forEachIndexed { i, (title, secs) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = FaintC, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                            Text(title, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(WatchLog.fmtDuration(secs), color = MutedC, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
        Text(
            "Counted on this device while something plays — never uploaded, never synced.",
            color = FaintC, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** One figure: the number large, what it counts under it. */
@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = TextC, fontSize = 26.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, maxLines = 1)
        Text(label, color = MutedC, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
