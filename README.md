# WM OS

Локально-ориентированное Android-приложение для заметок, задач, проектов и напоминаний
с необязательным ИИ-помощником **Граус** (Graus). Основные данные хранятся на устройстве;
без настроенного AI-провайдера работают заметки, поиск, задачи, напоминания, Inbox и экспорт.

> **Статус:** ранняя версия `0.1.0`. Часть функций реализована только на уровне данных
> или отсутствует. Ниже перечислено фактическое состояние, а не продуктовые обещания.

## 1. Статус модулей

| Модуль | Статус | Что реально есть | Ограничения |
|---|---|---|---|
| Локальная БД | ✅ | Room 2.6.1, 15 таблиц, schema v2, WAL, миграция 1→2, ручные репозитории | Нет FK-каскадов, нет шифрования БД |
| Second Brain | ✅ | Заметки/памятки/ссылки, папки (с защитой от циклов), теги, избранное, закрепление, архив, корзина, поиск, автосохранение, черновики, вложение файла к записи | Нет OCR, семантики, FTS и фильтра по тегам |
| Задачи | ✅ | Статусы, приоритеты, срок, проекты, soft delete, представления, отдельный экран задачи с редактированием, Undo после завершения | Нет подзадач, тегов задач, восстановления удалённых задач |
| Проекты | ✅ | Название, цель, описание, прогресс по реальным задачам, экран проекта | Нет этапов, редактирования проекта из UI, связанных заметок |
| Напоминания | ✅ | Одноразовые и повторяющиеся, естественный язык, AlarmManager, точные/неточные будильники, действия из уведомления, boot/timezone reschedule | Нет all-day, deep link в напоминание, надёжного продолжения серии после snooze, тестов планировщика |
| Inbox / Android Share | ✅ | Приём текста, URL, изображений и PDF; копирование в private storage; ручное добавление; статусы NEW/TRIAGED/FAILED | Нет извлечения текста, OCR и классификации Граусом; SAF-импорт файлов есть |
| AI Lab | ✅ | Свои OpenAI-совместимые провайдеры (4 пресета в диалоге + произвольный HTTPS URL), проверка подключения, маска ключа, Keystore-шифрование | Нет назначения моделей под задачи, автопереключения, лимитов/квот, стриминга |
| Граус | ✅ | Чат, 7 инструментов, подтверждение создающих действий, журнал, история диалогов, память фактов (просмотр/правка/удаление) | Нет режимов, изменения/удаления сущностей инструментами, стриминга, отмены |
| Autopilot | ✅ | Сценарии: ручной запуск, 3 разрешённых действия (сводка / задача / заметка), подтверждение, журнал, защита от повторного запуска | Нет триггеров по расписанию и событиям; фоновая работа не реализована |
| WM Files | ✅ | Вложения к записям: выбор через SAF, копия в приватное хранилище, открытие через FileProvider, удаление копии, экран со списком вложений | Нет OCR, извлечения текста из PDF, поиска дубликатов и пакетной обработки |
| WM Quick | ✅ | Генератор паролей (SecureRandom), безопасный калькулятор, конвертер единиц | Нет шаблонов, списков покупок, чек-листов, истории |
| Экспорт / backup | ✅ | JSON и Markdown через SAF; импорт с предпросмотром, режимы «объединить / как новые / заменить», одна транзакция | Нет checksum/manifest/шифрования архива, вложений и тегов |
| Безопасность | ✅/⚠️ частично | API-ключи в EncryptedSharedPreferences + Keystore, системный backup отключён, HTTPS-only, **блокировка PIN/биометрией с авто-блокировкой в фоне** | **Нет полного шифрования БД**, `FLAG_SECURE`, certificate pinning |
| Настройки | ⚠️ частично | Тема, ссылка на настройки уведомлений, About | «Компактные списки», «Отправлять текст заметок», «Автопереключение» сохраняются, но не применяются |
| Навигация и экраны | ✅ | 5 вкладок, 23 маршрута, Compose-экраны, общие состояния, глобальный поиск по заметкам/задачам/напоминаниям/проектам/Inbox | Нет отдельного экрана WM Files |
| Тесты | ✅ | 46 unit-тестов (recurrence, parser, quick tools, automation), все проходят | Нет instrumentation/Compose-тестов, тестов БД, AI, backup, уведомлений |
| Сборка | ✅ | `gradlew.bat :app:assembleDebug` — `BUILD SUCCESSFUL`, debug APK 21 МБ | Release-сборка не проверялась, приложение не запускалось на устройстве |

## 2. Стек и версии

