package app.rota.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

internal fun resolvedBatchCategory(
    existing: List<String>,
    selected: String,
    typed: String,
): String {
    val raw = typed.trim().ifEmpty { selected.trim() }
    if (raw.isEmpty()) return ""
    return existing.firstOrNull { it.equals(raw, ignoreCase = true) } ?: raw
}

@Composable
internal fun DeleteHabitsConfirmDialog(
    count: Int,
    step: Int,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (count == 1) "Delete this habit?" else "Delete $count habits?") },
            text = { Text("Nothing is deleted yet. You will confirm once more.") },
            confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    } else if (step == 2) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    if (count == 1) {
                        "This habit and its history will be removed. This cannot be undone."
                    } else {
                        "$count habits and their history will be removed. This cannot be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryBatchDialog(
    count: Int,
    categories: List<String>,
    initial: String?,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var picked by remember { mutableStateOf(initial) }
    var typed by remember { mutableStateOf("") }
    val pendingNew = typed.trim().takeIf { raw ->
        raw.isNotEmpty() && categories.none { it.equals(raw, ignoreCase = true) }
    }
    val options = categories + listOfNotNull(pendingNew)
    val active: String? = if (typed.isNotBlank()) {
        resolvedBatchCategory(categories, "", typed)
    } else {
        picked
    }
    val canApply = typed.isNotBlank() || picked != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 420.dp)
                    .padding(24.dp),
            ) {
                Text("Category", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Same category for ${if (count == 1) "1 habit" else "$count habits"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (initial == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "These habits don't share one yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tap a category to select it. Tap it again to clear it. Type a name to create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryChoiceChip(
                            label = "Uncategorized",
                            selected = active == "",
                            onClick = {
                                typed = ""
                                picked = ""
                            },
                        )
                        options.forEach { option ->
                            val selected = active?.equals(option, ignoreCase = true) == true
                            CategoryChoiceChip(
                                label = option,
                                selected = selected,
                                onClick = {
                                    typed = ""
                                    picked = if (selected) "" else option
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New category") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            onApply(resolvedBatchCategory(categories, picked.orEmpty(), typed))
                        },
                        enabled = canApply,
                    ) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun CategoryChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
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
