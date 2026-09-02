package com.nuvio.ckplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * In-app toasts: a small glass pill under the status bar, in the app's own material, instead of
 * the system toast (which floats grey over the bottom of the screen and ignores the accent).
 * `Toasts.show(text)` from anywhere; [ToastHost] sits once in AppRoot over every screen.
 */
internal object Toasts {
    /** The latest request: a stamp so the same text twice still shows twice. */
    var current by mutableStateOf<Pair<Long, String>?>(null)
        private set

    fun show(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) current = System.currentTimeMillis() to t.take(200)
    }
}

@Composable
internal fun ToastHost(modifier: Modifier = Modifier) {
    val cur = Toasts.current
    var shown by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var text by remember { mutableStateOf("") }          // kept through the fade-out
    LaunchedEffect(cur) {
        if (cur == null) return@LaunchedEffect
        shown = cur; text = cur.second
        delay(if (cur.second.length > 60) 3600L else 2200L)
        if (shown == cur) shown = null
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it / 2 },
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Row(
            Modifier.statusBarsPadding().padding(top = 10.dp).widthIn(max = 420.dp)
                .background(BarGlass, Pill).border(1.dp, Hairline, Pill)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(Red, CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(
                text, color = Ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, fontFamily = Sans,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
