package com.nuvio.ckplayer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowCircleUp
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** The Founder's ring (Avatar): on the card the gold takes the accent's place, so the card still wears one colour. */
private val GoldC = Color(0xFFE0B24A)

@Composable
internal fun SettingsSupportScreen(onBack: () -> Unit, onProfile: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tv = remember { Account.isTv(ctx) }
    val me = Cloud.profile
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }      // the code's answer, under the code
    var saved by remember { mutableStateOf("") }       // a failed wall or mark save, under what was pressed

    LaunchedEffect(Unit) { Support.load(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Support Nebula", null, onBack)
        LandOnFirstControl()

        val rank = Support.rank()
        if (me?.sup == true) {
            SupporterPanel(
                me, rank,
                onWall = { on ->
                    if (busy) return@SupporterPanel
                    busy = true; saved = ""
                    scope.launch { saved = Support.setWall(ctx, on) ?: ""; busy = false }
                },
                onMark = { m ->
                    if (busy) return@SupporterPanel
                    busy = true; saved = ""
                    scope.launch { saved = Support.setMark(ctx, m) ?: ""; busy = false }
                },
            )
            if (saved.isNotEmpty()) Text(
                saved, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        } else {
            SupportPitch(tv = tv, onOpen = { scope.launch { Support.open(ctx) } })
        }
        // the Plus-level perks (the vote, the month) — below that level, a line each (Perks.kt)
        SupportPerks(rank = rank, tv = tv)
        if (rank in 1..2 && Support.url != null) {
            Spacer(Modifier.height(BLOCK_GAP))
            RaiseTier(tv, onOpen = { scope.launch { Support.open(ctx) } })
        }
        if (rank < 3) {
            Spacer(Modifier.height(BLOCK_GAP))
            SupportCodePanel(
                signedIn = me != null,
                code = code, onCode = { code = it.take(20) },
                busy = busy, onProfile = onProfile,
                onRedeem = {
                    if (busy) return@SupportCodePanel
                    busy = true; status = "Checking the code…"
                    scope.launch {
                        val err = Support.redeem(ctx, code)
                        busy = false
                        if (err == null) {
                            code = ""; status = ""
                            Toasts.show("You're a ${Support.tierName()}. Thank you. " +
                                (if (Support.rank() >= 2) "Any colour is yours in Appearance." else "Three more accents are yours in Appearance."))
                        } else {
                            status = err
                        }
                    }
                },
            )
        }

        if (status.isNotEmpty()) Text(
            status, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )

        if (Support.wall.isNotEmpty()) {
            Spacer(Modifier.height(BLOCK_GAP))
            SupportPanel {
                if (Support.founders.isNotEmpty()) WallNames("Founders", Support.founders)
                if (Support.founders.isNotEmpty() && Support.others.isNotEmpty()) {
                    Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(LineC))
                }
                if (Support.others.isNotEmpty()) WallNames("Thanks to", Support.others)
            }
        }
    }
}

