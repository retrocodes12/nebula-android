package com.nuvio.ckplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * New-episode alerts (phone only; Settings › Playback, off by default). Every ~12 h, with any network, WorkManager runs
 * [NewEpisodeWorker]: for every show followed by either profile (NewEpisodesPlan.shows), the title's meta is asked of
 * the add-on it came from — then the profile's other add-ons that serve meta, as the title page does — and every
 * episode out since that show's last check gets one notification per show, grouped when there are several. A tap opens
 * the show's title page (DeepLinks `open=title`). Checks remembered in prefs `newep_v1` {"<profile>|series:<id>": ms}.
 */
object NewEpisodes {
    private const val P = "ckplayer"
    private const val WORK = "new-episodes"
    private const val CHANNEL = "new_episodes"
    private const val GROUP = "com.nuvio.ckplayer.NEW_EPISODES"
    private const val SUMMARY_ID = 5190

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)

    /** On only where the switch is on, and never on a TV. */
    fun wanted(ctx: Context): Boolean = !Account.isTv(ctx) && prefs(ctx).getBoolean("pref_newep", false)

    /** The schedule follows the switch: kept as it is when on (KEEP), cancelled when off. Safe to call at every launch. */
    fun sync(ctx: Context) {
        val wm = runCatching { WorkManager.getInstance(ctx.applicationContext) }.getOrNull() ?: return
        if (!wanted(ctx)) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<NewEpisodeWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

    /** One pass. Every show is asked (four at a time); a show whose meta could not be read keeps its old check time. */
    suspend fun check(ctx: Context) {
        if (!wanted(ctx)) return
        if (Cloud.profile == null) runCatching { Cloud.load(ctx) }   // a cold start for the worker: the first profile's name
        val p = prefs(ctx)
        val profiles = Viewers.list(ctx)
        val two = profiles.size == 2
        val now = System.currentTimeMillis()
        val last = readMap(p.getString("newep_v1", null))
        data class Work(val show: NewEpisodesPlan.Show, val addons: List<Triple<String, String, Boolean>>, val watched: Set<String>)
        val jobs = ArrayList<Work>()
        for (v in profiles) {
            val progress = json(p.getString(Viewers.local("progress", v.id), null))
            val library = json(p.getString(Viewers.local("library", v.id), null))
            val addons = NewEpisodesPlan.addons(p.getString(Viewers.local("addons", v.id), null)).filter { it.third }
            val watched = NewEpisodesPlan.watched(progress)
            NewEpisodesPlan.shows(v.id, progress, library).forEach { jobs += Work(it, addons, watched) }
        }
        val gate = Semaphore(4)
        val answers = coroutineScope {
            jobs.map { w ->
                async {
                    gate.withPermit {
                        val eps = runCatching { episodesOf(w.show, w.addons) }
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                        Triple(w, eps, eps?.let { NewEpisodesPlan.fresh(it, last[w.show.key] ?: 0L, now, w.watched) })
                    }
                }
            }.map { it.await() }
        }
        val next = HashMap(NewEpisodesPlan.keep(last, jobs.map { it.show }))
        val hits = ArrayList<Pair<NewEpisodesPlan.Show, List<Episode>>>()
        for ((w, eps, fresh) in answers) {
            if (eps == null) continue                       // unread: asked again next time from the same point
            next[w.show.key] = now
            if (!fresh.isNullOrEmpty()) hits += w.show to fresh
        }
        p.edit().putString("newep_v1", writeMap(next)).apply()
        if (hits.isNotEmpty() && canNotify(ctx)) post(ctx, hits, if (two) profiles.associate { it.id to Viewers.name(it) } else null)
    }

    /** The show's episodes: from the add-on it came from, else the profile's other add-ons that serve its meta. */
    private suspend fun episodesOf(show: NewEpisodesPlan.Show, addons: List<Triple<String, String, Boolean>>): List<Episode>? {
        val order = addons.sortedByDescending { it.first == show.addonUrl }
        for ((url, base, _) in order) {
            if (url != show.addonUrl) {
                val m = runCatching { Stremio.loadManifest(url) }.getOrNull() ?: continue
                if (!m.canMeta(show.type, show.id)) continue
            }
            val eps = runCatching { Stremio.loadSeriesVideos(base, show.type, show.id) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            if (!eps.isNullOrEmpty()) return eps
        }
        return null
    }

    private fun post(ctx: Context, hits: List<Pair<NewEpisodesPlan.Show, List<Episode>>>, names: Map<String, String>?) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A show in My List or Continue watching has a new episode"
            })
        fun builder() = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val lines = ArrayList<String>()
        for ((show, eps) in hits) {
            val (title, text) = NewEpisodesPlan.lines(show.name, eps, names?.get(show.profile))
            lines += "$title · $text"
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.titleUri(show.type, show.id, show.addonUrl, show.name)))
                .setClass(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(ctx, show.key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = builder()
                .setSmallIcon(R.drawable.ic_stat_episode)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            runCatching { nm.notify(show.key, 1, n) }
        }
        if (hits.size > 1) {
            val style = Notification.InboxStyle()
            lines.take(6).forEach { style.addLine(it) }
            val n = builder()
                .setSmallIcon(R.drawable.ic_stat_episode)
                .setContentTitle("${hits.size} shows have new episodes")
                .setContentText(lines.first())
                .setStyle(style)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(SUMMARY_ID, n) }
        }
    }

    private fun json(s: String?): JSONObject = runCatching { JSONObject(s ?: "{}") }.getOrDefault(JSONObject())

    private fun readMap(s: String?): Map<String, Long> {
        val out = HashMap<String, Long>()
        runCatching { val o = JSONObject(s ?: "{}"); o.keys().forEach { k -> out[k] = o.optLong(k) } }
        return out
    }

    private fun writeMap(m: Map<String, Long>): String = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }.toString()
}

/** The ~12-hourly pass ([NewEpisodes.check]); a failure waits for the next one rather than retrying in a loop. */
class NewEpisodeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { NewEpisodes.check(applicationContext) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        return Result.success()
    }
}
