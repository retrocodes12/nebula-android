package com.nuvio.ckplayer

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * What Downloads may keep and how it reads (pure; `DownloadPlanTest`; the engine is Downloads.kt).
 *
 * Only a finished file, or a finished unprotected playlist, is ever saved, and only inside the app. Never a live stream, a
 * protected one, a web player page, or a torrent: live has no end, a protected stream's keys are not ours to keep, and a
 * torrent would seed from the phone.
 */
internal object DownloadPlan {
    const val NO_LIVE = "Live streams can’t be downloaded. Try another stream."
    const val NO_PROTECTED = "Protected streams can’t be downloaded. Try another stream."
    const val NO_KIND = "This stream can’t be downloaded. Try another one."
    const val NO_REACH = "Couldn’t reach that stream. Try again, or try another one."
    const val NO_PAGE = "That stream is a web page, not a video. Try another one."
    const val NO_ROOM = "Not enough room on this phone for that download."
    const val NOT_READY = "That source isn’t ready yet. Try another one."

    /** Left free after a download: a phone filled to the last byte stops working properly. */
    const val MARGIN_BYTES = 500L shl 20
    /** Under this a "film" is a debrid host's "not ready yet" clip, not the film. */
    const val MIN_FILE_BYTES = 20L shl 20
    private const val LANGS_MAX = 2

    private val MPD = Regex("\\.mpd(\\?|#|$)")

    sealed interface Source {
        data class File(val size: Long) : Source
        data object Hls : Source
        data class Refused(val why: String) : Source
    }

    /** The row itself: is it worth offering? The stream's first bytes still decide ([sniff]). */
    fun savable(url: String, torrent: Boolean, page: Boolean): Boolean {
        if (torrent || page) return false
        val u = url.trim().lowercase(Locale.US)
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false
        return !MPD.containsMatchIn(u)
    }

    /** The whole size from a ranged answer's headers; 0 when the host does not say. */
    fun totalOf(code: Int, contentRange: String?, contentLength: String?): Long {
        contentRange?.substringAfterLast('/', "")?.trim()?.toLongOrNull()?.let { return it }
        return if (code == 200) contentLength?.trim()?.toLongOrNull() ?: 0L else 0L
    }

    /** What a stream's first bytes say it is. An address alone cannot tell: many carry no extension. */
    fun sniff(code: Int, contentType: String?, head: String, total: Long): Source {
        if (code !in 200..299) return Source.Refused(NO_REACH)
        val type = contentType.orEmpty().lowercase(Locale.US)
        val text = head.removePrefix("﻿").trimStart()
        val low = text.take(256).lowercase(Locale.US)
        return when {
            text.startsWith("#EXTM3U") || "mpegurl" in type -> Source.Hls
            "<mpd" in low || "dash+xml" in type -> Source.Refused(NO_KIND)
            "text/html" in type || low.startsWith("<!doctype") || low.startsWith("<html") -> Source.Refused(NO_PAGE)
            total <= 0L -> Source.Refused(NO_LIVE)                 // no length: it has no end we can see
            total < MIN_FILE_BYTES -> Source.Refused(NOT_READY)
            else -> Source.File(total)
        }
    }

    /** The playlist's verdict once it is read: null = it can be saved. */
    fun hlsRefusal(ended: Boolean, encrypted: Boolean): String? = when {
        encrypted -> NO_PROTECTED
        !ended -> NO_LIVE
        else -> null
    }

    fun hasRoom(size: Long, free: Long): Boolean = free - size.coerceAtLeast(0L) >= MARGIN_BYTES

    /** Everything a saved title needs with no network, stored with the download itself. */
    data class Meta(
        val viewer: String,
        val type: String,
        val id: String,
        val name: String,                     // "Show · S1E2 · Name" for an episode, the title for a film
        val poster: String? = null,
        val shape: String = "poster",
        val art: String? = null,              // the poster's file on this phone
        val addonUrl: String = "",
        val streamName: String = "",
        val streamTitle: String = "",
        val fileName: String = "",
        val source: String? = null,           // "1080p · Torrentio", the player's source line
        val description: String? = null,
        val subs: List<SubTrack> = emptyList(),   // file:// addresses
        val headers: Map<String, String> = emptyMap(),
        val at: Long = 0L,
    ) {
        fun toBytes(): ByteArray = JSONObject()
            .put("viewer", viewer).put("type", type).put("id", id).put("name", name)
            .put("poster", poster ?: "").put("shape", shape).put("art", art ?: "")
            .put("addonUrl", addonUrl).put("streamName", streamName).put("streamTitle", streamTitle)
            .put("fileName", fileName).put("source", source ?: "").put("description", description ?: "")
            .put("subs", JSONArray().apply { subs.forEach { put(JSONObject().put("url", it.url).put("lang", it.lang)) } })
            .put("headers", JSONObject().apply { headers.forEach { (k, v) -> put(k, v) } })
            .put("at", at)
            .toString().toByteArray(Charsets.UTF_8)

        companion object {
            /** Null for bytes that are not ours: a row that cannot be read is left out, it does not crash the page. */
            fun read(bytes: ByteArray?): Meta? {
                if (bytes == null || bytes.isEmpty()) return null
                return runCatching {
                    val o = JSONObject(String(bytes, Charsets.UTF_8))
                    val subs = o.optJSONArray("subs")
                    val heads = o.optJSONObject("headers")
                    Meta(
                        viewer = o.getString("viewer"), type = o.getString("type"), id = o.getString("id"),
                        name = o.optString("name"), poster = o.optString("poster").ifEmpty { null },
                        shape = o.optString("shape", "poster"), art = o.optString("art").ifEmpty { null },
                        addonUrl = o.optString("addonUrl"), streamName = o.optString("streamName"),
                        streamTitle = o.optString("streamTitle"), fileName = o.optString("fileName"),
                        source = o.optString("source").ifEmpty { null },
                        description = o.optString("description").ifEmpty { null },
                        subs = List(subs?.length() ?: 0) { i -> subs!!.getJSONObject(i).let { SubTrack(it.getString("url"), it.getString("lang")) } },
                        headers = heads?.keys()?.asSequence()?.associateWith { heads.getString(it) }.orEmpty(),
                        at = o.optLong("at"),
                    )
                }.getOrNull()
            }
        }
    }

