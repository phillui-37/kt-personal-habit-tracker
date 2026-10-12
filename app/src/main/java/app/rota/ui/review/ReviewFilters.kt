package app.rota.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rota.domain.ReviewStatusFilter
import app.rota.domain.model.Period
import app.rota.ui.components.ChipRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReviewFilterPanel(
    filter: ReviewFilter,
    habitNames: List<Pair<Long, String>>,
    categories: List<String>,
    onRange: (String) -> Unit,
    onStatus: (ReviewStatusFilter) -> Unit,
    onPeriod: (Period?) -> Unit,
    onHabit: (Long?) -> Unit,
    onCategory: (String?) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val summary = filterSummary(filter, habitNames)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = !open }
                .semantics { role = Role.Button }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Filters",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (open) "Collapse filters" else "Expand filters",
            )
        }
        if (!open) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            FilterGroup("Range", rangeValue(filter)) {
                val ranges = listOf("week", "month", "quarter", "year", "custom")
                ChipRow(
                    options = ranges.map { rangeLabel(it) to (filter.rangeKey == it) },
                    onSelect = { onRange(ranges[it]) },
                )
                if (filter.rangeKey == "custom") {
                    CustomRangeRow(
                        start = filter.customStart,
                        end = filter.customEnd,
                        onChange = onCustomRange,
                    )
                }
            }
            FilterGroup("Status", statusLabel(filter.status)) {
                val statuses = listOf(
                    ReviewStatusFilter.ALL,
                    ReviewStatusFilter.DONE,
                    ReviewStatusFilter.SKIPPED,
                    ReviewStatusFilter.MISSED,
                    ReviewStatusFilter.SNOOZED,
                )
                ChipRow(
                    options = statuses.map { statusLabel(it) to (filter.status == it) },
                    onSelect = { onStatus(statuses[it]) },
                )
            }
            FilterGroup("Cadence", cadenceLabel(filter.period)) {
                val periods = listOf<Period?>(null) + Period.entries
                ChipRow(
                    options = periods.map { cadenceLabel(it) to (filter.period == it) },
                    onSelect = { onPeriod(periods[it]) },
                )
            }
            if (habitNames.isNotEmpty()) {
                FilterGroup("Habit", habitLabel(filter.habitId, habitNames)) {
                    val ids = listOf<Long?>(null) + habitNames.map { it.first }
                    ChipRow(
                        options = (listOf<Pair<Long?, String>>(null to "All habits") + habitNames)
                            .map { it.second to (filter.habitId == it.first) },
                        onSelect = { onHabit(ids[it]) },
                    )
                }
            }
            FilterGroup("Category", categoryValue(filter.category)) {
                val options = listOf<String?>(null) + categories + listOf("")
                ChipRow(
                    options = options.map { categoryValue(it) to categorySelected(filter.category, it) },
                    onSelect = { onCategory(options[it]) },
                )
            }
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    value: String,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = !open }
                .semantics { role = Role.Button }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (open) "Collapse $title" else "Expand $title",
            )
        }
        if (open) {
            Box(Modifier.padding(bottom = 8.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeRow(
    start: LocalDate,
    end: LocalDate,
    onChange: (LocalDate, LocalDate) -> Unit,
) {
    var picking by remember { mutableStateOf<String?>(null) }
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    Row {
        TextButton(onClick = { picking = "start" }) { Text("From ${start.format(formatter)}") }
        TextButton(onClick = { picking = "end" }) { Text("to ${end.format(formatter)}") }
    }
    picking?.let { which ->
        key(which) {
            val initial = if (which == "start") start else end
            val picker = rememberDatePickerState(
                initialSelectedDateMillis = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        val millis = picker.selectedDateMillis ?: return@TextButton
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (which == "start") onChange(date, end) else onChange(start, date)
                        picking = null
                    }) { Text("Use") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            ) {
                DatePicker(state = picker)
            }
        }
    }
}

private fun filterSummary(filter: ReviewFilter, habitNames: List<Pair<Long, String>>): String =
    listOf(
        rangeValue(filter),
        statusLabel(filter.status),
        cadenceLabel(filter.period),
        habitLabel(filter.habitId, habitNames),
        categoryValue(filter.category),
    ).joinToString(" · ")

private fun rangeValue(filter: ReviewFilter): String {
    if (filter.rangeKey != "custom") return rangeLabel(filter.rangeKey)
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    val start = minOf(filter.customStart, filter.customEnd)
    val end = maxOf(filter.customStart, filter.customEnd)
    return "${start.format(formatter)} – ${end.format(formatter)}"
}

private fun rangeLabel(key: String): String = when (key) {
    "week" -> "Week"
    "month" -> "Month"
    "quarter" -> "Quarter"
    "year" -> "Year"
    "custom" -> "Custom"
    else -> key.replaceFirstChar(Char::uppercase)
}

private fun statusLabel(status: ReviewStatusFilter): String = when (status) {
    ReviewStatusFilter.ALL -> "All"
    ReviewStatusFilter.DONE -> "Done"
    ReviewStatusFilter.SKIPPED -> "Skipped"
    ReviewStatusFilter.MISSED -> "Missed"
    ReviewStatusFilter.SNOOZED -> "Snoozed"
    ReviewStatusFilter.LOGGED -> "Logged"
}

private fun cadenceLabel(period: Period?): String =
    period?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Any cadence"

private fun habitLabel(habitId: Long?, habitNames: List<Pair<Long, String>>): String =
    if (habitId == null) "All habits" else habitNames.firstOrNull { it.first == habitId }?.second ?: "Habit"

private fun categoryValue(category: String?): String = when (category) {
    null -> "All categories"
    "" -> "Uncategorized"
    else -> category
}

private fun categorySelected(current: String?, option: String?): Boolean = when {
    current == null && option == null -> true
    current == null || option == null -> false
    else -> current.equals(option, ignoreCase = true)
}
