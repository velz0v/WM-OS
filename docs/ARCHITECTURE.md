# Архитектура WM OS

WM OS — однопроектное Android-приложение (`:app`) на Kotlin/Compose. Слои разделены
по пакетам, DI собран вручную, состояние экранов идёт через `ViewModel` + `StateFlow`.
Ниже — фактическая структура, а не целевая диаграмма из ТЗ.

## 1. Слои

```text
┌──────────────────────────────────────────────────────────────────┐
│ ui/                                                              │
│  nav/WmApp.kt — NavHost + нижняя навигация                       │
│  screens/* — Compose-экраны и ViewModels                         │
│  components/Common.kt — общие состояния и карточки                │
│  theme/* — цвета, типографика, тема                              │
│  UiSupport.kt — форматирование дат, плюрализация, container()     │
└───────────────────────────────┬──────────────────────────────────┘
                                │ ViewModel вызывает репозитории
┌───────────────────────────────▼──────────────────────────────────┐
│ data/repo/                                                       │
│  BrainRepository, TaskRepository, ReminderRepository,             │
│  InboxRepository, ChatRepository, ProviderRepository,             │
│  ActionLogRepository, BackupRepository                            │
│  — валидация, правила, двухфазное планирование, экспорт           │
└───────────────────────────────┬──────────────────────────────────┘
                                │ suspend/Flow
┌───────────────────────────────▼──────────────────────────────────┐
│ data/db/                                                         │
│  WmDatabase, DAO, Entity — Room 2.6.1, schema v1, WAL             │
└──────────────────────────────────────────────────────────────────┘

Отдельно:
  domain/reminder/  — чистые RecurrenceRule, RecurrenceCalculator,
                      NaturalReminderParser, интерфейс ReminderScheduler
  domain/quick/     — SafeExpression, PasswordGenerator, UnitConverter
  reminders/        — Android-реализация AlarmManager и BroadcastReceiver
  ai/               — AiClient, ToolRegistry, GrausEngine
  data/prefs/       — SettingsRepository (DataStore), SecretStore, DraftStore
```

### 1.1 Направление зависимостей

* UI зависит от `AppContainer` и репозиториев.
* Репозитории зависят от `WmDatabase` (DAO).
* `domain/reminder` и `domain/quick` не зависят от Android (кроме Java Time).
* `ai` зависит от `AppContainer` — это исключение: `ToolRegistry` вызывает
  `container.tasks`, `container.brain`, `container.reminders`, `container.providers`.
* `reminders` зависит от `domain.reminder` и `WmApplication`.
* `data/prefs` зависит от Android Context.

Строгого правила «UI не обращается к БД» в коде нет: `NoteEditorScreen` сам подписывается
на `app.container.brain.observeFolders()`, чтобы показать выбор папки. Это единственное
такое место в экранах; остальные читают данные через ViewModel.

## 2. Ручной DI: `AppContainer`

`WmApplication.onCreate()` создаёт `AppContainer(this)`. Все зависимости — `by lazy`
singletons:

| Поле | Тип | Зависимости |
|---|---|---|
| `database` | `WmDatabase` | Context |
| `settings` | `SettingsRepository` | Context |
| `secrets` | `SecretStore` | Context |
| `scheduler` | `ReminderScheduler` | `AlarmReminderScheduler` |
| `brain` | `BrainRepository` | database |
| `tasks` | `TaskRepository` | database |
| `reminders` | `ReminderRepository` | database, scheduler |
| `inbox` | `InboxRepository` | Context, database |
| `chat` | `ChatRepository` | database |
| `providers` | `ProviderRepository` | database, secrets |
| `actionLog` | `ActionLogRepository` | database |
| `backup` | `BackupRepository` | Context, database |
| `graus` | `GrausEngine` | container |
| `drafts` | `DraftStore` | Context |
| `appScope` | `CoroutineScope` | IO + SupervisorJob |
| `openInboxSignal` | `MutableStateFlow<Boolean>` | — |

`WmDatabase.build()` — потокобезопасный singleton с double-checked locking, WAL,
без destructive fallback.

### 2.1 Как ViewModel получает зависимости

`ui/UiSupport.kt`:

```kotlin
fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WmApplication).container
```

Каждый ViewModel имеет `companion object { val Factory = viewModelFactory { initializer { ... } } }`.
Для параметризованных фабрик (note/reminder/folder/project) используется
`fun factory(id: String)`. Экраны вызывают `viewModel(factory = …Factory)`.

### 2.2 Почему без Hilt

* Граф маленький: 10 репозиториев и 2–3 платформенных сервиса.
* Ручное связывание видно в одном файле и не требует KSP-генерации (KSP уже занят Room).
* Нет скрытых зависимостей и легче понять порядок инициализации.
* Минусы приняты осознанно: фабрики приходится писать руками, подменять зависимости в
  тестах сложнее, `AppContainer` протекает в `ToolRegistry`.

## 3. UI и однонаправленный поток данных

