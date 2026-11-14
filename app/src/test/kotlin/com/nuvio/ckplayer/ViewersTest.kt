package com.nuvio.ckplayer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Who's watching: the lists as the other apps write them, and where each profile's history lives. */
class ViewersTest {
    private fun doc(vararg v: Pair<String, String>) =
        JSONObject().put("at", 1L).put("list", JSONArray().apply { v.forEach { put(JSONObject().put("id", it.first).put("name", it.second)) } })

    @Test fun parse_firstAlwaysThere_sorted_viewersHoldsTheFirstTwoOnly_oddIdsDropped() {
        assertEquals(listOf("a"), Viewers.parse(JSONObject()).map { it.id })
        assertEquals(listOf("a", "b"), Viewers.parse(doc("b" to "Kids")).map { it.id })
        // `viewers` is the first two: a third written there (or a duplicate) is not a profile
        assertEquals(listOf("a", "b"), Viewers.parse(doc("b" to "Kids", "a" to "Sohil", "c" to "X", "b" to "Dup")).map { it.id })
        assertEquals("Kids", Viewers.parse(doc("b" to "Kids", "b" to "Dup")).first { it.id == "b" }.name)
    }

    private fun people() = JSONObject().put("at", 5L)
        .put("list", JSONArray()
            .put(JSONObject().put("id", "d").put("name", "Dad").put("av", "#FF9F0A"))
            .put(JSONObject().put("id", "c").put("name", "Mum"))
            .put(JSONObject().put("id", "b").put("name", "Not from here"))
            .put(JSONObject().put("id", "g").put("name", "Seven")))
        .put("x", JSONObject()
            .put("a", JSONObject().put("pic", "planet").put("share", 1))
            .put("b", JSONObject().put("pic", "rocket").put("born", 11L))
            .put("c", JSONObject().put("share", 1).put("born", 12L))
            .put("d", JSONObject().put("pic", "NOT A NAME")))

    @Test fun parse_threeToSixComeFromPeople_withEveryonesExtras() {
        val l = Viewers.parse(doc("b" to "Kids"), people())
        assertEquals(listOf("a", "b", "c", "d"), l.map { it.id })
        assertEquals("Kids", l[1].name)                      // the second is `viewers`' own, whatever `people` says
        assertEquals("planet", l[0].pic)
        assertEquals(false, l[0].share)                      // the first profile's add-ons are the account's: nothing to share
        assertEquals("rocket", l[1].pic); assertEquals(11L, l[1].born)
        assertEquals(true, l[2].share); assertEquals(12L, l[2].born); assertEquals("", l[2].pic)
        assertEquals("Dad", l[3].name); assertEquals("#FF9F0A", l[3].av)
        assertEquals("", l[3].pic)                           // a picture's name is letters only
        assertEquals(0L, l[3].born)
    }

    @Test fun wire_theTwoListsAndTheExtrasReadBackTheSame() {
        val l = Viewers.parse(doc("b" to "Kids"), people())
        val back = Viewers.parse(
            JSONObject().put("list", Viewers.listJson(l.filter { it.id <= "b" })),
            JSONObject().put("list", Viewers.listJson(l.filter { it.id > "b" })).put("x", Viewers.extrasJson(l)),
        )
        assertEquals(l, back)
        // only what is set rides in the extras
        val x = Viewers.extrasJson(l)
        assertEquals(false, x.has("d"))
        assertEquals(false, x.getJSONObject("a").has("share"))
        assertEquals(1, x.getJSONObject("c").getInt("share"))
    }

    @Test fun local_everyProfilePastTheFirstUnderItsLetter_aSharedListIsTheFirsts() {
        assertEquals("progress_c", Viewers.local("progress", "c"))
        assertEquals("library_f", Viewers.local("library", "f"))
        assertEquals("track_d", Viewers.local("track", "d"))
        assertEquals("addons_e", Viewers.local("addons", "e"))
        assertEquals("addons_sync_e", Viewers.local("addons_sync", "e"))
        // set to use the first profile's add-ons: its list and stamps are the first's; its history is still its own
        assertEquals("addons", Viewers.local("addons", "e", true))
        assertEquals("addons_sync", Viewers.local("addons_sync", "e", true))
        assertEquals("progress_e", Viewers.local("progress", "e", true))
        // not a profile: the account's
        assertEquals("progress", Viewers.local("progress", "g"))
        assertEquals("progress", Viewers.local("progress", ""))
    }

    @Test fun baseKey_takesAProfilesLetterOffItsOwnDocuments_only() {
        assertEquals("progress", Viewers.baseKey("progress_b"))
        assertEquals("addons_sync", Viewers.baseKey("addons_sync_f"))
        assertEquals("track", Viewers.baseKey("track_c"))
        assertEquals("progress", Viewers.baseKey("progress"))
        assertEquals("sub_style", Viewers.baseKey("sub_style"))       // (ends in a word, not a letter)
        assertEquals("ratings_b", Viewers.baseKey("ratings_b"))       // not a per-profile document
        assertEquals("progress_a", Viewers.baseKey("progress_a"))     // the first profile has no letter
        assertEquals("progress_g", Viewers.baseKey("progress_g"))
    }

