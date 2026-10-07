package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/*
 * The TV Guide (2026-10-07, Android TV only): a sports schedule built from ANY enabled add-on whose catalog items carry
 * start times. No add-on with times = no Guide at all (the rail item does not exist). The shared web/webOS player builds
 * the same thing from the same spec (scratchpad guide-spec.md), so the rules below are the spec's, word for word where
 * it gives them.
 */

/** One catalog item as the guide reads it: the card it opens ([meta]) plus its schedule facts. */
data class GuideEntry(
    val meta: MetaItem,
    /** The start, epoch ms — `released` (ISO with a time part), else `releaseInfo`/`time` in the "YYYY-MM-DD HH:MM UTC" form. */
    val start: Long?,
    /** `isLive` the boolean true, or (with no start) a releaseInfo that is just the word LIVE ("🔴 LIVE", not "Live TV"). */
    val live: Boolean,
    /** How long it runs (no end times exist anywhere): the runtime, else the sport's usual length. */
    val minutes: Int,
    /** The group it sits in: the sport, emoji and symbols stripped. */
    val label: String,
)

/** Where an item came from, so OK opens it exactly as its Home card from that catalog would. */
data class GuideSource(val addon: Addon, val catalog: CatalogRef)
data class GuideItem(val entry: GuideEntry, val src: GuideSource)

/** A placed item: its span on the guide's clock (live items without a start get one around now). */
data class GuideBlock(val item: GuideItem, val start: Long, val end: Long) {
    val id: String get() = item.entry.meta.id
    val live: Boolean get() = item.entry.live
}

/** One row of the grid. A sport with overlapping games takes several lanes; its label shows on the first only. */
data class GuideLane(val label: String, val first: Boolean, val blocks: List<GuideBlock>)

/** The pure rules — no Android, no Compose: JVM-tested (GuideTest). */
object GuidePlan {
    const val MIN = 60_000L
    const val HOUR = 3_600_000L
    const val HALF = 1_800_000L
    const val AHEAD = 48 * HOUR

