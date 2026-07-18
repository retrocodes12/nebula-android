package com.nuvio.ckplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Checks GitHub Releases for a newer Nebula build than the one installed. */
object Updates {
    private const val LATEST_API = "https://api.github.com/repos/retrocodes12/nebula-android/releases/latest"
    const val APK_URL = "https://github.com/retrocodes12/nebula-android/releases/latest/download/Nebula.apk"

    data class Release(val version: String, val notes: String, val apkUrl: String)

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(Stremio.httpGetText(LATEST_API))
            val version = j.optString("tag_name").removePrefix("v").removePrefix("V").trim()
            if (version.isEmpty()) return@withContext null
            // First non-empty line of the release notes, trimmed to a card-friendly length.
            val notes = j.optString("body")
                .lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?.take(140).orEmpty()
            Release(version, notes, APK_URL)
        } catch (e: Exception) {
            null
        }
    }

    /** Strict "remote is newer than current" over dotted numeric versions (1.5.0 > 1.4.0). */
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}
