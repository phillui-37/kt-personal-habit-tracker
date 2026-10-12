package app.rota.ui.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rota.data.prefs.RoutineTemplate
import app.rota.data.prefs.SettingsStore
import app.rota.data.repo.HabitRepository
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitType
import app.rota.domain.model.Period
import app.rota.notify.ReminderScheduler
import app.rota.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HabitDraft(
    val id: Long = 0,
    val name: String = "",
    val note: String = "",
    val category: String = "",
    val type: HabitType = HabitType.CHECK,
    val target: Int = 1,
    val period: Period = Period.DAILY,
    val timesPerPeriod: Int = 1,
    val intervalDays: Int = 3,
    val weekdaysMask: Int = 0,
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 8 * 60,
    val colorIndex: Int = 0,
    val routine: List<String> = emptyList(),
    val createdAtEpochDay: Long = 0,
    val archived: Boolean = false,
    val rotationIndex: Int = 0,
    val sortOrder: Int = 0,
)

@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val habits: HabitRepository,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetRefresher,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val habitId: Long = savedStateHandle.get<Long>("habitId") ?: 0L
    private val _draft = MutableStateFlow(HabitDraft(id = habitId))
    val draft: StateFlow<HabitDraft> = _draft.asStateFlow()
    val categorySuggestions: StateFlow<List<String>> = habits.observeBundles(includeArchived = true)
        .map { bundles ->
            bundles.map { it.habit.category.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val routineTemplates: StateFlow<List<RoutineTemplate>> = settingsStore.settings
        .map { it.routineTemplates }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (habitId != 0L) {
            viewModelScope.launch {
                val bundle = habits.getBundle(habitId) ?: return@launch
                val h = bundle.habit
                _draft.value = HabitDraft(
                    id = h.id,
                    name = h.name,
                    note = h.note,
                    category = h.category,
                    type = h.type,
                    target = h.target,
                    period = h.period,
                    timesPerPeriod = h.timesPerPeriod,
                    intervalDays = h.intervalDays,
                    weekdaysMask = h.weekdaysMask,
                    reminderEnabled = h.reminderMinutes != null,
                    reminderMinutes = h.reminderMinutes ?: (8 * 60),
                    colorIndex = h.colorIndex,
                    routine = bundle.routine.map { it.title },
                    createdAtEpochDay = h.createdAtEpochDay,
                    archived = h.archived,
                    rotationIndex = h.rotationIndex,
                    sortOrder = h.sortOrder,
                )
            }
        }
    }

    fun update(transform: (HabitDraft) -> HabitDraft) {
        _draft.update(transform)
    }

    fun applyTemplate(titles: List<String>) {
        _draft.update { it.copy(routine = titles) }
    }

    suspend fun save(): Boolean {
        val d = _draft.value
        if (d.name.isBlank()) return false
        val habit = Habit(
            id = d.id,
            name = d.name.trim(),
            note = d.note.trim(),
            category = d.category.trim(),
            type = d.type,
            target = d.target,
            period = d.period,
            timesPerPeriod = d.timesPerPeriod,
            intervalDays = d.intervalDays,
            weekdaysMask = d.weekdaysMask,
            reminderMinutes = d.reminderMinutes.takeIf { d.reminderEnabled },
            colorIndex = d.colorIndex,
            archived = d.archived,
            rotationIndex = d.rotationIndex,
            createdAtEpochDay = d.createdAtEpochDay,
            sortOrder = d.sortOrder,
        )
        val id = habits.saveHabit(habit, d.routine)
        reminders.schedule(habit.copy(id = id))
        widgets.refresh()
        return true
    }
}
