package com.nuvio.ckplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Convert sound for your TV (1.93.0). An LG TV drops a DTS film's sound without a word; a Nebula desktop sharing with the
 * TV converts it, and since 1.93.0 so does this phone — but a phone cannot carry a 4K film's picture for a TV, so it never
 * touches the film. The TV reads the film from its host itself (the picture goes straight into its player) and sends the
 * DTS frames it reads here, about 1.5 Mbps; FFmpeg (libnebulaff.so, built by scripts/build-ffmpeg.sh: DTS in, Dolby
 * Digital Plus out, nothing else — no network) converts them, and the Dolby Digital Plus (0.64 Mbps) goes straight back.
 * This phone never learns what is playing or where from: it sees sound, and only from a device holding the token the
 * profile publishes (`sound_v1`, the computer's `relay_v1` shape, which the TV already knows how to find).
 *
 *   GET  /sound/healthz?k=                → {ok, name, sound:{codec:"ec-3", audioOnly:true, v:2}}
 *   POST /sound/open?f=dts&ch=&k=         → {id}: one conversion, for one run of playback (a seek starts another)
 *   POST /sound/feed?id=&k=[&end=1]       body: the next frames (end: the film is over) → 204
 *   GET  /sound/out?id=&k=                → the converted sound, fragmented MP4 from 0, for as long as the conversion runs
 *   POST /sound/close?id=&k=              → 204
 *
 * The rig ~/.claude/nebula-player/rigs/phone-mock.mjs is the same contract in Node (the TV side is tested against it).
 */
object SoundShare {
    private const val PREFS = "nebula_sound_share"
    private const val PORT = 47331                 // the same on every phone; another one is taken when it is busy
    private const val MAX_RUNS = 2                 // a TV, plus the one a seek is replacing
    private const val MAX_CONNS = 8
    private const val IDLE_MS = 60_000L
    private const val MAX_FEED = 8 * 1024 * 1024   // one feed: well over a second of the heaviest DTS

    /** On here, as the Settings row shows it; the service follows it. */
    var on by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var port = 0
    private val conns = AtomicInteger(0)
    private val runs = ConcurrentHashMap<String, Run>()
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null
    @Volatile private var app: Context? = null

    private class Run(val id: String, val proc: Process) {
        @Volatile var touched = System.currentTimeMillis()
        @Volatile var ended = false
        val err = StringBuilder()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The FFmpeg this app carries, or null on a processor it was not built for (an x86 emulator). */
    fun ffmpeg(ctx: Context): File? = File(ctx.applicationInfo.nativeLibraryDir, "libnebulaff.so").takeIf { it.isFile }

    fun available(ctx: Context) = ffmpeg(ctx) != null && !Account.isTv(ctx)

    /** At launch: back on when it was left on (the service cannot start itself from the background on Android 14). */
    fun restore(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean("on", false) && available(ctx)) set(ctx, true) else on = false
    }

    fun set(ctx: Context, want: Boolean) {
        val c = ctx.applicationContext
        prefs(c).edit().putBoolean("on", want).apply()
        on = want
        val i = Intent(c, SoundShareService::class.java)
        if (want) {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        } else {
            c.stopService(i)
            stop(c)
        }
    }

    // ---------- the server (the service calls start/stop) ----------

    internal fun start(ctx: Context) {
        val c = ctx.applicationContext
        app = c
        if (socket?.isClosed == false) { publish(c); return }
        val s = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT), 16) }
        } catch (e: IOException) {
            ServerSocket(0, 16)
        }
        socket = s
        port = s.localPort
        Thread({ accept(s) }, "nebula-sound-http").apply { isDaemon = true }.start()
        Thread({ watch(s) }, "nebula-sound-idle").apply { isDaemon = true }.start()
        runCatching {
            val wm = c.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nebula:sound").apply { setReferenceCounted(false); acquire() }
        }
        // the address the TV asks moves with the network: published again whenever it changes
        runCatching {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) { publish(c) }
                override fun onLost(network: Network) { publish(c) }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCb = cb
        }
        publish(c)
    }

    internal fun stop(ctx: Context) {
        val c = ctx.applicationContext
        socket?.let { runCatching { it.close() } }
        socket = null
        for (id in runs.keys.toList()) end(id)
        runCatching { wifi?.release() }; wifi = null
        runCatching { if (wake?.isHeld == true) wake?.release() }; wake = null
        netCb?.let { cb -> runCatching { (c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb) } }
        netCb = null
        status = ""
        // the TV stops asking this phone
        if (Cloud.linked(c)) scope.launch { runCatching { Cloud.api(c, "PUT", "/v1/kv/sound_v1", JSONObject().put("v", doc(c, off = true).toString())) } }
    }

    private fun token(ctx: Context): String {
        val p = prefs(ctx)
        p.getString("token", null)?.takeIf { it.length == 32 }?.let { return it }
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val t = b.joinToString("") { "%02x".format(it) }
        p.edit().putString("token", t).apply()
        return t
    }

    private fun name(ctx: Context): String =
        runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.MODEL.orEmpty().ifBlank { "Your phone" }

    /** This phone's private LAN addresses (IPv4; a TV only ever asks one of those, relayLanHost). */
    internal fun lanHosts(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }.filter { lanHost(it) }.distinct().take(4)
    }.getOrDefault(emptyList())

    private fun doc(ctx: Context, off: Boolean = false): JSONObject = JSONObject()
        .put("v", 1).put("hosts", JSONArray(lanHosts())).put("port", port).put("token", token(ctx))
        .put("name", name(ctx)).put("plat", "android").put("at", System.currentTimeMillis()).apply { if (off) put("off", true) }

    private fun publish(ctx: Context) {
        if (socket == null) return
        val hosts = lanHosts()
        if (!Cloud.linked(ctx)) { status = "Sign in here and on the TV — that is how the TV finds this phone"; return }
        if (hosts.isEmpty()) { status = "Join the same Wi-Fi as your TV"; return }
        scope.launch {
            status = try {
                Cloud.api(ctx, "PUT", "/v1/kv/sound_v1", JSONObject().put("v", doc(ctx).toString()))
                "On as ${name(ctx)} · ${hosts.joinToString(" · ")} port $port · keep the phone on the same Wi-Fi as the TV"
            } catch (e: Exception) {
                "Could not tell your profile where this phone is (${e.message ?: "no connection"}) · The TV will not find it"
            }
        }
    }

    // ---------- HTTP ----------

    private fun accept(s: ServerSocket) {
        while (!s.isClosed) {
            val c = try { s.accept() } catch (e: IOException) { return }
            if (conns.get() >= MAX_CONNS) { runCatching { c.close() }; continue }
            conns.incrementAndGet()
            Thread({
                try { serve(c) } catch (t: Throwable) { /* a TV that went away is not an error */ }
                finally { conns.decrementAndGet(); runCatching { c.close() } }
            }, "nebula-sound-conn").apply { isDaemon = true }.start()
        }
    }

    private fun watch(s: ServerSocket) {
        while (!s.isClosed) {
            try { Thread.sleep(5_000) } catch (e: InterruptedException) { return }
            val now = System.currentTimeMillis()
            runs.values.filter { now - it.touched > IDLE_MS }.forEach { end(it.id) }
        }
    }

    private fun serve(c: Socket) {
        c.tcpNoDelay = true
        c.soTimeout = 30_000
        val input = BufferedInputStream(c.getInputStream(), 64 * 1024)
        val out = c.getOutputStream()
        val head = readHead(input) ?: return
        val line = head.first().split(" ")
        val method = line.getOrNull(0).orEmpty().uppercase(Locale.US)
        val target = line.getOrNull(1).orEmpty()
        val path = target.substringBefore('?')
        val q = query(target.substringAfter('?', ""))
        if (method == "OPTIONS") return reply(out, 204, extra = mapOf("Access-Control-Allow-Methods" to "GET, POST", "Access-Control-Allow-Headers" to "Content-Type"))
        val ctx = app ?: return reply(out, 503)
        if (!same(q["k"].orEmpty(), token(ctx))) return reply(out, 401)
        when {
            path == "/sound/healthz" && method == "GET" ->
                reply(out, 200, JSONObject().put("ok", true).put("name", name(ctx))
                    .put("sound", JSONObject().put("codec", "ec-3").put("audioOnly", true).put("v", 2)).toString(), "application/json")
            path == "/sound/open" && method == "POST" -> open(ctx, q, out)
            path == "/sound/feed" && method == "POST" -> feed(q, head, input, out)
            path == "/sound/out" && method == "GET" -> pipeOut(q, out)
            path == "/sound/close" && method == "POST" -> { q["id"]?.let { end(it) }; reply(out, 204) }
            else -> reply(out, 404)
        }
    }

    private fun open(ctx: Context, q: Map<String, String>, out: OutputStream) {
        if (q["f"] != "dts") return reply(out, 415)
        val ff = ffmpeg(ctx) ?: return reply(out, 501)
        if (runs.size >= MAX_RUNS) return reply(out, 503, extra = mapOf("Retry-After" to "1"))
        val id = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val proc = try { ProcessBuilder(listOf(ff.absolutePath) + soundArgs(q["ch"]?.toIntOrNull() ?: 0)).start() } catch (e: IOException) { return reply(out, 500) }
        val r = Run(id, proc)
        runs[id] = r
        Thread({
            runCatching { proc.errorStream.bufferedReader().forEachLine { l -> synchronized(r.err) { if (r.err.length < 2000) r.err.append(l).append('\n') } } }
        }, "nebula-sound-err").apply { isDaemon = true }.start()
        holdWake()
        reply(out, 200, JSONObject().put("id", id).toString(), "application/json")
    }

    private fun feed(q: Map<String, String>, head: List<String>, input: InputStream, out: OutputStream) {
        val r = runs[q["id"].orEmpty()] ?: return reply(out, 404)
        val len = head.firstOrNull { it.startsWith("content-length:", true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: return reply(out, 411)
        if (len < 0 || len > MAX_FEED) return reply(out, 413)
        r.touched = System.currentTimeMillis()
        val stdin = r.proc.outputStream
        val buf = ByteArray(64 * 1024)
        var left = len
        try {
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size, left))
                if (n < 0) break
                stdin.write(buf, 0, n)
                left -= n
            }
            stdin.flush()
            if (q["end"] == "1") stdin.close()
        } catch (e: IOException) {
            end(r.id); return reply(out, 410)
        }
        reply(out, 204)
    }

    private fun pipeOut(q: Map<String, String>, out: OutputStream) {
        val r = runs[q["id"].orEmpty()] ?: return reply(out, 404)
        head(out, 200, mapOf("Content-Type" to "audio/mp4", "Cache-Control" to "no-store"))
        val buf = ByteArray(32 * 1024)
        try {
            val from = r.proc.inputStream
            while (true) {
                val n = from.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                out.flush()
                r.touched = System.currentTimeMillis()
            }
        } catch (e: IOException) {
            // the TV went away (a seek, the film closed): the conversion goes with it
        } finally {
            end(r.id)
        }
    }

    private fun end(id: String) {
        val r = runs.remove(id) ?: return
        r.ended = true
        runCatching { r.proc.outputStream.close() }
        runCatching { r.proc.destroy() }
        if (runs.isEmpty()) runCatching { if (wake?.isHeld == true) wake?.release() }
    }

    /** The phone stays awake while it converts (the screen may go off mid-film); released when the last conversion ends. */
    private fun holdWake() {
        val c = app ?: return
        runCatching {
            if (wake == null) wake = (c.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nebula:sound").apply { setReferenceCounted(false) }
            if (wake?.isHeld != true) wake?.acquire(4 * 60 * 60 * 1000L)
        }
    }

    // ---------- small parts (pure: SoundShareTest) ----------

    /** FFmpeg's command: DTS frames in on its input, Dolby Digital Plus in fragmented MP4 out — 640 kbps 5.1 at most
        (the encoder stops at six channels; 7.1 is folded down), 256 kbps for stereo. The same as the desktop's for a TV. */
    internal fun soundArgs(ch: Int): List<String> {
        val enc = listOf("-c:a", "eac3", "-b:a", if (ch >= 5 || ch <= 0) "640k" else "256k") + (if (ch > 6 || ch <= 0) listOf("-ac", "6") else emptyList())
        return listOf("-hide_banner", "-loglevel", "error", "-nostdin", "-f", "dts", "-i", "pipe:0", "-map", "0:a:0") + enc +
            listOf("-f", "mp4", "-movflags", "frag_keyframe+delay_moov+default_base_moof", "-frag_duration", "500000", "pipe:1")
    }

    internal fun query(s: String): Map<String, String> = s.split('&').filter { it.isNotEmpty() }.associate {
        val k = it.substringBefore('='); val v = it.substringAfter('=', "")
        runCatching { URLDecoder.decode(k, "UTF-8") }.getOrDefault(k) to runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
    }

    /** Equal without telling how much of it was right by how long it took. */
    internal fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun readHead(input: InputStream): List<String>? {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        while (lines.size < 40) {
            val b = input.read()
            if (b < 0) return lines.takeIf { it.isNotEmpty() }
            if (b == '\n'.code) {
                val l = sb.toString().trimEnd('\r'); sb.setLength(0)
                if (l.isEmpty()) return lines.takeIf { it.isNotEmpty() }
                lines.add(l)
            } else if (sb.length < 8192) sb.append(b.toChar())
        }
        return lines
    }

    private fun head(out: OutputStream, code: Int, extra: Map<String, String> = emptyMap()) {
        val reason = when (code) { 200 -> "OK"; 204 -> "No Content"; 401 -> "Unauthorized"; 404 -> "Not Found"; 410 -> "Gone"; 411 -> "Length Required"
            413 -> "Payload Too Large"; 415 -> "Unsupported Media Type"; 501 -> "Not Implemented"; 503 -> "Service Unavailable"; else -> "Error" }
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\n")
        // the TV's page is a packaged app (no origin of its own): every answer may be read by it
        sb.append("Access-Control-Allow-Origin: *\r\nConnection: close\r\n")
        extra.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray()); out.flush()
    }

    private fun reply(out: OutputStream, code: Int, body: String = "", type: String = "text/plain", extra: Map<String, String> = emptyMap()) {
        val b = body.toByteArray()
        head(out, code, extra + mapOf("Content-Type" to type, "Content-Length" to b.size.toString()))
        if (b.isNotEmpty()) out.write(b)
        out.flush()
    }
}

