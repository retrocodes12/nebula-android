package com.nuvio.ckplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases for a newer Nebula build, and downloads/installs it in-app. */
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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

    private fun apkFile(context: Context, version: String) = File(context.cacheDir, "nebula-update-$version.apk")

    /** A previously-completed download for this version, if one is cached. */
    fun cachedApk(context: Context, version: String): File? =
        apkFile(context, version).takeIf { it.exists() && it.length() > 0L }

    /**
     * Download this version's APK into cacheDir, reporting 0..100 progress.
     * Writes to a .part file and promotes it only on success (so a cached file is
     * always complete), and clears downloads for other versions. Returns the file or null.
     */
    suspend fun downloadApk(context: Context, version: String, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val out = apkFile(context, version)
            val tmp = File(context.cacheDir, "nebula-update-$version.part")
            val conn = (URL(APK_URL).openConnection() as HttpURLConnection).apply {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
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
