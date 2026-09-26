package com.example.hotspotportal.root

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The app's whole injection boundary.
 *
 * libsu's `Shell.cmd(String...)` writes each element on its own line, so
 * arguments are joined into one command line here. If a MAC or interface name
 * can escape its quotes, a client-supplied value can flush the whole table.
 */
class ShellQuotingTest {

    @Test
    fun `every element is single-quoted`() {
        assertEquals(
            "'iptables' '-w' '5' '-t' 'nat' '-L' '-n'",
            RootShellManager.quoteArgs(listOf("iptables", "-w", "5", "-t", "nat", "-L", "-n")),
        )
    }

    @Test
    fun `shell metacharacters in a MAC cannot escape the quotes`() {
        val evil = "aa:bb:cc:dd:ee:ff; iptables -F"
        val quoted = RootShellManager.quoteArgs(listOf("iptables", "-m", "mac", "--mac-source", evil))

        // The whole MAC is one single-quoted word, so ';' is literal.
        assertEquals("'iptables' '-m' 'mac' '--mac-source' 'aa:bb:cc:dd:ee:ff; iptables -F'", quoted)
    }

    @Test
    fun `an embedded single quote is closed, escaped and reopened`() {
        // A MAC cannot contain this, but a hostname or SSID passed through the
        // same path could, and an unescaped quote would end the word early.
        assertEquals("'it'\\''s'", RootShellManager.quoteArgs(listOf("it's")))
    }

    @Test
    fun `a command substitution cannot escape the quotes`() {
        val quoted = RootShellManager.quoteArgs(listOf("echo", "$(id)"))
        assertEquals("'echo' '$(id)'", quoted)
    }
}
