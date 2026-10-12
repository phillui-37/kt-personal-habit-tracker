package app.rota.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rota.data.prefs.RoutineTemplate
import app.rota.ui.components.RoutineItemEditor
import app.rota.ui.components.SectionLabel

@Composable
fun RoutinePresetSection(
    presets: List<RoutineTemplate>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAddItem: (String, String) -> Unit,
    onRenameItem: (String, Int, String) -> Unit,
    onDeleteItem: (String, Int) -> Unit,
    onMoveItem: (String, Int, Int) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    fun commitAdd() {
        val name = draft.trim()
        if (name.isEmpty()) return
        onAdd(name)
        draft = ""
    }

    SectionLabel("Routine presets")
    Text(
        "These show up when you add or edit a habit. Build the rotations you actually use.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Preset name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { commitAdd() }),
        )
        OutlinedButton(onClick = ::commitAdd, enabled = draft.isNotBlank()) { Text("Add") }
    }
    if (presets.isEmpty()) {
        Text(
            "No presets yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    Column(Modifier.padding(top = 4.dp)) {
        presets.forEach { preset ->
            key(preset.id) {
                RoutinePresetBlock(
                    preset = preset,
                    onRename = { name -> onRename(preset.id, name) },
                    onDelete = { onDelete(preset.id) },
                    onAddItem = { title -> onAddItem(preset.id, title) },
                    onRenameItem = { index, title -> onRenameItem(preset.id, index, title) },
                    onDeleteItem = { index -> onDeleteItem(preset.id, index) },
                    onMoveItem = { from, to -> onMoveItem(preset.id, from, to) },
                )
            }
        }
    }
}

@Composable
private fun RoutinePresetBlock(
    preset: RoutineTemplate,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddItem: (String) -> Unit,
    onRenameItem: (Int, String) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(preset.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    val summary = if (preset.items.isEmpty()) {
        "No items"
    } else {
        preset.items.joinToString(" → ")
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
                Text(preset.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = {
                renameText = preset.name
                renaming = true
            }) {
                Icon(Icons.Outlined.Edit, contentDescription = "Rename ${preset.name}")
            }
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (open) "Collapse ${preset.name}" else "Expand ${preset.name}",
            )
        }
        if (open) {
            RoutineItemEditor(
                items = preset.items,
                onAdd = onAddItem,
                onRename = onRenameItem,
                onDelete = onDeleteItem,
                onMove = onMoveItem,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            TextButton(onClick = { confirmDelete = true }) { Text("Delete preset") }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename preset") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renameText.trim())
                        renaming = false
                    },
                    enabled = renameText.isNotBlank(),
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete preset?") },
            text = { Text("Remove \"${preset.name}\". Habits you already saved keep their own items.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
