package com.tepmex.ctxcalendar.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tepmex.ctxcalendar.ui.calendar.CalendarScreen
import com.tepmex.ctxcalendar.ui.calendar.CalendarViewModel
import com.tepmex.ctxcalendar.ui.calendar.CalendarViewModelFactory
import com.tepmex.ctxcalendar.ui.day.DayDetailScreen
import com.tepmex.ctxcalendar.ui.day.DayDetailViewModelFactory
import com.tepmex.ctxcalendar.ui.photo.PhotoViewerScreen
import com.tepmex.ctxcalendar.ui.settings.SettingsScreen
import com.tepmex.ctxcalendar.ui.settings.SettingsViewModelFactory
import java.time.LocalDate

object Routes {
    const val CALENDAR = "calendar"
    const val DAY = "day/{date}"
    const val PHOTO = "photo/{photoId}"
    const val SETTINGS = "settings"

    fun day(date: LocalDate): String = "day/$date"
    fun photo(photoId: Long): String = "photo/$photoId"
}

@Composable
fun CtxCalendarNavHost(
    viewModelFactory: CalendarViewModelFactory,
    dayDetailViewModelFactory: DayDetailViewModelFactory,
    settingsViewModelFactory: SettingsViewModelFactory,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val viewModel: CalendarViewModel = viewModel(factory = viewModelFactory)

    NavHost(
        navController = navController,
        startDestination = Routes.CALENDAR,
        modifier = modifier,
    ) {
        composable(Routes.CALENDAR) {
            CalendarScreen(
                viewModel = viewModel,
                onDayClick = { date ->
                    navController.navigate(Routes.day(date))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModelFactory = settingsViewModelFactory,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.DAY,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
            ),
        ) { entry ->
            val date = LocalDate.parse(entry.arguments?.getString("date"))
            DayDetailScreen(
                date = date,
                photos = viewModel.photosFor(date),
                dayDetailViewModelFactory = dayDetailViewModelFactory,
                onBack = { navController.popBackStack() },
                onPhotoClick = { photo ->
                    navController.navigate(Routes.photo(photo.id))
                },
            )
        }

        composable(
            route = Routes.PHOTO,
            arguments = listOf(
                navArgument("photoId") { type = NavType.LongType },
            ),
        ) { entry ->
            val photoId = entry.arguments?.getLong("photoId") ?: return@composable
            val photo = viewModel.photoById(photoId) ?: run {
                navController.popBackStack()
                return@composable
            }
            PhotoViewerScreen(
                photoUri = photo.uri,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
