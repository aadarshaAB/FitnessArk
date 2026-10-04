package com.fitnessark.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fitnessark.BuildConfig
import com.fitnessark.data.model.UnitSystem
import com.fitnessark.data.repository.ImportMode
import com.fitnessark.data.repository.ReminderSettings
import com.fitnessark.data.repository.ThemeMode
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.util.DateUtils
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** True if notifications can be shown: always pre-Android 13, otherwise only once granted. */
private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    // The backup file the user picked, waiting for them to choose merge or replace
    var pendingImportUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Reminders (F5)
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    // The time to turn the reminder on with, once a just-requested notification permission is granted
    var pendingReminderTime by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val time = pendingReminderTime
        pendingReminderTime = null
        if (granted && time != null) viewModel.setReminder(enabled = true, hour = time.first, minute = time.second)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = viewModel.exportData()
                result.onSuccess { zipFile -> viewModel.writeExportToUri(zipFile, uri) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance Section
            SettingsSectionHeader("Appearance")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = state.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) })
                        Text("Dark", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = state.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) })
                        Text("Light", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = state.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) })
                        Text("System", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            SettingsSectionHeader("Units")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        "Measurement units",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UnitSystem.entries.forEach { system ->
                            RadioButton(selected = state.unitSystem == system,
                                onClick = { viewModel.setUnitSystem(system) })
                            Text("${system.label} (${system.hint})",
                                style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(12.dp))
                        }
                    }
                    Text(
                        "Your data is always stored in kg and cm, so you can switch any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            SettingsSectionHeader("Reminders")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ReminderSettingRow(
                    settings = state.reminderSettings,
                    onToggle = { wantsOn ->
                        if (wantsOn && !notificationsAllowed(context)) {
                            pendingReminderTime = state.reminderSettings.hour to state.reminderSettings.minute
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setReminder(enabled = wantsOn)
                        }
                    },
                    onTimeClick = { showTimePicker = true }
                )
            }

            // Data Management Section
            SettingsSectionHeader("Data Management")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    SettingsActionItem(
                        icon = Icons.Default.FileUpload,
                        title = "Export Data",
                        subtitle = "Backup all data as a ZIP file",
                        isLoading = state.isExporting,
                        onClick = {
                            exportLauncher.launch("fitness_ark_backup_${System.currentTimeMillis()}.zip")
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsActionItem(
                        icon = Icons.Default.FileDownload,
                        title = "Import Data",
                        subtitle = "Restore from a backup ZIP file",
                        isLoading = state.isImporting,
                        onClick = {
                            importLauncher.launch(arrayOf("application/zip", "*/*"))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsActionItem(
                        icon = Icons.Default.DeleteForever,
                        title = "Clear All Data",
                        subtitle = "Delete all measurements and photos",
                        isDestructive = true,
                        onClick = { showClearDialog = true }
                    )
                }
            }

            // About Section
            SettingsSectionHeader("About")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutRow("Version", BuildConfig.VERSION_NAME)
                    AboutRow("Total Entries", "${state.entryCount}")
                    AboutRow("Storage Used", formatBytes(state.appSizeBytes))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = state.reminderSettings.hour,
            initialMinute = state.reminderSettings.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    viewModel.setReminder(enabled = true, hour = timeState.hour, minute = timeState.minute)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            icon = { Icon(Icons.Default.FileDownload, null) },
            title = { Text("Import backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Merge adds the backup to what's on this phone. If a day is in both, " +
                        "the backup's values are used, and anything the backup doesn't have for " +
                        "that day is kept.")
                    Text("Replace deletes everything on this phone first, so you end up with " +
                        "exactly what's in the backup.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.importData(uri, ImportMode.MERGE)
                    pendingImportUri = null
                }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            viewModel.importData(uri, ImportMode.REPLACE)
                            pendingImportUri = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Replace") }
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.Warning, "Warning", tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Data?") },
            text = {
                Text("This will permanently delete ALL measurements, photos, and progress data. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingRow(
    settings: ReminderSettings,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Daily check-in reminder", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Skipped automatically once you've checked in that day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = onToggle)
        }
        if (settings.enabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onTimeClick) {
                Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(DateUtils.formatTimeOfDay(settings.hour, settings.minute))
            }
        }
    }
}

@Composable
fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isDestructive: Boolean = false
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (isDestructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = CyanPrimary)
            }
        }
    }
}

@Composable
fun AboutRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
        else -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
    }
}
