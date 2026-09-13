package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * MDBList tracking — scrobble only: what plays here goes to the person's MDBList history; nothing comes back.
 * The shared player's `mdbl*` block, rule for rule: `start` when a film or episode plays (again after a pause, after
 * a real jump and every 5 min while it plays — how MDBList keeps the resume point), `pause` on pause, `stop` at the
 * end or on leaving; live streams and ids MDBList cannot place send nothing. Android calls MDBList directly — the
 * web builds go through the cloud's relay only because a browser cannot.
 * The key is the synced doc `mdblist` {key, user, at}: newest wins across a profile's devices, so a disconnect
 * (key "") reaches them all.
 */
object Mdblist {
    private const val API = "https://api.mdblist.com"
    private const val PREF = "mdblist_v1"
    const val BEAT_MS = 5 * 60_000L
    private val KEY_RE = Regex("[A-Za-z0-9_-]{8,128}")
    private val JSON_MT = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Compose state, so Settings redraws when a sync brings a key in or takes it away. */
    var key by mutableStateOf(""); private set
    var user by mutableStateOf(""); private set
    private var at = 0L
    private var loaded = false
    @Volatile private var warned = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(prefs(ctx).getString(PREF, null) ?: return)
            key = o.optString("key"); user = o.optString("user"); at = o.optLong("at")
        }
    }

    fun at(ctx: Context): Long { load(ctx); return at }
    fun validKey(k: String) = KEY_RE.matches(k)

    private fun store(ctx: Context, k: String, u: String, stamp: Long) {
        key = k; user = u.take(60); at = stamp
        prefs(ctx).edit().putString(PREF, doc(ctx)).apply()
    }

    /** Connect (a checked key) or disconnect (""), stamped now so it wins on every other device. */
    fun set(ctx: Context, k: String, u: String) {
        load(ctx)
        store(ctx, k, u, System.currentTimeMillis())
        warned = false
        Cloud.noteChanged(ctx, "mdblist")
    }

    internal fun doc(ctx: Context): String {
        load(ctx)
        return JSONObject().put("key", key).put("user", user).put("at", at).toString()
    }

    /** Newest wins. A malformed doc pushes our copy over it rather than wedging the key. */
    internal fun merge(ctx: Context, remote: JSONObject): Pair<Boolean, Boolean> {
        load(ctx)
        val rk = remote.opt("key") as? String ?: return false to (at > 0)
        val rAt = remote.optLong("at")
        if (rAt > at) {
            store(ctx, rk, remote.optString("user"), rAt)
            warned = false
            return true to false
        }
        return false to (at > rAt)
    }

    /** Asks MDBList who a key belongs to: (true, the user name — may be "") or (false, why, in words). */
    suspend fun verify(k: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$API/user".toHttpUrl().newBuilder().addQueryParameter("apikey", k).build())
                .header("Accept", "application/json")
                .build()
            http.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> {
                        val o = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull()
                        val name = listOf("username", "user_name", "name").map { o?.optString(it).orEmpty() }.firstOrNull { it.isNotEmpty() }
                        true to name.orEmpty().take(60)
                    }
                    r.code == 401 || r.code == 403 -> false to "MDBList did not accept that key."
                    r.code == 429 -> false to "Too many tries — wait a minute and try again."
                    else -> false to "Could not reach MDBList. Try again."
                }
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            false to "Could not reach MDBList. Try again."
        }
    }

    /** "tt0111161" → {imdb}, "tmdb:278" / "kitsu:1" / "tvdb:81189" → {tmdb|kitsu|tvdb: n}; null for anything else. */
    private fun ids(v: String): JSONObject? {
        if (Regex("tt\\d{5,10}").matches(v)) return JSONObject().put("imdb", v)
        val m = Regex("(tmdb|kitsu|tvdb):([1-9]\\d{0,9})").matchEntire(v) ?: return null
        return JSONObject().put(m.groupValues[1], m.groupValues[2].toLong())
    }

    /** What MDBList knows a title by: {movie:{ids}} or {show:{ids, season:{number, episode:{number}}}}; null for a
        channel, an add-on's own id, a kitsu series (MDBList places shows by imdb/tmdb/tvdb) or an episode id with
        no season in it. */
    internal fun item(type: String, id: String): JSONObject? {
        if (type == "movie") {
            val i = ids(id) ?: return null
            if (i.has("tvdb")) return null
            return JSONObject().put("movie", JSONObject().put("ids", i))
        }
        if (type != "series") return null
        val root = seriesIdOf(id)
        if (root == id) return null
        val i = ids(root) ?: return null
        if (i.has("kitsu")) return null
        val (se, ep) = episodeNumbersOf(id) ?: return null
        val s = se?.takeIf { Regex("\\d{1,5}").matches(it) }?.toInt() ?: return null
        val e = ep.takeIf { Regex("\\d{1,5}").matches(it) }?.toInt() ?: return null
        return JSONObject().put(
            "show",
            JSONObject().put("ids", i).put("season", JSONObject().put("number", s).put("episode", JSONObject().put("number", e))),
        )
    }

    fun canTrack(type: String?, id: String?) = key.isNotEmpty() && type != null && id != null && item(type, id) != null

    /** One event for (type, id) at pct percent, off the main thread. A refused key says so once. */
    fun scrobble(ctx: Context, event: String, type: String?, id: String?, pct: Double, version: String) {
        load(ctx)
        val k = key
        if (k.isEmpty() || type == null || id == null) return
        val body = item(type, id) ?: return
        body.put("progress", (pct.coerceIn(0.0, 100.0) * 100).roundToLong() / 100.0)
        if (version.isNotEmpty()) body.put("app_version", version)
        scope.launch {
            runCatching {
                val req = Request.Builder()
                    .url("$API/scrobble/$event".toHttpUrl().newBuilder().addQueryParameter("apikey", k).build())
                    .header("Accept", "application/json")
                    .post(body.toString().toRequestBody(JSON_MT))
                    .build()
                http.newCall(req).execute().use { r ->
                    if ((r.code == 401 || r.code == 403) && !warned) {
                        warned = true
                        withContext(Dispatchers.Main) {
                            Toasts.show("MDBList turned the key down — connect it again in Settings › Playback.")
                        }
                    }
                }
            }
        }
    }
}