```text
Compose event → ViewModel.fun → repository suspend → Room
       ▲                                  │
       └──── StateFlow<UiState> ◄─────────┘
             collectAsStateWithLifecycle()
```

* Экраны не содержат бизнес-логики: только компоновка, диалоги и вызовы ViewModel.
* Состояние — `StateFlow`; для одного экрана часто несколько независимых flow
  (`state`, `folders`, `filter`), а не один монолитный state.
* `SharingStarted.WhileSubscribed(5_000)` — стандарт для экранных flow.
* События (создать, сохранить, удалить) — обычные функции ViewModel, запускающие
  `viewModelScope.launch`.
* Навигационные колбэки передаются в экран сверху (`onOpenNote`, `onBack`), экраны не
  знают `NavController`.

### 3.1 Список ViewModel

| ViewModel | Экран | Основные зависимости |
|---|---|---|
| `HomeViewModel` | Главная | tasks, reminders, brain, inbox |
| `BrainViewModel` | Знания | brain |
| `FolderViewModel` | Папка | brain |
| `NoteEditorViewModel` | Редактор заметки | brain, drafts, appScope |
| `PlanViewModel` | План | tasks, reminders |
| `ProjectViewModel` | Проект | tasks |
| `TrashViewModel` | Корзина | brain |
| `ActionLogViewModel` | Журнал | actionLog |
| `ReminderEditorViewModel` | Напоминание | reminders, scheduler |
| `GrausViewModel` | Граус | chat, providers, graus |
| `AiLabViewModel` | AI Lab | providers, actionLog |
| `InboxViewModel` | Входящие | inbox, brain, tasks |
| `BackupViewModel` | Экспорт | backup |
| `SettingsViewModel` | Настройки | settings |
| `MoreViewModel` | Ещё | inbox |

## 4. Навигация

`ui/nav/Routes.kt` объявляет маршруты, `ui/nav/WmApp.kt` — единственный `NavHost`.
Нижняя навигация показывается только на пяти верхнеуровневых маршрутах.

| Маршрут | Экран | В нижней навигации |
|---|---|---|
| `home` | `HomeScreen` | Да, «Главная» |
| `brain` | `BrainScreen` | Да, «Знания» |
| `plan` | `PlanScreen` | Да, «План» |
| `graus` | `GrausScreen` | Да, «Граус» |
| `more` | `MoreScreen` | Да, «Ещё» |
| `note/{itemId}` | `NoteEditorScreen` | Нет |
| `folder/{folderId}` | `FolderScreen` | Нет |
| `project/{projectId}` | `ProjectScreen` | Нет |
| `reminder/new` | `ReminderEditorScreen` | Нет |
| `reminder/{reminderId}` | `ReminderEditorScreen` | Нет |
| `inbox` | `InboxScreen` | Нет |
| `trash` | `TrashScreen` | Нет |
| `ai-lab` | `AiLabScreen` | Нет |
| `quick` | `QuickScreen` | Нет |
| `backup` | `BackupScreen` | Нет |
| `settings` | `SettingsScreen` | Нет |
| `about` | `AboutScreen` | Нет |
| `action-log` | `ActionLogScreen` | Нет |

Маршруты `note/new` и `task/{taskId}` объявлены, но **не подключены** к `NavHost`.
Создание заметки идёт через `createItem()` с последующим переходом на `note/{id}`.
Открытие задачи с Главной ведёт на «План», потому что отдельного экрана задачи нет.

Поведение верхнеуровневой навигации:

```kotlin
navController.navigate(destination.route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

`TopLevelDestination.fromRoute()` использует `startsWith`, поэтому detail-маршруты не
подсвечивают вкладки и не показывают нижнюю панель.

### 4.1 Share и deep links

`MainActivity.handleShareIntent()` вызывается в `onCreate` и `onNewIntent`:

* `ACTION_SEND` с `EXTRA_STREAM` → `InboxRepository.captureUri` (копирование в private storage);
* `ACTION_SEND` с `EXTRA_TEXT` → `captureText`; URL определяется по префиксу `http(s)://`;
* `ACTION_SEND` с `EXTRA_HTML_TEXT` (если нет text) → `captureText`;
* `ACTION_PROCESS_TEXT` → `captureText`.

После захвата выставляется `openInboxSignal = true`; `WmApp` подписан на него и делает
`navigate("inbox")`. `EXTRA_REMINDER_ID` из уведомления не обрабатывается.

## 5. Состояния экранов

В `ui/components/Common.kt` есть:

