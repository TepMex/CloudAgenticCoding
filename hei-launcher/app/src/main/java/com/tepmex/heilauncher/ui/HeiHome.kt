package com.tepmex.heilauncher.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.heilauncher.R
import com.tepmex.heilauncher.domain.LaunchableApp
import com.tepmex.heilauncher.domain.NameSize
import com.tepmex.heilauncher.domain.formatBattery
import com.tepmex.heilauncher.domain.formatClock
import com.tepmex.heilauncher.domain.formatHomeDate
import com.tepmex.heilauncher.domain.formatSince
import com.tepmex.heilauncher.domain.formatWeather
import com.tepmex.heilauncher.domain.jumpIndex
import com.tepmex.heilauncher.domain.railIndexAt
import com.tepmex.heilauncher.domain.railLetters
import androidx.core.view.WindowCompat
import com.tepmex.heilauncher.domain.DayYearProgress
import com.tepmex.heilauncher.timing.ui.IdealTimingAppShell
import com.tepmex.heilauncher.timing.ui.IdealTimingViewModelFactory
import com.tepmex.heilauncher.timing.ui.theme.IdealTimingTheme
import java.util.Locale
import kotlinx.coroutines.launch

private const val PAGE_TIMING = 0
private const val PAGE_FAVOURITES = 1
private const val PAGE_APPS = 2

private val Ink = Color.White
private val Muted = Color(0xFF8A8A8A)
private val Dim = Color(0xFF4A4A4A)

