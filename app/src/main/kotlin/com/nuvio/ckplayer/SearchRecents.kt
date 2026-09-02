package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

/**
 * Recent searches, device-local, the last eight. Typing a title is the slowest
 * thing in the app, so a search that was worth submitting is kept to run again.
 * Same key and shape as the web player, newest first.
 */
object RecentSearches {
    private const val P = "ckplayer"
    private const val K = "recent_q"
    private const val MAX = 8

    /** Bumps on every change so the idle page repaints. */
    var version by mutableStateOf(0); private set

    fun all(ctx: Context): List<String> {
        val s = ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString(K, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun note(ctx: Context, raw: String) {
        val q = raw.trim()
        if (q.length < 2) return
        val next = listOf(q) + all(ctx).filterNot { it.equals(q, ignoreCase = true) }
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString(K, JSONArray(next.take(MAX)).toString()).apply()
        version++
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().remove(K).apply()
        version++
    }
}

/** Before a query: recent searches to run again, then what one search covers,
    each add-on a chip into its catalogs. */
@Composable
internal fun SearchIdle(ctx: Context, addons: List<Addon>, onRecent: (String) -> Unit, onAddon: (Addon) -> Unit) {
    val recent = RecentSearches.version.let { if (addons.isEmpty()) emptyList() else RecentSearches.all(ctx) }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        if (recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT", color = MutedC, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
                TextAction("Clear") { RecentSearches.clear(ctx) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 26.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recent.forEach { q -> Chip(q, false) { onRecent(q) } }
            }
        }
        Text(
            if (addons.isEmpty()) "NOTHING TO SEARCH YET"
            else "ONE SEARCH · " + addons.size + " ADD-ON" + (if (addons.size > 1) "S" else ""),
            color = MutedC, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
        )
        Text(
            if (addons.isEmpty()) "Add an add-on first — then one search covers everything it has."
            else "Titles, series and episodes from every add-on you have added, in one place.",
            color = MutedC, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (addons.isNotEmpty()) Box {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                addons.forEach { a -> Chip(a.name.ifEmpty { "Add-on" }, false) { onAddon(a) } }
            }
        }
    }
}
