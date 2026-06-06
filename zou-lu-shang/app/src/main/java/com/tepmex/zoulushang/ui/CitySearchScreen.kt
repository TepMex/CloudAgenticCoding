package com.tepmex.zoulushang.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySearchScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Choose city") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setShowCityPicker(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.citySearchQuery,
                onValueChange = viewModel::updateCitySearchQuery,
                label = { Text("Search city") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSavingCity,
            )

            if (uiState.isSearching) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }

            if (uiState.isSavingCity) {
                Text(
                    text = "Saving city boundary…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (uiState.cities.isNotEmpty()) {
                    item(key = "saved-header") {
                        Text(
                            text = "Saved cities",
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(uiState.cities, key = { "saved-${it.id}" }) { city ->
                        ListItem(
                            headlineContent = { Text(city.displayName) },
                            onClick = { viewModel.selectCity(city) },
                            enabled = !uiState.isSavingCity,
                        )
                    }
                }

                if (uiState.citySearchResults.isNotEmpty()) {
                    item(key = "search-header") {
                        Text(
                            text = "Search results",
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(uiState.citySearchResults, key = { "search-${it.placeId}" }) { result ->
                        ListItem(
                            headlineContent = { Text(result.displayName) },
                            onClick = { viewModel.addCityFromSearch(result) },
                            enabled = !uiState.isSavingCity,
                        )
                    }
                }
            }
        }
    }
}
