@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import android.content.Context
import android.media.MediaDataSource
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

/**
 * One HTTP identity for everything that touches a stream.
 *
 * ExoPlayer fetches media, manifests, licences and subtitles through a `DefaultHttpDataSource`
 * built here (our User-Agent, `X-Nebula-Client`, cross-protocol redirects), and the scrub-frame
 * reader ([RangeSource]) goes through an OkHttp client wearing the same User-Agent, headers and
 * cookie store — so a host that fingerprints its clients sees one player, not a player plus a
 * stranger. Resolver links used to refuse the second connection for exactly that reason.
 */
internal object MediaHttp {
    val HEADERS = mapOf("X-Nebula-Client" to "android")
    private const val CONNECT_MS = 15_000
    private const val READ_MS = 20_000

    /** The cookie store both stacks share: HttpURLConnection reads the process default, OkHttp gets a bridge. */
    private val cookies: CookieManager by lazy {
        (CookieHandler.getDefault() as? CookieManager)
            ?: CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER).also { CookieHandler.setDefault(it) }
    }

    @Volatile private var ua: String? = null

    /** "NebulaPlayer/1.55.0 (Linux;Android 14) AndroidXMedia3/1.11.0" — the shape hosts expect from a player. */
    fun userAgent(ctx: Context): String =
        ua ?: Util.getUserAgent(ctx.applicationContext, "NebulaPlayer").also { ua = it }

    fun httpFactory(ctx: Context): DefaultHttpDataSource.Factory {
        cookies      // installed as the process default before the first request goes out
        return DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent(ctx))
            .setDefaultRequestProperties(HEADERS)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_MS)
            .setReadTimeoutMs(READ_MS)
    }

    /** What `ExoPlayer.Builder` gets: the same identity for media, manifests, subtitle files and licence calls. */
    fun mediaSourceFactory(ctx: Context): DefaultMediaSourceFactory {
        val http = httpFactory(ctx)
        val drm = DefaultDrmSessionManagerProvider().apply { setDrmHttpDataSourceFactory(http) }
        return DefaultMediaSourceFactory(ctx)
            .setDataSourceFactory(DefaultDataSource.Factory(ctx, http))   // file:// subtitles keep working
            .setDrmSessionManagerProvider(drm)
    }

    /** The reader's client: follows redirects across hosts and protocols, shares the cookie store above. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(SharedCookieJar(cookies))
            .connectTimeout(CONNECT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }
}

/** OkHttp's view of the java.net cookie store the player's HttpURLConnection writes to. */
private class SharedCookieJar(private val store: CookieManager) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        runCatching { store.put(url.toUri(), mapOf("Set-Cookie" to cookies.map { it.toString() })) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val h = runCatching { store.get(url.toUri(), emptyMap()) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Cookie>()
        for (line in h["Cookie"].orEmpty()) for (part in line.split(";")) {
            Cookie.parse(url, part.trim())?.let(out::add)
        }
        return out
    }
}

/**
 * The scrub reader's file, served to `MediaMetadataRetriever` as a [MediaDataSource]: every read is
 * a `Range: bytes=…` request through [MediaHttp.client] wearing the player's identity, the last two
 * 256 KB windows are kept so cue parsing does not fetch the same bytes twice, and the total comes
 * from the first reply's `Content-Range`. A host that ignores ranges, refuses the request or cannot
 * be reached sets [failure] — the playback Info panel prints that sentence.
 */
internal class RangeSource(private val url: String, private val ua: String) : MediaDataSource() {
    companion object { const val WINDOW = 256L * 1024 }
    private class Win(val start: Long, val bytes: ByteArray)

    private val wins = ArrayDeque<Win>()      // newest last; at most two
    private var total = -1L                   // unknown until the first reply
    /** Why the file cannot be read, once known — plain words for the Info panel. */
    @Volatile var failure: String? = null; private set

    /** The window holding [pos], fetched if need be; null with [failure] set when the host will not serve it. */
    private fun window(pos: Long): Win? {
        wins.firstOrNull { pos >= it.start && pos < it.start + it.bytes.size }?.let { return it }
        val start = pos - pos % WINDOW
        val end = if (total > 0) minOf(start + WINDOW - 1, total - 1) else start + WINDOW - 1
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")      // what the player sends; no transparent gzip on video bytes
            .header("Range", "bytes=$start-$end")
            .apply { MediaHttp.HEADERS.forEach { (k, v) -> header(k, v) } }
            .build()
        try {
            MediaHttp.client.newCall(req).execute().use { res ->
                when (res.code) {
                    206 -> {
                        val cr = res.header("Content-Range")
                        // a 206 for some other range would be copied into the wrong place: refuse it
                        if (contentRangeStart(cr) != start) { failure = "host answered with the wrong range"; return null }
                        if (total < 0) {
                            total = contentRangeTotal(cr) ?: run {
                                failure = "host did not say how long the file is"; return null
                            }
                        }
                    }
                    200 -> { failure = "host does not support range requests"; return null }
                    else -> { failure = "host refused the second connection (${res.code})"; return null }
                }
                val bytes = res.body?.bytes() ?: ByteArray(0)     // at most one window
                if (bytes.isEmpty()) { failure = "empty reply from the host"; return null }
                // a body shorter than the range asked for would read as a false end of file at [pos]
                if (pos >= start + bytes.size) { failure = "host cut the range short"; return null }
                val w = Win(start, bytes)
                if (wins.size >= 2) wins.removeFirst()
                wins.addLast(w)
                return w
            }
        } catch (e: IOException) {
            failure = "could not reach the host"
            return null
        }
    }

    /** "bytes 0-262143/1234567" → 0; null when malformed. */
    private fun contentRangeStart(h: String?): Long? {
        val m = Regex("bytes\\s+(\\d+)-\\d+/(?:\\d+|\\*)").find(h ?: return null) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    /** "bytes 0-262143/1234567" → 1234567; null when the size is starred or malformed. */
    private fun contentRangeTotal(h: String?): Long? {
        val m = Regex("bytes\\s+\\d+-\\d+/(\\d+)").find(h ?: return null) ?: return null
        return m.groupValues[1].toLongOrNull()?.takeIf { it > 0 }
    }

    override fun getSize(): Long {
        if (total < 0 && window(0) == null) throw IOException(failure ?: "unreadable")
        return total
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return 0
        if (position < 0 || (total >= 0 && position >= total)) return -1
        val w = window(position) ?: throw IOException(failure ?: "unreadable")
        val from = (position - w.start).toInt()
        val n = minOf(size, w.bytes.size - from)
        if (n <= 0) return -1
        System.arraycopy(w.bytes, from, buffer, offset, n)
        return n
    }

    override fun close() { wins.clear() }
}
