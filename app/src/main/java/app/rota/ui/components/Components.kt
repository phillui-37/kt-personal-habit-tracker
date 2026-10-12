package app.rota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rota.domain.CategoryFilter
import app.rota.domain.model.Habit
import app.rota.domain.model.Period
import app.rota.ui.theme.HabitPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, (label, selected) ->
            FilterChip(
                selected = selected,
                onClick = { onSelect(index) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun WeekdayPicker(
    mask: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        WeekdayPresetChip(
            label = "Weekday",
            selected = mask == Habit.WEEKDAYS,
            onClick = { onChange(if (mask == Habit.WEEKDAYS) 0 else Habit.WEEKDAYS) },
        )
        WeekdayPresetChip(
            label = "Weekend",
            selected = mask == Habit.WEEKEND,
            onClick = { onChange(if (mask == Habit.WEEKEND) 0 else Habit.WEEKEND) },
        )
    }
    val days = DayOfWeek.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            val bit = 1 shl (day.value - 1)
            val actuallySelected = mask != 0 && mask and bit != 0
            val label = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (actuallySelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable {
                        val next = if (actuallySelected) mask and bit.inv() else mask or bit
                        onChange(next)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (actuallySelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
    Text(
        text = if (mask == 0) "Any day" else "Only selected days",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun WeekdayPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun CategoryFilterBar(
    categories: List<String>,
    includeUncategorized: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!CategoryFilter.visible(categories, includeUncategorized)) return
    val options = buildList {
        add(null to "All")
        categories.forEach { add(it to it) }
        if (includeUncategorized) add("" to "Uncategorized")
    }
    ChipRow(
        options = options.map { (value, label) -> label to categoryChipSelected(selected, value) },
        onSelect = { onSelect(options[it].first) },
        modifier = modifier,
    )
}

private fun categoryChipSelected(current: String?, option: String?): Boolean = when {
    current == null && option == null -> true
    current == null || option == null -> false
    else -> current.equals(option, ignoreCase = true)
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HabitPalette.forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (index == selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) },
            )
        }
    }
}

@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..999,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }) {
            Icon(Icons.Outlined.Remove, contentDescription = "Decrease")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(36.dp),
        )
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }) {
            Icon(Icons.Outlined.Add, contentDescription = "Increase")
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun Heatmap(
    days: Map<LocalDate, Float>,
    color: Color,
    weeks: Int = 16,
) {
    val today = LocalDate.now()
    val start = today.minusWeeks(weeks.toLong() - 1).with(DayOfWeek.MONDAY)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        (0 until 7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until weeks).forEach { col ->
                    val date = start.plusWeeks(col.toLong()).plusDays(row.toLong())
                    val intensity = days[date] ?: 0f
                    val alpha = if (date.isAfter(today)) 0.06f else (0.12f + intensity.coerceIn(0f, 1f) * 0.88f)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (date.isAfter(today)) MaterialTheme.colorScheme.surfaceVariant else color.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}

fun periodLabel(period: Period): String = when (period) {
    Period.DAILY -> "Day"
    Period.WEEKLY -> "Week"
    Period.MONTHLY -> "Month"
    Period.QUARTERLY -> "Quarter"
    Period.YEARLY -> "Year"
    Period.CUSTOM -> "Custom"
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QuietCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        content = content,
    )
}
