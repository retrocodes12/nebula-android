package com.nuvio.ckplayer

import android.content.Context
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
 * Watch-party client: a thin WebSocket wrapper around the nebula-party room server. Only sync messages travel
 * here — every member streams the video themselves. Mirrors the shared HTML player's protocol, v2 since 1.107.0
 * (brief party-2026-11-06.md): the relay's clock, statuses, chat, host-only, remove, end for everyone, a stream
 * that may carry no address (the host keeps its link), the sender's receipt. A v1 relay simply never answers the
 * clock and the session runs as before.
 */

data class PartyStreamDesc(
    val url: String, val title: String, val subs: List<SubTrack>,
    // content identity rides along so viewers get subtitles + progress (and can look the title up in their own add-ons)
    val type: String? = null, val id: String? = null, val name: String? = null,
    val poster: String? = null, val addonUrl: String? = null,
    val file: String? = null, val hash: String? = null,
    /** false = the host keeps its link to itself; [url] is then "" and viewers find the file themselves */
    val share: Boolean = true,
) {
    val key: String get() = PartySync.keyOf(type, id, url)
}

/**
 * The party's playback. pos is seconds; when live=true it means "seconds behind the live edge" (engine-neutral).
 * [at] is the relay's clock in ms (v2) or this device's (v1: arrival − age).
 */
data class PartyState(
    val playing: Boolean, val pos: Double, val live: Boolean,
    val rate: Double = 1.0, val waiting: Boolean = false,
    val at: Long = System.currentTimeMillis(), val seq: Long = 0, val by: String = "", val name: String = "",
)

data class PartyMember(val id: String, val name: String, val host: Boolean, val s: String, val k: String, val behind: Int?, val v: Int)

data class PartyChatLine(
    val id: Long, val by: String, val name: String, val text: String, val cid: String? = null,
    val pend: Boolean = false, val fail: Boolean = false,
)

sealed interface PartyEvent {
    data class Created(val code: String, val id: String, val v2: Boolean, val hostOnly: Boolean) : PartyEvent
    data class Joined(
        val code: String, val id: String, val v2: Boolean, val host: Boolean, val hostOnly: Boolean,
        val stream: PartyStreamDesc?, val state: PartyState?, val count: Int,
        val members: List<PartyMember>, val chat: List<PartyChatLine>,
    ) : PartyEvent
    data class State(val state: PartyState) : PartyEvent
    data class Stated(val seq: Long, val at: Long) : PartyEvent
    data class StreamSwitch(val stream: PartyStreamDesc) : PartyEvent
    data class Peers(val count: Int, val names: List<String> = emptyList(), val members: List<PartyMember>? = null) : PartyEvent
    data class React(val emoji: String, val name: String) : PartyEvent
    data class Chat(val line: PartyChatLine) : PartyEvent
    data class Room(val hostOnly: Boolean) : PartyEvent
    data object Promoted : PartyEvent
    data class Ended(val reason: String, val removed: Boolean = false) : PartyEvent
    data class Error(val message: String, val chatCid: String? = null) : PartyEvent
    data object Disconnected : PartyEvent
}

object PartyConfig {
    private const val FALLBACK = "wss://play.rifflehq.in/party/ws"
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

    /** This install's id for the relay (a removed member stays out of that party), kept on the device only. */
    fun deviceId(ctx: Context): String {
        val p = ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE)
        val have = p.getString("party_dev", null)
        if (have != null && Regex("^[A-Za-z0-9_-]{8,64}$").matches(have)) return have
        val made = "a" + java.util.UUID.randomUUID().toString().replace("-", "").take(20)
        p.edit().putString("party_dev", made).apply()
        return made
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
    @Volatile private var helloSent = false
    private var retryUntil = 0L
    private var wakeToastShown = false

    var displayName: String = "Guest"
    var deviceId: String = ""
    /** the relay answered its clock: it speaks v2 */
    @Volatile var v2 = false
        private set
    private val clock = PartySync.Clock()

    /** The relay's time on this device (its clock answers; this device's own on a v1 relay). */
    fun nowMs(): Long = System.currentTimeMillis() + clock.off

    fun sendReact(emoji: String) = send(JSONObject().put("t", "react").put("emoji", emoji.take(8)))

    fun create(stream: PartyStreamDesc, code: String? = null) = connect { sock ->
        sock.send(JSONObject().put("t", "create").put("name", displayName).put("v", 2).put("dev", deviceId).apply {
            if (code != null) put("code", code)
            // the host keeps its link to itself only on a relay that takes a stream without one
            put("stream", streamJson(if (!v2 && !stream.share) stream.copy(share = true) else stream))
        }.toString())
    }

