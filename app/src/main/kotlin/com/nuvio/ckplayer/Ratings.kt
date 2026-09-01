package com.nuvio.ckplayer

import android.content.Context
import org.json.JSONObject

/**
 * Star ratings, ½ the Letterboxd idea: one record per title, same JSON shape
 * as the web player so they sync as the "ratings" key. Clearing writes a
 * tombstone rather than deleting — a stale synced copy must not resurrect it.
 */
object Ratings {
    private const val PREFS = "ckplayer"
    private const val KEY = "ratings"

    fun key(type: String, id: String) = "$type:$id"

    internal fun all(ctx: Context): JSONObject = runCatching {
        JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}")
    }.getOrDefault(JSONObject())

    internal fun replaceAll(ctx: Context, o: JSONObject) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    fun get(ctx: Context, type: String, id: String): Int {
        val r = all(ctx).optJSONObject(key(type, id)) ?: return 0
        return if (r.optBoolean("removed")) 0 else r.optInt("rating", 0)
    }

    /** rating 0 clears. Persists, syncs, and refreshes the friend profile. */
    fun set(ctx: Context, type: String, m: MetaItem, rating: Int) {
        val o = all(ctx)
        o.put(
            key(type, m.id),
            if (rating <= 0) JSONObject().put("removed", true).put("at", System.currentTimeMillis())
            else JSONObject()
                .put("type", type).put("id", m.id).put("name", m.name)
                .put("poster", m.poster ?: "").put("shape", m.posterShape)
                .put("rating", rating.coerceIn(1, 5)).put("at", System.currentTimeMillis()),
        )
        replaceAll(ctx, o)
        Cloud.noteChanged(ctx, KEY)
        Social.publishSoon(ctx)
    }

    /** Live ratings, newest first. */
    fun list(ctx: Context): List<JSONObject> {
        val o = all(ctx)
        val out = mutableListOf<JSONObject>()
        for (k in o.keys()) {
            val r = o.optJSONObject(k) ?: continue
            if (!r.optBoolean("removed") && r.optString("id").isNotEmpty()) out.add(r)
        }
        out.sortByDescending { it.optLong("at") }
        return out
    }
}
