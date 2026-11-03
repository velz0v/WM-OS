package dev.wmos.ui.screens.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.ProjectEntity
import dev.wmos.data.db.entity.TaskEntity
import dev.wmos.data.db.entity.TaskPriorityValues
import dev.wmos.data.db.entity.TaskStatusValues
import dev.wmos.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TaskFormState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val status: String = TaskStatusValues.NEXT,
    val priority: String = TaskPriorityValues.NORMAL,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime = LocalTime.of(18, 0),
    val projectId: String? = null,
    val projectName: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val original: TaskEntity? = null,
)

class TaskDetailViewModel(
    private val container: AppContainer,
    private val taskId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(TaskFormState(loading = taskId != null))
    val state: StateFlow<TaskFormState> = _state

    val projects: StateFlow<List<ProjectEntity>> = container.tasks.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (taskId == null) {
            _state.value = TaskFormState(loading = false, status = TaskStatusValues.INBOX)
        } else {
            viewModelScope.launch { load() }
        }
    }

    private suspend fun load() {
        val task = container.tasks.task(taskId!!)
        if (task == null) {
            _state.value = TaskFormState(loading = false, missing = true)
            return
        }
        val zone = ZoneId.systemDefault()
        val due = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val project = task.projectId?.let { container.tasks.project(it)?.name }
        _state.value = TaskFormState(
            loading = false,
            title = task.title,
            description = task.description,
            status = task.status,
            priority = task.priority,
            dueDate = due?.toLocalDate(),
            dueTime = due?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(18, 0),
            projectId = task.projectId,
            projectName = project,
            original = task,
        )
    }

    fun setTitle(value: String) {
        _state.value = _state.value.copy(title = value, error = null)
    }

    fun setDescription(value: String) {
        _state.value = _state.value.copy(description = value)
    }

    fun setStatus(status: String) {
        _state.value = _state.value.copy(status = status)
    }

    fun setPriority(priority: String) {
        _state.value = _state.value.copy(priority = priority)
    }

    fun setDueDate(date: LocalDate?) {
        _state.value = _state.value.copy(dueDate = date)
    }

    fun setDueTime(time: LocalTime) {
        _state.value = _state.value.copy(dueTime = time)
    }

    fun setProject(id: String?, name: String?) {
        _state.value = _state.value.copy(projectId = id, projectName = name)
    }

    fun dueAtMillis(): Long? {
        val s = _state.value
        val date = s.dueDate ?: return null
        return date.atTime(s.dueTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val title = s.title.trim()
        if (title.isEmpty()) {
            _state.value = s.copy(error = "Название задачи обязательно")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            val existing = s.original
            val result: Result<Unit> = if (existing == null) {
                container.tasks.createTask(
                    title = title,
                    description = s.description,
                    dueAt = dueAtMillis(),
                    priority = s.priority,
                    status = s.status,
                    projectId = s.projectId,
                ).map { }
            } else {
                container.tasks.updateTask(
                    existing.copy(
                        title = title,
                        description = s.description,
                        status = s.status,
                        priority = s.priority,
                        dueAt = dueAtMillis(),
                        projectId = s.projectId,
                        completedAt = if (s.status == TaskStatusValues.DONE) {
                            existing.completedAt ?: System.currentTimeMillis()
                        } else {
                            null
                        },
                    ),
                )
            }
            result.onSuccess {
                _state.value = _state.value.copy(saving = false, saved = true)
                onSaved()
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    saving = false,
                    error = error.message ?: "Не удалось сохранить задачу",
                )
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _state.value.original?.id
        if (id == null) {
            onDeleted()
            return
        }
        viewModelScope.launch {
            container.tasks.delete(id)
            onDeleted()
        }
    }

    companion object {
        fun factory(taskId: String?) = viewModelFactory {
            initializer { TaskDetailViewModel(container(), taskId) }
        }
    }
}
