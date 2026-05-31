package com.tepmex.ctxcalendar.ui.day

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.ctxcalendar.R
import com.tepmex.ctxcalendar.data.GalleryPhoto
import com.tepmex.ctxcalendar.util.PerformanceLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

enum class DayDetailPage(val titleRes: Int) {
    Photos(R.string.day_tab_photos),
    Chronology(R.string.day_tab_chronology),
    YoutubeSearch(R.string.day_tab_youtube_search),
    YoutubeWatch(R.string.day_tab_youtube_watch),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    date: LocalDate,
    photos: List<GalleryPhoto>,
    dayDetailViewModelFactory: DayDetailViewModelFactory,
    onBack: () -> Unit,
    onPhotoClick: (GalleryPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DayDetailViewModel = viewModel(factory = dayDetailViewModelFactory)
    val takeoutState by viewModel.takeoutState.collectAsStateWithLifecycle()

    LaunchedEffect(date) {
        viewModel.loadForDate(date)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, date) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadForDate(date)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val title = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault()))
    }
    val pages = DayDetailPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            PerformanceLog.log(
                "day pager settled: ${pages[pagerState.settledPage].name} (date=$date)",
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                pages.forEachIndexed { index, page ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                text = stringResource(page.titleRes),
                                maxLines = 1,
                            )
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { pageIndex ->
                val page = pages[pageIndex]
                val isSettledOnPage =
                    pagerState.settledPage == pageIndex && !pagerState.isScrollInProgress
                val isVisibleDuringSwipe = pagerState.currentPage == pageIndex

                when (page) {
                    DayDetailPage.Photos -> {
                        val keepPhotosDuringSwipe =
                            pagerState.isScrollInProgress && pagerState.settledPage == pageIndex
                        if (isSettledOnPage || keepPhotosDuringSwipe) {
                            DayPhotosTab(
                                photos = photos,
                                onPhotoClick = onPhotoClick,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    DayDetailPage.Chronology -> {
                        if (isSettledOnPage) {
                            val timeline = takeoutState.timeline
                            ChronologyMapTab(
                                isLoading = takeoutState.isLoading,
                                hasDatabase = takeoutState.hasDatabase,
                                track = timeline?.track.orEmpty(),
                                visits = timeline?.visits.orEmpty(),
                                activities = timeline?.activities.orEmpty(),
                                errorMessage = takeoutState.errorMessage,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (isVisibleDuringSwipe) {
                            PagerSwipePlaceholder()
                        }
                    }
                    DayDetailPage.YoutubeSearch -> {
                        if (isSettledOnPage) {
                            YoutubeSearchTab(
                                isLoading = takeoutState.isLoading,
                                hasDatabase = takeoutState.hasDatabase,
                                searches = takeoutState.timeline?.searches.orEmpty(),
                                errorMessage = takeoutState.errorMessage,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (isVisibleDuringSwipe) {
                            PagerSwipePlaceholder()
                        }
                    }
                    DayDetailPage.YoutubeWatch -> {
                        if (isSettledOnPage) {
                            YoutubeWatchTab(
                                isLoading = takeoutState.isLoading,
                                hasDatabase = takeoutState.hasDatabase,
                                watches = takeoutState.timeline?.watches.orEmpty(),
                                errorMessage = takeoutState.errorMessage,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (isVisibleDuringSwipe) {
                            PagerSwipePlaceholder()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerSwipePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
