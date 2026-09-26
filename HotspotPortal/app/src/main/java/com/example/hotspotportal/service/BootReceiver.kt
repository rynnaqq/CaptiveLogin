package com.example.hotspotportal.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.hotspotportal.PortalApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Auto-arms the portal after a reboot when the admin opted in.
 *
 * The hotspot is usually not up when BOOT_COMPLETED fires, so this polls for
 * it and gives up rather than starting a service that would sit idle holding
 * the rules awake.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val app = PortalApp.from(context)
        val pending = goAsync()

        app.appScope.launch {
            val enabled = runCatching {
                withTimeoutOrNull(5_000) {
                    app.settings.settings.first().autoStartOnBoot
                } == true
            }.getOrDefault(false)

            if (enabled) {
                val up = runCatching {
                    withTimeoutOrNull(60_000) {
                        app.hotspotDetector.state.first { it != null }
                    } != null
                }.getOrDefault(false)
                if (up) {
                    app.eventLog.record("boot_autostart")
                    PortalService.start(context)
                }
            }
            pending.finish()
        }
    }
}
