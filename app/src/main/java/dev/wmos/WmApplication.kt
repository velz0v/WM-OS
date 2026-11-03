package dev.wmos

import android.app.Application
import dev.wmos.data.db.WmDatabase
import dev.wmos.data.prefs.SecretStore
import dev.wmos.data.prefs.SettingsRepository
import dev.wmos.data.repo.ActionLogRepository
import dev.wmos.data.repo.BrainRepository
import dev.wmos.data.repo.ChatRepository
import dev.wmos.data.repo.InboxRepository
import dev.wmos.data.repo.ProviderRepository
import dev.wmos.data.repo.ReminderRepository
import dev.wmos.data.repo.TaskRepository
import dev.wmos.domain.reminder.ReminderScheduler
import dev.wmos.reminders.AlarmReminderScheduler
import dev.wmos.reminders.ReminderNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container.
 *
 * No DI framework on purpose: the graph is small, the wiring is explicit, and there is no
 * code generation to slow the build down or to hide a dependency cycle.
 */
class AppContainer(context: android.content.Context) {
    private val appContext = context.applicationContext

    val database: WmDatabase by lazy { WmDatabase.build(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val secrets: SecretStore by lazy { SecretStore(appContext) }

    val scheduler: ReminderScheduler by lazy { AlarmReminderScheduler(appContext) }

    val brain: BrainRepository by lazy { BrainRepository(database) }

    val tasks: TaskRepository by lazy { TaskRepository(database) }

    val reminders: ReminderRepository by lazy { ReminderRepository(database, scheduler) }

    val inbox: InboxRepository by lazy { InboxRepository(appContext, database) }

    val chat: ChatRepository by lazy { ChatRepository(database) }

    val providers: ProviderRepository by lazy { ProviderRepository(database, secrets) }

    val actionLog: ActionLogRepository by lazy { ActionLogRepository(database) }

    val attachments: dev.wmos.data.repo.AttachmentRepository by lazy {
        dev.wmos.data.repo.AttachmentRepository(appContext, database)
    }

    val automation: dev.wmos.data.repo.AutomationRepository by lazy {
        dev.wmos.data.repo.AutomationRepository(database, brain, tasks, reminders, actionLog)
    }

    val search: dev.wmos.data.repo.SearchRepository by lazy {
        dev.wmos.data.repo.SearchRepository(database)
    }

    val memory: dev.wmos.data.repo.MemoryRepository by lazy {
        dev.wmos.data.repo.MemoryRepository(database)
    }

    val graus: dev.wmos.ai.GrausEngine by lazy { dev.wmos.ai.GrausEngine(this) }

    val backup: dev.wmos.data.repo.BackupRepository by lazy {
        dev.wmos.data.repo.BackupRepository(appContext, database)
    }

    val drafts: dev.wmos.data.prefs.DraftStore by lazy { dev.wmos.data.prefs.DraftStore(appContext) }

    val appLockRepository: dev.wmos.security.AppLockRepository by lazy {
        dev.wmos.security.AppLockRepository(appContext, secrets)
    }

    val appLock: dev.wmos.security.AppLockManager by lazy {
        dev.wmos.security.AppLockManager(appLockRepository)
    }

    /** Outlives any screen; used to flush a draft when a ViewModel is destroyed. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Set when a share intent was captured, so the UI can jump to Inbox. */
    val openInboxSignal = kotlinx.coroutines.flow.MutableStateFlow(false)

    val snackbar = dev.wmos.ui.components.SnackbarController()
}

class WmApplication : Application() {
    lateinit var container: AppContainer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifications.ensureChannel(this)
        applicationScope.launch(Dispatchers.IO) {
            runCatching { container.brain.seedDefaultFoldersIfEmpty() }
        }
    }
}
