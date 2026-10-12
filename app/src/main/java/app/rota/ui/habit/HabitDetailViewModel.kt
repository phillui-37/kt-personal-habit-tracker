package app.rota.ui.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rota.data.repo.HabitRepository
import app.rota.domain.CategoryFilter
import app.rota.domain.model.HabitBundle
import app.rota.notify.ReminderScheduler
import app.rota.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

data class HabitListUi(
    val bundles: List<HabitBundle> = emptyList(),
    val hasAny: Boolean = false,
    val categories: List<String> = emptyList(),
    val includeUncategorized: Boolean = false,
    val category: String? = null,
)

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetRefresher,
) : ViewModel() {
    private val categoryFilter = MutableStateFlow<String?>(null)

    val ui: StateFlow<HabitListUi> = combine(
        habits.observeBundles(includeArchived = true),
        categoryFilter,
    ) { bundles, selected ->
        val named = CategoryFilter.named(bundles.map { it.habit.category })
        val hasUncategorized = bundles.any { it.habit.category.isBlank() }
        val effective = CategoryFilter.effective(selected, named, hasUncategorized)
        HabitListUi(
            bundles = bundles.filter { CategoryFilter.matches(it.habit.category, effective) },
            hasAny = bundles.isNotEmpty(),
            categories = named,
            includeUncategorized = hasUncategorized,
            category = effective,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitListUi())

    fun setCategory(category: String?) {
        categoryFilter.value = category
    }

    fun deleteSelected(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                habits.deleteHabit(id)
                reminders.cancel(id)
            }
            widgets.refresh()
        }
    }

    fun updateCategory(ids: Set<Long>, category: String) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            habits.updateCategory(ids, category)
            widgets.refresh()
        }
    }
}
