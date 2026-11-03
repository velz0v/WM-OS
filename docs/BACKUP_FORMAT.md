# Формат экспорта и будущего backup

## 1. Текущее состояние: экспорт есть, импорта нет

`BackupRepository` (`data/repo/BackupRepository.kt`) реализует **только экспорт**:

* полный JSON — через `ActivityResultContracts.CreateDocument("application/json")`;
* Markdown — через `CreateDocument("text/markdown")`.

Импорт, восстановление, проверка контрольных сумм и слияние конфликтов **не реализованы**.
Это явно написано на экране «Экспорт и backup»: «Импорт пока не реализован». Нельзя
называть созданный JSON «резервной копией, проверенной восстановлением»: тестового
restore на чистом профиле нет.

Системный Android backup также отключён: `android:allowBackup="false"`,
`android:fullBackupContent="false"`, а `res/xml/data_extraction_rules.xml` исключает
`root`, `file`, `database`, `sharedpref` и `external` для cloud-backup и device-transfer.
Поэтому единственный способ вынести данные — ручной экспорт, который пользователь
кладёт в выбранное им место.

## 2. Полный JSON: что реально выгружается

Имя файла, предлагаемое в UI: `wm-os-backup.json`. Кодировка — UTF-8, без pretty-print.

### 2.1 Корневые поля

| Поле | Тип | Пример | Примечание |
|---|---|---|---|
| `schemaVersion` | number | `1` | Версия формата, а не версия БД |
| `app` | string | `"WM OS"` | Константа |
| `appVersion` | string | `"0.1.0"` | Захардкожена в репозитории |
| `exportedAt` | string | `"2026-10-29T12:00:00Z"` | ISO-8601 Instant |
| `folders` | array | `[]` | Все папки, включая архивные |
| `items` | array | `[]` | Только `isDeleted = 0` |
| `tasks` | array | `[]` | Только `isDeleted = 0` |
| `projects` | array | `[]` | Все проекты, включая архивные |
| `reminders` | array | `[]` | Все напоминания, включая `DONE`/`CANCELLED`/`MISSED` |

Пример скелета:

```json
{
  "schemaVersion": 1,
  "app": "WM OS",
  "appVersion": "0.1.0",
  "exportedAt": "2026-10-29T15:20:00Z",
  "folders": [],
  "items": [],
  "tasks": [],
  "projects": [],
  "reminders": []
}
```

### 2.2 `folders[]`

| Поле | Тип | Как формируется |
|---|---|---|
| `id` | string | UUID |
| `name` | string | Как есть |
| `description` | string | Как есть |
| `parentId` | string | `null` превращается в `""` |
| `isPinned` | boolean | — |
| `isArchived` | boolean | — |
| `createdAt` | number | epoch millis |
| `updatedAt` | number | epoch millis |

Не выгружаются: `colorKey`, `sortOrder`.

### 2.3 `items[]`

| Поле | Тип | Как формируется |
|---|---|---|
| `id` | string | UUID |
| `type` | string | `NOTE`/`MEMO`/`LINK`/`EXCERPT`/`FILE` |
| `title` | string | Как есть |
| `content` | string | Полный текст |
| `folderId` | string | `null` → `""` |
| `url` | string | `null` → `""` |
| `source` | string | `null` → `""` |
| `isFavorite` | boolean | — |
| `isPinned` | boolean | — |
| `isArchived` | boolean | — |
| `createdAt` | number | epoch millis |
| `updatedAt` | number | epoch millis |

Не выгружаются: `searchText` (восстанавливается из title+content),
`mimeType`, `attachmentUri`, `revision`, `isDeleted`, `deletedAt`.
Записи из корзины в экспорт не попадают; вложения не копируются.

### 2.4 `tasks[]`

| Поле | Тип | Как формируется |
|---|---|---|
| `id` | string | UUID |
| `title` | string | — |
| `description` | string | — |
| `status` | string | `INBOX`/`NEXT`/`IN_PROGRESS`/`WAITING`/`DONE`/`CANCELLED` |
| `priority` | string | `LOW`/`NORMAL`/`HIGH`/`URGENT` |
| `dueAt` | number | `null` → `0` (неотличимо от 1970-01-01) |
| `projectId` | string | `null` → `""` |
| `createdAt` | number | epoch millis |
| `updatedAt` | number | epoch millis |

Не выгружаются: `parentTaskId`, `completedAt`, `sortOrder`, `isDeleted`.

### 2.5 `projects[]`

| Поле | Тип | Как формируется |
|---|---|---|
| `id` | string | UUID |
| `name` | string | — |
| `description` | string | — |
| `goal` | string | — |
| `status` | string | `ACTIVE` и любые другие значения из БД |
| `dueAt` | number | `null` → `0` |
| `createdAt` | number | epoch millis |

Не выгружаются: `updatedAt`, `priority`, `startAt`, `colorKey`, `isArchived`.
Восстановление из такого JSON потеряет часть метаданных, даже если импорт появится.

### 2.6 `reminders[]`

| Поле | Тип | Как формируется |
|---|---|---|
| `id` | string | UUID |
| `title` | string | — |
| `description` | string | — |
| `nextTriggerAt` | number | epoch millis |
| `timeZoneId` | string | IANA-зона |
| `recurrenceRule` | string | JSON-строка правила или `""` |
| `status` | string | `SCHEDULED`/`NOT_SCHEDULED`/`SNOOZED`/`FIRED`/`DONE`/`CANCELLED`/`MISSED` |
| `allDay` | boolean | В текущем UI всегда `false` |

Не выгружаются: `notificationId`, `linkedEntityId`, `linkedEntityType`, `lastFiredAt`,
`revision`, `createdAt`, `updatedAt`. История `reminder_occurrences` не выгружается.
После импорта потребуется заново назначить `notificationId` и перепланировать будильники.

