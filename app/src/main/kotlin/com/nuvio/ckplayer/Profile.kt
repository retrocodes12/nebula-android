package com.nuvio.ckplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Nebula Profile screens — the Android twin of the web player's profile view:
// signed out (sign in · create · sign in with a code), an old link-code group that
// has no profile yet ("legacy"), and signed in (name, colour, TV link, devices,
// password, sign out, delete). Errors stay red while the accent can be anything.
private val ErrC = Color(0xFFFF453A)

/** Settings home: who is signed in here, or the invitation to be. */
@Composable
internal fun ProfileCard(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val p = Cloud.profile
    val state = Cloud.state(ctx)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().background(SurfaceC, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else LineC, shape)
            .clip(shape).clickable(interactionSource = interaction, indication = null) { onOpen() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(p?.avatar ?: "", p?.let { it.name.ifEmpty { it.handle } } ?: "?", 48.dp, dim = p == null)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        p != null -> p.name.ifEmpty { "@${p.handle}" }
                        state == "legacy" -> "Add a profile"
                        else -> "Sign in or create a profile"
                    },
                    color = TextC, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (p?.sup == true) SupporterMark()
            }
            val n = Cloud.devices.size
            Text(
                when {
                    p != null -> "@${p.handle}" + (if (n > 1) " · $n devices" else "")
                    state == "legacy" -> "This device syncs with a link code from an earlier version"
                    else -> "Sync add-ons, progress and My List across your devices"
                },
                color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FaintC, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun ProfileScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tv = remember { Account.isTv(ctx) }
    var gen by remember { mutableStateOf(0) }          // bumps when the link changes without the profile changing
    val profile = Cloud.profile
    val state = remember(profile, gen) { Cloud.state(ctx) }
    var recovery by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }   // message · is it an error
    val say: (String) -> Unit = { status = it to false }
    val fail: (String) -> Unit = { status = it to true }

    LaunchedEffect(profile?.handle) { if (profile != null) Account.refreshProfile(ctx) }

    // the floating pill nav sits over this screen (a tab root since the nav gained the avatar), so the last rows clear it
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp)) {
        BackBar(
            "Profile",
            when {
                profile != null -> "@${profile.handle}"
                state == "legacy" -> "Link code from an earlier version"
                else -> "No email, no tracking — a profile is optional"
            },
            onBack,
        )
        status?.let { (m, err) ->
            Text(m, color = if (err) ErrC else MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        val key = recovery
        when {
            key != null -> RecoveryPanel(profile, key) { recovery = null }
            profile != null -> SignedIn(profile, tv, say, fail)
            else -> SignedOut(state == "legacy", tv, say, fail, onRecovery = { recovery = it }, onForget = { gen++ })
        }
        Spacer(Modifier.height(96.dp))
    }
}

// ---------- signed out / legacy ----------
@Composable
private fun SignedOut(
    legacy: Boolean, tv: Boolean, say: (String) -> Unit, fail: (String) -> Unit,
    onRecovery: (String) -> Unit, onForget: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember(legacy) { mutableStateOf(if (legacy) "attach" else if (tv) "code" else "signin") }
    Text(
        if (legacy) "This device syncs with a link code from an earlier version. Give it a profile — everything you’ve synced stays exactly where it is, and every device signs in by handle from now on."
        else "Your add-ons, progress, My List and ratings follow you to every device you sign in on. No email, no tracking — a profile is optional.",
        color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 14.dp),
    )
    val modes = when {
        legacy -> listOf("attach" to "Add a profile", "signin" to "Sign in to another")
        tv -> listOf("code" to "Sign in with a code", "signin" to "Handle and password", "create" to "Create profile")
        else -> listOf("signin" to "Sign in", "create" to "Create profile", "code" to "Sign in with a code")
    }
    Segmented {
        modes.forEach { (k, label) ->
            Chip(label, mode == k || (mode == "recover" && k == "signin"), inSeg = true) { mode = k }
        }
    }
    Spacer(Modifier.height(14.dp))
    Panel {
        when (mode) {
            "signin" -> SignInForm(legacy, say, fail, onForgot = { mode = "recover" })
            "recover" -> RecoverForm(say, fail, onRecovery, onBackToSignIn = { mode = "signin" })
            "code" -> CodeForm(fail)
            else -> CreateForm(attach = legacy, say, fail, onRecovery)
        }
    }
    if (legacy) Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("This device", color = MutedC, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextAction("Stop syncing here") {
            scope.launch { Cloud.flushAndWait(ctx); Cloud.forget(ctx); onForget(); say("Sync is off for this device. Nothing was deleted.") }
        }
    }
}

