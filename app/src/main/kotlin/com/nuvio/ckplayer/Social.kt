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
 * Nebula Profile: friends add each other by @handle, and what is shared (name,
 * recent watches, ratings, My List) is served only to mutual friends. Opt-in,
 * and disable deletes it server-side. Installs from before profiles still wear
 * their 7-character friend code, which keeps working.
 */
object Social {
    private const val PREFS = "ckplayer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pubJob: Job? = null
    private val OLD_CODE = Regex("^[A-Z2-9]{7}$")

    var on by mutableStateOf(false); private set
    var code by mutableStateOf(""); private set
    var handle by mutableStateOf(""); private set

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        on = p.getBoolean("social_on", false)
        code = p.getString("social_code", "") ?: ""
        handle = p.getString("social_handle", "") ?: ""
    }

    private fun store(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("social_on", on).putString("social_code", code).putString("social_handle", handle).apply()
    }

    /** What friends type to find me: the handle, or the old code. */
    val myKey: String get() = if (handle.isNotEmpty()) "@$handle" else code

    /** Local forget only — the switch lives with the profile, not the device. */
    fun reset(ctx: Context) {
        pubJob?.cancel()
        on = false; code = ""; handle = ""; store(ctx)
    }

    /** Pull the server's view of Friends after signing in or booting. */
    suspend fun refresh(ctx: Context) {
        if (!Cloud.linked(ctx)) return
        runCatching {
            val r = Cloud.api(ctx, "GET", "/v1/social/me", null)
            on = r.optBoolean("on")
            code = if (on) r.optString("code") else ""
            handle = if (on) r.optString("handle") else ""
            store(ctx)
        }
    }

    fun displayName(ctx: Context): String =
        Cloud.profile?.name?.trim()?.ifEmpty { null }
            ?: (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("party_name", "") ?: "")
                .trim().ifEmpty { android.os.Build.MODEL.take(24) }

    /** Turns Friends on. A profile IS the identity friends look for, so one is required. */
    suspend fun enable(ctx: Context): String? {
        val p = Cloud.profile ?: return "Friends find each other by @handle — sign in or create a profile first."
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/social/enable", JSONObject().put("name", displayName(ctx)))
            on = true; code = r.optString("code"); handle = r.optString("handle").ifEmpty { p.handle }; store(ctx)
            publishSoon(ctx)
            null
        }.getOrElse { Account.errorText(it) }
    }

    suspend fun disable(ctx: Context) {
        runCatching { Cloud.api(ctx, "POST", "/v1/social/disable", JSONObject()) }
        on = false; code = ""; handle = ""; store(ctx)
    }

    /** `{handle}` or `{code}` for a friend card — what the server's social routes take. */
    fun friendRef(f: JSONObject): JSONObject {
        val h = f.optString("handle")
        return if (h.isNotEmpty()) JSONObject().put("handle", h) else JSONObject().put("code", f.optString("code"))
    }
    fun friendKey(f: JSONObject): String = f.optString("handle").ifEmpty { null }?.let { "@$it" } ?: f.optString("code")
    fun friendLabel(f: JSONObject): String = f.optString("name").ifEmpty { friendKey(f) }.ifEmpty { "A friend" }
    /** Friend cards carry the supporter mark too, so friends see each other's. */
    fun friendSup(f: JSONObject): Boolean = f.optBoolean("sup")
    fun friendMark(f: JSONObject): String = Support.cleanMark(f.optString("mark"))
    fun friendFounder(f: JSONObject): Boolean = f.optString("tier") == "founder"

    /** Friends are mutual (server, 09-23): an add is an ASK until they add back, and nothing is shared before that. */
    fun friendPending(f: JSONObject): Boolean = f.optBoolean("pending")

    /** Add a friend by @handle (or one of the old 7-character codes). Returns (their label, null) or (null, error);
        the label comes back with [lastAddPending] set when it is only an ask so far. */
    @Volatile var lastAddPending = false
        private set
    suspend fun addFriend(ctx: Context, raw: String): Pair<String?, String?> {
        lastAddPending = false
        val typed = raw.replace(Regex("\\s"), "")
        val ref = if (OLD_CODE.matches(typed)) JSONObject().put("code", typed)
        else {
            val h = Account.cleanHandle(typed)
            if (!Account.handleOk(h)) return null to "Enter a friend’s @handle."
            JSONObject().put("handle", h)
        }
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/social/friend", ref)
            lastAddPending = friendPending(r)
            friendLabel(r) to null
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            null to when ((it as? Cloud.HttpFail)?.code) {
                409 -> "They have a profile, but Friends is off on their side."
                404 -> "No one has that handle."
                else -> Account.errorText(it)
            }
        }
    }

    suspend fun friends(ctx: Context): JSONArray? = runCatching {
        Cloud.api(ctx, "GET", "/v1/social/friends", null).optJSONArray("friends")
    }.getOrNull()

    /** People who added you and wait for you to add them back. */
    suspend fun asks(ctx: Context): JSONArray? = runCatching {
        Cloud.api(ctx, "GET", "/v1/social/asks", null).optJSONArray("asks")
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

    /** Adding back is the yes (true when it worked). */
    suspend fun acceptAsk(ctx: Context, f: JSONObject): Boolean = runCatching {
        Cloud.api(ctx, "POST", "/v1/social/friend", friendRef(f)); true
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)

    /** Clears the ask from your list (the asker is not told). */
    suspend fun dismissAsk(ctx: Context, f: JSONObject) {
        runCatching { Cloud.api(ctx, "POST", "/v1/social/ask_dismiss", friendRef(f)) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    suspend fun inbox(ctx: Context): JSONArray? = runCatching {
        Cloud.api(ctx, "GET", "/v1/social/inbox", null).optJSONArray("inbox")
    }.getOrNull()

    suspend fun inboxClear(ctx: Context) {
        runCatching { Cloud.api(ctx, "POST", "/v1/social/inbox_clear", JSONObject()) }
    }

    suspend fun recommend(ctx: Context, friend: JSONObject, type: String, m: MetaItem): Boolean = runCatching {
        Cloud.api(
            ctx, "POST", "/v1/social/recommend",
            friendRef(friend).put(
                "item",
                JSONObject().put("type", type).put("id", m.id).put("name", m.name).put("poster", m.poster ?: ""),
            ),
        )
        true
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }   // cancelled is not "failed to send"
        .getOrDefault(false)

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
            // One record per episode: keep only the newest per show, or a friend's row repeats it.
            .distinctBy { it.type + ":" + (if (it.type == "series") seriesIdOf(it.id) else it.id) }
            .take(20)
            .forEach { r ->
                val rootId = if (r.type == "series") seriesIdOf(r.id) else r.id
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
