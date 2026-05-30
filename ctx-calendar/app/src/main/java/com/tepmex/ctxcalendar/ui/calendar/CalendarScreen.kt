package com.tepmex.ctxcalendar.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.ctxcalendar.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    val monthTitle = uiState.currentMonth.format(
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()),
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(monthTitle) },
                navigationIcon = {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.previous_month),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.next_month),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            !uiState.hasMediaPermission -> {
                PermissionPrompt(
                    onRequest = {
                        permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            uiState.isLoading && uiState.photosByDay.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.loading_photos),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (uiState.loadError != null) {
                        Text(
                            text = uiState.loadError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }

                    CalendarMonthGrid(
                        yearMonth = uiState.currentMonth,
                        photosByDay = uiState.photosByDay,
                        onDayClick = onDayClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .monthSwipeNavigation(
                                onSwipeToPreviousMonth = viewModel::previousMonth,
                                onSwipeToNextMonth = viewModel::nextMonth,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRequest,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.grant_photos_access))
            }
        }
    }
}
