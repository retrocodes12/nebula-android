package com.nuvio.ckplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks for a newer Nebula build, and downloads/installs it in-app.
 *
 * The check asks our own cloud first (`/cloud/v1/releases`, one shared copy of the GitHub data,
 * refreshed server-side every few minutes) and only falls back to GitHub's API directly. GitHub
 * allows 60 anonymous calls an hour per public IP, so a household that also opens the landing page
 * used to hit 403 here and read "Could not reach the release feed".
 */
object Updates {
    private const val RELEASES_API = "https://play.rifflehq.in/cloud/v1/releases"
    private const val LATEST_API = "https://api.github.com/repos/retrocodes12/nebula-android/releases/latest"
    const val APK_URL = "https://github.com/retrocodes12/nebula-android/releases/latest/download/Nebula.apk"
    /** Only an asset hosted under our own release page may be installed. */
    private const val ASSET_PREFIX = "https://github.com/retrocodes12/nebula-android/releases/download/"
    private const val TIMEOUT_MS = 10_000

    data class Release(val version: String, val notes: String, val apkUrl: String)

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            fromCloud() ?: fromGitHub()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** The cloud's `{ android: { version, tag, assets: [{name, url, size}] } }`; null on any miss. */
    private fun fromCloud(): Release? {
        return try {
            val a = JSONObject(getText(RELEASES_API)).optJSONObject("android") ?: return null
            val version = cleanVersion(a.optString("version").ifEmpty { a.optString("tag") })
            if (version.isEmpty()) return null
            var apk = APK_URL
            val assets = a.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val o = assets.optJSONObject(i) ?: continue
                val u = o.optString("url")
                if (o.optString("name") == "Nebula.apk" && u.startsWith(ASSET_PREFIX)) { apk = u; break }
            }
            Release(version, "", apk)
        } catch (e: Exception) {
            null
        }
    }

    /** GitHub's own `releases/latest` — the fallback when the cloud is unreachable. */
    private fun fromGitHub(): Release? {
        return try {
            val j = JSONObject(getText(LATEST_API))
            val version = cleanVersion(j.optString("tag_name"))
            if (version.isEmpty()) return null
            // First non-empty line of the release notes, trimmed to a card-friendly length.
            val notes = j.optString("body")
                .lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?.take(140).orEmpty()
            Release(version, notes, APK_URL)
        } catch (e: Exception) {
            null
        }
    }

    /** "v1.55.0" → "1.55.0"; anything that is not dotted digits is rejected as empty. */
    private fun cleanVersion(raw: String): String {
        val v = raw.trim().removePrefix("v").removePrefix("V").trim()
        return if (Regex("^\\d+(\\.\\d+){1,3}$").matches(v)) v else ""
    }

    /** One short GET with the app's identity and a 10 s ceiling on both connect and read. */
    private fun getText(u: String): String {
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/json, */*")
        conn.setRequestProperty("User-Agent", "NebulaPlayer")
        conn.setRequestProperty("X-Nebula-Client", "android")
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            return body
        } finally {
            conn.disconnect()
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

    private fun apkFile(context: Context, version: String) = File(context.cacheDir, "nebula-update-$version.apk")

    /** A previously-completed download for this version, if one is cached. */
    fun cachedApk(context: Context, version: String): File? =
        apkFile(context, version).takeIf { it.exists() && it.length() > 0L }

    /**
     * Download this version's APK into cacheDir, reporting 0..100 progress.
     * Writes to a .part file and promotes it only on success (so a cached file is
     * always complete), and clears downloads for other versions. Returns the file or null.
     * [url] is the release's own asset when the check learned it, else the evergreen link.
     */
    suspend fun downloadApk(context: Context, version: String, url: String = APK_URL, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val out = apkFile(context, version)
            val tmp = File(context.cacheDir, "nebula-update-$version.part")
            val src = if (url.startsWith(ASSET_PREFIX)) url else APK_URL
            val conn = (URL(src).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", "NebulaPlayer")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) { conn.disconnect(); return@withContext null }
            val total = conn.contentLength.toLong()
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        if (total > 0) onProgress(((readTotal * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            conn.disconnect()
            if (tmp.length() <= 0L) { tmp.delete(); return@withContext null }
            context.cacheDir.listFiles()?.forEach {
                if (it.name.startsWith("nebula-update-") && it != tmp) it.delete()
            }
            if (!tmp.renameTo(out)) return@withContext null
            out
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Hand the downloaded APK to the system installer (installs over the current app).
     * Returns false when the app still needs the "install unknown apps" permission —
     * in that case the relevant Settings screen is opened so the user can grant it, then retry.
     */
    fun installApk(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
