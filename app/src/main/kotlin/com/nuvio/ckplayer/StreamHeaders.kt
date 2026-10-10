package com.nuvio.ckplayer

import org.json.JSONObject

/**
 * A stream's own request headers (`behaviorHints.proxyHeaders.request`): some hosts answer only when the request
 * carries their Referer or Origin (the sports add-on's "India only" rows, 2026-10-10). They are noted per address
 * while a stream list is read ([note]) and sent with every request of the play that opens that address: PlayerScreen
 * sets [active] next to the relay choice and clears it when the player goes away; [MediaHttp] adds them.
 *
 * Add-on requests say the app can do this with `X-Nebula-Caps: headers`, so the add-on offers such rows only to an
 * app that will send them.
 */
internal object StreamHeaders {
    private const val MAX = 400

    /** The request's own framing and what the player decides itself are never taken from an add-on. */
    private val REFUSED = setOf(
        "host", "content-length", "connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade",
        "range", "accept-encoding", "proxy-authorization", "proxy-connection", "x-nebula-client", "x-nebula-caps",
    )
    private val NAME = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}$")

    private val byUrl = object : LinkedHashMap<String, Map<String, String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>) = size > MAX
    }

    /** One play's claim on the headers: a player that goes away clears only its own (two overlap during a swap). */
    class Active(val headers: Map<String, String>)

    /** The headers of the play now open, or null = none. */
    @Volatile var active: Active? = null

    /** A play opens [url]: its headers (if the list gave any) become the ones sent. Returns the claim to [end]. */
    fun begin(url: String): Active? = forUrl(url).takeIf { it.isNotEmpty() }?.let(::Active).also { active = it }

    fun end(claim: Active?) {
        if (claim != null && active === claim) active = null
    }

    /** `behaviorHints` → the headers worth sending: string values only, valid names, no line breaks, nothing refused. */
    fun parse(behaviorHints: JSONObject?): Map<String, String> {
        val request = behaviorHints?.optJSONObject("proxyHeaders")?.optJSONObject("request") ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (name in request.keys()) {
            val value = request.opt(name) as? String ?: continue
            if (!NAME.matches(name) || name.lowercase() in REFUSED) continue
            if (value.isEmpty() || value.length > 2048 || value.any { it == '\r' || it == '\n' || it == '\u0000' }) continue
            // one spelling per name: Media3 keeps request headers in a map, and "referer" beside "Referer" would race
            out[name.split('-').joinToString("-") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }] = value
            if (out.size >= 16) break
        }
        return out
    }

    @Synchronized fun note(url: String, headers: Map<String, String>) {
        if (headers.isEmpty()) byUrl.remove(url) else byUrl[url] = headers
    }

    @Synchronized fun forUrl(url: String): Map<String, String> = byUrl[url].orEmpty()
}
