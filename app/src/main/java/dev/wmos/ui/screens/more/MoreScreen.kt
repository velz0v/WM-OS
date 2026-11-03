package dev.wmos.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.ui.container
import dev.wmos.ui.theme.ChipBlue
import dev.wmos.ui.theme.ChipGreen
import dev.wmos.ui.theme.ChipLime
import dev.wmos.ui.theme.ChipOrange
import dev.wmos.ui.theme.ChipPurple
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MoreViewModel(container: AppContainer) : ViewModel() {
    val inboxCount: StateFlow<Int> = container.inbox.observeNewCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    companion object {
        val Factory = viewModelFactory { initializer { MoreViewModel(container()) } }
    }
}

private data class MenuEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val chipColor: Color,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun MoreScreen(
    onOpenInbox: () -> Unit,
    onOpenAiLab: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenAutopilot: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenQuick: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenActionLog: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: MoreViewModel = viewModel(factory = MoreViewModel.Factory),
) {
    val inboxCount by viewModel.inboxCount.collectAsStateWithLifecycle()

    val inboxEntry = MenuEntry(
        title = "Входящие",
        subtitle = if (inboxCount == 0) "Нет необработанных материалов" else "$inboxCount ждут разбора",
        icon = Icons.Outlined.Inbox,
        chipColor = ChipBlue,
        onClick = onOpenInbox,
    )
    val trashEntry = MenuEntry(
        title = "Корзина",
        subtitle = "Удалённые записи можно восстановить",
        icon = Icons.Outlined.Delete,
        chipColor = ChipOrange,
        onClick = onOpenTrash,
    )
    val filesEntry = MenuEntry(
        title = "WM Files",
        subtitle = "Вложения к записям в приватном хранилище",
        icon = Icons.Outlined.FolderOpen,
        chipColor = ChipGreen,
        onClick = onOpenFiles,
    )
    val aiLabEntry = MenuEntry(
        title = "AI Lab",
        subtitle = "Провайдеры, модели и ключи",
        icon = Icons.Outlined.SmartToy,
        chipColor = ChipLime,
        onClick = onOpenAiLab,
    )
    val memoryEntry = MenuEntry(
        title = "Память Грауса",
        subtitle = "Факты, которые помощник помнит",
        icon = Icons.Outlined.Psychology,
        chipColor = ChipPurple,
        onClick = onOpenMemory,
    )
    val autopilotEntry = MenuEntry(
        title = "Autopilot",
        subtitle = "Сценарии, которые меняют данные только по вашему запуску",
        icon = Icons.Outlined.Bolt,
        chipColor = ChipLime,
        onClick = onOpenAutopilot,
    )
    val logEntry = MenuEntry(
        title = "Журнал действий Грауса",
        subtitle = "Что было выполнено и с каким результатом",
        icon = Icons.Outlined.Build,
        chipColor = ChipBlue,
        onClick = onOpenActionLog,
    )
    val quickEntry = MenuEntry(
        title = "WM Quick",
        subtitle = "Пароли, калькулятор, конвертер единиц",
        icon = Icons.Outlined.Bolt,
        chipColor = ChipOrange,
        onClick = onOpenQuick,
    )
    val backupEntry = MenuEntry(
        title = "Экспорт и backup",
        subtitle = "Markdown, JSON и архив с манифестом",
        icon = Icons.Outlined.Save,
        chipColor = ChipGreen,
        onClick = onOpenBackup,
    )
    val settingsEntry = MenuEntry(
        title = "Настройки",
        subtitle = "Тема, напоминания, приватность",
        icon = Icons.Outlined.Settings,
        chipColor = ChipPurple,
        onClick = onOpenSettings,
    )
    val aboutEntry = MenuEntry(
        title = "О приложении",
        subtitle = "Версия, лицензия, ограничения",
        icon = Icons.Outlined.Info,
        chipColor = ChipBlue,
        onClick = onOpenAbout,
    )
    val privacyEntry = MenuEntry(
        title = "Приватность",
        subtitle = "Данные остаются на устройстве",
        icon = Icons.Outlined.Lock,
        chipColor = ChipOrange,
        enabled = false,
        onClick = {},
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ещё", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            item { SectionTitle("Материалы") }
            item { MenuTile(inboxEntry) }
            item { MenuTile(trashEntry) }
            item { MenuTile(filesEntry) }

            item { SectionTitle("ИИ") }
            item { MenuTile(aiLabEntry) }
            item { MenuTile(memoryEntry) }
            item { MenuTile(autopilotEntry) }
            item { MenuTile(logEntry) }

            item { SectionTitle("Инструменты") }
            item { MenuTile(quickEntry) }
            item { MenuTile(backupEntry) }

            item { SectionTitle("Приложение") }
            item { MenuTile(settingsEntry) }
            item { MenuTile(aboutEntry) }
            item { MenuTile(privacyEntry) }

            item {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "WM OS 0.1.0 · локальное хранилище",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** One menu entry in the "123.jpeg" style: rounded row with a coloured icon chip. */
@Composable
private fun MenuTile(
    entry: MenuEntry,
    modifier: Modifier = Modifier,
) {
    val alpha = if (entry.enabled) 1f else 0.45f
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (entry.enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = entry.onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(entry.chipColor.copy(alpha = alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = Color(0xFF10130A),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