    fun join(code: String) = connect { sock ->
        sock.send(JSONObject().put("t", "join").put("code", code).put("name", displayName).put("v", 2).put("dev", deviceId).toString())
    }

    /** [hb] = a heartbeat, dropped by the relay if someone acted since [base] (the seq this device last held). */
    fun sendState(playing: Boolean, pos: Double, live: Boolean, rate: Double = 1.0, waiting: Boolean = false, hb: Boolean = false, base: Long = 0) {
        val o = JSONObject().put("t", "state").put("playing", playing).put("pos", pos).put("live", live)
        if (v2) {
            o.put("rate", rate)
            if (waiting) o.put("waiting", true)
            if (hb) o.put("hb", true).put("base", base)
        }
        send(o)
    }

    fun sendStream(stream: PartyStreamDesc) = send(JSONObject().put("t", "stream").put("stream", streamJson(stream)))
    fun sendStatus(s: String, k: String, behind: Int?) {
        if (!v2) return
        send(JSONObject().put("t", "status").put("s", s).put("k", k).apply { if (behind != null) put("behind", behind) })
    }
    fun sendChat(text: String, cid: String) { if (v2) send(JSONObject().put("t", "chat").put("text", text).put("cid", cid)) }
    fun setHostOnly(on: Boolean) { if (v2) send(JSONObject().put("t", "hostonly").put("on", on)) }
    fun kick(id: String) { if (v2) send(JSONObject().put("t", "kick").put("id", id)) }
    fun endAll() { if (v2) send(JSONObject().put("t", "endall")) }

    fun leave() {
        closed = true
        try { ws?.send("""{"t":"leave"}""") } catch (e: Exception) {}
        try { ws?.close(1000, null) } catch (e: Exception) {}
        ws = null
    }

    private fun send(o: JSONObject) { try { ws?.send(o.toString()) } catch (e: Exception) {} }

    private fun streamJson(s: PartyStreamDesc): JSONObject = JSONObject().apply {
        put("title", s.title)
        if (s.share || s.type.isNullOrEmpty() || s.id.isNullOrEmpty()) {
            put("url", s.url)
            if (s.subs.isNotEmpty()) {
                put("subs", JSONArray().apply { s.subs.forEach { put(JSONObject().put("url", it.url).put("lang", it.lang)) } })
            }
        } else put("share", false)
        s.type?.let { put("type", it) }
        s.id?.let { put("id", it) }
        s.name?.let { put("name", it) }
        s.poster?.let { put("poster", it) }
        s.addonUrl?.let { put("addonUrl", it) }
        s.file?.takeIf { it.isNotEmpty() }?.let { put("file", it.take(300)) }
        s.hash?.takeIf { it.length == 40 }?.let { put("hash", it) }
    }

    private fun connect(onOpen: (WebSocket) -> Unit) {
        pendingOpen = onOpen
        retryUntil = System.currentTimeMillis() + 90_000
        attempt()
    }

    private fun clockAsk(sock: WebSocket) { try { sock.send(JSONObject().put("t", "clock").put("c", System.currentTimeMillis()).toString()) } catch (e: Exception) {} }

    private fun hello(sock: WebSocket) {
        if (helloSent) return
        helloSent = true
        pendingOpen?.invoke(sock)
    }

