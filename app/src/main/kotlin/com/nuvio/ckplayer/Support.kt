package com.nuvio.ckplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Support Nebula — the one place money is mentioned. Nebula stays free, with no ads
 * and no account required, and nothing that already exists moves behind this: what a
 * supporter gets is cosmetic, a mark by their name, three more accent colours, and
 * their name on the wall if they want it.
 *
 * The link is server-configured (`GET /v1/support` answers `url: null` until the
 * Founder sets one), so this whole section stays hidden until there is somewhere to
 * send people — [visible]. The mark itself rides on the profile, which is why
 * redeeming a code needs a signed-in device.
 */
object Support {
    private const val PREFS = "ckplayer"
    private const val KEY = "support_v1"
    private const val FRESH_MS = 5 * 60_000L

    /** Where to send someone who wants to chip in; null until the Founder sets one. */
    var url by mutableStateOf<String?>(null); private set
    /** Names on the wall, in the order the server sent them. */
    var wall by mutableStateOf<List<String>>(emptyList()); private set
    /** How many supporters there are — including the ones who stayed off the wall. */
    var count by mutableStateOf(0); private set

    private var at = 0L
    @Volatile private var loading = false

    /** Show the Support row at all? A supporter always sees it, link or no link. */
    val visible: Boolean get() = url != null || Cloud.profile?.sup == true

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Boot: last known answer, so the row and the wall do not flicker in. */
    fun restore(ctx: Context) {
        val o = runCatching { JSONObject(prefs(ctx).getString(KEY, "") ?: "") }.getOrNull() ?: return
        url = cleanUrl(o)
        wall = names(o.optJSONArray("wall"))
        count = o.optInt("count")
        at = o.optLong("at")
    }

    /** One `GET /v1/support`. Skipped when the cached copy is fresh, unless [force]. */
    suspend fun load(ctx: Context, force: Boolean = false) {
        if (loading) return
        if (!force && at > 0L && System.currentTimeMillis() - at < FRESH_MS) return
        loading = true
        try {
            val r = Cloud.api(ctx, "GET", "/v1/support", null, auth = false)
            url = cleanUrl(r)
            wall = names(r.optJSONArray("wall"))
            count = r.optInt("count")
            at = System.currentTimeMillis()
            store(ctx)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // offline: keep whatever [restore] brought
        } finally {
            loading = false
        }
    }

    /** Redeem a supporter code on this profile. Null on success, else a line to show. */
    suspend fun redeem(ctx: Context, codeRaw: String): String? {
        if (Cloud.profile == null) return "Sign in first — the supporter mark lives on your profile."
        val code = normCode(codeRaw)
        if (code.isEmpty()) return "A code looks like NEB-XXXX-XXXX."
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/support/redeem", JSONObject().put("code", code))
            Cloud.noteSupporter(ctx, r.optJSONObject("supporter"))
            Account.refreshProfile(ctx)
            load(ctx, force = true)
            null
        }.getOrElse { Account.errorText(it) }
    }

    /** Show or hide this name on the wall. Null on success, else a line to show. */
    suspend fun setWall(ctx: Context, on: Boolean): String? = runCatching {
        val r = Cloud.api(ctx, "PUT", "/v1/support", JSONObject().put("wall", on))
        Cloud.noteSupporter(ctx, r.optJSONObject("supporter"))
        load(ctx, force = true)
        null
    }.getOrElse { Account.errorText(it) }

    // ---------- plumbing ----------
    private fun store(ctx: Context) {
        val arr = JSONArray()
        wall.forEach { arr.put(it) }
        val o = JSONObject().put("url", url ?: "").put("wall", arr).put("count", count).put("at", at)
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }

    /** The link is opened with ACTION_VIEW, so only ever trust an http(s) one. */
    private fun cleanUrl(o: JSONObject): String? {
        if (o.isNull("url")) return null
        val u = o.optString("url").trim()
        return if (u.startsWith("http://") || u.startsWith("https://")) u else null
    }

    private fun names(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val row = a.optJSONObject(i)
            val n = (row?.optString("name") ?: a.optString(i)).trim()
            n.ifEmpty { null }
        }
    }

    /** "neb-ab12 cd34" → "AB12CD34"; anything that is not 8 alphabet characters → "". */
    private fun normCode(raw: String): String {
        var s = raw.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        if (s.length == 11 && s.startsWith("NEB")) s = s.substring(3)
        return if (s.length == 8) s else ""
    }

    /** "A, B and C" — the wall reads as a sentence, not a list. */
    internal fun joinNames(list: List<String>): String = when (list.size) {
        0 -> ""
        1 -> list[0]
        else -> list.dropLast(1).joinToString(", ") + " and " + list.last()
    }

    internal fun sinceText(ms: Long): String =
        if (ms <= 0L) "" else SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(ms))
}

