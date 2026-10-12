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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.rota.domain.ReviewEvent
import app.rota.domain.ReviewStats
import app.rota.domain.ReviewStatusFilter
import app.rota.domain.model.Period
import app.rota.ui.components.EmptyState
import app.rota.ui.components.QuietCard
import app.rota.ui.components.SectionLabel
import app.rota.ui.theme.RotaTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onRange: (String) -> Unit,
    onStatus: (ReviewStatusFilter) -> Unit,
    onPeriod: (Period?) -> Unit,
    onHabit: (Long?) -> Unit,
    onCategory: (String?) -> Unit = {},
    onCustomRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
) {
    val summary = state.summary
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    val snoozed = summary.events.filter { it.status == ReviewStatusFilter.SNOOZED }
    val activity = summary.events.filter { it.status != ReviewStatusFilter.SNOOZED }
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
                ReviewFilterPanel(
                    filter = state.filter,
                    habitNames = state.habitNames,
                    categories = state.categories,
                    onRange = onRange,
                    onStatus = onStatus,
                    onPeriod = onPeriod,
                    onHabit = onHabit,
                    onCategory = onCategory,
                    onCustomRange = onCustomRange,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Rate", "${(summary.completionRate * 100).toInt()}%", Modifier.weight(1f))
                    StatCard("Done", "${summary.done}", Modifier.weight(1f))
                    StatCard("Missed", "${summary.missed}", Modifier.weight(1f))
                }
            }
            item { ReviewChartSection(summary.events) }
            if (snoozed.isNotEmpty()) {
                item { SectionLabel("Snooze reasons") }
                categoryGroups(snoozed, "snooze") { event ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(event.habit.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${event.date.format(formatter)} · ${event.snoozeReason}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionLabel("Activity") }
            if (activity.isEmpty()) {
                item {
                    EmptyState(
                        title = "No activity",
                        body = "Completions, skips, and missed periods in this range will land here.",
                    )
                }
            } else {
                categoryGroups(activity, "activity") { event ->
                    EventRow(event, formatter)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

private fun LazyListScope.categoryGroups(
    events: List<ReviewEvent>,
    keyPrefix: String,
    itemContent: @Composable (ReviewEvent) -> Unit,
) {
    ReviewStats.groupedByCategory(events).forEachIndexed { groupIndex, group ->
        item(key = "$keyPrefix-h-$groupIndex") {
            Text(
                "${group.category} · ${group.events.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
        }
        itemsIndexed(
            group.events,
            key = { index, event ->
                "$keyPrefix-$groupIndex-$index-${event.habit.id}-${event.date.toEpochDay()}-${event.status}"
            },
        ) { _, event ->
            itemContent(event)
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
