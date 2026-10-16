package com.nuvio.ckplayer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Who's watching: the list as the other apps write it, and where each profile's history lives. */
class ViewersTest {
    private fun doc(vararg v: Pair<String, String>) =
        JSONObject().put("at", 1L).put("list", JSONArray().apply { v.forEach { put(JSONObject().put("id", it.first).put("name", it.second)) } })

    @Test fun parse_firstAlwaysThere_sorted_atMostTwo_oddIdsDropped() {
        assertEquals(listOf("a"), Viewers.parse(JSONObject()).map { it.id })
        assertEquals(listOf("a", "b"), Viewers.parse(doc("b" to "Kids")).map { it.id })
        assertEquals(listOf("a", "b"), Viewers.parse(doc("b" to "Kids", "a" to "Sohil", "c" to "X", "b" to "Dup")).map { it.id })
        assertEquals("Kids", Viewers.parse(doc("b" to "Kids", "b" to "Dup")).first { it.id == "b" }.name)
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
