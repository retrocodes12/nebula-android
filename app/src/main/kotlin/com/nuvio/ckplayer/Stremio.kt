package com.nuvio.ckplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Addon(val manifestUrl: String, val name: String, val base: String, val logo: String? = null)
data class CatalogRef(val type: String, val id: String, val name: String, val genres: List<String>, val search: Boolean = false)

/** Parsed manifest: the addon, its catalogs, and whether/what it serves as streams. */
data class ManifestInfo(
    val addon: Addon,
    val catalogs: List<CatalogRef>,
    val hasStreams: Boolean,
    val streamTypes: List<String>?,
    val streamIdPrefixes: List<String>?,
    val hasMeta: Boolean = false,
    val metaTypes: List<String>? = null,
    val metaIdPrefixes: List<String>? = null,
) {
    /** Stremio semantics: stream resource + matching type + matching id prefix (absent = match all). */
    fun canStream(type: String, id: String): Boolean = matches(hasStreams, streamTypes, streamIdPrefixes, type, id)
    fun canMeta(type: String, id: String): Boolean = matches(hasMeta, metaTypes, metaIdPrefixes, type, id)
    private fun matches(has: Boolean, types: List<String>?, prefixes: List<String>?, type: String, id: String): Boolean {
        if (!has) return false
        if (!types.isNullOrEmpty() && type !in types) return false
        if (!prefixes.isNullOrEmpty()) return prefixes.any { id.startsWith(it) }
        return true
    }
}
data class MetaItem(val id: String, val type: String, val name: String, val poster: String?, val posterShape: String = "poster")
data class SubTrack(val url: String, val lang: String)
data class StreamItem(val name: String, val title: String, val url: String, val subtitles: List<SubTrack> = emptyList())
/** One episode of a series (a Stremio meta `videos` entry). */
data class Episode(
    val id: String, val season: Int, val episode: Int?,
    val name: String, val overview: String?, val thumbnail: String?,
)

object Stremio {

    suspend fun httpGetText(u: String): String = withContext(Dispatchers.IO) {
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "*/*")
        // A User-Agent is required by the GitHub API (update check) and also lets
        // the addon recognise the Nebula app by UA.
        conn.setRequestProperty("User-Agent", "NebulaPlayer")
        // Identify the Nebula app so the addon serves direct ClearKey DASH cards
        // (and skips the "Open in Nebula Player" launcher meant for other clients).
        conn.setRequestProperty("X-Nebula-Client", "android")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            body
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun baseOf(manifestUrl: String): String = manifestUrl.replace(Regex("/manifest\\.json.*$"), "")