/** Keeps the process, and so the converter, alive with the screen off; its notification says so and turns it off. */
class SoundShareService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { SoundShare.set(this, false); stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Convert sound for your TV", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, SoundShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE) }
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(R.drawable.ic_stat_sound)
            .setContentTitle("Converting sound for your TV")
            .setContentText("Your TV can play DTS films with sound while this is on")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        SoundShare.start(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() { SoundShare.stop(this); super.onDestroy() }

    companion object {
        private const val CHANNEL = "sound_share"
        private const val ID = 4731
        const val ACTION_STOP = "com.nuvio.ckplayer.SOUND_SHARE_STOP"
    }
}

/** Settings › Playback on a phone: the switch and where it stands. */
@Composable
internal fun SoundShareRow() {
    val ctx = LocalContext.current
    if (!SoundShare.available(ctx)) return
    // Android 13+: the notification that keeps it running (and turns it off) needs leave to show; it runs either way
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { SoundShare.set(ctx, true) }
    SettingsChips(
        "Convert sound for your TV",
        SoundShare.status.ifEmpty { "An LG TV plays DTS films silently · With this on, the TV sends this phone the film's sound and it sends back sound the TV plays · The picture never passes through the phone · Both signed in to the same profile, on the same Wi-Fi" },
        listOf("on" to "On", "off" to "Off"), if (SoundShare.on) "on" else "off", divider = false,
    ) {
        if (it == "on" && Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else SoundShare.set(ctx, it == "on")
    }
}
