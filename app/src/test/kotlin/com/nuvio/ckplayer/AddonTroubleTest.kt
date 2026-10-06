package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What an add-on failure says to the viewer: a sentence of ours, never the exception's own text. */
class AddonTroubleTest {
    @Test fun httpStatusIsNamedPlainly() {
        assertEquals("The add-on answered with an error (HTTP 404).", addonTrouble(RuntimeException("HTTP 404")))
        assertEquals("The add-on answered with an error (HTTP 503).", addonTrouble(RuntimeException("HTTP 503")))
    }

    @Test fun networkAndParseFailuresAreSentences() {
        val offline = addonTrouble(java.net.UnknownHostException("Unable to resolve host \"v3-cinemeta.strem.io\": No address associated with hostname"))
        assertEquals("Couldn’t reach the add-on — check the connection.", offline)
        assertEquals("Couldn’t reach the add-on — check the connection.", addonTrouble(java.net.SocketTimeoutException("timeout")))
        val page = addonTrouble(org.json.JSONException("Value <!DOCTYPE of type java.lang.String cannot be converted to JSONObject"))
        assertEquals("The add-on’s answer could not be read.", page)
        assertFalse(page.contains("DOCTYPE"))
    }

    @Test fun anythingElseIsGeneric() {
        assertEquals("Something went wrong asking the add-on.", addonTrouble(IllegalStateException("boom")))
        assertEquals("Something went wrong asking the add-on.", addonTrouble(RuntimeException("HTTP")))
    }
}
