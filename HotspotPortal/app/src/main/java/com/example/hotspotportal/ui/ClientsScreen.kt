package com.example.hotspotportal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.hotspotportal.clients.ObservedClient
import java.util.concurrent.TimeUnit

@Composable
fun ClientsScreen(vm: PortalViewModel) {
    val clients by vm.clients.collectAsStateWithLifecycle()
    var kicking by remember { mutableStateOf<ObservedClient?>(null) }

    if (clients.isEmpty()) {
        EmptyState(stringResource(R.string.clients_empty))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(clients, key = { it.info.mac }) { client ->
            ClientRow(client, onKick = { kicking = client })
        }
    }

    kicking?.let { target ->
        AlertDialog(
            onDismissRequest = { kicking = null },
            title = { Text(stringResource(R.string.confirm_kick_title)) },
            text = { Text(stringResource(R.string.confirm_kick_body)) },
            confirmButton = {
                TextButton(onClick = {
                    // Kicking removes the whitelist rule immediately; the
                    // device is blocked again on its next HTTP request.
                    vm.kick(target.info.mac)
                    kicking = null
                }) { Text(stringResource(R.string.confirm)) }
            },            dismissButton = {
                TextButton(onClick = { kicking = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ClientRow(client: ObservedClient, onKick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(client.info.ip, fontWeight = FontWeight.SemiBold)
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (client.authorized) {
                                stringResource(R.string.state_logged_in, client.username.orEmpty())
                            } else {
                                stringResource(R.string.state_blocked)
                            }
                        )
                    },
                )
            }
            Text(
                client.info.mac,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            client.expiresAt?.let { exp ->
                val left = ((exp - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                Text(
                    stringResource(R.string.time_remaining, formatDuration(left)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (client.authorized) {
                TextButton(onClick = onKick) { Text(stringResource(R.string.action_kick)) }
            }
        }
    }
}

internal fun formatDuration(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
internal fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