@Composable
private fun WallNames(title: String, names: List<String>) {
    Eyebrow(title)
    Text(Support.joinNames(names), color = TextC, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Already a supporter: the card, then what they can set, in one group. */
@Composable
private fun SupporterPanel(me: Profile, rank: Int, onWall: (Boolean) -> Unit, onMark: (String) -> Unit) {
    SupporterPass(me, rank)
    Spacer(Modifier.height(BLOCK_GAP))
    SupportGroup {
        if (rank >= 2) {
            MarkPicker(Support.myMark(), onMark)
            GroupLine(full = true)
        }
        SupportRow(
            title = "Your name on the wall",
            sub = "In Settings › Support on every device, and on the website",
            key = "support/wall", icon = Icons.Outlined.Groups, checked = me.wall,
        ) { onWall(!me.wall) }
        if (rank >= 2) {
            GroupLine()
            EarlyBuildsRow()
        }
        if (me.subStatus.isNotEmpty() || me.subManage.isNotEmpty()) {
            GroupLine()
            PlanRow(me)
        }
    }
}

/** The membership card: the tier large, who holds it, and since when. A card, so it asks nothing of the viewer. */
@Composable
private fun SupporterPass(me: Profile, rank: Int) {
    val founder = rank >= 3
    val ink = if (founder) GoldC else Red
    val mark = Support.myMark()
    val since = Support.sinceText(me.supSince)
    // a failed payment has its own red line on the plan's row, in full: the card keeps to the short states
    val plan = if (me.subStatus == "past_due") "" else Support.subStatusText(me.subStatus)
    PassCard(mark, ink, rim = if (founder) GoldC else ink.copy(alpha = .4f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(markVector(mark), contentDescription = "Supporter", tint = ink, modifier = Modifier.size(18.dp))
            Eyebrow("Nebula", color = MutedC)
        }
        PassTitle(Support.tierName())
        Row(
            Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(me.avatar, me.name.ifEmpty { me.handle }, 38.dp, ring = founder)
            Column {
                Text(
                    me.name.ifEmpty { me.handle }, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@" + me.handle.removePrefix("@"), color = MutedC, fontFamily = Mono, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (since.isNotEmpty() || plan.isNotEmpty()) Row(
            Modifier.padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            if (since.isNotEmpty()) PassFact("Since", since)
            if (plan.isNotEmpty()) PassFact("Plan", plan)
        }
        Text(
            "Thank you. This is what keeps Nebula going.", color = MutedC, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
private fun PassTitle(text: String) {
    Text(
        text, color = TextC, fontSize = 30.sp, lineHeight = 35.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.7).sp, maxLines = 2, modifier = Modifier.padding(top = 24.dp),
    )
}

@Composable
private fun PassFact(label: String, value: String) {
    Column {
        Eyebrow(label)
        Text(value, color = TextC, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The card's material: a panel, one coloured hairline, and the mark itself set large and faint into the corner. */
@Composable
private fun PassCard(mark: String, ink: Color, rim: Color, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val art = rememberVectorPainter(markVector(mark))
    // under a remote the card is the landing: the first control below it would open the page scrolled past it
    val stop = if (remoteMode()) Modifier.focusRing(shape).focusable() else Modifier
    Column(
        Modifier.fillMaxWidth().then(stop).clip(shape).background(SurfaceC, shape)
            .drawBehind {
                val side = 230.dp.toPx()
                translate(size.width - side * .76f, size.height - side * .78f) {
                    with(art) { draw(Size(side, side), alpha = .08f, colorFilter = ColorFilter.tint(ink)) }
                }
            }
            .border(1.dp, rim, shape).padding(22.dp),
        content = content,
    )
}

/** Supporter Plus and up: the four shapes as themselves, so the choice is seen before it is made. */
@Composable
private fun MarkPicker(current: String, onMark: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Your mark", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Beside your name, for you and your friends", color = MutedC, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Support.MARKS.forEach { (k, label) ->
                val on = current == k
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.weight(1f).returnTo("mark/$k").focusRing(shape).clip(shape)
                        .background(if (on) Red.copy(alpha = .12f) else Color.Transparent)
                        .border(if (on) 1.5.dp else 1.dp, if (on) Red else LineC, shape)
                        .selectable(selected = on, role = Role.RadioButton) { if (!on) onMark(k) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(markVector(k), contentDescription = null, tint = if (on) Red else MutedC, modifier = Modifier.size(24.dp))
                    Text(
                        label, color = if (on) TextC else MutedC, fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Below the top tier: what the top adds (what Plus adds is listed just above it), and the way there. A TV shows the
    address instead of opening a page. */
@Composable
private fun RaiseTier(tv: Boolean, onOpen: () -> Unit) {
    val sub = "Founder: the Founders list, a gold ring, Nebula Sports without the sponsor prompt."
    if (tv) {
        SupportPanel {
            Text("Raise your tier", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            TvSupportAddress()
        }
    } else {
        SupportGroup {
            SupportRow("Raise your tier", sub, "support/tiers", Icons.Outlined.ArrowCircleUp, chevron = true, onClick = onOpen)
        }
    }
}

/** A TV has no browser worth typing a card number into: the short address, large. */
@Composable
private fun TvSupportAddress() {
    Text(
        "play.rifflehq.in/support", color = TextC, fontFamily = Mono, fontSize = 22.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
    Text(
        "Open it on your phone. It takes a minute." +
            (if (Cloud.profile != null) " Sign in there with the same profile, or type the code it gives you here." else ""),
        color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp),
    )
}

/** Not a supporter: the same card, holding what it is for and the way to do it. */
@Composable
private fun SupportPitch(tv: Boolean, onOpen: () -> Unit) {
    PassCard("star", Red, rim = Line2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(markVector("star"), contentDescription = null, tint = Red, modifier = Modifier.size(18.dp))
            Eyebrow("Nebula", color = MutedC)
        }
        PassTitle("Keep Nebula going")
        Text(
            "Nebula is free, with no ads and no account required. If it earns a place in your evenings, you can chip in.",
            color = MutedC, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportTick("A mark beside your name")
            SupportTick("More accent colours")
            SupportTick("Your name on the wall, if you like")
        }
        if (Support.url != null) {
            Spacer(Modifier.height(20.dp))
            if (tv) TvSupportAddress() else SupportCta("Support Nebula", onClick = onOpen)
        }
        Text(
            "Every feature stays free for everyone. Supporting keeps the sync server and the site running.",
            color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** The code that comes with the thank-you note, typed once on any signed-in device. Folded: few people hold one. */
@Composable
private fun SupportCodePanel(
    signedIn: Boolean,
    code: String,
    onCode: (String) -> Unit,
    busy: Boolean,
    onProfile: () -> Unit,
    onRedeem: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SupportGroup(Modifier.animateContentSize()) {
        SupportRow(
            "Have a supporter code?", "It comes with the thank-you", "support/code", Icons.Outlined.Redeem,
            open = open,
        ) { open = !open }
        if (open) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
            if (signedIn) {
                Text(
                    "One code, typed once on any signed-in device. The mark follows your profile.",
                    color = MutedC, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 12.dp),
                )
                PField(code, onCode, "NEB-XXXX-XXXX", caps = true, last = true, onDone = onRedeem)
                Spacer(Modifier.height(4.dp))
                SupportCta(if (busy) "Checking…" else "Redeem", enabled = !busy, onClick = onRedeem)
            } else {
                Text(
                    "Sign in first. The supporter mark lives on your profile, so it follows you to every device.",
                    color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 12.dp),
                )
                SupportCta("Sign in", onClick = onProfile)
            }
        }
    }
}

/** The page's one filled control. */
@Composable
private fun SupportCta(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Red, contentColor = OnAccent,
            disabledContainerColor = Red.copy(alpha = .6f), disabledContentColor = OnAccent.copy(alpha = .8f),
        ),
        shape = shape,
        modifier = Modifier.fillMaxWidth().height(50.dp).focusRing(shape),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

/** One thing a tier gives, with a tick. */
@Composable
internal fun SupportTick(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Red, modifier = Modifier.size(18.dp))
        Text(text, color = TextC, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

private val BLOCK_GAP = 14.dp
