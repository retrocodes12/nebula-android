package com.nuvio.ckplayer

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val handle: String, val name: String, val avatar: String)
data class DeviceRec(val id: String, val name: String, val plat: String, val at: Long, val seen: Long, val me: Boolean)
class TvCode(val code: String, val poll: String, val until: Long)

/**
 * Nebula Profile — an @handle and a password, nothing else. Every action here is
 * one call to the cloud (see cloud/profile.js in nebula-player) followed by the
 * local bookkeeping in [Cloud]; each returns null on success or a user-facing
 * error string, so screens never see HTTP.
 */
object Account {
    private val HANDLE_RE = Regex("^[a-z0-9_]{3,20}$")
    val AVATARS = listOf("#636366", "#E50914", "#0A84FF", "#30D158", "#BF5AF2", "#FF9F0A", "#FF375F", "#F2F2F7")

    /** User-facing text for a failed call — the server's error strings, translated once, here. */
    fun errorText(e: Throwable?, fallback: String = "Could not reach the server."): String {
        val f = e as? Cloud.HttpFail ?: return fallback
        if (f.code == 429) return "Too many tries — give it a minute."
        return when (f.error) {
            "handle taken" -> "That handle is taken."
            "bad handle" -> "Handles are 3–20 letters, numbers or underscores."
            "bad password" -> "Passwords are at least 8 characters."
            "wrong handle or password" -> "Wrong handle or password."
            "wrong handle or key" -> "That handle and recovery key don’t match."
            "wrong password" -> "Wrong password."
            "already has a profile" -> "This device already belongs to a profile — it will show up in a moment."
            "code not found or expired" -> "That code was not found — it may have expired."
            "unauthorized" -> "This device was signed out. Sign in again."
            else -> "Something went wrong (${f.code})."
        }
    }

