package com.tepmex.paircompelo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.repository.ListSummary
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: PreferenceRepository,
) : ViewModel() {
    val richSummaries: StateFlow<List<ListSummary>> =
        repository.observeActiveLists()
            .mapLatest { repository.buildListSummaries() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
