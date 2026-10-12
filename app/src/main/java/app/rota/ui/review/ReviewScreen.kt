package app.rota.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.rota.domain.ReviewEvent
import app.rota.domain.ReviewStatusFilter
import app.rota.domain.model.Period
import app.rota.ui.components.ChipRow
import app.rota.ui.components.EmptyState
import app.rota.ui.components.QuietCard
import app.rota.ui.components.SectionLabel
import app.rota.ui.theme.RotaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onRange: (String) -> Unit,
    onStatus: (ReviewStatusFilter) -> Unit,
    onPeriod: (Period?) -> Unit,
    onHabit: (Long?) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
) {
    val summary = state.summary
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Review", fontWeight = FontWeight.Medium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                val ranges = listOf("week", "month", "quarter", "year", "custom")
                ChipRow(
                    options = ranges.map {
                        it.replaceFirstChar(Char::uppercase) to (state.filter.rangeKey == it)
                    },
                    onSelect = { onRange(ranges[it]) },
                )
                if (state.filter.rangeKey == "custom") {
                    CustomRangeRow(
                        start = state.filter.customStart,
                        end = state.filter.customEnd,
                        onChange = onCustomRange,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    options = listOf(
                        ReviewStatusFilter.ALL to "All",
                        ReviewStatusFilter.DONE to "Done",
                        ReviewStatusFilter.SKIPPED to "Skipped",
                        ReviewStatusFilter.MISSED to "Missed",
                        ReviewStatusFilter.SNOOZED to "Snoozed",
                    ).map { it.second to (state.filter.status == it.first) },
                    onSelect = { index ->
                        onStatus(
                            listOf(
                                ReviewStatusFilter.ALL,
                                ReviewStatusFilter.DONE,
                                ReviewStatusFilter.SKIPPED,
                                ReviewStatusFilter.MISSED,
                                ReviewStatusFilter.SNOOZED,
                            )[index],
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    options = (listOf<Period?>(null) + Period.entries).map { period ->
                        (period?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Any cadence") to
                            (state.filter.period == period)
                    },
                    onSelect = { index ->
                        onPeriod((listOf<Period?>(null) + Period.entries)[index])
                    },
                )
                if (state.habitNames.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        options = (listOf<Pair<Long?, String>>(null to "All habits") + state.habitNames.map { it.first to it.second })
                            .map { it.second to (state.filter.habitId == it.first) },
                        onSelect = { index ->
                            val ids = listOf<Long?>(null) + state.habitNames.map { it.first }
                            onHabit(ids[index])
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Rate", "${(summary.completionRate * 100).toInt()}%", Modifier.weight(1f))
                    StatCard("Done", "${summary.done}", Modifier.weight(1f))
                    StatCard("Missed", "${summary.missed}", Modifier.weight(1f))
                }
                if (summary.events.any { it.status == ReviewStatusFilter.SNOOZED }) {
                    SectionLabel("Snooze reasons")
                }
            }
            val snoozed = summary.events.filter { it.status == ReviewStatusFilter.SNOOZED }
            items(snoozed, key = { "s-${it.habit.id}-${it.date}" }) { event ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(event.habit.name, fontWeight = FontWeight.Medium)
                    Text(
                        "${event.date.format(formatter)} · ${event.snoozeReason}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionLabel("Activity") }
            if (summary.events.none { it.status != ReviewStatusFilter.SNOOZED }) {
                item {
                    EmptyState(
                        title = "No activity",
                        body = "Completions, skips, and missed periods in this range will land here.",
                    )
                }
            }
            items(
                summary.events.filter { it.status != ReviewStatusFilter.SNOOZED },
                key = { "${it.habit.id}-${it.date}-${it.status}" },
            ) { event ->
                EventRow(event, formatter)
            }
            item { Spacer(Modifier.height(32.dp)) }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picking = "start" }) { Text("From ${start.format(formatter)}") }
        TextButton(onClick = { picking = "end" }) { Text("to ${end.format(formatter)}") }
    }
    picking?.let { which ->
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

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    QuietCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EventRow(event: ReviewEvent, formatter: DateTimeFormatter) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(event.habit.name, style = MaterialTheme.typography.titleSmall)
        Text(
            buildString {
                append(event.date.format(formatter))
                append(" · ")
                append(event.status.name.lowercase())
                event.routineLabel?.let { append(" · "); append(it) }
                if (event.value > 0) {
                    append(" · ")
                    append(event.value)
                    if (event.target > 1) append("/${event.target}")
                }
                if (event.note.isNotBlank()) {
                    append(" · ")
                    append(event.note)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewPreview() {
    RotaTheme {
        ReviewScreen(
            state = ReviewUiState(),
            onRange = {},
            onStatus = {},
            onPeriod = {},
            onHabit = {},
        )
    }
}
