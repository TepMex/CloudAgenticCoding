package com.tepmex.zuotasks.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.zuotasks.data.ZuoTasksRepository
import com.tepmex.zuotasks.domain.TreeNodeItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class DialogState(
    val stack: List<Long?>,
    val hidden: Boolean,
    val pendingHide: Long?,
    val addProject: Boolean,
    val addTask: Boolean,
)

data class ProjectsUiState(
    val navigationStack: List<Long?> = listOf(null),
    val showHidden: Boolean = false,
    val items: List<TreeNodeItem> = emptyList(),
    val currentTitle: String = "Projects",
    val pendingHideProjectId: Long? = null,
    val showAddProjectDialog: Boolean = false,
    val showAddTaskDialog: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModel(
    private val repository: ZuoTasksRepository,
) : ViewModel() {

    private val navigationStack = MutableStateFlow(listOf<Long?>(null))
    private val showHidden = MutableStateFlow(false)
    private val pendingHideProjectId = MutableStateFlow<Long?>(null)
    private val showAddProjectDialog = MutableStateFlow(false)
    private val showAddTaskDialog = MutableStateFlow(false)

    private val listContent = combine(navigationStack, showHidden) { stack, hidden ->
        stack.lastOrNull() to hidden
    }.flatMapLatest { (parentId, hidden) ->
        combine(
            repository.observeProjectChildren(parentId, hidden),
            if (parentId == null) {
                kotlinx.coroutines.flow.flowOf("Projects")
            } else {
                repository.observeProject(parentId).flatMapLatest { project ->
                    kotlinx.coroutines.flow.flowOf(project?.name ?: "Project")
                }
            },
        ) { children, title -> children to title }
    }

    private val dialogState = combine(
        navigationStack,
        showHidden,
        pendingHideProjectId,
        showAddProjectDialog,
        showAddTaskDialog,
    ) { stack, hidden, pendingHide, addProject, addTask ->
        DialogState(stack, hidden, pendingHide, addProject, addTask)
    }

    val uiState: StateFlow<ProjectsUiState> = combine(dialogState, listContent) { dialog, content ->
        val (children, title) = content
        ProjectsUiState(
            navigationStack = dialog.stack,
            showHidden = dialog.hidden,
            items = children,
            currentTitle = title,
            pendingHideProjectId = dialog.pendingHide,
            showAddProjectDialog = dialog.addProject,
            showAddTaskDialog = dialog.addTask,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectsUiState(),
    )

    fun toggleShowHidden() {
        showHidden.update { !it }
    }

    fun openAddProjectDialog() {
        showAddProjectDialog.value = true
    }

    fun openAddTaskDialog() {
        showAddTaskDialog.value = true
    }

    fun dismissDialogs() {
        showAddProjectDialog.value = false
        showAddTaskDialog.value = false
    }

    fun addProject(name: String) {
        viewModelScope.launch {
            repository.addProject(navigationStack.value.lastOrNull(), name)
            showAddProjectDialog.value = false
        }
    }

    fun addTask(name: String) {
        viewModelScope.launch {
            repository.addTask(navigationStack.value.lastOrNull(), name)
            showAddTaskDialog.value = false
        }
    }

    fun toggleTask(taskId: Long) {
        viewModelScope.launch {
            repository.toggleTask(taskId)
        }
    }

    fun enterProject(projectId: Long) {
        navigationStack.update { it + projectId }
    }

    fun navigateUp(): Boolean {
        if (navigationStack.value.size <= 1) return false
        navigationStack.update { it.dropLast(1) }
        return true
    }

    fun requestHideProject(projectId: Long) {
        pendingHideProjectId.value = projectId
    }

    fun confirmHideProject() {
        val id = pendingHideProjectId.value ?: return
        viewModelScope.launch {
            repository.hideProject(id)
            pendingHideProjectId.value = null
        }
    }

    fun cancelHideProject() {
        pendingHideProjectId.value = null
    }
}
