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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rota.domain.DueCalculator
import app.rota.domain.StreakCalculator
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.HabitBundle
import app.rota.ui.components.Heatmap
import app.rota.ui.components.QuietCard
import app.rota.ui.components.SectionLabel
import app.rota.ui.components.periodLabel
import app.rota.ui.theme.habitColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    bundle: HabitBundle?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val habit = bundle?.habit
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(habit?.name ?: "Habit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onEdit) { Text("Edit") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (bundle == null || habit == null) {
            Text("This habit is gone.", modifier = Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val today = LocalDate.now()
        val streak = StreakCalculator.currentStreak(habit, bundle.checkIns, today)
        val (done, target) = DueCalculator.periodProgress(habit, today, bundle.checkIns)
        val heat = bundle.checkIns.associate { log ->
            log.date to when (log.status) {
                CheckInStatus.DONE -> 1f
                CheckInStatus.LOG -> (log.value.toFloat() / habit.target.coerceAtLeast(1)).coerceIn(0.2f, 0.8f)
                CheckInStatus.SKIPPED -> 0.15f
            }
        }
        val formatter = DateTimeFormatter.ofPattern("d MMM")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            bundle.currentRoutine?.let {
                Text(
                    "Today: ${it.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = habitColor(habit.colorIndex),
                )
            }
            Text(
                "${periodLabel(habit.period)} · $done / $target this period",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                QuietCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Streak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$streak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    }
                }
                QuietCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${bundle.checkIns.count { it.status == CheckInStatus.DONE }}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            SectionLabel("History")
            Heatmap(days = heat, color = habitColor(habit.colorIndex))
            val snoozes = listOfNotNull(bundle.snooze).filter { it.reason.isNotBlank() }
            if (snoozes.isNotEmpty()) {
                SectionLabel("Recent snooze")
                snoozes.forEach {
                    Text(it.reason, style = MaterialTheme.typography.bodyLarge)
                }
            }
            SectionLabel("Latest")
            bundle.checkIns.sortedByDescending { it.epochDay }.take(8).forEach { log ->
                Text(
                    "${log.date.format(formatter)} · ${log.status.name.lowercase()}" +
                        (log.routineLabel?.let { " · $it" } ?: "") +
                        if (log.value > 0 && habit.target > 1) " · ${log.value}/${habit.target}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onArchive) {
                Text(if (habit.archived) "Unarchive" else "Archive")
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
