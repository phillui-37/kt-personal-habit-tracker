package app.rota.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rota.ui.components.RoutineItemEditor
import app.rota.ui.components.SectionLabel

@Composable
fun RoutineSettingsSection(
    routines: List<RoutineHabitUi>,
    onAdd: (Long, String) -> Unit,
    onRename: (Long, Int, String) -> Unit,
    onDelete: (Long, Int) -> Unit,
    onMove: (Long, Int, Int) -> Unit,
) {
    SectionLabel("Rotating routines")
    Text(
        "Add, update, or delete the steps in each habit. The current step advances when you complete it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (routines.isEmpty()) {
        Text(
            "Add a habit first, then build its rotation here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    Column(Modifier.padding(top = 4.dp)) {
        routines.forEach { habit ->
            key(habit.id) {
                RoutineHabitBlock(
                    habit = habit,
                    onAdd = { title -> onAdd(habit.id, title) },
                    onRename = { index, title -> onRename(habit.id, index, title) },
                    onDelete = { index -> onDelete(habit.id, index) },
                    onMove = { from, to -> onMove(habit.id, from, to) },
                )
            }
        }
    }
}

@Composable
private fun RoutineHabitBlock(
    habit: RoutineHabitUi,
    onAdd: (String) -> Unit,
    onRename: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val summary = buildString {
        if (habit.items.isEmpty()) {
            append("No items")
        } else {
            append("${habit.items.size} item")
            if (habit.items.size != 1) append("s")
            habit.currentTitle?.let { append(" · now $it") }
        }
        if (habit.archived) append(" · archived")
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = !open }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(habit.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (open) "Collapse ${habit.name}" else "Expand ${habit.name}",
            )
        }
        if (open) {
            RoutineItemEditor(
                items = habit.items,
                onAdd = onAdd,
                onRename = onRename,
                onDelete = onDelete,
                onMove = onMove,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
