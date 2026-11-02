package com.nuvio.ckplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Downloads on a real Android, through the real stack: a film and a playlist fetched over HTTP from a server inside the
 * test (clip made by device-tests.yml, never committed), through the real service, then THE SERVER IS STOPPED and each is
 * played back through MediaHttp's own source factory, the player's route. A clock that moves with nothing to fetch from
 * is the whole claim of the feature. A live playlist must be refused.
 */
@RunWith(AndroidJUnit4::class)
class DownloadsDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val app: Context = ins.targetContext
    private fun log(s: String) = Log.i("DOWNLOADS-DEVICE", s)

    /** Plain HTTP with Range: the assets, and a live variant of the playlist that never ends. */
    private inner class Server : AutoCloseable {
        private val sock = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val files = HashMap<String, ByteArray>()
        val base = "http://127.0.0.1:${sock.localPort}"
        init { thread(isDaemon = true) { while (!sock.isClosed) runCatching { val s = sock.accept(); thread(isDaemon = true) { serve(s) } } } }

        private fun body(path: String): ByteArray? {
            if (path == "/hls/live.m3u8") return body("/hls/index.m3u8")?.let { String(it).replace("#EXT-X-ENDLIST", "").replace("#EXT-X-PLAYLIST-TYPE:VOD", "").toByteArray() }
            return synchronized(files) {
                files.getOrPut(path) { runCatching { ins.context.assets.open("dl" + path).readBytes() }.getOrNull() ?: return null }
            }
        }

        private fun serve(s: Socket) = runCatching {
            s.use {
                val rd = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val path = rd.readLine().split(' ')[1]
                var range: LongRange? = null
                while (true) {
                    val h = rd.readLine() ?: break
                    if (h.isEmpty()) break
                    if (h.lowercase().startsWith("range: bytes=")) {
                        val (a, b) = h.substringAfter('=').split('-')
                        range = a.toLong()..(b.toLongOrNull() ?: Long.MAX_VALUE)
                    }
                }
                val data = body(path)
                val out = s.getOutputStream()
                if (data == null) { out.write("HTTP/1.1 404 No\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return@use }
                val type = if (path.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp4"
                val from = range?.first?.toInt() ?: 0
                val to = minOf(range?.last ?: Long.MAX_VALUE, data.size - 1L).toInt()
                val head = if (range != null) "HTTP/1.1 206 Part\r\nContent-Range: bytes $from-$to/${data.size}\r\n" else "HTTP/1.1 200 OK\r\n"
                out.write((head + "Content-Type: $type\r\nContent-Length: ${to - from + 1}\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n").toByteArray())
                out.write(data, from, to - from + 1)
                out.flush()
            }
        }

        override fun close() = sock.close()
    }

    private val from = Addon("https://addon.test/manifest.json", "Test", "https://addon.test")
    private fun add(url: String, item: MetaItem): String? = runBlocking(Dispatchers.Main) {
        Downloads.enqueue(app, StreamItem("Test", "Test", url), from, item, "1080p · Test") { emptyList() }
    }

    private fun waitDone(item: MetaItem): SavedRow {
        val until = System.currentTimeMillis() + 180_000
        while (System.currentTimeMillis() < until) {
            val r = Downloads.rows.firstOrNull { it.meta.id == item.id }
            if (r?.phase == DownloadPlan.Phase.DONE) return r
            check(r?.phase != DownloadPlan.Phase.FAILED) { "the download failed" }
            Thread.sleep(250)
        }
        error("no finish: " + Downloads.rows.firstOrNull { it.meta.id == item.id })
    }

    /** As PlayerScreen opens a saved play, minus the screen. The clock must move past 3 s with no error. */
    private fun playsOffline(r: SavedRow, mime: String?) {
        val moved = CountDownLatch(1)
        var error: PlaybackException? = null
        var dur = C.TIME_UNSET
        val main = Handler(Looper.getMainLooper())
        lateinit var exo: ExoPlayer
        main.post {
            val claim = Downloads.begin(r.url)
            assertNotNull("a finished download must claim its address", claim)
            exo = ExoPlayer.Builder(app).setMediaSourceFactory(MediaHttp.mediaSourceFactory(app)).build()
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = e; moved.countDown() }
            })
            val b = MediaItem.Builder().setUri(r.url)
            mime?.let { b.setMimeType(it) }
            claim!!.let { s -> b.setCustomCacheKey(s.cacheKey).setStreamKeys(s.keys); s.mime?.let { m -> b.setMimeType(m) } }
            exo.setMediaItem(b.build()); exo.prepare(); exo.play()
            fun look() {
                dur = exo.duration
                if (exo.currentPosition > 3_000) moved.countDown() else main.postDelayed({ look() }, 200)
            }
            look()
        }
        val ok = moved.await(60, TimeUnit.SECONDS)
        val done = CountDownLatch(1)
        var pos = 0L
        main.post { pos = exo.currentPosition; exo.release(); Downloads.end(Downloads.active); done.countDown() }
        done.await(10, TimeUnit.SECONDS)
        log("played offline to ${pos} ms of ${dur} ms, error=${error?.errorCodeName}")
        assertNull("error playing offline: ${error?.errorCodeName}", error)
        assertTrue("the clock did not move offline (at $pos ms)", ok && pos > 3_000)
        assertTrue("a known length", dur > 3_000)
    }

    @Before fun setUp() {
        // a test device may be on a metered network: Wi-Fi only must not hold this up
        app.getSharedPreferences("ckplayer", Context.MODE_PRIVATE).edit().putBoolean("pref_dlwifi", false).commit()
        ins.runOnMainSync { Downloads.manager(app) }
    }

    @Test fun fileSavesAndPlaysWithTheServerGone() {
        val item = MetaItem("dl-file", "movie", "Film", null)
        val started = System.currentTimeMillis()
        Server().use { srv -> assertNull(add(srv.base + "/film.mp4", item)); waitDone(item) }
        val r = Downloads.rows.first { it.meta.id == item.id }
        log("file: ${r.bytes} bytes in ${System.currentTimeMillis() - started} ms")
        assertEquals(r.total, r.bytes)
        playsOffline(r, null)
        ins.runOnMainSync { Downloads.remove(app, r) }
    }

    @Test fun playlistSavesAndPlaysWithTheServerGone() {
        val item = MetaItem("dl-hls", "movie", "Playlist", null)
        val url: String
        Server().use { srv ->
            url = srv.base + "/hls/index.m3u8"
            assertNull(add(url, item))
            waitDone(item)
        }
        val r = Downloads.rows.first { it.meta.id == item.id }
        log("playlist: ${r.bytes} bytes, keys ${r.request.streamKeys}")
        assertEquals(MimeTypes.APPLICATION_M3U8, r.request.mimeType)
        assertEquals(url, r.url)
        playsOffline(r, MimeTypes.APPLICATION_M3U8)
        ins.runOnMainSync { Downloads.remove(app, r) }
    }

    @Test fun liveAndHtmlAreRefused() {
        Server().use { srv ->
            assertEquals(DownloadPlan.NO_LIVE, add(srv.base + "/hls/live.m3u8", MetaItem("dl-live", "movie", "Live", null)))
            assertEquals(DownloadPlan.NO_REACH, add(srv.base + "/nothing", MetaItem("dl-none", "movie", "None", null)))
        }
        assertTrue(Downloads.rows.none { it.meta.id == "dl-live" || it.meta.id == "dl-none" })
    }
}
