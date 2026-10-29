package com.nuvio.ckplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Settings › Playback: what Nebula does outside the player — the launcher's Watch Next row on a TV (WatchNext.kt). */
@Composable
internal fun PlaybackExtrasGroup() {
    val ctx = LocalContext.current
    val tv = Account.isTv(ctx)
    if (!tv) return
    SettingsHeader("HOME SCREEN", "What the TV's own home screen shows from Nebula")
    SettingsGroup {
        SettingsToggle(
            "Watch Next on the home screen",
            "What you are part-way through, in the TV's Watch Next row · Removing one there keeps it off until you play it again",
            Prefs.watchNext, divider = false,
        ) { Prefs.setWatchNext(ctx, it); WatchNext.poke(ctx, soon = true) }
    }
}
