package com.nuvio.ckplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The watch party's card in the player (the shared player's #partyCard): the code, who is here and what each is doing,
 * the chat, the reactions, and the host's controls. Every press reaches a remote: the rows are TextActions / chips.
 */
@Composable
internal fun PartyRoom(
    code: String, isHost: Boolean, v2: Boolean, hostOnly: Boolean, hostV2: Boolean,
    members: List<PartyMember>, me: String, myStatus: String,
    chat: List<PartyChatLine>, ask: PartyStreamDesc?,
    onClose: () -> Unit, onInvite: () -> Unit, onSend: (String) -> Unit, onKick: (String) -> Unit,
    onHostOnly: (Boolean) -> Unit, onLeave: () -> Unit, onEnd: () -> Unit,
    onAskYes: () -> Unit, onAskNo: () -> Unit, onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    // a press that asks for a second one (Remove, End for everyone): kept by key, the list redraws under it
    var armed by remember { mutableStateOf("") }
    var armedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(armed, armedAt) { if (armed.isNotEmpty()) { delay(3500); armed = "" } }
    fun twoStep(key: String, go: () -> Unit) {
        if (armed == key && System.currentTimeMillis() - armedAt < 3500) { armed = ""; go() }
        else { armed = key; armedAt = System.currentTimeMillis() }
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.widthIn(max = 380.dp)
            .background(Color(0xF5141419), shape)
            .border(1.dp, Color(0x24FFFFFF), shape)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Watch together", color = TextC, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (v2) Text("${members.size.coerceAtLeast(1)} of 25 seats", color = FaintC, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
            Box(Modifier.focusRequester(first)) { TextAction("Close") { onClose() } }
        }
        if (ask != null) {
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("Switch the party to ${ask.name ?: ask.title}? Everyone will start watching it with you.", color = TextC, fontSize = 13.sp, lineHeight = 18.sp)
                Row(Modifier.padding(top = 6.dp)) {
                    Chip("Switch", true) { onAskYes() }
                    Box(Modifier.padding(start = 8.dp)) { Chip("Not now", false) { onAskNo() } }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(code, color = TextC, fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
            TextAction("Invite") { onInvite() }
        }
        Text("play.rifflehq.in/?party=$code", color = MutedC, fontSize = 11.sp)
        Eyebrow("MEMBERS", Modifier.padding(top = 14.dp, bottom = 4.dp))
        val list = members.ifEmpty { listOf(PartyMember(me, "You", isHost, "ready", "", null, 2)) }
        list.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(avColor(m.name), CircleShape), contentAlignment = Alignment.Center) {
                    Text(m.name.take(1).uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    val tags = listOfNotNull("You".takeIf { m.id == me }, "Host".takeIf { m.host }).joinToString(" · ")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, color = TextC, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (tags.isNotEmpty()) Text("  $tags", color = FaintC, fontSize = 11.sp)
                    }
                    val stLabel = if (m.id == me) myStatus else PartySync.statusLabel(m)
                    if (stLabel.isNotEmpty()) Text(stLabel, color = if (m.s == "buffering" || m.s == "loading" || m.s == "choosing") Color(0xFFFFD479) else MutedC, fontSize = 11.5.sp)
                }
                if (isHost && v2 && m.id != me) TextAction(if (armed == "rm:" + m.id) "Remove ${m.name}?" else "Remove", danger = armed == "rm:" + m.id) { twoStep("rm:" + m.id) { onKick(m.id) } }
            }
        }
        if (v2 && !isHost && (hostOnly || !hostV2)) Text("Only the host can control playback. You can still pause or rewind for yourself.", color = FaintC, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
        if (v2) {
            Eyebrow("CHAT", Modifier.padding(top = 14.dp, bottom = 4.dp))
            val listState = rememberLazyListState()
            LaunchedEffect(chat.size) { if (chat.isNotEmpty()) listState.scrollToItem(chat.size - 1) }
            if (chat.isEmpty()) Text("No messages yet. Only people in this party can see the chat.", color = FaintC, fontSize = 12.sp)
            else LazyColumn(Modifier.heightIn(max = 150.dp), state = listState) {
                items(chat) { c ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text((if (c.by == me) "You" else c.name) + "  ", color = if (c.by == me) TextC else MutedC, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(c.text + if (c.fail) "  · Not sent" else "", color = if (c.fail) Color(0xFFFF453A) else if (c.pend) FaintC else TextC, fontSize = 13.sp)
                    }
                }
            }
            var draft by remember { mutableStateOf("") }
            val typing = tvTyping()
            fun send() { val t = draft.trim().take(200); if (t.isNotEmpty()) { onSend(t); draft = "" }; typing.done() }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it.take(200) },
                    placeholder = { Text("Message", color = MutedC) }, singleLine = true, readOnly = typing.readOnly,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }, onDone = { send() }),
                    modifier = typing.modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
                Box(Modifier.padding(start = 8.dp)) { Chip("Send", draft.isNotBlank()) { send() } }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("👍", "😂", "❤️", "😮", "🔥").forEach { e -> Chip(e, false) { onReact(e) } }
        }
        Column(Modifier.padding(top = 10.dp)) {
            if (isHost && v2) Chip("Only I can control playback · " + if (hostOnly) "On" else "Off", hostOnly) { onHostOnly(!hostOnly) }
            TextAction("Leave party") { onLeave() }
            if (isHost && v2) TextAction(if (armed == "end") "Press again to end it" else "End for everyone", danger = true) { twoStep("end") { onEnd() } }
        }
    }
}

private fun avColor(name: String): Color {
    var h = 0L
    name.forEach { h = (h * 31 + it.code) and 0xffffffffL }
    return Color.hsl((h % 360).toFloat(), 0.38f, 0.42f)
}

/** Waiting for Ana · Paused for you · 2 min behind · Catch up — bottom left, outside the chrome. */
@Composable
internal fun PartyPillText(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    GlassPill(text, modifier = modifier) { onClick?.invoke() }
}

/** Incoming chat lines while the card is closed: the last three, gone after six seconds. */
@Composable
internal fun PartyFeed(lines: List<Triple<Long, String, String>>, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 460.dp)) {
        lines.takeLast(3).forEach { (at, name, text) ->
            var shown by remember(at) { mutableStateOf(true) }
            LaunchedEffect(at) { delay(6000); shown = false }
            if (shown) Row(
                Modifier.padding(top = 6.dp).background(Color(0xDB1C1C1E), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("$name  ", color = TextC, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(text, color = TextC, fontSize = 13.5.sp)
            }
        }
    }
}

/** Settings › Watch party: a row with a switch. */
@Composable
internal fun PartyPrefRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp)
            .background(SurfaceC, RoundedCornerShape(12.dp)).border(1.dp, LineC, RoundedCornerShape(12.dp))
            .focusRing(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked, onCheckedChange = { onChange(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = Red, uncheckedThumbColor = Color(0xFF8E8E93), uncheckedBorderColor = Color.Transparent),
        )
    }
}
