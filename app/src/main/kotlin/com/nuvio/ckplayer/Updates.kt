package com.nuvio.ckplayer

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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

    /** [notes] = the release's whole text (the card shows its first line, the ⓘ sheet all of it); [size] = the APK's
        bytes, 0 when the feed did not say. */
    data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long = 0L)

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
            var size = 0L
            val assets = a.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val o = assets.optJSONObject(i) ?: continue
                val u = o.optString("url")
                if (o.optString("name") == "Nebula.apk" && u.startsWith(ASSET_PREFIX)) { apk = u; size = o.optLong("size"); break }
            }
            // the feed carries the release's text since 09-22 (it used to carry none, so the card only ever said
            // "A new version is available"); an older cloud without it gives an empty string
            Release(version, cleanNotes(a.optString("notes")), apk, size)
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
            var size = 0L
            val assets = j.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val o = assets.optJSONObject(i) ?: continue
                if (o.optString("name") == "Nebula.apk") { size = o.optLong("size"); break }
            }
            Release(version, cleanNotes(j.optString("body").take(4000)), APK_URL, size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A release's text made readable as plain text: GitHub keeps Markdown, and a link printed raw read as
     * "[i18n(nb): …](https://github.com/…)" in the sheet (Nuvio shows exactly that). Links keep their words, emphasis
     * and heading marks go, list dashes become bullets, and runs of blank lines fold to one.
     */
    fun cleanNotes(raw: String): String = raw.replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")                 // images
        .replace(Regex("""\[([^\]]+)]\((?:[^)]*)\)"""), "$1")          // [words](url) → words
        .replace(Regex("""(\*\*|__|`)"""), "")
        .lines().joinToString("\n") { line ->
            val t = line.trimEnd()
            when {
                Regex("""^\s*#{1,6}\s+""").containsMatchIn(t) -> t.replace(Regex("""^\s*#{1,6}\s+"""), "")
                Regex("""^\s*[-*]\s+""").containsMatchIn(t) -> t.replace(Regex("""^\s*[-*]\s+"""), "• ")
                else -> t
            }
        }
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

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

    /**
     * The one APK download of this process. It used to live in the Home card's own scope: leaving Home could not
     * stop the blocking copy, and coming back started a second writer into the same file ("Download failed", and a
     * cached APK that could be two downloads interleaved). Now whichever card asks first starts it here, in the
     * app's scope, and every card after that — the same visit or a later one — only watches it.
     */
    class Download(val version: String) {
        var progress by mutableStateOf(0)
        var phase by mutableStateOf("downloading")      // downloading · ready · failed
        var file by mutableStateOf<File?>(null)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    /** The download in flight or finished this session, if any (main thread only). */
    var download by mutableStateOf<Download?>(null)
        private set

    /** Start [version]'s download unless it is already running or done; returns the one to watch. */
    fun startDownload(context: Context, version: String, url: String = APK_URL): Download {
        download?.let { d -> if (d.version == version && d.phase != "failed") return d }
        job?.cancel()
        val app = context.applicationContext
        val d = Download(version)
        download = d
        job = appScope.launch {
            val f = downloadApk(app, version, url) { p -> if (p != d.progress) d.progress = p }
            if (f != null) { d.file = f; d.phase = "ready" } else d.phase = "failed"
        }
        return d
    }

    /** Metered data (a phone on mobile data, a hotspot): the card waits for a tap instead of spending it unasked. */
    fun metered(context: Context): Boolean =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered == true

    /** A previously-completed download for this version, if one is cached. */
    fun cachedApk(context: Context, version: String): File? =
        apkFile(context, version).takeIf { it.exists() && it.length() > 0L }

    /**
     * Download this version's APK into cacheDir, reporting 0..100 progress (from the IO thread).
     * Writes to a unique .part file and promotes it only on success (so a cached file is
     * always complete), and clears downloads for other versions. Returns the file or null.
     * Only [startDownload] calls it, so one runs per process.
     * [url] is the release's own asset when the check learned it, else the evergreen link.
     */
    private suspend fun downloadApk(context: Context, version: String, url: String = APK_URL, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        // a name of its own per writer, promoted by an atomic rename: two writers can never share one file
        var tmp: File? = null
        try {
            val out = apkFile(context, version)
            val part = File.createTempFile("nebula-update-$version-", ".part", context.cacheDir)
            tmp = part
            val src = if (url.startsWith(ASSET_PREFIX)) url else APK_URL
            val conn = (URL(src).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", "NebulaPlayer")
            }
            try {
                conn.connect()
                if (conn.responseCode !in 200..299) return@withContext null
                val total = conn.contentLength.toLong()
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            ensureActive()          // the copy blocks: cancellation is only seen here
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            readTotal += n
                            if (total > 0) onProgress(((readTotal * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (part.length() <= 0L) return@withContext null
            // clear older versions and leftovers of a killed run (only one writer runs per process)
            context.cacheDir.listFiles()?.forEach {
                if (it.name.startsWith("nebula-update-") && it != part) it.delete()
            }
            if (!part.renameTo(out)) return@withContext null
            tmp = null
            out
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            tmp?.delete()
        }
    }

    /**
     * Hand the downloaded APK to the system installer (installs over the current app). Returns null when it was
     * handed over, or the sentence to show when it could not be: the "install unknown apps" permission is missing
     * (its Settings screen is opened when this device has one — some TVs hide it), or no installer answered.
     */
    fun installApk(context: Context, apk: File): String? {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            return if (opened) "Allow installs from Nebula, then tap Install"
            else "Allow Nebula to install apps in this device's Settings (Security or Apps › Special app access), then tap Install"
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (runCatching { context.startActivity(intent) }.isSuccess) null
        else "No installer opened — get the update with Downloader (code 7664693) instead"
    }
}