    private fun attempt() {
        scope.launch {
            if (closed) return@launch
            val url = PartyConfig.serverUrl()
            client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    established = true
                    ws = webSocket
                    helloSent = false
                    // the relay's clock first: its answer says it speaks v2 (and gives the first sample)
                    clockAsk(webSocket)
                    scope.launch { delay(1200); hello(webSocket) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val m = try { JSONObject(text) } catch (e: Exception) { return }
                    if (m.optString("t") == "clock") {
                        clock.sample(m.optLong("c"), m.optLong("s"), System.currentTimeMillis())
                        if (!v2) {
                            v2 = true
                            hello(webSocket)
                            // three more samples, 400 ms apart: the shortest round trip wins
                            scope.launch { repeat(3) { delay(400); clockAsk(webSocket) } }
                            // and one a minute for as long as the party lasts
                            scope.launch { while (!closed && ws === webSocket) { delay(60_000); clockAsk(webSocket) } }
                        }
                        return
                    }
                    val ev: PartyEvent? = when (m.optString("t")) {
                        "created" -> PartyEvent.Created(m.optString("code"), m.optString("id"), m.optInt("v") == 2, m.optBoolean("hostOnly"))
                        "joined" -> PartyEvent.Joined(
                            m.optString("code"), m.optString("id"), m.optInt("v") == 2, m.optBoolean("host"), m.optBoolean("hostOnly"),
                            m.optJSONObject("stream")?.let { parseStream(it) }?.takeIf { it.url.isNotEmpty() || !it.share },
                            m.optJSONObject("state")?.let { parseState(it) },
                            m.optInt("count", 1),
                            parseMembers(m.optJSONArray("members")) ?: emptyList(),
                            m.optJSONArray("chat")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { parseChat(it) } } } ?: emptyList(),
                        )
                        "state" -> PartyEvent.State(parseState(m))
                        "stated" -> PartyEvent.Stated(m.optLong("seq"), m.optLong("at"))
                        "stream" -> m.optJSONObject("stream")?.let { parseStream(it) }?.takeIf { it.url.isNotEmpty() || !it.share }?.let { PartyEvent.StreamSwitch(it) }
                        "peers" -> PartyEvent.Peers(m.optInt("count", 1), m.optJSONArray("names")?.let { arr ->
                            (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifEmpty { null } }
                        } ?: emptyList(), parseMembers(m.optJSONArray("members")))
                        "react" -> PartyEvent.React(m.optString("emoji"), m.optString("name"))
                        "chat" -> PartyEvent.Chat(parseChat(m))
                        "room" -> PartyEvent.Room(m.optBoolean("hostOnly"))
                        "host" -> PartyEvent.Promoted
                        "end" -> PartyEvent.Ended(m.optString("reason").ifEmpty { "Party ended." }, m.optBoolean("removed"))
                        "error" -> PartyEvent.Error(m.optString("message").ifEmpty { "Party error." }, if (m.optBoolean("chat")) m.optString("cid") else null)
                        else -> null
                    }
                    if (ev != null) scope.launch { onEvent(ev) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = gone()
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // A cold relay can take a while to answer — keep knocking for 90 s.
                    if (!established && !closed && System.currentTimeMillis() < retryUntil) {
                        if (!wakeToastShown) {
                            wakeToastShown = true
                            scope.launch { onEvent(PartyEvent.Error("Waking the party server. Hang on…")) }
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

    private fun parseState(o: JSONObject): PartyState {
        val at = if (v2 && o.optLong("at") > 0) o.optLong("at") else nowMs() - o.optLong("age", 0)
        val rate = o.optDouble("rate", 1.0).let { if (it.isNaN() || it < 0.25 || it > 4) 1.0 else it }
        return PartyState(
            o.optBoolean("playing"), o.optDouble("pos", 0.0), o.optBoolean("live"),
            rate, o.optBoolean("waiting"), at, o.optLong("seq"), o.optString("by"), o.optString("name"),
        )
    }

    private fun parseMembers(a: JSONArray?): List<PartyMember>? = a?.let {
        (0 until it.length()).mapNotNull { i ->
            it.optJSONObject(i)?.let { o ->
                PartyMember(o.optString("id"), o.optString("name").ifEmpty { "Guest" }, o.optBoolean("host"), o.optString("s", "loading"),
                    o.optString("k"), if (o.has("behind")) o.optInt("behind") else null, o.optInt("v", 1))
            }
        }.take(30)
    }

    private fun parseChat(o: JSONObject) = PartyChatLine(
        o.optLong("id"), o.optString("by"), o.optString("name").ifEmpty { "Guest" }, o.optString("text"), o.optString("cid").ifEmpty { null },
    )

    private fun parseStream(o: JSONObject): PartyStreamDesc {
        val subs = mutableListOf<SubTrack>()
        o.optJSONArray("subs")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { webUrl(it.optString("url")).takeIf { u -> u.isNotEmpty() }?.let { u -> subs.add(SubTrack(u, it.optString("lang"))) } }
        }
        // the host's addresses are someone else's: a stream or subtitle that is not a web address is dropped here, and a
        // stream left with no address is no stream at all unless it names a title to look up (UrlSafety.kt)
        return PartyStreamDesc(
            webUrl(o.optString("url")), o.optString("title").ifEmpty { "Watch party" }, subs,
            type = o.optString("type").ifEmpty { null }, id = o.optString("id").ifEmpty { null },
            name = o.optString("name").ifEmpty { null }, poster = o.optString("poster").ifEmpty { null },
            addonUrl = o.optString("addonUrl").ifEmpty { null },
            file = o.optString("file").ifEmpty { null }, hash = o.optString("hash").ifEmpty { null },
            share = !(o.has("share") && !o.optBoolean("share", true)),
        )
    }
}
