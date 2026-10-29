package com.nuvio.ckplayer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Settings › Playback: night mode (NightMode.kt), then what Nebula does outside the player — the launcher's Watch Next
    row on a TV (WatchNext.kt), new-episode alerts on a phone (NewEpisodes.kt). */
@Composable
internal fun PlaybackExtrasGroup() {
    val ctx = LocalContext.current
    // Android 9 and later (the effect it needs); hidden below that
    if (NightMode.available) {
        SettingsHeader("SOUND", "How the film's sound is evened out on this device")
        SettingsGroup {
            SettingsToggle(
                "Night mode",
                "Loud moments quieter, quiet voices lifted · Also in the player's Audio list" +
                    if (Account.isTv(ctx)) " · Not applied when the TV passes the sound to a sound bar or receiver untouched" else "",
                Prefs.night, divider = false,
            ) { Prefs.setNight(ctx, it) }
        }
    }
    if (Account.isTv(ctx)) {
        SettingsHeader("HOME SCREEN", "What the TV's own home screen shows from Nebula")
        SettingsGroup {
            SettingsToggle(
                "Watch Next on the home screen",
                "What you are part-way through, in the TV's Watch Next row · Removing one there keeps it off until you play it again",
                Prefs.watchNext, divider = false,
            ) { Prefs.setWatchNext(ctx, it); WatchNext.poke(ctx, soon = true) }
        }
        return
    }
    // Android 13+ asks before an app may post; refused, the switch stays off and says where to allow it
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refused = !ok
        if (ok) Prefs.setNewEpisodes(ctx, true)
        else Toasts.show("Nebula may not post notifications — allow them in the phone's settings to get new-episode alerts.")
    }
    val blocked = refused || (Prefs.newEpisodes && !NewEpisodes.canNotify(ctx))
    SettingsHeader("NEW EPISODES", "Word when a show you follow has more")
    SettingsGroup {
        SettingsToggle(
            "New episode alerts",
            if (blocked) "Notifications are turned off for Nebula on this phone · Allow them below, then turn this on"
            else "A notification when a show in My List or Continue watching has a new episode · Checked about twice a day",
            Prefs.newEpisodes, divider = blocked,
        ) { on ->
            if (on && Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else { refused = false; Prefs.setNewEpisodes(ctx, on) }
        }
        if (blocked) Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            TextAction("Allow notifications") {
                val open = if (Build.VERSION.SDK_INT >= 26)
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                runCatching { ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}
