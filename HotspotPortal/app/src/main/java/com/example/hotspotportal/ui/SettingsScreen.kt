package com.example.hotspotportal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hotspotportal.R
import com.example.hotspotportal.ui.theme.Ink
import com.example.hotspotportal.ui.theme.LabelText
import com.example.hotspotportal.ui.theme.PopPink
import com.example.hotspotportal.ui.theme.White
import com.example.hotspotportal.ui.theme.brutalPanel

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
        SettingsCard(
            jp = stringResource(R.string.jp_general),
            en = stringResource(R.string.settings_general),
        ) {
            NumberField(stringResource(R.string.jp_portal_title), stringResource(R.string.portal_title), cfg.title) { vm.setTitle(it) }
            NumberField(stringResource(R.string.jp_welcome), stringResource(R.string.portal_welcome), cfg.welcome) { vm.setWelcome(it) }
            NumberField(stringResource(R.string.jp_footer), stringResource(R.string.portal_footer), cfg.footer) { vm.setFooter(it) }
        }

        SettingsCard(
            jp = stringResource(R.string.jp_advanced),
            en = stringResource(R.string.settings_advanced),
        ) {
            NumberField(
                jp = stringResource(R.string.jp_iface_override),
                en = stringResource(R.string.iface_override),
                value = cfg.ifaceOverride,
                onChange = { vm.setIfaceOverride(it) },
            )
            NumberField(stringResource(R.string.jp_http_port), stringResource(R.string.http_port), cfg.httpPort.toString()) { vm.setHttpPort(it.toIntOrNull() ?: 8080) }
            NumberField(stringResource(R.string.jp_dns_port), stringResource(R.string.dns_port), cfg.dnsPort.toString()) { vm.setDnsPort(it.toIntOrNull() ?: 5353) }
            NumberField(stringResource(R.string.jp_tls_port), stringResource(R.string.tls_port), cfg.tlsPort.toString()) { vm.setTlsPort(it.toIntOrNull() ?: 8443) }

            ToggleRow(stringResource(R.string.jp_debug_logging), stringResource(R.string.debug_logging), cfg.debugLogging) { vm.setDebugLogging(it) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 2.dp, color = Ink)
            OutlinedButton(onClick = { vm.rebuildRules() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.jp_reset_rules))
            }
        }

        SettingsCard(
            jp = stringResource(R.string.jp_auto_start),
            en = stringResource(R.string.auto_start_boot),
        ) {
            ToggleRow(
                jp = stringResource(R.string.jp_auto_start),
                en = stringResource(R.string.auto_start_boot),
                checked = cfg.autoStartOnBoot,
            ) { vm.setAutoStart(it) }
            Text(
                stringResource(R.string.auto_start_boot_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(jp: String, en: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .brutalPanel(fill = MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .background(PopPink)
                .border(BorderStroke(2.dp, Ink), RectangleShape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            LabelText(
                jp = jp,
                en = en,
                jpStyle = MaterialTheme.typography.labelMedium,
                enStyle = MaterialTheme.typography.labelSmall,
                jpColor = White,
                enColor = White,
            )
        }
        HorizontalDivider(thickness = 2.dp, color = Ink)
        content()
    }
}

/**
 * Label above the field rather than floating inside it.
 *
 * M3's floating label cuts a gap in the field's own border, which breaks the
 * one thing this design is built on.
 */
@Composable
private fun NumberField(jp: String, en: String, value: String, onChange: (String) -> Unit) {
    Column {
        LabelText(
            jp = jp,
            en = en,
            jpStyle = MaterialTheme.typography.labelMedium,
            enStyle = MaterialTheme.typography.labelSmall,
            jpColor = Ink,
            enColor = Ink,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun ToggleRow(jp: String, en: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelText(
            jp = jp,
            en = en,
            jpStyle = MaterialTheme.typography.bodyMedium,
            enStyle = MaterialTheme.typography.labelSmall,
            jpColor = Ink,
            enColor = Ink,
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
