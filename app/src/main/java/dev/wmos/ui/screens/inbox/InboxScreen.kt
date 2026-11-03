package dev.wmos.ui.screens.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.InboxItemEntity
import dev.wmos.data.db.entity.InboxStatusValues
import dev.wmos.data.db.entity.KnowledgeTypeValues
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.formatDateTime
import dev.wmos.ui.snippet
import dev.wmos.ui.theme.LocalWmColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<InboxItemEntity>> = container.inbox.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun capture(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { container.inbox.captureText(text) }
    }

    fun captureFile(uri: android.net.Uri, mimeType: String?) {
        viewModelScope.launch { container.inbox.captureUri(uri, mimeType) }
    }

    fun toNote(item: InboxItemEntity) {
        viewModelScope.launch {
            container.brain.createItem(
                type = if (item.kind == "LINK") KnowledgeTypeValues.LINK else KnowledgeTypeValues.NOTE,
                title = item.title,
                content = item.rawText,
                url = item.url,
                source = item.sourcePackage,
            )
            container.inbox.markTriaged(item.id, "Превращено в заметку")
        }
    }

    fun toTask(item: InboxItemEntity) {
        viewModelScope.launch {
            container.tasks.createTask(
                title = item.title.ifBlank { item.rawText.take(80) },
                description = item.rawText,
            )
            container.inbox.markTriaged(item.id, "Превращено в задачу")
        }
    }

    fun delete(item: InboxItemEntity) {
        viewModelScope.launch { container.inbox.delete(item.id) }
    }

    companion object {
        val Factory = viewModelFactory { initializer { InboxViewModel(container()) } }
    }
}

@Composable
fun InboxScreen(
    onBack: () -> Unit,
    viewModel: InboxViewModel = viewModel(factory = InboxViewModel.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val extras = LocalWmColors.current
    var captureOpen by remember { mutableStateOf(false) }
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri: android.net.Uri? -> uri?.let { viewModel.captureFile(it, null) } },
    )
    var deleting by remember { mutableStateOf<InboxItemEntity?>(null) }

    deleting?.let { item ->
        dev.wmos.ui.components.ConfirmDialog(
            title = "Удалить материал?",
            message = "«${item.title.ifBlank { "Без названия" }}» будет удалён вместе с копией файла. " +
                "Если он уже превращён в заметку или задачу — они останутся.",
            confirmLabel = "Удалить материал",
            destructive = true,
            onConfirm = {
                viewModel.delete(item)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    if (captureOpen) {
        CaptureDialog(
            onDismiss = { captureOpen = false },
            onCapture = {
                viewModel.capture(it)
                captureOpen = false
            },
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
                title = { Text("Входящие") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "application/pdf",
                                "image/*",
                                "text/plain",
                                "application/octet-stream",
                            ),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = "Прикрепить файл")
                }
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = { captureOpen = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Добавить") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = extras.onAccent,
                )
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
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = "Входящие пусты",
                        message = "Сюда попадают материалы из «Поделиться» и быстрые ссылки. " +
                            "Сначала сохранение, потом разбор.",
                        actionLabel = "Добавить материал",
                        onAction = { captureOpen = true },
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title.ifBlank { "Без названия" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                            )
                            if (item.rawText.isNotBlank()) {
                                Text(
                                    snippet(item.rawText, 120),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                            Text(
                                "${item.kind} · ${formatDateTime(item.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            item.errorMessage?.let { message ->
                                Text(
                                    message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        when (item.status) {
                            InboxStatusValues.NEW -> StatusPill(
                                text = "Новое",
                                color = extras.onWarningContainer,
                                background = extras.warningContainer,
                            )

                            InboxStatusValues.FAILED -> StatusPill(
                                text = "Ошибка",
                                color = extras.onDangerContainer,
                                background = extras.dangerContainer,
                                icon = Icons.Outlined.Warning,
                            )

                            else -> StatusPill(
                                text = "Разобрано",
                                color = extras.onSuccessContainer,
                                background = extras.successContainer,
                            )
                        }
                    }
                    if (item.status == InboxStatusValues.NEW) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.toNote(item) }) {
                                Icon(Icons.Outlined.Description, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("В заметку")
                            }
                            TextButton(onClick = { viewModel.toTask(item) }) {
                                Icon(Icons.Outlined.TaskAlt, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("В задачу")
                            }
                            TextButton(onClick = { deleting = item }) {
                                Icon(Icons.Outlined.Delete, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Удалить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureDialog(onDismiss: () -> Unit, onCapture: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый материал") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст или ссылка") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Материал сохраняется локально до любого анализа.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCapture(text) }, enabled = text.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
