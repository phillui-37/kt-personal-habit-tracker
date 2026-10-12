package app.rota.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.net.toUri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.rota.data.prefs.AppSettings
import app.rota.data.prefs.RoutineTemplate
import app.rota.data.prefs.ThemeMode
import app.rota.data.prefs.WebDavConfig
import app.rota.ui.components.ChipRow
import app.rota.ui.components.SectionLabel

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    backupUi: BackupUi,
    canExact: Boolean,
    onTheme: (ThemeMode) -> Unit,
    onWebDav: (WebDavConfig) -> Unit,
    onExport: (Uri) -> Unit,
    onRestoreFile: (Uri) -> Unit,
    onBackupWebDav: () -> Unit,
    onRestoreWebDav: () -> Unit,
    onTestWebDav: (WebDavConfig) -> Unit,
    routines: List<RoutineHabitUi> = emptyList(),
    onAddRoutineItem: (Long, String) -> Unit = { _, _ -> },
    onRenameRoutineItem: (Long, Int, String) -> Unit = { _, _, _ -> },
    onDeleteRoutineItem: (Long, Int) -> Unit = { _, _ -> },
    onMoveRoutineItem: (Long, Int, Int) -> Unit = { _, _, _ -> },
    presets: List<RoutineTemplate> = emptyList(),
    onAddPreset: (String) -> Unit = {},
    onRenamePreset: (String, String) -> Unit = { _, _ -> },
    onDeletePreset: (String) -> Unit = {},
    onAddPresetItem: (String, String) -> Unit = { _, _ -> },
    onRenamePresetItem: (String, Int, String) -> Unit = { _, _, _ -> },
    onDeletePresetItem: (String, Int) -> Unit = { _, _ -> },
    onMovePresetItem: (String, Int, Int) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    var web by remember(settings.webDav) { mutableStateOf(settings.webDav) }
    var confirmFileRestore by remember { mutableStateOf<Uri?>(null) }
    var confirmWebRestore by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport(uri)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmFileRestore = uri
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val needsLocalNetwork = Build.VERSION.SDK_INT >= 37
    var localNetworkGranted by remember {
        mutableStateOf(
            !needsLocalNetwork ||
                ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var afterLocalNetworkGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val localNetworkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localNetworkGranted = granted
        val next = afterLocalNetworkGrant
        afterLocalNetworkGrant = null
        if (granted) next?.invoke()
    }
    fun requestLocalNetwork() {
        if (!localNetworkGranted) {
            localNetworkPermission.launch(ACCESS_LOCAL_NETWORK)
        }
    }

    fun withLocalNetwork(action: () -> Unit) {
        if (localNetworkGranted) {
            action()
        } else {
            afterLocalNetworkGrant = action
            localNetworkPermission.launch(ACCESS_LOCAL_NETWORK)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionLabel("Appearance")
            ChipRow(
                options = ThemeMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) to (settings.theme == it) },
                onSelect = { onTheme(ThemeMode.entries[it]) },
            )

            RoutinePresetSection(
                presets = presets,
                onAdd = onAddPreset,
                onRename = onRenamePreset,
                onDelete = onDeletePreset,
                onAddItem = onAddPresetItem,
                onRenameItem = onRenamePresetItem,
                onDeleteItem = onDeletePresetItem,
                onMoveItem = onMovePresetItem,
            )

            RoutineSettingsSection(
                routines = routines,
                onAdd = onAddRoutineItem,
                onRename = onRenameRoutineItem,
                onDelete = onDeleteRoutineItem,
                onMove = onMoveRoutineItem,
            )

            SectionLabel("Reminders")
            Text(
                "Each habit can fire at its own time. Android needs notification access, and exact alarms if you want them on the minute.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = { notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Allow notifications")
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = "package:${context.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }) {
                    Text(if (canExact) "Exact alarms allowed" else "Allow exact alarms")
                }
            }

            SectionLabel("Backup and restore")
            Text(
                "Export a JSON snapshot, or keep the same file on a WebDAV folder (Nextcloud, Synology, a NAS). Restore replaces every habit on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { export.launch("rota-backup.json") },
                    modifier = Modifier.weight(1f),
                    enabled = !backupUi.busy,
                ) { Text("Backup file") }
                OutlinedButton(
                    onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    modifier = Modifier.weight(1f),
                    enabled = !backupUi.busy,
                ) { Text("Restore file") }
            }

            SectionLabel("WebDAV")
            if (needsLocalNetwork) {
                Text(
                    "A NAS on your Wi-Fi needs local network access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { requestLocalNetwork() }) {
                    Text(if (localNetworkGranted) "Local network allowed" else "Allow local network")
                }
            }
            OutlinedTextField(
                value = web.url,
                onValueChange = { web = web.copy(url = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server URL") },
                singleLine = true,
                placeholder = { Text("https://nas.example/remote.php/dav/files/you") },
            )
            OutlinedTextField(
                value = web.username,
                onValueChange = { web = web.copy(username = it) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Username") },
                singleLine = true,
            )
            OutlinedTextField(
                value = web.password,
                onValueChange = { web = web.copy(password = it) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Password") },
                singleLine = true,
            )
            OutlinedTextField(
                value = web.remotePath,
                onValueChange = { web = web.copy(remotePath = it) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("File path") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Daily automatic backup", modifier = Modifier.weight(1f))
                Switch(
                    checked = web.autoDaily,
                    onCheckedChange = {
                        web = web.copy(autoDaily = it)
                        onWebDav(web.copy(autoDaily = it))
                        if (it) requestLocalNetwork()
                    },
                )
            }
            TextButton(onClick = { onWebDav(web) }) { Text("Save WebDAV settings") }
            TextButton(
                onClick = { withLocalNetwork { onTestWebDav(web) } },
                enabled = !backupUi.busy,
            ) { Text("Test connection") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        onWebDav(web)
                        withLocalNetwork { onBackupWebDav() }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !backupUi.busy,
                ) { Text("Backup now") }
                OutlinedButton(
                    onClick = {
                        onWebDav(web)
                        withLocalNetwork { confirmWebRestore = true }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !backupUi.busy,
                ) { Text("Restore now") }
            }
            backupUi.message?.let {
                Text(
                    it,
                    color = if (backupUi.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    confirmFileRestore?.let { uri ->
        RestoreDialog(
            onDismiss = { confirmFileRestore = null },
            onConfirm = {
                onRestoreFile(uri)
                confirmFileRestore = null
            },
        )
    }
    if (confirmWebRestore) {
        RestoreDialog(
            onDismiss = { confirmWebRestore = false },
            onConfirm = {
                onRestoreWebDav()
                confirmWebRestore = false
            },
        )
    }
}

@Composable
private fun RestoreDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace local data?") },
        text = { Text("Restore writes over every habit, check-in, and snooze on this phone. The current data is gone unless you backed it up first.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
