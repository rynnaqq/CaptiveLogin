package com.example.hotspotportal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hotspotportal.R
import com.example.hotspotportal.service.PortalState
import com.example.hotspotportal.ui.theme.BrutalButton
import com.example.hotspotportal.ui.theme.Danger
import com.example.hotspotportal.ui.theme.Go
import com.example.hotspotportal.ui.theme.Ink
import com.example.hotspotportal.ui.theme.LabelText
import com.example.hotspotportal.ui.theme.Navy
import com.example.hotspotportal.ui.theme.Ocean
import com.example.hotspotportal.ui.theme.PanelTint
import com.example.hotspotportal.ui.theme.PopLemon
import com.example.hotspotportal.ui.theme.PopPink
import com.example.hotspotportal.ui.theme.White
import com.example.hotspotportal.ui.theme.brutalPanel

/**
 * The app's identity strip: the mark and the name on a hard-bordered ocean block.
 *
 * The artwork sits in a sharp square rather than a circle - one radius scale for
 * the whole app, no exceptions.
 *
 * The portal state is deliberately NOT repeated here. This header used to print
 * "Portal inactive" in lemon directly above a status card whose headline read
 * "Portal inactive" in 24sp - the same two words twice, a couple of centimetres
 * apart, in two sizes and two colours. State lives in exactly one place on this
 * screen, and that place is the card below.
 */
@Composable
private fun BrandedHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalPanel(fill = Navy)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_rimuru),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(62.dp)
                .border(BorderStroke(2.dp, Ink), RectangleShape),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = White,
        )
    }
}

/**
 * A section heading marked by a solid pink block.
 *
 * The block is the accent; the text is left in plain sentence case. An earlier
 * pass put katakana in a pink chip here and shouted the English half in caps,
 * which with the heavy borders read as noise rather than character.
 */
@Composable
private fun SectionLabel(jp: String, en: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 14.dp, height = 14.dp)
                .background(PopPink)
                .border(BorderStroke(2.dp, Ink), RectangleShape),
        )
        LabelText(jp = jp, en = en)
    }
}

private data class Status(val jpRes: Int, val en: String, val fill: Color, val ink: Color)