/** One player's tracking state: the item MDBList was told about and what it was told last. */
internal class MdblSession {
    var type: String? = null
    var id: String? = null
    var started = false      // a start went out for this item and no stop yet
    var on = false           // MDBList thinks it is playing
    var sentAt = 0L          // when the last event went out …
    var sentPos = 0L         // … and where the video was then (ms)
    var seekAt = 0L          // a jump waiting to be judged and reported
    var lastPct = -1.0       // for the stop sent after the player moved on in place
}

/** Settings › Playback › Tracking: a key field + Connect, or who is connected + Disconnect (the web row's twin). */
@Composable
internal fun MdblistSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { Mdblist.load(ctx); true }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun connect() {
        val k = draft.trim()
        if (!Mdblist.validKey(k)) { Toasts.show("That does not look like an MDBList API key."); return }
        busy = true
        scope.launch {
            val (ok, said) = Mdblist.verify(k)
            busy = false
            if (ok) {
                Mdblist.set(ctx, k, said); draft = ""
                Toasts.show("MDBList connected" + (if (said.isNotEmpty()) " — $said" else "") + ".")
            } else Toasts.show(said)
        }
    }
    SettingsHeader("TRACKING", "What you watch, kept on MDBList")
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("Track on MDBList", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val on = Mdblist.key.isNotEmpty()
            Text(
                if (on) "Connected" + (if (Mdblist.user.isNotEmpty()) " as ${Mdblist.user}" else "") +
                    " · Films and episodes you watch go to your history there" +
                    (if (Cloud.linked(ctx)) " · Every device on your profile tracks too" else "")
                else "Adds the films and episodes you watch to your history there · Paste the API key from mdblist.com › Preferences",
                color = MutedC, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp),
            )
            if (on) {
                Row(Modifier.padding(top = 6.dp)) {
                    TextAction("Disconnect") { Mdblist.set(ctx, "", ""); Toasts.show("MDBList disconnected.") }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it.trim().take(128) },
                        placeholder = { Text("API key", color = MutedC) },
                        singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { connect() }),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Line2,
                            cursorColor = Red,
                            focusedTextColor = TextC,
                            unfocusedTextColor = TextC,
                        ),
                    )
                    Button(
                        onClick = { connect() }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(if (busy) "Checking…" else "Connect", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
