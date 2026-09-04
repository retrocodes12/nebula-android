package com.nuvio.ckplayer

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * The bridge between the swarm and the player: a very small HTTP server bound to loopback that
 * serves the file [P2p] is downloading as if it were an ordinary progressive video on a web host.
 *
 * The player asks for a byte range, this works out which pieces that range lives in, tells
 * libtorrent to fetch those first, waits for them, then reads them off the disk and writes them
 * out. A seek is just a range far from the last one — the sequential window moves there and the
 * pieces before it stop mattering. Nothing here is reachable from outside the device: the socket is
 * bound to 127.0.0.1, and a request that does not name the torrent currently playing gets a 404.
 */
internal object P2pServer {
    private const val READAHEAD = 12               // pieces kept on a deadline in front of the reader
    private const val SEEK_JUMP = 26               // pieces past the reader before a read counts as a seek
    private const val STALL_MS = 60_000L           // how long one piece may hold a response up
    private const val MAX_CONNS = 4
    private const val CHUNK = 128 * 1024

    @Volatile private var socket: ServerSocket? = null
    @Volatile private var port = 0
    private val conns = AtomicInteger(0)

    /** Binds on first use and stays up for the session; returns the port the address is built from. */
    fun start(): Int {
        socket?.let { if (!it.isClosed) return port }
        synchronized(this) {
            socket?.let { if (!it.isClosed) return port }
            val s = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            socket = s
            port = s.localPort
            Thread({ accept(s) }, "nebula-p2p-http").apply { isDaemon = true }.start()
            return port
        }
    }

    fun stop() {
        val s = socket ?: return
        socket = null
        runCatching { s.close() }
    }

    private fun accept(s: ServerSocket) {
        while (!s.isClosed) {
            val c = try { s.accept() } catch (e: IOException) { return }
            if (conns.get() >= MAX_CONNS) { runCatching { c.close() }; continue }
            conns.incrementAndGet()
            Thread({
                try { serve(c) } catch (t: Throwable) { /* a closed player is not an error */ }
                finally { conns.decrementAndGet(); runCatching { c.close() } }
            }, "nebula-p2p-conn").apply { isDaemon = true }.start()
        }
    }

    // ---------- one request ----------

    private fun serve(c: Socket) {
        c.tcpNoDelay = true
        c.soTimeout = 30_000
        val input = c.getInputStream()
        val out = BufferedOutputStream(c.getOutputStream(), CHUNK)
        val head = readHead(input) ?: return
        val request = head.firstOrNull().orEmpty().split(" ")
        val method = request.getOrNull(0).orEmpty().uppercase(Locale.US)
        val path = request.getOrNull(1).orEmpty()
        if (method != "GET" && method != "HEAD") return fail(out, 405, "Method not allowed")

        val live = P2p.live
        // the address names one torrent and one file; anything else is a leftover connection
        if (live == null || !path.startsWith("/p2p/${live.hash}/${live.wanted}/")) return fail(out, 404, "Not playing")

        val rangeHeader = head.firstOrNull { it.startsWith("range:", true) }?.substringAfter(':')?.trim()
        val range = parseRange(rangeHeader, live.size)
        if (range == null && rangeHeader != null) {
            writeHead(out, 416, mapOf("Content-Range" to "bytes */${live.size}", "Content-Length" to "0"))
            out.flush(); return
        }
        val start = range?.first ?: 0L
        val end = range?.second ?: (live.size - 1)
        val length = end - start + 1
        val headers = linkedMapOf(
            "Content-Type" to mimeOf(live.name),
            "Accept-Ranges" to "bytes",
            "Content-Length" to length.toString(),
            "Connection" to "close",
        )
        if (range != null) headers["Content-Range"] = "bytes $start-$end/${live.size}"
        writeHead(out, if (range != null) 206 else 200, headers)
        out.flush()
        if (method == "HEAD") return
        // The player reads one range at a time; a seek opens a new request and abandons the old one.
        // Claiming the serial here tells a read still waiting on a piece that nobody wants it any more.
        pump(live, live.serial.incrementAndGet(), start, end, out)
    }

    /** Request line and headers; null when the player hung up before sending one. */
    private fun readHead(input: InputStream): List<String>? {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        while (lines.size < 40) {
            val b = input.read()
            if (b < 0) return lines.takeIf { it.isNotEmpty() }
            if (b == '\n'.code) {
                val line = sb.toString().trimEnd('\r')
                sb.setLength(0)
                if (line.isEmpty()) return lines
                lines.add(line)
            } else if (sb.length < 4096) sb.append(b.toChar())
        }
        return lines
    }

