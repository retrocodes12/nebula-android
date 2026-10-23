package com.nuvio.ckplayer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A profile's PIN (ProfilePin.kt): the hash every app shares, the lock on the wire, the wait after five wrong. */
class PinTest {
    @Test fun pinHash_matchesTheSharedVectors() {
        assertEquals("10100505", ProfilePin.pinHash("0a1b2c3d", "1234"))
        assertEquals("11ce4385", ProfilePin.pinHash("ffffffff", "0000"))
        assertEquals("e4f71e45", ProfilePin.pinHash("00000000", "9999"))
    }

    @Test fun salt_isEightLowercaseHex_andAMadeLockMatchesOnlyItsPin() {
        repeat(20) { assertTrue(Regex("^[0-9a-f]{8}$").matches(ProfilePin.newSalt())) }
        val l = ProfilePin.make("4821")
        assertTrue(ProfilePin.matches(l, "4821"))
        assertFalse(ProfilePin.matches(l, "4822"))
        assertTrue(ProfilePin.pinOk("0007"))
        assertFalse(ProfilePin.pinOk("123")); assertFalse(ProfilePin.pinOk("12a4")); assertFalse(ProfilePin.pinOk("12345"))
    }

    @Test fun lockOf_needsBothFieldsAsEightLowercaseHex() {
        assertEquals(ProfilePin.Lock("0a1b2c3d", "10100505"), ProfilePin.lockOf(JSONObject().put("s", "0a1b2c3d").put("h", "10100505")))
        assertNull(ProfilePin.lockOf(null))
        assertNull(ProfilePin.lockOf(JSONObject().put("s", "0a1b2c3d")))
        assertNull(ProfilePin.lockOf(JSONObject().put("s", "0A1B2C3D").put("h", "10100505")))
        assertNull(ProfilePin.lockOf(JSONObject().put("s", "0a1b2c3").put("h", "10100505")))
    }

    @Test fun viewersList_carriesTheLockThroughParseAndSerialise() {
        val wire = JSONObject().put("at", 5L).put("list", JSONArray()
            .put(JSONObject().put("id", "a").put("name", "Sohil").put("av", "#E50914")
                .put("lock", JSONObject().put("s", "0a1b2c3d").put("h", "10100505")))
            .put(JSONObject().put("id", "b").put("name", "Kids").put("av", "#0A84FF")))
        val l = Viewers.parse(wire)
        assertEquals(ProfilePin.Lock("0a1b2c3d", "10100505"), l.first { it.id == "a" }.lock)
        assertNull(l.first { it.id == "b" }.lock)
        // written back (a rename, an add): the lock goes with its profile, and no empty lock appears on the other
        val back = Viewers.listJson(l.map { if (it.id == "a") it.copy(name = "Sohil 2") else it })
        val a = (0 until back.length()).map { back.getJSONObject(it) }.first { it.getString("id") == "a" }
        val b = (0 until back.length()).map { back.getJSONObject(it) }.first { it.getString("id") == "b" }
        assertEquals("Sohil 2", a.getString("name"))
        assertEquals("0a1b2c3d", a.getJSONObject("lock").getString("s"))
        assertEquals("10100505", a.getJSONObject("lock").getString("h"))
        assertFalse(b.has("lock"))
        assertEquals(l.map { if (it.id == "a") it.copy(name = "Sohil 2") else it }, Viewers.parse(JSONObject().put("list", back)))
        // a malformed lock is no lock (and is not written back)
        val bad = Viewers.parse(JSONObject().put("list", JSONArray().put(JSONObject().put("id", "a").put("lock", JSONObject().put("s", "x").put("h", "y")))))
        assertNull(bad.first().lock)
        assertFalse(Viewers.listJson(bad).getJSONObject(0).has("lock"))
    }

    @Test fun fiveWrongInARow_waitThirtySeconds_thenCountAgain() {
        var t = ProfilePin.Tries()
        for (i in 1..4) {
            t = ProfilePin.wrong(t, 1_000L)
            assertEquals(i, t.fails)
            assertFalse(ProfilePin.cooling(t, 1_000L))
        }
        t = ProfilePin.wrong(t, 2_000L)
        assertEquals(0, t.fails)
        assertTrue(ProfilePin.cooling(t, 2_000L))
        assertTrue(ProfilePin.cooling(t, 31_999L))
        assertFalse(ProfilePin.cooling(t, 32_000L))
        assertEquals(1, ProfilePin.wrong(t, 32_000L).fails)
    }

    @Test fun forgotPin_wording() {
        assertEquals("Wrong password.", ProfilePin.forgotError(403))
        assertEquals("Too many tries — wait a minute.", ProfilePin.forgotError(429))
        assertEquals("Could not reach Nebula. Try again.", ProfilePin.forgotError(500))
        assertEquals("Could not reach Nebula. Try again.", ProfilePin.forgotError(null))
    }
}
