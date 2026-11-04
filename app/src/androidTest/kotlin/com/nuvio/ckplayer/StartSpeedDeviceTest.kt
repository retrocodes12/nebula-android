package com.nuvio.ckplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * How fast a debrid-style row starts: the row's address is a slow "resolve" link (800 ms, like an add-on's) that
 * redirects to the file. A Matroska file and an MP4 with its index at the end are each started at a resume point
 * through MediaHttp's source factory (the player's route) and through a plain one (no redirect memory), and the
 * test counts how often the slow link was asked. The player's route must ask it once.
 */
@RunWith(AndroidJUnit4::class)
class StartSpeedDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val app: Context = ins.targetContext
    private fun log(s: String) = Log.i("START-DEVICE", s)

    private inner class Server : AutoCloseable {
        private val sock = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val files = HashMap<String, ByteArray>()
        val resolves = AtomicInteger()
        val base = "http://127.0.0.1:${sock.localPort}"
        init { thread(isDaemon = true) { while (!sock.isClosed) runCatching { val s = sock.accept(); thread(isDaemon = true) { serve(s) } } } }

        private fun body(name: String): ByteArray? = synchronized(files) {
            files.getOrPut(name) { runCatching { ins.context.assets.open("dl/$name").readBytes() }.getOrNull() ?: return null }
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
                val out = s.getOutputStream()
                if (path.startsWith("/r/")) {
                    // the add-on's resolve link: slow, then a redirect to the file server
                    resolves.incrementAndGet()
                    Thread.sleep(800)
                    out.write("HTTP/1.1 302 Found\r\nLocation: $base/f/${path.removePrefix("/r/").substringBefore('?')}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    return@use
                }
                val name = path.removePrefix("/f/")
                val data = body(name)
                if (data == null) { out.write("HTTP/1.1 404 No\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return@use }
                val type = if (name.endsWith(".mkv")) "video/x-matroska" else "video/mp4"
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

    /** Ready at [startMs], then a jump to 22 s: the time to the first ready, and how often the slow link was asked. */
    private fun start(srv: Server, url: String, factory: MediaSource.Factory): Pair<Long, Int> {
        val before = srv.resolves.get()
        val ready = CountDownLatch(1)
        val jumped = CountDownLatch(1)
        var error: PlaybackException? = null
        var took = -1L
        val main = Handler(Looper.getMainLooper())
        lateinit var exo: ExoPlayer
        main.post {
            exo = ExoPlayer.Builder(app).setMediaSourceFactory(factory).build()
            val t0 = System.nanoTime()
            var seeked = false
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = e; ready.countDown(); jumped.countDown() }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state != Player.STATE_READY) return
                    if (!seeked) {
                        took = (System.nanoTime() - t0) / 1_000_000
                        ready.countDown()
                        seeked = true
                        exo.seekTo(22_000)
                    } else jumped.countDown()
                }
            })
            exo.setMediaItem(MediaItem.fromUri(url), 12_000)
            exo.prepare(); exo.play()
        }
        ready.await(60, TimeUnit.SECONDS)
        jumped.await(60, TimeUnit.SECONDS)
        val done = CountDownLatch(1)
        main.post { exo.release(); done.countDown() }
        done.await(10, TimeUnit.SECONDS)
        assertNull("error starting $url: ${error?.errorCodeName}", error)
        return took to (srv.resolves.get() - before)
    }

    @Test fun aDebridRowAsksItsSlowLinkOnce() {
        Server().use { srv ->
            for ((i, name) in listOf("film.mkv", "tail.mp4").withIndex()) {
                val plain = start(srv, "${srv.base}/r/$name?plain=$i", DefaultMediaSourceFactory(app))
                val ours = start(srv, "${srv.base}/r/$name?ours=$i", MediaHttp.mediaSourceFactory(app))
                log("$name: plain ready in ${plain.first} ms asking the link ${plain.second}×, ours ${ours.first} ms asking it ${ours.second}×")
                assertTrue("$name never became ready", ours.first > 0 && plain.first > 0)
                assertEquals("$name: the slow link must be asked once for the start and the jump", 1, ours.second)
                assertTrue("$name: the plain route was expected to ask it more than once (${plain.second})", plain.second > 1)
            }
        }
    }
}
