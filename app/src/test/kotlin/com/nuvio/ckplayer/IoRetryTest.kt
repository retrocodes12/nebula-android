package com.nuvio.ckplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which HTTP answers a reading failure is asked again for (2026-10-02: "ERROR_CODE_IO_UNSPECIFIED (2000)" mid-film). */
class IoRetryTest {
    @Test fun serverTroubleIsAskedAgainARefusalIsNot() {
        for (c in listOf(500, 502, 503, 504, 520, 408, 429)) assertTrue("$c", httpWorthRetry(c))
        for (c in listOf(400, 401, 403, 404, 410, 416, 451)) assertFalse("$c", httpWorthRetry(c))
    }
}
