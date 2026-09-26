package com.example.hotspotportal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hotspotportal.R
import com.example.hotspotportal.clients.ObservedClient
import com.example.hotspotportal.ui.theme.Danger
import com.example.hotspotportal.ui.theme.Ink
import com.example.hotspotportal.ui.theme.PanelTint
import com.example.hotspotportal.ui.theme.PopPink
import com.example.hotspotportal.ui.theme.White
import com.example.hotspotportal.ui.theme.brutalPanel

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            },
            dismissButton = {
                TextButton(onClick = { kicking = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ClientRow(client: ObservedClient, onKick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .brutalPanel(fill = White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                client.info.ip,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            // A hard chip, not a soft AssistChip: the signed-in state is the
            // thing the admin scans this list for.
            val (chipFill, chipInk) = if (client.authorized) PopPink to White else PanelTint to Ink
            Box(
                Modifier
                    .background(chipFill)
                    .border(BorderStroke(2.dp, Ink), RectangleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = if (client.authorized) {
                        stringResource(R.string.state_logged_in, client.username.orEmpty())
                    } else {
                        stringResource(R.string.state_blocked)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = chipInk,
                )
            }
        }
        Text(
            client.info.mac,
            style = MaterialTheme.typography.bodySmall,
            color = Ink,
        )
        // Liveness is measured, not inferred: a row the kernel is no longer
        // sure about has been probed, and a device that stopped answering is
        // shown as offline instead of looking like a live guest whose timer
        // is stuck.
        if (!client.info.present) {
            Text(
                stringResource(R.string.state_offline, client.info.state.lowercase()),
                style = MaterialTheme.typography.bodySmall,
                color = Danger,
                fontWeight = FontWeight.Bold,
            )
        }

        if (client.authorized) {
            HorizontalDivider(thickness = 2.dp, color = Ink)
            TextButton(
                onClick = onKick,
                contentPadding = PaddingValues(0.dp),
            ) { Text(stringResource(R.string.action_kick), color = Danger) }
        }
    }
}

/**
 * The empty state is a bordered block rather than floating grey text, so a list
 * with nothing in it still looks like part of the app instead of a failed load.
 */
@Composable
internal fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .brutalPanel(fill = PanelTint)
                .padding(20.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
    }
}
