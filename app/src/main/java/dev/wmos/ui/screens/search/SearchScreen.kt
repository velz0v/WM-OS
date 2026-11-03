package dev.wmos.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.repo.SearchResults
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.SectionHeader
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.dueLabel
import dev.wmos.ui.snippet
import dev.wmos.ui.titleOrFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private var job: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        job?.cancel()
        if (value.trim().length < 2) {
            _results.value = SearchResults(query = value.trim())
            _searching.value = false
            return
        }
        job = viewModelScope.launch {
            _searching.value = true
            delay(250)
            val found = withContext(Dispatchers.IO) { container.search.search(value) }
            _results.value = found
            _searching.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { SearchViewModel(container()) } }
    }
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenReminder: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenInbox: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("Поиск") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Заметки, задачи, напоминания, проекты") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )

            LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                if (query.trim().length < 2) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = "Введите хотя бы два символа",
                            message = "Поиск идёт по заметкам, задачам, напоминаниям, проектам и входящим.",
                        )
                    }
                } else if (results.total == 0 && !searching) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = "Ничего не найдено",
                            message = "Попробуйте другое слово. Поиск не выходит за пределы ваших данных.",
                        )
                    }
                }

                if (results.notes.isNotEmpty()) {
                    item { SectionHeader("Заметки", subtitle = "${results.notes.size}") }
                    items(results.notes, key = { "n-${it.id}" }) { note ->
                        WmCard(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            onClick = { onOpenNote(note.id) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.StickyNote2,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        titleOrFallback(note.title, note.content),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        snippet(note.content, 90),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }

                if (results.tasks.isNotEmpty()) {
                    item { SectionHeader("Задачи", subtitle = "${results.tasks.size}") }
                    items(results.tasks, key = { "t-${it.id}" }) { task ->
                        WmCard(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            onClick = { onOpenTask(task.id) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.TaskAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    task.dueAt?.let {
                                        Text(
                                            dueLabel(it),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (results.reminders.isNotEmpty()) {
                    item { SectionHeader("Напоминания", subtitle = "${results.reminders.size}") }
                    items(results.reminders, key = { "r-${it.id}" }) { reminder ->
                        WmCard(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            onClick = { onOpenReminder(reminder.id) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Alarm,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(reminder.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(
                                        dueLabel(reminder.nextTriggerAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (results.projects.isNotEmpty()) {
                    item { SectionHeader("Проекты", subtitle = "${results.projects.size}") }
                    items(results.projects, key = { "p-${it.id}" }) { project ->
                        WmCard(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            onClick = { onOpenProject(project.id) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.FolderSpecial,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(project.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                if (results.inbox.isNotEmpty()) {
                    item { SectionHeader("Входящие", subtitle = "${results.inbox.size}") }
                    item {
                        WmCard(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            onClick = onOpenInbox,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Inbox,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Открыть входящие — материалов с совпадением: ${results.inbox.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
