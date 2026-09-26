package com.example.hotspotportal.net

import android.util.Log
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Accepts TCP on 8443 and closes it immediately.
 *
 * Blocked clients that try HTTPS get a fast RST instead of a 30-second hang,
 * which pushes them to their plain-HTTP connectivity probe and the portal
 * appears in seconds. A REJECT rule cannot be used here because REJECT is not
 * a valid nat PREROUTING target (spec gotcha 4).
 */
class TlsResetter(private val onReset: (String) -> Unit = {}) {

    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var thread: Thread? = null

    /** Binds on the caller's thread so a failure propagates to the service. */
    fun start(port: Int = DEFAULT_TLS_PORT) {
        if (!running.compareAndSet(false, true)) return
        val s = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("0.0.0.0", port))
        }
        server = s
        thread = Thread({
            while (running.get()) {
                val client: Socket = try {
                    s.accept()
                } catch (e: Exception) {
                    if (running.get()) Log.w(TAG, "accept failed: ${e.message}") else null
                    if (!running.get()) break
                    continue
                }
                // The client never sends anything useful; closing here is the
                // entire mechanism.
                runCatching { client.close() }
                    .onSuccess { onReset(client.inetAddress?.hostAddress ?: "?") }
            }
        }, "tls-resetter").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server?.close() }
        server = null
    }

    companion object {
        private const val TAG = "TlsResetter"
        const val DEFAULT_TLS_PORT = 8443
    }
}
