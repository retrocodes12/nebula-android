@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Play through your PC — the TV only (Android TV boxes are as short of memory as any television).
 *
 * The TV's buffer ends where its memory does; a computer on the same network has plenty. Nebula for the
 * desktop runs a read-ahead cache (its relay.js) and publishes its address + a token to the profile's
 * cloud store (`relay_v1`). Before a play, the TV reads that, asks every published address at once
 * whether it is sharing, and while one answers every request of the stream goes through it: a
 * [ResolvingDataSource] rewrites the addresses and reports the ORIGINAL one back to the engine, so a
 * manifest's relative addresses still resolve. Keys never leave the TV (the licence call has its own
 * reader). A relay that stops answering mid-play is dropped for two minutes and the item is prepared
 * again, direct, from where it was. Phones never do this — the setting is not even shown to them.
 */
internal object Relay {
    class Live(val base: String, val token: String, val name: String, val at: Long)

    /** The one that answered lately (five minutes), or null. */
    @Volatile var live: Live? = null; private set
    /** The relay the current play goes through, or null = direct. PlayerScreen sets and clears it. */
    @Volatile var via: Live? = null
    /** The Settings row's second line, refreshed after every probe. */
    var status by mutableStateOf(""); private set
    @Volatile private var deadUntil = 0L
    @Volatile private var doc: JSONObject? = null
    @Volatile private var pulledAt = 0L

    private val probe = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .callTimeout(1500, TimeUnit.MILLISECONDS)      // connect + read together: a blocking call ignores withTimeout
        .build()

    fun wanted(ctx: Context): Boolean = Account.isTv(ctx) && Prefs.relay != "off"

    private fun usable(d: JSONObject?): Boolean =
        d != null && !d.optBoolean("off") && d.optString("token").isNotEmpty() && d.optInt("port") > 0 &&
            (d.optJSONArray("hosts")?.length() ?: 0) > 0

    /** The cloud's word on the sharing computer, at most once a minute; a signed-out TV keeps the last copy it saw. */
    private suspend fun pull(ctx: Context): JSONObject? {
        if (!Cloud.linked(ctx)) return doc
        if (System.currentTimeMillis() - pulledAt < 60_000) return doc
        pulledAt = System.currentTimeMillis()
        doc = try {
            // capped at 2 s: resolve() waits at most ~2.6 s, but withTimeoutOrNull cannot interrupt the blocking cloud call,
            // whose own limits (12 s connect, 20 s read) once held a play up for half a minute
            JSONObject(Cloud.api(ctx, "GET", "/v1/kv/relay_v1", null, timeoutMs = 2_000).getString("v"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Cloud.HttpFail) {
            if (e.code == 404) null else doc
        } catch (e: Exception) {
            doc
        }
        return doc
    }

    /** Every published address asked at once (a PC lists its virtual adapters too); the first that says ok wins. */
    private suspend fun probeAll(d: JSONObject): Live? = coroutineScope {
        val hosts = d.getJSONArray("hosts")
        val port = d.optInt("port")
        val token = d.optString("token")
        val name = d.optString("name").ifEmpty { "Your PC" }
        (0 until minOf(hosts.length(), 6)).map { i ->
            async(Dispatchers.IO) {
                val base = "http://${hosts.optString(i)}:$port"
                val ok = runCatching {
                    probe.newCall(Request.Builder().url("$base/relay/healthz?k=$token").build()).execute().use { r ->
                        r.code == 200 && JSONObject(r.body?.string() ?: "{}").optBoolean("ok")
                    }
                }.getOrDefault(false)
                if (ok) Live(base, token, name, System.currentTimeMillis()) else null
            }
        }.awaitAll().firstOrNull { it != null }
    }

    /** One lookup at a time: the streams page starts one while the add-ons answer, and the play that follows waits for
        it and reads its answer — a second lookup racing it would read the cloud copy the first had not stored yet. */
    private val lookup = Mutex()

    /** The relay for the next play: the one that answered lately, else the cloud + a probe — never more than ~2.6 s of waiting. */
    suspend fun resolve(ctx: Context): Live? = lookup.withLock {
        if (!wanted(ctx)) { refreshStatus(ctx); return@withLock null }
        val l = live
        if (l != null && System.currentTimeMillis() - l.at < 300_000) return@withLock l
        if (System.currentTimeMillis() < deadUntil) return@withLock null
        val found = withTimeoutOrNull(2_600) {
            val d = pull(ctx)
            if (usable(d)) probeAll(d!!) else null
        }
        live = found
        if (found == null) deadUntil = System.currentTimeMillis() + 120_000
        refreshStatus(ctx)
        found
    }

    /** Settings opened, or the chips changed: ask again now. */
    suspend fun refresh(ctx: Context) {
        live = null; deadUntil = 0L
        refreshStatus(ctx)
        resolve(ctx)
    }

    /** The relay stopped answering: forget it for two minutes, and stop the Settings row claiming it is sharing. */
    fun drop(ctx: Context) {
        live = null; via = null
        deadUntil = System.currentTimeMillis() + 120_000
        refreshStatus(ctx)
    }

    fun url(u: String, l: Live): String = l.base + "/relay?u=" + Uri.encode(u) + "&k=" + l.token

    /** The original address behind a relay address. */
    fun orig(u: String): String {
        val m = Regex("[?&]u=([^&]+)").find(u) ?: return u
        return runCatching { Uri.decode(m.groupValues[1]) }.getOrDefault(u)
    }

    private fun refreshStatus(ctx: Context) {
        val l = live
        val d = doc
        status = when {
            !Account.isTv(ctx) -> ""
            Prefs.relay == "off" -> "Off — the TV fetches video itself"
            l != null -> "${l.name} is sharing · ${l.base.removePrefix("http://")}"
            usable(d) -> "${d!!.optString("name").ifEmpty { "Your PC" }} was sharing but does not answer right now · Check it is on, and on the same network"
            !Cloud.linked(ctx) -> "Sign in here and on the computer — that is how the TV finds it"
            else -> "No computer is sharing right now · Turn it on in Nebula for Windows or Linux › Settings › Playback"
        }
    }

    /**
     * The engine's addresses go through the relay while a play uses one (P2P's own loopback server and the
     * relay's own addresses excepted); the address reported back is the original, so manifests resolve.
     */
    fun resolver(): ResolvingDataSource.Resolver = object : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val l = via ?: return dataSpec
            val u = dataSpec.uri.toString()
            if (!u.startsWith("http://") && !u.startsWith("https://")) return dataSpec
            if (P2p.isLocal(u) || u.startsWith(l.base)) return dataSpec
            return dataSpec.buildUpon().setUri(Uri.parse(url(u, l))).build()
        }

        override fun resolveReportedUri(uri: Uri): Uri {
            val s = uri.toString()
            return if (s.contains("/relay?u=")) Uri.parse(orig(s)) else uri
        }
    }
}
