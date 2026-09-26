package com.example.hotspotportal.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-MAC brute-force limiter: 5 failures in 5 minutes locks that device
 * out of the login form.
 *
 * Pure and clock-injectable so the unit test can drive the window without
 * sleeping.
 */
class RateLimiter(
    private val maxAttempts: Int = 5,
    private val windowMillis: Long = 5 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Attempts(val firstAt: Long, var count: Int)

    private val attempts = ConcurrentHashMap<String, Attempts>()

    /** True when this client may still try. */
    fun allow(mac: String): Boolean {
        val t = now()
        val a = attempts[mac] ?: return true
        if (t - a.firstAt >= windowMillis) {
            attempts.remove(mac)
            return true
        }
        return a.count < maxAttempts
    }

    /** Seconds until the lockout lifts; 0 when not locked. */
    fun retryAfterSeconds(mac: String): Long {
        val a = attempts[mac] ?: return 0
        val remaining = windowMillis - (now() - a.firstAt)
        return if (remaining <= 0) 0 else (remaining + 999) / 1000
    }

    fun remainingAttempts(mac: String): Int {
        val a = attempts[mac] ?: return maxAttempts
        if (now() - a.firstAt >= windowMillis) return maxAttempts
        return (maxAttempts - a.count).coerceAtLeast(0)
    }

    fun recordFailure(mac: String) {
        val t = now()
        attempts.compute(mac) { _, existing ->
            if (existing == null || t - existing.firstAt >= windowMillis) {
                Attempts(t, 1)
            } else {
                existing.copy(count = existing.count + 1)
            }
        }
    }

    fun reset(mac: String) {
        attempts.remove(mac)
    }

    fun clear() = attempts.clear()
}
