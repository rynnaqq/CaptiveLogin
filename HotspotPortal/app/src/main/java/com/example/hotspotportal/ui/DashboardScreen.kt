package com.example.hotspotportal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hotspotportal.R
import com.example.hotspotportal.service.PortalState

@Composable
fun DashboardScreen(vm: PortalViewModel, onStop: () -> Unit) {
    val dash by vm.dashboard.collectAsStateWithLifecycle()
    val cfg by vm.settings.collectAsStateWithLifecycle()
    val active = dash.portalState == PortalState.ACTIVE
    // Stop has to be offered while merely ARMED too. While the portal waits for
    // a hotspot it is not ACTIVE, and gating the button on ACTIVE left an armed
    // portal with no way to stop it at all.
    val armed = active || dash.portalState == PortalState.ARMED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Status card: the one thing the admin looks at first.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(if (active) R.string.portal_active else R.string.portal_inactive),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when (dash.portalState) {
                        PortalState.STOPPED -> stringResource(R.string.portal_inactive)
                        PortalState.ARMED -> stringResource(R.string.waiting_hotspot)
                        PortalState.ERROR -> stringResource(R.string.err_setup_failed, "setup")
                        PortalState.ACTIVE -> stringResource(R.string.hotspot_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Capability rows
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRow(stringResource(R.string.root_available), dash.rootAvailable)
                StatusRow("iptables", dash.iptablesAvailable)
                Spacer(Modifier.height(2.dp))
                InfoRow(stringResource(R.string.iface_label), dash.hotspot?.interfaceName ?: stringResource(R.string.unknown))
                InfoRow(stringResource(R.string.gateway_label), dash.hotspot?.gatewayIp ?: stringResource(R.string.unknown))
                InfoRow(stringResource(R.string.subnet_label), dash.hotspot?.cidr ?: stringResource(R.string.unknown))
                InfoRow(stringResource(R.string.ssid_label), dash.hotspot?.ssid ?: stringResource(R.string.unknown))
            }
        }

        // Counts
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                Modifier.weight(1f),
                stringResource(R.string.connected_devices),
                dash.clientCount.toString(),
            )
            MetricCard(
                Modifier.weight(1f),
                stringResource(R.string.logged_in_devices),
                dash.loggedInCount.toString(),
            )
        }

        InfoRow(
            stringResource(R.string.session_summary, "${cfg.sessionDurationHours}h", "${cfg.idleTimeoutMinutes}m"),
            "",
        )

        Spacer(Modifier.height(4.dp))

        if (armed) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.stop_portal)) }
        } else {
            Button(
                onClick = { vm.activate() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.activate_portal)) }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = when (ok) {
                true -> "✓"
                false -> "✗"
                null -> "…"
            },
            color = when (ok) {
                true -> MaterialTheme.colorScheme.secondary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetricCard(modifier: Modifier, label: String, value: String) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
