package app.rota.domain

import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitBundle
import app.rota.domain.model.Period
import java.time.LocalDate

enum class ReviewStatusFilter {
    ALL,
    DONE,
    SKIPPED,
    SNOOZED,
    MISSED,
    LOGGED,
}

data class ReviewEvent(
    val date: LocalDate,
    val habit: Habit,
    val routineLabel: String?,
    val status: ReviewStatusFilter,
    val value: Int,
    val target: Int,
    val note: String,
    val snoozeReason: String = "",
)

data class ReviewSummary(
    val range: DateRange,
    val completionRate: Float,
    val done: Int,
    val skipped: Int,
    val missed: Int,
    val snoozed: Int,
    val events: List<ReviewEvent>,
)

object ReviewStats {
    fun summarize(
        bundles: List<HabitBundle>,
        range: DateRange,
        statusFilter: ReviewStatusFilter,
        periodFilter: Period?,
        habitId: Long?,
    ): ReviewSummary {
        val events = bundles
            .filter { habitId == null || it.habit.id == habitId }
            .filter { periodFilter == null || it.habit.period == periodFilter }
            .flatMap { bundle -> eventsFor(bundle, range) }
            .sortedWith(compareByDescending<ReviewEvent> { it.date }.thenBy { it.habit.name })

        val visible = events.filter { statusFilter == ReviewStatusFilter.ALL || it.status == statusFilter }
        val done = events.count { it.status == ReviewStatusFilter.DONE }
        val skipped = events.count { it.status == ReviewStatusFilter.SKIPPED }
        val missed = events.count { it.status == ReviewStatusFilter.MISSED }
        val snoozed = events.count { it.status == ReviewStatusFilter.SNOOZED }
        val tracked = done + skipped + missed
        val rate = if (tracked == 0) 0f else done.toFloat() / tracked.toFloat()

        return ReviewSummary(
            range = range,
            completionRate = rate,
            done = done,
            skipped = skipped,
            missed = missed,
            snoozed = snoozed,
            events = visible,
        )
    }

    private fun eventsFor(bundle: HabitBundle, range: DateRange): List<ReviewEvent> {
        val habit = bundle.habit
        val events = mutableListOf<ReviewEvent>()

        bundle.checkIns.filter { it.date in range }.forEach { log ->
            events += ReviewEvent(
                date = log.date,
                habit = habit,
                routineLabel = log.routineLabel,
                status = when (log.status) {
                    CheckInStatus.DONE -> ReviewStatusFilter.DONE
                    CheckInStatus.SKIPPED -> ReviewStatusFilter.SKIPPED
                    CheckInStatus.LOG -> ReviewStatusFilter.LOGGED
                },
                value = log.value,
                target = habit.target,
                note = log.note,
            )
        }

        bundle.snooze?.let { snooze ->
            val created = java.time.Instant.ofEpochMilli(snooze.createdAtEpochMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            if (created in range) {
                events += ReviewEvent(
                    date = created,
                    habit = habit,
                    routineLabel = bundle.currentRoutine?.title,
                    status = ReviewStatusFilter.SNOOZED,
                    value = 0,
                    target = habit.target,
                    note = "",
                    snoozeReason = snooze.reason.ifBlank { "No reason given" },
                )
            }
        }

        periodWindows(habit, range).forEach { window ->
            if (!window.end.isBefore(LocalDate.now())) return@forEach
            val logs = bundle.checkIns.filter { it.date in window }
            val consumed = logs.count {
                it.status == CheckInStatus.DONE || it.status == CheckInStatus.SKIPPED
            }
            val missing = (slotTarget(habit) - consumed).coerceAtLeast(0)
            repeat(missing) {
                events += ReviewEvent(
                    date = window.end,
                    habit = habit,
                    routineLabel = bundle.currentRoutine?.title,
                    status = ReviewStatusFilter.MISSED,
                    value = 0,
                    target = habit.target,
                    note = "",
                )
            }
        }

        return events
    }

    private fun slotTarget(habit: Habit): Int = when (habit.period) {
        Period.DAILY, Period.CUSTOM -> 1
        else -> habit.timesPerPeriod.coerceAtLeast(1)
    }

    private fun periodWindows(habit: Habit, range: DateRange): List<DateRange> {
        if (habit.period == Period.CUSTOM) return emptyList()
        val windows = mutableListOf<DateRange>()
        var cursor = range.start
        while (!cursor.isAfter(range.end)) {
            val bounds = PeriodWindows.bounds(habit.period, cursor)
            val clipped = DateRange(
                maxOf(bounds.start, range.start),
                minOf(bounds.end, range.end),
            )
            windows += bounds
            cursor = bounds.end.plusDays(1)
            if (clipped.start == clipped.end && habit.period == Period.DAILY) {
                // continue
            }
        }
        return windows.distinct()
    }
}
