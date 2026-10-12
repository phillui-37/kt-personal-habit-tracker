package app.rota.domain

import java.time.format.DateTimeFormatter

enum class ChartTextField(val label: String) {
    HABIT("Habit"),
    CATEGORY("Category"),
    STATUS("Status"),
    ROUTINE("Routine"),
    DATE("Date"),
}

enum class ChartNumberField(val label: String) {
    COUNT("Count"),
    VALUE("Value"),
    TARGET("Target"),
}

enum class ChartStyle(val label: String) {
    BAR("Bar"),
    LINE("Line"),
}

data class ChartPoint(
    val label: String,
    val value: Double,
    val sortKey: Long = 0,
)

object ReviewCharts {
    fun present(
        events: List<ReviewEvent>,
        text: ChartTextField,
        number: ChartNumberField,
        style: ChartStyle,
        dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM"),
    ): List<ChartPoint> {
        if (events.isEmpty()) return emptyList()
        val buckets = linkedMapOf<String, MutableList<ReviewEvent>>()
        val labels = linkedMapOf<String, String>()
        val sortKeys = linkedMapOf<String, Long>()
        events.forEach { event ->
            val identity = identity(event, text, dateFormatter)
            labels.putIfAbsent(identity.key, identity.label)
            sortKeys.putIfAbsent(identity.key, identity.sortKey)
            buckets.getOrPut(identity.key) { mutableListOf() }.add(event)
        }
        val points = buckets.map { (key, rows) ->
            ChartPoint(
                label = labels.getValue(key),
                value = aggregate(rows, number),
                sortKey = sortKeys.getValue(key),
            )
        }
        return when {
            text == ChartTextField.DATE -> points.sortedBy { it.sortKey }
            style == ChartStyle.LINE -> points.sortedBy { it.label.lowercase() }
            else -> points.sortedWith(compareByDescending<ChartPoint> { it.value }.thenBy { it.label.lowercase() })
        }
    }

    fun formatNumber(value: Double): String {
        val whole = value.toLong()
        return if (kotlin.math.abs(value - whole) < 0.001) whole.toString() else "%.1f".format(value)
    }

    private fun aggregate(rows: List<ReviewEvent>, number: ChartNumberField): Double = when (number) {
        ChartNumberField.COUNT -> rows.size.toDouble()
        ChartNumberField.VALUE -> rows.sumOf { it.value.toDouble() }
        ChartNumberField.TARGET -> rows.sumOf { it.target.toDouble() }
    }

    private fun identity(
        event: ReviewEvent,
        text: ChartTextField,
        dateFormatter: DateTimeFormatter,
    ): ChartIdentity = when (text) {
        ChartTextField.HABIT -> {
            val name = event.habit.name.trim().ifBlank { "Untitled" }
            ChartIdentity(name.lowercase(), name, 0)
        }
        ChartTextField.CATEGORY -> {
            val raw = event.habit.category.trim()
            ChartIdentity(raw.lowercase(), ReviewStats.categoryLabel(raw), 0)
        }
        ChartTextField.STATUS -> {
            val label = event.status.name.lowercase().replaceFirstChar(Char::uppercase)
            ChartIdentity(label.lowercase(), label, 0)
        }
        ChartTextField.ROUTINE -> {
            val label = event.routineLabel?.trim().orEmpty().ifBlank { "None" }
            ChartIdentity(label.lowercase(), label, 0)
        }
        ChartTextField.DATE -> {
            val day = event.date.toEpochDay()
            ChartIdentity(day.toString(), event.date.format(dateFormatter), day)
        }
    }

    private data class ChartIdentity(val key: String, val label: String, val sortKey: Long)
}
