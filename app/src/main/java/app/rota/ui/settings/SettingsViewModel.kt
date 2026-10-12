package app.rota.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rota.data.backup.BackupRepository
import app.rota.data.prefs.AppSettings
import app.rota.data.prefs.RoutineTemplate
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

data class BackupUi(
    val busy: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

data class RoutineHabitUi(
    val id: Long,
    val name: String,
    val items: List<String>,
    val currentTitle: String?,
    val archived: Boolean,
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

    val routines: StateFlow<List<RoutineHabitUi>> = habits.observeBundles(includeArchived = true)
        .map { bundles ->
            bundles.map { bundle ->
                RoutineHabitUi(
                    id = bundle.habit.id,
                    name = bundle.habit.name,
                    items = bundle.routine.map { it.title },
                    currentTitle = bundle.currentRoutine?.title,
                    archived = bundle.habit.archived,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _backupUi = MutableStateFlow(BackupUi())
    val backupUi: StateFlow<BackupUi> = _backupUi
    private val routineEdits = Mutex()
    private val presetEdits = Mutex()

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

    fun addRoutinePreset(name: String) = editPresets { current ->
        val trimmed = name.trim()
        if (trimmed.isEmpty()) current
        else current + RoutineTemplate(id = UUID.randomUUID().toString(), name = trimmed)
    }

    fun renameRoutinePreset(id: String, name: String) = editPresets { current ->
        val trimmed = name.trim()
        if (trimmed.isEmpty()) current
        else current.map { if (it.id == id) it.copy(name = trimmed) else it }
    }

    fun deleteRoutinePreset(id: String) = editPresets { current ->
        current.filter { it.id != id }
    }

    fun addPresetItem(id: String, title: String) = editPresetItems(id) { it + title }

    fun renamePresetItem(id: String, index: Int, title: String) = editPresetItems(id) { items ->
        items.mapIndexed { itemIndex, current -> if (itemIndex == index) title else current }
    }

    fun deletePresetItem(id: String, index: Int) = editPresetItems(id) { items ->
        items.filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    fun movePresetItem(id: String, from: Int, to: Int) = editPresetItems(id) { items ->
        if (from !in items.indices || to !in items.indices || from == to) {
            items
        } else {
            items.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun addRoutineItem(habitId: Long, title: String) = editRoutine(habitId) { it + title }

    fun renameRoutineItem(habitId: Long, index: Int, title: String) = editRoutine(habitId) { items ->
        items.mapIndexed { itemIndex, current -> if (itemIndex == index) title else current }
    }

    fun deleteRoutineItem(habitId: Long, index: Int) = editRoutine(habitId) { items ->
        items.filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    fun moveRoutineItem(habitId: Long, from: Int, to: Int) = editRoutine(habitId) { items ->
        if (from !in items.indices || to !in items.indices || from == to) {
            items
        } else {
            items.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    private fun editPresets(transform: (List<RoutineTemplate>) -> List<RoutineTemplate>) {
        viewModelScope.launch {
            presetEdits.withLock {
                val current = settingsStore.settings.first().routineTemplates
                val next = transform(current)
                if (next != current) settingsStore.setRoutineTemplates(next)
            }
        }
    }

    private fun editPresetItems(id: String, transform: (List<String>) -> List<String>) = editPresets { current ->
        current.map { preset ->
            if (preset.id != id) {
                preset
            } else {
                preset.copy(
                    items = transform(preset.items).map { it.trim() }.filter { it.isNotEmpty() },
                )
            }
        }
    }

    private fun editRoutine(habitId: Long, transform: (List<String>) -> List<String>) {
        viewModelScope.launch {
            routineEdits.withLock {
                val bundle = habits.getBundle(habitId) ?: return@withLock
                val next = transform(bundle.routine.map { it.title })
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                habits.saveHabit(bundle.habit, next)
                widgets.refresh()
            }
        }
    }

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
