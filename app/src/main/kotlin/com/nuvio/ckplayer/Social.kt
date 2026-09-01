package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Friends — the experimental Letterboxd-shaped layer. The identity is the
 * cloud sync group wearing a permanent 7-char code; profiles (name, recent
 * watches, ratings, My List) are pushed to the relay and served only to
 * mutual friends. Opt-in, and disable deletes it server-side.
 */
object Social {
    private const val PREFS = "ckplayer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pubJob: Job? = null

    var on by mutableStateOf(false); private set
    var code by mutableStateOf(""); private set

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        on = p.getBoolean("social_on", false)
        code = p.getString("social_code", "") ?: ""
    }

    private fun store(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("social_on", on).putString("social_code", code).apply()
    }

    fun displayName(ctx: Context): String =
        (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("party_name", "") ?: "")
            .trim().ifEmpty { android.os.Build.MODEL.take(24) }

    /** Turns Friends on, minting a sync group first if this device has none. */
    suspend fun enable(ctx: Context): String? {
        if (!Cloud.linked(ctx) && Cloud.createGroup(ctx) == null) return "Could not reach the server."
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/social/enable", JSONObject().put("name", displayName(ctx)))
            on = true; code = r.getString("code"); store(ctx)
            publishSoon(ctx)
            null
        }.getOrDefault("Could not reach the server.")
    }

    suspend fun disable(ctx: Context) {
        runCatching { Cloud.api(ctx, "POST", "/v1/social/disable", JSONObject()) }
        on = false; code = ""; store(ctx)
    }

    suspend fun addFriend(ctx: Context, codeRaw: String): Pair<String?, String?> {
        val c = codeRaw.trim().replace(" ", "").uppercase()
        if (c.length < 7) return null to "Enter the 7-character friend code."
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/social/friend", JSONObject().put("code", c))
            r.optString("name").ifEmpty { "your friend" } to null
        }.getOrDefault(null to "No one has that code.")
    }

    suspend fun friends(ctx: Context): JSONArray? = runCatching {
        Cloud.api(ctx, "GET", "/v1/social/friends", null).optJSONArray("friends")
    }.getOrNull()

    suspend fun inbox(ctx: Context): JSONArray? = runCatching {
        Cloud.api(ctx, "GET", "/v1/social/inbox", null).optJSONArray("inbox")
    }.getOrNull()

    suspend fun inboxClear(ctx: Context) {
        runCatching { Cloud.api(ctx, "POST", "/v1/social/inbox_clear", JSONObject()) }
    }

    suspend fun recommend(ctx: Context, friendCode: String, type: String, m: MetaItem): Boolean = runCatching {
        Cloud.api(
            ctx, "POST", "/v1/social/recommend",
            JSONObject().put("code", friendCode).put(
                "item",
                JSONObject().put("type", type).put("id", m.id).put("name", m.name).put("poster", m.poster ?: ""),
            ),
        )
        true
    }.getOrDefault(false)

    /** Push the profile a few seconds after whatever changed it settles. */
    fun publishSoon(ctx: Context) {
        if (!on) return
        val app = ctx.applicationContext
        pubJob?.cancel()
        pubJob = scope.launch { delay(4000); publish(app) }
    }

    private suspend fun publish(ctx: Context) {
        if (!on || !Cloud.linked(ctx)) return
        val recent = JSONArray()
        Progress.all(ctx).values
            .filter { !it.dismissed && it.name.isNotEmpty() }
            .sortedByDescending { it.at }
            .take(20)
            .forEach { r ->
                val rootId = if (r.type == "series") r.id.substringBefore(':') else r.id
                recent.put(
                    JSONObject().put("type", r.type).put("id", r.id).put("name", r.name)
                        .put("poster", r.poster ?: "").put("shape", r.shape).put("at", r.at)
                        .put("rating", Ratings.get(ctx, r.type, rootId)),
                )
            }
        val ratings = JSONArray()
        Ratings.list(ctx).take(60).forEach { ratings.put(it) }
        val list = JSONArray()
        Library.list(ctx).take(40).forEach { li ->
            list.put(
                JSONObject().put("type", li.type).put("id", li.id).put("name", li.name)
                    .put("poster", li.poster ?: "").put("shape", li.shape),
            )
        }
        val doc = JSONObject()
            .put("name", displayName(ctx)).put("recent", recent)
            .put("ratings", ratings).put("list", list)
        runCatching {
            Cloud.api(
                ctx, "PUT", "/v1/social/profile",
                JSONObject().put("v", doc.toString()).put("name", displayName(ctx)),
            )
        }
    }
}
