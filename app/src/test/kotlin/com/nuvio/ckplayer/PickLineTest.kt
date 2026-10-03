package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 2026-10-03 "Pick the line you just heard": the lines, `here`, the window, the badges, the timing a pick sets. */
class PickLineTest {
    private fun seg(a: Long, b: Long, vararg t: String) = PickSeg(a, b, t.toList())
    private fun line(a: Long, b: Long, t: String = "x") = PickLine(a, b, t)

    @Test fun clean_stripsTagsAndCollapsesSpace() {
        assertEquals("Hello there", PickLines.clean("<i>Hello</i>\n  there"))
        assertEquals("Where are you going?", PickLines.clean("{\\an8}Where are\\Nyou going?"))
        assertEquals("a b", PickLines.clean(" a \tb "))
        assertEquals("", PickLines.clean(null))
        assertEquals("", PickLines.clean("<b></b>"))
    }

    @Test fun lines_sortsDropsEmptyAndRejoinsSplitCues() {
        val got = PickLines.lines(listOf(
            seg(5000, 6000, "Second"),
            seg(1000, 2000, "First"),
            seg(2500, 2500, "Zero length"),
            seg(3000, 4000, "<i></i>"),
            // a WebVTT pair that overlaps: three pieces of time, two lines
            seg(7000, 8000, "Long line"),
            seg(8000, 9000, "Long line", "Short one"),
            seg(9000, 9500, "Short one"),
        ))
        assertEquals(listOf("First", "Second", "Long line", "Short one"), got.map { it.text })
        assertEquals(7000L, got[2].startMs); assertEquals(9000L, got[2].endMs)
        assertEquals(8000L, got[3].startMs); assertEquals(9500L, got[3].endMs)
        // the same words again later are a line of their own
        val again = PickLines.lines(listOf(seg(1000, 2000, "Yes."), seg(10_000, 11_000, "Yes.")))
        assertEquals(2, again.size)
    }

    @Test fun here_isTheLastLineStartedByTheAnchorInShownTime() {
        val ls = listOf(line(1000, 2000), line(5000, 6000), line(9000, 10_000))
        assertEquals(0, PickLines.here(ls, 500, 0))            // before every line: the first
        assertEquals(1, PickLines.here(ls, 5000, 0))           // exactly at a start counts
        assertEquals(1, PickLines.here(ls, 8999, 0))
        assertEquals(2, PickLines.here(ls, 60_000, 0))
        // shown = file + offset: 3 s later puts the second line's shown start at 8 s
        assertEquals(0, PickLines.here(ls, 7000, 3000))
        assertEquals(1, PickLines.here(ls, 8000, 3000))
        assertEquals(0, PickLines.here(emptyList(), 1000, 0))
    }

    @Test fun window_spansThirtyEachSideAndGrows() {
        assertEquals(0 to 31, PickLines.window(0, 500))
        assertEquals(70 to 131, PickLines.window(100, 500))
        assertEquals(469 to 500, PickLines.window(499, 500))
        assertEquals(0 to 5, PickLines.window(2, 5))
        assertEquals(40, PickLines.earlier(70)); assertEquals(0, PickLines.earlier(20))
        assertEquals(161, PickLines.later(131, 500)); assertEquals(500, PickLines.later(480, 500))
    }

    @Test fun badge_saysWhereTheLineSits() {
        val l = line(10_000, 12_000)
        assertEquals("On screen", PickLines.badge(l, 11_000, 0))
        assertEquals("On screen", PickLines.badge(l, 12_000, 0))
        assertEquals("12 s ago", PickLines.badge(l, 22_000, 0))
        assertEquals("in 4 s", PickLines.badge(l, 6_000, 0))
        assertEquals("1:05 ago", PickLines.badge(l, 75_000, 0))
        assertEquals("in 2:00", PickLines.badge(l, -110_000, 0))
        // never "0 s": a line starting 0.3 s after the anchor reads "in 1 s"
        assertEquals("in 1 s", PickLines.badge(l, 9_700, 0))
        // in the timing as it is: 5 s earlier shows it 5..7 s
        assertEquals("On screen", PickLines.badge(l, 6_000, -5000))
    }

    @Test fun offsetFor_endsThePickedLineAtTheAnchor() {
        val l = line(10_000, 12_000)
        assertEquals(5300L, PickLines.offsetFor(17_300, l))   // said at 17.3 s, the file ends it at 12 s: 5.3 s later
        assertEquals(-4000L, PickLines.offsetFor(8_000, l))
        assertEquals(0L, PickLines.offsetFor(12_040, l))       // under 0.05 s is none
        assertEquals(100L, PickLines.offsetFor(12_060, l))
        assertEquals(5300L, PickLines.offsetFor(17_349, l))
        assertEquals(5400L, PickLines.offsetFor(17_350, l))
        assertEquals(0L, PickLines.round100(-40))
        assertEquals(-1300L, PickLines.round100(-1260))
    }

    @Test fun movedText_wordsTheChange() {
        assertEquals("Subtitles moved 5.3 s earlier.", PickLines.movedText(-5300))
        assertEquals("Subtitles moved 0.4 s later.", PickLines.movedText(400))
        assertEquals("The subtitles were already in time.", PickLines.movedText(0))
        assertEquals("The subtitles were already in time.", PickLines.movedText(-49))
        assertEquals("Paused here. Pick the line that was just said — the subtitles move to match it.", PickLines.note(true))
        assertEquals("Pick the line that was just said — the subtitles move to match it.", PickLines.note(false))
    }
}
