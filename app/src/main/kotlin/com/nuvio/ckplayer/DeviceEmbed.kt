@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A stream row whose address is a web player page, not a stream (`behaviorHints.nebulaEmbed`; the sports add-on's
 * SportzX "LIVE [FHD]" rows, 2026-10-30). The page's stream answers only an Indian address — so never the add-on's
 * server — and only a real Chrome network stack: curl, OkHttp and Node got 403 with the very same headers. So the
 * play happens here, on the viewer's own connection, in a WebView out of sight:
 *
 * 1. [open] loads the page in a WebView laid behind the player and waits for the first `.m3u8` it asks for
 *    (`shouldInterceptRequest`; ~6 s measured), the page's ads kept from navigating it away;
 * 2. the WebView is then parked on a blank page of the SAME origin ([BRIDGE]), which stops the page's own player;
 * 3. every request of the play (playlists, pieces, keys) goes through [Source] → [Session.fetch] → the parked page's
 *    `fetch()` (Origin and Referer = the page's site, as the page's player sent them) → bytes back over the JS bridge.
 *
 * Add-on requests say the app can do this with `X-Nebula-Caps: embed` (Stremio.kt); the add-on offers such rows
 * only to an app that will. One play at a time: [active], cleared by the player when it goes away.
 */
internal object DeviceEmbed {
    private const val MAX = 200
    private const val RESOLVE_MS = 30_000L
    private const val PARK_MS = 10_000L
    private const val FETCH_MS = 20_000L
    private const val BODY_MAX = 64 * 1024 * 1024
    private val HLS = Regex("\\.m3u8$", RegexOption.IGNORE_CASE)

    /** Silences the page's own player while it starts: the viewer hears the stream once, from ours. */
    private const val MUTE_JS =
        "(function(){var m=function(){var l=document.querySelectorAll('video,audio');" +
            "for(var i=0;i<l.length;i++){l[i].muted=true;l[i].volume=0;}};setInterval(m,100);m();})();"

    /** The parked page: `nf(id, url)` fetches with the page's origin and hands the body back in base64 slices. */
    private const val BRIDGE = """<!doctype html><html><head><meta charset="utf-8"></head><body><script>
function b64(u8){var s='',C=32768;for(var i=0;i<u8.length;i+=C){s+=String.fromCharCode.apply(null,u8.subarray(i,i+C));}return btoa(s);}
function nf(id,url){fetch(url,{cache:'no-store'}).then(function(r){return r.arrayBuffer().then(function(b){
var u8=new Uint8Array(b),N=786432;for(var i=0;i<u8.length;i+=N){NebulaBridge.chunk(id,b64(u8.subarray(i,i+N)));}
NebulaBridge.done(id,r.status,r.headers.get('content-type')||'',r.url||url);});})
.catch(function(e){NebulaBridge.fail(id,String((e&&e.message)||e));});}
NebulaBridge.ready();
</script></body></html>"""

