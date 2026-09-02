package com.nuvio.ckplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Addon(val manifestUrl: String, val name: String, val base: String, val logo: String? = null)
data class CatalogRef(
    val type: String,
    val id: String,
    val name: String,
    val genres: List<String>,
    val search: Boolean = false,
    /** Advertises the `skip` extra — without it, paging would just refetch page 1. */
    val skip: Boolean = false,
    /** Home can ask for it as it is. A catalog that only answers to a list of ids
        (Cinemeta's "Last videos") or to a search term has no page to show. */
    val browsable: Boolean = true,
)

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
    val hasSubs: Boolean = false,
    val subTypes: List<String>? = null,
    val subIdPrefixes: List<String>? = null,
) {
    /** Stremio semantics: stream resource + matching type + matching id prefix (absent = match all). */
    fun canStream(type: String, id: String): Boolean = matches(hasStreams, streamTypes, streamIdPrefixes, type, id)
    fun canMeta(type: String, id: String): Boolean = matches(hasMeta, metaTypes, metaIdPrefixes, type, id)
    fun canSubs(type: String, id: String): Boolean = matches(hasSubs, subTypes, subIdPrefixes, type, id)
    private fun matches(has: Boolean, types: List<String>?, prefixes: List<String>?, type: String, id: String): Boolean {
        if (!has) return false
        if (!types.isNullOrEmpty() && type !in types) return false
        if (!prefixes.isNullOrEmpty()) return prefixes.any { id.startsWith(it) }
        return true
    }
}
data class MetaItem(
    val id: String, val type: String, val name: String, val poster: String?,
    val posterShape: String = "poster",
    // catalogue responses often carry the whole premium presentation — keep it
    val imdbRating: String? = null, val releaseInfo: String? = null,
    val background: String? = null, val logo: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),      // the hero's meta line names the first one
)
data class SubTrack(val url: String, val lang: String)
data class StreamItem(
    val name: String, val title: String, val url: String,
    val subtitles: List<SubTrack> = emptyList(),
    val videoSize: Long = 0L,
    // behaviorHints.bingeGroup: the add-on's own "this is the same release
    // across episodes" — the strongest signal a next episode can follow
    val bingeGroup: String = "",
)
/** One episode of a series (a Stremio meta `videos` entry). */
data class Episode(
    val id: String, val season: Int, val episode: Int?,
    val name: String, val overview: String?, val thumbnail: String?,
    val released: String? = null,
)

/** The full meta document for one title — what the detail page shows. */
data class FullMeta(
    val name: String,
    val description: String?,
    val background: String?,
    val poster: String?,
    val logo: String? = null,
    val runtime: String?,
    val imdbRating: String?,
    val releaseInfo: String?,
    val genres: List<String>,
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val videos: List<Episode>,
)

object Stremio {

