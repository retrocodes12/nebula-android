package com.nuvio.ckplayer

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/**
 * "Your month on Nebula" — how much this device actually played, per calendar month: the seconds the picture was
 * moving (the engine playing — never buffering, never paused), per title as the player names it (a series, a film, a
 * live channel or event) and per day of the month.
 *
 * Private by construction: it lives in its own preferences file, `ckplayer_private`, which the backup rules leave out
 * (res/xml/backup_rules.xml, data_extraction_rules.xml), it is never sent to the cloud and never synced. Kept: the
 * current month and the two before it. Recorded for everyone, so a new supporter sees their month so far; shown only
 * from Supporter Plus up (Perks.kt).
 *
 * The document: `{"v":1, "months": {"2026-09": {"total": s, "titles": {"The Bear": s}, "days": {"28": s}}}}`.
 */
object WatchLog {
    private const val FILE = "ckplayer_private"
    private const val KEY = "watchlog_v1"
    /** The current month and the two before it. */
    const val KEEP_MONTHS = 3
    /** A month keeps its biggest titles past this many (a channel-surfing month must not grow the file for ever). */
    const val MAX_TITLES = 200
    const val MAX_TITLE_LEN = 120
    /** A day counts as a day you watched from a minute of it — a trailer of a few seconds is not an evening. */
    const val DAY_MIN_SECS = 60L
    /** A clock gap longer than this between two ticks is not counted (a device asleep, a stalled main thread). */
    private const val MAX_STEP_MS = 3_000L
    /** Written to disk after this many new seconds, and whenever the player stops or closes ([flush]). */
    private const val SAVE_EVERY_SECS = 30L

    /** Bumped on every save, so an open recap redraws. */
    var version by mutableIntStateOf(0)
        private set

    private var doc: JSONObject? = null
    private var lastTick = 0L
    private var pendingMs = 0L
    private var pendingKey = ""
    private var unsaved = 0L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The document, read once per process. Main thread only (the player's clock and the Settings screen). */
    fun doc(ctx: Context): JSONObject {
        doc?.let { return it }
        val d = runCatching { JSONObject(prefs(ctx).getString(KEY, "") ?: "") }.getOrNull()?.takeIf { it.optInt("v") == 1 }
            ?: JSONObject().put("v", 1).put("months", JSONObject())
        if (d.optJSONObject("months") == null) d.put("months", JSONObject())
        doc = d
        return d
    }

    /**
     * One beat of the player's clock (every ~400 ms). [playing] is the engine's own isPlaying — true only while the
     * picture moves. The time between two playing beats is added to [title] on today's date; a beat that is not
     * playing ends the run, so a pause or a buffering spell adds nothing.
     */
    fun tick(ctx: Context, title: String, playing: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!playing) { lastTick = 0L; return }
        val step = if (lastTick > 0L) now - lastTick else 0L
        lastTick = now
        if (step <= 0L || step > MAX_STEP_MS) return
        val c = Calendar.getInstance()
        val month = monthKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        val day = c.get(Calendar.DAY_OF_MONTH)
        val name = cleanTitle(title)
        val key = "$month|$day|$name"
        if (key != pendingKey) { pendingKey = key; pendingMs = 0L }        // a new title or a new day: the part-second goes
        pendingMs += step
        val secs = pendingMs / 1000L
        if (secs <= 0L) return
        pendingMs -= secs * 1000L
        add(doc(ctx), month, day, name, secs)
        unsaved += secs
        if (unsaved >= SAVE_EVERY_SECS) flush(ctx)
    }