@Composable
private fun SignInForm(legacy: Boolean, say: (String) -> Unit, fail: (String) -> Unit, onForgot: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var handle by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun go() {
        if (busy) return
        busy = true
        scope.launch {
            val err = Account.signIn(ctx, handle, pw)
            busy = false
            if (err == null) say("Signed in as @${Account.cleanHandle(handle)} — pulling your things…") else fail(err)
        }
    }
    PanelLabel("Sign in")
    PField(handle, { handle = it.take(24) }, "@handle")
    PField(pw, { pw = it }, "Password", password = true, last = true, onDone = { go() })
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PButton(if (busy) "Signing in…" else "Sign in", busy) { go() }
        TextAction("Forgot password?", onClick = onForgot)
    }
    if (legacy) Hint("Signing in moves this device to that profile. What’s on it merges in; the link-code group stays for the other devices.")
}

@Composable
private fun CreateForm(attach: Boolean, say: (String) -> Unit, fail: (String) -> Unit, onRecovery: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var handle by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun go() {
        if (busy) return
        busy = true
        scope.launch {
            val (key, err) = Account.createProfile(ctx, handle, name, pw)
            busy = false
            if (key != null) { say("Welcome, @${Account.cleanHandle(handle)}."); onRecovery(key) } else fail(err ?: "Something went wrong.")
        }
    }
    PanelLabel(if (attach) "Add a profile to this device’s group" else "Create a profile")
    PField(handle, { handle = it.take(24) }, "Choose a handle — letters, numbers, _")
    PField(name, { name = it.take(40) }, "Display name — what friends see", words = true)
    PField(pw, { pw = it }, "Password — 8 characters or more", password = true, last = true, onDone = { go() })
    Row(Modifier.padding(top = 4.dp)) {
        PButton(if (busy) (if (attach) "Adding…" else "Creating…") else if (attach) "Add profile" else "Create profile", busy) { go() }
    }
    Hint("No email. You’ll get a recovery key once — it’s the only way back in if you forget the password.")
}

@Composable
private fun RecoverForm(say: (String) -> Unit, fail: (String) -> Unit, onRecovery: (String) -> Unit, onBackToSignIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var handle by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun go() {
        if (busy) return
        busy = true
        scope.launch {
            val (fresh, err) = Account.recover(ctx, handle, key, pw)
            busy = false
            if (fresh != null) { say("Password reset — you’re signed in."); onRecovery(fresh) } else fail(err ?: "Something went wrong.")
        }
    }
    PanelLabel("Reset your password")
    PField(handle, { handle = it.take(24) }, "@handle")
    PField(key, { key = it.take(24) }, "Recovery key", caps = true)
    PField(pw, { pw = it }, "New password — 8 characters or more", password = true, last = true, onDone = { go() })
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PButton(if (busy) "Resetting…" else "Reset password", busy) { go() }
        TextAction("Back to sign in", onClick = onBackToSignIn)
    }
    Hint("This signs out every other device and gives you a fresh recovery key.")
}

/** TV side: show a code, poll until a signed-in phone or computer approves it. */
@Composable
private fun CodeForm(fail: (String) -> Unit) {
    val ctx = LocalContext.current
    var tv by remember { mutableStateOf<TvCode?>(null) }
    var expired by remember { mutableStateOf(false) }
    var gen by remember { mutableStateOf(0) }
    LaunchedEffect(gen) {
        tv = null; expired = false
        val t = Account.tvStart(ctx)
        if (t == null) { fail("Could not get a code."); return@LaunchedEffect }
        tv = t
        while (true) {
            delay(3000)
            when (Account.tvPoll(ctx, t)) {
                "done" -> return@LaunchedEffect        // Cloud.profile changed — the screen re-renders signed in
                "expired" -> { expired = true; return@LaunchedEffect }
            }
        }
    }
    PanelLabel("Sign in with a code")
    val code = tv?.code
    Text(
        when { expired -> "Expired"; code == null -> "······"; else -> code.chunked(3).joinToString(" ") },
        color = if (code != null && !expired) TextC else FaintC, fontFamily = Mono, fontSize = 44.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 4.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp).background(Surface2, RoundedCornerShape(14.dp)).padding(vertical = 18.dp),
    )
    Text(
        if (expired) "That code has expired — get a new one."
        else "On a phone or computer where you’re signed in, open Settings › Profile › Link a TV and type this code.",
        color = MutedC, fontSize = 14.sp, lineHeight = 21.sp,
    )
    Row(Modifier.padding(top = 6.dp)) { TextAction("Get a new code") { gen++ } }
}

