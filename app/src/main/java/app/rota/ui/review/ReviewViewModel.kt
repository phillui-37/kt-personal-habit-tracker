package app.rota.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rota.data.repo.HabitRepository
import app.rota.domain.DateRange
import app.rota.domain.PeriodWindows
import app.rota.domain.ReviewStats
import app.rota.domain.ReviewStatusFilter
import app.rota.domain.ReviewSummary
import app.rota.domain.model.Period
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

data class ReviewFilter(
    val rangeKey: String = "week",
    val status: ReviewStatusFilter = ReviewStatusFilter.ALL,
    val period: Period? = null,
    val habitId: Long? = null,
    val customStart: LocalDate = LocalDate.now().minusDays(29),
    val customEnd: LocalDate = LocalDate.now(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    habits: HabitRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(ReviewFilter())

    val state = combine(habits.observeBundles(includeArchived = true), filter) { bundles, current ->
        val today = LocalDate.now()
        val range = if (current.rangeKey == "custom") {
            DateRange(
                minOf(current.customStart, current.customEnd),
                maxOf(current.customStart, current.customEnd),
            )
        } else {
            PeriodWindows.namedRange(current.rangeKey, today)
        }
        val summary = ReviewStats.summarize(
            bundles = bundles,
            range = range,
            statusFilter = current.status,
            periodFilter = current.period,
            habitId = current.habitId,
        )
        ReviewUiState(
            filter = current,
            summary = summary,
            habitNames = bundles.map { it.habit.id to it.habit.name },
            range = range,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReviewUiState(),
    )

    fun setRange(key: String) = filter.update { it.copy(rangeKey = key) }
    fun setCustomRange(start: LocalDate, end: LocalDate) = filter.update {
        it.copy(rangeKey = "custom", customStart = start, customEnd = end)
    }
    fun setStatus(status: ReviewStatusFilter) = filter.update { it.copy(status = status) }
    fun setPeriod(period: Period?) = filter.update { it.copy(period = period) }
    fun setHabit(id: Long?) = filter.update { it.copy(habitId = id) }
}

data class ReviewUiState(
    val filter: ReviewFilter = ReviewFilter(),
    val summary: ReviewSummary = ReviewSummary(
        range = DateRange(LocalDate.now(), LocalDate.now()),
        completionRate = 0f,
        done = 0,
        skipped = 0,
        missed = 0,
        snoozed = 0,
        events = emptyList(),
    ),
    val habitNames: List<Pair<Long, String>> = emptyList(),
    val range: DateRange = DateRange(LocalDate.now(), LocalDate.now()),
)
