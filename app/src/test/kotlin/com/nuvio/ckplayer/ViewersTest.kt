package com.nuvio.ckplayer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    @Test fun local_onlyHistoryMovesForTheSecondProfile() {
        assertEquals("progress", Viewers.local("progress", "a"))
        assertEquals("progress_b", Viewers.local("progress", "b"))
        assertEquals("library_b", Viewers.local("library", "b"))
        // add-ons, settings and ratings stay the account's
        assertEquals("addons", Viewers.local("addons", "b"))
        assertEquals("sub_style", Viewers.local("sub_style", "b"))
        assertEquals("ratings", Viewers.local("ratings", "b"))
        assertEquals("viewers", Viewers.local("viewers", "b"))
    }
}
