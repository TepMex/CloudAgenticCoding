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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tepmex.myweek.R
import com.tepmex.myweek.domain.Block
import com.tepmex.myweek.domain.Task
import com.tepmex.myweek.domain.WeekPhase

private sealed interface Line {
    val key: String
    val type: String

    data class Header(val block: Block, val bottom: Boolean) : Line {
        override val key: String = "h-${block.id}"
        override val type: String = "header"
    }

    data class TaskLine(val colorKey: String, val task: Task, val bottom: Boolean) : Line {
        override val key: String = "t-${task.id}"
        override val type: String = "task"
    }

    data class Gap(val blockId: Long) : Line {
        override val key: String = "g-$blockId"
        override val type: String = "gap"
    }
}

@Composable
fun HomeScreen(
    state: HomeUi,
    onToggle: (Long, Boolean) -> Unit,
    onEditBlock: (Long) -> Unit,
    onAddTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = state as? HomeUi.Ready
    val blocks = ready?.sprint?.blocks.orEmpty()
    val rows = remember(blocks) { lines(blocks) }
    if (ready == null) {
        Spacer(modifier.fillMaxSize())
        return
    }
    val planning = ready.phase == WeekPhase.Planning
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header", contentType = "page") {
            WeekHeader(ready)
        }
        items(items = rows, key = { it.key }, contentType = { it.type }) { line ->
            when (line) {
                is Line.Gap -> Spacer(Modifier.height(12.dp))
                is Line.Header -> ChecklistHeader(
                    title = line.block.title,
                    colorKey = line.block.colorKey,
                    top = true,
                    bottom = line.bottom,
                    onAdd = if (planning) ({ onAddTask(line.block.id) }) else null,
                    onEdit = if (planning) ({ onEditBlock(line.block.id) }) else null,
                )
                is Line.TaskLine -> ChecklistTask(
                    emoji = line.task.emoji,
                    title = line.task.title,
                    done = line.task.done,
                    colorKey = line.colorKey,
                    bottom = line.bottom,
                    onToggle = { onToggle(line.task.id, !line.task.done) },
                    onEdit = if (planning) ({ onEditTask(line.task.id) }) else null,
                )
            }
        }
        if (rows.isEmpty() && ready.phase != WeekPhase.NoSprint) {
            item(key = "empty", contentType = "empty") {
                Text(
                    text = stringResource(
                        if (planning) R.string.empty_planning else R.string.empty_tracking,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekHeader(ready: HomeUi.Ready?) {
    val sprint = ready?.sprint
    val total = sprint?.blocks?.sumOf { it.tasks.size } ?: 0
    val done = sprint?.blocks?.sumOf { block -> block.tasks.count { it.done } } ?: 0
    val title = sprint?.let { weekRange(it.weekStart) } ?: stringResource(R.string.week_title)
    val status = when (ready?.phase) {
        WeekPhase.Planning -> stringResource(R.string.status_planning)
        WeekPhase.Tracking -> stringResource(R.string.status_tracking)
        WeekPhase.NoSprint, null -> stringResource(R.string.status_empty)
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (total > 0) {
            Text(
                text = stringResource(R.string.progress, done, total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (ready?.debug == true) {
            val banner = if (ready.usingOverride) {
                stringResource(R.string.debug_banner_override, todayLabel(ready.today))
            } else {
                stringResource(R.string.debug_banner_real)
            }
            Text(
                text = banner,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun lines(blocks: List<Block>): List<Line> {
    val rows = ArrayList<Line>(blocks.sumOf { it.tasks.size + 2 })
    blocks.forEachIndexed { index, block ->
        if (index > 0) rows += Line.Gap(block.id)
        rows += Line.Header(block, bottom = block.tasks.isEmpty())
        val lastId = block.tasks.lastOrNull()?.id
        block.tasks.forEach { task ->
            rows += Line.TaskLine(block.colorKey, task, bottom = task.id == lastId)
        }
    }
    return rows
}
