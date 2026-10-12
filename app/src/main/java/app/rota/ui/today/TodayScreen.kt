package app.rota.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitBundle
import app.rota.domain.model.HabitType
import app.rota.domain.model.Period
import app.rota.domain.model.RoutineItem
import app.rota.domain.model.TodayItem
import app.rota.domain.model.TodayStatus
import app.rota.ui.components.CategoryFilterBar
import app.rota.ui.components.EmptyState
import app.rota.ui.components.periodLabel
import app.rota.ui.theme.RotaTheme
import app.rota.ui.theme.habitColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    items: List<TodayItem>,
    onOpen: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onAdd: (Long, Int) -> Unit,
    onLog: (Long, Int) -> Unit,
    onSkip: (Long) -> Unit,
    onNote: (Long, String) -> Unit,
    onSnooze: (Long, SnoozeChoice, String) -> Unit,
    onWake: (Long) -> Unit,
    onAddHabit: () -> Unit,
    hasAny: Boolean = items.isNotEmpty(),
    categories: List<String> = emptyList(),
    includeUncategorized: Boolean = false,
    category: String? = null,
    onCategory: (String?) -> Unit = {},
) {
    var snoozeFor by remember { mutableStateOf<TodayItem?>(null) }
    var noteFor by remember { mutableStateOf<TodayItem?>(null) }
    var timerFor by remember { mutableStateOf<TodayItem?>(null) }
    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today", fontWeight = FontWeight.Medium)
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onAddHabit) { Text("Add") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CategoryFilterBar(
                categories = categories,
                includeUncategorized = includeUncategorized,
                selected = category,
                onSelect = onCategory,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            when {
                !hasAny -> EmptyState(
                    title = "Nothing due",
                    body = "Add a habit, or a rotation you can step through.",
                    modifier = Modifier.padding(24.dp),
                )
                items.isEmpty() -> EmptyState(
                    title = "Nothing in this category",
                    body = "Choose another category, or All.",
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.habit.id }) { item ->
                        TodayRow(
                            item = item,
                            onOpen = { onOpen(item.habit.id) },
                            onToggle = { onToggle(item.habit.id) },
                            onAdd = { onAdd(item.habit.id, it) },
                            onSkip = { onSkip(item.habit.id) },
                            onNote = { noteFor = item },
                            onSnooze = { snoozeFor = item },
                            onTimer = { timerFor = item },
                            onWake = { onWake(item.habit.id) },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    snoozeFor?.let { item ->
        SnoozeSheet(
            name = item.habit.name,
            onDismiss = { snoozeFor = null },
            onConfirm = { choice, reason ->
                onSnooze(item.habit.id, choice, reason)
                snoozeFor = null
            },
        )
    }
    noteFor?.let { item ->
        NoteSheet(
            name = item.habit.name,
            initial = item.todayNote,
            onDismiss = { noteFor = null },
            onSave = { text ->
                onNote(item.habit.id, text)
                noteFor = null
            },
        )
    }
    timerFor?.let { item ->
        DurationSheet(
            name = item.habit.name,
            already = item.todayValue,
            target = item.habit.target,
            onDismiss = { timerFor = null },
            onAddMinutes = {
                onLog(item.habit.id, item.todayValue + it)
                timerFor = null
            },
        )
    }
}

@Composable
private fun TodayRow(
    item: TodayItem,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onAdd: (Int) -> Unit,
    onSkip: () -> Unit,
    onNote: () -> Unit,
    onSnooze: () -> Unit,
    onTimer: () -> Unit,
    onWake: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val muted = item.status == TodayStatus.DONE || item.status == TodayStatus.SKIPPED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(habitColor(item.habit.colorIndex).copy(alpha = if (muted) 0.35f else 1f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.habit.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (muted) 0.45f else 1f),
            )
            val subtitle = buildList {
                item.habit.category.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                item.routineCaption?.let { add(it) }
                add(periodLabel(item.habit.period))
                if (item.periodTarget > 1) add("${item.periodDone}/${item.periodTarget}")
                if (item.streak > 0) add("${item.streak} streak")
                when (item.status) {
                    TodayStatus.SNOOZED -> add("Snoozed")
                    TodayStatus.SKIPPED -> add("Skipped")
                    TodayStatus.DONE -> add("Done")
                    else -> Unit
                }
            }.joinToString(" · ")
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.habit.type != HabitType.CHECK) {
                Text(
                    "${item.todayValue}/${item.habit.target} ${item.habit.unitLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.todayNote.isNotBlank()) {
                Text(
                    item.todayNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        when {
            item.status == TodayStatus.SNOOZED -> TextButton(onClick = onWake) { Text("Wake") }
            item.habit.type == HabitType.CHECK -> {
                FilledIconButton(
                    onClick = onToggle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (item.status == TodayStatus.DONE) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                        contentColor = if (item.status == TodayStatus.DONE) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "Done")
                }
            }
            item.habit.type == HabitType.COUNT -> {
                IconButton(onClick = { onAdd(-1) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = { onAdd(1) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add")
                }
            }
            item.habit.type == HabitType.DURATION -> {
                AssistChip(onClick = { onAdd(5) }, label = { Text("+5") })
                IconButton(onClick = onTimer) {
                    Icon(Icons.Outlined.Timer, contentDescription = "Timer")
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Note") }, onClick = { menu = false; onNote() })
                DropdownMenuItem(text = { Text("Skip today") }, onClick = { menu = false; onSkip() })
                DropdownMenuItem(text = { Text("Snooze") }, onClick = { menu = false; onSnooze() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    name: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Note · $name", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("How it went") },
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { onSave(text) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save note")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SnoozeSheet(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: (SnoozeChoice, String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf(SnoozeChoice.HOUR) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Snooze $name", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SnoozeChoice.entries.forEach { option ->
                    val selected = choice == option
                    FilterChip(
                        selected = selected,
                        onClick = { choice = option },
                        label = { Text(option.label) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${choice.label} selected",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reason (optional)") },
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { onConfirm(choice, reason) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Snooze · ${choice.label}") }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationSheet(
    name: String,
    already: Int,
    target: Int,
    onDismiss: () -> Unit,
    onAddMinutes: (Int) -> Unit,
) {
    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text("$already / $target min logged", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            val minutes = (elapsedMs / 60000L).toInt()
            val seconds = ((elapsedMs / 1000L) % 60).toInt()
            Text(
                "%d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.displaySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    running = !running
                    if (running) {
                        scope.launch {
                            val start = System.currentTimeMillis() - elapsedMs
                            while (isActive && running) {
                                elapsedMs = System.currentTimeMillis() - start
                                delay(250)
                            }
                        }
                    }
                }) { Text(if (running) "Pause" else "Start") }
                OutlinedButton(
                    onClick = { onAddMinutes(((elapsedMs / 60000L).toInt()).coerceAtLeast(1)) },
                    enabled = elapsedMs > 5_000,
                ) { Text("Add time") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30).forEach { n ->
                    AssistChip(onClick = { onAddMinutes(n) }, label = { Text("+$n") })
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayPreview() {
    RotaTheme {
        TodayScreen(
            items = listOf(
                TodayItem(
                    bundle = HabitBundle(
                        habit = Habit(
                            id = 1,
                            name = "Strength",
                            period = Period.WEEKLY,
                            timesPerPeriod = 3,
                            rotationIndex = 1,
                        ),
                        routine = listOf(
                            RoutineItem(1, 1, "Core", 0),
                            RoutineItem(2, 1, "Legs", 1),
                            RoutineItem(3, 1, "Back", 2),
                        ),
                    ),
                    status = TodayStatus.DUE,
                    streak = 4,
                    periodDone = 1,
                    periodTarget = 3,
                ),
            ),
            onOpen = {},
            onToggle = {},
            onAdd = { _, _ -> },
            onLog = { _, _ -> },
            onSkip = {},
            onNote = { _, _ -> },
            onSnooze = { _, _, _ -> },
            onWake = {},
            onAddHabit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SnoozePreview() {
    RotaTheme {
        SnoozeSheet(name = "Strength", onDismiss = {}, onConfirm = { _, _ -> })
    }
}
