package com.example.hotspotportal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hotspotportal.PortalApp
import com.example.hotspotportal.auth.AuthStore
import com.example.hotspotportal.clients.ObservedClient
import com.example.hotspotportal.clients.groupByDevice
import com.example.hotspotportal.net.HotspotState
import com.example.hotspotportal.service.PortalService
import com.example.hotspotportal.service.PortalState
import com.example.hotspotportal.store.PortalSettings
import com.example.hotspotportal.store.PortalUserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val portalState: PortalState = PortalState.STOPPED,
    val rootAvailable: Boolean? = null,
    val iptablesAvailable: Boolean? = null,
    val hotspot: HotspotState? = null,
    val clientCount: Int = 0,
    val loggedInCount: Int = 0,
)

class PortalViewModel(app: Application) : AndroidViewModel(app) {

    private val portalApp = PortalApp.from(app)
    private val settingsStore = portalApp.settings
    private val userDao = com.example.hotspotportal.store.PortalDatabase.get(app).userDao()

    val settings: StateFlow<PortalSettings> = portalApp.settingsState
    val users: StateFlow<List<PortalUserEntity>> =
        userDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val logs = portalApp.eventLog.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val clients: StateFlow<List<ObservedClient>> = portalApp.clientMonitor.clients
        .map { groupByDevice(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dashboard = MutableStateFlow(DashboardState())
    val dashboard: StateFlow<DashboardState> = _dashboard.asStateFlow()

    val hotspot: StateFlow<HotspotState?> = portalApp.hotspotDetector.state

    init {
        viewModelScope.launch {
            portalApp.hotspotDetector.state.collect { _dashboard.value = _dashboard.value.copy(hotspot = it) }
        }
        viewModelScope.launch {
            portalApp.clientMonitor.clients.collect { list ->
                // Counted per device: one laptop with an IPv4 lease and two
                // global IPv6 addresses is one guest, not three.
                val devices = groupByDevice(list)
                _dashboard.value = _dashboard.value.copy(
                    clientCount = devices.size,
                    loggedInCount = devices.count { it.authorized },
                )
            }
        }
        viewModelScope.launch { refreshCapabilities() }
    }

    fun refreshCapabilities() = viewModelScope.launch {
        _dashboard.value = _dashboard.value.copy(
            rootAvailable = portalApp.shell.isAvailable(),
            iptablesAvailable = portalApp.firewall.resolveTools() == null,
            portalState = PortalService.instance?.state?.value ?: PortalState.STOPPED,
        )
    }

    fun activate() {
        PortalService.start(getApplication())
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            refreshCapabilities()
        }
    }

    fun stop() = PortalService.stop(getApplication())

    // --- users ------------------------------------------------------------

    fun createUser(
        username: String,
        password: String,
        deviceLimit: Int,
        expiresAt: Long?,
        note: String,
        onDone: (Boolean) -> Unit,
    ) = viewModelScope.launch {
        val result = portalApp.authStore.createUser(username, password, deviceLimit, expiresAt, note)
        result.onSuccess {
            portalApp.eventLog.record("user_created", it.username)
            onDone(true)
        }.onFailure {
            portalApp.eventLog.error("user_create_failed", it.message.orEmpty())
            onDone(false)
        }
    }

    fun updateUser(user: PortalUserEntity, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        runCatching { userDao.update(user) }
            .onSuccess { onDone(true) }
            .onFailure { onDone(false) }
    }

    fun setPassword(user: PortalUserEntity, password: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        runCatching { portalApp.authStore.setPassword(user, password) }
            .onSuccess { onDone(true) }
            .onFailure { onDone(false) }
    }

    fun deleteUser(user: PortalUserEntity) = viewModelScope.launch {
        userDao.delete(user)
        portalApp.eventLog.record("user_deleted", user.username)
    }

    fun generatePassword(): String = portalApp.authStore.generatePassword()

    // --- settings ---------------------------------------------------------

    fun setSessionHours(v: Int) = viewModelScope.launch { settingsStore.setSessionDurationHours(v) }
    fun setIdleMinutes(v: Int) = viewModelScope.launch { settingsStore.setIdleTimeoutMinutes(v) }
    fun setIfaceOverride(v: String) = viewModelScope.launch { settingsStore.setIfaceOverride(v) }
    fun setHttpPort(v: Int) = viewModelScope.launch { settingsStore.setHttpPort(v) }
    fun setDnsPort(v: Int) = viewModelScope.launch { settingsStore.setDnsPort(v) }
    fun setTlsPort(v: Int) = viewModelScope.launch { settingsStore.setTlsPort(v) }
    fun setAutoStart(v: Boolean) = viewModelScope.launch { settingsStore.setAutoStartOnBoot(v) }
    fun setDebugLogging(v: Boolean) = viewModelScope.launch { settingsStore.setDebugLogging(v) }
    fun setTitle(v: String) = viewModelScope.launch { settingsStore.setTitle(v) }
    fun setWelcome(v: String) = viewModelScope.launch { settingsStore.setWelcome(v) }
    fun setFooter(v: String) = viewModelScope.launch { settingsStore.setFooter(v) }

    fun clearLogs() = viewModelScope.launch { portalApp.eventLog.clear() }

    /** Kicks a client immediately: removes its whitelist rule on the spot. */
    fun kick(mac: String) = viewModelScope.launch {
        // revoke() is suspend, so it must be called from the coroutine
        // directly — runCatching's lambda is not a suspend context.
        portalApp.serviceSession()?.revoke(mac, "kicked")
        portalApp.eventLog.record("kicked", mac)
    }

    /** Manual rule rebuild from the Settings tab. */
    fun rebuildRules() = viewModelScope.launch {
        val hs = portalApp.hotspotDetector.refresh()
        if (hs == null) {
            portalApp.eventLog.error("rebuild_failed", "no hotspot interface")
            return@launch
        }
        val cfg = portalApp.settingsState.value
        runCatching { portalApp.firewall.install(hs, cfg.httpPort, cfg.dnsPort, cfg.tlsPort) }
            .onSuccess { portalApp.eventLog.record("rules_rebuilt", hs.cidr) }
            .onFailure { portalApp.eventLog.error("rebuild_failed", it.message.orEmpty()) }
    }
}
