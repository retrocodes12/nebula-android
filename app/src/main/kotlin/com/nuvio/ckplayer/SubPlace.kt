package com.nuvio.ckplayer

/**
 * Black bars (issue #2, nebula-player#8): where the subtitle views go when lines should sit at the true bottom of the
 * screen. Both views stay inside the picture's frame, so their size and their line breaks are the picture's as before
 * (spread over the whole screen they were 3.6x larger on an upright phone); they are only MOVED down, never by more than
 * the bar, so nothing inside the frame can leave the screen. Numbers in, numbers out: SubStyle.kt does the views.
 */
internal object SubPlace {
    /** Move the views down [shift] pixels and rest the lines [pad] of the frame's height above its bottom edge. */
    data class Spot(val shift: Int, val pad: Float)

    /** Under 1 % of the screen is rounding, not a bar. */
    private const val MIN_BAR = 0.01f
    /** The gap left under the last line, of the screen's height. A TV may hide a little of its edge. */
    private const val GAP_PHONE = 0.02f
    private const val GAP_TV = 0.035f

    /**
     * Null when there is no bar under the picture: the film fills the height (a 16:9 film, Stretch), its frame overhangs
     * the screen (Fill), or the sides are the black ones. The caller then rests the lines where Bottom does.
     */
    fun bars(screenH: Int, frameBottom: Int, frameH: Int, tv: Boolean): Spot? {
        if (screenH <= 0 || frameH <= 0) return null
        val bar = screenH - frameBottom
        if (bar < screenH * MIN_BAR) return null
        return Spot(bar, screenH * (if (tv) GAP_TV else GAP_PHONE) / frameH)
    }
}
