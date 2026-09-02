package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Subtitle appearance — a live sample that reflects size, colour, background, font and
 * edge, and rows that cycle their value on tap (or OK on a TV remote). The pieces are
 * shared by the Settings page (SubStylePanel) and the Style column of the in-player
 * Subtitles panel.
 */

/** The sample line every appearance choice is reflected in. */
@Composable
internal fun SubStylePreview(style: Map<String, String>, modifier: Modifier = Modifier) {
    val fg = Color(SubStyle.COLOR.first { it.first == style.getValue("color") }.second)
    val sampleStyle = TextStyle(
        color = fg,
        fontSize = 13.sp * SubStyle.sizeFactor(style.getValue("size")),
        fontFamily = when (style.getValue("font")) {
            "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace
            else -> FontFamily.SansSerif
        },
        shadow = when (style.getValue("edge")) {
            "shadow" -> Shadow(Color.Black, Offset(2f, 3f), blurRadius = 5f)
            "outline" -> Shadow(Color.Black, Offset(0f, 0f), blurRadius = 3f)
            else -> null
        },
    )
    Box(
        modifier.fillMaxWidth()
            .background(Color(0xFF2A3550), RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Subtitles will look like this",
            style = sampleStyle,
            modifier = Modifier.background(Color(SubStyle.BG.first { it.first == style.getValue("bg") }.second)),
        )
    }
}

/** The six cycling rows: size, colour, background, edge, font, position. */
@Composable
internal fun SubStyleRows(ctx: Context, style: Map<String, String>, modifier: Modifier = Modifier) {
    Column(modifier) {
        SubStyle.ORDER.forEach { k ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    // focus must be visible from the couch: same border language
                    // as every other focusable in the app
                    .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(interactionSource = interaction, indication = null) { SubStyle.cycle(ctx, k) }
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(SubStyle.LABELS.getValue(k), color = TextC, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    "‹ " + (SubStyle.VALUE_LABELS[style.getValue(k)] ?: style.getValue(k)) + " ›",
                    color = if (focused) TextC else MutedC, fontSize = 13.sp,
                )
            }
        }
    }
}

/** The Settings › Subtitle style page: preview, rows, Reset and Done in one hairline card. */
@Composable
internal fun SubStylePanel(onDone: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_EXPRESSION") SubStyle.version.value   // recompose on cycle
    val style = SubStyle.get(ctx)

    Column(
        Modifier
            .width(288.dp)
            .background(SurfaceC, RoundedCornerShape(12.dp))
            .border(1.dp, Line2, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "SUBTITLE APPEARANCE", color = MutedC, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp,
        )
        SubStylePreview(style, Modifier.padding(top = 10.dp, bottom = 6.dp))
        SubStyleRows(ctx, style)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("Reset", false) { SubStyle.reset(ctx) }
            Spacer(Modifier.weight(1f))
            Chip("Done", true, onClick = onDone)
        }
    }
}
