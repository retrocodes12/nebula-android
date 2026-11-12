package com.nuvio.ckplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarnC = Color(0xFFFF453A)

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

@Composable
internal fun SupportPerks(rank: Int, tv: Boolean) {
    Spacer(Modifier.height(14.dp))
    if (rank < 2) {
        PerkTeasers(tv)
        return
    }
    VotePanel()
    Spacer(Modifier.height(14.dp))
    RecapCard()
}

/** The monthly plan: its state in words, and the private page where it is managed (cancel, the card, a pause). */
@Composable
internal fun PlanRow(me: Profile) {
    val ctx = LocalContext.current
    // the link is never stored: a fresh start has it again once /me has answered (boot asks; so does this page)
    LaunchedEffect(me.subManage.isEmpty()) { if (me.subManage.isEmpty()) Account.refreshProfile(ctx) }
    val words = Support.subStatusText(me.subStatus)
    SupportRow(
        title = "Manage subscription",
        sub = "Monthly plan" + (if (words.isNotEmpty()) " · $words" else ""),
        warn = me.subStatus == "past_due",
        key = "perk/plan", icon = Icons.Outlined.CreditCard, chevron = true,
    ) {
        val u = me.subManage
        if (u.isEmpty()) { Toasts.show("Getting your plan's page. Try again in a moment."); return@SupportRow }
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toasts.show("No web browser on this device. Manage it from Nebula on your phone.") }
    }
}

@Composable
internal fun EarlyBuildsRow() {
    val ctx = LocalContext.current
    SupportRow(
        title = "Early builds",
        sub = "Get new versions a few days before everyone else. They are tested, but newer.",
        key = "perk/early", icon = Icons.Outlined.Update, checked = Prefs.earlyBuilds,
    ) { Prefs.setEarlyBuilds(ctx, !Prefs.earlyBuilds) }
}

/** Below the Plus level: what the perks are and who gets them, one line each. */
@Composable
private fun PerkTeasers(tv: Boolean) {
    SupportPanel {
        Text("With Supporter Plus or Monthly", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        val lines = listOfNotNull(
            "Any accent colour, and your own mark",
            "A vote on what gets built next",
            "New versions a few days before everyone else",
            "Your month on Nebula: hours, days and top titles",
            if (tv) null else "Another app icon",
        )
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach { SupportTick(it) }
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

/** The hairline card a block of text sits in. */
@Composable
internal fun SupportPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SurfaceC, RoundedCornerShape(16.dp))
            .border(1.dp, LineC, RoundedCornerShape(16.dp)).padding(18.dp),
        content = content,
    )
}

/** The same card with no inset, for rows that run edge to edge with [GroupLine] between them. */
@Composable
internal fun SupportGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(SurfaceC, shape).border(1.dp, LineC, shape), content = content)
}

/** The hairline between rows: it starts under the row's text, as a grouped list's does, unless [full]. */
@Composable
internal fun GroupLine(full: Boolean = false) {
    Box(Modifier.padding(start = if (full) 0.dp else 60.dp).fillMaxWidth().height(1.dp).background(LineC))
}

/** One row of a [SupportGroup]: a tile, a title, one line, and a switch ([checked]), a chevron, or a fold ([open]). The
    whole row is the control: a remote stops once, and the switch only shows the state. */
@Composable
internal fun SupportRow(
    title: String,
    sub: String,
    key: String,
    icon: ImageVector,
    warn: Boolean = false,
    checked: Boolean? = null,
    chevron: Boolean = false,
    open: Boolean? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val action = if (checked != null) {
        Modifier.toggleable(value = checked, interactionSource = interaction, indication = null, role = Role.Switch) { onClick() }
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button) { onClick() }
    }
    Row(
        Modifier.fillMaxWidth().returnTo(key).landingSlot().then(action).then(rowLit(focused))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).background(Surface2, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = TextC, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = if (warn) WarnC else MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (checked != null) Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccent, checkedTrackColor = Red,
                uncheckedThumbColor = Color(0xFF8E8E93), uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
        if (chevron) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FaintC, modifier = Modifier.size(22.dp))
        if (open != null) {
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "fold")
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = FaintC, modifier = Modifier.size(22.dp).rotate(turn))
        }
    }
}
