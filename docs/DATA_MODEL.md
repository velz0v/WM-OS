# Модель данных WM OS

Документ описывает **фактическую** схему Room из
`app/src/main/java/dev/wmos/data/db/entity/Entities.kt` и поведение DAO/репозиториев.
Версия схемы — `1`, база — `wm_os.db`, журнал — WAL.

## 1. Общие соглашения

| Соглашение | Значение |
|---|---|
| Идентификаторы | Строковые UUID (`UUID.randomUUID().toString()`), генерируются в репозиториях |
| Время | `Long`, epoch milliseconds UTC; локальное форматирование — только в UI |
| Часовой пояс | У напоминания хранится `timeZoneId` (IANA, например `Europe/Moscow`) |
| Булевы значения | Room/SQLite хранит их как `INTEGER` 0/1 |
| Внешние ключи | **Не объявлены**. Связи логические, по строковым ID; каскадов на уровне SQLite нет |
| `createdAt` / `updatedAt` | Есть у большинства сущностей; выставляются репозиториями, не UI |
| Удаление | Где есть soft delete — отдельный флаг; остальные сущности удаляются физически |

> Важное следствие отсутствия foreign keys: база не мешает оставить `knowledge_items.folderId`
> на удалённую папку или `reminder_occurrences.reminderId` на удалённое напоминание. Целостность
> обеспечивается кодом репозиториев, и не для всех сценариев она реализована.

## 2. Таблицы

### 2.1 `folders` — папки Second Brain

| Колонка | Тип | Null | По умолчанию | Назначение |
|---|---|---:|---|---|
| `id` | TEXT | нет | — | PK |
| `name` | TEXT | нет | — | Название; `createFolder` ограничивает 120 символами, `renameFolder` проверяет только непустоту |
| `description` | TEXT | нет | `""` | Описание |
| `parentId` | TEXT | да | `null` | Родительская папка; `null` — корень |
| `colorKey` | TEXT | да | `null` | Ключ цвета (в UI пока не используется) |
| `isPinned` | INTEGER | нет | `false` | Закрепление |
| `isArchived` | INTEGER | нет | `false` | Архив |
| `sortOrder` | INTEGER | нет | `0` | Ручной порядок |
| `createdAt` | INTEGER | нет | — | Создание |
| `updatedAt` | INTEGER | нет | — | Изменение |

Индексы: `parentId`, `name`.

Иерархия защищена в `BrainRepository`:

* нельзя сделать папку родителем самой себя;
* нельзя переместить папку внутрь собственного потомка (`isDescendant`, обход с ограничением 64 шага);
* удаление папки переносит **только её непосредственные записи** в родительскую папку и удаляет
  саму папку. Дочерние папки не перемещаются и не удаляются — у них остаётся `parentId`
  на уже удалённую папку.

### 2.2 `knowledge_items` — заметки, памятки, ссылки, выдержки, файлы

| Колонка | Тип | Null | По умолчанию | Назначение |
|---|---|---:|---|---|
| `id` | TEXT | нет | — | PK |
| `type` | TEXT | нет | — | `NOTE`, `MEMO`, `LINK`, `EXCERPT`, `FILE` |
| `title` | TEXT | нет | `""` | Необязательный заголовок |
| `content` | TEXT | нет | `""` | Основной текст |
| `folderId` | TEXT | да | `null` | Логическая ссылка на `folders.id` |
| `url` | TEXT | да | `null` | Ссылка |
| `source` | TEXT | да | `null` | Источник (например, package отправителя) |
| `mimeType` | TEXT | да | `null` | MIME |
| `attachmentUri` | TEXT | да | `null` | URI вложения; файлового импорта в UI пока нет |
| `searchText` | TEXT | нет | `""` | Нижний регистр `title + "\n" + content`, пробелы схлопываются, до 20 000 символов |
| `isFavorite` | INTEGER | нет | `false` | Избранное |
| `isPinned` | INTEGER | нет | `false` | Закрепление |
| `isArchived` | INTEGER | нет | `false` | Архив |
| `isDeleted` | INTEGER | нет | `false` | Корзина |
| `deletedAt` | INTEGER | да | `null` | Момент перемещения в корзину |
| `revision` | INTEGER | нет | `1` | Ревизия **контента** для оптимистической блокировки |
| `createdAt` | INTEGER | нет | — | Создание |
| `updatedAt` | INTEGER | нет | — | Изменение |

