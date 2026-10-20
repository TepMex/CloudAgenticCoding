package com.tepmex.inbox.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tepmex.inbox.R
import com.tepmex.inbox.domain.CenterAction
import com.tepmex.inbox.domain.Routes
import com.tepmex.inbox.domain.centerAction
import com.tepmex.inbox.domain.tabForRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow

@Composable
fun InboxAppShell(
    factory: InboxViewModelFactory,
    effects: Channel<LaunchEffect>,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = viewModel(factory = factory),
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current
    val backStack by nav.currentBackStackEntryAsState()
    val tab = tabForRoute(backStack?.destination?.route)
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val hapticOn by viewModel.haptic.collectAsStateWithLifecycle()
    val center = centerAction(tab, draft)
    val send = {
        if (viewModel.send() && hapticOn) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.notices.collect { notice ->
            val text = when (notice) {
                Notice.SaveFailed -> context.getString(R.string.notice_save_failed)
                Notice.TimePassed -> context.getString(R.string.notice_time_passed)
                Notice.Copied -> context.getString(R.string.notice_copied)
            }
            snackbar.showSnackbar(text)
        }
    }
    LaunchedEffect(effects, nav) {
        effects.consumeAsFlow().collect { effect ->
            when (effect) {
                LaunchEffect.OpenArchive -> nav.openTab(Routes.ARCHIVE)
                is LaunchEffect.SharedText -> {
                    viewModel.captureIncoming(effect.text)
                    nav.openTab(Routes.INBOX)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            InboxBottomBar(
                tab = tab,
                center = center,
                onReminders = { nav.openTab(Routes.REMINDERS) },
                onInbox = { nav.openTab(Routes.INBOX) },
                onCenter = {
                    when (center) {
                        CenterAction.Send, CenterAction.SendDisabled -> send()
                        CenterAction.OpenCapture -> nav.openTab(Routes.CAPTURE)
                    }
                },
                onArchive = { nav.openTab(Routes.ARCHIVE) },
                onSettings = { nav.openTab(Routes.SETTINGS) },
            )
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.CAPTURE,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.CAPTURE) {
                CaptureScreen(viewModel = viewModel)
            }
            composable(Routes.INBOX) {
                InboxScreen(
                    viewModel = viewModel,
                    onOpen = { id -> nav.navigate(Routes.process(id)) },
                )
            }
            composable(
                route = Routes.PROCESS,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val args = entry.arguments
                val id = if (args != null && args.containsKey("id")) args.getLong("id") else -1L
                ProcessScreen(
                    viewModel = viewModel,
                    id = id,
                    onFinished = { nav.popBackStack() },
                )
            }
            composable(Routes.REMINDERS) { RemindersScreen(viewModel = viewModel) }
            composable(Routes.ARCHIVE) { ArchiveScreen(viewModel = viewModel) }
            composable(Routes.SETTINGS) { SettingsScreen(viewModel = viewModel) }
        }
    }
}

private fun NavHostController.openTab(route: String) {
    val current = currentDestination?.route
    val onProcess = current?.startsWith("process") == true
    if (!onProcess && current == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