| Компонент | Версия / значение |
|---|---|
| Язык | Kotlin 2.0.21 |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 26 |
| Java / JVM target | 17 |
| UI | Jetpack Compose, Compose BOM `2024.10.01`, Material 3 |
| Навигация | `navigation-compose` 2.8.4 |
| БД | Room 2.6.1 (`room-runtime`, `room-ktx`), KSP `2.0.21-1.0.28` |
| Настройки | DataStore Preferences 1.1.1 |
| Секреты | `androidx.security:security-crypto:1.1.0-alpha06` |
| Биометрия | `androidx.biometric:biometric:1.1.0` |
| Сеть | OkHttp 4.12.0 |
| Сериализация | kotlinx.serialization-json 1.7.3 |
| Корутины | kotlinx-coroutines-android 1.9.0 |
| Фоновые задачи | Напоминания — через AlarmManager; WorkManager 2.9.1 объявлен в зависимостях, но `Worker` нет |
| Тесты | JUnit 4.13.2, coroutines-test 1.9.0, Robolectric 4.13 (не используется) |
| Стиль | ktlint 1.2.1 (`org.jlleitschuh.gradle.ktlint` 12.1.1), baseline в `app/ktlint-baseline.xml` |
| versionName / versionCode | `0.1.0` / `1` |
| applicationId | `dev.wmos` (debug: `dev.wmos.debug`) |

## 3. Сборка

Требования: JDK 17+, Android SDK 35 (platform + build-tools), Gradle 8.9.

```powershell
gradlew.bat :app:assembleDebug
```

Wrapper зафиксирован в дереве (`gradlew`, `gradlew.bat`, `gradle/wrapper/` на Gradle 8.9),
скачивать Gradle отдельно не нужно — достаточно JDK и Android SDK.

```powershell
gradlew.bat :app:testDebugUnitTest
```

`local.properties` должен содержать `sdk.dir` (файл в `.gitignore`, в репозиторий не
попадает).

### 3.1 Фактический статус сборки

Последняя проверка (`build4.log`) — сборка и unit-тесты:

```text
> Task :app:testDebugUnitTest
> Task :app:assembleDebug
BUILD SUCCESSFUL in 3m 25s
45 actionable tasks: 21 executed, 24 up-to-date
```

До этого успешно проходили сборка через wrapper (`build3.log`, 1m 37s) и через
установленный Gradle (`build2.log`, 3m 32s). Артефакт:
`app/build/outputs/apk/debug/app-debug.apk` (~21 МБ). Самый первый прогон в
`build.log` (15:17) падал на четырёх ошибках компиляции; они были исправлены, после
чего сборка стабильно проходит. Ошибки в текущем исходном состоянии не воспроизводятся.

## 4. Тесты

```powershell
gradlew.bat :app:testDebugUnitTest
```

46 unit-тестов в четырёх классах:

| Файл | Что проверяет |
|---|---|
| `RecurrenceCalculatorTest` | daily/weekly/monthly, 31-е, SKIP/CLAMP, 29 февраля, DST 09:00, interval, encode/decode |
| `NaturalReminderParserTest` | «завтра в 18:30», «через 45 минут», «каждый понедельник», «каждый день», неоднозначность, диапазон, месячное правило |
| `QuickToolsTest` | приоритет операций, скобки, деление на ноль, SecureRandom-пароль, конвертер |
| `AutomationActionsTest` | валидация конфигов Autopilot, отказ на неизвестном действии и битом JSON, отсутствие JSON в описании |

Instrumentation-тестов (`app/src/androidTest`) и Compose UI-тестов нет. Robolectric
объявлен в зависимостях, но ни одного теста с ним нет.

Последний прогон (в составе `build10.log`) — 46 тестов, 0 failures:

```text
AutomationActionsTest:      9 tests, 0 failures
NaturalReminderParserTest: 11 tests, 0 failures
QuickToolsTest:            14 tests, 0 failures
RecurrenceCalculatorTest:  12 tests, 0 failures
```

## 5. Структура проекта

```text
WM OS/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/dev/wmos/
│       │   │   ├── MainActivity.kt          # theme + WmApp + share intent
│       │   │   ├── WmApplication.kt         # AppContainer, seed, notification channel
│       │   │   ├── ai/                      # AiClient, GrausEngine, ToolRegistry
│       │   │   ├── data/
│       │   │   │   ├── db/                  # WmDatabase, DAO, Entity
│       │   │   │   ├── prefs/               # Settings, SecretStore, DraftStore
│       │   │   │   └── repo/                # Brain, Task, Reminder, Inbox, Chat, …
│       │   │   ├── domain/
│       │   │   │   ├── quick/               # SafeExpression, PasswordGenerator, UnitConverter
│       │   │   │   └── reminder/            # Recurrence, parser, scheduler interface
│       │   │   ├── reminders/               # AlarmManager, receivers, notifications
│       │   │   └── ui/
│       │   │       ├── components/          # Common.kt
│       │   │       ├── nav/                 # Routes, WmApp
│       │   │       ├── screens/             # home, brain, note, plan, reminders, graus, …
│       │   │       └── theme/               # Color, Type, Theme
│       │   └── res/                         # manifest resources, иконки, strings
│       └── test/java/dev/wmos/domain/       # unit-тесты
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── docs/
└── tools/spec.txt                           # исходное ТЗ (в .gitignore)
```

