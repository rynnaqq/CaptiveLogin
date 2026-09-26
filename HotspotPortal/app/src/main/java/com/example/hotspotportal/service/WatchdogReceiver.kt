package com.example.hotspotportal.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.hotspotportal.PortalApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-checks every 15 minutes that our jump rules are still in place. A third
 * party (or a network-stack reset) can flush the table under us, which would
 * silently unblock every guest.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = PortalApp.from(context)
        val pending = goAsync()
        app.appScope.launch(Dispatchers.IO) {
            runCatching {
                if (!app.firewall.rulesIntact()) {
                    val hs = app.hotspotDetector.refresh()
                    if (hs != null) {
                        val cfg = app.settingsState.value
                        app.firewall.install(hs, cfg.httpPort, cfg.dnsPort, cfg.tlsPort)
                        app.eventLog.record("watchdog_reinstall", hs.cidr)
                    }
                }
            }
            schedule(context)
            pending.finish()
        }
    }

    companion object {
        private const val INTERVAL_MS = 15 * 60 * 1000L

        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Inexact repeating is fine for a self-heal check and survives
            // Doze without an exact-alarm permission.
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, INTERVAL_MS, INTERVAL_MS, pi)
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.cancel(pi)
        }
    }
}
