package com.nuvio.ckplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** This build's version name, for the services that ask which app is calling. */
internal fun trackAppVersion(ctx: android.content.Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()

/**
 * Settings › Playback › Tracking (the web player's three rows): Trakt and Simkl sign in with a code typed on the
 * service's own page — nothing of theirs is typed here; MDBList takes the person's API key. A service shows only when
 * the cloud says it is set up, or when this profile is already connected to it.
 */
@Composable
internal fun TrackSettings() {
    val ctx = LocalContext.current
    remember { Track.load(ctx); true }
    LaunchedEffect(Unit) { Track.config(ctx) }
    val d = Track.doc
    val trakt = Track.traktId.isNotEmpty() || d.trakt.a.isNotEmpty()
    val simkl = Track.simklId.isNotEmpty() || d.simkl.a.isNotEmpty()
    val mdbl = Track.mdblOffered || d.mdbl.key.isNotEmpty()
    if (!trakt && !simkl && !mdbl) return
    SettingsHeader("TRACKING", "The films and episodes you watch, added to your own account's history")
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
            if (trakt) TrackCodeRow("trakt", "Trakt", d.trakt, Track.traktId.isNotEmpty())
            if (simkl) TrackCodeRow("simkl", "Simkl", d.simkl, Track.simklId.isNotEmpty())
            if (mdbl) TrackMdblRow(d.mdbl)
        }
    }
}

@Composable
private fun TrackCodeRow(svc: String, name: String, auth: TrackAuth, offered: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember { trackAppVersion(ctx) }
    val tv = remember { Account.isTv(ctx) }
    var code by remember { mutableStateOf<Track.Code?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val on = auth.a.isNotEmpty()
    // the code is polled while it shows: approved → connected; run out or declined → said, and Connect comes back
    LaunchedEffect(code) {
        val c = code ?: return@LaunchedEffect
        while (true) {
            delay(c.everyMs)
            when (Track.poll(ctx, c, version)) {
                Track.Poll.PENDING -> {}
                Track.Poll.DONE -> { code = null; note = null; Toasts.show("$name connected"); break }
                Track.Poll.EXPIRED -> { code = null; note = "That code ran out. Connect again for a new one"; break }
                Track.Poll.DENIED -> { code = null; note = "The sign-in was declined"; break }
                Track.Poll.SETUP -> { code = null; note = "$name sign-in is not available right now"; break }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Track on $name", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val c = code
                Text(
                    when {
                        on -> "Connected" + (if (auth.user.isNotEmpty()) " as ${auth.user}" else "") + (if (Cloud.linked(ctx)) " · Every device on this profile reports" else "")
                        c != null -> "On a phone or computer, open " + c.url.removePrefix("https://") + " and type this code"
                        else -> "Sign in with a code, nothing is typed here"
                    },
                    color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (on) TextAction("Disconnect") { Track.disconnect(ctx, svc); note = null }
            else if (code == null && offered) Button(
                onClick = {
                    if (busy) return@Button
                    busy = true; note = null
                    scope.launch {
                        val c = Track.askCode(svc, version)
                        busy = false
                        if (c == null) note = "$name did not answer. Try again in a moment" else code = c
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.focusRing(RoundedCornerShape(12.dp), landing = false),
            ) { Text(if (busy) "Asking…" else "Connect", fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
        code?.let { c ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(c.user, color = TextC, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                // a phone opens the page itself (the code is already in the address); a TV has no browser worth the trip
                if (!tv) TextAction("Open the page") {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.full)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                TextAction("Cancel") { code = null }
            }
        }
        note?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun TrackMdblRow(k: TrackKey) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val typing = tvTyping()
    val keyboard = LocalSoftwareKeyboardController.current
    val on = k.key.isNotEmpty()
    fun connect() {
        val v = draft.trim()
        if (v.isEmpty() || busy) return
        typing.done(); keyboard?.hide()
        if (!TrackWire.validMdblKey(v)) { note = "That does not look like an MDBList API key"; return }
        busy = true; note = null
        scope.launch {
            val (ok, said) = Track.mdblVerify(v)
            busy = false
            if (ok) { Track.mdblConnect(ctx, v, said); draft = ""; Toasts.show("MDBList connected") } else note = said
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Track on MDBList", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (on) "Connected" + (if (k.user.isNotEmpty()) " as ${k.user}" else "") + " · key ending " + k.key.takeLast(4) +
                        (if (Cloud.linked(ctx)) " · Every device on this profile reports" else "")
                    else "Paste your API key from mdblist.com › Preferences",
                    color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (on) TextAction("Disconnect") { Track.disconnect(ctx, "mdbl"); note = null }
        }
        if (!on) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.trim().take(128); note = null },
                    placeholder = { Text("API key", color = MutedC) },
                    singleLine = true,
                    readOnly = typing.readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                    modifier = typing.modifier.weight(1f).returnTo("mdbl-key"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
                // never disabled while checking: a disabled control drops a remote's focus
                Button(
                    onClick = { connect() },
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.focusRing(RoundedCornerShape(12.dp), landing = false),
                ) { Text(if (busy) "Checking…" else "Connect", fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
        note?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}
