package com.nuvio.ckplayer

import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.libtorrent4j.LibTorrent
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * P2P streams (Settings › Streams).
 *
 * Some add-ons answer with a torrent rather than a link: no `url`, just an `infoHash` and the index
 * of the file inside it. Nebula used to drop those rows on the floor. With P2P on they are listed
 * like any other stream, and playing one runs a real BitTorrent engine on the phone: libtorrent
 * fetches the torrent's file list from the swarm, downloads the chosen file in order from the front,
 * and [P2pServer] hands those bytes to the player over loopback as an ordinary HTTP file. Nothing
 * leaves the device except the BitTorrent traffic itself, and no server of ours is in the path.
 *
 * The switch is off until someone turns it on, because BitTorrent shows your address to every other
 * peer in the swarm and that should never happen by surprise. One torrent runs at a time, and its
 * data is deleted when the player closes unless "Keep downloads" says otherwise.
 */
object P2p {
    /** What a torrent row carries instead of an address until the engine has resolved one. */
    const val SCHEME = "nebula-p2p"

    private const val META_TIMEOUT_S = 75          // how long the swarm gets to hand over the file list
    private const val HEAD_PIECES = 8              // pieces wanted before the player is allowed to open
    private const val KEEP_CAP = 8L * 1024 * 1024 * 1024   // ceiling for kept downloads
    private const val FREE_MARGIN = 700L * 1024 * 1024     // disk left alone

