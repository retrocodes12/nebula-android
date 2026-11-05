package com.nuvio.ckplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The content advisory a film or episode shows for a few seconds as it first plays —
 * "Violence · Severe", "Nudity · Moderate" (Nuvio's parental guide, same timings).
 * Nebula Cloud answers `/v1/advisory?id=tt…` from the title's public parents-guide votes
 * and keeps it two weeks; the request carries the title id and nothing else. A series
 * answers for all its episodes. Off in Settings › Playback › Content advisory.
 * Mirrors the shared player's `pg*` block.
 */
object Advisory {
    class Warn(val kind: String, val level: String)
    /** The cloud's answer for a title: its levels (most severe first) and its age rating where this device is ("" none). */
    class Answer(val warns: List<Warn>, val cert: String) {
        /** The overlay's rows: "Rated · UA" first, then the levels. */
        fun rows(): List<Warn> = (if (cert.isNotEmpty()) listOf(Warn("Rated", cert)) else emptyList()) + warns
    }

    private const val URL = "https://play.rifflehq.in/cloud/v1/advisory?id="
    private val TT = Regex("^(tt\\d{5,10})(?::|$)")
    private val KIND = mapOf(
        "violence" to "Violence", "profanity" to "Profanity", "frightening" to "Frightening",
        "nudity" to "Nudity", "alcohol" to "Alcohol/Drugs",
    )
    private val LEVEL = mapOf("severe" to "Severe", "moderate" to "Moderate", "mild" to "Mild")
    private val cache = HashMap<String, Answer>()         // this process; the cloud keeps two weeks
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** The title's IMDb id: a film's own, or an episode's series (`tt…:S:E`). */
    fun ttOf(id: String?): String? = id?.let { TT.find(it)?.groupValues?.get(1) }

    /** Most severe first, at most five, + the age rating; null when the cloud could not be reached (asked again next time). */
    suspend fun load(tt: String): Answer? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[tt] }?.let { return@withContext it }
        val got = runCatching {
            http.newCall(Request.Builder().url(URL + tt + "&cc=" + region()).get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val o0 = JSONObject(r.body?.string() ?: "{}")
                val arr = o0.optJSONArray("items")
                val warns = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val k = KIND[o.optString("k")] ?: return@mapNotNull null
                    val s = LEVEL[o.optString("s")] ?: return@mapNotNull null
                    Warn(k, s)
                }.take(5)
                Answer(warns, o0.optString("cert").trim().take(16))
            }
        }.getOrNull()
        if (got != null) synchronized(cache) { cache[tt] = got }
        got
    }

    /** The age rating already known for a title id ("" while it is asked, or none). */
    fun certKnown(id: String?): String = ttOf(id)?.let { synchronized(cache) { cache[it] }?.cert }.orEmpty()

    // The country whose rating is shown: the clock's time zone first (a TV's language is often en-US wherever it is),
    // then the phone's own region, else the US. Mirrors the shared player's ageRegion().
    private val TZ = mapOf(
        "Asia/Kolkata" to "IN", "Asia/Calcutta" to "IN", "Europe/London" to "GB", "Europe/Dublin" to "IE", "Europe/Berlin" to "DE",
        "Europe/Paris" to "FR", "Europe/Madrid" to "ES", "Europe/Rome" to "IT", "Europe/Amsterdam" to "NL", "Europe/Brussels" to "BE",
        "Europe/Zurich" to "CH", "Europe/Vienna" to "AT", "Europe/Stockholm" to "SE", "Europe/Oslo" to "NO", "Europe/Copenhagen" to "DK",
        "Europe/Helsinki" to "FI", "Europe/Lisbon" to "PT", "Europe/Warsaw" to "PL", "Europe/Istanbul" to "TR", "Asia/Dubai" to "AE",
        "Asia/Riyadh" to "SA", "Asia/Singapore" to "SG", "Asia/Kuala_Lumpur" to "MY", "Asia/Manila" to "PH", "Asia/Jakarta" to "ID",
        "Asia/Tokyo" to "JP", "Asia/Seoul" to "KR", "Asia/Karachi" to "PK", "Asia/Dhaka" to "BD", "Asia/Colombo" to "LK",
        "Asia/Kathmandu" to "NP", "Africa/Johannesburg" to "ZA", "Africa/Lagos" to "NG", "Africa/Nairobi" to "KE",
        "America/Sao_Paulo" to "BR", "America/Mexico_City" to "MX", "America/Argentina/Buenos_Aires" to "AR",
        "America/Toronto" to "CA", "America/Vancouver" to "CA", "Pacific/Auckland" to "NZ",
    )
    private val US_TZ = Regex("^America/(New_York|Chicago|Denver|Los_Angeles|Phoenix|Anchorage|Detroit|Boise|Indiana|Kentucky)|^Pacific/Honolulu$")
    internal fun regionOf(tz: String, localeCountry: String): String {
        val cc = TZ[tz] ?: if (tz.startsWith("Australia/")) "AU" else if (US_TZ.containsMatchIn(tz)) "US" else localeCountry.uppercase()
        return if (Regex("^[A-Z]{2}$").matches(cc)) cc else "US"
    }
    private val region by lazy { regionOf(java.util.TimeZone.getDefault().id.orEmpty(), java.util.Locale.getDefault().country.orEmpty()) }
    private fun region(): String = region
}

private val RowH = 18.dp
private val RowGap = 2.dp

/**
 * A white line drawn downwards, then the rows one by one; held 5 s, then out in reverse
 * (rows bottom first, the line, the box) and [onDone]. Shown while [warns] is set; the
 * caller drops it in [onDone]. Keyed on the list, so a new title starts over.
 */
@Composable
fun AdvisoryOverlay(warns: List<Advisory.Warn>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (warns.isEmpty()) return
    val n = warns.size
    val box = remember(warns) { Animatable(0f) }
    val line = remember(warns) { Animatable(0f) }
    val rows = remember(warns) { List(n) { Animatable(0f) } }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(warns) {
        box.animateTo(1f, tween(300, easing = LinearEasing))
        line.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        for (r in rows) { delay(80); r.animateTo(1f, tween(200, easing = LinearEasing)) }
        delay(5000)
        for (r in rows.asReversed()) { delay(60); r.animateTo(0f, tween(150, easing = LinearEasing)) }
        delay(100)
        line.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        delay(200)
        box.animateTo(0f, tween(200, easing = LinearEasing))
        done()
    }
    if (box.value <= 0f) return
    val full = RowH * n + RowGap * (n - 1)
    Row(modifier.alpha(box.value), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.width(3.dp).height(full * line.value)
                .background(Color.White, RoundedCornerShape(1.dp))
        )
        Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(RowGap)) {
            warns.forEachIndexed { i, w ->
                Row(Modifier.height(RowH).alpha(rows[i].value), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.kind, color = Color(0xD9FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(" · ", color = Color(0x66FFFFFF), fontSize = 11.sp, maxLines = 1)
                    Text(w.level, color = Color(0x80FFFFFF), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

/** The age rating as an outlined plate beside a title's facts ("UA"). */
@Composable
internal fun AgePlate(text: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        text, color = Color.White, fontSize = size, fontWeight = FontWeight.SemiBold, maxLines = 1, letterSpacing = 0.4.sp,
        modifier = Modifier.border(1.dp, Color(0x8CFFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
