package com.nuvio.ckplayer

// A stream row that is not a stream: an add-on's link to a web page (the protocol's `externalUrl`), such as the sports
// add-on's "Watch an ad to unlock Live TV (12 h)" and its donation card on a locked channel. Those rows were dropped
// where the list is read, so a locked channel said "No playable streams right now" and gave no way in. The Streams page
// alone shows them, under the rows that play: a phone opens the page in its browser and asks the add-ons again when the
// viewer comes back; a TV shows the address as a code to scan with a phone, and Check again asks the add-ons again.

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject

/** A row that opens a web page: the add-on's own name and text, and the page's address (http or https only). */
data class StreamLink(val name: String, val title: String, val url: String)

object StreamLinks {
    /** The link row an add-on's stream object makes, or null: no web address, or neither a name nor a text to show. */
    fun of(s: JSONObject): StreamLink? {
        val url = webUrl(s.optString("externalUrl"))
        if (url.isEmpty()) return null
        val name = s.optString("name").trim()
        val text = s.optString("title").ifEmpty { s.optString("description") }.trim()
        if (name.isEmpty() && text.isEmpty()) return null
        return StreamLink(name.take(200), text.take(600), url)
    }

    /** The page in the device's browser. False when nothing here opens web pages (many TVs). */
    fun open(ctx: Context, url: String): Boolean = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    /** The address as a QR code's modules (no quiet zone: the drawing adds it), or null when it cannot be made. */
    fun qr(url: String): BitMatrix? = runCatching {
        QRCodeWriter().encode(
            url, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }.getOrNull()

    /** "sports.nebulapro.xyz": the host a TV names under the code, so the viewer knows where it goes. */
    fun host(url: String): String = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
}

/** A link row on the Streams page: the add-on's name for it, its text as written, and a mark that it opens a page. */
@Composable
internal fun StreamLinkRow(link: StreamLink, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FocusCard(
        shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth(),
        onClick = onOpen, zoom = false, interactionSource = interaction, landing = false,
    ) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceC, RoundedCornerShape(14.dp))
                .border(1.dp, LineC, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    link.name.ifEmpty { "Open the page" }, color = TextC, fontSize = 15.sp, fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (link.title.isNotEmpty()) Text(
                    link.title, color = MutedC, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 6,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Opens a web page",
                tint = if (focused) TextC else MutedC, modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A TV (or a phone with no browser): the link as a code to scan, and Check again for when the page has been seen. */
@Composable
internal fun StreamLinkSheet(link: StreamLink, onAgain: () -> Unit, onClose: () -> Unit) {
    val modules = remember(link.url) { StreamLinks.qr(link.url) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            withFrameNanos {}
            if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xD40A0A0C)), contentAlignment = Alignment.Center) {
            Row(
                Modifier.padding(24.dp).widthIn(max = 760.dp).clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141418)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (modules != null) QrModules(modules, Modifier.size(220.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "OPEN ON YOUR PHONE", color = MutedC, fontFamily = Mono, fontSize = tinySp(10f),
                        fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp,
                    )
                    Text(
                        link.name.ifEmpty { "Open the page" }, color = TextC, fontSize = 20.sp, fontFamily = Sans,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Point your phone's camera at the code. When you are done there, choose Check again.",
                        color = MutedC, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp),
                    )
                    StreamLinks.host(link.url).takeIf { it.isNotEmpty() }?.let { h ->
                        Text(h, color = FaintC, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Check again", false, modifier = Modifier.focusRequester(first)) { onAgain() }
                        Chip("Close", false) { onClose() }
                    }
                }
            }
        }
    }
}

/** Dark modules on a white card with its own quiet zone, so any phone camera reads it on a dark screen. */
@Composable
private fun QrModules(m: BitMatrix, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp)) {
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
