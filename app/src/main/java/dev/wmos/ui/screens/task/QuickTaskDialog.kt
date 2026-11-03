package dev.wmos.ui.screens.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.wmos.ui.startOfDay
import java.time.LocalDate

/** Minimal capture: only a title is required. Everything else is optional by design. */
@Composable
fun QuickTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, dueAt: Long?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var dueChoice by remember { mutableStateOf(DueChoice.NONE) }
    val focus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Что нужно сделать") },
                    singleLine = true,
                    supportingText = { Text("Срок и проект можно добавить позже") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (title.isNotBlank()) onConfirm(title.trim(), dueChoice.toMillis())
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DueChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = dueChoice == choice,
                            onClick = { dueChoice = choice },
                            label = { Text(choice.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), dueChoice.toMillis()) },
                enabled = title.isNotBlank(),
            ) { Text("Создать задачу") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

enum class DueChoice(val label: String) {
    NONE("Без срока"),
    TODAY("Сегодня"),
    TOMORROW("Завтра"),
    WEEK("Через неделю"),
    ;

    fun toMillis(): Long? = when (this) {
        NONE -> null
        TODAY -> startOfDay(LocalDate.now()) + 18 * 60 * 60 * 1000L
        TOMORROW -> startOfDay(LocalDate.now().plusDays(1)) + 18 * 60 * 60 * 1000L
        WEEK -> startOfDay(LocalDate.now().plusDays(7)) + 18 * 60 * 60 * 1000L
    }
}
