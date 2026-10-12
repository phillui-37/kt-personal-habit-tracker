package app.rota.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rota.data.backup.BackupRepository
import app.rota.data.prefs.AppSettings
import app.rota.data.prefs.SettingsStore
import app.rota.data.prefs.ThemeMode
import app.rota.data.prefs.WebDavConfig
import app.rota.data.repo.HabitRepository
import app.rota.notify.BackupWorker
import app.rota.notify.ReminderScheduler
import app.rota.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUi(
    val busy: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val backup: BackupRepository,
    private val habits: HabitRepository,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetRefresher,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _backupUi = MutableStateFlow(BackupUi())
    val backupUi: StateFlow<BackupUi> = _backupUi

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setTheme(mode) }
    }

    fun setWebDav(config: WebDavConfig) {
        viewModelScope.launch {
            settingsStore.setWebDav(config)
            BackupWorker.sync(context, config.autoDaily && config.isConfigured)
        }
    }

    fun exportTo(uri: Uri) = runBackup("Backup saved.") {
        context.contentResolver.openOutputStream(uri)?.use { backup.writeTo(it) }
            ?: error("Could not write the backup file.")
    }

    fun restoreFrom(uri: Uri) = runBackup("Restore complete.") {
        context.contentResolver.openInputStream(uri)?.use { backup.restoreFrom(it) }
            ?: error("Could not read the backup file.")
        afterRestore()
    }

    fun backupWebDav() = runBackup("Uploaded to WebDAV.") {
        backup.backupToWebDav()
    }

    fun restoreWebDav() = runBackup("Restored from WebDAV.") {
        backup.restoreFromWebDav()
        afterRestore()
    }

    fun testWebDav(config: WebDavConfig) = runBackup("Connected to WebDAV.") {
        backup.testWebDav(config)
    }

    fun canExactAlarms(): Boolean = reminders.canExact()

    private suspend fun afterRestore() {
        val bundles = habits.observeBundles(includeArchived = true).first()
        reminders.reschedule(bundles.map { it.habit })
        widgets.refresh()
    }

    private fun runBackup(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _backupUi.value = BackupUi(busy = true)
            _backupUi.value = try {
                block()
                BackupUi(message = success)
            } catch (error: Exception) {
                BackupUi(message = error.message ?: "Backup failed.", error = true)
            }
        }
    }
}