    @Test fun stale_aSlotThatIsEmptyNow_orHoldsSomeoneBornAtAnotherTime_isWiped() {
        val now = Viewers.parse(doc("b" to "Kids"), people())            // b born 11, c born 12, d born 0
        // this device knew the same people: nothing to do
        assertEquals(emptySet<String>(), Viewers.stale(mapOf("b" to 11L, "c" to 12L, "d" to 0L), now))
        // it knew another c, and an e that is gone
        assertEquals(setOf("c", "e"), Viewers.stale(mapOf("b" to 11L, "c" to 7L, "e" to 3L), now))
        // a slot it never saw filled is left alone, whatever is in it now
        assertEquals(emptySet<String>(), Viewers.stale(emptyMap(), now))
        // a second profile from before births were stamped (0) that was removed and made again
        assertEquals(setOf("b"), Viewers.stale(mapOf("b" to 0L), now))
        // the first profile is never a slot
        assertEquals(emptySet<String>(), Viewers.stale(mapOf("a" to 1L), now))
    }

    @Test fun pictures_everyOneReads_andStartsWithAMove() {
        assertEquals(24, WhoPics.ALL.size)
        assertEquals(24, WhoPics.ALL.map { it.id }.toSet().size)
        for (p in WhoPics.ALL) {
            val ops = WhoPics.parse(p.path)
            assertEquals(p.id, true, ops != null && ops.size > 4 && ops.first().c == 'M' && ops.last().c == 'Z')
            assertEquals(p.id, true, ops!!.all { o -> o.n.all { it >= 0f && it <= 24f } })
            assertEquals(p.id, true, Regex("^[a-z]{1,16}$").matches(p.id))
        }
        assertNull(WhoPics.find("nope")); assertNull(WhoPics.find("")); assertNull(WhoPics.find(null))
        assertNull(WhoPics.parse("M 1 2 L 3"))
        assertNull(WhoPics.parse("L 1 2 Z"))
        assertNull(WhoPics.parse("M 1 2 A 3 4 Z"))
    }

    @Test fun local_historyMovesForTheSecondProfile_settingsStay() {
        assertEquals("progress", Viewers.local("progress", "a"))
        assertEquals("progress_b", Viewers.local("progress", "b"))
        assertEquals("library_b", Viewers.local("library", "b"))
        // settings and ratings stay the account's
        assertEquals("sub_style", Viewers.local("sub_style", "b"))
        assertEquals("ratings", Viewers.local("ratings", "b"))
        assertEquals("viewers", Viewers.local("viewers", "b"))
    }

    @Test fun local_addonsAndTheirStampsArePerProfile() {
        assertEquals("addons", Viewers.local("addons", "a"))
        assertEquals("addons_sync", Viewers.local("addons_sync", "a"))
        assertEquals("addons_b", Viewers.local("addons", "b"))
        assertEquals("addons_sync_b", Viewers.local("addons_sync", "b"))
    }

    @Test fun standIn_onlyWhenTheAccountWasNotAsked_orDidNotAnswer() {
        // signed out / no answer: the copy stands in until a pull finds the account's own list
        assertEquals(true, Viewers.standIn(null, false))
        // the account answered with no second list: the copy IS the second profile's list
        assertEquals(false, Viewers.standIn(setOf("addons", "progress"), false))
        // it holds one: the second starts empty, nothing to stand in for
        assertEquals(false, Viewers.standIn(setOf("addons_b"), true))
    }

    @Test fun addonStart_copiesTheFirstListAndItsOwnStamps_onlyWhenTheCloudHasNoSecondList() {
        val list = JSONArray().put(JSONObject().put("url", "https://a.example/manifest.json")).toString()
        val sync = JSONObject().put("at", JSONObject().put("https://a.example/manifest.json", 1234L))
            .put("removed", JSONObject().put("https://gone.example/manifest.json", 99L)).put("orderAt", 77L).toString()
        val seed = Viewers.addonStart(bHas = false, cloudHasB = false, aList = list, aSync = sync)!!
        assertEquals(list, seed.first)
        // the first profile's own times, not "now": a newer removal synced into addons_b still wins
        assertEquals(1234L, JSONObject(seed.second!!).getJSONObject("at").getLong("https://a.example/manifest.json"))
        assertEquals(77L, JSONObject(seed.second!!).getLong("orderAt"))
        // the cloud already holds addons_b: start empty, with empty stamps, and let the pull adopt it as it is
        assertEquals("[]" to "{}", Viewers.addonStart(bHas = false, cloudHasB = true, aList = list, aSync = sync))
        // a store of its own already: never touched
        assertNull(Viewers.addonStart(bHas = true, cloudHasB = false, aList = list, aSync = sync))
        assertNull(Viewers.addonStart(bHas = true, cloudHasB = true, aList = list, aSync = sync))
        // an empty or missing first list leaves the second empty
        assertNull(Viewers.addonStart(bHas = false, cloudHasB = false, aList = "[]", aSync = sync))
        assertNull(Viewers.addonStart(bHas = false, cloudHasB = false, aList = null, aSync = null))
        assertNull(Viewers.addonStart(bHas = false, cloudHasB = false, aList = "not json", aSync = null))
        // no stamps yet: the list alone
        assertNull(Viewers.addonStart(bHas = false, cloudHasB = false, aList = list, aSync = null)!!.second)
    }
}
