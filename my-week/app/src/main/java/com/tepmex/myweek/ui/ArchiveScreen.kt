package com.tepmex.myweek.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tepmex.myweek.R
import com.tepmex.myweek.domain.Sprint

@Composable
fun ArchiveScreen(
    state: ArchiveUi,
    modifier: Modifier = Modifier,
) {
    val sprints = (state as? ArchiveUi.Ready)?.sprints.orEmpty()
    if (state is ArchiveUi.Loading) {
        Spacer(modifier.fillMaxSize())
        return
    }
    if (sprints.isEmpty()) {
        Text(
            text = stringResource(R.string.archive_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(20.dp),
        )
        return
    }
    // Newest sprint is the first item and sits at the bottom. Older weeks are reached by scrolling up.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(bottom = 12.dp, top = 12.dp),
    ) {
        items(items = sprints.asReversed(), key = { it.id }, contentType = { "sprint" }) { sprint ->
            ArchivedSprint(sprint)
        }
    }
}

@Composable
private fun ArchivedSprint(sprint: Sprint) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = sprintDate(sprint.weekStart),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
                .semantics { heading() },
        )
        if (sprint.blocks.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_tracking),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        sprint.blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            ChecklistHeader(
                title = block.title,
                colorKey = block.colorKey,
                top = true,
                bottom = block.tasks.isEmpty(),
                onAdd = null,
                onEdit = null,
            )
            val lastId = block.tasks.lastOrNull()?.id
            block.tasks.forEach { task ->
                ChecklistTask(
                    emoji = task.emoji,
                    title = task.title,
                    done = task.done,
                    colorKey = block.colorKey,
                    bottom = task.id == lastId,
                    onToggle = null,
                    onEdit = null,
                )
            }
        }
    }
}
