package dev.wmos.ui.screens.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.KnowledgeItemEntity
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.formatDateTime
import dev.wmos.ui.formatFileSize
import dev.wmos.ui.openPrivateFile
import dev.wmos.ui.titleOrFallback
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FilesViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<KnowledgeItemEntity>> = container.attachments.observeItemsWithAttachments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun displayName(path: String): String = container.attachments.displayName(path)

    fun sizeOf(path: String): Long = container.attachments.sizeOf(path)

    fun uriFor(path: String) = container.attachments.uriFor(path)

    fun detach(itemId: String) {
        viewModelScope.launch { container.attachments.detach(itemId) }
    }

    fun openPath(path: String): Boolean = container.attachments.resolve(path) != null

    companion object {
        val Factory = viewModelFactory { initializer { FilesViewModel(container()) } }
    }
}

@Composable
fun FilesScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    viewModel: FilesViewModel = viewModel(factory = FilesViewModel.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<KnowledgeItemEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    deleting?.let { item ->
        val path = item.attachmentUri.orEmpty()
        ConfirmDialog(
            title = "Удалить вложение?",
            message = "Копия файла «${viewModel.displayName(path)}» будет удалена из приватного хранилища. " +
                "Оригинал на устройстве останется.",
            confirmLabel = "Удалить вложение",
            destructive = true,
            onConfirm = {
                viewModel.detach(item.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    error?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Файл недоступен") },
            text = { Text(message) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { error = null }) { Text("Понятно") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                title = { Text("WM Files") },
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
            item {
                Text(
                    "Здесь только файлы, которые вы сами прикрепили к записям. Они скопированы в приватное " +
                        "хранилище приложения: оригиналы не изменяются и не удаляются. Приложение не сканирует " +
                        "память устройства и не запускает файлы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = "Вложений нет",
                        message = "Откройте заметку → меню → «Прикрепить файл». Поддерживаются выбранные вами " +
                            "документы, изображения и PDF.",
                    )
                }
            }

            items(items, key = { it.id }) { item ->
                val path = item.attachmentUri.orEmpty()
                val name = viewModel.displayName(path)
                WmCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onClick = { onOpenNote(item.id) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = iconFor(item.mimeType),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${formatFileSize(viewModel.sizeOf(path))} · ${item.mimeType ?: "тип неизвестен"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "В записи «${titleOrFallback(item.title, item.content)}» · ${formatDateTime(item.updatedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = {
                                val uri = viewModel.uriFor(path)
                                if (uri == null) {
                                    error = "Копия файла не найдена — возможно, она была удалена"
                                } else if (!openPrivateFile(context, uri, item.mimeType)) {
                                    error = "Нет приложения, которое открывает файлы этого типа"
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Description, contentDescription = "Открыть файл")
                        }
                        IconButton(onClick = { deleting = item }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить вложение")
                        }
                    }
                }
            }
        }
    }
}

private fun iconFor(mimeType: String?) = when {
    mimeType == null -> Icons.Outlined.Description
    mimeType.startsWith("image/") -> Icons.Outlined.Image
    mimeType == "application/pdf" -> Icons.Outlined.PictureAsPdf
    else -> Icons.Outlined.Description
}
