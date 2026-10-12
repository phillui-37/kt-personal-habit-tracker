package app.rota.domain

import app.rota.domain.model.Habit
import app.rota.domain.model.Period
import java.time.DayOfWeek
import java.time.LocalDate

data class DateRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(end)

    fun dates(): Sequence<LocalDate> = generateSequence(start) { current ->
        val next = current.plusDays(1)
        if (next.isAfter(end)) null else next
    }
}

object PeriodWindows {
    fun bounds(period: Period, date: LocalDate): DateRange = when (period) {
        Period.DAILY -> DateRange(date, date)
        Period.WEEKLY -> {
            val start = date.with(DayOfWeek.MONDAY)
            DateRange(start, start.plusDays(6))
        }
        Period.MONTHLY -> {
            val start = date.withDayOfMonth(1)
            DateRange(start, start.plusMonths(1).minusDays(1))
        }
        Period.QUARTERLY -> {
            val quarterIndex = (date.monthValue - 1) / 3
            val start = LocalDate.of(date.year, quarterIndex * 3 + 1, 1)
            DateRange(start, start.plusMonths(3).minusDays(1))
        }
        Period.YEARLY -> DateRange(
            LocalDate.of(date.year, 1, 1),
            LocalDate.of(date.year, 12, 31),
        )
        Period.CUSTOM -> DateRange(date, date)
    }

    fun namedRange(label: String, today: LocalDate): DateRange = when (label) {
        "week" -> bounds(Period.WEEKLY, today)
        "month" -> bounds(Period.MONTHLY, today)
        "quarter" -> bounds(Period.QUARTERLY, today)
        "year" -> bounds(Period.YEARLY, today)
        "custom" -> DateRange(today.minusDays(29), today)
        else -> bounds(Period.WEEKLY, today)
    }

    fun nextCustomDue(habit: Habit, lastConsumed: LocalDate?): LocalDate {
        val interval = habit.intervalDays.coerceAtLeast(1).toLong()
        val start = LocalDate.ofEpochDay(habit.createdAtEpochDay)
        return lastConsumed?.plusDays(interval) ?: start
    }
}
