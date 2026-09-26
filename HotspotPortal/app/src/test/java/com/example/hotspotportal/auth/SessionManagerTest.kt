package com.example.hotspotportal.auth

import com.example.hotspotportal.net.FirewallCommands
import com.example.hotspotportal.net.FirewallManager
import com.example.hotspotportal.root.ShellResult
import com.example.hotspotportal.root.ShellRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sessions and their firewall rules.
 *
 * There is no expiry and no idle clock any more: a guest stays authorised until
 * the admin kicks it, the account is disabled or deleted, or the portal stops.
 * These cover what is left - that a login installs the rules, that a kick
 * removes exactly that MAC's rules, that a stop clears everything, and that a
 * session genuinely never expires on its own.
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
    private val events = mutableListOf<String>()

    private fun manager() = SessionManager(firewall) { event, _ -> events += event }

    @Test
    fun `login installs both v4 and v6 rules`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "tok")

        assertTrue(shell.commands.any { it == FirewallCommands.allowMacV4("aa:bb:cc:dd:ee:01") })
        assertTrue(shell.commands.any { it == FirewallCommands.allowMacV6("aa:bb:cc:dd:ee:01") })
        assertTrue(sm.isAuthorized("aa:bb:cc:dd:ee:01"))
        assertEquals("alice", sm.sessionFor("aa:bb:cc:dd:ee:01")?.username)
    }

    @Test
    fun `a kick removes that MAC's rules and nothing else`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t1")
        sm.create("aa:bb:cc:dd:ee:02", "bob", "t2")
        shell.commands.clear()

        sm.revoke("aa:bb:cc:dd:ee:01", "kicked")

        assertTrue(shell.commands.any { it == FirewallCommands.removeMacV4("aa:bb:cc:dd:ee:01") })
        assertTrue(shell.commands.any { it == FirewallCommands.removeMacV6("aa:bb:cc:dd:ee:01") })
        assertFalse(shell.commands.any { it.contains("dd:ee:02") })
        assertFalse(sm.isAuthorized("aa:bb:cc:dd:ee:01"))
        assertTrue(sm.isAuthorized("aa:bb:cc:dd:ee:02"))
    }

    @Test
    fun `a session never expires on its own`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t1")

        // Nothing in the manager is driven by a clock any more, so there is no
        // waiting to do: the session is simply still authorised, and no
        // firewall rule has been removed behind the admin's back.
        val removals = shell.commands.filter { it.firstOrNull()?.contains("mac-source") == true && it.contains("-D") }
        assertTrue(removals.isEmpty())
        assertTrue(sm.isAuthorized("aa:bb:cc:dd:ee:01"))
        assertNull(sm.sessionFor("aa:bb:cc:dd:ee:02"))
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

    @Test
    fun `logging in again on the same device does not consume a second slot`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t1")
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t2")

        assertEquals(1, sm.activeDeviceCount("alice"))
    }

    @Test
    fun `login and revoke are logged for the admin`() = runTest {
        val sm = manager()
        sm.create("aa:bb:cc:dd:ee:01", "alice", "t1")
        sm.revoke("aa:bb:cc:dd:ee:01", "kicked")

        assertTrue(events.any { it == "login_ok" })
        assertTrue(events.any { it == "session_end" })
    }
}
