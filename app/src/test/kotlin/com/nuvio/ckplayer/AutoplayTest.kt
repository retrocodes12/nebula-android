package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Autoplay's rules (Autoplay.kt; the shared player's shelf mode): the best row, when to stop waiting, a debrid clip. */
class AutoplayTest {
    private val comet = Addon("https://comet.example/manifest.json", "Comet | TB", "https://comet.example")
    private val plain = Addon("https://plain.example/manifest.json", "Plain", "https://plain.example")
    private fun row(name: String, title: String = "", url: String = "https://h.example/" + name.hashCode(), a: Addon = plain) =
        StreamItem(name, title, url) to a
    private val none: (StreamItem) -> Boolean = { false }
    private fun names(rows: List<Pair<StreamItem, Addon>>) = rows.map { it.first.name }

    @Test fun debridMarks_readyWaitAndButtons() {
        assertEquals(Autoplay.Debrid.READY, Autoplay.debrid("[TB⚡] Comet 2160p", ""))
        assertEquals(Autoplay.Debrid.READY, Autoplay.debrid("[RD+] Torrentio 1080p", ""))
        assertEquals(Autoplay.Debrid.WAIT, Autoplay.debrid("[TB⬇️] Comet 2160p", ""))
        assertEquals(Autoplay.Debrid.WAIT, Autoplay.debrid("[TB download] Torrentio", ""))
        assertEquals(Autoplay.Debrid.ACTION, Autoplay.debrid("[TB🔄] Comet Sync", ""))
        assertEquals(Autoplay.Debrid.ACTION, Autoplay.debrid("Comet", "https://x.example/debrid-sync/abc"))
        assertEquals(Autoplay.Debrid.NONE, Autoplay.debrid("Torrentio 4K", ""))
    }

    @Test fun instant_torBoxBeforeOtherServices() {
        assertEquals(2, Autoplay.instant(StreamItem("[TB⚡] Comet 1080p", "", "u")))
        assertEquals(1, Autoplay.instant(StreamItem("[RD⚡] Comet 1080p", "", "u")))
        assertEquals(0, Autoplay.instant(StreamItem("[TB⬇️] Comet 1080p", "", "u")))
        assertEquals(0, Autoplay.instant(StreamItem("Plain 4K", "", "u")))
    }

    @Test fun debridAddonNames() {
        assertTrue(Autoplay.debridAddon("Comet | ElfHosted | TB"))
        assertTrue(Autoplay.debridAddon("Torrentio RD"))
        assertTrue(Autoplay.debridAddon("TorBox Search"))
        assertFalse(Autoplay.debridAddon("Cinemeta"))
        assertFalse(Autoplay.debridAddon("TBS Sports"))
    }

    @Test fun rank_dropsHeldAndButtons_thenInstantThenQuality() {
        val rows = listOf(
            row("Plain 4K"), row("[TB⬇️] Comet 4K", a = comet), row("[TB🔄] Comet Sync", a = comet),
            row("[RD⚡] Other 1080p"), row("[TB⚡] Comet 1080p", a = comet), row("Plain 720p"),
        )
        assertEquals(listOf("[TB⚡] Comet 1080p", "[RD⚡] Other 1080p", "Plain 4K", "Plain 720p"),
            names(Autoplay.rank(rows, Autoplay.Cfg(), none, none)))
    }

    @Test fun rank_instantOff_qualityLeads() {
        val rows = listOf(row("[TB⚡] Comet 1080p", a = comet), row("Plain 4K"))
        assertEquals(listOf("Plain 4K", "[TB⚡] Comet 1080p"), names(Autoplay.rank(rows, Autoplay.Cfg(instant = false), none, none)))
    }

    @Test fun rank_aboveAimFor_sitsUnderTheAimButOverLower() {
        val rows = listOf(row("A 4K"), row("B 720p"), row("C 1080p"), row("D"))
        assertEquals(listOf("C 1080p", "A 4K", "B 720p", "D"), names(Autoplay.rank(rows, Autoplay.Cfg(max = 3), none, none)))
    }

    @Test fun rank_slowThenStutterSink_tiesKeepAddonOrder() {
        val slowOne = row("A 1080p")
        val av1 = row("B 1080p AV1")
        val rows = listOf(slowOne, av1, row("C 1080p"), row("D 1080p"))
        val ranked = Autoplay.rank(rows, Autoplay.Cfg(), { it === slowOne.first }, { it === av1.first })
        assertEquals(listOf("C 1080p", "D 1080p", "B 1080p AV1", "A 1080p"), names(ranked))
    }

    @Test fun rank_torrentRowsAreNeverThePick() {
        val t = StreamItem("Plain 4K", "", "nebula-p2p://abc/0", infoHash = "a".repeat(40)) to plain
        assertEquals(listOf("Plain 720p"), names(Autoplay.rank(listOf(t, row("Plain 720p")), Autoplay.Cfg(), none, none)))
    }

