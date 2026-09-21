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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
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
    /** Names on the wall, in the order the server sent them (founders first), with each one's tier. */
    var wall by mutableStateOf<List<Pair<String, String>>>(emptyList()); private set
    val founders: List<String> get() = wall.filter { it.second == "founder" }.map { it.first }
    val others: List<String> get() = wall.filter { it.second != "founder" }.map { it.first }

    // ---- tiers (2026-09-19): one-time, permanent; a higher code or payment raises the profile ----
    val TIERS = listOf("supporter" to "Supporter", "plus" to "Supporter Plus", "founder" to "Founder")
    val MARKS = listOf("star" to "Star", "heart" to "Heart", "bolt" to "Bolt", "crown" to "Crown")
    fun cleanTier(v: String?): String = if (TIERS.any { it.first == v }) v!! else "supporter"
    fun cleanMark(v: String?): String = if (MARKS.any { it.first == v }) v!! else "star"
    /** 0 = not a supporter, 1 supporter, 2 plus, 3 founder. */
    fun rank(): Int { val p = Cloud.profile ?: return 0; if (!p.sup) return 0; return TIERS.indexOfFirst { it.first == p.tier } + 1 }
    fun tierName(): String = TIERS.firstOrNull { it.first == Cloud.profile?.tier }?.second ?: "Supporter"
    /** The mark by THIS profile's name: the chosen one from Supporter Plus up, a star below. */
    fun myMark(): String = if (rank() >= 2) cleanMark(Cloud.profile?.mark) else "star"
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
        wall = rows(o.optJSONArray("wall"))
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
            wall = rows(r.optJSONArray("wall"))
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

    /** Supporter Plus and up: the mark beside the name. Null on success, else a line to show. */
    suspend fun setMark(ctx: Context, mark: String): String? = runCatching {
        val r = Cloud.api(ctx, "PUT", "/v1/support", JSONObject().put("mark", cleanMark(mark)))
        Cloud.noteSupporter(ctx, r.optJSONObject("supporter"))
        null
    }.getOrElse { Account.errorText(it) }

    /**
     * Open the support page. Signed in: with a 15-minute link token, so what is bought lands on
     * this profile without a code; the plain link otherwise, or if the token call fails.
     */
    suspend fun open(ctx: Context) {
        val u = url ?: return
        val withToken = if (Cloud.profile == null) u else runCatching {
            val t = Cloud.api(ctx, "POST", "/v1/support/link", JSONObject()).optString("token")
            if (t.isEmpty()) u else u + (if ('?' in u) "&" else "?") + "for=" + t
        }.getOrDefault(u)
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(withToken))) }
    }

    // ---------- plumbing ----------
    private fun store(ctx: Context) {
        val arr = JSONArray()
        wall.forEach { arr.put(JSONObject().put("name", it.first).put("tier", it.second)) }
        val o = JSONObject().put("url", url ?: "").put("wall", arr).put("count", count).put("at", at)
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }

    /** The link is opened with ACTION_VIEW, so only ever trust an http(s) one. */
    private fun cleanUrl(o: JSONObject): String? {
        if (o.isNull("url")) return null
        val u = o.optString("url").trim()
        return if (u.startsWith("http://") || u.startsWith("https://")) u else null
    }

    private fun rows(a: JSONArray?): List<Pair<String, String>> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val row = a.optJSONObject(i)
            val n = (row?.optString("name") ?: a.optString(i)).trim()
            if (n.isEmpty()) null else n to cleanTier(row?.optString("tier"))
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

// the four marks, the same paths the web player draws (24×24)
private val MARK_PATHS = mapOf(
    "star" to "M12 2.6l2.9 6 6.6.9-4.8 4.6 1.2 6.5L12 17.5l-5.9 3.1 1.2-6.5L2.5 9.5l6.6-.9z",
    "heart" to "M12 21s-7.5-4.6-9.5-9.3C1.2 8.4 3.3 5 6.8 5c2 0 3.5 1.1 5.2 3 1.7-1.9 3.2-3 5.2-3 3.5 0 5.6 3.4 4.3 6.7C19.5 16.4 12 21 12 21z",
    "bolt" to "M13.5 2L4 13.5h6.5L9.5 22 20 9.5h-6.5z",
    "crown" to "M3 8l4.5 4L12 5l4.5 7L21 8l-1.5 11h-15z",
)
private val markVectors = HashMap<String, ImageVector>()
internal fun markVector(mark: String): ImageVector = markVectors.getOrPut(mark) {
    val d = MARK_PATHS[mark] ?: MARK_PATHS.getValue("star")
    ImageVector.Builder(name = "mark_$mark", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(androidx.compose.ui.graphics.Color.White))
        .build()
}

