@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.NotificationUtil
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** One saved or saving title, as the pages read it. */
internal data class SavedRow(
    val key: String,
    val url: String,
    val meta: DownloadPlan.Meta,
    val phase: DownloadPlan.Phase,
    val percent: Float,
    val bytes: Long,
    val total: Long,
    val request: DownloadRequest,
)

/**
 * Downloads, the way Netflix has them (a phone only; rules in DownloadPlan.kt): a film or episode kept inside the app to
 * watch with no connection. Media3's download stack over one [SimpleCache] in `noBackupFilesDir` — private to the app, out
 * of backups, and not playable anywhere else, which is the point. [DownloadsService] keeps it going with the app closed.
 *
 * Who saved a title is part of its key, so each who's-watching profile sees its own. A saved play ([begin]) reads through
 * the cache (MediaHttp's Patient), never the network; everything else about it is an ordinary play, with its resume point
 * and ticks.
 */
internal object Downloads {
    const val CHANNEL = "downloads"
    private const val NOTIFY_DONE = 7742
    private const val PAUSED = 1                      // our stop reason; 0 is Download.STOP_REASON_NONE
    private const val PARALLEL = 2
    private const val SUBS_WAIT_MS = 12_000L
    private const val TICK_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val service = DownloadsService::class.java

    @Volatile private var cache: Cache? = null
    private var mgr: DownloadManager? = null
    private var known: List<Download> = emptyList()
    private var ticker: Job? = null
    private val adding = HashSet<String>()            // a second tap while the stream is still being checked

    /** Every profile's rows, newest first; read [mine] for a page. */
    var rows by mutableStateOf<List<SavedRow>>(emptyList()); private set

    fun available(ctx: Context) = !Account.isTv(ctx)

    /** Called at launch on a phone: the rows come up, and anything unfinished carries on. */
    fun start(ctx: Context) {
        if (available(ctx)) manager(ctx)
    }

    /** Main thread only, like everything that touches the manager. */
    fun manager(ctx: Context): DownloadManager = mgr ?: run {
        val app = ctx.applicationContext
        val db = StandaloneDatabaseProvider(app)
        val c = SimpleCache(File(app.noBackupFilesDir, "downloads"), NoOpCacheEvictor(), db)
        cache = c
        DownloadManager(app, DefaultDownloadIndex(db), Fetcher(app, c)).also { m ->
            m.maxParallelDownloads = PARALLEL
            m.requirements = needs(app)
            m.addListener(Events(app))
            mgr = m
        }
    }

