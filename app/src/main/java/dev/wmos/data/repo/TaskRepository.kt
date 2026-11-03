package dev.wmos.data.repo

import dev.wmos.data.db.WmDatabase
import dev.wmos.data.db.entity.ProjectEntity
import dev.wmos.data.db.entity.TaskEntity
import dev.wmos.data.db.entity.TaskPriorityValues
import dev.wmos.data.db.entity.TaskStatusValues
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Task and project use cases. Due dates are epoch millis; the UI decides how to show them. */
class TaskRepository(private val db: WmDatabase) {
    private val taskDao = db.taskDao()
    private val projectDao = db.projectDao()

    fun observeAll(): Flow<List<TaskEntity>> = taskDao.observeAll()

    fun observeOpen(): Flow<List<TaskEntity>> = taskDao.observeOpen()

    fun observeClosed(): Flow<List<TaskEntity>> = taskDao.observeClosed()

    fun observeByProject(projectId: String): Flow<List<TaskEntity>> = taskDao.observeByProject(projectId)

    fun observeTask(id: String): Flow<TaskEntity?> = taskDao.observeById(id)

    fun observeOpenCount(): Flow<Int> = taskDao.observeOpenCount()

    fun observeOverdueCount(now: Long): Flow<Int> = taskDao.observeOverdueCount(now)

    suspend fun task(id: String): TaskEntity? = taskDao.getById(id)

    suspend fun createTask(
        title: String,
        description: String = "",
        dueAt: Long? = null,
        priority: String = TaskPriorityValues.NORMAL,
        status: String = TaskStatusValues.INBOX,
        projectId: String? = null,
    ): Result<String> {
        val clean = title.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Название задачи не может быть пустым"))
        if (clean.length > 300) return Result.failure(IllegalArgumentException("Название слишком длинное"))
        if (projectId != null && projectDao.getById(projectId) == null) {
            return Result.failure(IllegalArgumentException("Проект не найден"))
        }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        taskDao.upsert(
            TaskEntity(
                id = id,
                title = clean,
                description = description.trim(),
                status = status,
                priority = priority,
                dueAt = dueAt,
                projectId = projectId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return Result.success(id)
    }

    suspend fun updateTask(task: TaskEntity): Result<Unit> {
        if (task.title.isBlank()) return Result.failure(IllegalArgumentException("Название не может быть пустым"))
        taskDao.upsert(task.copy(updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    suspend fun setStatus(id: String, status: String) {
        val now = System.currentTimeMillis()
        val completedAt = if (status == TaskStatusValues.DONE) now else null
        taskDao.setStatus(id, status, completedAt, now)
    }

    suspend fun toggleDone(id: String) {
        val existing = taskDao.getById(id) ?: return
        val newStatus = if (existing.status == TaskStatusValues.DONE) TaskStatusValues.NEXT else TaskStatusValues.DONE
        setStatus(id, newStatus)
    }

    suspend fun delete(id: String) = taskDao.softDelete(id, System.currentTimeMillis())

    // ---------------------------------------------------------------- projects

    fun observeProjects(): Flow<List<ProjectEntity>> = projectDao.observeActive()

    fun observeProject(id: String): Flow<ProjectEntity?> = projectDao.observeById(id)

    fun observeProjectOpenTasks(projectId: String): Flow<Int> = projectDao.observeOpenTaskCount(projectId)

    suspend fun project(id: String): ProjectEntity? = projectDao.getById(id)

    suspend fun createProject(
        name: String,
        description: String = "",
        goal: String = "",
        dueAt: Long? = null,
    ): Result<String> {
        val clean = name.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Название проекта не может быть пустым"))
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        projectDao.upsert(
            ProjectEntity(
                id = id,
                name = clean,
                description = description.trim(),
                goal = goal.trim(),
                dueAt = dueAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return Result.success(id)
    }

    suspend fun updateProject(project: ProjectEntity): Result<Unit> {
        if (project.name.isBlank()) return Result.failure(IllegalArgumentException("Название не может быть пустым"))
        projectDao.upsert(project.copy(updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    /** Deleting a project keeps its tasks; they simply lose the project link. */
    suspend fun deleteProjectKeepingTasks(id: String): Result<Unit> {
        taskDao.clearProject(id, System.currentTimeMillis())
        projectDao.delete(id)
        return Result.success(Unit)
    }
}