### 2.7 Чего в JSON нет вообще

* тегов и связей `item_tags`;
* вложений и файлов Inbox;
* истории срабатываний;
* диалогов и сообщений Грауса;
* провайдеров и, разумеется, API-ключей (в БД только `secretAlias`; он не выгружается);
* журнала действий;
* сохранённой памяти;
* настроек DataStore;
* черновиков редактора;
* корзины.

Секреты не попадают в экспорт по построению: `secretAlias` не включён в `AiProviderEntity`
экспорт, потому что таблица провайдеров вообще не выгружается. Это не то же самое, что
шифрование экспорта: сам JSON — открытый текст.

## 3. Markdown-экспорт

`exportMarkdown(uri)` выгружает только `knowledge_items` с `isDeleted = 0`:

```markdown
# WM OS — экспорт заметок

Дата выгрузки: 2026-10-29T12:00:00Z

## Заголовок (или первая строка до 60 символов, или «Без названия»)

- Тип: NOTE
- Изменено: 2026-10-29T11:00:00Z
- Ссылка: https://example.com     ← только если url не пуст

Полный текст заметки

---
```

Папки, теги, флаги, вложения и задачи в Markdown не выгружаются. `ExportSummary`
для Markdown возвращает `tasks = 0`, `reminders = 0`; это не ошибка, а неучастие
этих сущностей в формате.

## 4. Запись файла и ограничения

* используется SAF (`ContentResolver.openOutputStream(uri)`), приложение не пишет
  в публичные папки само;
* нет временного файла и атомарной замены: при обрыве записи целевой файл может остаться
  частичным;
* нет checksum, манифеста, сжатия и шифрования;
* весь JSON строится в памяти (`buildString`/`buildJsonObject`), поэтому очень большая
  база может привести к нехватке памяти;
* размер в килобайтах в UI считается как `bytes / 1024`, то есть округляется вниз;
* `ExportSummary.tasks`/`reminders` для Markdown равны нулю, а для JSON — реальным количествам;
* исключения записи ловятся в `runCatching`, UI показывает «Ошибка» и текст сообщения.

## 5. Спецификация следующей итерации (не реализовано)

Ниже — предложение формата **backup**, а не описание текущего кода. Его нельзя
реализовывать частично и объявлять готовым без теста восстановления на чистом профиле.

### 5.1 Контейнер

```text
wm-os-backup-20261029-152000.zip
├── manifest.json
├── data/
│   ├── folders.json
│   ├── items.json
│   ├── tags.json
│   ├── tasks.json
│   ├── projects.json
│   ├── reminders.json
│   ├── occurrences.json
│   ├── conversations.json
│   └── messages.json
└── attachments/
    └── <itemId>/<sha256>_<filename>
```

`manifest.json`:

```json
{
  "format": "wm-os-backup",
  "formatVersion": 2,
  "schemaVersion": 1,
  "appVersion": "0.2.0",
  "createdAt": "2026-10-29T15:20:00Z",
  "counts": {
    "items": 120,
    "tasks": 34,
    "reminders": 7,
    "attachments": 3
  },
  "checksums": {
    "data/items.json": "sha256:…",
    "attachments/…": "sha256:…"
  },
  "encrypted": false,
  "excludesSecrets": true
}
```

### 5.2 Обязательные требования к импорту

1. **Ничего не менять до валидации.** Сначала распаковать во временный каталог, проверить
   `formatVersion`, `schemaVersion`, JSON-схему, размеры, количество файлов, checksum.
2. **Защита путей.** Запретить `..`, абсолютные пути, symlink-ловушки и превышение
   распакованного объёма/числа файлов (zip bomb).
3. **Секреты не импортировать.** API-ключи, пароли и токены не входят в backup по умолчанию;
   после восстановления пользователь заново вводит ключ.
4. **Предпросмотр и конфликты.** Показать количество объектов, папки назначения и конфликты
   по ID. Варианты: объединить, импортировать отдельно, заменить — каждый с отдельным
   подтверждением. Перед заменой предложить сохранить текущую копию.
5. **Транзакция/staging.** Импорт применять одной транзакцией или из staging-базы; при сбое
   текущие данные не должны стать частично перезаписанными.
6. **Восстановление ID и ссылок.** Сохранять строковые ID, чтобы не ломать
   `folderId`/`projectId`/`linkedEntityId`; для конфликтов ID предусмотреть явную политику.
7. **Отчёт.** Показать, что восстановлено, что пропущено и что не удалось; не писать «Готово»
   при частичном успехе.
8. **Проверка.** Обязательный тест: экспорт → полное удаление приложения/данных → импорт
   на чистом профиле → сверка заметок, задач, напоминаний, тегов и вложений.

### 5.3 Версионирование

* `formatVersion` описывает контейнер; `schemaVersion` — структуру данных;
* неизвестная мажорная версия → отказ с понятным сообщением, без «попробовать всё равно»;
* минорные поля с неизвестными ключами игнорируются, но не теряются молча при повторном
  экспорте (по возможности сохранять их в `extra`).

## 6. Что проверено, а что нет

| Проверка | Статус |
|---|---|
| JSON-экспорт собирает папки/записи/задачи/проекты/напоминания | Да, по коду |
| Markdown-экспорт только живых заметок | Да, по коду |
| Импорт/restore | **Нет реализации** |
| Checksum/manifest/шифрование backup | **Нет** |
| Вложения в backup | **Нет** |
| Тест «экспорт → восстановление на чистом профиле» | **Нет** |
| Автоматическая проверка целостности JSON | **Нет** |
