package com.nuvio.ckplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which failed stream requests are asked once more (2026-10-29: a slow add-on that drops a request now and then). */
class StreamRetryTest {
    @Test fun serverTroubleIsAskedAgain() {
        for (c in listOf(500, 502, 503, 504, 520, 408, 429)) assertTrue("$c", Stremio.streamRetryWorth(Stremio.HttpStatus(c), 300))
    }

    @Test fun aRefusalIsFinal() {
        for (c in listOf(400, 401, 403, 404, 410, 451)) assertFalse("$c", Stremio.streamRetryWorth(Stremio.HttpStatus(c), 300))
    }

    @Test fun aDroppedConnectionIsAskedAgain() {
        assertTrue(Stremio.streamRetryWorth(java.io.IOException("unexpected end of stream"), 4_000))
        assertTrue(Stremio.streamRetryWorth(java.net.ConnectException("refused"), 50))
        assertTrue(Stremio.streamRetryWorth(java.net.UnknownHostException("addon.test"), 50))
    }

    @Test fun aTimeoutOnlyBeforeTheWholeWait() {
        assertTrue(Stremio.streamRetryWorth(java.net.SocketTimeoutException("connect timed out"), 15_000))
        assertFalse(Stremio.streamRetryWorth(java.net.SocketTimeoutException("Read timed out"), Stremio.STREAM_READ_MS.toLong() + 15))
    }

    @Test fun anAnswerThatIsNotAStreamListIsFinal() {
        assertFalse(Stremio.streamRetryWorth(org.json.JSONException("not json"), 300))
        assertFalse(Stremio.streamRetryWorth(IllegalStateException("x"), 300))
    }

    @Test fun theMessageIsUnchanged() {
        org.junit.Assert.assertEquals("HTTP 503", Stremio.HttpStatus(503).message)
    }
}