Индексы: `folderId`, `updatedAt`, `isDeleted`, `type`.

Поиск — не FTS, а `LIKE '%query%'` по `searchText`. Поэтому:

* поиск регистронезависим для латиницы и кириллицы, потому что обе стороны приводятся к нижнему регистру;
* подстрока ищется в любом месте текста, включая середину слова;
* специальные символы `%` и `_` в запросе не экранируются и работают как wildcard SQLite;
* индексы по `searchText` нет, для больших объёмов это линейный скан.

### 2.3 `tags` и `item_tags`

`tags`:

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `name` | TEXT | нет | — |
| `colorKey` | TEXT | да | `null` |
| `createdAt` | INTEGER | нет | — |

Уникальный индекс по `name`. Имена нормализуются в репозитории: trim, удаление `#`, нижний регистр,
максимум 40 символов, не более 20 тегов на запись.

`item_tags` — связь многие-ко-многим:

| Колонка | Тип | Null |
|---|---|---:|
| `itemId` | TEXT | нет |
| `tagId` | TEXT | нет |

Составной PK `(itemId, tagId)`, индекс по `tagId`. Теги, оставшиеся без связей, удаляются только
явным вызовом `deleteOrphanTags()`; автоматически это не происходит.

### 2.4 `tasks` — задачи

| Колонка | Тип | Null | По умолчанию | Назначение |
|---|---|---:|---|---|
| `id` | TEXT | нет | — | PK |
| `title` | TEXT | нет | — | Обязательное; `createTask` ограничивает 300 символами |
| `description` | TEXT | нет | `""` | Описание |
| `status` | TEXT | нет | `INBOX` | `INBOX`, `NEXT`, `IN_PROGRESS`, `WAITING`, `DONE`, `CANCELLED` |
| `priority` | TEXT | нет | `NORMAL` | `LOW`, `NORMAL`, `HIGH`, `URGENT` |
| `dueAt` | INTEGER | да | `null` | Срок, epoch millis |
| `projectId` | TEXT | да | `null` | Логическая ссылка на `projects.id` |
| `parentTaskId` | TEXT | да | `null` | Поле есть, но подзадачи в UI и репозитории не реализованы |
| `completedAt` | INTEGER | да | `null` | Заполняется только при статусе `DONE` |
| `sortOrder` | INTEGER | нет | `0` | Ручной порядок (в UI не используется) |
| `createdAt` | INTEGER | нет | — | Создание |
| `updatedAt` | INTEGER | нет | — | Изменение |
| `isDeleted` | INTEGER | нет | `false` | Удаление |

Индексы: `status`, `dueAt`, `projectId`.

Особенности:

* `setStatus` выставляет `completedAt = now` только для `DONE`; для `CANCELLED` и остальных — `null`;
* `toggleDone` переключает `DONE` ↔ `NEXT`, а не в исходный статус;
* удаление — soft delete, но `deletedAt` нет и восстановления из UI нет;
* при удалении проекта ссылки задач обнуляются (`clearProject`), задачи остаются.

### 2.5 `projects` — проекты

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `name` | TEXT | нет | — |
| `description` | TEXT | нет | `""` |
| `goal` | TEXT | нет | `""` |
| `status` | TEXT | нет | `ACTIVE` |
| `priority` | TEXT | нет | `NORMAL` |
| `startAt` | INTEGER | да | `null` |
| `dueAt` | INTEGER | да | `null` |
| `colorKey` | TEXT | да | `null` |
| `isArchived` | INTEGER | нет | `false` |
| `createdAt` | INTEGER | нет | — |
| `updatedAt` | INTEGER | нет | — |

Индекс: `status`. Прогресс проекта в UI считается по реальным задачам
(`done / total`), а не хранится в таблице.

### 2.6 `reminders` — напоминания

