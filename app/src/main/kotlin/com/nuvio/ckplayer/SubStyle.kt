@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import org.json.JSONObject

/**
 * Subtitle appearance — stored as the web player's `sub_style` document, option
 * KEYS not values ({size:"large", color:"yellow", …}), so one synced style
 * follows the user across web, TV, desktop and Android. The mapping from keys
 * to Media3's CaptionStyleCompat lives here and only here.
 *
 * One Position is NOT in that document: Black bars ([BARS]) belongs to this device, beside the picture size. Every other
 * app turns a position it does not know into Bottom when it reads the document (web, webOS, desktop, Apple and every
 * Android before this one), so a synced "bars" would be dropped on any of them and come back as Bottom the next time
 * that app saved a style. Kept here instead, nothing another app does can reset it, and the document stays as it was.
 */
object SubStyle {
    private const val PREFS = "ckplayer"
    const val BARS = "bars"
    private const val BARS_KEY = "pref_subbars"                // `pref_`: Prefs.resetAll clears it with the others
    private const val CHROME_PAD = 0.32f                       // while the chrome is up, cues lift clear of the lower third
    val ORDER = listOf("size", "color", "bg", "edge", "font", "pos", "bold")
    val LABELS = mapOf(
        "size" to "Size", "color" to "Colour", "bg" to "Background",
        "edge" to "Edge", "font" to "Font", "pos" to "Position", "bold" to "Bold",
    )
    val VALUE_LABELS = mapOf(
        "off" to "Off", "on" to "On",
        "small" to "Small", "normal" to "Normal", "large" to "Large", "xl" to "Extra large", "huge" to "Huge",
        "white" to "White", "yellow" to "Yellow", "cyan" to "Cyan", "green" to "Green",
        "dark" to "Dark", "light" to "Translucent", "none" to "None",
        "shadow" to "Shadow", "outline" to "Outline",
        "sans" to "Sans-serif", "serif" to "Serif", "mono" to "Monospace",
        "bottom" to "Bottom", "raised" to "Raised", "high" to "High", "centre" to "Centre", BARS to "Black bars",
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
    private val BOLD = listOf("off", "on")
    private val DEFAULT = mapOf(
        "size" to "normal", "color" to "white", "bg" to "dark",
        "edge" to "shadow", "font" to "sans", "pos" to "bottom", "bold" to "off",
    )

    /** Bumped on every change (local or synced-in) so an open player restyles live. */
    val version = mutableStateOf(0)

    fun options(k: String): List<String> = when (k) {
        "size" -> SIZE.map { it.first }
        "color" -> COLOR.map { it.first }
        "bg" -> BG.map { it.first }
        "edge" -> EDGE.map { it.first }
        "font" -> FONT.map { it.first }
        "bold" -> BOLD
        else -> POS.map { it.first } + BARS
    }

    /** What the synced document may hold for a key: everything but Black bars. */
    private fun syncable(k: String): List<String> = if (k == "pos") POS.map { it.first } else options(k)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The style as the synced document holds it: every key present, unknown values fall to defaults. */
    fun synced(ctx: Context): Map<String, String> {
        val o = runCatching { JSONObject(prefs(ctx).getString("sub_style", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        return ORDER.associateWith { k ->
            o.optString(k).takeIf { it in syncable(k) } ?: DEFAULT.getValue(k)
        }
    }

    /** The style this device shows: the synced one, with Black bars as its Position when chosen here. */
    fun get(ctx: Context): Map<String, String> =
        if (barsOn(ctx)) synced(ctx) + ("pos" to BARS) else synced(ctx)

    fun barsOn(ctx: Context): Boolean = prefs(ctx).getBoolean(BARS_KEY, false)

    fun at(ctx: Context): Long = prefs(ctx).getLong("sub_style_at", 0L)

    private fun save(ctx: Context, style: Map<String, String>, at: Long, fromSync: Boolean) {
        val o = JSONObject()
        style.forEach { (k, v) -> o.put(k, v) }
        prefs(ctx).edit().putString("sub_style", o.toString()).putLong("sub_style_at", at).apply()
        version.value++
        if (!fromSync) Cloud.noteChanged(ctx, "sub_style")
    }

    /** Move one option to its next value (or, with [step] −1, the one before); returns the new value key. */
    fun cycle(ctx: Context, k: String, step: Int = 1): String {
        val opts = options(k)
        val next = opts[Math.floorMod(opts.indexOf(get(ctx).getValue(k)) + step, opts.size)]
        if (k == "pos") {
            prefs(ctx).edit().putBoolean(BARS_KEY, next == BARS).apply()
            if (next == BARS) { version.value++; return next }   // this device's own: nothing to save or sync
        }
        save(ctx, synced(ctx) + (k to next), System.currentTimeMillis(), fromSync = false)
        return next
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit().remove(BARS_KEY).apply()
        save(ctx, DEFAULT, System.currentTimeMillis(), fromSync = false)
    }

    /** A synced-in style (already known to be newer) replaces the local one. */
    fun applyRemote(ctx: Context, style: JSONObject, at: Long) {
        val m = ORDER.associateWith { k ->
            style.optString(k).takeIf { it in syncable(k) } ?: DEFAULT.getValue(k)
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
            view.setBottomPaddingFraction(restingPad(ctx))
            view.setApplyEmbeddedStyles(true)
            view.setApplyEmbeddedFontSizes(true)
            return
        }
        val s = synced(ctx)
        val face = FONT.first { it.first == s.getValue("font") }.second
        view.setStyle(
            CaptionStyleCompat(
                COLOR.first { it.first == s.getValue("color") }.second,
                BG.first { it.first == s.getValue("bg") }.second,
                0x00000000,                                  // no window box behind the cue block
                EDGE.first { it.first == s.getValue("edge") }.second,
                0xFF000000.toInt(),
                if (s["bold"] == "on") Typeface.create(face, Typeface.BOLD) else face,   // Bold: the same face, heavier
            )
        )
        view.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * sizeFactor(s.getValue("size"))
        )
        view.setBottomPaddingFraction(restingPad(ctx))
        // a chosen style must actually show — embedded VTT colors/sizes would
        // silently win over everything the panel sets
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
    }

    /** The size multiplier behind a size key (the preview scales with it too). */
    fun sizeFactor(key: String): Float = SIZE.first { it.first == key }.second

    /** Where the lines rest with the chrome away. Black bars with no bar on this film rests where Bottom does. */
    private fun restingPad(ctx: Context): Float =
        if (!configured(ctx) || barsOn(ctx)) POS.first().second
        else POS.first { it.first == synced(ctx).getValue("pos") }.second

    /**
     * Put [views] (the player's two subtitle views, in the picture's frame) where the lines belong now: lifted clear of
     * the chrome ([chromeUp]), down on the bar for Black bars, else at their resting place. A top-placed line or a
     * picture subtitle moves down with the view — it stays on screen, the move is never more than the bar.
     */
    fun place(ctx: Context, views: List<SubtitleView>, screen: View?, chromeUp: Boolean) {
        val frame = views.firstOrNull()?.parent as? View
        val spot = if (!chromeUp && barsOn(ctx) && screen != null && frame != null && frame !== screen)
            SubPlace.bars(screen.height, frame.bottom, frame.height, Account.isTv(ctx)) else null
        views.forEach { v ->
            v.translationY = (spot?.shift ?: 0).toFloat()
            v.setBottomPaddingFraction(spot?.pad ?: if (chromeUp) CHROME_PAD else restingPad(ctx))
        }
    }

    /** Style the stream's own subtitle view, add the second one for add-on subtitles and keep both in place; returns the second. */
    fun mount(ctx: Context, pv: PlayerView, chromeUp: () -> Boolean): SubtitleView {
        pv.subtitleView?.let { apply(ctx, it) }
        // add-on subtitles (loadPick) draw on a second SubtitleView just above the stream's own, inside the
        // picture's frame — so they size and sit exactly as those do (spread over the whole screen they
        // were 3.6x larger on an upright phone, and sat in a wide film's black bar)
        val own = pv.subtitleView
        val frame = own?.parent as? ViewGroup ?: pv
        val over = SubtitleView(ctx).apply { isFocusable = false; SubStyle.apply(ctx, this) }
        frame.addView(
            over, if (own != null && own.parent === frame) frame.indexOfChild(own) + 1 else frame.childCount,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        // Black bars moves both past the frame's edge. The frame must not cut them off there, and neither may the player
        // above it: a view group clips each child to that child's own bounds, so the frame's overflow needs both to let go
        if (frame !== pv) { frame.clipChildren = false; pv.clipChildren = false }
        // Fill makes the picture's frame larger than the screen (it is centred, its edges cut off): both subtitle
        // views live in that frame, so they are pulled back inside the screen by the overhang, or a 4:3 film's
        // lines would sit below the bottom edge
        frame.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (frame === pv) return@addOnLayoutChangeListener
            val ox = maxOf(0, ((r - l) - pv.width) / 2)
            val oy = maxOf(0, ((b - t) - pv.height) / 2)
            val views = listOfNotNull(own, over)
            views.forEach { v ->
                if (v.paddingLeft != ox || v.paddingTop != oy) v.post { v.setPadding(ox, oy, ox, oy) }
            }
            place(ctx, views, pv, chromeUp())                   // the film's shape arrived, or the screen turned
        }
        return over
    }
}
