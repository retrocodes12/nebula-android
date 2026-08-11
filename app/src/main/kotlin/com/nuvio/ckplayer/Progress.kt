package com.nuvio.ckplayer

import android.content.Context
import org.json.JSONObject

/**
 * One resume point per playable item, keyed "<type>:<id>". For a series that id
 * is the episode id, so every episode resumes on its own. Finished items keep a
 * `done` tombstone so episode lists can tick them off.
 *
 * Mirrors the shared HTML player's store field for field, deliberately: the two
 * are read by humans side by side when something looks wrong on one platform.
 */
data class ProgressRec(
    val type: String,
    val id: String,
    val name: String = "",
    val poster: String? = null,
    val shape: String = "poster",
    val addonUrl: String = "",
    val pos: Long = 0L,
    val dur: Long = 0L,
    val done: Boolean = false,
    val at: Long = 0L,
)

object Progress {
    private const val KEY = "progress"
    private const val MAX = 200            // newest N kept
    const val MIN_POS_MS = 15_000L         // below this it isn't worth resuming
    const val END_GAP_MS = 60_000L         // within this of the end counts as finished

    private var cache: MutableMap<String, ProgressRec>? = null

    fun key(type: String, id: String) = "$type:$id"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE)

    private fun load(ctx: Context): MutableMap<String, ProgressRec> {
        cache?.let { return it }
        val m = LinkedHashMap<String, ProgressRec>()
        runCatching {
            val o = JSONObject(prefs(ctx).getString(KEY, "{}") ?: "{}")
            for (k in o.keys()) {
                val r = o.optJSONObject(k) ?: continue
                m[k] = ProgressRec(
                    type = r.optString("type"),
                    id = r.optString("id"),
                    name = r.optString("name"),
                    poster = r.optString("poster").ifEmpty { null },
                    shape = r.optString("shape", "poster"),
                    addonUrl = r.optString("addonUrl"),
                    pos = r.optLong("pos"),
                    dur = r.optLong("dur"),
                    done = r.optBoolean("done"),
                    at = r.optLong("at"),
                )
            }
        }
        cache = m
        return m
    }

    private fun persist(ctx: Context, m: MutableMap<String, ProgressRec>) {
        if (m.size > MAX) {
            m.entries.sortedByDescending { it.value.at }.drop(MAX).map { it.key }
                .forEach { m.remove(it) }
        }
        val o = JSONObject()
        m.forEach { (k, r) ->
            o.put(
                k,
                JSONObject()
                    .put("type", r.type).put("id", r.id).put("name", r.name)
                    .put("poster", r.poster ?: "").put("shape", r.shape)
                    .put("addonUrl", r.addonUrl).put("pos", r.pos).put("dur", r.dur)
                    .put("done", r.done).put("at", r.at)
            )
        }
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }

    fun get(ctx: Context, type: String, id: String): ProgressRec? = load(ctx)[key(type, id)]

    /** Where playback of (type,id) should start, in ms — 0 means the beginning. */
    fun resumeAt(ctx: Context, type: String, id: String): Long {
        val r = get(ctx, type, id) ?: return 0
        if (r.done || r.pos <= 0 || r.dur <= 0) return 0
        if (r.pos < MIN_POS_MS || r.pos > r.dur - END_GAP_MS) return 0
        return r.pos
    }

    fun note(ctx: Context, rec: ProgressRec) {
        if (rec.id.isEmpty()) return
        val m = load(ctx)
        val k = key(rec.type, rec.id)
        if (rec.done || (rec.dur > 0 && rec.pos > rec.dur - END_GAP_MS)) {
            m[k] = ProgressRec(rec.type, rec.id, done = true, at = System.currentTimeMillis())
            persist(ctx, m)
            return
        }
        if (rec.pos < MIN_POS_MS) {                 // rewound to the top — forget it
            if (m[k]?.done == false) { m.remove(k); persist(ctx, m) }
            return
        }
        m[k] = rec.copy(at = System.currentTimeMillis())
        persist(ctx, m)
    }

    fun clear(ctx: Context, type: String, id: String) {
        val m = load(ctx)
        if (m.remove(key(type, id)) != null) persist(ctx, m)
    }

    /** Newest first, only the things actually worth resuming. */
    fun continueList(ctx: Context): List<ProgressRec> =
        load(ctx).values
            .filter { !it.done && it.pos >= MIN_POS_MS && it.dur > 0 && it.pos <= it.dur - END_GAP_MS }
            .sortedByDescending { it.at }
            .take(20)
}
