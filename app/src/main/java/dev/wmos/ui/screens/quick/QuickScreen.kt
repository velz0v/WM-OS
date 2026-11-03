package dev.wmos.ui.screens.quick

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wmos.domain.quick.PasswordGenerator
import dev.wmos.domain.quick.SafeExpression
import dev.wmos.domain.quick.UnitConverter
import dev.wmos.ui.components.SectionHeader
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.theme.LocalWmColors

@Composable
fun QuickScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("WM Quick") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                listOf("Пароль", "Калькулятор", "Единицы").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> PasswordTab()
                1 -> CalculatorTab()
                else -> ConverterTab()
            }
        }
    }
}

@Composable
private fun PasswordTab() {
    val context = LocalContext.current
    val extras = LocalWmColors.current
    var length by remember { mutableFloatStateOf(20f) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var password by remember {
        mutableStateOf(PasswordGenerator.generate(20, digits, symbols, upper))
    }
    var copied by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            WmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = password,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Пароль", password))
                        copied = true
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Скопировать пароль")
                    }
                    IconButton(onClick = {
                        password = PasswordGenerator.generate(length.toInt(), digits, symbols, upper)
                        copied = false
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Сгенерировать заново")
                    }
                }
                Text(
                    text = if (copied) {
                        "Скопировано в буфер"
                    } else {
                        "Надёжность: ${PasswordGenerator.strength(length.toInt(), digits, symbols, upper)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (copied) extras.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Длина: ${length.toInt()}")
            Slider(
                value = length,
                onValueChange = {
                    length = it
                    password = PasswordGenerator.generate(it.toInt(), digits, symbols, upper)
                },
                valueRange = 8f..64f,
                steps = 55,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            ToggleRow("Цифры", digits) {
                digits = it
                password = PasswordGenerator.generate(length.toInt(), digits, symbols, upper)
            }
        }
        item {
            ToggleRow("Символы", symbols) {
                symbols = it
                password = PasswordGenerator.generate(length.toInt(), digits, symbols, upper)
            }
        }
        item {
            ToggleRow("Заглавные буквы", upper) {
                upper = it
                password = PasswordGenerator.generate(length.toInt(), digits, symbols, upper)
            }
        }
        item {
            Text(
                "Пароль генерируется криптостойким генератором (SecureRandom) на устройстве. " +
                    "Он не отправляется в ИИ и не попадает в журналы.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CalculatorTab() {
    var expression by remember { mutableStateOf("") }
    val result = remember(expression) {
        if (expression.isBlank()) null else SafeExpression.evaluate(expression).getOrNull()
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            OutlinedTextField(
                value = expression,
                onValueChange = { expression = it },
                label = { Text("Выражение") },
                placeholder = { Text("(120 + 30) * 2 / 5") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = expression.isNotBlank() && result == null,
                supportingText = {
                    Text(
                        when {
                            expression.isBlank() -> "Поддерживаются + − × ÷ % и скобки"
                            result == null -> "Не удалось вычислить"
                            else -> "= $result"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            val keys = listOf(
                listOf("7", "8", "9", "/"),
                listOf("4", "5", "6", "*"),
                listOf("1", "2", "3", "-"),
                listOf("0", ".", "(", ")"),
                listOf("+", "%"),
            )
            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    row.forEach { key ->
                        OutlinedButton(
                            onClick = { expression += key },
                            modifier = Modifier.weight(1f),
                        ) { Text(key) }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedButton(
                    onClick = { expression = expression.dropLast(1) },
                    modifier = Modifier.weight(1f),
                ) { Text("⌫") }
                OutlinedButton(
                    onClick = { expression = "" },
                    modifier = Modifier.weight(1f),
                ) { Text("Сброс") }
            }
        }
    }
}

@Composable
private fun ConverterTab() {
    var kind by remember { mutableStateOf(UnitConverter.Kind.LENGTH) }
    val units = UnitConverter.units.getValue(kind)
    var from by remember { mutableStateOf(units.first().id) }
    var to by remember { mutableStateOf(units.getOrNull(1)?.id ?: units.first().id) }
    var valueText by remember { mutableStateOf("1") }

    val value = valueText.replace(',', '.').toDoubleOrNull()
    val converted = value?.let { UnitConverter.convert(kind, from, to, it) }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitConverter.Kind.entries.forEach { entry ->
                    FilterChip(
                        selected = kind == entry,
                        onClick = {
                            kind = entry
                            val list = UnitConverter.units.getValue(entry)
                            from = list.first().id
                            to = list.getOrNull(1)?.id ?: list.first().id
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = valueText,
                onValueChange = { valueText = it },
                label = { Text("Значение") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                isError = valueText.isNotBlank() && value == null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { unit ->
                    FilterChip(
                        selected = from == unit.id,
                        onClick = { from = unit.id },
                        label = { Text(unit.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { unit ->
                    FilterChip(
                        selected = to == unit.id,
                        onClick = { to = unit.id },
                        label = { Text(unit.label) },
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            WmCard {
                Text(
                    text = converted?.let { "%.4f".format(it).trimEnd('0').trimEnd('.') } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "${units.firstOrNull { it.id == from }?.label ?: from} → " +
                        (units.firstOrNull { it.id == to }?.label ?: to),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
