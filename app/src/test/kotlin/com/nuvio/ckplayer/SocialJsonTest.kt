package com.nuvio.ckplayer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Friend cards come from the server as JSON where any field may be JSON null. */
class SocialJsonTest {
    private fun o(json: String) = JSONObject(json)

    @Test fun jstr_jsonNullIsEmpty_notTheWordNull() {
        // regression: optString turned null into "null", so a friend with no handle became "@null"
        assertEquals("", Social.jstr(o("""{"handle":null}"""), "handle"))
        assertEquals("", Social.jstr(o("{}"), "handle"))
        assertEquals("sam", Social.jstr(o("""{"handle":"sam"}"""), "handle"))
    }

    @Test fun friendKey_isTheHandle_elseTheOldCode() {
        assertEquals("@sam", Social.friendKey(o("""{"handle":"sam","code":"ABCD234"}""")))
        assertEquals("ABCD234", Social.friendKey(o("""{"handle":null,"code":"ABCD234"}""")))
    }

    @Test fun friendKey_twoFriendsWithoutHandlesDoNotShareAKey() {
        // two "@null"s were one duplicate key and crashed the keyed list
        val a = Social.friendKey(o("""{"handle":null,"code":"AAAA234"}"""))
        val b = Social.friendKey(o("""{"handle":null,"code":"BBBB234"}"""))
        assertFalse(a == b)
    }

    @Test fun friendLabel_nameThenKeyThenAFriend() {
        assertEquals("Sam", Social.friendLabel(o("""{"name":"Sam","handle":"sam"}""")))
        assertEquals("@sam", Social.friendLabel(o("""{"name":null,"handle":"sam"}""")))
        assertEquals("A friend", Social.friendLabel(o("""{"name":null,"handle":null,"code":null}""")))
    }

    @Test fun offOnServer_onlyA400MeansFriendsIsOff() {
        // a 400 is "friends is not enabled" (turned off on another device): Friends goes off here, the list reads empty
        assertTrue(Social.offOnServer(Cloud.HttpFail(400, "friends is not enabled")))
        // anything else is a failure to ask — never taken as off, or a flaky connection would switch Friends off
        assertFalse(Social.offOnServer(Cloud.HttpFail(401, "unauthorized")))
        assertFalse(Social.offOnServer(Cloud.HttpFail(500, "server")))
        assertFalse(Social.offOnServer(java.io.IOException("offline")))
        assertFalse(Social.offOnServer(RuntimeException("400")))
    }

    @Test fun friendRef_sendsTheHandleWhenThereIsOne_elseTheCode() {
        val byHandle = Social.friendRef(o("""{"handle":"sam","code":"ABCD234"}"""))
        assertEquals("sam", byHandle.getString("handle"))
        assertFalse(byHandle.has("code"))
        val byCode = Social.friendRef(o("""{"handle":null,"code":"ABCD234"}"""))
        assertEquals("ABCD234", byCode.getString("code"))
        assertFalse(byCode.has("handle"))
    }
}
