package com.tepmex.heilauncher.ui

import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.heilauncher.AppGraph
import com.tepmex.heilauncher.data.LauncherPrefs
import com.tepmex.heilauncher.domain.BatteryStatus
import com.tepmex.heilauncher.domain.LaunchableApp
import com.tepmex.heilauncher.domain.Weather
import com.tepmex.heilauncher.domain.filterApps
import com.tepmex.heilauncher.domain.resolveFavourites
import com.tepmex.heilauncher.domain.toggleFavourite
import com.tepmex.heilauncher.domain.withUsage
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class HomeUiState(
    val now: LocalDateTime,
    val systemIs24Hour: Boolean,
    val prefs: LauncherPrefs,
    val favourites: List<LaunchableApp>,
    val listedApps: List<LaunchableApp>,
    val query: String,
    val battery: BatteryStatus?,
    val weather: Weather?,
    val appsReady: Boolean,
)

class HomeViewModel(private val graph: AppGraph) : ViewModel() {
    private val apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    private val usage = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val prefs = MutableStateFlow(graph.prefs.read())
    private val battery = MutableStateFlow<BatteryStatus?>(null)
    private val weather = MutableStateFlow<Weather?>(null)
    private val query = MutableStateFlow("")
    private val ready = MutableStateFlow(false)
    private val now = MutableStateFlow(LocalDateTime.now())
    private val is24Hour = MutableStateFlow(DateFormat.is24HourFormat(graph.appContext))
    private val active = MutableStateFlow(false)
    private val homePulse = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val reloadMutex = Mutex()

    val homeEvents = homePulse.asSharedFlow()

    private val prefsListener: (LauncherPrefs) -> Unit = { prefs.value = it }

    val ui: StateFlow<HomeUiState> = combine(
        combine(apps, usage, prefs, query) { loaded, used, pref, q ->
            Core(loaded, used, pref, q)
        },
        combine(battery, weather, now, is24Hour, ready) { batt, wx, moment, hour24, isReady ->
            Sensors(batt, wx, moment, hour24, isReady)
        },
    ) { core, sensors ->
        val merged = withUsage(core.apps, if (core.prefs.showLastOpened) core.usage else emptyMap())
        HomeUiState(
            now = sensors.now,
            systemIs24Hour = sensors.is24Hour,
            prefs = core.prefs,
            favourites = resolveFavourites(core.prefs.favouriteIds, merged),
            listedApps = filterApps(merged, core.query),
            query = core.query,
            battery = if (core.prefs.showBattery) sensors.battery else null,
            weather = if (core.prefs.showWeather) sensors.weather else null,
            appsReady = sensors.ready,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    init {
        graph.prefs.addListener(prefsListener)
        viewModelScope.launch(Dispatchers.Default) { reloadApps() }
        viewModelScope.launch {
            graph.apps.changes.collect { reloadApps() }
        }
        viewModelScope.launch {
            active.collectLatest { running ->
                if (!running) return@collectLatest
                while (isActive) {
                    refreshVolatile()
                    delay(millisUntilNextMinute())
                }
            }
        }
    }

    fun setActive(value: Boolean) {
        active.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun onHomePressed() {
        query.value = ""
        homePulse.tryEmit(Unit)
    }

    /** @return true when the app is pinned after the toggle */
    fun toggleFavourite(component: String): Boolean {
        var pinned = false
        graph.prefs.update { current ->
            val ids = toggleFavourite(current.favouriteIds, component)
            pinned = component in ids
            current.copy(favouriteIds = ids)
        }
        return pinned
    }

    fun removeFavourite(component: String) {
        graph.prefs.update { current ->
            current.copy(favouriteIds = current.favouriteIds.filterNot { it == component })
        }
    }

    override fun onCleared() {
        graph.prefs.removeListener(prefsListener)
    }

    private suspend fun reloadApps() {
        reloadMutex.withLock {
            val loaded = withContext(Dispatchers.Default) { graph.apps.load() }
            apps.value = loaded
            if (loaded.isNotEmpty()) {
                val ids = loaded.mapTo(HashSet()) { it.component }
                graph.prefs.update { current ->
                    val cleaned = current.favouriteIds.filter { it in ids }
                    if (cleaned == current.favouriteIds) current else current.copy(favouriteIds = cleaned)
                }
            }
            ready.value = true
        }
    }

    private suspend fun refreshVolatile() {
        withContext(Dispatchers.IO) {
            val pref = graph.prefs.read()
            val moment = LocalDateTime.now()
            val hour24 = DateFormat.is24HourFormat(graph.appContext)
            val batt = if (pref.showBattery) graph.battery.read() else null
            val used = if (pref.showLastOpened) graph.usage.read() else emptyMap()
            val wx = if (pref.showWeather) graph.weather.refresh() else null
            if (!isActive) return@withContext
            now.value = moment
            is24Hour.value = hour24
            battery.value = batt
            usage.value = used
            weather.value = wx
        }
    }

    private fun initialState(): HomeUiState {
        val pref = prefs.value
        return HomeUiState(
            now = now.value,
            systemIs24Hour = is24Hour.value,
            prefs = pref,
            favourites = emptyList(),
            listedApps = emptyList(),
            query = "",
            battery = null,
            weather = null,
            appsReady = false,
        )
    }

    private data class Core(
        val apps: List<LaunchableApp>,
        val usage: Map<String, Long>,
        val prefs: LauncherPrefs,
        val query: String,
    )

    private data class Sensors(
        val battery: BatteryStatus?,
        val weather: Weather?,
        val now: LocalDateTime,
        val is24Hour: Boolean,
        val ready: Boolean,
    )

    companion object {
        fun millisUntilNextMinute(nowMillis: Long = System.currentTimeMillis()): Long {
            val wait = 60_000L - (nowMillis % 60_000L)
            return wait.coerceAtLeast(250L)
        }
    }
}