@Composable
fun DashboardScreen(vm: PortalViewModel, onStop: () -> Unit) {
    val dash by vm.dashboard.collectAsStateWithLifecycle()
    val active = dash.portalState == PortalState.ACTIVE
    // Stop has to be offered while merely ARMED too. While the portal waits for
    // a hotspot it is not ACTIVE, and gating the button on ACTIVE left an armed
    // portal with no way to stop it at all.
    val armed = active || dash.portalState == PortalState.ARMED

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Every label now carries an English gloss underneath, which made the
            // screen taller than the viewport. Without this the column silently
            // clips and the Activate/Stop button - the primary action - falls off
            // the bottom and cannot be reached at all.
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandedHeader()

        // The one thing the admin looks at first. Fill carries the state, so it
        // reads from across the room: green live, lemon waiting, red stopped.
        val (statusJp, statusEn, statusFill, statusInk) = when (dash.portalState) {
            PortalState.ACTIVE -> Status(R.string.jp_portal_active, "ACTIVE", Go, White)
            PortalState.ARMED -> Status(R.string.jp_portal_armed, "ARMED", PopLemon, Ink)
            else -> Status(R.string.jp_portal_inactive, "INACTIVE", Danger, White)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .brutalPanel(fill = statusFill, lift = 6.dp)
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = statusEn,
                    style = MaterialTheme.typography.headlineSmall,
                    color = statusInk,
                )
                Text(
                    text = stringResource(statusJp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dash.portalState == PortalState.ARMED) Ink else statusInk,
                )
                Text(
                    // Only worth a second line when it says something the
                    // headline does not; "Portal inactive" twice is noise.
                    text = when (dash.portalState) {
                        PortalState.STOPPED -> stringResource(R.string.dashboard_idle_hint)
                        PortalState.ARMED -> stringResource(R.string.waiting_hotspot)
                        PortalState.ERROR -> stringResource(R.string.err_setup_failed, "setup")
                        PortalState.ACTIVE -> dash.hotspot?.ssid
                            ?: stringResource(R.string.hotspot_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dash.portalState == PortalState.ARMED) Ink else statusInk,
                )
            }
        }

        // Capability rows
        Column(
            Modifier
                .fillMaxWidth()
                .brutalPanel(fill = White)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel(
                jp = stringResource(R.string.jp_network),
                en = stringResource(R.string.network_label),
            )
            StatusRow(
                jp = stringResource(R.string.jp_root_available),
                en = stringResource(R.string.root_available),
                ok = dash.rootAvailable,
            )
            StatusRow(jp = "iptables", en = "iptables", ok = dash.iptablesAvailable)
            HorizontalDivider(thickness = 2.dp, color = Ink)
            val info = listOf(
                Triple(
                    stringResource(R.string.jp_iface),
                    stringResource(R.string.iface_label),
                    dash.hotspot?.interfaceName ?: stringResource(R.string.unknown),
                ),
                Triple(
                    stringResource(R.string.jp_gateway),
                    stringResource(R.string.gateway_label),
                    dash.hotspot?.gatewayIp ?: stringResource(R.string.unknown),
                ),
                Triple(
                    stringResource(R.string.jp_subnet),
                    stringResource(R.string.subnet_label),
                    dash.hotspot?.cidr ?: stringResource(R.string.unknown),
                ),
                Triple(
                    stringResource(R.string.jp_ssid),
                    stringResource(R.string.ssid_label),
                    dash.hotspot?.ssid ?: stringResource(R.string.unknown),
                ),
            ).filter { it.third.isNotEmpty() }
            info.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = Ink)
                InfoRow(jp = row.first, en = row.second, value = row.third)
            }
        }

        // Counts
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                Modifier.weight(1f),
                stringResource(R.string.jp_connected_devices),
                stringResource(R.string.connected_devices),
                dash.clientCount.toString(),
            )
            MetricCard(
                Modifier.weight(1f),
                stringResource(R.string.jp_logged_in),
                stringResource(R.string.logged_in_devices),
                dash.loggedInCount.toString(),
            )
        }

        if (armed) {
            BrutalButton(
                jp = stringResource(R.string.jp_stop_portal),
                en = stringResource(R.string.stop_portal),
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                fill = White,
                contentColor = Danger,
            )
        } else {
            BrutalButton(
                jp = stringResource(R.string.jp_activate_portal),
                en = stringResource(R.string.activate_portal),
                onClick = { vm.activate() },
                modifier = Modifier.fillMaxWidth(),
                fill = PopPink,
                contentColor = White,
            )
        }
    }
}

@Composable
private fun StatusRow(jp: String, en: String, ok: Boolean?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelText(jp = jp, en = en)
        // A hard chip rather than a bare glyph: the tick has to survive being
        // glanced at, and a coloured square is the brutalist equivalent of a badge.
        val chipFill = when (ok) {
            true -> Go
            false -> Danger
            null -> White
        }
        Box(
            Modifier
                .background(chipFill)
                .border(BorderStroke(2.dp, Ink), RectangleShape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            LabelText(
                jp = when (ok) {
                    true -> stringResource(R.string.jp_ok)
                    false -> stringResource(R.string.jp_no)
                    null -> "—"
                },
                // The OK hand sign rather than the word: it is the gesture the
                // Japanese side of this app already speaks, and an emoji needs
                // no asset, no decoder and no dependency. An animated GIF here
                // would mean Coil plus a binary for a value that changes twice
                // a day.
                en = when (ok) {
                    true -> "👌"
                    false -> "👎"
                    null -> "❓"
                },
                jpStyle = MaterialTheme.typography.labelSmall,
                enStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 18.sp),
                jpColor = if (ok == null) Ink else White,
                enColor = if (ok == null) Ink else White,
            )
        }
    }
}

@Composable
private fun InfoRow(jp: String, en: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelText(jp = jp, en = en)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
    }
}

@Composable
private fun MetricCard(modifier: Modifier, jp: String, en: String, value: String) {
    Column(
        modifier
            .brutalPanel(fill = PanelTint)
            .padding(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = Ocean,
        )
        LabelText(jp = jp, en = en)
    }
}
