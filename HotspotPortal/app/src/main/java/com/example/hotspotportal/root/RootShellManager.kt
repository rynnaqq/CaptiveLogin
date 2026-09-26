package com.example.hotspotportal.root

import com.topjohnwu.superuser.NoShellException
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

    /**
     * Why the last [isAvailable] returned false, or null when root is live.
     * The UI has no other way to tell "no su on this device" from "su denied",
     * so the reason is kept here and logged instead of being swallowed.
     */
    @Volatile
    var lastRootFailure: String? = null
        private set

    /**
     * True when a root shell is live.
     *
     * libsu already proves root before handing a shell back: `ShellImpl`'s
     * constructor writes `id` to the shell and only reports success when the
     * output contains "uid=0". So a returned shell *is* root, and re-deriving
     * that from `id -u` was a false-negative generator - any banner on stdout
     * (ksud prints one) made the trimmed output fail to parse as an Int and
     * turned a working root shell into "no root".
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(SHELL_TIMEOUT_MS) {
                val root = Shell.getShell().isRoot
                lastRootFailure = if (root) null else "su started but the shell is not uid=0"
                root
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            lastRootFailure = describeFailure(t)
            false
        }
    }

    private fun describeFailure(t: Throwable): String {
        val base = t.javaClass.simpleName +
            (t.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
        // NoShellException is libsu's "could not start su" - always a grant or
        // mount problem on the device, never a parsing problem.
        return if (t is NoShellException) {
            "$base - no usable su. Check the superuser grant in KernelSU/Magisk for THIS " +
                "installed build (a reinstall drops the grant), then reactivate."
        } else {
            base
        }
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
         * Note: setDefaultBuilder takes the Builder, not the built Shell, so
         * build() must NOT be called here.
         */
        fun installDefaultBuilder() {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setTimeout(SHELL_TIMEOUT_MS)
                    .setInitializers()
            )
        }
    }
}
