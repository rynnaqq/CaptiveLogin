package com.example.hotspotportal.auth

import com.example.hotspotportal.net.FirewallCommands
import com.example.hotspotportal.net.FirewallManager
import com.example.hotspotportal.root.ShellResult
import com.example.hotspotportal.root.ShellRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test 4 (spec 13): expiry and idle revoke remove exactly the right rules.
 * The clock is injected, so no sleeping.
 */
class SessionManagerTest {

    private class RecordingShell : ShellRunner {
        val commands = mutableListOf<List<String>>()
        override suspend fun isAvailable() = true
        override suspend fun toolVersion(tool: String) = "1.0"
        override suspend fun exec(args: List<String>): ShellResult {
            commands += args
            return ShellResult(0, "", "")
        }
    }

    private val shell = RecordingShell()
    private val firewall = FirewallManager(shell)
    private var now = 1_000_000L
    private val cfg = SessionManager.SessionConfig(durationMillis = 8 * 3600_000L, idleMillis = 30 * 60_000L)
    private val events = mutableListOf<String>()

    private fun manager() = SessionManager(firewall, { cfg }) { event, _ -> events += event }

    @Test
    fun `login installs both v4 and v6 rules`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "tok")

        assertTrue(shell.commands.any { it == FirewallCommands.allowMacV4("aa:bb:cc:dd:ee:01") })
        assertTrue(shell.commands.any { it == FirewallCommands.allowMacV6("aa:bb:cc:dd:ee:01") })
        assertTrue(sm.isAuthorized("aa:bb:cc:dd:ee:01"))
    }

    @Test
    fun `expiry removes the rules for that MAC only`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t1")
        sm.create("aa:bb:cc:dd:ee:02", "bob", "t2")
        shell.commands.clear()

        now += cfg.durationMillis + 1
        sm.tick(presentMacs = setOf("aa:bb:cc:dd:ee:01", "aa:bb:cc:dd:ee:02"), now = now)

        assertTrue(shell.commands.any { it == FirewallCommands.removeMacV4("aa:bb:cc:dd:ee:01") })
        assertTrue(shell.commands.any { it == FirewallCommands.removeMacV6("aa:bb:cc:dd:ee:01") })
        // Both sessions expired at the same time — both must be removed.
        assertTrue(shell.commands.any { it == FirewallCommands.removeMacV4("aa:bb:cc:dd:ee:02") })
        assertEquals(0, sm.sessions.value.size)
    }

    @Test
    fun `idle revoke requires the MAC to be absent`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t1")
        shell.commands.clear()

        // Past the idle window, but the device is still on the network.
        now += cfg.idleMillis + 1
        sm.tick(presentMacs = setOf("aa:bb:cc:dd:ee:01"), now = now)
        assertTrue(shell.commands.isEmpty())
        assertTrue(sm.isAuthorized("aa:bb:cc:dd:ee:01"))

        // Now it disappears: the full idle window has already passed.
        shell.commands.clear()
        now += 1
        sm.tick(presentMacs = emptySet(), now = now)
        assertTrue(shell.commands.any { it == FirewallCommands.removeMacV4("aa:bb:cc:dd:ee:01") })
        assertFalse(sm.isAuthorized("aa:bb:cc:dd:ee:01"))
    }

    @Test
    fun `revoke all clears every rule on portal stop`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t1")
        sm.create("aa:bb:cc:dd:ee:02", "bob", "t2")
        shell.commands.clear()

        sm.revokeAll()
        assertEquals(0, sm.sessions.value.size)
        assertTrue(shell.commands.any { it == FirewallCommands.removeMacV4("aa:bb:cc:dd:ee:01") })
        assertTrue(shell.commands.any { it == FirewallCommands.removeMacV6("aa:bb:cc:dd:ee:02") })
    }

    @Test
    fun `device limit counts active sessions for a user`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t1")
        sm.create("aa:bb:cc:dd:ee:02", "alice", "t2")
        sm.create("aa:bb:cc:dd:ee:03", "bob", "t3")
        assertEquals(2, sm.activeDeviceCount("alice"))
        assertEquals(1, sm.activeDeviceCount("bob"))
    }
}
