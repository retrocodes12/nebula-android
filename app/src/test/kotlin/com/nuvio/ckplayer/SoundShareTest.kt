package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Convert sound for your TV: the converter's command and the server's small parts. */
class SoundShareTest {
    @Test fun args_dtsInFromThePipe_dolbyDigitalPlusOutInFragments() {
        val a = SoundShare.soundArgs(6)
        assertEquals(listOf("-f", "dts", "-i", "pipe:0"), a.subList(a.indexOf("-f"), a.indexOf("-f") + 4))
        assertTrue(a.containsAll(listOf("-c:a", "eac3", "640k", "pipe:1", "frag_keyframe+delay_moov+default_base_moof")))
        assertFalse("5.1 stays 5.1", a.contains("-ac"))
        // nothing in the command reaches out: the input is the pipe, the output the pipe
        assertFalse(a.any { it.startsWith("http") || it.startsWith("/") })
    }

    @Test fun args_sevenOneIsFoldedToSix_stereoIsSmaller_unknownIsTreatedAsSurround() {
        assertEquals("6", SoundShare.soundArgs(8).let { it[it.indexOf("-ac") + 1] })
        assertTrue(SoundShare.soundArgs(2).contains("256k"))
        assertTrue(SoundShare.soundArgs(0).containsAll(listOf("640k", "-ac")))
    }

    @Test fun query_decodesWhatTheTvSends() {
        val q = SoundShare.query("id=ab12&k=cd%20ef&end=1&empty=")
        assertEquals("ab12", q["id"]); assertEquals("cd ef", q["k"]); assertEquals("1", q["end"]); assertEquals("", q["empty"])
        assertTrue(SoundShare.query("").isEmpty())
    }

    @Test fun token_comparedWhole() {
        assertTrue(SoundShare.same("a".repeat(32), "a".repeat(32)))
        assertFalse(SoundShare.same("a".repeat(32), "a".repeat(31) + "b"))
        assertFalse(SoundShare.same("", "a".repeat(32)))
    }
}
