package com.nuvio.ckplayer

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

/**
 * A PIN on a who's-watching profile (10-23; the same rule and the same hash on every app — brief `pin-2026-10-23.md`).
 * The profile's entry in the synced `viewers` list carries `lock: {s: salt, h: hash}`, both 8 lowercase hex. Four
 * digits cannot be protected by any hash (10 000 tries): this keeps the PIN out of plain sight in the doc, nothing
 * more. A household lock, not security.
 */
object ProfilePin {
    private val HEX8 = Regex("^[0-9a-f]{8}$")
    const val MAX_TRIES = 5
    const val COOL_MS = 30_000L

    data class Lock(val s: String, val h: String)
    /** Wrong PINs in a row on this run, and until when the keys do nothing after the fifth. */
    data class Tries(val fails: Int = 0, val until: Long = 0L)

    /** FNV-1a 32-bit over "salt:pin", 1000 rounds with the hash carried through: 8 lowercase hex digits. */
    fun pinHash(salt: String, pin: String): String {
        val s = "$salt:$pin"
        var h = 0x811C9DC5.toInt()                             // 2166136261, the offset basis, set once
        repeat(1000) { for (c in s) { h = h xor c.code; h *= 16777619 } }   // Int arithmetic wraps mod 2^32
        return h.toUInt().toString(16).padStart(8, '0')
    }

    fun newSalt(): String {
        val b = ByteArray(4).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(java.util.Locale.US, it.toInt() and 0xFF) }
    }

    /** A lock as the wire carries it, or null: a profile is locked only when both fields are 8 lowercase hex. */
    fun lockOf(o: JSONObject?): Lock? {
        if (o == null) return null
        val s = o.optString("s"); val h = o.optString("h")
        return if (HEX8.matches(s) && HEX8.matches(h)) Lock(s, h) else null
    }

    fun lockJson(l: Lock): JSONObject = JSONObject().put("s", l.s).put("h", l.h)
    fun make(pin: String): Lock = newSalt().let { Lock(it, pinHash(it, pin)) }
    fun matches(l: Lock, pin: String): Boolean = pinHash(l.s, pin) == l.h
    fun pinOk(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }

    /** One more wrong PIN: the fifth in a row starts the 30-second wait (and the count again). Pure, for the tests. */
    internal fun wrong(t: Tries, now: Long): Tries =
        if (t.fails + 1 >= MAX_TRIES) Tries(0, now + COOL_MS) else Tries(t.fails + 1, 0L)
    internal fun cooling(t: Tries, now: Long): Boolean = now < t.until

    /** What "Forgot PIN?" says when the password check fails (the HTTP code, null = no answer). */
    internal fun forgotError(code: Int?): String = when (code) {
        403 -> "Wrong password."
        429 -> "Too many tries — wait a minute."
        else -> "Could not reach Nebula. Try again."
    }

    // per profile, this run only: cancelling and opening the PIN screen again does not reset the count
    private val tries = HashMap<String, Tries>()
    fun triesOf(id: String): Tries = tries[id] ?: Tries()
    fun noteWrong(id: String, now: Long): Tries = wrong(triesOf(id), now).also { tries[id] = it }
    fun clearTries(id: String) { tries.remove(id) }
}

private val PinErr = Color(0xFFFF453A)               // the system red, as Remove profile

/** A PIN screen is up: the keyboard's and the remote's digits, Backspace and Esc are its own (MainActivity hears them
    before the browse keys, which would read Backspace as Back). */
internal object PinKeys {
    @Volatile var handler: ((KeyEvent) -> Boolean)? = null
}

/** The editor's name and colour while a PIN step covers it (null = not touched yet: the saved ones). */
internal class WhoDraft {
    var name by mutableStateOf<String?>(null)
    var av by mutableStateOf<String?>(null)
}

internal enum class PinMode { CHECK, CHOOSE, CONFIRM }
/** What the PIN screen was opened for: entering the profile, editing it, or setting its PIN from the editor. */
internal enum class PinFor { ENTER, EDIT, SET }
internal data class PinAsk(val id: String, val mode: PinMode, val purpose: PinFor, val first: String = "", val note: String = "")

