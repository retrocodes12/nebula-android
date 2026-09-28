package com.nuvio.ckplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Plus-level perks on Settings › Support (2026-09-28, "so the monthly plan actually has something good to offer"):
 * Supporter Plus, Founder and the monthly plan get the vote on what's next (Vote.kt), early builds (Updates.kt), their
 * month on Nebula (WatchLog.kt) and another app icon (AppIcons.kt, Appearance); a monthly supporter also gets their
 * plan's state and the way to manage it. Below that level each perk is one line saying who gets it — no dead controls.
 */
object Perks {
    /** Offer early builds on the update card: the Plus level, and switched on. */
    fun early(): Boolean = Support.rank() >= 2 && Prefs.earlyBuilds
}

private val WarnC = Color(0xFFFF453A)

@Composable
internal fun SupportPerks(rank: Int, tv: Boolean) {
    val me = Cloud.profile
    if (me != null && me.sup && (me.subStatus.isNotEmpty() || me.subManage.isNotEmpty())) {
        Spacer(Modifier.height(12.dp))
        PlanPanel(me)
    }
    if (rank < 2) {
        Spacer(Modifier.height(12.dp))
        PerkTeasers(tv)
        return
    }
    Spacer(Modifier.height(12.dp))
    VotePanel()
    Spacer(Modifier.height(12.dp))
    EarlyBuildsPanel()
    Spacer(Modifier.height(12.dp))
    RecapCard()
}

/** The monthly plan: its state in words, and the private page where it is managed (cancel, the card, a pause). */
@Composable
private fun PlanPanel(me: Profile) {
    val ctx = LocalContext.current
    // the link is never stored: a fresh start has it again once /me has answered (boot asks; so does this page)
    LaunchedEffect(me.subManage.isEmpty()) { if (me.subManage.isEmpty()) Account.refreshProfile(ctx) }
    val words = Support.subStatusText(me.subStatus)
    PerkRow(
        title = "Manage subscription",
        sub = "Monthly plan" + (if (words.isNotEmpty()) " · $words" else ""),
        warn = me.subStatus == "past_due",
        key = "perk/plan",
    ) {
        val u = me.subManage
        if (u.isEmpty()) { Toasts.show("Getting your plan's page — try again in a moment."); return@PerkRow }
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toasts.show("No web browser on this device — manage it from Nebula on your phone.") }
    }
}

@Composable
private fun EarlyBuildsPanel() {
    val ctx = LocalContext.current
    PerkRow(
        title = "Early builds",
        sub = "Get new versions a few days before everyone else. They are tested, but newer.",
        key = "perk/early",
        checked = Prefs.earlyBuilds,
    ) { Prefs.setEarlyBuilds(ctx, !Prefs.earlyBuilds) }
}

/** A perk as one row in the page's panel material: title, one line, and a switch when [checked] is given. */
@Composable
private fun PerkRow(title: String, sub: String, key: String, warn: Boolean = false, checked: Boolean? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .returnTo(key)
            .clip(shape)
            .background(SurfaceC, shape)
            .border(1.dp, LineC, shape)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .then(rowLit(focused))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = if (warn) WarnC else MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (checked != null) Switch(
            checked = checked, onCheckedChange = { onClick() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccent, checkedTrackColor = Red,
                uncheckedThumbColor = Color(0xFF8E8E93), uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** Below the Plus level: what the perks are and who gets them, one line each. */
@Composable
private fun PerkTeasers(tv: Boolean) {
    SupportPanel {
        Text("Supporter Plus and Monthly supporters also", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        val lines = listOfNotNull(
            "vote on what gets built next",
            "get new versions a few days before everyone else",
            "see their month on Nebula — hours, days and top titles",
            if (tv) null else "pick another app icon",
        )
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(FaintC))
                    Text(l, color = MutedC, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

/**
 * About › "Thank you to our supporters": every name on the wall — Founders first, then everyone else — for everyone
 * to see (it is the thank-you). Nothing at all while the wall is empty. Under a remote the block takes focus so the
 * D-pad can scroll down to it (a plain text below the last row was out of the remote's reach).
 */
@Composable
internal fun SupportersThanks() {
    val founders = Support.founders
    val others = Support.others
    if (founders.isEmpty() && others.isEmpty()) return
    val remote = remoteMode()
    SettingsHeader("THANK YOU TO OUR SUPPORTERS")
    Column(
        Modifier.fillMaxWidth()
            .then(if (remote) Modifier.focusRing(RoundedCornerShape(18.dp), landing = false).focusable() else Modifier)
            .background(SurfaceC, RoundedCornerShape(18.dp))
            .border(1.dp, LineC, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (founders.isNotEmpty()) {
            Eyebrow("Founders")
            Text(Support.joinNames(founders), color = TextC, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (others.isNotEmpty()) {
            if (founders.isNotEmpty()) Spacer(Modifier.height(12.dp))
            Eyebrow("Supporters")
            Text(Support.joinNames(others), color = TextC, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