| Компонент | Назначение | Фактическое использование |
|---|---|---|
| `SectionHeader` | Заголовок секции + действие | Почти все списки |
| `EmptyState` | Пустое состояние с CTA | Home, Brain, Plan, Inbox, Trash, Backup, AI Lab, Quick |
| `LoadingState` | Спиннер + текст | Редактор заметки |
| `ErrorState` | Ошибка + retry | Объявлен, но в экранах не вызывается |
| `OfflineBanner` | Плашка «Нет сети» | Объявлен, но не вызывается |
| `WmCard` | Карточка-контейнер | Везде |
| `StatusPill` | Текст + цвет статуса | Статусы напоминаний, Inbox, сохранения, AI Lab |
| `ConfirmDialog` | Подтверждение с точной формулировкой | Удаления, очистка корзины |
| `WmListRow` | Строка списка, min 56 dp | «Ещё», проекты, корзина |

Состояния загрузки/пустоты/ошибки реализованы не на каждом экране одинаково:

* `HomeScreen` показывает пустое состояние и спиннер; ошибок БД не показывает;
* `BrainScreen` — `EmptyState`, без Loading/Error;
* `NoteEditorScreen` — Loading, Missing, ErrorStrip с retry, draft dialog;
* `BackupScreen` — busy-флаг, success/error card;
* `AiLabScreen` — empty, test running/success/error, save error;
* `InboxScreen` — empty, статусы NEW/FAILED/TRIAGED.

## 6. Потоки и потоки выполнения

| Операция | Где выполняется | Замечание |
|---|---|---|
| Room suspend/Flow | Room dispatcher | корректно |
| Чтение списков | `stateIn(viewModelScope)` | Main-safe, Room сам уходит в IO |
| AI-проверка в AI Lab | `withContext(Dispatchers.IO)` | корректно |
| Экспорт backup | `withContext(Dispatchers.IO)` | корректно |
| Копирование файла Inbox | `lifecycleScope.launch` без явного IO | `captureUri` делает блокирующий `copyTo` в корутине Main; потенциальный jank/ANR |
| Запрос Грауса | `viewModelScope` → `withContext(Dispatchers.IO)` → `GrausEngine` → `AiClient.chat()` | блокирующий HTTP выполняется в IO, Main не блокируется |
| Планирование AlarmManager | вызывающий поток (обычно Main/IO) | быстрый системный вызов |
| Черновик при `onCleared` | `container.appScope` (IO) | переживает ViewModel |

Проблема с сетевым вызовом Грауса исправлена: `send()` и `confirmPending()` в
`GrausViewModel` оборачивают `GrausEngine` в `withContext(Dispatchers.IO)`, поэтому
синхронный `OkHttpClient.newCall(...).execute()` не выполняется на Main.
`AiLabViewModel` делает то же для проверки подключения.

## 7. Ошибки и Result

* Репозитории возвращают `Result<T>` для пользовательских операций
  (`createTask`, `saveItem`, `create` reminder, `save provider`).
* UI разворачивает `onSuccess`/`onFailure` и показывает текст исключения.
* Доменные конфликты: `ConflictException(currentRevision)`.
* Инструменты Грауса используют собственный `ToolStatus`, а не исключения.
* Глобального error handler/аналитики нет; ошибки БД в большинстве экранов не
  перехватываются и могут привести к пустому списку/крашу.
* Нет единого Snackbar-хоста; сообщения живут внутри экранов.

## 8. Фоновые компоненты

| Компонент | Роль |
|---|---|
| `ReminderReceiver` | Приём будильника и действий уведомления, `goAsync()` |
| `BootReceiver` | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET` → `rescheduleAll()` |
| `WmApplication.applicationScope` | Seed стартовых папок, фоновые операции приложения |
| `AppContainer.appScope` | Финальный flush черновика при `onCleared` |
| WorkManager | Зависимость объявлена, но ни одной `Worker` нет |

## 9. Сборка и тесты

* Один модуль `:app`, namespace/applicationId `dev.wmos`.
* Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9 (Gradle Wrapper зафиксирован в дереве).
* Compose BOM 2024.10.01, Room 2.6.1 + KSP, OkHttp 4.12.0.
* `compileSdk/targetSdk 35`, `minSdk 26`, Java/JVM 17.
* Room schema export: `app/schemas` (после успешной сборки там лежит
  `dev.wmos.data.db.WmDatabase/1.json`).
* Unit-тесты: 37 в трёх классах (`RecurrenceCalculatorTest`, `NaturalReminderParserTest`,
  `QuickToolsTest`). Instrumentation/Compose-тестов нет, хотя зависимости объявлены.
* Robolectric объявлен, но не используется.

## 10. Известные архитектурные долги

1. `ToolRegistry` зависит от всего `AppContainer`, а не от узких интерфейсов.
2. `NoteEditorScreen` обращается к репозиторию напрямую.
3. Нет слоя use case: правила живут в репозиториях, часть — в DAO.
4. `PendingAction`/`continuations` не переживают процесс.
5. `SavedMemory` не подключена.
6. Нет Paging: часть списков ограничена `LIMIT 500`, инструменты — 20; UI всё равно
   грузит результат в память.
7. Нет FTS/индекса поиска; `LIKE` по `searchText`.
8. Нет detekt; ktlint, CI-workflow и secret scanning добавлены, но CI в этой среде не запускался.
