package com.nuvio.ckplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    // `monthly` (2026-09-28, $1.50 a month) carries every Plus-level perk while its subscription runs — the server ranks
    // it 2 and takes it away when the subscription ends. The order of this list is the order of the names, NOT the rank.
    val TIERS = listOf("supporter" to "Supporter", "plus" to "Supporter Plus", "monthly" to "Monthly Supporter", "founder" to "Founder")
    private val RANKS = mapOf("supporter" to 1, "plus" to 2, "monthly" to 2, "founder" to 3)
    val MARKS = listOf("star" to "Star", "heart" to "Heart", "bolt" to "Bolt", "crown" to "Crown")
    fun cleanTier(v: String?): String = if (TIERS.any { it.first == v }) v!! else "supporter"
    fun cleanMark(v: String?): String = if (MARKS.any { it.first == v }) v!! else "star"
    /** A tier's rank: 1 supporter, 2 plus or monthly, 3 founder (an unknown tier counts as a supporter). */
    internal fun rankOf(tier: String?): Int = RANKS[cleanTier(tier)] ?: 1
    /** 0 = not a supporter, 1 supporter, 2 plus or monthly, 3 founder. */
    fun rank(): Int { val p = Cloud.profile ?: return 0; if (!p.sup) return 0; return rankOf(p.tier) }
    fun tierName(): String = TIERS.firstOrNull { it.first == Cloud.profile?.tier }?.second ?: "Supporter"
    /** The monthly plan's state in words, or "" when there is none (or the service sent a state this build does not know). */
    internal fun subStatusText(status: String): String = when (status) {
        "trialing" -> "Free week"
        "active" -> "Active"
        "past_due" -> "Payment failed. Update your card"
        "paused" -> "Paused"
        else -> ""
    }
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
        if (Cloud.profile == null) return "Sign in first. The supporter mark lives on your profile."
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
