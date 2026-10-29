package com.nuvio.ckplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import org.json.JSONObject

/**
 * What goes into the Android TV launcher's Watch Next row: Continue watching of the profile this device is on, one
 * entry per film or show (a show's newest episode), newest first. Pure, for the tests.
 */
object WatchNextPlan {
    const val MAX = 12

    data class Entry(
        /** Ours, stable across episodes: "movie:tt…", "series:tt…" — what the launcher row is remembered by. */
        val key: String,
        val type: String,
        /** The record's own id (the episode's, for a show): what the row resumes. */
        val id: String,
        val title: String,
        val episodeTitle: String?,
        val season: Int?,
        val episode: Int?,
        val poster: String?,
        val landscape: Boolean,
        val posMs: Long,
        val durMs: Long,
        val at: Long,
    )

    fun keyOf(type: String, id: String): String = if (type == "series") "series:" + seriesIdOf(id) else "$type:$id"

    /** "Show · S2E5 · Episode name" (how the player names an episode) → (show, season, episode, episode name). */
    internal fun splitName(name: String): Triple<String, Pair<Int, Int>?, String?> {
        val parts = name.split(" · ")
        val m = parts.getOrNull(1)?.trim()?.let { Regex("""(?i)^S(\d+)E(\d+)$""").matchEntire(it) }
            ?: return Triple(name, null, null)
        val se = m.groupValues[1].toIntOrNull()?.let { s -> m.groupValues[2].toIntOrNull()?.let { e -> s to e } }
        return Triple(parts[0], se, parts.drop(2).joinToString(" · ").ifEmpty { null })
    }

    /**
     * [recs] = the records worth resuming (Progress.continueList). A title the viewer took off the row ([removed]: key →
     * when) stays off until a record of it is newer than that — it was played again.
     */
    fun entries(recs: List<ProgressRec>, removed: Map<String, Long>): List<Entry> {
        val seen = HashSet<String>()
        val out = ArrayList<Entry>()
        for (r in recs.sortedByDescending { it.at }) {
            val key = keyOf(r.type, r.id)
            if (!seen.add(key)) continue
            val gone = removed[key]
            if (gone != null && r.at <= gone) continue
            val (title, se, ep) = splitName(r.name)
            val nums = se ?: episodeNumbersOf(r.id)?.let { (s, e) -> s?.toIntOrNull()?.let { it to (e.toIntOrNull() ?: 0) } }
            out += Entry(
                key = key, type = r.type, id = r.id,
                title = title.ifBlank { "Untitled" }, episodeTitle = ep,
                season = nums?.first, episode = nums?.second?.takeIf { it > 0 },
                poster = r.poster?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                landscape = r.shape == "landscape",
                posMs = r.pos, durMs = r.dur, at = r.at,
            )
            if (out.size == MAX) break
        }
        return out
    }

    /** The rows of ours to take away: remembered, and no longer wanted. */
    fun drops(entries: List<Entry>, known: Set<String>): Set<String> = known - entries.map { it.key }.toSet()

    /** Takings-off remembered, newest first, at most [cap]; one played again since it was taken off is forgotten. */
    fun keepRemoved(removed: Map<String, Long>, recs: List<ProgressRec>, cap: Int = 100): Map<String, Long> {
        val newest = HashMap<String, Long>()
        recs.forEach { r -> val k = keyOf(r.type, r.id); newest[k] = maxOf(newest[k] ?: 0L, r.at) }
        return removed.filter { (k, at) -> (newest[k] ?: 0L) <= at }
            .entries.sortedByDescending { it.value }.take(cap).associate { it.key to it.value }
    }
}

/**
 * The Android TV launcher's "Watch Next" row (Play Next on some launchers), fed from Continue watching (TV only,
 * Settings › Playback, on by default). An app needs no permission for its own Watch Next rows. The ids of the rows this
 * app put there are kept (prefs `wn_ids`), so only those are ever touched; a row the viewer removed from the launcher
 * (gone, or no longer browsable) is remembered (`wn_removed`) and not put back until that title is played again.
 * The rows belong to one account + profile (`wn_owner`): another one — a profile switch, a sign-in elsewhere — takes
 * all of ours away first; after a sign-out nothing is shown until something is played (`wn_hold`).
 * Updates are throttled (a playing film saves every 10 s: at most once a minute) and run off the main thread.
 */
object WatchNext {
    private const val P = "ckplayer"
    private const val THROTTLE_MS = 60_000L
    private const val SOON_MS = 2_000L