| Колонка | Тип | Null | По умолчанию | Назначение |
|---|---|---:|---|---|
| `id` | TEXT | нет | — | PK |
| `title` | TEXT | нет | — | Обязательное |
| `description` | TEXT | нет | `""` | Текст уведомления |
| `nextTriggerAt` | INTEGER | нет | — | Следующее срабатывание, epoch millis |
| `timeZoneId` | TEXT | нет | — | IANA-зона |
| `allDay` | INTEGER | нет | `false` | Поле есть; редактор всегда сохраняет `false` |
| `recurrenceRule` | TEXT | да | `null` | JSON `RecurrenceRule`; `null` — один раз |
| `status` | TEXT | нет | `SCHEDULED` | См. статусы ниже |
| `linkedEntityId` | TEXT | да | `null` | Логическая связь с задачей/заметкой |
| `linkedEntityType` | TEXT | да | `null` | Тип связанной сущности |
| `notificationId` | INTEGER | нет | `0` | Request code и ID уведомления |
| `lastFiredAt` | INTEGER | да | `null` | Последнее срабатывание |
| `revision` | INTEGER | нет | `1` | Инкрементируется, но не проверяется при записи |
| `createdAt` | INTEGER | нет | — | Создание |
| `updatedAt` | INTEGER | нет | — | Изменение |

Индексы: `status`, `nextTriggerAt`.

Статусы: `SCHEDULED`, `NOT_SCHEDULED`, `SNOOZED`, `FIRED`, `DONE`, `CANCELLED`, `MISSED`.
Активными для перепланирования считаются `SCHEDULED`, `SNOOZED`, `NOT_SCHEDULED`.

### 2.7 `reminder_occurrences` — материализованные срабатывания

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `reminderId` | TEXT | нет | — |
| `scheduledAt` | INTEGER | нет | — |
| `firedAt` | INTEGER | да | `null` |
| `state` | TEXT | нет | `"PENDING"` |
| `snoozedUntil` | INTEGER | да | `null` |

Индекс: `reminderId`. Запись создаётся только при фактическом срабатывании
(`recordOccurrence(..., "FIRED")`). Поле `snoozedUntil` в текущем коде не заполняется.
История ограничена 20 последними записями в запросе `observeOccurrences`.

### 2.8 `inbox_items` — входящие материалы

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `kind` | TEXT | нет | — |
| `title` | TEXT | нет | `""` |
| `rawText` | TEXT | нет | `""` |
| `url` | TEXT | да | `null` |
| `mimeType` | TEXT | да | `null` |
| `localUri` | TEXT | да | `null` |
| `sourcePackage` | TEXT | да | `null` |
| `status` | TEXT | нет | `"NEW"` |
| `errorMessage` | TEXT | да | `null` |
| `createdAt` | INTEGER | нет | — |
| `processedAt` | INTEGER | да | `null` |

Индексы: `status`, `createdAt`. `kind`: `TEXT`, `LINK`, `IMAGE`, `PDF`, `FILE`.
`status`: `NEW`, `TRIAGED`, `FAILED`.

Локальная копия файла лежит в `filesDir/inbox/<id>_<sanitized-name>`; `localUri` — это
`file://`-URI. При удалении Inbox-записи файл удаляется. При сбое копирования создаётся
запись `FAILED` с `errorMessage`, а не фиктивное «Сохранено».

### 2.9 `conversations` и `chat_messages`

`conversations`:

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `title` | TEXT | нет | — |
| `providerId` | TEXT | да | `null` |
| `model` | TEXT | да | `null` |
| `isArchived` | INTEGER | нет | `false` |
| `createdAt` | INTEGER | нет | — |
| `updatedAt` | INTEGER | нет | — |

Индекс: `updatedAt`. Заголовок «Новый диалог» заменяется первыми 48 символами первого
сообщения пользователя; ручное переименование ограничено 120 символами.

`chat_messages`:

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `conversationId` | TEXT | нет | — |
| `role` | TEXT | нет | — |
| `content` | TEXT | нет | — |
| `createdAt` | INTEGER | нет | — |
| `toolName` | TEXT | да | `null` |
| `toolStatus` | TEXT | да | `null` |
| `metaJson` | TEXT | да | `null` |

Индекс: `conversationId`. В текущем UI роль используется только `user`/`assistant`;
`toolName`, `toolStatus`, `metaJson` нигде не заполняются. Удаление диалога удаляет его
сообщения, но **не** созданные из него задачи/заметки.

