package com.example.hotspotportal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.hotspotportal.auth.CredentialVerdict
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hotspotportal.R
import com.example.hotspotportal.store.PortalUserEntity

@Composable
fun UsersScreen(vm: PortalViewModel) {
    val users by vm.users.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PortalUserEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PortalUserEntity?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // First-run card: the app ships with no credentials at all.
    if (users.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.first_user_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.first_user_card_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.new_user))
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.new_user))
            }
            TextButton(onClick = { verifying = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.verify_credentials))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(users, key = { it.id }) { user ->
                    UserRow(
                        user = user,
                        onEdit = { editing = user },
                        onToggle = { vm.updateUser(user.copy(enabled = !user.enabled)) { } },
                        onDelete = { deleting = user },
                        onCopy = {
                            copyToClipboard(context, "${user.username} credentials copied")
                        },
                    )
                }
            }
        }
    }

    if (verifying) {
        VerifyCredentialsDialog(
            check = { u, p, done -> vm.checkCredentials(u, p, done) },
            onDismiss = { verifying = false },
        )
    }

    if (creating) {
        UserEditorDialog(
            existing = null,
            generate = vm::generatePassword,
            onDismiss = { creating = false },
            onSave = { u, p, limit, exp, note ->
                vm.createUser(u, p, limit, exp, note) { creating = false }
            },
        )
    }

    editing?.let { user ->
        UserEditorDialog(
            existing = user,
            generate = vm::generatePassword,
            onDismiss = { editing = null },
            onSave = { _, p, limit, exp, note ->
                val updated = user.copy(deviceLimit = limit, expiresAt = exp, note = note)
                if (p.isNotBlank()) vm.setPassword(user, p) { editing = null } else vm.updateUser(updated) { editing = null }
            },
        )
    }

    deleting?.let { user ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.confirm_delete_user_title)) },
            text = { Text(stringResource(R.string.confirm_delete_user_body, user.username)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteUser(user); deleting = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun UserRow(
    user: PortalUserEntity,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(user.username, fontWeight = FontWeight.SemiBold)
                Switch(checked = user.enabled, onCheckedChange = { onToggle() })
            }
            Text(
                buildString {
                    append("limit ${user.deviceLimit}")
                    if (user.expired) append(" • expired")
                    if (user.note.isNotBlank()) append(" • ${user.note}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit_user)) }
                TextButton(onClick = onCopy) { Text(stringResource(R.string.copy_credentials)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

/**
 * Admin-only credential check.
 *
 * A stored password cannot be shown - only its BCrypt hash exists - so this
 * answers the question that actually matters when a guest cannot get in: is
 * the username wrong, or the password? The portal's own login form must never
 * answer this, or the LAN could enumerate accounts.
 */
@Composable
private fun VerifyCredentialsDialog(
    check: (String, String, (CredentialVerdict) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<CredentialVerdict?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.verify_credentials)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.verify_credentials_help),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; verdict = null },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; verdict = null },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                verdict?.let {
                    Text(
                        stringResource(
                            when (it) {
                                CredentialVerdict.CORRECT -> R.string.verdict_correct
                                CredentialVerdict.NO_SUCH_USER -> R.string.verdict_no_such_user
                                CredentialVerdict.WRONG_PASSWORD -> R.string.verdict_wrong_password
                                CredentialVerdict.DISABLED -> R.string.verdict_disabled
                                CredentialVerdict.EXPIRED -> R.string.verdict_expired
                            }
                        ),
                        color = if (it == CredentialVerdict.CORRECT) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { check(username.trim(), password) { verdict = it } },
                enabled = username.isNotBlank() && password.isNotEmpty(),
            ) { Text(stringResource(R.string.verify)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun UserEditorDialog(
    existing: PortalUserEntity?,
    generate: () -> String,
    onDismiss: () -> Unit,
    onSave: (username: String, password: String, deviceLimit: Int, expiresAt: Long?, note: String) -> Unit,
) {
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf((existing?.deviceLimit ?: 1).toString()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_user else R.string.edit_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing == null) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username)) },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(
                            if (existing == null) stringResource(R.string.password)
                            else stringResource(R.string.edit_user)
                        )
                    },
                    singleLine = true,
                )
                TextButton(onClick = { password = generate() }) { Text(stringResource(R.string.generate_password)) }
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.device_limit)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note)) },
                    singleLine = true,
                )
                if (password.isNotEmpty()) {
                    Text(
                        password,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (existing != null || username.isNotBlank()) &&
                    (existing != null || password.isNotBlank()),
                onClick = {
                    onSave(
                        username.ifBlank { existing?.username.orEmpty() },
                        password,
                        limit.toIntOrNull() ?: 1,
                        existing?.expiresAt,
                        note,
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun copyToClipboard(context: Context, label: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("portal", label))
}
