package com.nuvio.ckplayer

import org.json.JSONObject
import java.net.URLDecoder

/**
 * An add-on's own settings page, by the add-on protocol's rules: a manifest whose `behaviorHints` say `configurable` (or
 * `configurationRequired`) serves a page at `/configure` beside it, and that page hands back a NEW manifest address with
 * the settings inside it. Nothing here touches Android, so the rules are unit-tested.
 */
object AddonConfig {
    private val MANIFEST = Regex("/manifest\\.json(?=$|[?#])", RegexOption.IGNORE_CASE)
    private val STREMIO = Regex("^stremio://", RegexOption.IGNORE_CASE)
    private val WEB_INSTALL = Regex("^https://(web\\.stremio\\.com|app\\.strem\\.io)/.*[?&]addon=([^&]+)", RegexOption.IGNORE_CASE)
    private val HOST = Regex("^https?://([^/?#]+)", RegexOption.IGNORE_CASE)

    fun configurable(manifest: JSONObject): Boolean {
        val hints = manifest.optJSONObject("behaviorHints") ?: return false
        return hints.optBoolean("configurable") || hints.optBoolean("configurationRequired")
    }

    /** The settings page of a manifest address (its path and query kept: the page opens on the current settings). */
    fun pageUrl(manifestUrl: String): String? {
        val u = webUrl(manifestUrl)
        return if (MANIFEST.containsMatchIn(u)) MANIFEST.replaceFirst(u, "/configure") else null
    }

    /**
     * The manifest address a page is handing over, or null for any other navigation. Three shapes: the install link
     * (`stremio://…/manifest.json`, the one every page offers, and no browser on a phone can follow it), the manifest
     * itself, and the protocol's web install link that carries it in `addon=`.
     */
    fun installLink(address: String?): String? {
        val a = address?.trim().orEmpty()
        val web = WEB_INSTALL.find(a)?.groupValues?.get(2)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        val u = STREMIO.replaceFirst(web ?: a, "https://")
        return webUrl(u).takeIf { MANIFEST.containsMatchIn(it) }
    }

    /** The host of an address, for saying where a link goes without showing the keys that may sit in its path. What stands
        before an `@` is dropped: it is the part that can pose as another site. */
    fun host(url: String): String =
        HOST.find(url.trim())?.groupValues?.get(1)?.substringAfterLast('@').orEmpty()

    /** The list with [old] swapped for [fresh]: same rank, same On/Off. An add-on already installed at the new address
        goes (it would be listed twice); [old] gone meanwhile, a sync having removed it, leaves [fresh] at the end. */
    fun replaced(list: List<Addon>, old: String, fresh: Addon): List<Addon> {
        val rest = list.filterNot { it.manifestUrl == fresh.manifestUrl && it.manifestUrl != old }
        if (rest.none { it.manifestUrl == old }) return rest + fresh
        return rest.map { if (it.manifestUrl == old) fresh.copy(enabled = it.enabled) else it }
    }
}
