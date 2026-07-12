package com.tepmex.paircompelo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.paircompelo.R
import com.tepmex.paircompelo.data.repository.ListSummary
import com.tepmex.paircompelo.ui.theme.StarHighlight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenList: (String) -> Unit,
    onCreateList: () -> Unit,
    onCompareItems: (String) -> Unit,
    onItemRanking: (String) -> Unit,
    onListRanking: () -> Unit,
    onCompareLists: () -> Unit,
    onSettings: () -> Unit,
    onArchived: () -> Unit,
    onHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val summaries by viewModel.richSummaries.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = { /* search planned */ }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    HomeOverflowMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onCreateList = onCreateList,
                        onListRanking = onListRanking,
                        onCompareLists = onCompareLists,
                        onHistory = onHistory,
                        onArchived = onArchived,
                        onSettings = onSettings,
                        showCreateList = summaries.isNotEmpty(),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (summaries.isEmpty()) {
            HomeEmptyState(
                onCreateList = onCreateList,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.active_lists).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(summaries, key = { it.list.id }) { summary ->
                ListSummaryCard(
                    summary = summary,
                    onOpen = { onOpenList(summary.list.id.toString()) },
                    onCompare = { onCompareItems(summary.list.id.toString()) },
                    onRating = { onItemRanking(summary.list.id.toString()) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HomeOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCreateList: () -> Unit,
    onListRanking: () -> Unit,
    onCompareLists: () -> Unit,
    onHistory: () -> Unit,
    onArchived: () -> Unit,
    onSettings: () -> Unit,
    showCreateList: Boolean,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (showCreateList) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_list)) },
                onClick = {
                    onDismiss()
                    onCreateList()
                },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.list_rankings)) },
            onClick = {
                onDismiss()
                onListRanking()
            },
            leadingIcon = { Icon(Icons.Default.Leaderboard, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.compare_lists)) },
            onClick = {
                onDismiss()
                onCompareLists()
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.comparison_history)) },
            onClick = {
                onDismiss()
                onHistory()
            },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.archived_lists)) },
            onClick = {
                onDismiss()
                onArchived()
            },
            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings)) },
            onClick = {
                onDismiss()
                onSettings()
            },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
        )
    }
}

@Composable
private fun HomeEmptyState(
    onCreateList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .padding(bottom = 24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
            Text(
                text = stringResource(R.string.empty_list_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.empty_list_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Button(
            onClick = onCreateList,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .semantics { contentDescription = "Add list" },
        ) {
            Text(stringResource(R.string.new_list))
        }
    }
}

@Composable
private fun ListSummaryCard(
    summary: ListSummary,
    onOpen: () -> Unit,
    onCompare: () -> Unit,
    onRating: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "List ${summary.list.name}" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = listIconFor(summary.list.name),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(top = 4.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.list.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.items_and_comparisons,
                            summary.activeItemCount,
                            summary.itemComparisonCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (summary.topItemName != null) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = StarHighlight,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = buildString {
                                    append(summary.topItemName)
                                    summary.topItemRating?.let { rating ->
                                        append(" · ")
                                        append("%.0f".format(rating))
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.0f".format(summary.list.rating),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.elo_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (summary.activeItemCount >= 2) {
                    Button(
                        onClick = onCompare,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.compare))
                    }
                } else {
                    Button(
                        onClick = onOpen,
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.compare))
                    }
                }
                OutlinedButton(
                    onClick = onRating,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.rating))
                }
            }
        }
    }
}

private fun listIconFor(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("book") || lower.contains("книг") || lower.contains("read") ->
            Icons.AutoMirrored.Filled.MenuBook
        lower.contains("film") || lower.contains("movie") || lower.contains("фильм") ||
            lower.contains("cinema") ->
            Icons.Default.Movie
        lower.contains("restaurant") || lower.contains("food") || lower.contains("ресторан") ||
            lower.contains("eat") ->
            Icons.Default.Restaurant
        else -> Icons.AutoMirrored.Outlined.FormatListBulleted
    }
}
