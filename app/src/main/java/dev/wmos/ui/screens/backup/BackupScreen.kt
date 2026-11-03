package dev.wmos.ui.screens.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.ui.components.SectionHeader
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.theme.LocalWmColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val counts: Map<String, Int> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
    val failed: Boolean = false,
    val preview: dev.wmos.data.repo.BackupRepository.ImportPreview? = null,
    val importing: Boolean = false,
    val importMessage: String? = null,
    val importFailed: Boolean = false,
    val previewName: String = "",
)

class BackupViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    private var pendingImportUri: Uri? = null

    init {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) { container.backup.previewCounts() }
            _state.value = _state.value.copy(counts = counts)
        }
    }

    fun exportJson(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = withContext(Dispatchers.IO) { container.backup.exportJson(uri) }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        busy = false,
                        failed = false,
                        message = "Экспортировано ${it.bytes / 1024} КБ: заметок ${it.items}, задач ${it.tasks}, напоминаний ${it.reminders}",
                    )
                },
                onFailure = {
                    _state.value.copy(busy = false, failed = true, message = it.message ?: "Не удалось записать файл")
                },
            )
        }
    }

    fun exportMarkdown(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = withContext(Dispatchers.IO) { container.backup.exportMarkdown(uri) }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        busy = false,
                        failed = false,
                        message = "Markdown-файл записан: ${it.items} заметок, ${it.bytes / 1024} КБ",
                    )
                },
                onFailure = {
                    _state.value.copy(busy = false, failed = true, message = it.message ?: "Не удалось записать файл")
                },
            )
        }
    }

    fun previewImport(uri: Uri, name: String) {
        viewModelScope.launch {
            pendingImportUri = uri
            _state.value = _state.value.copy(importing = true, importMessage = null, importFailed = false)
            val result = withContext(Dispatchers.IO) { container.backup.previewImport(uri) }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(importing = false, preview = it, previewName = name)
                },
                onFailure = {
                    _state.value.copy(
                        importing = false,
                        importFailed = true,
                        importMessage = it.message ?: "Не удалось прочитать файл",
                    )
                },
            )
        }
    }

    fun applyImport(mode: dev.wmos.data.repo.BackupRepository.ImportMode) {
        val uri = pendingImportUri ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true, importMessage = null, importFailed = false)
            val result = withContext(Dispatchers.IO) { container.backup.applyImport(uri, mode) }
            _state.value = result.fold(
                onSuccess = { summary ->
                    val counts = withContext(Dispatchers.IO) { container.backup.previewCounts() }
                    _state.value.copy(
                        importing = false,
                        preview = null,
                        importFailed = false,
                        counts = counts,
                        importMessage = "Импортировано записей: ${summary.created}, пропущено дубликатов: ${summary.skipped}",
                    )
                },
                onFailure = {
                    _state.value.copy(
                        importing = false,
                        importFailed = true,
                        importMessage = it.message ?: "Импорт не выполнен",
                    )
                },
            )
        }
    }

    fun dismissPreview() {
        pendingImportUri = null
        _state.value = _state.value.copy(preview = null, importMessage = null)
    }

    companion object {
        val Factory = viewModelFactory { initializer { BackupViewModel(container()) } }
    }
}

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extras = LocalWmColors.current

    val jsonPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportJson) }

    val markdownPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let(viewModel::exportMarkdown) }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.previewImport(it, "выбранный файл") } }

    var confirmReplace by remember { mutableStateOf(false) }

    if (confirmReplace) {
        dev.wmos.ui.components.ConfirmDialog(
            title = "Заменить все данные?",
            message = "Текущие заметки, задачи, проекты и напоминания будут удалены и заменены содержимым файла. " +
                "Это необратимо. Сначала сделайте экспорт текущих данных.",
            confirmLabel = "Заменить данные",
            destructive = true,
            onConfirm = {
                confirmReplace = false
                viewModel.applyImport(dev.wmos.data.repo.BackupRepository.ImportMode.REPLACE)
            },
            onDismiss = { confirmReplace = false },
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
                title = { Text("Экспорт и backup") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                SectionHeader("Что будет выгружено")
                WmCard {
                    val counts = state.counts
                    Text("Папки: ${counts["folders"] ?: 0}", style = MaterialTheme.typography.bodyMedium)
                    Text("Записи: ${counts["items"] ?: 0}", style = MaterialTheme.typography.bodyMedium)
                    Text("Задачи: ${counts["tasks"] ?: 0}", style = MaterialTheme.typography.bodyMedium)
                    Text("Проекты: ${counts["projects"] ?: 0}", style = MaterialTheme.typography.bodyMedium)
                    Text("Напоминания: ${counts["reminders"] ?: 0}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "API-ключи и пароли в экспорт не попадают. Файл сохраняется туда, куда вы его положили, " +
                            "и дальше приложением не контролируется.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Экспорт")
                Button(
                    onClick = { jsonPicker.launch("wm-os-backup.json") },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Сохранить полный JSON")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { markdownPicker.launch("wm-os-notes.md") },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text("Сохранить заметки в Markdown")
                }
            }

            state.message?.let { message ->
                item {
                    Spacer(Modifier.height(12.dp))
                    WmCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        StatusPill(
                            text = if (state.failed) "Ошибка" else "Готово",
                            color = if (state.failed) extras.onDangerContainer else extras.onSuccessContainer,
                            background = if (state.failed) extras.dangerContainer else extras.successContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Импорт")
                OutlinedButton(
                    onClick = { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    enabled = !state.importing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text("Выбрать JSON-файл для импорта")
                }
                Text(
                    "Импорт всегда показывает предпросмотр до записи. API-ключи из файла не читаются и не восстанавливаются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            if (state.importing) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.padding(0.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            state.preview?.let { preview ->
                item {
                    WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Предпросмотр импорта", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Схема ${preview.schemaVersion}, выгружено: ${preview.exportedAt.ifBlank { "неизвестно" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Папки ${preview.counts["folders"] ?: 0} · записи ${preview.counts["items"] ?: 0} · " +
                                "задачи ${preview.counts["tasks"] ?: 0} · проекты ${preview.counts["projects"] ?: 0} · " +
                                "напоминания ${preview.counts["reminders"] ?: 0}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (preview.conflicts > 0) {
                            Text(
                                "Совпадений с текущими ID: ${preview.conflicts}. «Объединить» пропустит их, " +
                                    "«Как новые» создаст копии с новыми ID.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.warning,
                            )
                        }
                        preview.problems.forEach { problem ->
                            Text(
                                "Проблема: $problem",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.danger,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    viewModel.applyImport(
                                        dev.wmos.data.repo.BackupRepository.ImportMode.MERGE,
                                    )
                                },
                                enabled = preview.canImport && !state.importing,
                            ) { Text("Объединить") }
                            TextButton(
                                onClick = {
                                    viewModel.applyImport(
                                        dev.wmos.data.repo.BackupRepository.ImportMode.AS_NEW,
                                    )
                                },
                                enabled = preview.canImport && !state.importing,
                            ) { Text("Как новые") }
                            TextButton(
                                onClick = { confirmReplace = true },
                                enabled = preview.canImport && !state.importing,
                            ) { Text("Заменить", color = extras.danger) }
                        }
                        TextButton(onClick = { viewModel.dismissPreview() }) { Text("Отмена") }
                    }
                }
            }

            state.importMessage?.let { message ->
                item {
                    Spacer(Modifier.height(8.dp))
                    WmCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        StatusPill(
                            text = if (state.importFailed) "Ошибка импорта" else "Импорт выполнен",
                            color = if (state.importFailed) extras.onDangerContainer else extras.onSuccessContainer,
                            background = if (state.importFailed) extras.dangerContainer else extras.successContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