    /** Write what is in memory (the player stopping, closing, the app leaving the screen). */
    fun flush(ctx: Context) {
        lastTick = 0L
        if (unsaved <= 0L) return
        val d = doc(ctx)
        val c = Calendar.getInstance()
        prune(d, monthKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1))
        prefs(ctx).edit().putString(KEY, d.toString()).apply()
        unsaved = 0L
        version++
    }

    // ---------- the pure part (WatchLogTest) ----------

    /** "2026-09". */
    fun monthKey(year: Int, month1: Int): String = String.format(Locale.US, "%04d-%02d", year, month1)

    /** The month before [key]: "2026-01" → "2025-12". */
    fun prevMonth(key: String): String {
        val y = key.substringBefore('-').toIntOrNull() ?: return key
        val m = key.substringAfter('-').toIntOrNull() ?: return key
        return if (m <= 1) monthKey(y - 1, 12) else monthKey(y, m - 1)
    }

    /** The months kept, newest first: [now] and the two before it. */
    fun keptMonths(now: String): List<String> = generateSequence(now) { prevMonth(it) }.take(KEEP_MONTHS).toList()

    /** A title as the log keys it: trimmed, one line, not endless; "" becomes "Something". */
    fun cleanTitle(t: String): String = t.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE_LEN).ifEmpty { "Something" }

    /** Add [secs] to [title] on [day] of [month]. */
    fun add(d: JSONObject, month: String, day: Int, title: String, secs: Long) {
        if (secs <= 0L || day !in 1..31) return
        val months = d.optJSONObject("months") ?: JSONObject().also { d.put("months", it) }
        val m = months.optJSONObject(month) ?: JSONObject().also { months.put(month, it) }
        m.put("total", m.optLong("total") + secs)
        val titles = m.optJSONObject("titles") ?: JSONObject().also { m.put("titles", it) }
        val t = cleanTitle(title)
        titles.put(t, titles.optLong(t) + secs)
        if (titles.length() > MAX_TITLES) {
            // the smallest title that is not this one goes (the one being watched is always kept)
            titles.keys().asSequence().filter { it != t }.minByOrNull { titles.optLong(it) }?.let { titles.remove(it) }
        }
        val days = m.optJSONObject("days") ?: JSONObject().also { m.put("days", it) }
        days.put(day.toString(), days.optLong(day.toString()) + secs)
    }

    /** Drop every month but [now] and the two before it. */
    fun prune(d: JSONObject, now: String) {
        val months = d.optJSONObject("months") ?: return
        val keep = keptMonths(now).toSet()
        months.keys().asSequence().toList().forEach { if (it !in keep) months.remove(it) }
    }

    /** One month, added up for the card. */
    data class Recap(
        val month: String,
        val totalSecs: Long,
        /** days with at least [DAY_MIN_SECS] */
        val days: Int,
        /** the three biggest titles (at least a minute each), biggest first; ties by name */
        val top: List<Pair<String, Long>>,
        /** the day of the month with the most, and its seconds; 0 when no day counts */
        val busiestDay: Int,
        val busiestSecs: Long,
    )

    fun recap(d: JSONObject, month: String): Recap {
        val m = d.optJSONObject("months")?.optJSONObject(month) ?: return Recap(month, 0L, 0, emptyList(), 0, 0L)
        val titles = m.optJSONObject("titles") ?: JSONObject()
        val days = m.optJSONObject("days") ?: JSONObject()
        val top = titles.keys().asSequence().map { it to titles.optLong(it) }.filter { it.second >= DAY_MIN_SECS }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }).take(3).toList()
        val dayRows = days.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to days.optLong(k) } }
            .filter { it.second >= DAY_MIN_SECS }.toList()
        // the busiest day: the most seconds, the earlier day on a tie
        val busiest = dayRows.sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first }).firstOrNull()
        return Recap(month, m.optLong("total"), dayRows.size, top, busiest?.first ?: 0, busiest?.second ?: 0L)
    }

    /** The kept months that have anything in them, newest first — the current month always, even while empty. */
    fun monthsShown(d: JSONObject, now: String): List<String> {
        val months = d.optJSONObject("months")
        return keptMonths(now).filter { it == now || (months?.optJSONObject(it)?.optLong("total") ?: 0L) > 0L }
    }

    /** The month's big figure and its label: minutes below an hour ("42", "minutes watched"), then hours to one
        decimal ("12.4", a whole "25" without its ".0"), whole from a hundred up. */
    fun hoursStat(secs: Long): Pair<String, String> {
        if (secs < 3600L) return (secs / 60L).toString() to (if (secs / 60L == 1L) "minute watched" else "minutes watched")
        val h = secs / 3600.0
        val v = if (h < 100.0) String.format(Locale.US, "%.1f", h).removeSuffix(".0") else Math.round(h).toString()
        return v to (if (v == "1") "hour watched" else "hours watched")
    }

    private val MONTHS = java.text.DateFormatSymbols(Locale.US)
    /** "2026-09" → "September". */
    fun monthName(key: String): String = key.substringAfter('-').toIntOrNull()?.let { MONTHS.months.getOrNull(it - 1) } ?: key
    /** "2026-09" → "Sep". */
    fun monthShort(key: String): String = key.substringAfter('-').toIntOrNull()?.let { MONTHS.shortMonths.getOrNull(it - 1) } ?: key
    /** (2026-09, 28) → "28 Sep". */
    fun dayLabel(key: String, day: Int): String = "$day ${monthShort(key)}"
    /** The month this device is in now. */
    fun nowKey(): String = Calendar.getInstance().let { monthKey(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1) }

    /** A length for the card: "42 min" below an hour, then hours with one decimal below ten ("3.4 h"), whole above. */
    fun fmtDuration(secs: Long): String {
        if (secs < 3600L) return "${secs / 60L} min"
        val h = secs / 3600.0
        return if (h < 10.0) String.format(Locale.US, "%.1f h", h) else "${Math.round(h)} h"
    }
}

/** The player's side of the log: every half second, is the picture moving, and under what name ([title] follows the
    player — an episode hop keeps the series, a new title brings its own). */
@Composable
internal fun WatchLogTicker(player: Player, title: String) {
    val ctx = LocalContext.current
    val name by rememberUpdatedState(title)
    LaunchedEffect(player) {
        while (true) {
            delay(500)
            WatchLog.tick(ctx, name, player.isPlaying)
        }
    }
}
