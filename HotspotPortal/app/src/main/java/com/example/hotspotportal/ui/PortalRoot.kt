package com.example.hotspotportal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hotspotportal.R
import com.example.hotspotportal.ui.theme.Ink
import com.example.hotspotportal.ui.theme.LabelText
import com.example.hotspotportal.ui.theme.Parallelogram
import com.example.hotspotportal.ui.theme.PopPink
import com.example.hotspotportal.ui.theme.White
import com.example.hotspotportal.ui.theme.brutalPanel

private enum class Tab(val labelRes: Int, val jpRes: Int, val icon: ImageVector) {
    DASHBOARD(R.string.tab_dashboard, R.string.jp_tab_dashboard, Icons.Filled.Dashboard),
    CLIENTS(R.string.tab_clients, R.string.jp_tab_clients, Icons.Filled.List),
    USERS(R.string.tab_users, R.string.jp_tab_users, Icons.Filled.Person),
    SETTINGS(R.string.tab_settings, R.string.jp_tab_settings, Icons.Filled.Settings),
    LOGS(R.string.tab_logs, R.string.jp_tab_logs, Icons.Filled.Groups),
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
 * and an animated indicator, both of which read as soft Material.
 *
 * The anime comes from three cheap moves that all sit on top of the same hard
 * border language rather than fighting it: the active plate is skewed into a
 * parallelogram the way shonen UI panels are, it is drawn larger than its
 * neighbours so the current tab reads as raised, and the tabs are separated by
 * full-height ink rules so the bar looks like a comic panel grid instead of a
 * list.
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
                .height(NAV_BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEachIndexed { i, t ->
                // Manga panel dividers. The outer edges get no rule - the bar's
                // own top divider already frames it.
                if (i > 0) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(NAV_BAR_HEIGHT)
                            .background(Ink),
                    )
                }
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

/**
 * Tall enough for the plate, the gap and both label lines, so nothing is clipped
 * and every tab clears the 48dp minimum touch target.
 */
private val NAV_BAR_HEIGHT = 68.dp

/** Fixed slot the icon plate is centred in, so labels line up across tabs. */
private val PLATE_ROW = 32.dp

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
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Fixed-height row so every label starts on the same baseline. The active
        // plate is drawn larger and centred inside it - without this the taller
        // plate pushed its own label down and the row of labels came out ragged.
        Box(
            modifier = Modifier.height(PLATE_ROW),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(if (selected) 52.dp else 42.dp)
                    .height(if (selected) 30.dp else 24.dp)
                    .then(
                        if (selected) {
                            Modifier.brutalPanel(
                                fill = PopPink,
                                strokeWidth = 2.dp,
                                lift = 3.dp,
                                shape = Parallelogram(LEAN),
                            )
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
                    modifier = Modifier.size(if (selected) 19.dp else 17.dp),
                )
            }
        }
        // The screen is ~393dp wide, so each of the five items gets ~78dp.
        // ダッシュボード is 7 glyphs and wraps at labelMedium, which pushed every
        // English gloss onto a different baseline - hence the explicit sizes.
        LabelText(
            jp = stringResource(tab.jpRes),
            en = stringResource(tab.labelRes),
            jpStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            enStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            jpColor = Ink,
            enColor = Ink,
            maxLines = 1,
        )
    }
}

/** tan(9deg) - the lean of the active plate. */
private const val LEAN = 0.158f
