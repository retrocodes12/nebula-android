package com.nuvio.ckplayer

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * VLC's swipes on a phone's player: up/down on the left half = the screen's brightness (this window only, given back to
 * the phone's own setting when the player closes), on the right half = the media volume. A three-quarter-height swipe
 * covers the whole range. Gated with the other touch gestures (Settings › Playback › Touch gestures).
 */
internal class SwipeLevels(private val activity: Activity?) {
    enum class Kind { BRIGHTNESS, VOLUME }

    /** What the overlay shows: which level and where it is (0..1); null when nothing is being swiped. */
    var shown by mutableStateOf<Pair<Kind, Float>?>(null)
        private set
    /** Bumped when a swipe ends, so the overlay can fade a moment later. */
    var endedAt by mutableLongStateOf(0L)
        private set

    private val audio = activity?.getSystemService(AudioManager::class.java)
    private var kind: Kind? = null
    private var from = 0f
    private var moved = 0f
    private var brightnessTouched = false

    val active: Boolean get() = kind != null

    fun start(x: Float, width: Int) {
        val k = if (x < width / 2f) Kind.BRIGHTNESS else Kind.VOLUME
        if (k == Kind.VOLUME && audio == null) return
        if (k == Kind.BRIGHTNESS && activity == null) return
        kind = k; from = level(k); moved = 0f
        shown = k to from
    }

    /** [dy] in pixels, down positive (Compose's sign); up raises the level. */
    fun drag(dy: Float, height: Int) {
        val k = kind ?: return
        moved -= dy
        val v = (from + moved / (height * 0.75f)).coerceIn(0f, 1f)
        set(k, v)
        shown = k to v
    }

    fun end() {
        if (kind == null) return
        kind = null
        endedAt = System.currentTimeMillis()
    }

    fun hide() { if (kind == null) shown = null }

    /** The player is closing: the screen goes back to the phone's own brightness. */
    fun restore() {
        if (!brightnessTouched) return
        brightnessTouched = false
        activity?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            w.attributes = lp
        }
    }

    private fun level(k: Kind): Float = when (k) {
        Kind.BRIGHTNESS -> {
            val own = activity?.window?.attributes?.screenBrightness ?: -1f
            if (own >= 0f) own
            else runCatching {
                Settings.System.getInt(activity!!.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            }.getOrDefault(0.5f).coerceIn(0f, 1f)
        }
        Kind.VOLUME -> {
            val a = audio ?: return 0f
            val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            a.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        }
    }

    private fun set(k: Kind, v: Float) {
        when (k) {
            Kind.BRIGHTNESS -> activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = v.coerceAtLeast(0.01f)      // 0 would be a black screen on some panels
                w.attributes = lp
                brightnessTouched = true
            }
            Kind.VOLUME -> audio?.let { a ->
                val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val idx = (v * max).roundToInt().coerceIn(0, max)
                if (idx != a.getStreamVolume(AudioManager.STREAM_MUSIC))
                    runCatching { a.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0) }   // our own bar, not the system's
            }
        }
    }
}

/** The bar VLC shows while a level is swiped: an icon, a vertical track filled to the level, the percentage — on the
    side being swiped. Fades 0.7 s after the finger lifts. */
@Composable
internal fun SwipeLevelOverlay(levels: SwipeLevels, modifier: Modifier = Modifier) {
    val s = levels.shown ?: return
    LaunchedEffect(levels.endedAt) {
        if (levels.endedAt > 0L) { delay(700); levels.hide() }
    }
    val (kind, v) = s
    Box(modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = if (kind == SwipeLevels.Kind.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd) {
        Column(
            Modifier.padding(horizontal = 48.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                when {
                    kind == SwipeLevels.Kind.BRIGHTNESS -> Icons.Filled.BrightnessMedium
                    v <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp),
            )
            Box(Modifier.width(6.dp).height(140.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40FFFFFF)), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.width(6.dp).fillMaxHeight(v).background(Color.White))
            }
            Text("${(v * 100).roundToInt()}", color = Color.White, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