### 2.10 `ai_providers` — провайдеры Грауса

| Колонка | Тип | Null | По умолчанию | Назначение |
|---|---|---:|---|---|
| `id` | TEXT | нет | — | PK |
| `name` | TEXT | нет | — | Отображаемое имя |
| `kind` | TEXT | нет | — | ID пресета (`openai`, `deepseek`, `groq`, `openrouter`, `custom`) |
| `baseUrl` | TEXT | нет | — | Только HTTPS |
| `model` | TEXT | нет | — | Имя модели |
| `secretAlias` | TEXT | нет | — | Ключ в `EncryptedSharedPreferences`, **не сам секрет** |
| `isDefault` | INTEGER | нет | `false` | Провайдер по умолчанию |
| `enabled` | INTEGER | нет | `true` | Включён |
| `createdAt` | INTEGER | нет | — | Создание |
| `updatedAt` | INTEGER | нет | — | Изменение |

Индекс: `kind`. Репозиторий разрешает только `https://`, обрезает завершающий `/`,
при создании требует непустой ключ и сохраняет его в `SecretStore`. В БД секрета нет.

### 2.11 `action_log` — журнал действий

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `actor` | TEXT | нет | — |
| `tool` | TEXT | нет | — |
| `status` | TEXT | нет | — |
| `summary` | TEXT | нет | — |
| `targetIds` | TEXT | нет | `""` |
| `createdAt` | INTEGER | нет | — |

Индекс: `createdAt`. `targetIds` — ID через запятую; `summary` обрезается до 500 символов.
Журнал пишут `GrausEngine` и проверка подключения в AI Lab. Полные тексты заметок и
промпты туда не копируются, но summary может содержать название созданной сущности.

### 2.12 `saved_memory` — сохранённая память

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `key` | TEXT | нет | — |
| `value` | TEXT | нет | — |
| `isSensitive` | INTEGER | нет | `false` |
| `createdAt` | INTEGER | нет | — |
| `updatedAt` | INTEGER | нет | — |

Индекс: `key`. Экран «Память Грауса» (`ui/screens/memory`) позволяет просматривать, добавлять,
редактировать и удалять факты; флаг `isSensitive` помечает записи, которые не следует отправлять
во внешний контекст.

### 2.13 `automation_rules` — сценарии Autopilot

| Колонка | Тип | Null | По умолчанию |
|---|---|---:|---|
| `id` | TEXT | нет | — |
| `name` | TEXT | нет | — |
| `triggerType` | TEXT | нет | `MANUAL` |
| `actionType` | TEXT | нет | — |
| `configJson` | TEXT | нет | `{}` |
| `enabled` | INTEGER | нет | `true` |
| `requiresConfirmation` | INTEGER | нет | `true` |
| `lastRunAt` | INTEGER | да | `null` |
| `lastRunStatus` | TEXT | да | `null` |
| `lastRunMessage` | TEXT | да | `null` |
| `createdAt` | INTEGER | нет | — |
| `updatedAt` | INTEGER | нет | — |

Допустимые `actionType`: `CREATE_TASK`, `CREATE_NOTE`, `DAILY_SUMMARY`. `configJson`
валидируется в `domain/automation/AutomationActions` до исполнения; неизвестное действие
отклоняется. Триггеры `SCHEDULE`/`EVENT` объявлены константами, но в этой версии не
срабатывают — запуск только ручной.

## 3. Связи (логическая карта)

```text
folders.parentId ─────────────► folders.id            (дерево, без FK)
knowledge_items.folderId ─────► folders.id
item_tags.itemId ─────────────► knowledge_items.id
item_tags.tagId ──────────────► tags.id
tasks.projectId ──────────────► projects.id
tasks.parentTaskId ───────────► tasks.id               (поле не используется)
reminders.linkedEntityId ─────► tasks.id / knowledge_items.id (полиморфно, без FK)
reminder_occurrences.reminderId ► reminders.id
chat_messages.conversationId ─► conversations.id
ai_providers.secretAlias ─────► EncryptedSharedPreferences (вне Room)
```

## 4. Мягкое удаление и корзина

