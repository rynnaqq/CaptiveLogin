package com.example.hotspotportal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hotspotportal.R
import com.example.hotspotportal.ui.theme.Ink
import com.example.hotspotportal.ui.theme.PopPink
import com.example.hotspotportal.ui.theme.White
import com.example.hotspotportal.ui.theme.brutalPanel

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    DASHBOARD(R.string.tab_dashboard, Icons.Filled.Dashboard),
    CLIENTS(R.string.tab_clients, Icons.Filled.List),
    USERS(R.string.tab_users, Icons.Filled.Person),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
    LOGS(R.string.tab_logs, Icons.Filled.Groups),
}

@Composable
fun PortalRoot(vm: PortalViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }

    Scaffold(
        bottomBar = { BrutalNavBar(current = tab, onSelect = { tab = it }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(vm, onStop = { vm.stop() })
                Tab.CLIENTS -> ClientsScreen(vm)
                Tab.USERS -> UsersScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
                Tab.LOGS -> LogsScreen(vm)
            }
        }
    }
}

/**
 * A flat bar with a hard ink rule on top, replacing M3's NavigationBar.
 *
 * NavigationBar cannot carry this look: it draws its own rounded selection pill
 * and an animated indicator, both of which read as soft Material. The active tab
 * is a pink block with the same hard border and offset shadow as every panel, so
 * the nav is unmistakably part of the same system.
 */
@Composable
private fun BrutalNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(White),
    ) {
        HorizontalDivider(thickness = 2.dp, color = Ink)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { t ->
                NavItem(
                    tab = t,
                    selected = t == current,
                    onClick = { onSelect(t) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 26.dp)
                .then(
                    if (selected) {
                        Modifier.brutalPanel(fill = PopPink, strokeWidth = 2.dp, lift = 3.dp)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                // White on pink is 3.5:1, which clears the 3:1 non-text bar for a
                // glyph this size. Ink on pink would be 0.2:1 and unreadable.
                tint = if (selected) White else Ink,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = Ink,
        )
    }
}