    private val pages = object : LinkedHashMap<String, Boolean>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > MAX
    }

    /** The open play's page session, or null. */
    @Volatile var active: Session? = null
        private set

    /** `behaviorHints` → whether the row is a page this app opens itself. */
    fun parse(behaviorHints: JSONObject?): Boolean =
        behaviorHints?.optJSONObject("nebulaEmbed")?.optString("capture") == "hls"

    @Synchronized fun note(url: String, isPage: Boolean) {
        if (isPage) pages[url] = true else pages.remove(url)
    }

    @Synchronized fun isPage(url: String): Boolean = pages[url] == true

    /**
     * A request of the open play goes through the page: the stream's host and the hosts its playlists name — not every
     * address, because the next player can be starting another stream while this one fades out.
     */
    fun owns(uri: Uri): Boolean {
        val s = active ?: return false
        val u = uri.toString()
        return (u.startsWith("https://") || u.startsWith("http://")) && uri.host?.lowercase() in s.hosts
    }

    private val PLAYLIST_HOST = Regex("(?:^|URI=\")https?://([^/\"\\s:]+)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    /** The hosts a playlist's absolute addresses name (lines and `URI="…"`); nothing for a body that is not a playlist. */
    fun playlistHosts(body: ByteArray): Set<String> {
        if (body.size > 2 * 1024 * 1024) return emptySet()
        val text = String(body, Charsets.UTF_8)
        if (!text.startsWith("#EXTM3U")) return emptySet()
        return PLAYLIST_HOST.findAll(text).map { it.groupValues[1].lowercase() }.toSet()
    }

    /** Opens [page] out of sight and returns the session once the page asked for its stream; throws when it never did. */
    suspend fun open(activity: Activity, page: String): Session = withContext(Dispatchers.Main) {
        end(active)
        val session = Session(activity, page)
        try {
            session.start()
            active = session
            session
        } catch (t: Throwable) {
            session.close()
            throw t
        }
    }

    /** The player goes away (or opens something else): closes [s] if it is still the open one. */
    fun end(s: Session?) {
        if (s == null) return
        if (active === s) active = null
        s.close()
    }

    class Answer(val status: Int, val body: ByteArray, val type: String, val url: String)

    @SuppressLint("SetJavaScriptEnabled")
    class Session(private val activity: Activity, private val page: String) {
        private val main = Handler(Looper.getMainLooper())
        private val pageUri = Uri.parse(page)
        private val origin = "${pageUri.scheme}://${pageUri.authority}"
        private val found = CompletableDeferred<String>()
        private val parked = CompletableDeferred<Unit>()
        private val ids = AtomicInteger()
        private val waiting = ConcurrentHashMap<Int, Pending>()
        @Volatile private var closed = false
        @Volatile private var parking = false
        private var web: WebView? = null

        /** The stream the page asked for. */
        lateinit var stream: String
            private set

        /** The hosts this play reads through the page ([owns]). */
        val hosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** A playlist that names pieces on other hosts: those go through the page too. */
        fun learn(body: ByteArray) {
            hosts.addAll(playlistHosts(body))
        }

        private class Pending {
            val latch = CountDownLatch(1)
            val out = ByteArrayOutputStream()
            var status = 0
            var type = ""
            var url = ""
            var error: String? = null
        }

        private inner class Bridge {
            @JavascriptInterface fun chunk(id: Int, b64: String) {
                val p = waiting[id] ?: return
                if (p.out.size() > BODY_MAX) { p.error = "too large"; return }
                runCatching { p.out.write(Base64.decode(b64, Base64.NO_WRAP)) }.onFailure { p.error = "unreadable" }
            }

            @JavascriptInterface fun done(id: Int, status: Int, type: String, url: String) {
                val p = waiting[id] ?: return
                p.status = status; p.type = type; p.url = url
                p.latch.countDown()
            }

            @JavascriptInterface fun fail(id: Int, why: String) {
                val p = waiting[id] ?: return
                p.error = why.take(120).ifEmpty { "failed" }
                p.latch.countDown()
            }

            @JavascriptInterface fun ready() { parked.complete(Unit) }
        }

        suspend fun start() {
            val wv = WebView(activity)
            web = wv
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }
            // a header the page's own player never sends; on the stream's host it turned the fetch into a refused preflight
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet())
            }
            val earlyMute = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (earlyMute) WebViewCompat.addDocumentStartJavaScript(wv, MUTE_JS, setOf("*"))
            val pageHost = pageUri.host
            wv.webChromeClient = WebChromeClient()
            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val u = request.url
                    if (!parking && !found.isCompleted && u.host != pageHost && HLS.containsMatchIn(u.path.orEmpty())) {
                        found.complete(u.toString())
                    }
                    return null
                }

                // the page's ads open their own pages in this window; it stays on the player page
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && !request.isRedirect

                override fun onPageFinished(view: WebView, url: String?) {
                    if (!earlyMute && !parking) view.evaluateJavascript(MUTE_JS, null)
                }
            }
            // laid out behind the app's own screen: a page that thinks it is shown starts its player
            activity.findViewById<ViewGroup>(android.R.id.content)?.addView(wv, 0, FrameLayout.LayoutParams(640, 360))
            wv.loadUrl(page)
            stream = withTimeoutOrNull(RESOLVE_MS) { found.await() } ?: throw IOException("the page gave no stream")
            Uri.parse(stream).host?.let { hosts.add(it.lowercase()) }
            parking = true
            wv.addJavascriptInterface(Bridge(), "NebulaBridge")      // on the parked page only: added before its load
            wv.loadDataWithBaseURL("$origin/", BRIDGE, "text/html", "utf-8", null)
            withTimeoutOrNull(PARK_MS) { parked.await() } ?: throw IOException("the page did not settle")
        }

        /** One request of the play, through the parked page. Blocks the loader thread that asks; never the main one. */
        fun fetch(url: String, timeoutMs: Long = FETCH_MS): Answer {
            if (closed) throw IOException("closed")
            val id = ids.incrementAndGet()
            val p = Pending()
            waiting[id] = p
            main.post {
                val wv = web
                if (closed || wv == null) { p.error = "closed"; p.latch.countDown() }
                else wv.evaluateJavascript("nf($id," + JSONObject.quote(url) + ")", null)
            }
            try {
                if (!p.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) throw SocketTimeoutException("no answer through the page")
            } finally {
                waiting.remove(id)
            }
            p.error?.let { throw IOException(it) }
            return Answer(p.status, p.out.toByteArray(), p.type, p.url)
        }

        fun close() {
            if (closed) return
            closed = true
            waiting.values.forEach { it.error = "closed"; it.latch.countDown() }
            main.post {
                web?.let { wv ->
                    runCatching { (wv.parent as? ViewGroup)?.removeView(wv) }
                    runCatching { wv.stopLoading(); wv.destroy() }
                }
                web = null
            }
        }
    }

    /** Media3's reader for [owns] addresses: the whole answer through the page, served from memory. */
    class Source : BaseDataSource(true) {
        private var body: ByteArray? = null
        private var at = 0
        private var end = 0
        private var uri: Uri? = null
        private var type = ""
        private var opened = false

        override fun open(dataSpec: DataSpec): Long {
            val s = active ?: throw IOException("the stream's page is closed")
            transferInitializing(dataSpec)
            val a = s.fetch(dataSpec.uri.toString())
            if (a.status !in 200..299) {
                throw HttpDataSource.InvalidResponseCodeException(a.status, null, null, emptyMap(), dataSpec, a.body)
            }
            s.learn(a.body)
            val size = a.body.size
            val from = dataSpec.position
            if (from > size) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            val len = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - from else minOf(dataSpec.length, size - from)
            body = a.body; at = from.toInt(); end = (from + len).toInt()
            uri = Uri.parse(a.url.ifEmpty { dataSpec.uri.toString() })
            type = a.type
            opened = true
            transferStarted(dataSpec)
            return len
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val b = body ?: return C.RESULT_END_OF_INPUT
            val n = minOf(length, end - at)
            if (n <= 0) return C.RESULT_END_OF_INPUT
            System.arraycopy(b, at, buffer, offset, n)
            at += n
            bytesTransferred(n)
            return n
        }

        override fun getUri(): Uri? = uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            if (type.isEmpty()) emptyMap() else mapOf("Content-Type" to listOf(type))

        override fun close() {
            body = null; uri = null
            if (opened) { opened = false; transferEnded() }
        }
    }
}
