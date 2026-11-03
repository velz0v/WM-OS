package dev.wmos.ui.screens.brain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.FolderEntity
import dev.wmos.data.db.entity.KnowledgeItemEntity
import dev.wmos.data.db.entity.KnowledgeTypeValues
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.SectionHeader
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.formatDateTime
import dev.wmos.ui.pluralizeRu
import dev.wmos.ui.snippet
import dev.wmos.ui.titleOrFallback
import dev.wmos.ui.theme.LocalWmColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrainFilter(
    val query: String = "",
    val type: String = "",
    val favoriteOnly: Boolean = false,
    val folderId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BrainViewModel(private val container: AppContainer) : androidx.lifecycle.ViewModel() {
    private val filterState = MutableStateFlow(BrainFilter())

    val filter: StateFlow<BrainFilter> = filterState

    val folders: StateFlow<List<FolderEntity>> = container.brain.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folderCounts: StateFlow<Map<String?, Int>> = container.brain.observeFolderCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val items: StateFlow<List<KnowledgeItemEntity>> = filterState
        .flatMapLatest { f ->
            container.brain.observeItems(
                folderId = f.folderId,
                favoriteOnly = f.favoriteOnly,
                typeFilter = f.type,
                query = f.query,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loading: StateFlow<Boolean> = MutableStateFlow(false)

    fun setQuery(query: String) {
        filterState.value = filterState.value.copy(query = query)
    }

    fun setType(type: String) {
        filterState.value = filterState.value.copy(type = type)
    }

    fun toggleFavoriteOnly() {
        filterState.value = filterState.value.copy(favoriteOnly = !filterState.value.favoriteOnly)
    }

    fun createNote(type: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = container.brain.createItem(type = type)
            onCreated(id)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch { container.brain.createFolder(name) }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch { container.brain.moveToTrash(id) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { container.brain.toggleFavorite(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { BrainViewModel(container()) }
        }
    }
}

@Composable
fun BrainScreen(
    onOpenFolder: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: BrainViewModel = viewModel(factory = BrainViewModel.Factory),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val counts by viewModel.folderCounts.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var createMenuOpen by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<KnowledgeItemEntity?>(null) }

    if (folderDialog) {
        NewFolderDialog(
            onDismiss = { folderDialog = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                folderDialog = false
            },
        )
    }

    confirmDelete?.let { item ->
        dev.wmos.ui.components.ConfirmDialog(
            title = "Удалить в корзину?",
            message = "«${titleOrFallback(item.title, item.content)}» можно будет восстановить из корзины.",
            confirmLabel = "Удалить в корзину",
            onConfirm = {
                viewModel.deleteItem(item.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Знания", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Second Brain",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Глобальный поиск")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё действия")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Корзина") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenTrash()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Новая папка") },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                folderDialog = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { createMenuOpen = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Создать") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = LocalWmColors.current.onAccent,
                )
                DropdownMenu(expanded = createMenuOpen, onDismissRequest = { createMenuOpen = false }) {
                    CreateMenuItem("Заметка", Icons.Outlined.StickyNote2) {
                        createMenuOpen = false
                        viewModel.createNote(KnowledgeTypeValues.NOTE, onOpenItem)
                    }
                    CreateMenuItem("Памятка", Icons.Outlined.Lightbulb) {
                        createMenuOpen = false
                        viewModel.createNote(KnowledgeTypeValues.MEMO, onOpenItem)
                    }
                    CreateMenuItem("Ссылка", Icons.Outlined.Link) {
                        createMenuOpen = false
                        viewModel.createNote(KnowledgeTypeValues.LINK, onOpenItem)
                    }
                    CreateMenuItem("Папка", Icons.Outlined.FolderOpen) {
                        createMenuOpen = false
                        folderDialog = true
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Поиск по заметкам и памяткам") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.large,
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filter.type == "",
                            onClick = { viewModel.setType("") },
                            label = { Text("Все") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.type == KnowledgeTypeValues.NOTE,
                            onClick = { viewModel.setType(KnowledgeTypeValues.NOTE) },
                            label = { Text("Заметки") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.type == KnowledgeTypeValues.MEMO,
                            onClick = { viewModel.setType(KnowledgeTypeValues.MEMO) },
                            label = { Text("Памятки") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.type == KnowledgeTypeValues.LINK,
                            onClick = { viewModel.setType(KnowledgeTypeValues.LINK) },
                            label = { Text("Ссылки") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.favoriteOnly,
                            onClick = { viewModel.toggleFavoriteOnly() },
                            label = { Text("Избранное") },
                            leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            if (folders.isNotEmpty() && filter.query.isBlank() && filter.type == "" && !filter.favoriteOnly) {
                item { SectionHeader("Папки", subtitle = pluralizeRu(folders.size, "папка", "папки", "папок")) }
                items(folders, key = { "folder-${it.id}" }) { folder ->
                    FolderRow(
                        folder = folder,
                        count = counts[folder.id] ?: 0,
                        onClick = { onOpenFolder(folder.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                    )
                }
            }

            item {
                SectionHeader(
                    title = if (filter.query.isBlank()) "Материалы" else "Результаты поиска",
                    subtitle = if (items.isEmpty()) null else pluralizeRu(items.size, "запись", "записи", "записей"),
                )
            }

            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = if (filter.query.isBlank()) Icons.Outlined.Description else Icons.Outlined.Search,
                        title = if (filter.query.isBlank()) "Здесь пока пусто" else "Ничего не найдено",
                        message = if (filter.query.isBlank()) {
                            "Запишите мысль, сохраните ссылку или создайте памятку — всё хранится локально."
                        } else {
                            "Попробуйте другой запрос или снимите фильтры."
                        },
                        actionLabel = if (filter.query.isBlank()) "Создать заметку" else null,
                        onAction = if (filter.query.isBlank()) {
                            { viewModel.createNote(KnowledgeTypeValues.NOTE, onOpenItem) }
                        } else {
                            null
                        },
                    )
                }
            } else {
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onClick = { onOpenItem(item.id) },
                        onFavorite = { viewModel.toggleFavorite(item.id) },
                        onDelete = { confirmDelete = item },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun FolderRow(
    folder: FolderEntity,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WmCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (folder.isPinned) Icons.Outlined.Star else Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (folder.description.isNotBlank()) {
                    Text(
                        folder.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ItemRow(
    item: KnowledgeItemEntity,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WmCard(
        modifier = modifier.let { if (it == Modifier) Modifier.padding(horizontal = 16.dp, vertical = 4.dp) else it },
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = itemIcon(item.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = titleOrFallback(item.title, item.content),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.content.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = snippet(item.content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatDateTime(item.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavorite) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = if (item.isFavorite) "Убрать из избранного" else "В избранное",
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "В корзину",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

fun itemIcon(type: String): ImageVector = when (type) {
    KnowledgeTypeValues.MEMO -> Icons.Outlined.Lightbulb
    KnowledgeTypeValues.LINK -> Icons.Outlined.Link
    KnowledgeTypeValues.FILE -> Icons.Outlined.Description
    else -> Icons.Outlined.StickyNote2
}

@Composable
fun NewFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая папка", style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
                isError = name.length > 120,
                supportingText = if (name.length > 120) {
                    { Text("Слишком длинное название") }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && name.length <= 120,
            ) { Text("Создать") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
