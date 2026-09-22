package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update card: release notes made plain, and which version counts as newer. */
class UpdatesTest {
    @Test fun cleanNotes_markdownBecomesPlainText() {
        val raw = "## What's new\r\n- Fixed [the stall pill](https://github.com/x/y/pull/1)\r\n\r\n\r\n\r\n**Faster** `startup`"
        assertEquals("What's new\n• Fixed the stall pill\n\nFaster startup", Updates.cleanNotes(raw))
    }

    @Test fun cleanNotes_imagesGo() {
        assertEquals("Hello", Updates.cleanNotes("![screenshot](https://x/y.png) Hello"))
    }

    @Test fun isNewer_comparesNumbersNotText() {
        assertTrue(Updates.isNewer("1.10.0", "1.9.0"))       // as text "1.10.0" < "1.9.0"
        assertTrue(Updates.isNewer("1.79.0", "1.78.0"))
        assertFalse(Updates.isNewer("1.78.0", "1.78.0"))
        assertFalse(Updates.isNewer("1.77.9", "1.78.0"))
    }

    @Test fun isNewer_missingPartsCountAsZero() {
        assertFalse(Updates.isNewer("1.78", "1.78.0"))
        assertTrue(Updates.isNewer("1.78.1", "1.78"))
    }
}