| Сущность | Механизм | Восстановление | Где видно |
|---|---|---|---|
| `knowledge_items` | `isDeleted=1`, `deletedAt=now` | Да, `restoreFromTrash` | Экран «Корзина» |
| `tasks` | `isDeleted=1` | **Нет API** | Нигде; задача просто исчезает из списков |
| `folders` | физическое `DELETE` | Нет | — |
| `projects` | физическое `DELETE`, задачи теряют `projectId` | Нет | — |
| `reminders` | физическое `DELETE` | Нет | — |
| `inbox_items` | физическое `DELETE` + удаление файла | Нет | — |
| `conversations` | физическое `DELETE` сообщений и диалога | Нет | — |

Корзина существует **только для записей Second Brain**. Очистка корзины удаляет все
`knowledge_items` с `isDeleted=1` безвозвратно. Автоматического удаления по
`trashRetentionDays` нет: настройка сохраняется, но нигде не читается.

## 5. Ревизии и оптимистическая блокировка

| Сущность | Поле | Кто меняет | Проверка конфликта |
|---|---|---|---|
| `knowledge_items` | `revision` | `saveItem` (+1), `setTitleAndType` (+1) | `saveItem(expectedRevision)` возвращает `ConflictException`, если `existing.revision > expectedRevision` |
| `reminders` | `revision` | `update`, `snooze`, `complete`, DAO `markFired`/`reschedule` (+1) | Нет: `update` не принимает ожидаемую ревизию |
| `tasks`, `projects` | — | — | Нет |

Тонкости `knowledge_items`:

* метаданные (папка, избранное, закрепление, архив) **не** увеличивают `revision` — чтобы
  смена папки не превращала параллельный автосейв в ложный конфликт;
* `updateItem` не увеличивает `revision` вообще;
* `saveItem` при отсутствии строки создаёт её заново с `revision = 1` (например, если запись
  успели окончательно удалить);
* `saveItem` не пишет строку, если title и content не изменились, и возвращает текущую ревизию.

`ConflictException` наследуется от `IllegalStateException` и содержит `currentRevision`.
UI редактора показывает «Запись изменена в другом месте», сохраняя текст в черновик.

## 6. Политика миграций

* `@Database(version = 2, exportSchema = true)`.
* В `WmDatabase.build()` **нет** `fallbackToDestructiveMigration()` и нет
  `fallbackToDestructiveMigrationOnDowngrade()`. Это осознанное решение: потеря данных при
  обновлении запрещена.
* KSP экспортирует схему в `app/schemas` (`room.schemaLocation`). После успешной
  сборки там лежат `1.json` и `2.json`; эти файлы нужно коммитить вместе с изменениями схемы.
* Реализована миграция `MIGRATION_1_2` (`data/db/Migrations.kt`): создаёт таблицу
  `automation_rules`. SQL сверен с `2.json`, сгенерированным Room, но **не проверен на
  устройстве с базой v1** — это остаётся сделать при первом обновлении.
* При следующем изменении схемы обязательны:
  1. увеличить `version`;
  2. написать `Migration(old, new)` с явным SQL;
  3. зарегистрировать её в `WmMigrations.all`;
  4. приложить экспортированную схему и тест миграции;
  5. проверить обновление на копии реальной базы, а не только чистую установку.

## 7. Ограничения и известные расхождения

1. Нет foreign keys и каскадов; целостность связей — ответственность кода.
2. `SavedMemoryEntity` подключена к экрану «Память Грауса», но в контекст запросов к модели пока
   не подмешивается.
3. `tasks.parentTaskId`, `folders.colorKey`, `projects.startAt/colorKey`,
   `reminders.allDay`, `reminders.linkedEntity*`, `reminder_occurrences.snoozedUntil`
   существуют в схеме, но не имеют пользовательских сценариев.
4. Теги не участвуют в фильтрации списка и не экспортируются в JSON.
5. В экспорт JSON не попадают: теги, `item_tags`, вложения, история срабатываний,
   диалоги, провайдеры, журнал, память и настройки. Подробнее — в `docs/BACKUP_FORMAT.md`.
6. Задача, удалённая soft delete, не имеет `deletedAt` и не восстанавливается.
7. Удаление папки оставляет её подпапки с `parentId` на несуществующую запись.
