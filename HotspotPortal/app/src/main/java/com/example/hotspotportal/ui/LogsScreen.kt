package com.example.hotspotportal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hotspotportal.R
import com.example.hotspotportal.ui.theme.Danger
import com.example.hotspotportal.ui.theme.Ink
import com.example.hotspotportal.ui.theme.White
import com.example.hotspotportal.ui.theme.brutalPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(vm: PortalViewModel) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    if (logs.isEmpty()) {
        EmptyState(
            jp = stringResource(R.string.jp_logs_empty),
            en = stringResource(R.string.logs_empty),
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                onClick = {
                    val text = logs.joinToString("\n") { "${fmt.format(Date(it.timestamp))} ${it.event} ${it.detail}" }
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("portal-logs", text))
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
            ) { Text(stringResource(R.string.copy_logs)) }
            TextButton(
                onClick = { vm.clearLogs() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
            ) { Text(stringResource(R.string.clear_logs), color = Danger) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(logs, key = { it.id }) { entry ->
                val isError = entry.level == "ERROR"
                Column(
                    Modifier
                        .fillMaxWidth()
                        .brutalPanel(fill = if (isError) Danger.copy(alpha = 0.08f) else White)
                        .padding(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            fmt.format(Date(entry.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Ink,
                        )
                        Box(
                            Modifier
                                .background(if (isError) Danger else Ink)
                                .border(BorderStroke(1.dp, Ink), RectangleShape)
                                .padding(horizontal = 5.dp),
                        ) {
                            Text(
                                entry.event,
                                style = MaterialTheme.typography.labelSmall,
                                color = White,
                            )
                        }
                    }
                    if (entry.detail.isNotBlank()) {
                        Text(
                            entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
