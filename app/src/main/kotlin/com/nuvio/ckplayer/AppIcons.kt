package com.nuvio.ckplayer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Another app icon — a Plus-level perk (Supporter Plus, Founder, Monthly), phones only: an Android TV launcher shows
 * the banner, never these.
 *
 * Each icon is an `<activity-alias>` of MainActivity in the manifest with its own launcher entry. Classic is on by the
 * manifest and carries the Android TV launcher category too, so a TV — which never switches — and the release launch
 * gate (`monkey -c LAUNCHER`) find the app exactly as before; MainActivity itself keeps only the deep link. Switching
 * is `setComponentEnabledSetting(…, DONT_KILL_APP)`.
 *
 * In two steps, because turning OFF the alias a running task was opened from makes the system finish that task — the
 * app would close under the viewer's finger (and at launch, when a lapsed perk puts Classic back, it would close the
 * moment it opened). So [choose] turns the new icon on at once and turns off only aliases no task of ours stands on;
 * the rest go in [settle], when Nebula has left the screen (MainActivity.onStop, never while a player is up). For that
 * moment the launcher may list Nebula twice. The chosen key lives in `app_icon` (outside the `pref_` keys: Reset all
 * settings must not tell a different story from the launcher).
 */
object AppIcons {
    private const val KEY = "app_icon"

    /** key, label, the alias's class, its background and its mark's two colours (for the picker's drawing). */
    class LauncherIcon(val key: String, val label: String, val alias: String, val bg: Color, val disc: Color, val play: Color)

    val ICONS = listOf(
        LauncherIcon("classic", "Classic", "com.nuvio.ckplayer.LauncherClassic", Color(0xFF141414), Color(0xFFE50914), Color.White),
        LauncherIcon("gold", "Gold", "com.nuvio.ckplayer.LauncherGold", Color(0xFF141414), Color(0xFFE0B24A), Color(0xFF141414)),
        LauncherIcon("ice", "Ice", "com.nuvio.ckplayer.LauncherIce", Color(0xFF0B1620), Color(0xFF64D2FF), Color(0xFF0B1620)),
        LauncherIcon("mono", "Mono", "com.nuvio.ckplayer.LauncherMono", Color(0xFFF2F2F7), Color(0xFF141414), Color(0xFFF2F2F7)),
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE)
    private fun cn(ctx: Context, i: LauncherIcon) = ComponentName(ctx.packageName, i.alias)

    /** The icon chosen (what the launcher shows once [settle] has run). */
    fun chosen(ctx: Context): String = prefs(ctx).getString(KEY, null)?.takeIf { k -> ICONS.any { it.key == k } } ?: "classic"

    /** Is this alias on — by its own setting, or by the manifest's (only Classic is on there)? */
    private fun on(ctx: Context, i: LauncherIcon): Boolean = when (runCatching { ctx.packageManager.getComponentEnabledSetting(cn(ctx, i)) }
        .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> i.key == "classic"
        else -> false
    }

    /** Turn one alias on or off; "on" for Classic and "off" for the others is simply the manifest's own state. */
    private fun put(ctx: Context, i: LauncherIcon, enable: Boolean) {
        val state = when {
            i.key == "classic" && enable -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            i.key == "classic" -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            enable -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        ctx.packageManager.setComponentEnabledSetting(cn(ctx, i), state, PackageManager.DONT_KILL_APP)
    }

    /** The aliases our open tasks were started from (their root is the launcher entry that was tapped). */
    private fun inUse(ctx: Context): Set<String> = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.appTasks.mapNotNull { t -> runCatching { t.taskInfo.baseIntent?.component?.className }.getOrNull() }.toSet()
    }.getOrDefault(emptySet())

    /** Choose [key]: its icon appears now; the old one goes now if no open task stands on it, else at [settle].
        False when the system refused (nothing was changed then). */
    fun choose(ctx: Context, key: String): Boolean {
        val want = ICONS.firstOrNull { it.key == key } ?: return false
        return runCatching {
            if (!on(ctx, want)) put(ctx, want, true)           // on first: never a moment with no icon
            prefs(ctx).edit().putString(KEY, key).apply()
            val busy = inUse(ctx)
            ICONS.filter { it !== want && it.alias !in busy && on(ctx, it) }.forEach { put(ctx, it, false) }
            true
        }.getOrDefault(false)
    }

    /** Make the launcher match the choice: the chosen icon on, every other off. Writes nothing when it already does
        (every launch on a TV, the launch gate's emulator, and anyone who never picked). */
    fun settle(ctx: Context) {
        val want = ICONS.firstOrNull { it.key == chosen(ctx) } ?: return
        runCatching {
            if (!on(ctx, want)) put(ctx, want, true)
            ICONS.filter { it !== want && on(ctx, it) }.forEach { put(ctx, it, false) }
        }
    }

    /**
     * At launch, and again once the profile has been re-read: a perk that lapsed (the monthly plan ended, signed out)
     * quietly puts the classic icon back. Touches nothing when Classic is already the choice — the common case.
     */
    fun enforce(ctx: Context) {
        if (Support.rank() >= 2 || chosen(ctx) == "classic") return
        choose(ctx, "classic")
    }
}

/** Settings › Appearance › App icon: four drawn tiles, a ring on the current one. Phones at the Plus level only. */
@Composable
internal fun AppIconSection() {
    val ctx = LocalContext.current
    if (Account.isTv(ctx)) return
    if (Support.rank() < 2) {
        SettingsHeader("APP ICON")
        Text(
            "Supporter Plus and Monthly supporters can pick another app icon.",
            color = FaintC, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 4.dp),
        )
        return
    }
    var cur by remember { mutableStateOf(AppIcons.chosen(ctx)) }
    SettingsHeader("APP ICON", "How Nebula looks on your home screen")
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AppIcons.ICONS.forEach { ic ->
                    val on = cur == ic.key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(76.dp).focusRing(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (on) return@clickable
                                if (AppIcons.choose(ctx, ic.key)) {
                                    cur = ic.key
                                    Toasts.show("${ic.label} it is — your home screen shows it once you leave Nebula.")
                                } else Toasts.show("This phone would not change the icon.")
                            }.padding(vertical = 6.dp),
                    ) {
                        IconTile(ic, on)
                        Text(
                            ic.label, color = if (on) TextC else MutedC, fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            Text(
                "The new icon takes over when you leave Nebula. Some launchers move it off the home screen — it stays in your app list.",
                color = FaintC, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** The launcher icon drawn as the adaptive icon shows it: the disc and the play mark on the background, in a rounded square. */
@Composable
private fun IconTile(ic: AppIcons.LauncherIcon, on: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(52.dp).clip(shape).background(ic.bg)
            .border(if (on) 3.dp else 1.dp, if (on) Color.White else LineC, shape),
        contentAlignment = Alignment.Center,
    ) {
        // the foreground's geometry (res/drawable/ic_launcher_foreground.xml): a disc of radius 20 at the centre of the
        // 108-unit canvas and a triangle (48,44)-(48,64)-(66,54), of which the launcher shows the middle 72 units
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 72f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ic.disc, radius = 20f * u, center = c)
            val p = Path().apply {
                moveTo(c.x - 6f * u, c.y - 10f * u)
                lineTo(c.x - 6f * u, c.y + 10f * u)
                lineTo(c.x + 12f * u, c.y)
                close()
            }
            drawPath(p, ic.play)
        }
    }
}
