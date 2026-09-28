package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Vote on what's next" — the supporters' say in what gets built (cloud/support-vote.js). One round at a time, two to
 * six options, one ballot per supporter that can be changed while the round is open. The counts come only after one's
 * own ballot (nobody votes with the crowd) and for the last closed round. Casting needs the Plus level (Supporter Plus,
 * Founder, Monthly); anyone may look.
 */
object Vote {
    class Option(val id: String, val title: String, val note: String, val votes: Int?)
    class Round(val id: String, val title: String, val closes: Long, val options: List<Option>, val voters: Int?)
    class Past(val id: String, val title: String, val closed: Long, val winner: String?, val voters: Int, val options: List<Option>)
    class State(val round: Round?, val mine: String?, val can: Boolean, val last: Past?)

    /** `GET/POST /v1/support/vote`'s answer. Rows with no id or title are dropped; text is capped as the server caps it. */
    fun parse(o: JSONObject): State {
        fun str(j: JSONObject, k: String, max: Int) = if (j.isNull(k)) "" else j.optString(k).trim().take(max)
        fun options(j: JSONObject): List<Option> {
            val a = j.optJSONArray("options") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val x = a.optJSONObject(i) ?: return@mapNotNull null
                val id = str(x, "id", 40)
                val title = str(x, "title", 80)
                if (id.isEmpty() || title.isEmpty()) null
                else Option(id, title, str(x, "note", 200), if (x.has("votes") && !x.isNull("votes")) x.optInt("votes").coerceAtLeast(0) else null)
            }
        }
        val r = o.optJSONObject("round")?.let { j ->
            val opts = options(j)
            if (opts.isEmpty()) null
            else Round(str(j, "id", 40), str(j, "title", 80).ifEmpty { "What should Nebula build next?" }, j.optLong("closes"),
                opts, if (j.has("voters") && !j.isNull("voters")) j.optInt("voters").coerceAtLeast(0) else null)
        }
        val mine = str(o, "mine", 40).takeIf { m -> m.isNotEmpty() && r?.options?.any { it.id == m } == true }
        val last = o.optJSONObject("last")?.let { j ->
            Past(str(j, "id", 40), str(j, "title", 80), j.optLong("closed"),
                j.optJSONObject("winner")?.let { str(it, "title", 80) }?.ifEmpty { null },
                j.optInt("voters").coerceAtLeast(0), options(j))
        }
        return State(r, mine, o.optBoolean("can"), last)
    }

    /** Each option's share of the ballots, in whole percent, largest-remainder rounded so the column adds up to 100. */
    fun shares(votes: List<Int>): List<Int> {
        val total = votes.sum()
        if (total <= 0) return votes.map { 0 }
        val exact = votes.map { it * 100.0 / total }
        val floor = exact.map { it.toInt() }.toMutableList()
        var left = 100 - floor.sum()
        exact.withIndex().sortedByDescending { it.value - it.value.toInt() }.forEach { (i, _) -> if (left > 0) { floor[i]++; left-- } }
        return floor
    }

    /** The open round and the last one; null when the server could not be reached. */
    suspend fun load(ctx: Context): State? = runCatching { parse(Cloud.api(ctx, "GET", "/v1/support/vote", null)) }
        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

    /** Cast (or change) this profile's ballot. The new state, or a line to show. */
    suspend fun cast(ctx: Context, option: String): Pair<State?, String?> = runCatching {
        parse(Cloud.api(ctx, "POST", "/v1/support/vote", JSONObject().put("option", option))) to null
    }.getOrElse {
        if (it is kotlinx.coroutines.CancellationException) throw it
        null to Account.errorText(it)
    }

    internal fun dayText(ms: Long): String = if (ms <= 0L) "" else SimpleDateFormat("d MMM", Locale.US).format(Date(ms))
}

/** The vote card on Settings › Support (Plus level). */
@Composable
internal fun VotePanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<Vote.State?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf("") }
    var tries by remember { mutableIntStateOf(0) }
    LaunchedEffect(tries) {
        failed = false
        val s = Vote.load(ctx)
        if (s == null) failed = true else st = s
    }
    SupportPanel {
        Eyebrow("Vote on what's next")
        val s = st
        when {
            s == null && failed -> {
                Text("Could not reach the server.", color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                TextAction("Try again") { tries++ }
            }
            s == null -> Text("Loading the vote…", color = MutedC, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            s.round == null -> {
                Text("No vote open right now.", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                val last = s.last
                if (last?.winner != null) Text(
                    "Last time, “${last.title}”: ${last.winner} won, with ${last.voters} supporter${if (last.voters == 1) "" else "s"} voting.",
                    color = MutedC, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            else -> {
                val r: Vote.Round = s.round ?: return@SupportPanel
                Text(r.title, color = TextC, fontSize = 19.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                val closes = Vote.dayText(r.closes)
                if (closes.isNotEmpty()) Text("Closes $closes", color = MutedC, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
                val voted = s.mine != null
                val pct = if (voted) Vote.shares(r.options.map { it.votes ?: 0 }) else emptyList()
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.options.forEachIndexed { i, o ->
                        VoteRow(
                            o, mine = s.mine == o.id, share = pct.getOrNull(i), enabled = s.can && !busy,
                            onPick = {
                                if (busy || s.mine == o.id) return@VoteRow
                                busy = true; line = ""
                                scope.launch {
                                    val (next, err) = Vote.cast(ctx, o.id)
                                    if (next != null) st = next
                                    if (err != null) line = err
                                    busy = false
                                }
                            },
                        )
                    }
                }
                val mineTitle = r.options.firstOrNull { it.id == s.mine }?.title
                Text(
                    when {
                        !s.can -> "The vote is for Supporter Plus, Founders and Monthly supporters."
                        mineTitle != null -> "You voted for $mineTitle" + (r.voters?.let { " · $it supporter${if (it == 1) "" else "s"} so far" } ?: "") +
                            ". Pick another to change it while the vote is open."
                        else -> "Pick one. You can change it until the vote closes; the counts show once you have voted."
                    },
                    color = MutedC, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (line.isNotEmpty()) Text(line, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** One option: a row the remote walks (ring when lit), the title and its note; after one's ballot, its share as a bar. */
@Composable
private fun VoteRow(o: Vote.Option, mine: Boolean, share: Int?, enabled: Boolean, onPick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth()
            .returnTo("vote/${o.id}")
            .clip(shape)
            .background(if (mine) Color(0x14FFFFFF) else Color.Transparent)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onPick() }
            .then(rowLit(focused))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // the ballot mark: a hairline circle, filled with a tick on one's own choice
            val mark = Modifier.size(22.dp).clip(CircleShape)
            Box(
                if (mine) mark.background(Red) else mark.border(1.5.dp, Color(0x66EBEBF5), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (mine) Icon(Icons.Filled.Check, contentDescription = "Your vote", tint = OnAccent, modifier = Modifier.size(15.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(o.title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (o.note.isNotEmpty()) Text(o.note, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (share != null) Text("$share%", color = if (mine) TextC else MutedC, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        if (share != null) Box(Modifier.padding(start = 34.dp, top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(LineC)) {
            Box(Modifier.fillMaxWidth(share / 100f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (mine) Red else Color(0x66EBEBF5)))
        }
    }
}
