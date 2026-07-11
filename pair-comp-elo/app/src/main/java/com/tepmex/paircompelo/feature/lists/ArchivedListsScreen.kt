package com.tepmex.paircompelo.feature.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.model.PreferenceList
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArchivedListsViewModel @Inject constructor(
    repository: PreferenceRepository,
) : ViewModel() {
    val lists: StateFlow<List<PreferenceList>> = repository.observeArchivedLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ArchivedListsScreen(
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    viewModel: ArchivedListsViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    AppScaffold(title = "Archived lists", onBack = onBack) { padding ->
        if (lists.isEmpty()) {
            EmptyState(
                title = "Nothing archived",
                message = "Archived lists stay out of new comparisons until you restore them.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(lists, key = { it.id }) { list ->
                    ListItem(
                        headlineContent = { Text(list.name) },
                        supportingContent = { Text(list.description.orEmpty()) },
                        modifier = Modifier.clickable { onOpenList(list.id.toString()) },
                    )
                }
            }
        }
    }
}
