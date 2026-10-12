package app.rota.domain

import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.Period
import java.time.LocalDate

object StreakCalculator {
    fun currentStreak(
        habit: Habit,
        checkIns: List<CheckIn>,
        today: LocalDate,
    ): Int {
        return when (habit.period) {
            Period.DAILY -> dayStreak(habit, checkIns, today)
            Period.CUSTOM -> customStreak(habit, checkIns, today)
            else -> periodStreak(habit, checkIns, today)
        }
    }

    private fun dayStreak(habit: Habit, checkIns: List<CheckIn>, today: LocalDate): Int {
        val byDay = checkIns.associateBy { it.epochDay }
        var cursor = today
        if (byDay[today.toEpochDay()]?.status != CheckInStatus.DONE) {
            cursor = today.minusDays(1)
        }
        var streak = 0
        val created = LocalDate.ofEpochDay(habit.createdAtEpochDay)
        while (!cursor.isBefore(created)) {
            val log = byDay[cursor.toEpochDay()]
            when {
                log?.status == CheckInStatus.DONE -> streak += 1
                log?.status == CheckInStatus.SKIPPED -> return streak
                habit.allowsWeekday(cursor) -> return streak
            }
            cursor = cursor.minusDays(1)
            if (streak > 4000) break
        }
        return streak
    }

    private fun customStreak(habit: Habit, checkIns: List<CheckIn>, today: LocalDate): Int {
        val consumed = checkIns
            .filter { it.status == CheckInStatus.DONE || it.status == CheckInStatus.SKIPPED }
            .sortedByDescending { it.epochDay }
        if (consumed.isEmpty()) return 0
        val interval = habit.intervalDays.coerceAtLeast(1).toLong()
        var expected = PeriodWindows.nextCustomDue(
            habit,
            consumed.first().date.takeIf { it == today || consumed.first().status != CheckInStatus.DONE },
        )
        if (consumed.first().status == CheckInStatus.DONE && !consumed.first().date.isBefore(today.minusDays(interval))) {
            expected = consumed.first().date
        }
        var streak = 0
        var index = 0
        while (index < consumed.size) {
            val log = consumed[index]
            val due = expected
            if (log.date.isAfter(due.plusDays(interval - 1)) && log.status == CheckInStatus.DONE) {
                // completed later than the window; still counts as catching up
            }
            if (log.status != CheckInStatus.DONE) return streak
            streak += 1
            expected = log.date.minusDays(interval)
            index += 1
        }
        return streak
    }

    private fun periodStreak(habit: Habit, checkIns: List<CheckIn>, today: LocalDate): Int {
        var cursor = today
        val currentBounds = PeriodWindows.bounds(habit.period, today)
        val currentDone = checkIns.count {
            it.date in currentBounds && it.status == CheckInStatus.DONE
        }
        val target = habit.timesPerPeriod.coerceAtLeast(1)
        if (currentDone < target) {
            cursor = currentBounds.start.minusDays(1)
        }
        var streak = 0
        val created = LocalDate.ofEpochDay(habit.createdAtEpochDay)
        while (!cursor.isBefore(created)) {
            val bounds = PeriodWindows.bounds(habit.period, cursor)
            val done = checkIns.count { it.date in bounds && it.status == CheckInStatus.DONE }
            if (done >= target) {
                streak += 1
                cursor = bounds.start.minusDays(1)
            } else {
                return streak
            }
            if (streak > 400) break
        }
        return streak
    }
}