    // ---------- identity ----------
    fun isTv(ctx: Context): Boolean {
        val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    /** How this device is listed on the profile. */
    fun deviceInfo(ctx: Context): JSONObject {
        val tv = isTv(ctx)
        val model = Build.MODEL.trim().take(24)
        return JSONObject()
            .put("name", if (tv) "Android TV" else model.ifEmpty { "Android phone" })
            .put("plat", if (tv) "androidtv" else "android")
    }
    fun handleOk(h: String) = HANDLE_RE.matches(h)
    fun cleanHandle(raw: String) = raw.trim().removePrefix("@").lowercase()

    /** Boot: a pre-profile install trades the group's master secret for a token of its own. */
    suspend fun boot(ctx: Context) {
        if (!Cloud.linked(ctx)) return
        if (!Cloud.hasToken(ctx)) {
            runCatching {
                val r = Cloud.api(ctx, "POST", "/v1/device", JSONObject().put("device", deviceInfo(ctx)))
                Cloud.setToken(ctx, r.getString("token"))
                Cloud.setProfile(ctx, r.optJSONObject("profile"))
            }                                   // offline: keep the secret, try again next boot
            return
        }
        refreshProfile(ctx)
        Social.refresh(ctx)
    }

    /** Pull the profile and its device list; returns false when the call failed. */
    suspend fun refreshProfile(ctx: Context): Boolean {
        if (!Cloud.linked(ctx)) return false
        return runCatching {
            val r = Cloud.api(ctx, "GET", "/v1/profile/me", null)
            Cloud.setProfile(ctx, if (r.optBoolean("on")) r else null)
            val arr = r.optJSONArray("devices") ?: JSONArray()
            Cloud.devices = (0 until arr.length()).mapNotNull { i ->
                val d = arr.optJSONObject(i) ?: return@mapNotNull null
                DeviceRec(d.optString("id"), d.optString("name").ifEmpty { "Device" }, d.optString("plat"),
                    d.optLong("at"), d.optLong("seen"), d.optBoolean("me"))
            }
            true
        }.getOrDefault(false)
    }

    // ---------- sign in / create / recover ----------
    /** Returns null on success, else a user-facing error. */
    suspend fun signIn(ctx: Context, handleRaw: String, password: String): String? {
        val handle = cleanHandle(handleRaw)
        if (!handleOk(handle)) return if (handle.isEmpty()) "Enter your @handle." else "Handles are 3–20 letters, numbers or underscores."
        if (password.isEmpty()) return "Enter your password."
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/profile/signin",
                JSONObject().put("handle", handle).put("password", password).put("device", deviceInfo(ctx)), auth = false)
            Cloud.adopt(ctx, r, fresh = false)
            null
        }.getOrElse { errorText(it) }
    }

    /**
     * Create a profile. On a legacy link-code device the profile wraps the existing
     * group (nothing re-syncs); otherwise it is a brand-new one seeded from this
     * device. Returns (recovery key, null) or (null, error).
     */
    suspend fun createProfile(ctx: Context, handleRaw: String, name: String, password: String): Pair<String?, String?> {
        val handle = cleanHandle(handleRaw)
        if (!handleOk(handle)) return null to "Handles are 3–20 letters, numbers or underscores."
        if (password.length < 8) return null to "Passwords are at least 8 characters."
        val attach = Cloud.linked(ctx)
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/profile",
                JSONObject().put("handle", handle).put("name", name.trim()).put("password", password).put("device", deviceInfo(ctx)),
                auth = attach)
            val key = r.getString("recovery")
            Cloud.adopt(ctx, r, fresh = !attach)
            key to null
        }.getOrElse { null to errorText(it) }
    }

    /** Reset the password with the recovery key; signs out every other device. Returns (new key, null) or (null, error). */
    suspend fun recover(ctx: Context, handleRaw: String, key: String, password: String): Pair<String?, String?> {
        val handle = cleanHandle(handleRaw)
        if (!handleOk(handle)) return null to "Enter your @handle."
        if (key.replace(Regex("[^A-Za-z0-9]"), "").length < 16) return null to "Enter the 16-character recovery key."
        if (password.length < 8) return null to "Passwords are at least 8 characters."
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/profile/recover",
                JSONObject().put("handle", handle).put("key", key.trim()).put("password", password).put("device", deviceInfo(ctx)),
                auth = false)
            val fresh = r.getString("recovery")
            Cloud.adopt(ctx, r, fresh = false)
            fresh to null
        }.getOrElse { null to errorText(it) }
    }

    /** Sign out here only; the server forgets this device's token, nothing local is deleted. */
    suspend fun signOut(ctx: Context) {
        Cloud.flushAndWait(ctx)
        runCatching { Cloud.api(ctx, "POST", "/v1/profile/signout", JSONObject()) }
        Cloud.forget(ctx)
    }

    // ---------- a signed-in device managing its profile ----------
    suspend fun updateProfile(ctx: Context, name: String? = null, avatar: String? = null): String? = runCatching {
        val body = JSONObject()
        if (name != null) body.put("name", name.trim())
        if (avatar != null) body.put("avatar", avatar)
        val r = Cloud.api(ctx, "PUT", "/v1/profile", body)
        Cloud.setProfile(ctx, r.optJSONObject("profile"))
        Social.publishSoon(ctx)
        null
    }.getOrElse { errorText(it) }

    suspend fun changePassword(ctx: Context, current: String, next: String): String? {
        if (next.length < 8) return "Passwords are at least 8 characters."
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/profile/password",
                JSONObject().put("current", current).put("next", next).put("device", deviceInfo(ctx)))
            // on the old master secret the caller needs a token of its own now
            val t = r.optString("token")
            if (t.isNotEmpty()) Cloud.setToken(ctx, t)
            refreshProfile(ctx)
            null
        }.getOrElse { errorText(it) }
    }

    suspend fun deleteProfile(ctx: Context, password: String): String? {
        if (password.isEmpty()) return "Type your password to confirm."
        return runCatching {
            Cloud.api(ctx, "DELETE", "/v1/profile", JSONObject().put("password", password))
            Cloud.forget(ctx)
            null
        }.getOrElse { errorText(it) }
    }

    suspend fun removeDevice(ctx: Context, id: String): String? = runCatching {
        Cloud.api(ctx, "DELETE", "/v1/profile/device/$id", null)
        refreshProfile(ctx)
        null
    }.getOrElse { errorText(it) }

    // ---------- TV sign-in ----------
    /** Signed-in side: approve the code a TV is showing. Returns (device name, null) or (null, error). */
    suspend fun approveTv(ctx: Context, codeRaw: String): Pair<String?, String?> {
        val code = codeRaw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (code.length != 6) return null to "Enter the 6-character code from the TV."
        return runCatching {
            val r = Cloud.api(ctx, "POST", "/v1/tv/approve", JSONObject().put("code", code))
            refreshProfile(ctx)
            (r.optJSONObject("device")?.optString("name")?.ifEmpty { null } ?: "The TV") to null
        }.getOrElse { null to errorText(it) }
    }

    /** TV side: ask for a code to show. */
    suspend fun tvStart(ctx: Context): TvCode? = runCatching {
        val r = Cloud.api(ctx, "POST", "/v1/tv", JSONObject().put("device", deviceInfo(ctx)), auth = false)
        TvCode(r.getString("code"), r.getString("poll"), System.currentTimeMillis() + r.optLong("ttl", 600) * 1000)
    }.getOrNull()

    /** TV side: one poll. "pending", "done" (signed in), or "expired". */
    suspend fun tvPoll(ctx: Context, tv: TvCode): String {
        if (System.currentTimeMillis() > tv.until) return "expired"
        return runCatching {
            val r = Cloud.api(ctx, "GET", "/v1/tv/${tv.poll}", null, auth = false)
            if (r.optBoolean("pending")) "pending" else { Cloud.adopt(ctx, r, fresh = false); "done" }
        }.getOrElse { if ((it as? Cloud.HttpFail)?.code == 404) "expired" else "pending" }
    }
}