    suspend fun httpGetBytes(u: String): ByteArray = withContext(Dispatchers.IO) {
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("User-Agent", "NebulaPlayer")
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
            if (code !in 200..299 || body == null) throw RuntimeException("HTTP $code")
            body
        } finally {
            conn.disconnect()
        }
    }

    /** Subtitle add-on query: /subtitles/{type}/{id}.json -> [{url, lang}] */
    suspend fun loadSubtitles(base: String, type: String, id: String): List<SubTrack> {
        val j = JSONObject(httpGetText("$base/subtitles/${enc(type)}/${enc(id)}.json"))
        val arr = j.optJSONArray("subtitles") ?: return emptyList()
        val out = mutableListOf<SubTrack>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("url")
            if (u.isNotEmpty()) out.add(SubTrack(u, o.optString("lang", o.optString("language", "und"))))
        }
        return out
    }

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
            var supportsSkip = false
            // an extra the add-on insists on is only fine when it lists the values to pick from
            val required = HashSet<String>()
            val withOptions = HashSet<String>()
            val extra = c.optJSONArray("extra")
            if (extra != null) for (k in 0 until extra.length()) {
                val e = extra.getJSONObject(k)
                val name = e.optString("name")
                val opts = e.optJSONArray("options")
                if (opts != null && opts.length() > 0) withOptions.add(name)
                if (e.optBoolean("isRequired", false)) required.add(name)
                when (name) {
                    "genre" -> if (opts != null) for (o in 0 until opts.length()) genres.add(opts.getString(o))
                    "search" -> supportsSearch = true
                    "skip" -> supportsSkip = true
                }
            }
            val extraRequired = c.optJSONArray("extraRequired")
            if (extraRequired != null) for (k in 0 until extraRequired.length()) required.add(extraRequired.getString(k))
            val extraSupported = c.optJSONArray("extraSupported")
            if (extraSupported != null) for (k in 0 until extraSupported.length()) {
                when (extraSupported.getString(k)) {
                    "search" -> supportsSearch = true
                    "skip" -> supportsSkip = true
                }
            }
            val type = c.optString("type")
            val id = c.optString("id")
            val browsable = type.isNotEmpty() && id.isNotEmpty() && required.all { it in withOptions }
            cats.add(CatalogRef(type, id, c.optString("name", id), genres, supportsSearch, supportsSkip, browsable))
        }
        // stream resource: either the plain string "stream" (scoped by top-level
        // types/idPrefixes) or an object with its own types/idPrefixes
        var hasStreams = false
        var sTypes: List<String>? = null
        var sPrefixes: List<String>? = null
        var hasMeta = false
        var mTypes: List<String>? = null
        var mPrefixes: List<String>? = null
        var hasSubs = false
        var subTypes: List<String>? = null
        var subPrefixes: List<String>? = null
        val topTypes = strList(j.optJSONArray("types"))
        val topPrefixes = strList(j.optJSONArray("idPrefixes"))
        val res = j.optJSONArray("resources")
        if (res != null) for (i in 0 until res.length()) {
            when (val r = res.opt(i)) {
                "stream" -> { hasStreams = true; sTypes = topTypes; sPrefixes = topPrefixes }
                "meta" -> { hasMeta = true; mTypes = topTypes; mPrefixes = topPrefixes }
                "subtitles" -> { hasSubs = true; subTypes = topTypes; subPrefixes = topPrefixes }
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
                    "subtitles" -> {
                        hasSubs = true
                        subTypes = strList(r.optJSONArray("types")) ?: topTypes
                        subPrefixes = strList(r.optJSONArray("idPrefixes")) ?: topPrefixes
                    }
                }
            }
        }
        return ManifestInfo(addon, cats, hasStreams, sTypes, sPrefixes, hasMeta, mTypes, mPrefixes, hasSubs, subTypes, subPrefixes)
    }

    /** Full meta for one title, or null when the add-on has nothing. */
    suspend fun loadFullMeta(base: String, type: String, id: String): FullMeta? {
        val u = "$base/meta/${enc(type)}/${enc(id)}.json"
        val meta = JSONObject(httpGetText(u)).optJSONObject("meta") ?: return null
        val genres = mutableListOf<String>()
        (meta.optJSONArray("genres") ?: meta.optJSONArray("genre"))?.let { g ->
            for (i in 0 until g.length()) genres.add(g.optString(i))
        }
        val cast = mutableListOf<String>()
        meta.optJSONArray("cast")?.let { c -> for (i in 0 until c.length()) cast.add(c.optString(i)) }
        val director = mutableListOf<String>()
        (meta.optJSONArray("director"))?.let { c -> for (i in 0 until c.length()) director.add(c.optString(i)) }
            ?: meta.optString("director").takeIf { it.isNotEmpty() }?.let { director.add(it) }
        return FullMeta(
            name = meta.optString("name"),
            description = meta.optString("description").ifEmpty { meta.optString("overview").ifEmpty { null } },
            background = meta.optString("background").ifEmpty { null },
            poster = meta.optString("poster").ifEmpty { null },
            logo = meta.optString("logo").ifEmpty { null },
            runtime = meta.optString("runtime").ifEmpty { null },
            imdbRating = meta.optString("imdbRating").ifEmpty { null },
            releaseInfo = meta.optString("releaseInfo").ifEmpty {
                meta.optString("released").take(4).ifEmpty { null }
            },
            genres = genres.filter { it.isNotEmpty() },
            cast = cast.filter { it.isNotEmpty() }.take(6),
            director = director.filter { it.isNotEmpty() }.take(2),
            videos = parseVideos(meta.optJSONArray("videos")),
        )
    }

    /** Fetch a series' episode list (the meta `videos` array). Empty if none. */
    suspend fun loadSeriesVideos(base: String, type: String, id: String): List<Episode> {
        val u = "$base/meta/${enc(type)}/${enc(id)}.json"
        val meta = JSONObject(httpGetText(u)).optJSONObject("meta") ?: return emptyList()
        return parseVideos(meta.optJSONArray("videos"))
    }

    private fun parseVideos(vids: JSONArray?): List<Episode> {
        if (vids == null) return emptyList()
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
                    released = v.optString("released").ifEmpty { null },
                )
            )
        }
        return out
    }

    suspend fun loadCatalog(
        base: String,
        c: CatalogRef,
        genre: String?,
        query: String? = null,
        skip: Int = 0,
    ): List<MetaItem> {
        var u = "$base/catalog/${enc(c.type)}/${enc(c.id)}"
        // Stremio puts every extra in one path segment, joined with &
        val extras = mutableListOf<String>()
        if (!query.isNullOrEmpty()) extras.add("search=${enc(query)}")
        else if (!genre.isNullOrEmpty()) extras.add("genre=${enc(genre)}")
        if (skip > 0) extras.add("skip=$skip")
        if (extras.isNotEmpty()) u += "/" + extras.joinToString("&")
        u += ".json"
        val j = JSONObject(httpGetText(u))
        val metas = j.optJSONArray("metas") ?: return emptyList()
        val out = mutableListOf<MetaItem>()
        for (i in 0 until metas.length()) {
            val m = metas.getJSONObject(i)
            val poster = m.optString("poster").ifEmpty { null }
            val shape = m.optString("posterShape").ifEmpty { "poster" }
            out.add(MetaItem(
                m.optString("id"), m.optString("type", c.type), m.optString("name", m.optString("id")), poster, shape,
                imdbRating = m.optString("imdbRating").ifEmpty { null },
                releaseInfo = m.optString("releaseInfo").ifEmpty { m.optString("year").ifEmpty { null } },
                background = m.optString("background").ifEmpty { null },
                logo = m.optString("logo").ifEmpty { null },
                description = m.optString("description").ifEmpty { null },
                genres = (m.optJSONArray("genres") ?: m.optJSONArray("genre"))?.let { g ->
                    (0 until g.length()).map { g.optString(it).trim() }.filter { it.isNotEmpty() }.take(6)
                } ?: emptyList(),
            ))
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
            val text = s.optString("title").ifEmpty { s.optString("description") }
            val bh = s.optJSONObject("behaviorHints")
            val vsize = bh?.optLong("videoSize") ?: 0L
            out.add(StreamItem(s.optString("name"), text, url, subs, vsize, bh?.optString("bingeGroup").orEmpty()))
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
