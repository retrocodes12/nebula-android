package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The monthly tier's rank, the plan's fields, and the vote's answers. */
class PerksTest {
    @Test fun ranks_areAnExplicitMap_monthlyIsPlusLevel() {
        assertEquals(1, Support.rankOf("supporter"))
        assertEquals(2, Support.rankOf("plus"))
        assertEquals(2, Support.rankOf("monthly"))
        assertEquals(3, Support.rankOf("founder"))
        assertEquals(1, Support.rankOf("gold-plated"))          // an unknown tier is a supporter
        assertEquals(1, Support.rankOf(null))
        assertEquals("monthly", Support.cleanTier("monthly"))
        assertEquals("Monthly Supporter", Support.TIERS.first { it.first == "monthly" }.second)
    }

    @Test fun plan_statusInWords() {
        assertEquals("Free week", Support.subStatusText("trialing"))
        assertEquals("Active", Support.subStatusText("active"))
        assertEquals("Payment failed — update your card", Support.subStatusText("past_due"))
        assertEquals("Paused", Support.subStatusText("paused"))
        assertEquals("", Support.subStatusText("incomplete_expired"))
    }

    @Test fun plan_fieldsAreCleanedAtTheBoundary() {
        assertEquals("past_due", Cloud.cleanPlanStatus(" PAST_DUE "))
        assertEquals("", Cloud.cleanPlanStatus("<b>active</b>"))
        assertEquals("", Cloud.cleanPlanStatus(null))
        assertEquals("https://retrocodes.pocketsflow.com/portal/abc", Cloud.cleanManage(" https://retrocodes.pocketsflow.com/portal/abc "))
        assertEquals("", Cloud.cleanManage("http://retrocodes.pocketsflow.com/portal/abc"))      // https only
        assertEquals("", Cloud.cleanManage("javascript:alert(1)"))
        assertEquals("", Cloud.cleanManage("https://x.example/" + "a".repeat(600)))
    }

    @Test fun vote_beforeOnesBallot_noCounts() {
        val s = Vote.parse(JSONObject("""{"round":{"id":"r1","title":"What next?","closes":1790000000000,
            "options":[{"id":"o1","title":"Trakt","note":"Sync what you watch"},{"id":"o2","title":"Arabic","note":""},{"title":"no id"}]},
            "mine":null,"can":true,"last":null}"""))
        val r = s.round!!
        assertEquals("What next?", r.title)
        assertEquals(2, r.options.size)                         // a row without an id is dropped
        assertNull(r.options[0].votes)
        assertNull(r.voters)
        assertNull(s.mine)
        assertTrue(s.can)
        assertNull(s.last)
    }

    @Test fun vote_afterOnesBallot_countsAndMine() {
        val s = Vote.parse(JSONObject("""{"round":{"id":"r1","title":"What next?","closes":1,
            "options":[{"id":"o1","title":"A","note":"","votes":3},{"id":"o2","title":"B","note":"","votes":1}],"voters":4},
            "mine":"o2","can":true,
            "last":{"id":"r0","title":"Before","closed":2,"winner":{"id":"o1","title":"Won"},"voters":7,"options":[{"id":"o1","title":"Won","votes":7}]}}"""))
        assertEquals("o2", s.mine)
        assertEquals(3, s.round!!.options[0].votes)
        assertEquals(4, s.round!!.voters)
        assertEquals("Won", s.last!!.winner)
        assertEquals(7, s.last!!.voters)
    }

    @Test fun vote_mineMustBeOnTheBallot_andNoRoundIsNull() {
        val s = Vote.parse(JSONObject("""{"round":null,"mine":"o9","can":false,"last":{"id":"r0","title":"T","closed":2,"winner":null,"voters":0,"options":[]}}"""))
        assertNull(s.round)
        assertNull(s.mine)
        assertFalse(s.can)
        assertNull(s.last!!.winner)
    }

    @Test fun shares_addUpToAHundred() {
        assertEquals(listOf(34, 33, 33), Vote.shares(listOf(1, 1, 1)))
        assertEquals(100, Vote.shares(listOf(2, 5, 7, 1)).sum())
        assertEquals(listOf(75, 25), Vote.shares(listOf(3, 1)))
        assertEquals(listOf(0, 0), Vote.shares(listOf(0, 0)))
    }
}
