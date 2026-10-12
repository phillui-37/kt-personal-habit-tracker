package app.rota.ui.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rota.data.repo.HabitRepository
import app.rota.domain.model.HabitBundle
import app.rota.notify.ReminderScheduler
import app.rota.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val habits: HabitRepository,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetRefresher,
) : ViewModel() {
    private val habitId: Long = savedStateHandle.get<Long>("habitId") ?: 0L

    val bundle: StateFlow<HabitBundle?> = habits.observeBundle(habitId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun archive() {
        viewModelScope.launch {
            val current = bundle.value ?: return@launch
            habits.archive(habitId, !current.habit.archived)
            if (!current.habit.archived) reminders.cancel(habitId) else reminders.schedule(current.habit.copy(archived = false))
            widgets.refresh()
        }
    }

    fun delete() {
        viewModelScope.launch {
            reminders.cancel(habitId)
            habits.deleteHabit(habitId)
            widgets.refresh()
        }
    }
}

@HiltViewModel
class HabitListViewModel @Inject constructor(
    habits: HabitRepository,
) : ViewModel() {
    val bundles = habits.observeBundles(includeArchived = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
