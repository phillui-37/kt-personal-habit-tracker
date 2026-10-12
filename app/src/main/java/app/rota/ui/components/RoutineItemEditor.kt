package app.rota.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun RoutineItemEditor(
    items: List<String>,
    onAdd: (String) -> Unit,
    onRename: (index: Int, title: String) -> Unit,
    onDelete: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    onMove: ((from: Int, to: Int) -> Unit)? = null,
) {
    var draft by remember { mutableStateOf("") }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingText by remember { mutableStateOf("") }
    var deletingIndex by remember { mutableIntStateOf(-1) }

    fun commitAdd() {
        val title = draft.trim()
        if (title.isEmpty()) return
        onAdd(title)
        draft = ""
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("New item") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commitAdd() }),
            )
            OutlinedButton(onClick = ::commitAdd, enabled = draft.isNotBlank()) {
                Text("Add")
            }
        }
        if (items.isEmpty()) {
            Text(
                "No items yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items.forEachIndexed { index, title ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    title.ifBlank { "Untitled" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (onMove != null) {
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < items.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                    }
                }
                IconButton(onClick = {
                    editingIndex = index
                    editingText = title
                }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Update")
                }
                IconButton(onClick = { deletingIndex = index }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
        }
    }

    if (editingIndex in items.indices) {
        val index = editingIndex
        AlertDialog(
            onDismissRequest = { editingIndex = -1 },
            title = { Text("Update item") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(index, editingText.trim())
                        editingIndex = -1
                    },
                    enabled = editingText.isNotBlank(),
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = -1 }) { Text("Cancel") }
            },
        )
    }

    if (deletingIndex in items.indices) {
        val index = deletingIndex
        val title = items[index]
        AlertDialog(
            onDismissRequest = { deletingIndex = -1 },
            title = { Text("Delete item?") },
            text = { Text("Remove \"$title\" from this rotation.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(index)
                    deletingIndex = -1
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingIndex = -1 }) { Text("Cancel") }
            },
        )
    }
}
