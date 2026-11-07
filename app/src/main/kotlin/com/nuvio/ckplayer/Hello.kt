package com.nuvio.ckplayer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone

/**
 * The usage counter (2026-11-07): once a day, how many days ago Nebula was installed on this device, and whether it is
 * a TV or a phone — nothing else. No identifier is sent: thousands of devices share every value. The server counts how
 * many devices of each age showed up each day, which says how many new people come back (play.rifflehq.in/stats).
 * The shared player sends the same request (`hello` in its index.html); the privacy page names it "Usage counter".
 */
object Hello {
    private const val BASE = "https://play.rifflehq.in/hello"
    private const val DAY_MS = 86_400_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Days since 1970 in [tz] (the device's own calendar day). */
    internal fun localDay(ms: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val x = ms + tz.getOffset(ms)
        return x / DAY_MS - (if (x % DAY_MS < 0) 1 else 0)
    }

    /** The query for a device installed at [installedMs] and seen at [nowMs]; an age is never negative. */
    internal fun query(installedMs: Long, nowMs: Long, tv: Boolean, tz: TimeZone = TimeZone.getDefault()): String =
        "s=android&f=" + (if (tv) "tv" else "phone") + "&a=" + maxOf(0L, localDay(nowMs, tz) - localDay(installedMs, tz))

    /** At most once per calendar day; a day whose request failed is tried again on the next start. */
    fun daily(ctx: Context) {
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences("hello", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val today = localDay(now)
        if (sp.getLong("day", Long.MIN_VALUE) == today) return
        val installed = try {
            app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
        } catch (e: Exception) {
            return
        }
        val q = query(installed, now, Account.isTv(app))
        scope.launch {
            try {
                val conn = URL("$BASE?$q").openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "NebulaPlayer")
                try {
                    if (conn.responseCode in 200..299) sp.edit().putLong("day", today).apply()
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                // offline: the next start tries again
            }
        }
    }
}
