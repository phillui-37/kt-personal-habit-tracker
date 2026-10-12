package app.rota.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.rota.ui.theme.RotaTheme
import app.rota.domain.model.HabitType
import app.rota.domain.model.Period
import app.rota.notify.ReminderScheduler
import app.rota.ui.components.ChipRow
import app.rota.ui.components.ColorPicker
import app.rota.ui.components.SectionLabel
import app.rota.ui.components.Stepper
import app.rota.ui.components.WeekdayPicker
import kotlinx.coroutines.launch

private val templates = listOf(
    "Strength 3-day" to listOf("Core", "Legs", "Back"),
    "Push / Pull / Legs" to listOf("Push", "Pull", "Legs"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(
    draft: HabitDraft,
    onChange: ((HabitDraft) -> HabitDraft) -> Unit,
    onTemplate: (List<String>) -> Unit,
    onSave: suspend () -> Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showTime by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(
        initialHour = draft.reminderMinutes / 60,
        initialMinute = draft.reminderMinutes % 60,
        is24Hour = false,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "New habit" else "Edit habit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { if (onSave()) onBack() } }) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = { value -> onChange { it.copy(note = value) } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("Note") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            SectionLabel("Type")
            ChipRow(
                options = listOf(
                    "Check" to (draft.type == HabitType.CHECK),
                    "Count" to (draft.type == HabitType.COUNT),
                    "Duration" to (draft.type == HabitType.DURATION),
                ),
                onSelect = { index ->
                    val type = HabitType.entries[index]
                    onChange {
                        it.copy(
                            type = type,
                            target = when (type) {
                                HabitType.CHECK -> 1
                                HabitType.COUNT -> if (it.target < 2) 8 else it.target
                                HabitType.DURATION -> if (it.type != HabitType.DURATION) 30 else it.target
                            },
                        )
                    }
                },
            )
            if (draft.type != HabitType.CHECK) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (draft.type == HabitType.COUNT) "Target count" else "Target minutes")
                    Spacer(Modifier.weight(1f))
                    Stepper(value = draft.target, onChange = { v -> onChange { it.copy(target = v) } }, range = 1..600)
                }
            }

            SectionLabel("How often")
            ChipRow(
                options = Period.entries.map { period ->
                    period.label() to (draft.period == period)
                },
                onSelect = { index -> onChange { it.copy(period = Period.entries[index]) } },
            )
            if (draft.period != Period.DAILY && draft.period != Period.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Times per ${draft.period.label().lowercase()}")
                    Spacer(Modifier.weight(1f))
                    Stepper(
                        value = draft.timesPerPeriod,
                        onChange = { v -> onChange { it.copy(timesPerPeriod = v) } },
                        range = 1..31,
                    )
                }
            }
            if (draft.period == Period.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Every N days")
                    Spacer(Modifier.weight(1f))
                    Stepper(
                        value = draft.intervalDays,
                        onChange = { v -> onChange { it.copy(intervalDays = v) } },
                        range = 2..90,
                    )
                }
            }

            SectionLabel("Days")
            WeekdayPicker(mask = draft.weekdaysMask, onChange = { mask -> onChange { it.copy(weekdaysMask = mask) } })

            SectionLabel("Rotating routine")
            Text(
                "The current item advances when you complete the habit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { (label, titles) ->
                    OutlinedButton(onClick = { onTemplate(titles) }) { Text(label) }
                }
            }
            draft.routine.forEachIndexed { index, title ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { value ->
                            onChange { d -> d.copy(routine = d.routine.toMutableList().also { it[index] = value }) }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            if (index > 0) {
                                onChange { d ->
                                    d.copy(routine = d.routine.toMutableList().also {
                                        val swap = it[index - 1]
                                        it[index - 1] = it[index]
                                        it[index] = swap
                                    })
                                }
                            }
                        },
                    ) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up") }
                    IconButton(
                        onClick = {
                            if (index < draft.routine.lastIndex) {
                                onChange { d ->
                                    d.copy(routine = d.routine.toMutableList().also {
                                        val swap = it[index + 1]
                                        it[index + 1] = it[index]
                                        it[index] = swap
                                    })
                                }
                            }
                        },
                    ) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down") }
                    IconButton(onClick = {
                        onChange { d -> d.copy(routine = d.routine.toMutableList().also { it.removeAt(index) }) }
                    }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove") }
                }
            }
            OutlinedButton(
                onClick = { onChange { it.copy(routine = it.routine + "") } },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Add item") }

            SectionLabel("Reminder")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(if (draft.reminderEnabled) ReminderScheduler.clockLabel(draft.reminderMinutes) else "Off")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = draft.reminderEnabled,
                    onCheckedChange = { enabled ->
                        onChange { it.copy(reminderEnabled = enabled) }
                        if (enabled) showTime = true
                    },
                )
            }
            if (draft.reminderEnabled) {
                TextButton(onClick = { showTime = true }) { Text("Change time") }
            }
            if (showTime) {
                TimePicker(state = timeState)
                TextButton(onClick = {
                    onChange {
                        it.copy(reminderMinutes = ReminderScheduler.minutesOf(timeState.hour, timeState.minute))
                    }
                    showTime = false
                }) { Text("Use this time") }
            }

            SectionLabel("Color")
            ColorPicker(selected = draft.colorIndex, onSelect = { i -> onChange { it.copy(colorIndex = i) } })
            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun Period.label(): String = when (this) {
    Period.DAILY -> "Daily"
    Period.WEEKLY -> "Week"
    Period.MONTHLY -> "Month"
    Period.QUARTERLY -> "Quarter"
    Period.YEARLY -> "Year"
    Period.CUSTOM -> "Every N days"
}

@Preview(showBackground = true)
@Composable
private fun HabitEditPreview() {
    RotaTheme {
        HabitEditScreen(
            draft = HabitDraft(
                name = "Strength",
                period = Period.WEEKLY,
                timesPerPeriod = 3,
                routine = listOf("Core", "Legs", "Back"),
            ),
            onChange = {},
            onTemplate = {},
            onSave = { true },
            onBack = {},
        )
    }
}
