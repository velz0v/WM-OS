package dev.wmos.ui.screens.ailab

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wmos.ai.ProviderPreset
import dev.wmos.data.db.entity.AiProviderEntity
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.SectionHeader
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.theme.LocalWmColors

@Composable
fun AiLabScreen(
    onBack: () -> Unit,
    viewModel: AiLabViewModel = viewModel(factory = AiLabViewModel.Factory),
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val extras = LocalWmColors.current

    var editing by remember { mutableStateOf<AiProviderEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AiProviderEntity?>(null) }

    if (creating || editing != null) {
        ProviderDialog(
            existing = editing,
            presets = viewModel.presets,
            error = saveError,
            onDismiss = {
                creating = false
                editing = null
                viewModel.clearSaveError()
            },
            onSave = { id, name, kind, baseUrl, model, key, makeDefault ->
                viewModel.save(id, name, kind, baseUrl, model, key, makeDefault) {
                    creating = false
                    editing = null
                }
            },
        )
    }

    deleting?.let { provider ->
        ConfirmDialog(
            title = "Удалить провайдера?",
            message = "«${provider.name}» и сохранённый ключ будут удалены с устройства.",
            confirmLabel = "Удалить провайдера",
            destructive = true,
            onConfirm = {
                viewModel.delete(provider.id)
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
                title = { Text("AI Lab") },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Добавить провайдера")
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
            item {
                WmCard(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Ключи хранятся в зашифрованном хранилище Android Keystore. " +
                                "В базе остаётся только ссылка на ключ. В backup ключи не попадают.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item { SectionHeader("Провайдеры") }

            if (providers.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.SmartToy,
                        title = "Провайдеров нет",
                        message = "Добавьте свой ключ OpenAI-совместимого сервиса. Без него Граус не работает, " +
                            "но остальные функции приложения — работают.",
                        actionLabel = "Добавить провайдера",
                        onAction = { creating = true },
                    )
                }
            }

            items(providers, key = { it.id }) { provider ->
                WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                if (provider.isDefault) {
                                    Spacer(Modifier.width(8.dp))
                                    StatusPill(
                                        text = "По умолчанию",
                                        color = extras.onSuccessContainer,
                                        background = extras.successContainer,
                                    )
                                }
                            }
                            Text(
                                "${provider.model} · ${provider.baseUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Ключ: ${viewModel.maskedKey(provider).ifBlank { "не сохранён" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = provider.enabled,
                            onCheckedChange = { viewModel.toggleEnabled(provider) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                viewModel.resetTest()
                                viewModel.testConnection(provider)
                            },
                            label = { Text("Проверить") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        )
                        AssistChip(
                            onClick = { editing = provider },
                            label = { Text("Изменить") },
                        )
                        if (!provider.isDefault) {
                            AssistChip(
                                onClick = { viewModel.setDefault(provider.id) },
                                label = { Text("По умолчанию") },
                            )
                        }
                        AssistChip(
                            onClick = { deleting = provider },
                            label = { Text("Удалить") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        )
                    }
                }
            }

            if (test.running || test.success != null) {
                item {
                    val color = when (test.success) {
                        true -> extras.success
                        false -> extras.danger
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val background = when (test.success) {
                        true -> extras.successContainer
                        false -> extras.dangerContainer
                        null -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    WmCard(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (test.success == false) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = color,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (test.running) "Проверяем подключение…" else "Результат проверки",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(test.message, style = MaterialTheme.typography.bodySmall)
                            }
                            StatusPill(
                                text = if (test.running) {
                                    "…"
                                } else if (test.success == true) {
                                    "OK"
                                } else {
                                    "Ошибка"
                                },
                                color = color,
                                background = background,
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Ошибки, которые обрабатываются: неверный ключ, квота, лимит запросов, тайм-аут, " +
                            "недоступная модель, слишком большой контекст и ошибка сервера.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderDialog(
    existing: AiProviderEntity?,
    presets: List<ProviderPreset>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, String?, Boolean) -> Unit,
) {
    var preset by remember { mutableStateOf(presets.firstOrNull { it.id == existing?.kind } ?: presets.first()) }
    var name by remember { mutableStateOf(existing?.name ?: preset.name) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: preset.baseUrl) }
    var model by remember { mutableStateOf(existing?.model ?: preset.defaultModel) }
    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var makeDefault by remember { mutableStateOf(existing?.isDefault ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новый провайдер" else "Изменить провайдера") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.take(4).forEach { item ->
                        FilterChip(
                            selected = preset.id == item.id,
                            onClick = {
                                preset = item
                                if (existing == null) {
                                    name = item.name
                                    baseUrl = item.baseUrl
                                    model = item.defaultModel
                                }
                            },
                            label = { Text(item.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (только https)") },
                    singleLine = true,
                    isError = !baseUrl.startsWith("https://"),
                    supportingText = if (!baseUrl.startsWith("https://")) {
                        { Text("Нужен адрес вида https://api.example.com/v1") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Модель") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(if (existing == null) "API-ключ" else "Новый API-ключ (пусто — не менять)") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = keyVisible, onCheckedChange = { keyVisible = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Показать ключ", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = makeDefault, onCheckedChange = { makeDefault = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Использовать по умолчанию", style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = LocalWmColors.current.danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(existing?.id, name, preset.id, baseUrl, model, key.ifBlank { null }, makeDefault)
                },
                enabled = name.isNotBlank() && baseUrl.startsWith("https://") && model.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