    /** "bytes=1024-" or "bytes=0-1023"; null when absent or unsatisfiable. */
    private fun parseRange(header: String?, size: Long): Pair<Long, Long>? {
        val m = Regex("bytes=(\\d*)-(\\d*)").find(header ?: return null) ?: return null
        val fromText = m.groupValues[1]
        val toText = m.groupValues[2]
        if (fromText.isEmpty() && toText.isEmpty()) return null
        // "bytes=-500" means the last 500 bytes — the tail of a container, asked for often
        if (fromText.isEmpty()) {
            val n = toText.toLongOrNull() ?: return null
            if (n <= 0) return null
            return maxOf(0L, size - n) to size - 1
        }
        val from = fromText.toLongOrNull() ?: return null
        if (from >= size) return null
        val to = toText.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
        if (to < from) return null
        return from to to
    }

    // ---------- bytes ----------

    /**
     * Writes [start]..[end] of the file, waiting on each piece in turn. A piece that never arrives
     * ends the response: the player reports that rather than sitting on a frozen picture forever.
     */
    private fun pump(live: P2p.Live, serial: Int, start: Long, end: Long, out: OutputStream) {
        var pos = start
        var file: RandomAccessFile? = null
        try {
            val buf = ByteArray(CHUNK)
            while (pos <= end) {
                val piece = ((live.offset + pos) / live.pieceLen).toInt()
                if (!await(live, serial, piece)) return
                if (file == null) file = RandomAccessFile(live.file, "r")
                // never read past the piece just waited for
                val pieceLast = (piece + 1).toLong() * live.pieceLen - live.offset - 1
                val chunkEnd = minOf(end, pieceLast)
                file.seek(pos)
                while (pos <= chunkEnd) {
                    val want = minOf(buf.size.toLong(), chunkEnd - pos + 1).toInt()
                    val n = file.read(buf, 0, want)
                    if (n <= 0) return                 // the file is shorter than the torrent claims
                    out.write(buf, 0, n)
                    pos += n
                }
                out.flush()
            }
        } catch (e: IOException) {
            // the player closed the connection (a seek, or it went away) — nothing to report
        } finally {
            live.waitingSince = 0L
            runCatching { file?.close() }
        }
    }

    /** Puts [piece] at the front of the queue and waits for it. False when it never came. */
    private fun await(live: P2p.Live, serial: Int, piece: Int): Boolean {
        val h = live.handle
        if (h.havePiece(piece)) { prioritise(live, piece, moved = false); return true }
        prioritise(live, piece, moved = piece < live.head || piece > live.head + SEEK_JUMP)
        live.waitingSince = System.currentTimeMillis()
        val until = live.waitingSince + STALL_MS
        while (System.currentTimeMillis() < until) {
            if (h.havePiece(piece)) { live.waitingSince = 0L; return true }
            if (P2p.live !== live) return false             // the viewer moved on to another stream
            if (live.serial.get() != serial) return false   // a seek has already asked for somewhere else
            Thread.sleep(90)
        }
        live.waitingSince = 0L
        return false
    }

    /** Deadlines on the reader's window, and the sequential window itself when a seek moved it. */
    private fun prioritise(live: P2p.Live, piece: Int, moved: Boolean) {
        val h = live.handle
        runCatching {
            if (moved) h.setSequentialRange(piece, live.lastPiece)
            live.head = piece
            for (i in 0 until READAHEAD) {
                val p = piece + i
                if (p > live.lastPiece) break
                if (!h.havePiece(p)) h.setPieceDeadline(p, (i + 1) * 600)
            }
        }
    }

    // ---------- plumbing ----------

    private fun writeHead(out: OutputStream, code: Int, headers: Map<String, String>) {
        val reason = when (code) {
            200 -> "OK"; 206 -> "Partial Content"; 404 -> "Not Found"
            405 -> "Method Not Allowed"; 416 -> "Range Not Satisfiable"; else -> "Error"
        }
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun fail(out: OutputStream, code: Int, text: String) {
        writeHead(out, code, mapOf("Content-Type" to "text/plain", "Content-Length" to text.length.toString(), "Connection" to "close"))
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }
}