    /** Trackers every magnet gets: an add-on's `sources` are often a DHT hint and nothing else. */
    private val FALLBACK_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.dler.org:6969/announce",
    )

    /** One file of one torrent, open for reading. Everything [P2pServer] needs to answer a range. */
    internal class Live(
        val hash: String,
        val wanted: Int,              // the file index the add-on asked for; the key a repeat play matches on
        val index: Int,               // the file index actually being served
        val handle: TorrentHandle,
        val name: String,
        val size: Long,
        val offset: Long,             // where the file starts inside the torrent's byte stream
        val pieceLen: Int,
        val firstPiece: Int,
        val lastPiece: Int,
        val file: File,
        val url: String,
        val dir: File,
        val root: String,             // the torrent's own folder (or file) inside [dir] — what deleting removes
    ) {
        /** Counts requests, so a read the player has already moved on from stops waiting. */
        val serial = java.util.concurrent.atomic.AtomicInteger(0)
        /** The piece the reader is on, so a jump can be told from a steady read. */
        @Volatile var head: Int = firstPiece
        /** When the current read started waiting on a piece, 0 when nothing is waiting. */
        @Volatile var waitingSince: Long = 0L
    }

    /** The engine's answer: an address to play, or one sentence saying why there is none. */
    internal class Answer(val url: String? = null, val problem: String? = null)

    @Volatile private var sm: SessionManager? = null
    @Volatile internal var live: Live? = null; private set

    /** The heading of the preparing sheet — plain words, moved along as the swarm answers. */
    var stage by mutableStateOf(""); private set

    // ---------- what a stream row looks like before it is opened ----------

    /** The address a torrent row carries in the list: unique per row, and never fetched by anything. */
    fun rowUrl(infoHash: String, fileIdx: Int): String = "$SCHEME://$infoHash/$fileIdx"

    fun isRow(url: String): Boolean = url.startsWith("$SCHEME://")

    /** True for the loopback address the engine hands the player — [P2pServer]'s own. */
    fun isLocal(url: String): Boolean = url.startsWith("http://127.0.0.1:") && url.contains("/p2p/")

    /** Whether this build can run the engine at all: the native library ships per processor. */
    val available: Boolean
        get() = libOk ?: runCatching { LibTorrent.version(); true }.getOrDefault(false).also { libOk = it }

    @Volatile private var libOk: Boolean? = null

    // ---------- opening a stream ----------

    /**
     * Start [s] and return a loopback address the player can open, or one sentence for a toast.
     * Long-running by nature: finding the file list can take most of a minute on a cold swarm, and
     * the first pieces are waited for here so playback opens with something in hand rather than
     * stalling in the player. Cancelling the caller's job stops the download and clears up.
     */
    internal suspend fun open(ctx: Context, s: StreamItem): Answer = withContext(Dispatchers.IO) {
        // Nothing is playing when this fails, so nothing should be left running: a torrent added a
        // moment before the failure would otherwise keep pulling the swarm in the background, unseen.
        fun fail(why: String): Answer {
            stage = ""
            runCatching { shutdown(ctx) }
            return Answer(problem = why)
        }
        if (!Prefs.p2p) return@withContext Answer(problem = "P2P streams are off. Settings › Streams turns them on.")
        if (!available) return@withContext Answer(problem = "This device cannot play torrents.")
        val hash = s.infoHash.lowercase(Locale.US)
        if (!Regex("^[0-9a-f]{40}$").matches(hash)) return@withContext Answer(problem = "That stream has no usable address.")
        try {
            // the same torrent and file as the last play — the player is only hopping episodes
            live?.let { if (it.hash == hash && it.wanted == s.fileIdx) { stage = ""; return@withContext Answer(url = it.url) } }
            stage = "Looking for the file"
            release(ctx)                             // one torrent at a time: one screen, finite disk
            val session = session()
            val dir = dataDir(ctx)
            coroutineContext.ensureActive()
            val meta = session.fetchMagnet(magnet(hash, s.sources, s.fileName), META_TIMEOUT_S, dir)
                ?: return@withContext fail("No peers answered. That stream looks dead.")
            coroutineContext.ensureActive()
            val ti = TorrentInfo(meta)
            val files = ti.files()
            val index = pickFile(ti, s.fileIdx, s.fileName)
                ?: return@withContext fail("There is no video inside that torrent.")
            val size = files.fileSize(index)
            val free = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
            if (size > free - FREE_MARGIN) return@withContext fail("Not enough free space — that file is ${fmtSize(size)}.")
            stage = "Starting the download"
            val priorities = Priority.array(Priority.IGNORE, files.numFiles())
            priorities[index] = Priority.TOP_PRIORITY
            session.download(ti, dir, null, priorities, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
            var handle: TorrentHandle? = null
            var waited = 0
            while (waited < 15_000) {
                handle = session.find(ti.infoHash())
                if (handle != null && handle.isValid) break
                delay(150); waited += 150
            }
            val th = handle?.takeIf { it.isValid }
                ?: return@withContext fail("The engine could not start that torrent.")
            val pieceLen = ti.pieceLength()
            val offset = files.fileOffset(index)
            val first = (offset / pieceLen).toInt()
            val last = ((offset + size - 1) / pieceLen).toInt()
            th.setSequentialRange(first, last)
            // the head first so playback can begin, then the tail — a container's index often lives at the end
            for (p in first..minOf(first + HEAD_PIECES, last)) th.setPieceDeadline(p, (p - first + 1) * 700)
            for (p in maxOf(last - 1, first)..last) th.setPieceDeadline(p, 12_000)
            val port = P2pServer.start()
            val name = files.fileName(index)
            val l = Live(
                hash = hash, wanted = s.fileIdx, index = index, handle = th, name = name, size = size,
                offset = offset, pieceLen = pieceLen, firstPiece = first, lastPiece = last,
                file = File(dir, files.filePath(index)),
                url = "http://127.0.0.1:$port/p2p/$hash/${s.fileIdx}/" + enc(name),
                dir = dir, root = files.name(),
            )
            live = l
            stage = "Waiting for the first pieces"
            // enough of the front to open with: the player's own read timeout is far shorter than a cold swarm
            val head = minOf(first + 2, last)
            val until = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < until) {
                coroutineContext.ensureActive()
                if ((first..head).all { th.havePiece(it) }) break
                delay(250)
            }
            stage = ""
            if (!th.havePiece(first)) return@withContext fail("Nobody is sending this torrent. Try another stream.")
            Answer(url = l.url)
        } catch (e: CancellationException) {
            runCatching { release(ctx) }
            stage = ""
            throw e
        } catch (t: Throwable) {
            fail("Could not start that P2P stream.")
        }
    }

    // ---------- the swarm, in words ----------

    /** The live line under the preparing sheet's heading, and the player's Info panel row. */
    fun line(): String {
        val l = live
        val s = sm
        if (l == null) {
            val nodes = runCatching { s?.dhtNodes() ?: 0L }.getOrDefault(0L)
            return if (nodes > 0) "$nodes in the network" else "Reaching the network…"
        }
        val st = runCatching { l.handle.status() }.getOrNull() ?: return "…"
        val parts = mutableListOf<String>()
        parts.add("${st.numPeers()} peer" + if (st.numPeers() == 1) "" else "s")
        if (st.numSeeds() > 0) parts.add("${st.numSeeds()} sharing all of it")
        parts.add(fmtRate(st.downloadPayloadRate().toLong()))
        parts.add(String.format(Locale.US, "%.0f%%", st.progress() * 100f))
        return parts.joinToString(" · ")
    }

    /** True while a read has been sitting on a piece long enough to be worth telling the viewer about. */
    fun stalled(): Boolean {
        val since = live?.waitingSince ?: 0L
        return since > 0L && System.currentTimeMillis() - since > 6_000
    }

    // ---------- housekeeping ----------

    /** Stop the torrent. Its data goes too unless "Keep downloads" is on, in which case the folder is trimmed. */
    fun release(ctx: Context) {
        val l = live ?: return
        live = null
        runCatching { sm?.remove(l.handle) }
        if (!Prefs.p2pKeep) runCatching { File(l.dir, l.root).deleteRecursively() }
        else runCatching { trim(dataDir(ctx), KEEP_CAP) }
    }

    /**
     * What the player calls on its way out. Stopping libtorrent blocks and deleting a film-sized
     * download is not instant, so neither happens on the thread drawing the screen behind it.
     */
    fun leave(ctx: Context) {
        if (live == null && sm == null) return
        val app = ctx.applicationContext
        Thread({ runCatching { shutdown(app) } }, "nebula-p2p-stop").apply { isDaemon = true }.start()
    }

    /** Everything: the torrent, the session and the loopback server. */
    fun shutdown(ctx: Context) {
        release(ctx)
        P2pServer.stop()
        val s = sm ?: return
        sm = null
        runCatching { s.stop() }      // blocking by design; only ever called off the main thread
    }

    fun cacheBytes(ctx: Context): Long = runCatching { dirSize(dataDir(ctx)) }.getOrDefault(0L)

    fun clearCache(ctx: Context) {
        release(ctx)
        runCatching { dataDir(ctx).listFiles()?.forEach { it.deleteRecursively() } }
    }

    // ---------- internals ----------

    private fun session(): SessionManager {
        sm?.let { if (it.isRunning) return it }
        synchronized(this) {
            sm?.let { if (it.isRunning) return it }
            val sp = SettingsPack()
                .connectionsLimit(150)
                .activeDownloads(2)
                .activeSeeds(0)
                .activeLimit(4)
                .maxPeerlistSize(800)
                .downloadRateLimit(0)
                // a phone is not a seedbox: share back, but never at the cost of the picture
                .uploadRateLimit(512 * 1024)
                .seedingOutgoingConnections(false)
                .listenInterfaces("0.0.0.0:0,[::]:0")
            val s = SessionManager()
            s.start(SessionParams(sp))
            sm = s
            return s
        }
    }

    /** `sources` entries look like "tracker:udp://host:port/announce" or "dht:<hash>" — only trackers are of use here. */
    private fun magnet(hash: String, sources: List<String>, name: String): String {
        val trackers = sources.mapNotNull { raw ->
            val v = raw.removePrefix("tracker:").trim()
            v.takeIf { it.startsWith("udp://") || it.startsWith("http://") || it.startsWith("https://") }
        }
        val all = (trackers + FALLBACK_TRACKERS).distinct().take(24)
        val dn = if (name.isNotEmpty()) "&dn=" + enc(name) else ""
        return "magnet:?xt=urn:btih:$hash$dn" + all.joinToString("") { "&tr=" + enc(it) }
    }

    /** The add-on's index when it points at a real file, else the biggest video in the torrent. */
    private fun pickFile(ti: TorrentInfo, wanted: Int, hintName: String): Int? {
        val fs = ti.files()
        val n = fs.numFiles()
        fun playable(i: Int) = !fs.padFileAt(i) && VIDEO.containsMatchIn(fs.fileName(i))
        if (wanted in 0 until n && !fs.padFileAt(wanted) &&
            (playable(wanted) || (0 until n).none { playable(it) })) return wanted
        if (hintName.isNotEmpty()) {
            for (i in 0 until n) if (fs.fileName(i).equals(hintName, true)) return i
        }
        return (0 until n).filter { playable(it) }.maxByOrNull { fs.fileSize(it) }
            ?: (0 until n).filterNot { fs.padFileAt(it) }.maxByOrNull { fs.fileSize(it) }
    }

    private val VIDEO = Regex("\\.(mkv|mp4|m4v|avi|mov|ts|m2ts|webm|wmv|flv|mpg|mpeg)$", RegexOption.IGNORE_CASE)

    /** App-private and outside the cache the system may empty mid-film; gone when Nebula is uninstalled. */
    private fun dataDir(ctx: Context): File {
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "p2p").apply { mkdirs() }
    }

    private fun dirSize(f: File): Long =
        if (f.isDirectory) (f.listFiles()?.sumOf { dirSize(it) } ?: 0L) else f.length()

    /** Oldest downloads go first until the folder fits under [cap]. */
    private fun trim(dir: File, cap: Long) {
        var total = dirSize(dir)
        if (total <= cap) return
        dir.listFiles()?.sortedBy { it.lastModified() }?.forEach { f ->
            if (total <= cap) return
            val n = dirSize(f)
            if (f.deleteRecursively()) total -= n
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    internal fun fmtSize(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / 1073741824.0)
        b >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", b / 1048576.0)
        else -> String.format(Locale.US, "%d KB", b / 1024)
    }

    private fun fmtRate(bps: Long): String = when {
        bps >= 1L shl 20 -> String.format(Locale.US, "%.1f MB/s", bps / 1048576.0)
        else -> String.format(Locale.US, "%d KB/s", bps / 1024)
    }
}

/**
 * What is on screen while a torrent is being found: the title, the stage in plain words, the live
 * swarm line, and a way out. Held up in front of everything because there is nothing else to do
 * until the swarm answers — and a cold magnet can take most of a minute.
 */
@Composable
internal fun P2pSheet(title: String, onCancel: () -> Unit) {
    var line by remember { mutableStateOf("Reaching the network…") }
    LaunchedEffect(Unit) {
        while (true) { line = P2p.line(); delay(600) }
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xD40A0A0C))) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF141418))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .navigationBarsPadding().padding(22.dp),
            ) {
                Text(
                    "FINDING A TORRENT", color = MutedC, fontFamily = Mono, fontSize = 10.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp,
                )
                Text(
                    title, color = TextC, fontSize = 19.sp, fontFamily = Sans, fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    P2p.stage.ifEmpty { "Looking for the file" }, color = MutedC, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(line, color = FaintC, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Stop", false) { onCancel() }
                }
                Box(Modifier.height(2.dp))
            }
        }
    }
}