    /** Read from the file, not Prefs: after a kill the service comes back with no activity to have loaded Prefs. */
    private fun wifiOnly(ctx: Context) = ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE).getBoolean("pref_dlwifi", true)
    private fun needs(ctx: Context) = Requirements(if (wifiOnly(ctx)) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    /** Settings › Playback › Wi-Fi only, taking effect on what is already waiting. */
    fun networkChanged(ctx: Context) { mgr?.requirements = needs(ctx) }

    private fun http(ctx: Context, headers: Map<String, String>): DefaultHttpDataSource.Factory =
        MediaHttp.httpFactory(ctx).setDefaultRequestProperties(
            MediaHttp.HEADERS + ("User-Agent" to MediaHttp.userAgent(ctx)) + headers,
        )

    /** Each download fetches with its own stream's headers: a host that answers only with its Referer gets it. */
    private class Fetcher(private val app: Context, private val cache: Cache) : DownloaderFactory {
        private val pool = Executors.newFixedThreadPool(4)
        override fun createDownloader(request: DownloadRequest): Downloader {
            val source = CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory(http(app, DownloadPlan.Meta.read(request.data)?.headers.orEmpty()))
            return DefaultDownloaderFactory(source, pool).createDownloader(request)
        }
    }

    private class Events(private val app: Context) : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            reload()
            // the manager waits for the service: unfinished downloads pick up as the app opens
            if (downloadManager.currentDownloads.isNotEmpty()) runCatching { DownloadService.start(app, service) }
        }

        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            reload()
            val name = DownloadPlan.Meta.read(download.request.data)?.name ?: return
            val note = DownloadNotificationHelper(app, CHANNEL)
            val icon = R.drawable.ic_stat_episode
            when (download.state) {
                Download.STATE_COMPLETED -> note.buildDownloadCompletedNotification(app, icon, openPage(app), name)
                Download.STATE_FAILED -> note.buildDownloadFailedNotification(app, icon, openPage(app), name)
                else -> null
            }?.let { NotificationUtil.setNotification(app, NOTIFY_DONE + (download.request.id.hashCode() and 0xFFF), it) }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            reload()
            scope.launch(Dispatchers.IO) { extras(app, download.request.id).deleteRecursively() }
        }

        override fun onRequirementsStateChanged(downloadManager: DownloadManager, requirements: Requirements, notMetRequirements: Int) = reload()
        override fun onWaitingForRequirementsChanged(downloadManager: DownloadManager, waitingForRequirements: Boolean) = reload()
        override fun onIdle(downloadManager: DownloadManager) = reload()
    }

    /** The notification's tap: the Downloads tab. */
    internal fun openPage(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(Intent.ACTION_VIEW, Uri.parse("nebula://play?open=downloads"), ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun reload() {
        val m = mgr ?: return
        val all = ArrayList<Download>()
        runCatching { m.downloadIndex.getDownloads().use { c -> while (c.moveToNext()) all += c.download } }
        known = all
        publish()
        // the index only changes on a state change; while something runs, its numbers are read once a second
        if (ticker?.isActive != true && rows.any { it.phase == DownloadPlan.Phase.RUNNING }) ticker = scope.launch {
            while (rows.any { it.phase == DownloadPlan.Phase.RUNNING }) { delay(TICK_MS); publish() }
        }
    }

    private fun publish() {
        val m = mgr ?: return
        val live = m.currentDownloads.associateBy { it.request.id }
        rows = known.map { live[it.request.id] ?: it }.mapNotNull { row(it, m) }.sortedByDescending { it.meta.at }
    }

    private fun row(d: Download, m: DownloadManager): SavedRow? {
        val meta = DownloadPlan.Meta.read(d.request.data) ?: return null
        val phase = when (d.state) {
            Download.STATE_COMPLETED -> DownloadPlan.Phase.DONE
            Download.STATE_FAILED -> DownloadPlan.Phase.FAILED
            Download.STATE_STOPPED -> DownloadPlan.Phase.PAUSED
            Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> DownloadPlan.Phase.RUNNING
            Download.STATE_QUEUED -> if (m.notMetRequirements != 0) DownloadPlan.Phase.WAITING else DownloadPlan.Phase.QUEUED
            else -> return null                           // on its way out
        }
        return SavedRow(d.request.id, d.request.uri.toString(), meta, phase, d.percentDownloaded, d.bytesDownloaded, d.contentLength, d.request)
    }

    fun mine(): List<SavedRow> = rows.filter { it.meta.viewer == Viewers.current }
    fun find(type: String, id: String): SavedRow? = rows.firstOrNull { it.meta.viewer == Viewers.current && it.meta.type == type && it.meta.id == id }
    fun saved(type: String, id: String): SavedRow? = find(type, id)?.takeIf { it.phase == DownloadPlan.Phase.DONE }
    fun savable(st: StreamItem) = DownloadPlan.savable(st.url, st.isTorrent, DeviceEmbed.isPage(st.url))
    fun line(ctx: Context, r: SavedRow) = DownloadPlan.line(r.phase, r.percent, r.bytes, r.total, wifiOnly(ctx))
    fun freeBytes(ctx: Context) = ctx.noBackupFilesDir.usableSpace

    private fun extras(ctx: Context, key: String): File {
        val h = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
        return File(ctx.noBackupFilesDir, "download-extras/$h")
    }

    /**
     * Queue [st] for [item]. Null = it is on its way; otherwise the sentence saying why not. Nothing is queued until the
     * stream's first bytes say what it is: an address cannot tell a film from a live stream, and a live stream has no end
     * for a download to reach.
     */
    suspend fun add(ctx: Context, st: StreamItem, from: Addon, item: MetaItem, source: String?): String? =
        enqueue(ctx, st, from, item, source) { dir -> keepSubs(ctx.applicationContext, dir, st, item) }

    /** [add] with the subtitles handed in: DownloadsDeviceTest runs this one, as the debug build cannot load the add-on list. */
    internal suspend fun enqueue(
        ctx: Context, st: StreamItem, from: Addon, item: MetaItem, source: String?, keep: suspend (File) -> List<SubTrack>,
    ): String? {
        val app = ctx.applicationContext
        if (!savable(st)) return DownloadPlan.NO_KIND
        val key = Viewers.current + "/" + item.type + "/" + item.id
        if (find(item.type, item.id) != null || !adding.add(key)) return null
        try {
            val headers = StreamHeaders.forUrl(st.url)
            val kind = withContext(Dispatchers.IO) { readHead(app, st.url, headers) }
            if (kind is DownloadPlan.Source.Refused) return kind.why
            if (!DownloadPlan.hasRoom((kind as? DownloadPlan.Source.File)?.size ?: st.videoSize, freeBytes(app))) return DownloadPlan.NO_ROOM
            val dir = extras(app, key)
            val art = withContext(Dispatchers.IO) { keepArt(dir, item.poster) }
            val subs = keep(dir)
            val meta = DownloadPlan.Meta(
                viewer = Viewers.current, type = item.type, id = item.id, name = item.name, poster = item.poster,
                shape = item.posterShape, art = art, addonUrl = from.manifestUrl, streamName = st.name, streamTitle = st.title,
                fileName = st.fileName, source = source, description = item.description, subs = subs, headers = headers,
                at = System.currentTimeMillis(),
            )
            val request = when (kind) {
                is DownloadPlan.Source.File -> fileRequest(key, st.url, meta)
                else -> when (val r = playlistRequest(app, key, st.url, meta)) {
                    is Planned.Why -> { dir.deleteRecursively(); return r.text }
                    is Planned.Ok -> r.request
                }
            }
            DownloadService.sendAddDownload(app, service, request, false)
            return null
        } finally {
            adding.remove(key)
        }
    }

    // the key stays the same when a retry brings a fresh link, so what is already here is kept
    private fun fileRequest(key: String, url: String, meta: DownloadPlan.Meta) =
        DownloadRequest.Builder(key, Uri.parse(url)).setCustomCacheKey(key).setData(meta.toBytes()).build()

    private fun readHead(ctx: Context, url: String, headers: Map<String, String>): DownloadPlan.Source = runCatching {
        val req = Request.Builder().url(url)
            .header("User-Agent", MediaHttp.userAgent(ctx))
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=0-2047")
            .apply { (MediaHttp.HEADERS + headers).forEach { (k, v) -> header(k, v) } }
            .build()
        MediaHttp.client.newCall(req).execute().use { res ->
            val buf = ByteArray(2048)
            var n = 0
            res.body?.byteStream()?.let { s ->
                while (n < buf.size) { val got = s.read(buf, n, buf.size - n); if (got < 0) break; n += got }
            }
            DownloadPlan.sniff(
                res.code, res.header("Content-Type"), String(buf, 0, n, Charsets.UTF_8),
                DownloadPlan.totalOf(res.code, res.header("Content-Range"), res.header("Content-Length")),
            )
        }
    }.getOrDefault(DownloadPlan.Source.Refused(DownloadPlan.NO_REACH))

    private sealed interface Planned {
        data class Ok(val request: DownloadRequest) : Planned
        data class Why(val text: String) : Planned
    }

    /** A playlist is read first, for the same reason as [readHead], and for its best picture: all of them would be 5× the size. */
    private suspend fun playlistRequest(ctx: Context, key: String, url: String, meta: DownloadPlan.Meta): Planned =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val item = MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                val helper = DownloadHelper.Factory()
                    .setDataSourceFactory(http(ctx, meta.headers))
                    .setRenderersFactory(DefaultRenderersFactory(ctx))
                    .create(item)
                cont.invokeOnCancellation { helper.release() }
                helper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                        val pl = (helper.manifest as? HlsManifest)?.mediaPlaylist
                        val encrypted = pl == null || pl.protectionSchemes != null ||
                            pl.segments.any { it.fullSegmentEncryptionKeyUri != null || it.drmInitData != null }
                        val why = DownloadPlan.hlsRefusal(pl?.hasEndTag == true, encrypted)
                        val answer = if (why != null) Planned.Why(why) else Planned.Ok(helper.getDownloadRequest(key, meta.toBytes()))
                        helper.release()
                        if (cont.isActive) cont.resume(answer)
                    }

                    override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                        helper.release()
                        val live = e is DownloadHelper.LiveContentUnsupportedException
                        if (cont.isActive) cont.resume(Planned.Why(if (live) DownloadPlan.NO_LIVE else DownloadPlan.NO_REACH))
                    }
                })
            }
        }

    /** The poster for an offline list; a failure only means the list shows the title's initial. */
    private fun keepArt(dir: File, poster: String?): String? {
        if (poster == null) return null
        return runCatching {
            val req = Request.Builder().url(poster).build()
            MediaHttp.client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                dir.mkdirs()
                File(dir, "art").also { f -> res.body?.byteStream()?.use { s -> f.outputStream().use { s.copyTo(it) } } }.path
            }
        }.getOrNull()
    }

    /** The viewer's subtitle languages, kept as files so they play offline. A slow add-on does not hold the download up. */
    private suspend fun keepSubs(ctx: Context, dir: File, st: StreamItem, item: MetaItem): List<SubTrack> =
        withTimeoutOrNull(SUBS_WAIT_MS) {
            coroutineScope {
                val offers = st.subtitles + activeAddons(ctx).map { a ->
                    async {
                        try {
                            if (manifestFor(a.manifestUrl).canSubs(item.type, item.id)) Stremio.loadSubtitles(a.base, item.type, item.id) else emptyList()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
                val langs = listOf(Prefs.subLang.ifEmpty { java.util.Locale.getDefault().language }, Prefs.subLang2).filter { it.isNotEmpty() }
                DownloadPlan.pickSubs(offers, langs, ::subLangKey).mapIndexedNotNull { i, s ->
                    val text = subText(s) ?: return@mapIndexedNotNull null
                    withContext(Dispatchers.IO) {
                        dir.mkdirs()
                        val f = File(dir, DownloadPlan.subFile(i, text)).apply { writeText(text) }
                        SubTrack(Uri.fromFile(f).toString(), s.lang)
                    }
                }
            }
        }.orEmpty()

    fun pause(ctx: Context, r: SavedRow) = DownloadService.sendSetStopReason(ctx.applicationContext, service, r.key, PAUSED, false)
    fun resume(ctx: Context, r: SavedRow) = DownloadService.sendSetStopReason(ctx.applicationContext, service, r.key, Download.STOP_REASON_NONE, false)
    fun remove(ctx: Context, r: SavedRow) = DownloadService.sendRemoveDownload(ctx.applicationContext, service, r.key, false)
    fun removeMine(ctx: Context) = mine().forEach { remove(ctx, it) }

    /**
     * A failed download goes on from what it has. Links die — a debrid address lasts hours, a long film on bad Wi-Fi can
     * outlast it — so the add-on is asked for the same row again first. Null = queued, or the sentence saying why not.
     */
    suspend fun retry(ctx: Context, r: SavedRow): String? {
        val app = ctx.applicationContext
        val addons = activeAddons(app)
        val a = addons.firstOrNull { it.manifestUrl == r.meta.addonUrl } ?: return requeue(app, r, r.url)
        val fresh = try {
            DownloadPlan.sameRow(Stremio.loadStreams(a.base, r.meta.type, r.meta.id), r.url, r.meta)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return DownloadPlan.NO_REACH
        } ?: return "That stream is gone. Delete this download and pick another."
        return requeue(app, r, fresh.url)
    }

    private fun requeue(app: Context, r: SavedRow, url: String): String? {
        val request = if (r.request.customCacheKey != null) fileRequest(r.key, url, r.meta)
            else r.request                                // a playlist's pieces are keyed by address: same address only
        DownloadService.sendAddDownload(app, service, request, false)
        return null
    }

    /** This play's saved copy: what the player needs to read it back as it was saved. */
    class Claim(val request: DownloadRequest) {
        val cacheKey: String? get() = request.customCacheKey
        val mime: String? get() = request.mimeType
        val keys: List<StreamKey> get() = request.streamKeys
    }

    /** The saved play going on; while set, MediaHttp reads web addresses through [reader]. */
    @Volatile var active: Claim? = null

    /** Claimed as the play opens, as [StreamHeaders.begin] is: another address clears it. */
    fun begin(url: String): Claim? =
        rows.firstOrNull { it.url == url && it.phase == DownloadPlan.Phase.DONE }?.let { Claim(it.request) }.also { active = it }

    fun end(claim: Claim?) { if (claim != null && active === claim) active = null }

    fun isSaved(url: String) = rows.any { it.url == url && it.phase == DownloadPlan.Phase.DONE }

    /** Reads the saved bytes, and writes nothing. Null on a TV, which has no downloads. */
    fun reader(ctx: Context): DataSource? = cache?.let {
        CacheDataSource.Factory().setCache(it).setUpstreamDataSourceFactory(MediaHttp.httpFactory(ctx))
            .setCacheWriteDataSinkFactory(null).createDataSource()
    }
}

/** Keeps a download going with the app closed, with the system's progress notification. */
class DownloadsService : DownloadService(
    7741, DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, Downloads.CHANNEL, R.string.downloads_channel, 0,
) {
    override fun getDownloadManager(): DownloadManager = Downloads.manager(this)

    // No scheduler: from Android 12 one may not start this service from the background anyway, so it stays up, its
    // notification saying what it waits for, until the network it needs is back
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        DownloadNotificationHelper(this, Downloads.CHANNEL).buildProgressNotification(
            this, R.drawable.ic_stat_episode, Downloads.openPage(this), null, downloads, notMetRequirements,
        )
}
