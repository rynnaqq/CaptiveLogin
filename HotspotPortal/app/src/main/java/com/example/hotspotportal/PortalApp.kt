package com.example.hotspotportal

import android.app.Application
import android.content.Context
import com.example.hotspotportal.auth.AuthStore
import com.example.hotspotportal.clients.ClientMonitor
import com.example.hotspotportal.net.FirewallManager
import com.example.hotspotportal.net.HotspotDetector
import com.example.hotspotportal.root.RootShellManager
import com.example.hotspotportal.service.PortalService
import com.example.hotspotportal.store.EventLog
import com.example.hotspotportal.store.PortalDatabase
import com.example.hotspotportal.store.PortalSettings
import com.example.hotspotportal.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manual DI container. The graph is small and everything below the service is
 * stateless, so a DI framework would add an annotation processor for no gain.
 */
class PortalApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var shell: RootShellManager
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var authStore: AuthStore
        private set
    lateinit var firewall: FirewallManager
        private set
    lateinit var hotspotDetector: HotspotDetector
        private set
    lateinit var clientMonitor: ClientMonitor
        private set
    lateinit var eventLog: EventLog
        private set

    /**
     * Settings as a StateFlow: the networking layers read this synchronously
     * while the UI collects it reactively.
     */
    private val _settingsState = MutableStateFlow(PortalSettings())
    val settingsState: StateFlow<PortalSettings> = _settingsState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        RootShellManager.installDefaultBuilder()

        val db = PortalDatabase.get(this)
        shell = RootShellManager()
        settings = SettingsStore(this)
        authStore = AuthStore(db.userDao())
        firewall = FirewallManager(shell)
        eventLog = EventLog(db.logDao(), appScope)
        hotspotDetector = HotspotDetector(shell) { _settingsState.value.ifaceOverride }
        clientMonitor = ClientMonitor(shell)

        appScope.launch {
            settings.settings.collect { _settingsState.value = it }
        }
    }

    companion object {
        fun from(context: Context): PortalApp = context.applicationContext as PortalApp
    }

    /**
     * The live SessionManager, or null when the portal is not armed. The
     * session owner is the service (it owns the lifetime), so the UI reaches
     * it through here rather than holding a second reference.
     */
    fun serviceSession() = PortalService.instance?.sessions
}