// ---------- recovery key ----------
@Composable
private fun RecoveryPanel(me: Profile?, key: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    if (me != null) ProfileHead(me)
    Panel {
        PanelLabel("Your recovery key")
        Text(
            "Write it down or keep it somewhere safe. It is shown once, and it is the only way to reset your password — there is no email to send one to.",
            color = MutedC, fontSize = 14.sp, lineHeight = 21.sp,
        )
        Text(
            key, color = TextC, fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).background(Surface2, RoundedCornerShape(12.dp)).padding(vertical = 16.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PButton("I’ve saved it", false, onClick = onDone)
            TextAction("Copy") {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Nebula recovery key", key))
                if (Build.VERSION.SDK_INT < 33) Toasts.show("Copied")
            }
        }
    }
}

// ---------- signed in ----------
@Composable
private fun ProfileHead(me: Profile) {
    Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Avatar(me.avatar, me.name.ifEmpty { me.handle }, 72.dp)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(me.name.ifEmpty { "@${me.handle}" }, color = TextC, fontSize = 24.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (me.sup) SupporterMark(17.dp)
            }
            Text("@${me.handle}", color = MutedC, fontFamily = Mono, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun SignedIn(me: Profile, tv: Boolean, say: (String) -> Unit, fail: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    ProfileHead(me)
    Panel {
        PanelLabel("Profile")
        var name by remember(me.name) { mutableStateOf(me.name) }
        fun saveName() {
            val v = name.trim()
            if (v.isEmpty() || v == me.name) return
            scope.launch { val err = Account.updateProfile(ctx, name = v); if (err == null) say("Name saved.") else fail(err) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Name", color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("What friends and watch parties see", color = MutedC, fontSize = 12.sp, lineHeight = 16.sp)
            }
            PField(name, { name = it.take(40) }, "Name", words = true, last = true, onDone = { saveName() },
                modifier = Modifier.width(168.dp).onFocusChanged { if (!it.isFocused) saveName() }, fill = false)
        }
        Text("Colour", color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Your monogram, wherever you appear", color = MutedC, fontSize = 12.sp, lineHeight = 16.sp)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Account.AVATARS.forEach { a ->
                val on = me.avatar.equals(a, ignoreCase = true)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(avatarColor(a))
                        .border(if (on) 3.dp else 1.dp, if (on) Color.White else Color(0x33FFFFFF), CircleShape)
                        .clickable { scope.launch { Account.updateProfile(ctx, avatar = a)?.let(fail) } },
                )
            }
        }
    }
    val link: @Composable () -> Unit = { LinkTvPanel(me, say, fail) }
    val devs: @Composable () -> Unit = { DevicesPanel(say, fail) }
    if (tv) { devs(); link() } else { link(); devs() }
    PasswordPanel(say, fail)
    ThisDevicePanel(me, say, fail)
}

@Composable
private fun LinkTvPanel(me: Profile, say: (String) -> Unit, fail: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun go() {
        if (busy) return
        busy = true
        scope.launch {
            val (name, err) = Account.approveTv(ctx, code)
            busy = false
            if (name != null) { code = ""; say("$name is signed in as @${me.handle}.") } else fail(err ?: "Something went wrong.")
        }
    }
    Panel {
        PanelLabel("Link a TV")
        Text(
            "On the TV, open Settings › Profile › Sign in with a code and type its code here. Only approve a code that is on a screen in front of you.",
            color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PField(code, { code = it.take(8) }, "Code from the TV", caps = true, last = true, onDone = { go() }, modifier = Modifier.weight(1f), fill = false)
            PButton(if (busy) "Approving…" else "Approve", busy) { go() }
        }
    }
}

private fun whenText(t: Long): String {
    if (t <= 0) return ""
    val d = ((System.currentTimeMillis() - t) / 86_400_000L).toInt()
    return when {
        d <= 0 -> "today"
        d == 1 -> "yesterday"
        d < 30 -> "$d days ago"
        else -> SimpleDateFormat("d MMM", Locale.US).format(Date(t))
    }
}

private fun devIcon(plat: String): ImageVector = when (plat) {
    "webos", "tv", "androidtv" -> Icons.Filled.Tv
    "windows", "mac", "linux" -> Icons.Filled.Computer
    "android", "ios" -> Icons.Filled.PhoneAndroid
    else -> Icons.Filled.Language
}

