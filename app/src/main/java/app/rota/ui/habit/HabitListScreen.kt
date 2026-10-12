package app.rota.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rota.domain.model.HabitBundle
import app.rota.ui.components.CategoryFilterBar
import app.rota.ui.components.EmptyState
import app.rota.ui.components.periodLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    bundles: List<HabitBundle>,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Set<Long>) -> Unit,
    onUpdateCategory: (Set<Long>, String) -> Unit,
    hasAny: Boolean = bundles.isNotEmpty(),
    categories: List<String> = emptyList(),
    includeUncategorized: Boolean = false,
    category: String? = null,
    onCategory: (String?) -> Unit = {},
) {
    var batch by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var deleteStep by remember { mutableIntStateOf(0) }
    var showCategory by remember { mutableStateOf(false) }
    val visibleIds = remember(bundles) { bundles.map { it.habit.id }.toSet() }
    LaunchedEffect(visibleIds) {
        selectedIds = selectedIds.intersect(visibleIds)
    }

    fun leaveBatch() {
        batch = false
        selectedIds = emptySet()
        deleteStep = 0
        showCategory = false
    }

    val selectedBundles = bundles.filter { it.habit.id in selectedIds }
    val distinctCategories = selectedBundles.map { it.habit.category.trim() }.distinctBy { it.lowercase() }
    val initialCategory = if (distinctCategories.size == 1) distinctCategories.first() else null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Habits", fontWeight = FontWeight.Medium) },
                actions = {
                    if (batch) {
                        val allSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
                        TextButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else visibleIds
                        }) { Text(if (allSelected) "None" else "All") }
                        TextButton(onClick = { leaveBatch() }) { Text("Done") }
                    } else {
                        if (bundles.isNotEmpty()) {
                            TextButton(onClick = { batch = true }) { Text("Batch") }
                        }
                        TextButton(onClick = onAdd) { Text("Add") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (batch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (selectedIds.size) {
                            0 -> "None selected"
                            1 -> "1 selected"
                            else -> "${selectedIds.size} selected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    TextButton(
                        onClick = { deleteStep = 1 },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                            disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                        ),
                    ) { Text("Delete") }
                    TextButton(
                        onClick = { showCategory = true },
                        enabled = selectedIds.isNotEmpty(),
                    ) { Text("Category") }
                }
            }
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
                    title = "No habits yet",
                    body = "Start with one daily check, or a rotating weekly split.",
                    modifier = Modifier.padding(24.dp),
                )
                bundles.isEmpty() -> EmptyState(
                    title = "Nothing in this category",
                    body = "Choose another category, or All.",
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(bundles, key = { it.habit.id }) { bundle ->
                        val id = bundle.habit.id
                        val selected = id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (batch && selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.background,
                                )
                                .then(
                                    if (batch) {
                                        Modifier.toggleable(
                                            value = selected,
                                            role = Role.Checkbox,
                                            onValueChange = {
                                                selectedIds = if (selected) selectedIds - id else selectedIds + id
                                            },
                                        )
                                    } else {
                                        Modifier.clickable { onOpen(id) }
                                    },
                                )
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (batch) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = null,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(bundle.habit.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    buildString {
                                        bundle.habit.category.trim().takeIf { it.isNotEmpty() }?.let {
                                            append(it)
                                            append(" · ")
                                        }
                                        append(periodLabel(bundle.habit.period))
                                        bundle.currentRoutine?.let { append(" · "); append(it.title) }
                                        if (bundle.habit.archived) append(" · archived")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteStep > 0) {
        DeleteHabitsConfirmDialog(
            count = selectedIds.size,
            step = deleteStep,
            onDismiss = { deleteStep = 0 },
            onContinue = { deleteStep = 2 },
            onConfirm = {
                onDelete(selectedIds)
                leaveBatch()
            },
        )
    }
    if (showCategory) {
        CategoryBatchDialog(
            count = selectedIds.size,
            categories = categories,
            initial = initialCategory,
            onDismiss = { showCategory = false },
            onApply = { category ->
                onUpdateCategory(selectedIds, category)
                leaveBatch()
            },
        )
    }
}
