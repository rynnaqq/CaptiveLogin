package com.example.hotspotportal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.hotspotportal.PortalApp
import com.example.hotspotportal.R
import com.example.hotspotportal.auth.SessionManager
import com.example.hotspotportal.net.DnsInterceptor
import com.example.hotspotportal.net.HotspotState
import com.example.hotspotportal.net.TlsResetter
import com.example.hotspotportal.server.LoginApi
import com.example.hotspotportal.server.PortalServer
import com.example.hotspotportal.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PortalState { STOPPED, ARMED, ACTIVE, ERROR }

/**
 * Foreground orchestrator. Owns every engine and, critically, guarantees that
 * the firewall is torn down on every exit path — explicit Stop, onDestroy,
 * and onTaskRemoved all funnel into [teardown].
 */
class PortalService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: PortalApp

    private var server: PortalServer? = null
    private var dns: DnsInterceptor? = null
    private var tls: TlsResetter? = null

    /** Public so the admin UI can kick a client through the live session set. */
    @Volatile
    var sessions: SessionManager? = null
        private set

    private var loginApi: LoginApi? = null
    private var armed = false
    private var teardownStarted = false
    private val jobs = mutableListOf<Job>()

    private val _state = MutableStateFlow(PortalState.STOPPED)
    val state: StateFlow<PortalState> = _state.asStateFlow()

    private val _armed = MutableStateFlow(false)
    val armedFlow: StateFlow<Boolean> = _armed.asStateFlow()

    companion object {
        private const val CHANNEL_ID = "portal_status"
        private const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.example.hotspotportal.STOP"

        @Volatile
        var instance: PortalService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, PortalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PortalService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = PortalApp.from(this)
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            return START_NOT_STICKY
        }
        if (!armed) {
            armed = true
            _armed.value = true
            _state.value = PortalState.ARMED
            jobs += scope.launch { arm() }
        }
        return START_STICKY
    }

    /**
     * Waits for a hotspot, then installs everything. If the hotspot drops
     * while armed, rules are torn down and re-armed — the phone is never
     * left with rules pointing at a dead interface.
     */
    private suspend fun arm() {
        if (!app.shell.isAvailable()) {
            fail("no root")
            return
        }
        if (!app.firewall.hasIptables()) {
            fail("no iptables")
            return
        }

        while (currentCoroutineContext().isActive && armed) {
            val hotspot: HotspotState? = app.hotspotDetector.refresh()
            if (hotspot == null) {
                if (_state.value == PortalState.ACTIVE) teardown()
                _state.value = PortalState.ARMED
                delay(5000)
                continue
            }

            val cfg = app.settingsState.value

            // Built outside runCatching so the session set stays reachable
            // for the monitor wiring below.
            val sm = SessionManager(app.firewall, {
                val s = app.settingsState.value
                SessionManager.SessionConfig(s.sessionDurationMillis, s.idleMillis)
            }) { e, d -> app.eventLog.record(e, d) }
            sessions = sm

            val started = runCatching {
                app.eventLog.record("portal_start", "${hotspot.interfaceName} ${hotspot.cidr}")
                app.firewall.install(hotspot, cfg.httpPort, cfg.dnsPort, cfg.tlsPort)
                app.clientMonitor.bind(hotspot.interfaceName)

                val api = LoginApi(
                    authStore = app.authStore,
                    sessions = sm,
                    macResolver = { ip -> app.clientMonitor.macFor(ip) },
                    onEvent = { e, d -> app.eventLog.record(e, d) },
                )
                loginApi = api

                dns = DnsInterceptor(gatewayProvider = { hotspot.inOctets }).apply { start(cfg.dnsPort) }
                tls = TlsResetter().apply { start(cfg.tlsPort) }

                val s = PortalServer(
                    port = cfg.httpPort,
                    loginApi = api,
                    copyProvider = { app.settingsState.value.copy },
                    macResolver = { ip -> app.clientMonitor.macFor(ip) },
                    isAuthorized = { mac -> sm.isAuthorized(mac) },
                )
                server = s
                PortalServer.start(s, cfg.httpPort)
            }

            if (started.isFailure) {
                fail(started.exceptionOrNull()?.message ?: "setup failed")
                return
            }

            _state.value = PortalState.ACTIVE
            app.clientMonitor.onSessionsChanged { mac ->
                val sess = sm.sessionFor(mac)
                sess?.username to sess?.expiresAt
            }
            jobs += scope.launch { poll(sm) }
            return
        }
    }

    private suspend fun poll(sm: SessionManager) {
        while (currentCoroutineContext().isActive && armed) {
            app.clientMonitor.refresh()
            val present = app.clientMonitor.clients.value
                .filter { it.info.present }
                .map { it.info.mac }
                .toSet()
            present.forEach { sm.touch(it) }
            sm.tick(present)

            if (!app.firewall.rulesIntact()) {
                app.eventLog.error("rules_missing", "watchdog: jump rules vanished, reinstalling")
                val hs = app.hotspotDetector.refresh()
                if (hs != null) {
                    val cfg = app.settingsState.value
                    runCatching { app.firewall.install(hs, cfg.httpPort, cfg.dnsPort, cfg.tlsPort) }
                }
            }
            updateNotification(present.size)
            delay(60_000)
        }
    }

    private fun fail(reason: String) {
        _state.value = PortalState.ERROR
        app.eventLog.error("setup_failed", reason)
        teardown()
    }

    /** Idempotent — safe from onDestroy, onTaskRemoved, Stop, and after failure. */
    private fun teardown() {
        if (teardownStarted) return
        teardownStarted = true
        armed = false
        _armed.value = false
        _state.value = PortalState.STOPPED

        server?.let { runCatching { it.stop() } }
        server = null
        dns?.stop(); dns = null
        tls?.stop(); tls = null
        loginApi = null

        // Revoke sessions before removing the chain: each revoke removes its
        // own MAC rule, and the final uninstall sweeps anything left over.
        scope.launch {
            runCatching { sessions?.revokeAll() }
            runCatching { app.firewall.uninstall() }
            app.eventLog.record("portal_stop")
            sessions = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        teardown()
        instance = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away must not leave firewall rules installed.
        teardown()
        super.onTaskRemoved(rootIntent)
    }

    // --- notification -----------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.notif_channel_desc) }
        )
    }

    private fun buildNotification(deviceCount: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, PortalService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (deviceCount > 0) getString(R.string.notif_text, deviceCount)
        else getString(R.string.notif_text_idle)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(deviceCount: Int) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(deviceCount))
        }
    }
}