    @Test fun rank_keepWaiting_sinksNotReadyRows() {
        val rows = listOf(row("[TB⬇️] Comet 4K", a = comet), row("Plain 720p"), row("[TB🔄] Sync", a = comet))
        assertEquals(listOf("Plain 720p", "[TB⬇️] Comet 4K"), names(Autoplay.rank(rows, Autoplay.Cfg(), none, none, keepWaiting = true)))
    }

    private val ready4k = StreamItem("[TB⚡] Comet 2160p", "", "u")
    private val ready1080 = StreamItem("[TB⚡] Comet 1080p", "", "u")
    private val plain1080 = StreamItem("Plain 1080p", "", "u")
    private val plain4k = StreamItem("Plain 4K", "", "u")

    @Test fun verdict_quick() {
        val q = Autoplay.Cfg(wait = "quick")
        assertEquals(Autoplay.Verdict.PICK, Autoplay.verdict(q, 1_000, ready1080, listOf("Slow"), false))
        assertEquals(Autoplay.Verdict.PICK, Autoplay.verdict(q, 1_000, plain4k, listOf("Slow"), false))
        assertEquals(Autoplay.Verdict.WAIT, Autoplay.verdict(q, 7_000, plain1080, listOf("Slow"), false))
        assertEquals(Autoplay.Verdict.PICK, Autoplay.verdict(q, 8_000, plain1080, listOf("Slow"), false))
    }

    @Test fun verdict_balanced() {
        val b = Autoplay.Cfg()
        assertEquals(Autoplay.Verdict.PICK, Autoplay.verdict(b, 1_000, ready4k, listOf("Comet | TB"), false))
        // instant but under the aim, a debrid add-on still asked: wait for its 4K
        assertEquals(Autoplay.Verdict.WAIT, Autoplay.verdict(b, 1_000, ready1080, listOf("Comet | TB"), false))
        // …no debrid add-on left: the instant row goes
        assertEquals(Autoplay.Verdict.PICK, Autoplay.verdict(b, 1_000, ready1080, listOf("Cinemeta"), false))
        assertEquals(Autoplay.Verdict.WAIT, Autoplay.verdict(b, 10_000, plain4k, listOf("Cinemeta"), false))
        assertEquals(Autoplay.Verdict.PICK, Autoplay.verdict(b, 25_000, plain1080, listOf("Cinemeta"), false))
        assertEquals(Autoplay.Verdict.WAIT, Autoplay.verdict(b, 60_000, null, listOf("Cinemeta"), false))
        assertEquals(Autoplay.Verdict.NONE, Autoplay.verdict(b, 90_000, null, listOf("Cinemeta"), false))
    }

    @Test fun verdict_all_andEveryoneAnswered() {
        val all = Autoplay.Cfg(wait = "all")
        assertEquals(Autoplay.Verdict.WAIT, Autoplay.verdict(all, 60_000, ready4k, listOf("Slow"), false))
        assertEquals(Autoplay.Verdict.PICK, Autoplay.verdict(all, 90_000, ready4k, listOf("Slow"), false))
        assertEquals(Autoplay.Verdict.SETTLE, Autoplay.verdict(all, 3_000, plain1080, emptyList(), true))
        assertEquals(Autoplay.Verdict.NONE, Autoplay.verdict(Autoplay.Cfg(), 3_000, null, emptyList(), true))
    }

    @Test fun briefClip_onlyADebridFilmUnderThirtySeconds() {
        assertTrue(Autoplay.briefClip("[TB⚡] Comet 2160p", "u", "movie", 8_000))
        assertTrue(Autoplay.briefClip("[TB⬇️] Comet 2160p", "u", "series", 8_000))
        assertFalse(Autoplay.briefClip("[TB⚡] Comet 2160p", "u", "movie", 7_200_000))
        assertFalse(Autoplay.briefClip("Plain trailer", "u", "movie", 8_000))
        assertFalse(Autoplay.briefClip("[TB⚡] Comet", "u", "tv", 8_000))
        assertFalse(Autoplay.briefClip("[TB⚡] Comet", "u", "movie", 0))
    }

    @Test fun next_skipsTheFailedAndTheCurrent() {
        val q = listOf(row("A", url = "a"), row("B", url = "b"), row("C", url = "c"))
        assertEquals("c", Autoplay.next(q, "a", setOf("b"))?.first?.url)
        assertNull(Autoplay.next(q, "c", setOf("a", "b")))
    }

    @Test fun cardLines() {
        assertEquals("Starting 4K · Comet | TB · instant", Autoplay.startLine(ready4k, "Comet | TB"))
        assertEquals("That one didn\u2019t play · trying 1080p · Plain", Autoplay.startLine(plain1080, "Plain", "error"))
        assertEquals("That one was slow to start · trying 1080p · Plain", Autoplay.startLine(plain1080, "Plain", "slow"))
        assertEquals("Asking A and B for streams…", Autoplay.askLine(0, listOf("A", "B")))
        assertEquals("Found 3 streams · still asking 3 add-ons", Autoplay.askLine(3, listOf("A", "B", "C")))
        assertEquals("Found 1 stream · picking the best", Autoplay.askLine(1, emptyList()))
    }
}
