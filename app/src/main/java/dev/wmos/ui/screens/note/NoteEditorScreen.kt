package dev.wmos.ui.screens.note

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wmos.WmApplication
import dev.wmos.data.db.entity.FolderEntity
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.LoadingState
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.theme.LocalWmColors

@Composable
fun NoteEditorScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.factory(itemId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as WmApplication
    val folders by app.container.brain.observeFolders().collectAsStateWithLifecycle(initialValue = emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDetach by remember { mutableStateOf(false) }
    var attachError by remember { mutableStateOf<String?>(null) }

    val attachLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                val mime = context.contentResolver.getType(it)
                viewModel.attachFile(it, mime)
            }
        },
    )

    if (confirmDetach) {
        ConfirmDialog(
            title = "Удалить вложение?",
            message = "Копия файла в приватном хранилище приложения будет удалена. " +
                "Оригинал, который вы выбирали, останется на месте.",
            confirmLabel = "Удалить вложение",
            destructive = true,
            onConfirm = {
                confirmDetach = false
                viewModel.detachFile()
            },
            onDismiss = { confirmDetach = false },
        )
    }

    attachError?.let { message ->
        AlertDialog(
            onDismissRequest = { attachError = null },
            title = { Text("Не удалось открыть файл") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { attachError = null }) { Text("Понятно") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (state.draftOffer != null) {
        AlertDialog(
            onDismissRequest = { viewModel.discardDraft() },
            title = { Text("Найден более свежий черновик") },
            text = {
                Text(
                    "В черновике сохранён текст, который не попал в базу. " +
                        "Восстановить его или оставить сохранённую версию?",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreDraft() }) { Text("Восстановить черновик") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.discardDraft() }) { Text("Оставить сохранённое") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (tagDialog) {
        TagDialog(
            onDismiss = { tagDialog = false },
            onAdd = { viewModel.addTag(it) },
        )
    }

    if (folderDialog) {
        FolderPickerDialog(
            folders = folders,
            currentFolderId = state.folderId,
            onDismiss = { folderDialog = false },
            onPick = { folder ->
                viewModel.setFolder(folder?.id, folder?.name)
                folderDialog = false
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Переместить в корзину?",
            message = "Заметку можно будет восстановить. Окончательное удаление — отдельным действием в корзине.",
            confirmLabel = "В корзину",
            onConfirm = {
                viewModel.deleteForeverShell()
                confirmDelete = false
                onBack()
            },
            onDismiss = { confirmDelete = false },
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
                title = { SaveStatus(state.saveState, state.errorMessage, onRetry = viewModel::retrySave) },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = "Избранное",
                            tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.isPinned) "Открепить" else "Закрепить") },
                            leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.togglePinned()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.isArchived) "Вернуть из архива" else "В архив") },
                            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.toggleArchived()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Папка") },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                folderDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.attachmentPath == null) "Прикрепить файл" else "Заменить файл") },
                            leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                attachLauncher.launch(
                                    arrayOf("application/pdf", "image/*", "text/plain", "application/octet-stream"),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Экспорт Markdown") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/markdown"
                                    putExtra(Intent.EXTRA_TEXT, viewModel.markdown())
                                    putExtra(Intent.EXTRA_SUBJECT, state.title.ifBlank { "Заметка WM OS" })
                                }
                                context.startActivity(Intent.createChooser(send, "Экспорт заметки"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                LoadingState()
            }

            state.missing -> EmptyState(
                icon = Icons.Outlined.Delete,
                title = "Заметка не найдена",
                message = "Возможно, она удалена навсегда.",
                actionLabel = "Назад",
                onAction = onBack,
                modifier = Modifier.padding(inner),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { folderDialog = true },
                        label = { Text(state.folderName ?: "Без папки") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    state.tags.take(3).forEach { tag ->
                        AssistChip(onClick = { viewModel.removeTag(tag) }, label = { Text("#$tag") })
                    }
                    TextButton(onClick = { tagDialog = true }) { Text("+ тег") }
                }

                state.attachmentName?.let { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(
                            onClick = {
                                val uri = viewModel.attachmentUri()
                                if (uri == null) {
                                    attachError = "Файл вложения не найден в приватном хранилище"
                                } else {
                                    val opened = dev.wmos.ui.openPrivateFile(context, uri, null)
                                    if (!opened) {
                                        attachError = "На устройстве нет приложения, которое открывает этот тип файла"
                                    }
                                }
                            },
                            label = { Text("📎 $name") },
                        )
                        Text(
                            text = dev.wmos.ui.formatFileSize(state.attachmentSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { confirmDetach = true }) { Text("Убрать") }
                    }
                }

                BasicTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    decorationBox = { inner ->
                        Box {
                            if (state.title.isEmpty()) {
                                Text(
                                    "Заголовок",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )

                BasicTextField(
                    value = state.content,
                    onValueChange = viewModel::onContentChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (state.content.isEmpty()) {
                                Text(
                                    "Начните писать. Сохранение происходит автоматически.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )

                if (state.saveState == SaveState.ERROR) {
                    ErrorStrip(state.errorMessage ?: "Ошибка сохранения", viewModel::retrySave)
                }
            }
        }
    }
}

@Composable
private fun SaveStatus(state: SaveState, error: String?, onRetry: () -> Unit) {
    val extras = LocalWmColors.current
    when (state) {
        SaveState.SAVED -> StatusPill(
            text = "Сохранено",
            color = extras.onSuccessContainer,
            background = extras.successContainer,
        )

        SaveState.SAVING -> StatusPill(
            text = "Сохраняется…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            background = MaterialTheme.colorScheme.surfaceVariant,
        )

        SaveState.DIRTY -> StatusPill(
            text = "Не сохранено",
            color = extras.onWarningContainer,
            background = extras.warningContainer,
        )

        SaveState.ERROR -> TextButton(onClick = onRetry) {
            Text("Не удалось сохранить · Повторить", color = extras.danger, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ErrorStrip(message: String, onRetry: () -> Unit) {
    val extras = LocalWmColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(extras.dangerContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = extras.onDangerContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Повторить", color = extras.onDangerContainer) }
    }
}

@Composable
private fun TagDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить тег") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Название тега") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(value)
                    onDismiss()
                },
                enabled = value.isNotBlank(),
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun FolderPickerDialog(
    folders: List<FolderEntity>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onPick: (FolderEntity?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите папку") },
        text = {
            LazyColumn(modifier = Modifier.height(280.dp)) {
                item {
                    TextButton(onClick = { onPick(null) }) {
                        Text(if (currentFolderId == null) "• Без папки" else "Без папки")
                    }
                }
                items(folders, key = { it.id }) { folder ->
                    TextButton(
                        onClick = { onPick(folder) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (currentFolderId == folder.id) "• ${folder.name}" else folder.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
