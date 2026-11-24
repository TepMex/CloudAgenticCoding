package com.tepmex.pacholli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tepmex.pacholli.ui.theme.Drawer
import com.tepmex.pacholli.ui.theme.Teal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object Routes {
    const val Journal = "journal"
    const val Search = "search"
    const val Accounts = "accounts"
    const val Account = "account"
    const val Units = "units"
    const val Unit = "unit"
    const val Reports = "reports"
    const val Entry = "entry"
    const val Settings = "settings"
    const val Io = "io"
}

private val drawerRoutes = setOf(Routes.Journal, Routes.Search, Routes.Accounts, Routes.Units, Routes.Reports, Routes.Settings, Routes.Io)

@Composable
fun PacholliShell(factory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
    val vm: PacholliViewModel = viewModel(factory = factory)
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val book by vm.book.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = route in drawerRoutes,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Drawer, modifier = Modifier.width(300.dp)) {
                DrawerBody(book.title) { destination ->
                    scope.launch { drawerState.close() }
                    nav.navigate(destination) { launchSingleTop = true }
                }
            }
        },
        modifier = modifier,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = Routes.Journal, modifier = Modifier.fillMaxSize()) {
                composable(Routes.Journal) { JournalScreen(vm, nav, openDrawer(scope, drawerState)) }
                composable(Routes.Search) { SearchScreen(vm, nav, openDrawer(scope, drawerState)) }
                composable(Routes.Accounts) { AccountsScreen(vm, nav, openDrawer(scope, drawerState)) }
                composable(Routes.Account) { AccountEditorScreen(vm, nav) }
                composable(Routes.Units) { UnitsScreen(vm, nav, openDrawer(scope, drawerState)) }
                composable(Routes.Unit) { UnitEditorScreen(vm, nav) }
                composable(Routes.Reports) { ReportsScreen(vm, nav, openDrawer(scope, drawerState)) }
                composable(Routes.Entry) { EntryScreen(vm, nav) }
                composable(Routes.Settings) { SettingsScreen(vm, nav, openDrawer(scope, drawerState)) }
                composable(Routes.Io) { IoScreen(vm, nav, openDrawer(scope, drawerState)) }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            )
        }
    }
}

private fun openDrawer(scope: CoroutineScope, drawerState: androidx.compose.material3.DrawerState): () -> Unit =
    { scope.launch { drawerState.open() } }

@Composable
private fun DrawerBody(title: String, go: (String) -> Unit) {
    Column(Modifier.fillMaxHeight().background(Drawer).padding(top = 28.dp)) {
        Text(
            "Pacholli",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        Text(
            title.ifBlank { "Личные финансы" },
            color = Color(0xFFBDBDBD),
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
        )
        HorizontalDivider(color = Color(0xFF444444))
        DrawerRow(Icons.Filled.Search, Color(0xFF64B5F6), "Поиск") { go(Routes.Search) }
        DrawerRow(Icons.Filled.AccountBalance, Color(0xFFFFB74D), "Счета") { go(Routes.Accounts) }
        DrawerRow(Icons.Filled.Payments, Color(0xFF81C784), "Единицы") { go(Routes.Units) }
        DrawerRow(Icons.Filled.Assessment, Color(0xFFE57373), "Отчёты") { go(Routes.Reports) }
        HorizontalDivider(color = Color(0xFF444444))
        DrawerRow(Icons.Filled.Settings, Color(0xFF90A4AE), "Настройки") { go(Routes.Settings) }
        DrawerRow(Icons.Filled.ImportExport, Teal, "Импорт / Экспорт") { go(Routes.Io) }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
