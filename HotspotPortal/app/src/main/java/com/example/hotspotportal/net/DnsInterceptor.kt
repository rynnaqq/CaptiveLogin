package com.example.hotspotportal.net

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Answers every A query with the phone's own hotspot address and every AAAA
 * query with NOERROR/zero-answers, so a blocked client cannot slip onto the
 * internet over IPv6 (spec 5.4).
 *
 * Also receives all other UDP the catch-all redirect sends here, so malformed
 * packets are the normal case and are dropped silently rather than logged.
 */
class DnsInterceptor(private val gatewayProvider: () -> ByteArray?) {

    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var pool = Executors.newFixedThreadPool(2)

    fun start(port: Int = DEFAULT_DNS_PORT) {
        if (!running.compareAndSet(false, true)) return
        pool = Executors.newFixedThreadPool(2)
        Thread({ loop(port) }, "dns-interceptor").apply { isDaemon = true; start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { socket?.close() }
        socket = null
        pool.shutdownNow()
    }

    private fun loop(port: Int) {
        val s = runCatching { DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress("0.0.0.0", port)) } }
            .getOrElse {
                Log.e(TAG, "cannot bind UDP $port", it)
                running.set(false)
                return
            }
        socket = s
        val buf = ByteArray(2048)
        while (running.get()) {
            val packet = DatagramPacket(buf, buf.size)
            try {
                s.receive(packet)
            } catch (e: Exception) {
                if (running.get()) Log.w(TAG, "recv failed: ${e.message}")
                else return
                continue
            }
            val data = packet.data.copyOf(packet.length) // copy: buf is reused
            val query = DnsCodec.parseQuery(data) ?: continue
            if (query.`class` != DnsCodec.CLASS_IN) continue

            // Only A records get a payload. AAAA and everything else get
            // NOERROR with no answers.
            val ipv4 = if (query.type == DnsCodec.TYPE_A) gatewayProvider() else null
            val response = DnsCodec.buildResponse(query, data, ipv4)
            runCatching {
                s.send(DatagramPacket(response, response.size, packet.address, packet.port))
            }.onFailure { Log.w(TAG, "send failed: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "DnsInterceptor"
        const val DEFAULT_DNS_PORT = 5353
    }
}
