package com.nuvio.ckplayer

import java.net.URLEncoder

/**
 * What a nebula:// link asks for. Every form rides the one link the app has always answered, `nebula://play`, told
 * apart by its query:
 *  - `nebula://play?mpd=<web address>&t=<title>` plays an address straight away (the sports add-on's card);
 *  - `nebula://play?open=resume&type=<type>&id=<id>` resumes something this profile has started (the Android TV
 *    launcher's Watch Next row, WatchNext.kt);
 *  - `nebula://play?open=title&type=<type>&id=<id>&addon=<manifest>&n=<name>` opens a title page (a new-episode alert,
 *    NewEpisodes.kt);
 *  - `nebula://play?open=downloads` opens Library › Downloads (a download's notification, Downloads.kt).
 * Any web page can fire these, so each field is checked here and the app acts only on its own data: a resume needs a
 * record in this profile's history, a title page's add-on must be one that is installed (else the first one is asked).
 */
sealed class LinkReq {
    data class Play(val mpd: String, val title: String) : LinkReq()
    data class Resume(val type: String, val id: String) : LinkReq()
    data class Title(val type: String, val id: String, val addon: String, val name: String) : LinkReq()
    data object Downloads : LinkReq()
}

object DeepLinks {
    private val TYPE_RE = Regex("^[a-z][a-z0-9_-]{0,23}$")
    // printable ASCII with no space: tt0903747:1:2, kitsu:123:4, tmdb:1399 — the id is only ever looked up, never run
    private val ID_RE = Regex("^[\\x21-\\x7E]{1,200}$")

    /** The link's request, or null when it is not one of ours or a field fails its check. [q] reads a query value. */
    fun parse(scheme: String?, host: String?, q: (String) -> String?): LinkReq? {
        if (!scheme.equals("nebula", ignoreCase = true) || !host.equals("play", ignoreCase = true)) return null
        when (q("open")?.trim()) {
            null, "" -> {
                val mpd = q("mpd")?.trim().orEmpty()
                if (mpd.isEmpty()) return null
                // a web address only, never file:// or content:// (the player would read this app's own files, or another
                // app's, on a stranger's say-so)
                val s = mpd.substringBefore(':', "").lowercase()
                if (s != "http" && s != "https") return null
                val title = (q("t") ?: q("title") ?: "Nebula Sports").trim().take(200).ifEmpty { "Nebula Sports" }
                return LinkReq.Play(mpd, title)
            }
            "resume" -> {
                val type = q("type")?.trim().orEmpty()
                val id = q("id")?.trim().orEmpty()
                if (!TYPE_RE.matches(type) || !ID_RE.matches(id)) return null
                return LinkReq.Resume(type, id)
            }
            "title" -> {
                val type = q("type")?.trim().orEmpty()
                val id = q("id")?.trim().orEmpty()
                if (!TYPE_RE.matches(type) || !ID_RE.matches(id)) return null
                val addon = q("addon")?.trim().orEmpty().takeIf { a ->
                    val s = a.substringBefore(':', "").lowercase()
                    a.length <= 500 && (s == "http" || s == "https")
                }.orEmpty()
                val name = q("n")?.trim().orEmpty().filter { it >= ' ' }.take(120)
                return LinkReq.Title(type, id, addon, name)
            }
            "downloads" -> return LinkReq.Downloads
            else -> return null
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun resumeUri(type: String, id: String) = "nebula://play?open=resume&type=${enc(type)}&id=${enc(id)}"

    fun titleUri(type: String, id: String, addon: String, name: String) =
        "nebula://play?open=title&type=${enc(type)}&id=${enc(id)}&addon=${enc(addon)}&n=${enc(name.take(120))}"
}
