package app.rota.domain

import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitBundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReviewChartsTest {
    @Test
    fun barChartSumsValuesByCategoryAndRanksThem() {
        val events = listOf(
            event(category = "Health", value = 2),
            event(category = "health", value = 3),
            event(category = "", value = 9),
            event(category = "Home", value = 1),
        )
        val points = ReviewCharts.present(
            events,
            ChartTextField.CATEGORY,
            ChartNumberField.VALUE,
            ChartStyle.BAR,
        )
        assertThat(points.map { it.label }).containsExactly("Uncategorized", "Health", "Home").inOrder()
        assertThat(points.map { it.value }).containsExactly(9.0, 5.0, 1.0).inOrder()
    }

    @Test
    fun lineChartKeepsDatesInOrder() {
        val events = listOf(
            event(date = LocalDate.of(2026, 1, 10), value = 1),
            event(date = LocalDate.of(2026, 1, 2), value = 4),
            event(date = LocalDate.of(2026, 1, 2), value = 6),
        )
        val points = ReviewCharts.present(
            events,
            ChartTextField.DATE,
            ChartNumberField.VALUE,
            ChartStyle.LINE,
            DateTimeFormatter.ISO_LOCAL_DATE,
        )
        assertThat(points.map { it.label }).containsExactly("2026-01-02", "2026-01-10").inOrder()
        assertThat(points.map { it.value }).containsExactly(10.0, 1.0).inOrder()
    }

    @Test
    fun countAndRoutineUseTextLabels() {
        val events = listOf(
            event(routine = null),
            event(routine = "  "),
            event(routine = "Legs"),
            event(status = ReviewStatusFilter.SKIPPED, routine = "Legs"),
        )
        val byRoutine = ReviewCharts.present(events, ChartTextField.ROUTINE, ChartNumberField.COUNT, ChartStyle.BAR)
        assertThat(byRoutine.map { it.label }).containsExactly("Legs", "None").inOrder()
        assertThat(byRoutine.map { it.value }).containsExactly(2.0, 2.0).inOrder()

        val byStatus = ReviewCharts.present(events, ChartTextField.STATUS, ChartNumberField.TARGET, ChartStyle.LINE)
        assertThat(byStatus.map { it.label }).containsExactly("Done", "Skipped").inOrder()
    }

    @Test
    fun emptySelectionProducesNoPoints() {
        assertThat(
            ReviewCharts.present(emptyList(), ChartTextField.HABIT, ChartNumberField.COUNT, ChartStyle.BAR),
        ).isEmpty()
    }

    @Test
    fun groupsActivityByCategoryWithBlanksLast() {
        val groups = ReviewStats.groupedByCategory(
            listOf(
                event(name = "Taxes", category = "Home"),
                event(name = "Run", category = "Health"),
                event(name = "Water", category = ""),
                event(name = "Stretch", category = "health"),
            ),
        )
        assertThat(groups.map { it.category }).containsExactly("Health", "Home", "Uncategorized").inOrder()
        assertThat(groups.first().events.map { it.habit.name }).containsExactly("Run", "Stretch").inOrder()
    }

    @Test
    fun categoryFilterMatchesCaseAndBlanks() {
        val today = LocalDate.now()
        val health = bundle("Run", "Health", today)
        val alsoHealth = bundle("Yoga", "HEALTH", today)
        val home = bundle("Dishes", "home", today)
        val plain = bundle("Water", "", today)
        val range = DateRange(today, today)
        val healthOnly = ReviewStats.summarize(
            bundles = listOf(health, alsoHealth, home, plain),
            range = range,
            statusFilter = ReviewStatusFilter.ALL,
            periodFilter = null,
            habitId = null,
            categoryFilter = "health",
        )
        assertThat(healthOnly.events.map { it.habit.name }).containsExactly("Run", "Yoga").inOrder()
        val uncategorized = ReviewStats.summarize(
            bundles = listOf(health, alsoHealth, home, plain),
            range = range,
            statusFilter = ReviewStatusFilter.ALL,
            periodFilter = null,
            habitId = null,
            categoryFilter = "",
        )
        assertThat(uncategorized.events.map { it.habit.name }).containsExactly("Water")
    }

    private fun bundle(name: String, category: String, day: LocalDate) = HabitBundle(
        habit = Habit(id = name.hashCode().toLong(), name = name, category = category),
        routine = emptyList(),
        checkIns = listOf(
            CheckIn(
                habitId = name.hashCode().toLong(),
                epochDay = day.toEpochDay(),
                status = CheckInStatus.DONE,
                value = 1,
            ),
        ),
    )

    private fun event(
        name: String = "Habit",
        category: String = "",
        date: LocalDate = LocalDate.of(2026, 1, 1),
        status: ReviewStatusFilter = ReviewStatusFilter.DONE,
        value: Int = 1,
        target: Int = 1,
        routine: String? = null,
    ) = ReviewEvent(
        date = date,
        habit = Habit(name = name, category = category),
        routineLabel = routine,
        status = status,
        value = value,
        target = target,
        note = "",
    )
}
