package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Continue watching, Nuvio's way (11-05; mirrors the shared player's `continueList` + `nu*` block). ONE card per show —
 * its newest resume point, or, once the newest thing played there is finished, the episode after it ("Up next", worked
 * out by [seriesCursorOf] over the show's episode list) — and every film part-way through.
 *
 * The episode list lives with the add-on, so the answer is kept per profile in prefs (`nextup`, [Viewers.local]) against
 * the show's `sig` (the newest `at` of any of its records, so a mark, an unmark or a play works it out again). A stale
 * answer (12 h — a new episode may have aired) is shown while it is asked again; at most 12 shows are asked, three at a
 * time, a moment after the list was read. A card taken off the row is a `nextup:<show>` dismissed record in the progress
 * store (it syncs), and stays off until another of the show's episodes is finished.
 */
object ContinueNext {
    private const val TTL = 12 * 3600_000L
    private const val FAIL = 10 * 60_000L
    private const val SOON = 7 * 86_400_000L
    private const val ASK = 12
    const val NEXTUP = "nextup"

    data class Ep(val id: String, val s: Int, val e: Int?, val name: String, val thumb: String?, val rel: String?)
    data class Answer(
        val sig: Long, val t: Long, val name: String = "", val poster: String? = null, val back: String? = null,
        val addonUrl: String = "", val same: Boolean = false, val ep: Ep? = null,
    )
    /** One show's records, gathered: the newest played record, the newest finish, where it came from. */
    data class Show(
        val id: String, val type: String, var sig: Long = 0L, var last: ProgressRec? = null,
        var seedAt: Long = 0L, var addonUrl: String = "", var urlAt: Long = 0L,
    )

    /** Bumped when answers arrive: Home and Library read the list again. */
    var version by mutableIntStateOf(0)
        private set

    private var mem: Pair<String, MutableMap<String, Answer>>? = null
    private val queue = LinkedHashMap<String, Show>()
    private var running = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE)

    private fun store(ctx: Context): MutableMap<String, Answer> {
        val key = Viewers.local("nextup")
        mem?.let { if (it.first == key) return it.second }
        val m = LinkedHashMap<String, Answer>()
        runCatching {
            val o = JSONObject(prefs(ctx).getString(key, "{}") ?: "{}")
            for (k in o.keys()) {
                val a = o.optJSONObject(k) ?: continue
                val e = a.optJSONObject("ep")
                m[k] = Answer(
                    sig = a.optLong("sig"), t = a.optLong("t"), name = a.optString("name"),
                    poster = a.optString("poster").ifEmpty { null }, back = a.optString("back").ifEmpty { null },
                    addonUrl = a.optString("addonUrl"), same = a.optBoolean("same"),
                    ep = e?.let {
                        Ep(it.optString("id"), it.optInt("s", 1), if (it.has("e") && !it.isNull("e")) it.optInt("e") else null,
                            it.optString("name"), it.optString("thumb").ifEmpty { null }, it.optString("rel").ifEmpty { null })
                    },
                )
            }
        }
        mem = key to m
        return m
    }

    private fun save(ctx: Context) {
        val m = store(ctx)
        if (m.size > 60) m.entries.sortedBy { it.value.t }.take(m.size - 60).map { it.key }.forEach { m.remove(it) }
        val o = JSONObject()
        m.forEach { (k, a) ->
            o.put(k, JSONObject().put("sig", a.sig).put("t", a.t).put("name", a.name).put("poster", a.poster ?: "")
                .put("back", a.back ?: "").put("addonUrl", a.addonUrl).put("same", a.same)
                .put("ep", a.ep?.let { e ->
                    JSONObject().put("id", e.id).put("s", e.s).put("e", e.e ?: JSONObject.NULL).put("name", e.name)
                        .put("thumb", e.thumb ?: "").put("rel", e.rel ?: "")
                }))
        }
        prefs(ctx).edit().putString(Viewers.local("nextup"), o.toString()).apply()
    }

    /** The other profile from now on: its own answers. */
    fun reset() { mem = null; queue.clear(); version++ }

    /**
     * The row: one card per show plus the films, newest first (A to Z on the setting), twenty at most. Pure over its
     * inputs — [answer] gives a show's kept Up next (null while it is asked: [want] is told).
     */
    internal fun plan(
        all: Map<String, ProgressRec>, off: Set<String>, nextOn: Boolean, unaired: Boolean, az: Boolean, now: Long,
        answer: (Show) -> Answer?, want: (Show) -> Unit,
    ): List<ProgressRec> {
        val out = ArrayList<ProgressRec>()
        val shows = LinkedHashMap<String, Show>()
        for (r in all.values) {
            if (r.id.isEmpty() || r.type == NEXTUP) continue
            val sid = if (r.type == "series") seriesIdOf(r.id) else ""
            if (sid.isNotEmpty() && sid != r.id) {
                val s = shows.getOrPut(sid) { Show(sid, r.type) }
                s.sig = maxOf(s.sig, r.at)
                if (r.dismissed) continue
                if (r.addonUrl.isNotEmpty() && r.at >= s.urlAt) { s.addonUrl = r.addonUrl; s.urlAt = r.at }
                if (r.done) {
                    s.seedAt = maxOf(s.seedAt, r.at)
                    // a hand mark is a claim about the past, not where the viewer is (seriesCursorOf reads it the same way)
                    if (!r.hand && (s.last?.let { r.at > it.at } != false)) s.last = r
                    continue
                }
                if (Progress.resumable(r) && (s.last?.let { r.at > it.at } != false)) s.last = r
                continue
            }
            if (!Progress.resumable(r)) continue
            if (r.addonUrl.isNotEmpty() && r.addonUrl in off) continue
            out += r
        }
        for (s in shows.values) {
            if (s.addonUrl.isNotEmpty() && s.addonUrl in off) continue
            val last = s.last
            if (last != null && !last.done) { out += last; continue }          // the newest thing here is part-way: resume it
            if (s.seedAt <= 0L || !nextOn) continue                              // nothing finished, or Up next switched off
            val nd = all[Progress.key(NEXTUP, s.id)]
            if (nd != null && nd.dismissed && nd.at >= s.seedAt) continue        // taken off — until another one is finished
            val a = answer(s)
            if (a == null || a.sig != s.sig || now - a.t > TTL) want(s)
            if (a == null || a.sig != s.sig) continue
            val e = a.ep ?: continue
            // the episode after the one finished was started already (an older part-way record): that is the card
            val own = all[Progress.key(s.type, e.id)]
            if (own != null && Progress.resumable(own)) { out += own; continue }
            if (own != null && own.done) continue
            val rel = NewEpisodesPlan.releasedAt(e.rel)
            val isOut = rel == null || rel <= now
            // not out yet: only with "Show episodes not out yet", and a new season only in its last week (Nuvio's rule)
            if (!isOut && (!unaired || !(a.same || rel!! - now <= SOON))) continue
            val tag = "S${e.s}" + (e.e?.let { "E$it" } ?: "")
            out += ProgressRec(
                type = s.type, id = e.id,
                name = a.name + " · " + tag + (if (e.name.isNotEmpty()) " · " + e.name else ""),
                poster = e.thumb ?: a.back ?: a.poster, shape = "landscape",
                addonUrl = s.addonUrl.ifEmpty { a.addonUrl }, at = s.seedAt,
                next = true, seriesId = s.id, seriesPoster = a.poster,
                airs = if (isOut) 0L else rel!!, fresh = isOut && rel != null && rel > s.seedAt,
            )
        }
        val sorted = if (az) out.sortedBy { it.name.lowercase() } else out.sortedByDescending { it.at }
        return sorted.take(20)
    }

    fun list(ctx: Context, all: Map<String, ProgressRec>, off: Set<String>): List<ProgressRec> {
        val m = store(ctx)
        return plan(
            all, off, nextOn = Prefs.cwNext, unaired = Prefs.cwUnaired, az = Prefs.cwSort == "az", now = System.currentTimeMillis(),
            answer = { m[it.id] }, want = { want(ctx.applicationContext, it) },
        )
    }

    private fun want(ctx: Context, s: Show) {
        if (queue[s.id]?.sig == s.sig) return
        queue[s.id] = s.copy()
        if (!running) {
            running = true
            scope.launch { delay(900); run(ctx) }
        }
    }

    private suspend fun run(ctx: Context) {
        while (queue.isNotEmpty()) {
            val key = Viewers.local("nextup")
            val todo = queue.values.sortedByDescending { it.seedAt }.take(ASK)
            queue.clear()
            val got = withContext(Dispatchers.IO) {
                todo.chunked(3).flatMap { part -> part.map { s -> async { s to ask(ctx, s) } }.awaitAll() }
            }
            if (Viewers.local("nextup") != key) continue               // (the profile changed while asking)
            val m = store(ctx)
            val all = Progress.all(ctx)
            val now = System.currentTimeMillis()
            for ((s, meta) in got) {
                if (meta == null) { m[s.id] = Answer(s.sig, now - TTL + FAIL, ep = m[s.id]?.takeIf { it.sig == s.sig }?.ep); continue }
                val flat = meta.videos.sortedWith(compareBy({ it.season == 0 }, { it.season }, { it.episode ?: 0 }))
                val cur = seriesCursorOf(flat) { id -> all[Progress.key(s.type, id)] }
                val ep = cur?.upNext
                val i = if (ep == null) -1 else flat.indexOf(ep)
                m[s.id] = Answer(
                    sig = s.sig, t = now, name = meta.name, poster = meta.poster, back = meta.background,
                    addonUrl = s.addonUrl, same = i > 0 && flat[i - 1].season == ep!!.season,
                    ep = ep?.let { Ep(it.id, it.season, it.episode, it.name, it.thumbnail, it.released) },
                )
            }
            save(ctx)
            version++
        }
        running = false
    }

    /** The show's meta: from the add-on it came from, else the profile's other add-ons that serve its meta. */
    private suspend fun ask(ctx: Context, s: Show): FullMeta? {
        val addons = activeAddons(ctx)
        val order = addons.sortedByDescending { it.manifestUrl == s.addonUrl }
        for (a in order) {
            if (a.manifestUrl != s.addonUrl) {
                val ok = runCatching { manifestFor(a.manifestUrl).canMeta(s.type, s.id) }.getOrDefault(false)
                if (!ok) continue
            }
            val meta = runCatching { Stremio.loadFullMeta(a.base, s.type, s.id) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            if (meta != null && meta.videos.isNotEmpty()) return meta
        }
        return null
    }

    /** Take a show's Up next card off the row; it comes back once another of its episodes is finished. */
    fun dismiss(ctx: Context, seriesId: String) = Progress.dismissNext(ctx, seriesId)

    // ---------- resume at launch (Nuvio's resume prompt) ----------
    // Playing writes `last_play`, leaving the player the normal way clears it, so an app closed (or killed) in the
    // middle of something finds it at the next start and offers it back once. Settings › Offer to resume at start.
    fun markPlaying(ctx: Context, type: String?, id: String?) {
        val e = prefs(ctx).edit()
        if (type.isNullOrEmpty() || id.isNullOrEmpty() || type == "tv") e.remove("last_play")
        else e.putString("last_play", JSONObject().put("v", Viewers.current).put("type", type).put("id", id)
            .put("at", System.currentTimeMillis()).toString())
        e.apply()
    }

    /** What the last start left behind, read once (it is cleared as it is read). */
    fun takeLastPlay(ctx: Context): Triple<String, String, String>? {
        val p = prefs(ctx)
        val s = p.getString("last_play", null) ?: return null
        p.edit().remove("last_play").apply()
        val o = runCatching { JSONObject(s) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - o.optLong("at") > 14 * 86_400_000L) return null
        return Triple(o.optString("v"), o.optString("type"), o.optString("id"))
    }

    /** The card to offer for [type]/[id]: its resume point, or — finished — the show's Up next if it is out. */
    fun offerFor(ctx: Context, type: String, id: String): ProgressRec? {
        val r = Progress.get(ctx, type, id) ?: return null
        if (Progress.resumable(r)) return r
        if (!r.done || type != "series") return null
        val sid = seriesIdOf(id)
        return Progress.continueList(ctx).firstOrNull { it.next && it.airs == 0L && it.seriesId == sid }
    }
}