    private val TIME_RE = Regex("""^\s*(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?\s*(UTC|GMT|Z)?\s*$""")
    private val ISO_HAS_TIME = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}""")
    /** A releaseInfo that says live and nothing else: the channel list's "Live TV" (isLive false) is not an event. */
    private val LIVE_WORD = Regex("""^[^A-Za-z0-9]*live[^A-Za-z0-9]*$""", RegexOption.IGNORE_CASE)

    /** "2026-10-07 20:00 UTC" (also "…T20:00", ":SS", GMT, Z, or no zone — all UTC). */
    fun parseScheduleTime(s: String?): Long? {
        val m = TIME_RE.matchEntire(s ?: return null) ?: return null
        val g = m.groupValues
        return runCatching {
            LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }

    /** `released` as an ISO date-time WITH a time part; a bare date is no start. No zone = UTC (as the form above). */
    fun parseIsoDateTime(s: String?): Long? {
        val t = s?.trim() ?: return null
        if (!ISO_HAS_TIME.containsMatchIn(t)) return null
        return runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private val RUN_MIN = Regex("""^(\d+)\s*(?:m|min|mins|minute|minutes)?\.?$""")
    private val RUN_HM = Regex("""^(\d+)\s*h(?:r|rs|our|ours)?\s*(?:(\d+)\s*m(?:in|ins|inute|inutes)?\.?)?$""")

    /** `runtime` as minutes: 95, "95", "95 min", "1h 35m" → 95; anything else (or 0) → null. */
    fun runtimeMinutes(v: Any?): Int? {
        if (v is Number) return v.toInt().takeIf { it > 0 }
        val s = (v as? String)?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
        RUN_MIN.matchEntire(s)?.let { return it.groupValues[1].toIntOrNull()?.takeIf { n -> n > 0 } }
        RUN_HM.matchEntire(s)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return null
            val m = it.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: 0
            return (h * 60 + m).takeIf { n -> n > 0 }
        }
        return null
    }

    /** The sport → minutes table, in order: the first match wins ("american football" before "football"). */
    private val SPORT_MINUTES: List<Pair<List<String>, Int>> = listOf(
        listOf("american football", "nfl", "ncaaf") to 210,
        listOf("baseball", "mlb") to 180,
        listOf("basketball", "nba", "wnba", "euroleague") to 150,
        listOf("hockey", "nhl") to 150,
        listOf("cricket") to 240,
        listOf("tennis") to 150,
        listOf("golf") to 300,
        listOf("motorsport", "formula", "f1", "nascar", "indycar", "motogp") to 120,
        listOf("fight", "ufc", "boxing", "mma", "wwe") to 180,
        listOf("rugby") to 110,
        listOf("football", "soccer", "league", "liga", "serie", "bundesliga", "ligue", "cup", "brasileir", "uefa", "premier", "mls") to 115,
    )

    fun sportMinutes(sport: String?): Int {
        val s = sport.orEmpty().lowercase(Locale.US)
        if (s.isNotEmpty()) for ((words, mins) in SPORT_MINUTES) if (words.any { s.contains(it) }) return mins
        return 120
    }

    private val SYMBOLS = Regex("[\\p{So}\\p{Sk}\\p{Cs}\\p{Co}\\x{FE0F}\\x{FE0E}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}]")

    /** A group label: trimmed, emoji/symbols stripped; nothing left = "Other". */
    fun cleanLabel(s: String?): String =
        s.orEmpty().replace(SYMBOLS, "").replace(Regex("\\s+"), " ").trim().ifEmpty { "Other" }

    private fun str(o: JSONObject, k: String): String? =
        if (o.isNull(k)) null else (o.opt(k) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { (a.opt(it) as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }

    /** One catalog answer → entries (every meta with an id; the schedule test is [isSchedule]). Same card fields as Home. */
    fun parseCatalog(text: String, catalogType: String): List<GuideEntry> {
        val metas = runCatching { JSONObject(text).optJSONArray("metas") }.getOrNull() ?: return emptyList()
        val out = ArrayList<GuideEntry>()
        for (i in 0 until metas.length()) {
            val m = metas.optJSONObject(i) ?: continue
            val id = str(m, "id") ?: continue
            // genre may be a plain string (the sports add-on) or the old array form
            val genre = str(m, "genre") ?: strings(m.optJSONArray("genre")).firstOrNull()
            val genres = strings(m.optJSONArray("genres")).ifEmpty { strings(m.optJSONArray("genre")) }
            val description = str(m, "description")
            val releaseInfo = str(m, "releaseInfo")
            val start = parseIsoDateTime(str(m, "released"))
                ?: parseScheduleTime(releaseInfo)
                ?: parseScheduleTime(str(m, "time"))
            // a real boolean only: the string "False" (or "true") is not live; else, untimed, a releaseInfo that is only
            // the word live — 709 channel metas say "Live TV" with isLive false and must not become 709 live events
            val live = m.opt("isLive") == true || (start == null && releaseInfo != null && LIVE_WORD.matches(releaseInfo))
            val sportText = genre ?: genres.firstOrNull() ?: description?.split(Regex("\\s+"))?.firstOrNull()
            val minutes = runtimeMinutes(if (m.isNull("runtime")) null else m.opt("runtime")) ?: sportMinutes(sportText)
            val meta = MetaItem(
                id, str(m, "type") ?: catalogType, str(m, "name") ?: id, str(m, "poster"),
                str(m, "posterShape") ?: "poster",
                imdbRating = str(m, "imdbRating"),
                releaseInfo = releaseInfo ?: str(m, "year"),
                background = str(m, "background"),
                logo = str(m, "logo"),
                description = description,
                genres = genres.take(6),
            )
            out.add(GuideEntry(meta, start, live, minutes, cleanLabel(genre ?: genres.firstOrNull())))
        }
        return out
    }

    /** A catalog is a schedule when ≥1 item of its first page has a start or is live — a channel list with no times is not. */
    fun isSchedule(entries: List<GuideEntry>): Boolean = entries.any { it.start != null || it.live }

    /** One copy per meta id across catalogs, in first-seen order; a copy with a start replaces one without. */
    fun dedupe(items: List<GuideItem>): List<GuideItem> {
        val out = LinkedHashMap<String, GuideItem>()
        for (it in items) {
            val id = it.entry.meta.id
            val had = out[id]
            if (had == null || (had.entry.start == null && it.entry.start != null)) out[id] = it
        }
        return out.values.toList()
    }

    /** Each item's span now, kept when it has not ended and starts within 48 h. */
    fun blocks(items: List<GuideItem>, now: Long): List<GuideBlock> = items.mapNotNull { item ->
        val e = item.entry
        val st = e.start
        val dur = e.minutes * MIN
        val (s, end) = when {
            e.live && st == null -> (now - 90 * MIN) to (now + 60 * MIN)
            st == null -> return@mapNotNull null
            e.live -> st to maxOf(st + dur, now + 15 * MIN)
            else -> st to st + dur
        }
        if (end > now && s < now + AHEAD) GuideBlock(item, s, end) else null
    }

    /** Groups (live first, then by earliest start), items by start then name, packed greedily into lanes. */
    fun lanes(items: List<GuideItem>, now: Long): List<GuideLane> {
        val groups = LinkedHashMap<String, MutableList<GuideBlock>>()
        val names = HashMap<String, String>()
        for (b in blocks(items, now)) {
            val k = b.item.entry.label.lowercase(Locale.US)
            names.getOrPut(k) { b.item.entry.label }
            groups.getOrPut(k) { ArrayList() }.add(b)
        }
        val order = groups.entries.sortedWith(
            compareBy<Map.Entry<String, MutableList<GuideBlock>>>({ g -> if (g.value.any { it.live }) 0 else 1 },
                { g -> g.value.minOf { it.start } }, { g -> g.key }),
        )
        val out = ArrayList<GuideLane>()
        for (g in order) {
            val sorted = g.value.sortedWith(compareBy<GuideBlock>({ it.start }, { it.item.entry.meta.name.lowercase(Locale.US) }, { it.id }))
            val lanes = ArrayList<MutableList<GuideBlock>>()
            for (b in sorted) {
                val lane = lanes.firstOrNull { it.last().end <= b.start }
                if (lane != null) lane.add(b) else lanes.add(mutableListOf(b))
            }
            lanes.forEachIndexed { i, l -> out.add(GuideLane(names.getValue(g.key), i == 0, l)) }
        }
        return out
    }

    /** The grid's left edge: now floored to 30 minutes (local clock), minus 30 minutes. */
    fun windowStart(now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val off = tz.getOffset(now)
        val local = now + off
        return local - Math.floorMod(local, HALF) - off - HALF
    }

    /** The grid's right edge: the latest item end, at most 48 h on; at least two hours past the left edge. */
    fun windowEnd(lanes: List<GuideLane>, start: Long, now: Long): Long {
        val last = lanes.flatMap { it.blocks }.maxOfOrNull { it.end } ?: (start + 2 * HOUR)
        return maxOf(start + 2 * HOUR, minOf(last, now + AHEAD))
    }

    /** Where the guide lands: the first live block (top-most lane), else the earliest upcoming one, else the first. */
    fun landing(lanes: List<GuideLane>, now: Long): GuideBlock? {
        val all = lanes.flatMap { it.blocks }
        return all.firstOrNull { it.live }
            ?: all.filter { it.start >= now }.minWithOrNull(compareBy<GuideBlock>({ it.start }, { it.item.entry.meta.name }))
            ?: all.firstOrNull()
    }

    /**
     * Up/Down: in [lane], the block with the greatest overlap with the focused block's VISIBLE span ([visStart] =
     * max(its start, the window's left edge in view) → [end]); with no overlap, the one whose start is nearest.
     */
    fun pickInLane(lane: GuideLane, visStart: Long, end: Long): GuideBlock? {
        if (lane.blocks.isEmpty()) return null
        var best: GuideBlock? = null
        var bestOv = 0L
        for (b in lane.blocks) {
            val ov = minOf(end, b.end) - maxOf(visStart, b.start)
            if (ov > bestOv) { bestOv = ov; best = b }
        }
        return best ?: lane.blocks.minByOrNull { kotlin.math.abs(it.start - visStart) }
    }
}

/**
 * The scan behind the guide: every enabled add-on's browsable catalogs that are not films/series/anime, first page, no
 * extras, at most four requests at a time with a 15 s limit each, parsed off the main thread; cached five minutes and
 * redone when the add-ons change. [available] is what the TV rail reads — it follows the clock too (ended items go).
 */
object Guide {
    /** ≥1 schedule item survives the window: the rail shows "Guide". Compose state, written on the main thread. */
    var available by mutableStateOf(false)
        private set
    /** Every schedule item found (deduped), unfiltered by time — the screen places them against its own clock. */
    var items by mutableStateOf<List<GuideItem>>(emptyList())
        private set

    private const val CACHE_MS = 5 * 60_000L
    private const val FETCH_MS = 15_000L
    private val SKIP_TYPES = setOf("movie", "series", "anime")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var scannedAt = 0L
    private var scannedSig: String? = null
    private var runningSig: String? = null

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(FETCH_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(FETCH_MS, TimeUnit.MILLISECONDS)
            .readTimeout(FETCH_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()
    }

    /** Call on the main thread (AppRoot's minute tick, an add-on change with [force]). Cheap when the cache is fresh. */
    fun refresh(ctx: Context, force: Boolean = false) {
        val addons = activeAddons(ctx.applicationContext)
        val sig = addons.joinToString("\n") { it.manifestUrl }
        val now = System.currentTimeMillis()
        if (!force && sig == scannedSig && now - scannedAt < CACHE_MS) { recheck(); return }
        if (!force && job?.isActive == true && sig == runningSig) { recheck(); return }
        job?.cancel()
        runningSig = sig
        job = scope.launch {
            val found = runCatching { scan(addons) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            withContext(Dispatchers.Main) {
                // every request failing (no network) on the same add-ons keeps what was there rather than blanking it
                if (found != null && (found.second || sig != scannedSig)) items = found.first
                scannedAt = System.currentTimeMillis()
                scannedSig = sig
                recheck()
            }
        }
    }

    /** The clock moved: is anything still on (or due within 48 h)? */
    fun recheck() {
        val on = GuidePlan.blocks(items, System.currentTimeMillis()).isNotEmpty()
        if (on != available) available = on
    }

    /** (the schedule items, whether any catalog answered at all) */
    private suspend fun scan(addons: List<Addon>): Pair<List<GuideItem>, Boolean> = coroutineScope {
        val gate = Semaphore(4)
        val manifests = addons.map { a ->
            async { gate.withPermit { safe { withTimeoutOrNull(FETCH_MS) { manifestFor(a.manifestUrl) } } } }
        }.awaitAll()
        val wanted = ArrayList<GuideSource>()
        addons.forEachIndexed { i, a ->
            val info = manifests[i] ?: return@forEachIndexed
            for (c in info.catalogs) if (c.browsable && c.type.lowercase(Locale.US) !in SKIP_TYPES) wanted.add(GuideSource(a, c))
        }
        val pages = wanted.map { src ->
            async {
                val text = gate.withPermit { safe { fetch(catalogUrl(src)) } } ?: return@async null
                withContext(Dispatchers.Default) { GuidePlan.parseCatalog(text, src.catalog.type) }.let { src to it }
            }
        }.awaitAll()
        val answered = pages.any { it != null }
        val items = pages.filterNotNull()
            .filter { (_, entries) -> GuidePlan.isSchedule(entries) }
            .flatMap { (src, entries) -> entries.filter { it.start != null || it.live }.map { GuideItem(it, src) } }
        GuidePlan.dedupe(items) to answered
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun catalogUrl(s: GuideSource) = "${s.addon.base}/catalog/${enc(s.catalog.type)}/${enc(s.catalog.id)}.json"

    private suspend fun <T> safe(f: suspend () -> T?): T? = try { f() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    /** The same identity Home's catalog requests carry (the sports add-on recognises the app by it). */
    private suspend fun fetch(u: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(u)
            .header("Accept", "*/*").header("User-Agent", "NebulaPlayer").header("X-Nebula-Client", "android")
            .build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }
}
