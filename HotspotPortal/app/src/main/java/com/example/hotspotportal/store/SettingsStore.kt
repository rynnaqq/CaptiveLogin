package com.example.hotspotportal.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.hotspotportal.server.PortalCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore("portal_settings")

data class PortalSettings(
    val sessionDurationHours: Int = 8,
    val idleTimeoutMinutes: Int = 30,
    val ifaceOverride: String = "",
    val httpPort: Int = 8080,
    val dnsPort: Int = 5353,
    val tlsPort: Int = 8443,
    val autoStartOnBoot: Boolean = false,
    val debugLogging: Boolean = false,
    val title: String = "Wi-Fi Login",
    val welcome: String = "Sign in to use this network.",
    val footer: String = "",
) {
    val sessionDurationMillis: Long get() = sessionDurationHours * 3600_000L
    val idleMillis: Long get() = idleTimeoutMinutes * 60_000L
    val copy: PortalCopy get() = PortalCopy(title, welcome, footer)
}

/** All admin preferences. App-private; no storage permission needed. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val SESSION_HOURS = intPreferencesKey("session_hours")
        val IDLE_MINUTES = intPreferencesKey("idle_minutes")
        val IFACE = stringPreferencesKey("iface_override")
        val HTTP_PORT = intPreferencesKey("http_port")
        val DNS_PORT = intPreferencesKey("dns_port")
        val TLS_PORT = intPreferencesKey("tls_port")
        val AUTO_START = booleanPreferencesKey("auto_start_boot")
        val DEBUG = booleanPreferencesKey("debug_logging")
        val TITLE = stringPreferencesKey("portal_title")
        val WELCOME = stringPreferencesKey("portal_welcome")
        val FOOTER = stringPreferencesKey("portal_footer")
    }

    val settings: Flow<PortalSettings> = context.dataStore.data.map { p ->
        PortalSettings(
            sessionDurationHours = p[Keys.SESSION_HOURS] ?: 8,
            idleTimeoutMinutes = p[Keys.IDLE_MINUTES] ?: 30,
            ifaceOverride = p[Keys.IFACE].orEmpty(),
            httpPort = p[Keys.HTTP_PORT] ?: 8080,
            dnsPort = p[Keys.DNS_PORT] ?: 5353,
            tlsPort = p[Keys.TLS_PORT] ?: 8443,
            autoStartOnBoot = p[Keys.AUTO_START] ?: false,
            debugLogging = p[Keys.DEBUG] ?: false,
            title = p[Keys.TITLE] ?: "Wi-Fi Login",
            welcome = p[Keys.WELCOME] ?: "Sign in to use this network.",
            footer = p[Keys.FOOTER].orEmpty(),
        )
    }

    suspend fun setSessionDurationHours(v: Int) = put(Keys.SESSION_HOURS, v.coerceIn(1, 720))
    suspend fun setIdleTimeoutMinutes(v: Int) = put(Keys.IDLE_MINUTES, v.coerceIn(1, 1440))
    suspend fun setIfaceOverride(v: String) = put(Keys.IFACE, v.trim())
    suspend fun setHttpPort(v: Int) = put(Keys.HTTP_PORT, v.coerceIn(1024, 65535))
    suspend fun setDnsPort(v: Int) = put(Keys.DNS_PORT, v.coerceIn(1024, 65535))
    suspend fun setTlsPort(v: Int) = put(Keys.TLS_PORT, v.coerceIn(1024, 65535))
    suspend fun setAutoStartOnBoot(v: Boolean) = put(Keys.AUTO_START, v)
    suspend fun setDebugLogging(v: Boolean) = put(Keys.DEBUG, v)
    suspend fun setTitle(v: String) = put(Keys.TITLE, v)
    suspend fun setWelcome(v: String) = put(Keys.WELCOME, v)
    suspend fun setFooter(v: String) = put(Keys.FOOTER, v)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
