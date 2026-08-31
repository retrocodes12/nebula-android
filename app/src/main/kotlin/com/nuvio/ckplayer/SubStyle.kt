package com.nuvio.ckplayer

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.mutableStateOf
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import org.json.JSONObject

/**
 * Subtitle appearance — stored as the web player's `sub_style` document, option
 * KEYS not values ({size:"large", color:"yellow", …}), so one synced style
 * follows the user across web, TV, desktop and Android. The mapping from keys
 * to Media3's CaptionStyleCompat lives here and only here.
 */
object SubStyle {
    private const val PREFS = "ckplayer"
    val ORDER = listOf("size", "color", "bg", "edge", "font", "pos")
    val LABELS = mapOf(
        "size" to "Size", "color" to "Colour", "bg" to "Background",
        "edge" to "Edge", "font" to "Font", "pos" to "Position",
    )
    val VALUE_LABELS = mapOf(
        "small" to "Small", "normal" to "Normal", "large" to "Large", "xl" to "Extra large", "huge" to "Huge",
        "white" to "White", "yellow" to "Yellow", "cyan" to "Cyan", "green" to "Green",
        "dark" to "Dark", "light" to "Translucent", "none" to "None",
        "shadow" to "Shadow", "outline" to "Outline",
        "sans" to "Sans-serif", "serif" to "Serif", "mono" to "Monospace",
        "bottom" to "Bottom", "raised" to "Raised", "high" to "High", "centre" to "Centre",
    )

    private val SIZE = listOf("small" to 0.75f, "normal" to 1f, "large" to 1.3f, "xl" to 1.65f, "huge" to 2f)
    val COLOR = listOf(
        "white" to 0xFFFFFFFF.toInt(), "yellow" to 0xFFFFE600.toInt(),
        "cyan" to 0xFF62F0FF.toInt(), "green" to 0xFF7DFF8A.toInt(),
    )
    val BG = listOf("dark" to 0xCC000000.toInt(), "light" to 0x73000000.toInt(), "none" to 0x00000000)
    private val EDGE = listOf(
        "shadow" to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
        "outline" to CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        "none" to CaptionStyleCompat.EDGE_TYPE_NONE,
    )
    private val FONT = listOf<Pair<String, Typeface>>(
        "sans" to Typeface.SANS_SERIF, "serif" to Typeface.SERIF, "mono" to Typeface.MONOSPACE,
    )
    private val POS = listOf("bottom" to 0.08f, "raised" to 0.16f, "high" to 0.26f, "centre" to 0.45f)
    private val DEFAULT = mapOf(
        "size" to "normal", "color" to "white", "bg" to "dark",
        "edge" to "shadow", "font" to "sans", "pos" to "bottom",
    )

    /** Bumped on every change (local or synced-in) so an open player restyles live. */
    val version = mutableStateOf(0)

    fun options(k: String): List<String> = when (k) {
        "size" -> SIZE.map { it.first }
        "color" -> COLOR.map { it.first }
        "bg" -> BG.map { it.first }
        "edge" -> EDGE.map { it.first }
        "font" -> FONT.map { it.first }
        else -> POS.map { it.first }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The normalized style: every key present, unknown values fall to defaults. */
    fun get(ctx: Context): Map<String, String> {
        val o = runCatching { JSONObject(prefs(ctx).getString("sub_style", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        return ORDER.associateWith { k ->
            o.optString(k).takeIf { it in options(k) } ?: DEFAULT.getValue(k)
        }
    }

    fun at(ctx: Context): Long = prefs(ctx).getLong("sub_style_at", 0L)

    private fun save(ctx: Context, style: Map<String, String>, at: Long, fromSync: Boolean) {
        val o = JSONObject()
        style.forEach { (k, v) -> o.put(k, v) }
        prefs(ctx).edit().putString("sub_style", o.toString()).putLong("sub_style_at", at).apply()
        version.value++
        if (!fromSync) Cloud.noteChanged(ctx, "sub_style")
    }

    /** Advance one option to its next value; returns the new value key. */
    fun cycle(ctx: Context, k: String): String {
        val cur = get(ctx).toMutableMap()
        val opts = options(k)
        val next = opts[(opts.indexOf(cur.getValue(k)) + 1) % opts.size]
        cur[k] = next
        save(ctx, cur, System.currentTimeMillis(), fromSync = false)
        return next
    }

    fun reset(ctx: Context) = save(ctx, DEFAULT, System.currentTimeMillis(), fromSync = false)

    /** A synced-in style (already known to be newer) replaces the local one. */
    fun applyRemote(ctx: Context, style: JSONObject, at: Long) {
        val m = ORDER.associateWith { k ->
            style.optString(k).takeIf { it in options(k) } ?: DEFAULT.getValue(k)
        }
        save(ctx, m, at, fromSync = true)
    }

    /** Has the user (here or on a synced device) ever chosen a style? */
    fun configured(ctx: Context): Boolean = at(ctx) > 0L

    /**
     * Push the current choices into a Media3 SubtitleView. Until the user has
     * actually configured something, the system's own caption preferences win —
     * CaptioningManager styles are an accessibility setting, and overriding
     * them with app defaults for people who never opened the panel would be a
     * regression, not a feature.
     */
    fun apply(ctx: Context, view: SubtitleView) {
        if (!configured(ctx)) {
            view.setUserDefaultStyle()
            view.setUserDefaultTextSize()
            view.setBottomPaddingFraction(0.08f)
            view.setApplyEmbeddedStyles(true)
            view.setApplyEmbeddedFontSizes(true)
            return
        }
        val s = get(ctx)
        view.setStyle(
            CaptionStyleCompat(
                COLOR.first { it.first == s.getValue("color") }.second,
                BG.first { it.first == s.getValue("bg") }.second,
                0x00000000,                                  // no window box behind the cue block
                EDGE.first { it.first == s.getValue("edge") }.second,
                0xFF000000.toInt(),
                FONT.first { it.first == s.getValue("font") }.second,
            )
        )
        view.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * sizeFactor(s.getValue("size"))
        )
        view.setBottomPaddingFraction(POS.first { it.first == s.getValue("pos") }.second)
        // a chosen style must actually show — embedded VTT colors/sizes would
        // silently win over everything the panel sets
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
    }

    /** The size multiplier behind a size key (the preview scales with it too). */
    fun sizeFactor(key: String): Float = SIZE.first { it.first == key }.second
}