@Composable
private fun DevicesPanel(say: (String) -> Unit, fail: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val devs = Cloud.devices
    Panel {
        PanelLabel("Devices")
        if (devs.isEmpty()) Text("Only this device.", color = MutedC, fontSize = 14.sp)
        devs.forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(devIcon(d.plat), contentDescription = null, tint = MutedC, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (d.me) Eyebrow("this device", Modifier.padding(start = 8.dp))
                    }
                    val at = whenText(d.at); val seen = whenText(d.seen)
                    Text("Signed in $at" + (if (seen.isNotEmpty() && seen != at) " · last used $seen" else ""), color = MutedC, fontSize = 12.sp)
                }
                if (!d.me) TextAction("Remove") {
                    scope.launch { val err = Account.removeDevice(ctx, d.id); if (err == null) say("${d.name} is signed out.") else fail(err) }
                }
            }
            if (i < devs.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(LineC))
        }
    }
}

@Composable
private fun PasswordPanel(say: (String) -> Unit, fail: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var cur by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun go() {
        if (busy) return
        busy = true
        scope.launch {
            val err = Account.changePassword(ctx, cur, next)
            busy = false
            if (err == null) { open = false; cur = ""; next = ""; say("Password changed — every other device is signed out.") } else fail(err)
        }
    }
    Panel {
        PanelLabel("Password")
        if (!open) Row { GhostButton("Change password") { open = true } }
        else {
            PField(cur, { cur = it }, "Current password", password = true)
            PField(next, { next = it }, "New password — 8 characters or more", password = true, last = true, onDone = { go() })
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PButton(if (busy) "Saving…" else "Save password", busy) { go() }
                TextAction("Cancel") { open = false; cur = ""; next = "" }
            }
        }
        Hint("Changing it signs out every other device.")
    }
}

@Composable
private fun ThisDevicePanel(me: Profile, say: (String) -> Unit, fail: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var delOpen by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Panel {
        PanelLabel("This device")
        Text(
            "Signing out leaves everything on this device in place — it just stops following your profile.",
            color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton("Sign out") {
                if (busy) return@GhostButton
                busy = true
                scope.launch { Account.signOut(ctx); busy = false; say("Signed out on this device. Nothing here was deleted.") }
            }
            TextAction("Delete profile…", danger = true) { delOpen = true }
        }
        if (delOpen) {
            Text(
                "This deletes your profile, its synced data and its Friends from the server, and frees @${me.handle}. What is on your devices stays. Type your password to confirm.",
                color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
            )
            PField(pw, { pw = it }, "Password", password = true, last = true)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PButton(if (busy) "Deleting…" else "Delete profile", busy, danger = true) {
                    if (busy) return@PButton
                    busy = true
                    scope.launch {
                        val err = Account.deleteProfile(ctx, pw)
                        busy = false
                        if (err == null) say("@${me.handle} is deleted. Everything on this device stays.") else fail(err)
                    }
                }
                TextAction("Cancel") { delOpen = false; pw = "" }
            }
        }
    }
}

// ---------- atoms ----------
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp)
            .background(SurfaceC, RoundedCornerShape(18.dp))
            .border(1.dp, LineC, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun PanelLabel(text: String) = Eyebrow(text, Modifier.padding(bottom = 12.dp), color = MutedC)

@Composable
private fun Hint(text: String) =
    Text(text, color = FaintC, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))

/** The account-page text field; Support.kt reuses it for the supporter code. */
@Composable
internal fun PField(
    value: String, onChange: (String) -> Unit, placeholder: String,
    password: Boolean = false, caps: Boolean = false, words: Boolean = false, last: Boolean = false,
    onDone: () -> Unit = {}, modifier: Modifier = Modifier, fill: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder, color = FaintC, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = when { caps -> KeyboardCapitalization.Characters; words -> KeyboardCapitalization.Words; else -> KeyboardCapitalization.None },
            autoCorrectEnabled = false,
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = (if (fill) modifier.fillMaxWidth() else modifier).padding(bottom = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White, unfocusedBorderColor = Line2, cursorColor = Red,
            focusedTextColor = TextC, unfocusedTextColor = TextC,
        ),
    )
}

@Composable
private fun PButton(text: String, busy: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) ErrC else Red, contentColor = if (danger) Color.White else OnAccent,
            disabledContainerColor = if (danger) ErrC.copy(alpha = .6f) else Red.copy(alpha = .6f),
            disabledContentColor = (if (danger) Color.White else OnAccent).copy(alpha = .8f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Line2, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) { Text(text, color = TextC, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
internal fun TextAction(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text, color = if (danger) ErrC else if (focused) TextC else MutedC, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0x14FFFFFF) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
