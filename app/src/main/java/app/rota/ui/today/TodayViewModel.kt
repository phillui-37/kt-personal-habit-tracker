package app.rota.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rota.data.repo.HabitRepository
import app.rota.domain.CategoryFilter
import app.rota.domain.model.TodayItem
import app.rota.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val widgets: WidgetRefresher,
) : ViewModel() {
    private val categoryFilter = MutableStateFlow<String?>(null)

    val ui: StateFlow<TodayUi> = combine(habits.observeToday(), categoryFilter) { items, selected ->
        val named = CategoryFilter.named(items.map { it.habit.category })
        val hasUncategorized = items.any { it.habit.category.isBlank() }
        val effective = CategoryFilter.effective(selected, named, hasUncategorized)
        TodayUi(
            items = items.filter { CategoryFilter.matches(it.habit.category, effective) },
            hasAny = items.isNotEmpty(),
            categories = named,
            includeUncategorized = hasUncategorized,
            category = effective,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    fun setCategory(category: String?) {
        categoryFilter.value = category
    }

    fun toggle(id: Long) = write { habits.toggleCheck(id) }
    fun add(id: Long, delta: Int) = write { habits.addValue(id, delta) }
    fun log(id: Long, value: Int) = write { habits.logValue(id, value) }
    fun skip(id: Long) = write { habits.skip(id) }
    fun note(id: Long, text: String) = write { habits.setNote(id, text) }
    fun wake(id: Long) = write { habits.clearSnooze(id) }

    fun snooze(id: Long, choice: SnoozeChoice, reason: String) = write {
        habits.snooze(id, choice.untilMillis(), reason)
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            widgets.refresh()
        }
    }
}

data class TodayUi(
    val items: List<TodayItem> = emptyList(),
    val hasAny: Boolean = false,
    val categories: List<String> = emptyList(),
    val includeUncategorized: Boolean = false,
    val category: String? = null,
)

enum class SnoozeChoice(val label: String) {
    HOUR("1 hour"),
    TONIGHT("Tonight"),
    TOMORROW("Tomorrow"),
    ;

    fun untilMillis(now: LocalDateTime = LocalDateTime.now()): Long {
        val at = when (this) {
            HOUR -> now.plusHours(1)
            TONIGHT -> {
                val tonight = LocalDateTime.of(now.toLocalDate(), LocalTime.of(21, 0))
                if (tonight.isAfter(now)) tonight else tonight.plusDays(1)
            }
            TOMORROW -> LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 0))
        }
        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
