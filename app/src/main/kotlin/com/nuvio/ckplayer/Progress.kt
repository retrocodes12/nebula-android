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
    /** Removed from Continue watching — a tombstone, not a delete, so a synced
        device still holding the old position cannot push it straight back. */
    val dismissed: Boolean = false,
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
                    dismissed = r.optBoolean("dismissed"),
                    at = r.optLong("at"),
                )
            }
        }
        cache = m
        return m
    }

    private fun persist(ctx: Context, m: MutableMap<String, ProgressRec>) {
        persistRaw(ctx, m)
        Cloud.noteChanged(ctx, "progress")
    }

    private fun persistRaw(ctx: Context, m: MutableMap<String, ProgressRec>) {
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
                    .put("done", r.done).put("dismissed", r.dismissed).put("at", r.at)
            )
        }
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }

    fun get(ctx: Context, type: String, id: String): ProgressRec? = load(ctx)[key(type, id)]

    /** Where playback of (type,id) should start, in ms — 0 means the beginning. */
    fun resumeAt(ctx: Context, type: String, id: String): Long {
        val r = get(ctx, type, id) ?: return 0
        if (r.done || r.dismissed || r.pos <= 0 || r.dur <= 0) return 0
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
            val cur = m[k]
            if (cur != null && !cur.done && !cur.dismissed) {
                m[k] = ProgressRec(rec.type, rec.id, dismissed = true, at = System.currentTimeMillis())
                persist(ctx, m)
            }
            return
        }
        m[k] = rec.copy(at = System.currentTimeMillis())
        persist(ctx, m)
    }

    fun clear(ctx: Context, type: String, id: String) {
        val m = load(ctx)
        val k = key(type, id)
        if (m[k] != null) {
            m[k] = ProgressRec(type, id, dismissed = true, at = System.currentTimeMillis())
            persist(ctx, m)
        }
    }

    /** The whole store, for sync. */
    internal fun all(ctx: Context): Map<String, ProgressRec> = load(ctx).toMap()

    /** Replace the whole store after a sync merge (no re-push side effects). */
    internal fun replaceAll(ctx: Context, m: Map<String, ProgressRec>) {
        val mm = LinkedHashMap(m)
        cache = mm
        persistRaw(ctx, mm)
    }

    /** The twenty most recent things worth resuming — newest first, or A to Z when Settings › Home says so. */
    fun continueList(ctx: Context): List<ProgressRec> {
        val recent = load(ctx).values
            .filter { !it.done && !it.dismissed && it.pos >= MIN_POS_MS && it.dur > 0 && it.pos <= it.dur - END_GAP_MS }
            .sortedByDescending { it.at }
            .take(20)
        return if (Prefs.cwSort == "az") recent.sortedBy { it.name.lowercase() } else recent
    }
}
