package dev.wmos.ui.screens.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.ActionLogEntity
import dev.wmos.data.db.entity.FolderEntity
import dev.wmos.data.db.entity.KnowledgeItemEntity
import dev.wmos.data.db.entity.ProjectEntity
import dev.wmos.data.db.entity.TaskEntity
import dev.wmos.data.db.entity.TaskStatusValues
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.SectionHeader
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.components.WmListRow
import dev.wmos.ui.container
import dev.wmos.ui.dueLabel
import dev.wmos.ui.formatDateTime
import dev.wmos.ui.screens.brain.ItemRow
import dev.wmos.ui.screens.brain.NewFolderDialog
import dev.wmos.ui.theme.LocalWmColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ folder

@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModel(private val container: AppContainer, private val folderId: String) : ViewModel() {
    private val reload = MutableStateFlow(0)

    val folder: StateFlow<FolderEntity?> = reload.flatMapLatest { container.brain.observeFolder(folderId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<KnowledgeItemEntity>> = container.brain.observeItems(folderId = folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subfolders: StateFlow<List<FolderEntity>> = container.brain.observeAllFolders()
        .map { all -> all.filter { it.parentId == folderId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createSubfolder(name: String) {
        viewModelScope.launch { container.brain.createFolder(name, parentId = folderId) }
    }

    fun rename(name: String) {
        viewModelScope.launch { container.brain.renameFolder(folderId, name) }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch { container.brain.moveToTrash(id) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { container.brain.toggleFavorite(id) }
    }

    companion object {
        fun factory(folderId: String) = viewModelFactory {
            initializer { FolderViewModel(container(), folderId) }
        }
    }
}

@Composable
fun FolderScreen(
    folderId: String,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: FolderViewModel = viewModel(factory = FolderViewModel.factory(folderId)),
) {
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val subfolders by viewModel.subfolders.collectAsStateWithLifecycle()
    var newFolder by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<KnowledgeItemEntity?>(null) }

    if (newFolder) {
        NewFolderDialog(
            onDismiss = { newFolder = false },
            onConfirm = { name ->
                viewModel.createSubfolder(name)
                newFolder = false
            },
        )
    }

    deleting?.let { item ->
        ConfirmDialog(
            title = "В корзину?",
            message = "Запись можно будет восстановить.",
            confirmLabel = "В корзину",
            onConfirm = {
                viewModel.deleteItem(item.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text(folder?.name ?: "Папка") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newFolder = true },
                icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                text = { Text("Подпапка") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = LocalWmColors.current.onAccent,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            folder?.description?.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            if (subfolders.isNotEmpty()) {
                item { SectionHeader("Подпапки") }
                items(subfolders, key = { "sub-${it.id}" }) { sub ->
                    WmListRow(
                        title = sub.name,
                        subtitle = sub.description.ifBlank { null },
                        leading = Icons.Outlined.FolderOpen,
                        onClick = { onOpenFolder(sub.id) },
                    )
                }
            }
            item { SectionHeader("Материалы") }
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = "Папка пуста",
                        message = "Переместите сюда записи из общего списка или создайте новую заметку.",
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                ItemRow(
                    item = item,
                    onClick = { onOpenItem(item.id) },
                    onFavorite = { viewModel.toggleFavorite(item.id) },
                    onDelete = { deleting = item },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                )
            }
        }
    }
}

// ----------------------------------------------------------------- project

class ProjectViewModel(private val container: AppContainer, private val projectId: String) : ViewModel() {
    val project: StateFlow<ProjectEntity?> = container.tasks.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tasks: StateFlow<List<TaskEntity>> = container.tasks.observeByProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(id: String) {
        viewModelScope.launch { container.tasks.toggleDone(id) }
    }

    companion object {
        fun factory(projectId: String) = viewModelFactory {
            initializer { ProjectViewModel(container(), projectId) }
        }
    }
}

@Composable
fun ProjectScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: ProjectViewModel = viewModel(factory = ProjectViewModel.factory(projectId)),
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val done = tasks.count { it.status == TaskStatusValues.DONE }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text(project?.name ?: "Проект") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            project?.let { p ->
                item {
                    WmCard(modifier = Modifier.padding(16.dp)) {
                        if (p.goal.isNotBlank()) {
                            Text("Цель", style = MaterialTheme.typography.labelMedium)
                            Text(p.goal, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            "Задач: ${tasks.size}, выполнено: $done",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (tasks.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { done.toFloat() / tasks.size },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Задачи проекта") }
            if (tasks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.TaskAlt,
                        title = "Задач нет",
                        message = "Добавьте задачи на экране «План» и выберите этот проект.",
                    )
                }
            }
            items(tasks, key = { it.id }) { task ->
                WmRowForTask(task) { viewModel.toggle(task.id) }
            }
        }
    }
}

@Composable
private fun WmRowForTask(task: TaskEntity, onToggle: () -> Unit) {
    WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle) {
                Icon(Icons.Outlined.TaskAlt, contentDescription = "Переключить статус")
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                task.dueAt?.let {
                    Text(
                        dueLabel(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(task.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------- trash

class TrashViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<KnowledgeItemEntity>> = container.brain.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: String) {
        viewModelScope.launch { container.brain.restoreFromTrash(id) }
    }

    fun deleteForever(id: String) {
        viewModelScope.launch { container.brain.deleteForever(id) }
    }

    fun empty() {
        viewModelScope.launch { container.brain.emptyTrash() }
    }

    companion object {
        val Factory = viewModelFactory { initializer { TrashViewModel(container()) } }
    }
}

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = viewModel(factory = TrashViewModel.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<KnowledgeItemEntity?>(null) }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Очистить корзину?",
            message = "Будет удалено записей: ${items.size}. Вложения, созданные из этих записей, тоже исчезнут. " +
                "Восстановить их будет нельзя.",
            confirmLabel = "Удалить навсегда",
            destructive = true,
            onConfirm = {
                viewModel.empty()
                confirmEmpty = false
            },
            onDismiss = { confirmEmpty = false },
        )
    }

    confirmDelete?.let { item ->
        ConfirmDialog(
            title = "Удалить навсегда?",
            message = "«${item.title.ifBlank { item.content.take(40) }}» нельзя будет восстановить.",
            confirmLabel = "Удалить навсегда",
            destructive = true,
            onConfirm = {
                viewModel.deleteForever(item.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("Корзина") },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirmEmpty = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Очистить корзину")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Delete,
                        title = "Корзина пуста",
                        message = "Сюда попадают удалённые записи. Очистка освобождает место.",
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title.ifBlank { item.content.take(60) }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                formatDateTime(item.deletedAt ?: item.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.restore(item.id) }) {
                            Icon(Icons.Outlined.Restore, contentDescription = "Восстановить")
                        }
                        IconButton(onClick = { confirmDelete = item }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить навсегда")
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------- action log

class ActionLogViewModel(container: AppContainer) : ViewModel() {
    val entries: StateFlow<List<ActionLogEntity>> = container.actionLog.observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = viewModelFactory { initializer { ActionLogViewModel(container()) } }
    }
}

@Composable
fun ActionLogScreen(
    onBack: () -> Unit,
    viewModel: ActionLogViewModel = viewModel(factory = ActionLogViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("Журнал действий") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.TaskAlt,
                        title = "Журнал пуст",
                        message = "Здесь появятся действия Грауса: что он искал, что создал и с каким результатом.",
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        "${entry.tool} · ${entry.status}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatDateTime(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
