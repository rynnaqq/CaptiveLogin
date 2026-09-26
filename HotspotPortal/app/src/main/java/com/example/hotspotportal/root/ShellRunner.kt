package com.example.hotspotportal.root

/** Result of one privileged command. */
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Every privileged command the app runs goes through this interface, so the
 * network layer can be unit-tested against a fake and so there is exactly
 * one place where root is touched.
 */
interface ShellRunner {
    /** False when no root binary is usable (no Magisk/KernelSU, or denied). */
    suspend fun isAvailable(): Boolean

    /** Tool version string, or null when the tool is missing. */
    suspend fun toolVersion(tool: String): String?

    /**
     * Runs [args] as argv. Never a concatenated string — libsu execs the
     * array directly, so an interface name or MAC can never be parsed as
     * shell syntax.
     */
    suspend fun exec(args: List<String>): ShellResult
}