    enum class Phase { QUEUED, WAITING, RUNNING, PAUSED, DONE, FAILED }

    /** The fresh row for a failed download: the same address if it is still offered, else the same file or the same row. */
    fun sameRow(rows: List<StreamItem>, url: String, m: Meta): StreamItem? =
        rows.firstOrNull { it.url == url }
            ?: rows.firstOrNull { m.fileName.isNotEmpty() && it.fileName == m.fileName }
            ?: rows.firstOrNull { it.name == m.streamName && it.title == m.streamTitle }

    /** One file per wanted language, kept beside the download: the stream's own offers first, as the player ranks them. */
    fun pickSubs(offers: List<SubTrack>, langs: List<String>, keyOf: (String?) -> String): List<SubTrack> =
        langs.map(keyOf).filter { it.isNotEmpty() }.distinct().take(LANGS_MAX)
            .mapNotNull { k -> offers.firstOrNull { keyOf(it.lang) == k } }

    /** A kept subtitle's name: the player reads a .srt as SubRip and anything else as WebVTT. */
    fun subFile(i: Int, text: String): String =
        "sub-$i." + (if (text.trimStart().startsWith("WEBVTT")) "vtt" else "srt")

    fun sizeText(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / 1073741824.0)
        b >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", b / 1048576.0)
        else -> String.format(Locale.US, "%d KB", b / 1024)
    }

    /** A row's status line. [total] is ≤ 0 for a playlist, whose size is known only once it is all here. */
    fun line(phase: Phase, percent: Float, bytes: Long, total: Long, wifiOnly: Boolean): String {
        val pct = if (percent >= 0f) String.format(Locale.US, "%.0f%%", percent) else null
        return when (phase) {
            Phase.DONE -> sizeText(bytes)
            Phase.FAILED -> "Couldn’t finish · Tap to retry"
            Phase.QUEUED -> "Waiting to start"
            Phase.WAITING -> if (wifiOnly) "Waiting for Wi-Fi" else "Waiting for a connection"
            Phase.PAUSED -> listOfNotNull("Paused", pct).joinToString(" · ")
            Phase.RUNNING -> listOfNotNull(pct, sizeText(bytes) + (if (total > 0L) " of " + sizeText(total) else ""))
                .joinToString(" · ")
        }
    }

    private val TAG = Regex("""^S(\d+)(?:E(\d+))?$""", RegexOption.IGNORE_CASE)

    /** A saved episode's name, split: the show, then what is left ("S1E2 · Name"). A film has no [rest]. */
    fun nameParts(name: String): Pair<String, String?> {
        val parts = name.split(" · ")
        return if (parts.size > 1 && TAG.matches(parts[1].trim())) parts[0] to parts.drop(1).joinToString(" · ") else name to null
    }

    /** Season and episode from a saved name, for the order inside a show and for the offline next episode. */
    fun numbers(name: String): Pair<Int, Int?>? {
        val tag = nameParts(name).second?.substringBefore(" · ")?.trim() ?: return null
        val m = TAG.matchEntire(tag) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toIntOrNull()
    }

    /** One shelf per film or show, newest first; a show's episodes in playing order (specials last). */
    fun <T> shelves(rows: List<T>, meta: (T) -> Meta): List<Pair<String, List<T>>> {
        val groups = LinkedHashMap<String, MutableList<T>>()
        rows.sortedByDescending { meta(it).at }.forEach { r ->
            val m = meta(r)
            val (show, rest) = nameParts(m.name)
            groups.getOrPut(if (rest != null) "show/$show" else "one/${m.type}/${m.id}") { mutableListOf() } += r
        }
        return groups.map { (k, list) ->
            k to list.sortedWith(compareBy(
                { numbers(meta(it).name)?.first == 0 },
                { numbers(meta(it).name)?.first ?: 0 },
                { numbers(meta(it).name)?.second ?: 0 },
            ))
        }
    }
}
