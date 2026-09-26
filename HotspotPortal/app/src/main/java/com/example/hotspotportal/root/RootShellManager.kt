package com.example.hotspotportal.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * libsu-backed [ShellRunner]. Owns the app's single root shell.
 *
 * Note the package: libsu's Java classes live under `com.topjohnwu.superuser`,
 * not under the `com.github.topjohnwu.libsu` Maven coordinate.
 */
class RootShellManager : ShellRunner {

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(SHELL_TIMEOUT_MS) {
                Shell.getShell() ?: return@withTimeout false
                Shell.cmd("id", "-u").exec().out.joinToString("\n").trim().toIntOrNull() == 0
            }
        }.getOrDefault(false)
    }

    override suspend fun toolVersion(tool: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(SHELL_TIMEOUT_MS) {
                Shell.getShell() ?: return@withTimeout null
                val r = Shell.cmd(tool, "--version").exec()
                if (r.code == 0) {
                    (r.out.joinToString("\n").trim().ifEmpty { r.err.joinToString("\n").trim() })
                        .lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /**
     * Runs [args] as argv. libsu concatenates the varargs into one command
     * string inside the shell, but never re-tokenises them, so an interface
     * name or MAC is passed as a single literal argument — the injection
     * guarantee the FirewallCommands tests assert.
     */
    override suspend fun exec(args: List<String>): ShellResult = withContext(Dispatchers.IO) {
        require(args.isNotEmpty()) { "exec requires at least one argument" }
        runCatching {
            withTimeout(SHELL_TIMEOUT_MS) {
                Shell.getShell() ?: return@withTimeout ShellResult(-1, "", "no root shell available")
                val r = Shell.cmd(*args.toTypedArray()).exec()
                ShellResult(r.code, r.out.joinToString("\n"), r.err.joinToString("\n"))
            }
        }.getOrElse { ShellResult(-1, "", it.message ?: it.toString()) }
    }

    companion object {
        const val SHELL_TIMEOUT_MS = 15_000L

        /**
         * libsu needs a default builder before the first shell is created,
         * otherwise the first command can hang waiting on a grant prompt.
         */
        fun installDefaultBuilder() {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setTimeout(SHELL_TIMEOUT_MS)
                    .setInitializers()
                    .build()
            )
        }
    }
}
