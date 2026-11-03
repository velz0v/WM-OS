package dev.wmos.ui.screens.graus

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wmos.data.db.entity.ChatMessageEntity
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.formatDateTime
import dev.wmos.ui.theme.LocalWmColors

@Composable
fun GrausScreen(
    onOpenAiLab: () -> Unit,
    onBack: () -> Unit,
    viewModel: GrausViewModel = viewModel(factory = GrausViewModel.Factory),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val toolNotes by viewModel.toolNotes.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, sending, streaming != null) {
        if (streaming != null) {
            // The live bubble sits right below all messages — keep it visible as it grows.
            listState.animateScrollToItem(messages.size)
        } else if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            "Граус",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = when {
                                provider == null -> "Провайдер не настроен — ответ придёт из AI Lab"
                                else -> "${provider!!.name} · ${provider!!.model}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Outlined.History, contentDescription = "История диалогов")
                    }
                    IconButton(onClick = { viewModel.newConversation() }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Новый диалог")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (provider == null) {
                SetupCard(onOpenAiLab)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty() && !sending && streaming == null) {
                    item { EmptyChat(onOpenAiLab, onSuggestion = { viewModel.send(it) }) }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (streaming != null) {
                    item { StreamingBubble(streaming.orEmpty()) }
                }
                if (toolNotes.isNotEmpty()) {
                    item {
                        ToolNotesCard(toolNotes)
                    }
                }
                if (error != null) {
                    item {
                        ErrorBubble(error!!, viewModel::dismissError, onOpenAiLab)
                    }
                }
                if (sending && streaming == null) {
                    item { ThinkingRow() }
                }
            }

            pending?.let { action ->
                PendingCard(
                    title = action.title,
                    description = action.description,
                    preview = action.preview,
                    onConfirm = { viewModel.confirmPending() },
                    onCancel = { viewModel.cancelPending() },
                )
            }

            Composer(
                value = input,
                enabled = !sending,
                onValueChange = { input = it },
                onSend = {
                    val text = input
                    input = ""
                    viewModel.send(text)
                },
                canSend = input.isNotBlank() && !sending && provider != null,
                providerReady = provider != null,
            )
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            ConversationHistory(
                conversations = conversations,
                onOpen = { id ->
                    viewModel.openConversation(id)
                    showHistory = false
                },
                onDelete = { viewModel.deleteConversation(it) },
            )
        }
    }
}

@Composable
private fun ConversationHistory(
    conversations: List<dev.wmos.data.db.entity.ConversationEntity>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleting by remember { mutableStateOf<dev.wmos.data.db.entity.ConversationEntity?>(null) }

    deleting?.let { conversation ->
        ConfirmDialog(
            title = "Удалить диалог?",
            message = "Удалится только история переписки. Задачи и заметки, созданные из неё, останутся.",
            confirmLabel = "Удалить диалог",
            destructive = true,
            onConfirm = {
                onDelete(conversation.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            "История диалогов",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (conversations.isEmpty()) {
            Text(
                "Диалогов пока нет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        conversations.forEach { conversation ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpen(conversation.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        conversation.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        formatDateTime(conversation.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { deleting = conversation }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Удалить диалог",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    val extras = LocalWmColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 18.dp,
                    ),
                )
                .background(
                    when {
                        isUser -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                )
                .then(
                    if (isUser) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, extras.outlineSubtle, RoundedCornerShape(18.dp))
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isUser -> extras.onAccent
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
    Text(
        text = formatDateTime(message.createdAt),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth(),
        textAlign = if (isUser) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
    )
}

/** Growing answer bubble shown while the provider streams tokens. */
@Composable
private fun StreamingBubble(text: String) {
    val extras = LocalWmColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 6.dp,
                        bottomEnd = 18.dp,
                    ),
                )
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, extras.outlineSubtle, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (text.isBlank()) "…" else text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (text.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun ThinkingRow() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "thinkingAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Icon(
            Icons.Outlined.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Граус думает…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        )
    }
}

@Composable
private fun PendingCard(
    title: String,
    description: String,
    preview: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val extras = LocalWmColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(extras.accentSoft, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium)
        if (preview.isNotBlank() && preview != description) {
            Spacer(Modifier.height(4.dp))
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) { Text("Отменить") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onConfirm) { Text("Подтвердить", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ToolNotesCard(toolNotes: List<String>) {
    WmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Что сделал Граус", style = MaterialTheme.typography.labelLarge)
        }
        toolNotes.forEach { note ->
            Text(
                "• $note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupCard(onOpenAiLab: () -> Unit) {
    WmCard(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Подключите свой AI-провайдер", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Ключ хранится в Keystore и не попадает в базу, логи и backup. Без ключа локальные функции работают как обычно.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenAiLab) { Text("Открыть AI Lab") }
    }
}

/** Greeting with real, tappable starter prompts — no dead ends. */
@Composable
private fun EmptyChat(
    onOpenAiLab: () -> Unit,
    onSuggestion: (String) -> Unit,
) {
    val suggestions = listOf(
        "Что у меня на сегодня?",
        "Найди заметки про работу",
        "Напомни завтра в 18:30 отдохнуть",
        "Просто поболтаем: расскажи, что ты умеешь",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Привет, я Граус", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Поболтаем, поработаем с задачами и заметками или найдём что угодно в вашем Second Brain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onSuggestion(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        TextButton(onClick = onOpenAiLab) { Text("Подключить свой AI-провайдер") }
    }
}

@Composable
private fun ErrorBubble(message: String, onDismiss: () -> Unit, onOpenAiLab: () -> Unit) {
    val extras = LocalWmColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(extras.dangerContainer, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = extras.danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ошибка", style = MaterialTheme.typography.labelLarge, color = extras.danger)
            }
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = extras.onDangerContainer)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpenAiLab) { Text("AI Lab", color = extras.onDangerContainer) }
                TextButton(onClick = onDismiss) { Text("Закрыть", color = extras.onDangerContainer) }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    providerReady: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            placeholder = {
                Text(
                    if (providerReady) "Спросите или поручите…" else "Сначала подключите провайдера",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            maxLines = 5,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Отправить",
                tint = if (canSend) {
                    LocalWmColors.current.onAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
                modifier = Modifier
                    .size(20.dp)
                    .alpha(if (canSend || value.isNotBlank()) 1f else 0.7f),
            )
        }
    }
}
