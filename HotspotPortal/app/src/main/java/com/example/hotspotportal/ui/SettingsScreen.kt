package com.example.hotspotportal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hotspotportal.R

@Composable
fun SettingsScreen(vm: PortalViewModel) {
    val cfg by vm.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard(stringResource(R.string.settings_general)) {
            NumberField(stringResource(R.string.portal_title), cfg.title, { vm.setTitle(it) })
            NumberField(stringResource(R.string.portal_welcome), cfg.welcome, { vm.setWelcome(it) })
            NumberField(stringResource(R.string.portal_footer), cfg.footer, { vm.setFooter(it) })
        }

        SettingsCard(stringResource(R.string.session_summary, "session", "idle")) {
            NumberField(
                stringResource(R.string.session_duration),
                cfg.sessionDurationHours.toString(),
                { vm.setSessionHours(it.toIntOrNull() ?: 8) },
            )
            NumberField(
                stringResource(R.string.idle_timeout),
                cfg.idleTimeoutMinutes.toString(),
                { vm.setIdleMinutes(it.toIntOrNull() ?: 30) },
            )
        }

        SettingsCard(stringResource(R.string.settings_advanced)) {
            NumberField(
                stringResource(R.string.iface_override),
                cfg.ifaceOverride,
                { vm.setIfaceOverride(it) },
            )
            NumberField(stringResource(R.string.http_port), cfg.httpPort.toString()) { vm.setHttpPort(it.toIntOrNull() ?: 8080) }
            NumberField(stringResource(R.string.dns_port), cfg.dnsPort.toString()) { vm.setDnsPort(it.toIntOrNull() ?: 5353) }
            NumberField(stringResource(R.string.tls_port), cfg.tlsPort.toString()) { vm.setTlsPort(it.toIntOrNull() ?: 8443) }

            ToggleRow(stringResource(R.string.debug_logging), cfg.debugLogging) { vm.setDebugLogging(it) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = { vm.rebuildRules() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reset_all_rules))
            }
        }

        SettingsCard(stringResource(R.string.settings_general)) {
            ToggleRow(stringResource(R.string.auto_start_boot), cfg.autoStartOnBoot) { vm.setAutoStart(it) }
            Text(
                stringResource(R.string.auto_start_boot_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