    private fun strList(a: JSONArray?): List<String>? =
        a?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }

    suspend fun loadManifest(url: String): ManifestInfo {
        val j = JSONObject(httpGetText(url))
        val logo = j.optString("logo").ifEmpty { j.optString("icon") }.ifEmpty { null }
        val addon = Addon(url, j.optString("name", "Add-on"), baseOf(url), logo)
        val cats = mutableListOf<CatalogRef>()
        val arr = j.optJSONArray("catalogs") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val genres = mutableListOf<String>()
            var supportsSearch = false
            val extra = c.optJSONArray("extra")
            if (extra != null) for (k in 0 until extra.length()) {
                val e = extra.getJSONObject(k)
                when (e.optString("name")) {
                    "genre" -> {
                        val opts = e.optJSONArray("options")
                        if (opts != null) for (o in 0 until opts.length()) genres.add(opts.getString(o))
                    }
                    "search" -> supportsSearch = true
                }
            }
            val extraSupported = c.optJSONArray("extraSupported")
            if (extraSupported != null) for (k in 0 until extraSupported.length()) {
                if (extraSupported.getString(k) == "search") supportsSearch = true
            }
            cats.add(CatalogRef(c.optString("type"), c.optString("id"), c.optString("name", c.optString("id")), genres, supportsSearch))
        }
        // stream resource: either the plain string "stream" (scoped by top-level
        // types/idPrefixes) or an object with its own types/idPrefixes
        var hasStreams = false
        var sTypes: List<String>? = null
        var sPrefixes: List<String>? = null
        var hasMeta = false
        var mTypes: List<String>? = null
        var mPrefixes: List<String>? = null
        val topTypes = strList(j.optJSONArray("types"))
        val topPrefixes = strList(j.optJSONArray("idPrefixes"))
        val res = j.optJSONArray("resources")
        if (res != null) for (i in 0 until res.length()) {
            when (val r = res.opt(i)) {
                "stream" -> { hasStreams = true; sTypes = topTypes; sPrefixes = topPrefixes }
                "meta" -> { hasMeta = true; mTypes = topTypes; mPrefixes = topPrefixes }
                is JSONObject -> when (r.optString("name")) {
                    "stream" -> {
                        hasStreams = true
                        sTypes = strList(r.optJSONArray("types")) ?: topTypes
                        sPrefixes = strList(r.optJSONArray("idPrefixes")) ?: topPrefixes
                    }
                    "meta" -> {
                        hasMeta = true
                        mTypes = strList(r.optJSONArray("types")) ?: topTypes
                        mPrefixes = strList(r.optJSONArray("idPrefixes")) ?: topPrefixes
                    }
                }
            }
        }
        return ManifestInfo(addon, cats, hasStreams, sTypes, sPrefixes, hasMeta, mTypes, mPrefixes)
    }

    /** Fetch a series' episode list (the meta `videos` array). Empty if none. */
    suspend fun loadSeriesVideos(base: String, type: String, id: String): List<Episode> {
        val u = "$base/meta/${enc(type)}/${enc(id)}.json"
        val meta = JSONObject(httpGetText(u)).optJSONObject("meta") ?: return emptyList()
        val vids = meta.optJSONArray("videos") ?: return emptyList()
        val out = mutableListOf<Episode>()
        for (i in 0 until vids.length()) {
            val v = vids.optJSONObject(i) ?: continue
            val vid = v.optString("id")
            if (vid.isEmpty()) continue
            val ep = when {
                v.has("episode") && !v.isNull("episode") -> v.optInt("episode")
                v.has("number") && !v.isNull("number") -> v.optInt("number")
                else -> null
            }
            out.add(
                Episode(
                    id = vid,
                    season = if (v.has("season") && !v.isNull("season")) v.optInt("season") else 1,
                    episode = ep,
                    name = v.optString("name").ifEmpty { v.optString("title").ifEmpty { "Episode ${ep ?: ""}".trim() } },
                    overview = v.optString("overview").ifEmpty { v.optString("description").ifEmpty { null } },
                    thumbnail = v.optString("thumbnail").ifEmpty { null },
                )
            )
        }
        return out
    }

    suspend fun loadCatalog(base: String, c: CatalogRef, genre: String?, query: String? = null): List<MetaItem> {
        var u = "$base/catalog/${enc(c.type)}/${enc(c.id)}"
        if (!query.isNullOrEmpty()) u += "/search=${enc(query)}"
        else if (!genre.isNullOrEmpty()) u += "/genre=${enc(genre)}"
        u += ".json"
        val j = JSONObject(httpGetText(u))
        val metas = j.optJSONArray("metas") ?: return emptyList()
        val out = mutableListOf<MetaItem>()
        for (i in 0 until metas.length()) {
            val m = metas.getJSONObject(i)
            val poster = m.optString("poster").ifEmpty { null }
            val shape = m.optString("posterShape").ifEmpty { "poster" }
            out.add(MetaItem(m.optString("id"), m.optString("type", c.type), m.optString("name", m.optString("id")), poster, shape))
        }
        return out
    }

    suspend fun loadStreams(base: String, type: String, id: String): List<StreamItem> {
        val u = "$base/stream/${enc(type)}/${enc(id)}.json"
        val j = JSONObject(httpGetText(u))
        val arr = j.optJSONArray("streams") ?: return emptyList()
        val out = mutableListOf<StreamItem>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val url = s.optString("url")
            if (url.isEmpty()) continue
            val subs = mutableListOf<SubTrack>()
            val sarr = s.optJSONArray("subtitles")
            if (sarr != null) for (k in 0 until sarr.length()) {
                val o = sarr.optJSONObject(k) ?: continue
                val su = o.optString("url")
                if (su.isNotEmpty()) subs.add(SubTrack(su, o.optString("lang", "und")))
            }
            out.add(StreamItem(s.optString("name"), s.optString("title"), url, subs))
        }
        return out
    }

    /** Extract a ClearKey license URL (dashif:laurl / clearkey:Laurl) from a DASH manifest. */
    suspend fun resolveClearKeyLicenseUri(mpdUrl: String): String? {
        return try {
            val xml = httpGetText(mpdUrl)
            Regex("<(?:\\w+:)?laurl[^>]*>([^<]+)</(?:\\w+:)?laurl>", RegexOption.IGNORE_CASE)
                .find(xml)?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            null
        }
    }
}