/** The supporter mark beside a name: a star, or the shape a Supporter Plus chose, in the accent colour. */
@Composable
internal fun SupporterMark(size: Dp = 14.dp, mark: String = "star") {
    Icon(
        markVector(Support.cleanMark(mark)), contentDescription = "Supporter",
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

        val rank = Support.rank()
        if (me?.sup == true) {
            SupporterPanel(
                me, rank, tv,
                onWall = { on ->
                    if (busy) return@SupporterPanel
                    busy = true; status = ""
                    scope.launch { status = Support.setWall(ctx, on) ?: ""; busy = false }
                },
                onMark = { m ->
                    if (busy) return@SupporterPanel
                    busy = true; status = ""
                    scope.launch { status = Support.setMark(ctx, m) ?: ""; busy = false }
                },
                onOpen = { scope.launch { Support.open(ctx) } },
            )
        } else {
            SupportPitch(tv = tv, onOpen = { scope.launch { Support.open(ctx) } })
        }
        if (rank < 3) {
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
                            Toasts.show("You're a ${Support.tierName()} — thank you. " +
                                (if (Support.rank() >= 2) "Any colour is yours in Appearance." else "Three more accents are yours in Appearance."))
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

        if (Support.founders.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            Eyebrow("Founders")
            Text(
                Support.joinNames(Support.founders), color = TextC, fontSize = 15.sp, lineHeight = 23.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (Support.others.isNotEmpty()) {
            Spacer(Modifier.height(if (Support.founders.isNotEmpty()) 16.dp else 26.dp))
            Eyebrow("Thanks to")
            Text(
                Support.joinNames(Support.others), color = TextC, fontSize = 15.sp, lineHeight = 23.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Already a supporter: the tier, the date, the wall switch, the mark (plus and up), and a way to raise the tier. */
@Composable
private fun SupporterPanel(me: Profile, rank: Int, tv: Boolean, onWall: (Boolean) -> Unit, onMark: (String) -> Unit, onOpen: () -> Unit) {
    SupportPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupporterMark(20.dp, Support.myMark())
            Text("You're a ${Support.tierName()}", color = TextC, fontSize = 19.sp, fontFamily = Sans, fontWeight = FontWeight.Bold)
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
        if (rank >= 2) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Your mark", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Beside your name, for you and your friends",
                    color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                Segmented {
                    Support.MARKS.forEach { (k, label) -> Chip(label, me.mark == k, inSeg = true) { onMark(k) } }
                }
            }
        }
        if (rank < 3 && Support.url != null) {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp).height(1.dp).background(LineC))
            Text("Raise your tier", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Text(
                (if (rank < 2) "Supporter Plus: any accent colour, your own mark, early builds. " else "") +
                    "Founder: the Founders list, a gold ring, Nebula Sports without the sponsor prompt.",
                color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            if (tv) TvSupportAddress() else Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = TextC),
                shape = RoundedCornerShape(12.dp),
            ) { Text("See the tiers", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** A TV has no browser worth typing a card number into: the short address, large. */
@Composable
private fun TvSupportAddress() {
    Text(
        "play.rifflehq.in/support", color = TextC, fontFamily = Mono, fontSize = 22.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
    Text(
        "Open it on your phone — it takes a minute." +
            (if (Cloud.profile != null) " Sign in there with the same profile, or type the code it gives you here." else ""),
        color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp),
    )
}

/** Not a supporter: what it is for, and the way to do it. */
@Composable
private fun SupportPitch(tv: Boolean, onOpen: () -> Unit) {
    SupportPanel {
        Text(
            "Every feature stays free for everyone. Supporting keeps the sync server and the site " +
                "running, and you get a small thank-you: a mark beside your name, more accent colours, " +
                "your name on the wall if you like — and more at the higher tiers.",
            color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 14.dp),
        )
        if (Support.url == null) return@SupportPanel
        if (tv) {
            TvSupportAddress()
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
                modifier = Modifier.focusRing(RoundedCornerShape(12.dp)),
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
            modifier = Modifier.focusRing(RoundedCornerShape(12.dp)),
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