/** The supporter mark: a small filled star in the accent colour, beside a name. */
@Composable
internal fun SupporterMark(size: Dp = 14.dp) {
    Icon(
        Icons.Rounded.Star, contentDescription = "Supporter",
        tint = Prefs.accentColor, modifier = Modifier.size(size),
    )
}

@Composable
internal fun SettingsSupportScreen(onBack: () -> Unit, onProfile: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tv = remember { Account.isTv(ctx) }
    val me = Cloud.profile
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { Support.load(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        BackBar("Support Nebula", null, onBack)
        Text(
            "Nebula is free, with no ads and no account required. If it earns a place in your evenings, you can chip in.",
            color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 16.dp),
        )

        if (me?.sup == true) {
            SupporterPanel(me) { on ->
                if (busy) return@SupporterPanel
                busy = true; status = ""
                scope.launch {
                    status = Support.setWall(ctx, on) ?: ""
                    busy = false
                }
            }
        } else {
            SupportPitch(tv = tv, onOpen = {
                val u = Support.url ?: return@SupportPitch
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
            })
            Spacer(Modifier.height(12.dp))
            SupportCodePanel(
                signedIn = me != null,
                code = code, onCode = { code = it.take(20) },
                busy = busy, onProfile = onProfile,
                onRedeem = {
                    if (busy) return@SupportCodePanel
                    busy = true; status = "Checking the code…"
                    scope.launch {
                        val err = Support.redeem(ctx, code)
                        busy = false
                        if (err == null) {
                            code = ""; status = ""
                            Toasts.show("You're a supporter — thank you. Three more accents are yours in Appearance.")
                        } else {
                            status = err
                        }
                    }
                },
            )
        }

        if (status.isNotEmpty()) Text(
            status, color = MutedC, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )

        if (Support.wall.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            Eyebrow("Thanks to")
            Text(
                Support.joinNames(Support.wall), color = TextC, fontSize = 15.sp, lineHeight = 23.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Already a supporter: the mark, the date, and the one choice they still have. */
@Composable
private fun SupporterPanel(me: Profile, onWall: (Boolean) -> Unit) {
    SupportPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupporterMark(20.dp)
            Text("You're a supporter", color = TextC, fontSize = 19.sp, fontFamily = Sans, fontWeight = FontWeight.Bold)
        }
        val since = Support.sinceText(me.supSince)
        Text(
            (if (since.isEmpty()) "" else "Since $since. ") + "Thank you — this is what keeps Nebula going.",
            color = MutedC, fontSize = 14.sp, lineHeight = 21.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(LineC))
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Your name on the wall", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "In Settings › Support on every device, and on the website",
                    color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Segmented {
                Chip("Show", me.wall, inSeg = true) { onWall(true) }
                Chip("Hide", !me.wall, inSeg = true) { onWall(false) }
            }
        }
    }
}

/** Not a supporter: what it is for, and the way to do it. */
@Composable
private fun SupportPitch(tv: Boolean, onOpen: () -> Unit) {
    SupportPanel {
        Text(
            "Every feature stays free for everyone. Supporting keeps the sync server and the site " +
                "running, and you get a small thank-you: a supporter mark beside your name, three more " +
                "accent colours, and your name on the wall if you like.",
            color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 14.dp),
        )
        if (Support.url == null) return@SupportPanel
        if (tv) {
            // A TV has no browser worth typing a card number into.
            Text(
                "play.rifflehq.in/support", color = TextC, fontFamily = Mono, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                modifier = Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
            Text(
                "Open it on your phone — it takes a minute.",
                color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Support Nebula", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** The code that comes with the thank-you note, typed once on any signed-in device. */
@Composable
private fun SupportCodePanel(
    signedIn: Boolean,
    code: String,
    onCode: (String) -> Unit,
    busy: Boolean,
    onProfile: () -> Unit,
    onRedeem: () -> Unit,
) {
    SupportPanel {
        Text("Have a supporter code?", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "It comes with the thank-you. One code, typed once on any signed-in device — the mark follows your profile.",
            color = MutedC, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (!signedIn) {
            Text(
                "Sign in first — the supporter mark lives on your profile, so it follows you to every device.",
                color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(
                onClick = onProfile,
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Sign in", fontWeight = FontWeight.SemiBold) }
            return@SupportPanel
        }
        PField(code, onCode, "NEB-XXXX-XXXX", caps = true, last = true, onDone = onRedeem)
        Button(
            onClick = onRedeem, enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = Red, contentColor = OnAccent,
                disabledContainerColor = Red.copy(alpha = .6f), disabledContentColor = OnAccent.copy(alpha = .8f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) { Text(if (busy) "Checking…" else "Redeem", fontWeight = FontWeight.SemiBold) }
    }
}

/** The hairline card every block on this page sits in. */
@Composable
private fun SupportPanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SurfaceC, RoundedCornerShape(16.dp))
            .border(1.dp, LineC, RoundedCornerShape(16.dp)).padding(18.dp),
        content = content,
    )
}