## 6. Ключевые технические решения

* **Ручной DI** через `AppContainer`; Hilt/Koin не используются.
* **UDF**: `ViewModel` + `StateFlow` + `collectAsStateWithLifecycle`.
* **Room без destructive fallback**: потеря данных при обновлении схемы запрещена.
* **Секреты отдельно от БД**: в Room только alias, сам ключ — в Keystore-хранилище.
* **Напоминания — отдельная сущность**, а не поле задачи.
* **Двухфазное сохранение напоминания**: запись → попытка поставить будильник → честный
  статус `NOT_SCHEDULED` при отказе.
* **Подтверждение действий Грауса**: модель предлагает, приложение перечитывает результат.

## 7. Разрешения

| Разрешение | Зачем |
|---|---|
| `POST_NOTIFICATIONS` | Уведомления напоминаний |
| `SCHEDULE_EXACT_ALARM` | Точные будильники |
| `RECEIVE_BOOT_COMPLETED` | Восстановление расписания после reboot |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Запросы к выбранному AI-провайдеру |
| `VIBRATE` | Вибрация уведомлений |
| `USE_BIOMETRIC` | Объявлено, но биометрия **не реализована** |

Нет доступа к контактам, SMS, звонкам, геолокации, Accessibility, фоновой записи аудио.
Нет аналитики, рекламных SDK и скрытой телеметрии.

## 8. Ограничения версии 0.1.0

1. **Release-сборка и релизная подпись не проверялись.** Debug-сборка проходит, Gradle
   Wrapper 8.9 зафиксирован в дереве.
2. **Импорт есть, но не проверен на чистом профиле** — реализованы предпросмотр, режимы «объединить / как новые / заменить» и транзакционная запись; ручной прогон restore на чистой установке остаётся за вами.
3. **Нет полного шифрования БД/вложений/черновиков**; API-ключи защищены, остальное — нет.
4. **Нет блокировки приложения** PIN/биометрией; `USE_BIOMETRIC` не используется.
5. **Нет OCR, семантического поиска, локальных моделей и фоновых автоматизаций** (Autopilot запускается вручную).
6. **Приложение не запускалось на устройстве/эмуляторе** в этой среде: проверены сборка
   и unit-тесты, ручной прогон на телефоне остаётся за вами.
7. Настройки «компактные списки», «отправлять текст заметок», «автопереключение»
   сохраняются, но не имеют эффекта.
8. Уведомление открывает приложение, но не конкретное напоминание.
9. После snooze повторяющаяся серия не продолжается автоматически без действия
   «Выполнено».
10. Нет тестов AI, БД, backup, уведомлений и Compose UI. CI-workflow (`.github/workflows/android.yml`) и локальный `scripts/check-secrets.ps1` добавлены, но в этой среде не запускались.

## 9. Документация

* [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — слои, DI, навигация, потоки.
* [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) — все таблицы и правила.
* [`docs/AI_TOOLS.md`](docs/AI_TOOLS.md) — инструменты, подтверждения, ошибки API.
* [`docs/REMINDERS.md`](docs/REMINDERS.md) — повторения, парсер, AlarmManager.
* [`docs/BACKUP_FORMAT.md`](docs/BACKUP_FORMAT.md) — текущий экспорт и спецификация restore.
* [`docs/UX_UI_SPEC.md`](docs/UX_UI_SPEC.md) — палитра, типографика, экраны, доступность.
* [`SECURITY.md`](SECURITY.md) — модель угроз и что не защищено.
* [`CONTRIBUTING.md`](CONTRIBUTING.md) — сборка, стиль, миграции, секреты.
* [`CHANGELOG.md`](CHANGELOG.md) — что вошло в 0.1.0.

## 10. Лицензия

MIT. См. [`LICENSE`](LICENSE). Copyright (c) 2026 WM OS contributors.

Проект использует сторонние библиотеки (AndroidX, Compose, Room, OkHttp,
kotlinx.serialization) на условиях их собственных лицензий; их тексты не копируются
в репозиторий.