    private val main = Handler(Looper.getMainLooper())
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "watch-next").apply { isDaemon = true } }
    private var lastRun = 0L
    private var dueAt = 0L
    private var app: Context? = null
    private val tick = Runnable { dueAt = 0L; lastRun = SystemClock.uptimeMillis(); app?.let { run(it) } }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)

    /** Something in the history changed. [soon] for a change of WHAT is listed (a new title, one finished or removed,
        a sync, a profile switch, the app leaving the screen); a position moving waits for the minute. Any thread. */
    fun poke(ctx: Context, soon: Boolean) {
        if (!Account.isTv(ctx)) return
        val a = ctx.applicationContext
        main.post { schedule(a, soon) }
    }

    /** A real play saved its place: after a sign-out, the row comes back from here. */
    fun played(ctx: Context) {
        if (!Account.isTv(ctx)) return
        val p = prefs(ctx)
        if (p.getBoolean("wn_hold", false)) { p.edit().remove("wn_hold").apply(); poke(ctx, soon = true) }
    }

    /** Signed out on this device: every row of ours goes, and none comes back until something is played. */
    fun signedOut(ctx: Context) {
        if (!Account.isTv(ctx)) return
        prefs(ctx).edit().putBoolean("wn_hold", true).apply()
        poke(ctx, soon = true)
    }

    private fun schedule(ctx: Context, soon: Boolean) {
        app = ctx
        val now = SystemClock.uptimeMillis()
        val wait = if (soon) SOON_MS else maxOf(SOON_MS, THROTTLE_MS - (now - lastRun))
        val due = now + wait
        if (dueAt != 0L && dueAt <= due) return            // already coming, as soon or sooner
        main.removeCallbacks(tick)
        dueAt = due
        main.postAtTime(tick, due)
    }

    /** On the main thread: the snapshot (the progress store is read and written there); the launcher off it. */
    private fun run(ctx: Context) {
        val p = prefs(ctx)
        val owner = (Cloud.accountId(ctx) ?: "") + "/" + Viewers.current
        val recs = if (Prefs.watchNext && !p.getBoolean("wn_hold", false))
            runCatching { Progress.continueList(ctx) }.getOrDefault(emptyList()) else emptyList()
        worker.execute { runCatching { apply(ctx, owner, recs) } }
    }

    private fun apply(ctx: Context, owner: String, recs: List<ProgressRec>) {
        val p = prefs(ctx)
        val helper = PreviewChannelHelper(ctx)
        val ids = readMap(p.getString("wn_ids", null))
        var removed = readMap(p.getString("wn_removed", null))
        if (p.getString("wn_owner", null) != owner) {
            ids.values.forEach { delete(ctx, it) }
            ids.clear(); removed = LinkedHashMap()
        } else {
            // taken off the launcher by the viewer: remembered, so it is not put straight back
            val now = System.currentTimeMillis()
            for ((k, id) in ids.toMap()) {
                val prog = try { helper.getWatchNextProgram(id) } catch (e: Exception) { return }   // no launcher here: try later
                if (prog == null || !prog.isBrowsable) { removed[k] = now; delete(ctx, id); ids.remove(k) }
            }
        }
        removed = LinkedHashMap(WatchNextPlan.keepRemoved(removed, recs))
        val want = WatchNextPlan.entries(recs, removed)
        for (k in WatchNextPlan.drops(want, ids.keys)) { ids[k]?.let { delete(ctx, it) }; ids.remove(k) }
        for (e in want) {
            val prog = program(ctx, e)
            val id = ids[e.key]
            if (id != null) runCatching { helper.updateWatchNextProgram(prog, id) }
            else runCatching { helper.publishWatchNextProgram(prog) }.getOrNull()?.takeIf { it > 0 }?.let { ids[e.key] = it }
        }
        p.edit().putString("wn_ids", writeMap(ids)).putString("wn_removed", writeMap(removed)).putString("wn_owner", owner).apply()
    }

    private fun program(ctx: Context, e: WatchNextPlan.Entry): WatchNextProgram {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.resumeUri(e.type, e.id)))
            .setClass(ctx, MainActivity::class.java)
        // one setter per statement: the builders' setters return their own class's builder, not this one
        val b = WatchNextProgram.Builder()
        b.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
        b.setLastEngagementTimeUtcMillis(e.at)
        b.setType(if (e.type == "series") TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE else TvContractCompat.PreviewProgramColumns.TYPE_MOVIE)
        b.setLastPlaybackPositionMillis(e.posMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        b.setDurationMillis(e.durMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        b.setInternalProviderId(e.key)
        b.setIntent(intent)
        b.setTitle(e.title)
        e.episodeTitle?.let { b.setEpisodeTitle(it) }
        e.season?.let { b.setSeasonNumber(it) }
        e.episode?.let { b.setEpisodeNumber(it) }
        e.poster?.let {
            b.setPosterArtUri(Uri.parse(it))
            b.setPosterArtAspectRatio(
                if (e.landscape) TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9
                else TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3
            )
        }
        return b.build()
    }

    private fun delete(ctx: Context, id: Long) {
        runCatching { ctx.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null) }
    }

    private fun readMap(s: String?): LinkedHashMap<String, Long> {
        val out = LinkedHashMap<String, Long>()
        runCatching { val o = JSONObject(s ?: "{}"); o.keys().forEach { k -> out[k] = o.optLong(k) } }
        return out
    }

    private fun writeMap(m: Map<String, Long>): String = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }.toString()
}
