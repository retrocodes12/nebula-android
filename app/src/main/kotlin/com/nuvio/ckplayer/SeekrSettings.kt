package com.nuvio.ckplayer

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Settings › Playback › Seekr previews: a key field + Connect (checked with Seekr before it is kept), or Connected +
 * Disconnect. Names Seekr on purpose — an account the viewer connects must be named (the MDBList exception). A TV
 * field types only after OK (tvTyping); a half-typed key survives a sync redraw (the draft is local).
 */
@Composable
internal fun SeekrSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { Seekr.load(ctx); true }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val typing = tvTyping()
    val keyboard = LocalSoftwareKeyboardController.current
    fun connect() {
        val k = draft.trim()
        if (k.isEmpty() || busy) return
        typing.done(); keyboard?.hide()
        if (!SeekrWire.validKey(k)) { note = "That key was refused"; return }
        busy = true; note = null
        scope.launch {
            val r = Seekr.validate(k)
            busy = false
            when (r) {
                Seekr.Check.OK -> { Seekr.set(ctx, k); draft = "" }
                Seekr.Check.REFUSED -> note = "That key was refused"
                Seekr.Check.UNREACHABLE -> note = "Could not reach Seekr"
            }
        }
    }
    SettingsHeader("SEEKR PREVIEWS", "Your own free key from seekr.tv — instant pictures while you scrub films and episodes")
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (Seekr.key.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Connected", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        // the pictures ride on the scrub preview: with it off there is nothing to show them in
                        if (!Prefs.scrubFrames) Text(
                            "Turn on Preview frames while scrubbing to see them",
                            color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    TextAction("Disconnect") { Seekr.set(ctx, ""); note = null }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.trim().take(96); note = null },
                        placeholder = { Text("sk_live_…", color = MutedC) },
                        singleLine = true,
                        readOnly = typing.readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onDone = { connect() }),
                        modifier = typing.modifier.weight(1f).returnTo("seekr-key"),
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
                note?.let {
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            }
        }
    }
}
