package dev.wmos.ui.screens.memory

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import dev.wmos.data.db.entity.SavedMemoryEntity
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.formatDateTime
import dev.wmos.ui.theme.LocalWmColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(private val container: AppContainer) : ViewModel() {
    val entries: StateFlow<List<SavedMemoryEntity>> = container.memory.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: String?, key: String, value: String, sensitive: Boolean) {
        viewModelScope.launch { container.memory.save(id, key, value, sensitive) }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.memory.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory { initializer { MemoryViewModel(container()) } }
    }
}

@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = viewModel(factory = MemoryViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val extras = LocalWmColors.current
    var editing by remember { mutableStateOf<SavedMemoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedMemoryEntity?>(null) }

    if (creating || editing != null) {
        MemoryDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { id, key, value, sensitive ->
                viewModel.save(id, key, value, sensitive)
                creating = false
                editing = null
            },
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = "Удалить факт из памяти?",
            message = "«${entry.key}» больше не будет использоваться Граусом в ответах.",
            confirmLabel = "Удалить факт",
            destructive = true,
            onConfirm = {
                viewModel.delete(entry.id)
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
                title = { Text("Память Грауса") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Добавить") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = extras.onAccent,
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
            item {
                Text(
                    "Память — это сохранённые факты, которые Граус может использовать в ответах. " +
                        "Вы можете просматривать, изменять и удалять их в любой момент. " +
                        "Пароли и ключи сюда попадать не должны.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Psychology,
                        title = "Память пуста",
                        message = "Добавьте факт, который Граус должен помнить: например, часовой пояс или предпочтения.",
                        actionLabel = "Добавить факт",
                        onAction = { creating = true },
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.key, style = MaterialTheme.typography.titleSmall)
                                if (entry.isSensitive) {
                                    Spacer(Modifier.height(0.dp))
                                    StatusPill(
                                        text = "чувствительное",
                                        color = extras.onWarningContainer,
                                        background = extras.warningContainer,
                                    )
                                }
                            }
                            Text(
                                entry.value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                formatDateTime(entry.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editing = entry }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Изменить")
                        }
                        IconButton(onClick = { deleting = entry }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryDialog(
    initial: SavedMemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, Boolean) -> Unit,
) {
    var key by remember { mutableStateOf(initial?.key ?: "") }
    var value by remember { mutableStateOf(initial?.value ?: "") }
    var sensitive by remember { mutableStateOf(initial?.isSensitive ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый факт" else "Изменить факт") },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Что запомнить") },
                    placeholder = { Text("Часовой пояс") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Значение") },
                    placeholder = { Text("Europe/Moscow") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Чувствительное", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = sensitive, onCheckedChange = { sensitive = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial?.id, key.trim(), value.trim(), sensitive) },
                enabled = key.isNotBlank() && value.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
