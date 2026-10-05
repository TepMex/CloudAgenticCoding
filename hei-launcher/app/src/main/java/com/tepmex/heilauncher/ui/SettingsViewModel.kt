package com.tepmex.heilauncher.ui

import android.app.role.RoleManager
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.heilauncher.AppGraph
import com.tepmex.heilauncher.data.LauncherPrefs
import com.tepmex.heilauncher.domain.ClockFormat
import com.tepmex.heilauncher.domain.NameSize
import com.tepmex.heilauncher.domain.moveFavourite
import com.tepmex.heilauncher.domain.resolveFavourites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavouriteRow(val component: String, val label: String)

data class SettingsUiState(
    val prefs: LauncherPrefs = LauncherPrefs(),
    val favourites: List<FavouriteRow> = emptyList(),
    val usageGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val homeRoleHeld: Boolean = false,
    val versionName: String = "",
)

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = state.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val prefs = graph.prefs.read()
            val apps = graph.apps.load()
            val rows = resolveFavourites(prefs.favouriteIds, apps).map {
                FavouriteRow(it.component, it.label)
            }
            state.value = SettingsUiState(
                prefs = prefs,
                favourites = rows,
                usageGranted = graph.usage.canRead(),
                locationGranted = graph.weather.hasLocationPermission(),
                homeRoleHeld = homeRoleHeld(),
                versionName = versionName(),
            )
        }
    }

    fun setClock(format: ClockFormat) = edit { it.copy(clockFormat = format) }

    fun setShowYear(value: Boolean) = edit { it.copy(showYearProgress = value) }

    fun setShowDay(value: Boolean) = edit { it.copy(showDayProgress = value) }

    fun setShowBattery(value: Boolean) = edit { it.copy(showBattery = value) }

    fun setShowWeather(value: Boolean) = edit { it.copy(showWeather = value) }

    fun setShowLastOpened(value: Boolean) = edit { it.copy(showLastOpened = value) }

    fun setNameSize(size: NameSize) = edit { it.copy(nameSize = size) }

    fun move(component: String, delta: Int) {
        graph.prefs.update { current ->
            current.copy(favouriteIds = moveFavourite(current.favouriteIds, component, delta))
        }
        refresh()
    }

    fun remove(component: String) {
        graph.prefs.update { current ->
            current.copy(favouriteIds = current.favouriteIds.filterNot { it == component })
        }
        refresh()
    }

    private fun edit(transform: (LauncherPrefs) -> LauncherPrefs) {
        graph.prefs.update(transform)
        refresh()
    }

    private fun homeRoleHeld(): Boolean {
        val roles = graph.appContext.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun versionName(): String {
        return graph.appContext.packageManager.getPackageInfo(
            graph.appContext.packageName,
            PackageManager.PackageInfoFlags.of(0),
        ).versionName.orEmpty()
    }
}
