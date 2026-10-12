package app.rota.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.rota.ui.habit.HabitDetailScreen
import app.rota.ui.habit.HabitDetailViewModel
import app.rota.ui.habit.HabitEditScreen
import app.rota.ui.habit.HabitEditorViewModel
import app.rota.ui.habit.HabitListScreen
import app.rota.ui.habit.HabitListViewModel
import app.rota.ui.review.ReviewScreen
import app.rota.ui.review.ReviewViewModel
import app.rota.ui.settings.SettingsScreen
import app.rota.ui.settings.SettingsViewModel
import app.rota.ui.today.TodayScreen
import app.rota.ui.today.TodayViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Outlined.CalendarToday),
    Tab("review", "Review", Icons.Outlined.Insights),
    Tab("more", "More", Icons.Outlined.Menu),
)

@Composable
fun RotaNav(openHabitId: Long? = null) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showBar = route in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") {
                val vm: TodayViewModel = hiltViewModel()
                val items by vm.items.collectAsStateWithLifecycle()
                TodayScreen(
                    items = items,
                    onOpen = { nav.navigate("habit/$it") },
                    onToggle = vm::toggle,
                    onAdd = vm::add,
                    onLog = vm::log,
                    onSkip = vm::skip,
                    onNote = vm::note,
                    onSnooze = vm::snooze,
                    onWake = vm::wake,
                    onAddHabit = { nav.navigate("edit/0") },
                )
            }
            composable("review") {
                val vm: ReviewViewModel = hiltViewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                ReviewScreen(
                    state = state,
                    onRange = vm::setRange,
                    onStatus = vm::setStatus,
                    onPeriod = vm::setPeriod,
                    onHabit = vm::setHabit,
                    onCategory = vm::setCategory,
                    onCustomRange = vm::setCustomRange,
                )
            }
            composable("more") {
                val vm: HabitListViewModel = hiltViewModel()
                val bundles by vm.bundles.collectAsStateWithLifecycle()
                HabitListScreen(
                    bundles = bundles,
                    onOpen = { nav.navigate("habit/$it") },
                    onAdd = { nav.navigate("edit/0") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable("settings") {
                val vm: SettingsViewModel = hiltViewModel()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val backupUi by vm.backupUi.collectAsStateWithLifecycle()
                val routines by vm.routines.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    backupUi = backupUi,
                    canExact = vm.canExactAlarms(),
                    onTheme = vm::setTheme,
                    onWebDav = vm::setWebDav,
                    onExport = vm::exportTo,
                    onRestoreFile = vm::restoreFrom,
                    onBackupWebDav = vm::backupWebDav,
                    onRestoreWebDav = vm::restoreWebDav,
                    onTestWebDav = vm::testWebDav,
                    onBack = { nav.popBackStack() },
                    routines = routines,
                    onAddRoutineItem = vm::addRoutineItem,
                    onRenameRoutineItem = vm::renameRoutineItem,
                    onDeleteRoutineItem = vm::deleteRoutineItem,
                    onMoveRoutineItem = vm::moveRoutineItem,
                )
            }
            composable(
                "edit/{habitId}",
                arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                val vm: HabitEditorViewModel = hiltViewModel()
                val draft by vm.draft.collectAsStateWithLifecycle()
                val categories by vm.categorySuggestions.collectAsStateWithLifecycle()
                HabitEditScreen(
                    draft = draft,
                    onChange = vm::update,
                    onTemplate = vm::applyTemplate,
                    onSave = vm::save,
                    onBack = { nav.popBackStack() },
                    categorySuggestions = categories,
                )
            }
            composable(
                "habit/{habitId}",
                arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
            ) { entry ->
                val vm: HabitDetailViewModel = hiltViewModel()
                val bundle by vm.bundle.collectAsStateWithLifecycle()
                val id = entry.arguments?.getLong("habitId") ?: 0L
                HabitDetailScreen(
                    bundle = bundle,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("edit/$id") },
                    onArchive = vm::archive,
                    onDelete = {
                        vm.delete()
                        nav.popBackStack()
                    },
                )
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(openHabitId) {
        if (openHabitId != null && openHabitId > 0) {
            nav.navigate("habit/$openHabitId")
        }
    }
}