/**
 * The PIN screen, inside the picker overlay: the profile's monogram, the question, four dots, one line of message and
 * a keypad that works by touch, mouse and D-pad (the remote lands on 1; nothing is lit under a finger). [onAsk] moves
 * to the next step (choose → confirm), [onPassed] = the PIN was right or was set, [onCancel] = back where it came from.
 */
@Composable
internal fun PinScreen(ask: PinAsk, onAsk: (PinAsk) -> Unit, onPassed: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val v = remember(ask.id, Viewers.version) { Viewers.list(ctx).firstOrNull { it.id == ask.id } }
    if (v == null) { LaunchedEffect(Unit) { onCancel() }; return }        // (removed on another device meanwhile)
    val name = Viewers.name(v)
    val tv = Account.isTv(ctx)
    val remote = remoteMode()
    var digits by remember(ask) { mutableStateOf("") }
    var until by remember(ask) { mutableLongStateOf(ProfilePin.triesOf(ask.id).until) }
    var msg by remember(ask) {
        mutableStateOf(if (ProfilePin.cooling(ProfilePin.triesOf(ask.id), System.currentTimeMillis())) "Too many tries — wait 30 seconds." else ask.note)
    }
    var forgot by remember(ask) { mutableStateOf(false) }
    val one = remember { FocusRequester() }

    fun complete(pin: String) {
        when (ask.mode) {
            PinMode.CHECK -> {
                val lock = v.lock
                if (lock == null || ProfilePin.matches(lock, pin)) {   // (no lock: removed elsewhere meanwhile)
                    ProfilePin.clearTries(ask.id); Viewers.unlock(ask.id); onPassed()
                } else {
                    val t = ProfilePin.noteWrong(ask.id, System.currentTimeMillis())
                    digits = ""
                    until = t.until
                    msg = if (t.until > 0L) "Too many tries — wait 30 seconds." else "Wrong PIN."
                }
            }
            PinMode.CHOOSE -> onAsk(ask.copy(mode = PinMode.CONFIRM, first = pin, note = ""))
            PinMode.CONFIRM ->
                if (pin == ask.first) {
                    Viewers.setPin(ctx, ask.id, pin)
                    Toasts.show("PIN set for $name.")
                    onPassed()
                } else onAsk(ask.copy(mode = PinMode.CHOOSE, first = "", note = "The PINs didn't match — try again."))
        }
    }
    fun type(d: Char) {
        if (forgot || System.currentTimeMillis() < until || digits.length >= 4) return
        if (digits.isEmpty()) msg = ""                         // a note (wrong, didn't match) stays until the next digit
        digits += d
        if (digits.length == 4) complete(digits)
    }
    fun back() { if (!forgot && System.currentTimeMillis() >= until && digits.isNotEmpty()) digits = digits.dropLast(1) }

    // the 30-second wait ends by itself
    LaunchedEffect(until) {
        val wait = until - System.currentTimeMillis()
        if (wait > 0) { delay(wait); msg = ""; until = 0L }
    }
    // digits, Backspace and Esc from a keyboard or a remote's number keys (not while the password field is up)
    val typeNow by rememberUpdatedState({ d: Char -> type(d) })
    val backNow by rememberUpdatedState({ back() })
    val cancelNow by rememberUpdatedState({ if (forgot) { forgot = false } else onCancel() })
    val forgotNow by rememberUpdatedState(forgot)
    DisposableEffect(Unit) {
        val h: (KeyEvent) -> Boolean = h@{ e ->
            if (forgotNow && e.keyCode != KeyEvent.KEYCODE_ESCAPE) return@h false
            val digit = when (e.keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0' + (e.keyCode - KeyEvent.KEYCODE_0)
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (e.keyCode - KeyEvent.KEYCODE_NUMPAD_0)
                else -> null
            }
            val down = e.action == KeyEvent.ACTION_DOWN
            when {
                digit != null -> { if (down && e.repeatCount == 0) typeNow(digit); true }
                e.keyCode == KeyEvent.KEYCODE_DEL -> { if (down) backNow(); true }      // both halves: the UP is Back otherwise
                e.keyCode == KeyEvent.KEYCODE_ESCAPE -> { if (down && e.repeatCount == 0) cancelNow(); true }
                else -> false
            }
        }
        PinKeys.handler = h
        onDispose { if (PinKeys.handler === h) PinKeys.handler = null }
    }
    androidx.activity.compose.BackHandler { cancelNow() }
    LaunchedEffect(ask, forgot, remote) { if (remote && !forgot) runCatching { withFrameNanos {}; one.requestFocus() } }

    val title = when (ask.mode) {
        PinMode.CHECK -> "Enter the PIN for $name"
        PinMode.CHOOSE -> "Choose a 4-digit PIN for $name"
        PinMode.CONFIRM -> "Enter the same PIN again"
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .padding(top = if (tv) 36.dp else 64.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WhoMonogram(Viewers.avatar(v), name, if (tv) 84.dp else 96.dp)
        Text(
            title, color = TextC, fontSize = 24.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp).padding(top = 22.dp),
        )
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(4) { i ->
                val on = i < digits.length
                Box(Modifier.size(16.dp).background(if (on) TextC else Color.Transparent, CircleShape).border(1.5.dp, if (on) TextC else Line2, CircleShape))
            }
        }
        // one line, kept even when empty so nothing jumps when a message comes and goes
        Text(
            msg, color = PinErr, fontSize = 14.sp, fontFamily = Sans, textAlign = TextAlign.Center, maxLines = 2,
            modifier = Modifier.widthIn(max = 420.dp).heightIn(min = 20.dp).padding(top = 14.dp),
        )
        if (forgot) PinForgot(ask.id, onRemoved = {
            Toasts.show("PIN removed — add a new one from Manage Profiles.")
            onPassed()
        })
        else Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            val key = if (tv) 64.dp else 72.dp
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { k ->
                        if (k.isEmpty()) Box(Modifier.size(key)) else Box(
                            Modifier.size(key)
                                .then(if (k == "1") Modifier.focusRequester(one) else Modifier)
                                .clip(CircleShape)
                                .background(if (k == "⌫") Color.Transparent else SurfaceC, CircleShape)
                                .border(1.dp, if (k == "⌫") Color.Transparent else LineC, CircleShape)
                                .focusRing(CircleShape, landing = false)
                                .clickable { if (k == "⌫") back() else type(k[0]) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = TextC, modifier = Modifier.size(26.dp))
                            else Text(k, color = TextC, fontSize = 28.sp, fontFamily = Sans, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if (ask.mode == PinMode.CHECK && !forgot) PinTextButton("Forgot PIN?", MutedC, Modifier.padding(top = 24.dp)) { forgot = true }
        PinTextButton("Cancel", MutedC, Modifier.padding(top = if (forgot || ask.mode != PinMode.CHECK) 24.dp else 6.dp)) { cancelNow() }
    }
}

@Composable
private fun PinTextButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pill = RoundedCornerShape(50)
    Text(
        text, color = color, fontSize = 16.sp, fontFamily = Sans, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(pill).focusRing(pill, landing = false).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** "Forgot PIN?": the account's password takes the PIN off (POST /v1/profile/check — nothing changes on the server). */
@Composable
private fun PinForgot(id: String, onRemoved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (Cloud.state(ctx) != "in") {
        Text(
            "Sign in to your Nebula account on this device first — its password resets the PIN.",
            color = MutedC, fontSize = 15.sp, fontFamily = Sans, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp).padding(top = 18.dp),
        )
        return
    }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val go = {
        if (!busy) {
            busy = true; err = ""
            scope.launch {
                val e = Account.checkPassword(ctx, pw)
                busy = false
                if (e == null) { Viewers.removePin(ctx, id); onRemoved() } else err = e
            }
        }
    }
    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PField(pw, { pw = it }, "Password", password = true, last = true, onDone = { go() })
        if (err.isNotEmpty()) Text(err, color = PinErr, fontSize = 14.sp, fontFamily = Sans, modifier = Modifier.padding(bottom = 8.dp))
        val pill = RoundedCornerShape(50)
        Box(
            Modifier.padding(top = 8.dp).clip(pill).background(Color(0xFFF2F2F7).copy(alpha = if (busy) 0.6f else 1f), pill)
                .focusRing(pill, landing = false).clickable(enabled = !busy) { go() }
                .padding(horizontal = 32.dp, vertical = 14.dp),
        ) { Text("Remove the PIN", color = Color.Black, fontSize = 17.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold) }
    }
}
