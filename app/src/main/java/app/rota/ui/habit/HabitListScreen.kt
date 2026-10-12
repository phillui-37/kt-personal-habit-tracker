package app.rota.ui.habit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.rota.domain.model.HabitBundle
import app.rota.ui.components.EmptyState
import app.rota.ui.components.periodLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    bundles: List<HabitBundle>,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Habits", fontWeight = FontWeight.Medium) },
                actions = {
                    TextButton(onClick = onSettings) { Text("Settings") }
                    TextButton(onClick = onAdd) { Text("Add") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (bundles.isEmpty()) {
            EmptyState(
                title = "No habits yet",
                body = "Start with one daily check, or a rotating weekly strength split.",
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(bundles, key = { it.habit.id }) { bundle ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onOpen(bundle.habit.id) }
                            .padding(vertical = 14.dp),
                    ) {
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