@Composable
fun HeiHome(
    viewModel: HomeViewModel,
    timingFactory: IdealTimingViewModelFactory,
    onOpenSystemSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = PAGE_FAVOURITES, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val timingPage = pagerState.currentPage == PAGE_TIMING

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = timingPage
        controller.isAppearanceLightNavigationBars = timingPage
    }

    LaunchedEffect(viewModel) {
        viewModel.homeEvents.collect {
            pagerState.scrollToPage(PAGE_FAVOURITES)
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    BackHandler(enabled = pagerState.currentPage != PAGE_FAVOURITES) {
        scope.launch {
            pagerState.scrollToPage(PAGE_FAVOURITES)
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize().background(Color.Black),
        beyondViewportPageCount = 1,
    ) { page ->
        when (page) {
            PAGE_TIMING -> TimingPage(
                factory = timingFactory,
                active = pagerState.currentPage == PAGE_TIMING,
            )
            PAGE_FAVOURITES -> FavouritesPage(
                state = state,
                onLaunch = onLaunch,
                onUnpin = viewModel::removeFavourite,
                onOpenSystemSettings = onOpenSystemSettings,
                onOpenLauncherSettings = onOpenLauncherSettings,
            )
            PAGE_APPS -> AllAppsPage(
                state = state,
                onQuery = viewModel::setQuery,
                onLaunch = onLaunch,
                onToggleFavourite = { app ->
                    viewModel.toggleFavourite(app.component)
                },
            )
        }
    }
}

@Composable
private fun TimingPage(
    factory: IdealTimingViewModelFactory,
    active: Boolean,
) {
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        requestLocationIfNeeded(context, locationPermission::launch)
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        val notificationsGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!notificationsGranted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestLocationIfNeeded(activity, locationPermission::launch)
        }
    }
    IdealTimingTheme {
        IdealTimingAppShell(
            factory = factory,
            nfcEnabled = active,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun requestLocationIfNeeded(
    context: Context,
    launch: (Array<String>) -> Unit,
) {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) {
        launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
        )
    }
}

@Composable
private fun FavouritesPage(
    state: HomeUiState,
    onLaunch: (LaunchableApp) -> Unit,
    onUnpin: (String) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
) {
    val locale = locale()
    val nowMillis = System.currentTimeMillis()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp)) {
            Text(
                text = formatClock(state.now.toLocalTime(), state.prefs.clockFormat, state.systemIs24Hour, locale),
                color = Ink,
                fontSize = 48.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatHomeDate(state.now.toLocalDate(), locale),
                color = Ink,
                fontSize = 20.sp,
            )
            if (state.prefs.showYearProgress) {
                val year = DayYearProgress.year(state.now.toLocalDate())
                Spacer(Modifier.height(10.dp))
                ProgressRow(
                    percent = year.percent,
                    fraction = year.fraction,
                    description = stringResource(R.string.year_in_progress, year.percent),
                )
            }
            if (state.prefs.showDayProgress) {
                val day = DayYearProgress.day(state.now.toLocalTime())
                Spacer(Modifier.height(4.dp))
                ProgressRow(
                    percent = day.percent,
                    fraction = day.fraction,
                    description = stringResource(R.string.day_in_progress, day.percent),
                )
            }
            val battery = state.battery?.let { formatBattery(it) }
            val weather = state.weather?.let { formatWeather(it, locale) }
            if (battery != null || weather != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (battery != null) {
                        Text(battery, color = Ink, fontSize = 16.sp, maxLines = 1)
                    }
                    if (battery != null && weather != null) {
                        Spacer(Modifier.width(20.dp))
                    }
                    if (weather != null) {
                        Text(
                            text = weather,
                            color = Ink,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.appsReady && state.favourites.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_favourites),
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            items(state.favourites, key = { it.component }) { app ->
                AppRow(
                    label = app.label,
                    since = if (state.prefs.showLastOpened) formatSince(app.lastUsedAt, nowMillis) else null,
                    fontSize = state.prefs.nameSize.favouriteSp(),
                    onClick = { onLaunch(app) },
                    onLongClick = { onUnpin(app.component) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSystemSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.device_settings),
                    tint = Ink,
                )
            }
            IconButton(onClick = onOpenLauncherSettings) {
                Text(
                    text = "...",
                    color = Ink,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AllAppsPage(
    state: HomeUiState,
    onQuery: (String) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onToggleFavourite: (LaunchableApp) -> Boolean,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val nowMillis = System.currentTimeMillis()
    val letters = railLetters(if (state.query.isBlank()) state.listedApps else emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.query) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        SearchField(
            query = state.query,
            onQuery = onQuery,
            onDone = { keyboard?.hide() },
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (state.appsReady && state.listedApps.isEmpty()) {
            Text(
                text = stringResource(R.string.no_matches),
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(start = 24.dp, end = 8.dp),
            ) {
                items(state.listedApps, key = { it.component }) { app ->
                    AppRow(
                        label = app.label,
                        since = if (state.prefs.showLastOpened) formatSince(app.lastUsedAt, nowMillis) else null,
                        fontSize = state.prefs.nameSize.drawerSp(),
                        onClick = { onLaunch(app) },
                        onLongClick = {
                            val pinned = onToggleFavourite(app)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val message = if (pinned) R.string.pinned else R.string.unpinned
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            if (state.query.isBlank() && letters.isNotEmpty()) {
                AlphabetRail(
                    letters = letters,
                    onJump = { key ->
                        val index = jumpIndex(state.listedApps, key)
                        scope.launch { listState.scrollToItem(index) }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(color = Ink, fontSize = 28.sp)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(Ink),
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            color = Muted,
                            fontSize = 28.sp,
                        )
                    }
                    inner()
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
            }
        },
    )
}

@Composable
private fun AppRow(
    label: String,
    since: String?,
    fontSize: TextUnit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Ink,
            fontSize = fontSize,
            lineHeight = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (since != null) {
            Spacer(Modifier.width(12.dp))
            Text(text = since, color = Ink, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AlphabetRail(
    letters: List<com.tepmex.heilauncher.domain.RailLetter>,
    onJump: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var last = -1
                    fun pick(y: Float) {
                        val index = railIndexAt(y, size.height.toFloat(), letters.size)
                        if (index != last) {
                            last = index
                            onJump(letters[index].key)
                        }
                    }
                    pick(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        pick(change.position.y)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter.key,
                        color = if (letter.enabled) Ink else Dim,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            fontSize = if (letter.key.length > 1) 9.sp else 10.sp,
                            lineHeight = 10.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(percent: Int, fraction: Double, description: String) {
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DitherBar(fraction, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$percent%",
            color = Ink,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun DitherBar(fraction: Double, modifier: Modifier = Modifier) {
    val progress = fraction.toFloat().coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val step = 3.dp.toPx()
        val cols = (size.width / step).toInt().coerceAtLeast(1)
        val rows = (size.height / step).toInt().coerceAtLeast(1)
        val filled = (cols * progress).toInt()
        val dot = step * 0.7f
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if ((x + y) % 2 != 0) continue
                val color = if (x < filled) Color(0xFFF2F2F2) else Color(0xFF3A3A3A)
                drawRect(color, topLeft = Offset(x * step, y * step), size = Size(dot, dot))
            }
        }
    }
}

private fun NameSize.favouriteSp(): TextUnit = when (this) {
    NameSize.S -> 28.sp
    NameSize.M -> 36.sp
    NameSize.L -> 44.sp
}

private fun NameSize.drawerSp(): TextUnit = when (this) {
    NameSize.S -> 22.sp
    NameSize.M -> 28.sp
    NameSize.L -> 34.sp
}

@Composable
private fun locale(): Locale {
    val configuration = LocalConfiguration.current
    return configuration.locales[0]
}
