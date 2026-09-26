package com.example.hotspotportal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hotspotportal.R

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
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
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
