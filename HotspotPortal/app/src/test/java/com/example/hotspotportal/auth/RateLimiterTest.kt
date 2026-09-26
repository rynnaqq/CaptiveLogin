package com.example.hotspotportal.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test 3 (spec 13): 5 failures locks the device out, window resets it. */
class RateLimiterTest {

    private var now = 1_000L
    private val limiter = RateLimiter(maxAttempts = 5, windowMillis = 5 * 60 * 1000L) { now }

    private val mac = "aa:bb:cc:dd:ee:ff"

    @Test
    fun `allows five attempts then locks`() {
        repeat(5) { limiter.recordFailure(mac) }
        assertFalse(limiter.allow(mac))
        assertEquals(0, limiter.remainingAttempts(mac))
    }

    @Test
    fun `unrelated mac is unaffected`() {
        repeat(5) { limiter.recordFailure(mac) }
        assertTrue(limiter.allow("11:22:33:44:55:66"))
    }

    @Test
    fun `resets once the window elapses`() {
        repeat(5) { limiter.recordFailure(mac) }
        assertFalse(limiter.allow(mac))

        now += 5 * 60 * 1000L + 1
        assertTrue(limiter.allow(mac))
        assertEquals(5, limiter.remainingAttempts(mac))
    }

    @Test
    fun `successful login clears the counter`() {
        repeat(3) { limiter.recordFailure(mac) }
        limiter.reset(mac)
        assertEquals(5, limiter.remainingAttempts(mac))
    }

    @Test
    fun `retry after reports whole seconds`() {
        repeat(5) { limiter.recordFailure(mac) }
        now += 30_000
        assertEquals(270, limiter.retryAfterSeconds(mac))
    }
}
