package com.nuvio.ckplayer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Watch-party client: a thin WebSocket wrapper around the nebula-party room
 * server. Only sync messages travel here — every member streams the video
 * themselves. Mirrors the shared HTML player's protocol.
 */

data class PartyStreamDesc(val url: String, val title: String, val subs: List<SubTrack>)

/** pos is seconds; when live=true it means "seconds behind the live edge" (engine-neutral). */
data class PartyState(val playing: Boolean, val pos: Double, val live: Boolean, val atLocal: Long)

sealed interface PartyEvent {
    data class Created(val code: String) : PartyEvent
    data class Joined(val code: String, val stream: PartyStreamDesc?, val state: PartyState?, val count: Int) : PartyEvent
    data class State(val state: PartyState) : PartyEvent
    data class StreamSwitch(val stream: PartyStreamDesc) : PartyEvent
    data class Peers(val count: Int) : PartyEvent
    data object Promoted : PartyEvent
    data class Ended(val reason: String) : PartyEvent
    data class Error(val message: String) : PartyEvent
    data object Disconnected : PartyEvent
}

object PartyConfig {
    private const val FALLBACK = "wss://nebula-party.onrender.com/ws"
    @Volatile private var cached: String? = null

    /** The server address lives in party.json on the download site so it can move without app updates. */
    suspend fun serverUrl(): String = cached ?: withContext(Dispatchers.IO) {
        val url = try {
            val txt = java.net.URL("https://retrocodes12.github.io/nebula-player/party.json").readText()
            JSONObject(txt).optString("server").ifEmpty { FALLBACK }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FALLBACK
        }
        cached = url
        url
    }
}

class PartySession(
    private val scope: CoroutineScope,
    private val onEvent: (PartyEvent) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    @Volatile private var established = false
    @Volatile private var closed = false
    private var pendingOpen: ((WebSocket) -> Unit)? = null
    private var retryUntil = 0L
    private var wakeToastShown = false

    fun create(stream: PartyStreamDesc, code: String? = null) = connect { sock ->
        sock.send(JSONObject().put("t", "create").put("name", "Host").apply {
            if (code != null) put("code", code)
            put("stream", streamJson(stream))
        }.toString())
    }

    fun join(code: String) = connect { sock ->
        sock.send(JSONObject().put("t", "join").put("code", code).put("name", "Guest").toString())
    }

    fun sendState(playing: Boolean, pos: Double, live: Boolean) {
        ws?.send(JSONObject().put("t", "state").put("playing", playing).put("pos", pos).put("live", live).toString())
    }

    fun sendStream(stream: PartyStreamDesc) {
        ws?.send(JSONObject().put("t", "stream").put("stream", streamJson(stream)).toString())
    }

    fun leave() {
        closed = true
        try { ws?.send("""{"t":"leave"}""") } catch (e: Exception) {}
        try { ws?.close(1000, null) } catch (e: Exception) {}
        ws = null
    }

    private fun streamJson(s: PartyStreamDesc): JSONObject = JSONObject().apply {
        put("url", s.url)
        put("title", s.title)
        if (s.subs.isNotEmpty()) {
            put("subs", JSONArray().apply { s.subs.forEach { put(JSONObject().put("url", it.url).put("lang", it.lang)) } })
        }
    }

    private fun connect(onOpen: (WebSocket) -> Unit) {
        pendingOpen = onOpen
        retryUntil = System.currentTimeMillis() + 90_000
        attempt()
    }

    private fun attempt() {
        scope.launch {
            if (closed) return@launch
            val url = PartyConfig.serverUrl()
            client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    established = true
                    ws = webSocket
                    pendingOpen?.invoke(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val m = try { JSONObject(text) } catch (e: Exception) { return }
                    val ev: PartyEvent? = when (m.optString("t")) {
                        "created" -> PartyEvent.Created(m.optString("code"))
                        "joined" -> PartyEvent.Joined(
                            m.optString("code"),
                            m.optJSONObject("stream")?.let { parseStream(it) },
                            m.optJSONObject("state")?.let { parseState(it) },
                            m.optInt("count", 1),
                        )
                        "state" -> PartyEvent.State(parseState(m))
                        "stream" -> m.optJSONObject("stream")?.let { PartyEvent.StreamSwitch(parseStream(it)) }
                        "peers" -> PartyEvent.Peers(m.optInt("count", 1))
                        "host" -> PartyEvent.Promoted
                        "end" -> PartyEvent.Ended(m.optString("reason").ifEmpty { "Party ended." })
                        "error" -> PartyEvent.Error(m.optString("message").ifEmpty { "Party error." })
                        else -> null
                    }
                    if (ev != null) scope.launch { onEvent(ev) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = gone()
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // A free-tier Render instance takes 30–60s to wake — keep knocking.
                    if (!established && !closed && System.currentTimeMillis() < retryUntil) {
                        if (!wakeToastShown) {
                            wakeToastShown = true
                            scope.launch { onEvent(PartyEvent.Error("Waking the party server — hang on…")) }
                        }
                        scope.launch { delay(5000); attempt() }
                    } else gone()
                }
            })
        }
    }

    private fun gone() {
        ws = null
        if (!closed) {
            closed = true
            scope.launch { onEvent(PartyEvent.Disconnected) }
        }
    }

    private fun parseState(o: JSONObject) = PartyState(
        o.optBoolean("playing"),
        o.optDouble("pos", 0.0),
        o.optBoolean("live"),
        System.currentTimeMillis() - o.optLong("age", 0),
    )

    private fun parseStream(o: JSONObject): PartyStreamDesc {
        val subs = mutableListOf<SubTrack>()
        o.optJSONArray("subs")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { subs.add(SubTrack(it.optString("url"), it.optString("lang"))) }
        }
        return PartyStreamDesc(o.optString("url"), o.optString("title").ifEmpty { "Watch party" }, subs)
    }
}
