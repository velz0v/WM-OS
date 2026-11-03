package dev.wmos.ui.nav

/** Every route in the app, declared once. Arguments are typed, never string-concatenated in place. */
object Routes {
    const val HOME = "home"
    const val BRAIN = "brain"
    const val PLAN = "plan"
    const val GRAUS = "graus"
    const val MORE = "more"

    const val FOLDER = "folder/{folderId}"

    fun folder(id: String) = "folder/$id"

    const val NOTE = "note/{itemId}"

    fun note(id: String) = "note/$id"

    const val NOTE_NEW = "note/new"

    const val TASK = "task/{taskId}"

    fun task(id: String) = "task/$id"

    const val TASK_NEW = "task/new"

    const val REMINDER = "reminder/{reminderId}"

    fun reminder(id: String) = "reminder/$id"

    const val REMINDER_NEW = "reminder/new"

    const val PROJECT = "project/{projectId}"

    fun project(id: String) = "project/$id"

    const val INBOX = "inbox"
    const val TRASH = "trash"
    const val SEARCH = "search"
    const val FILES = "files"
    const val AI_LAB = "ai-lab"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val QUICK = "quick"
    const val ABOUT = "about"
    const val ACTION_LOG = "action-log"
    const val MEMORY = "memory"
    const val AUTOPILOT = "autopilot"
}

enum class TopLevelDestination(
    val route: String,
    val label: String,
) {
    HOME(Routes.HOME, "Главная"),
    BRAIN(Routes.BRAIN, "Знания"),
    PLAN(Routes.PLAN, "Дела"),
    GRAUS(Routes.GRAUS, "Граус"),
    MORE(Routes.MORE, "Ещё"),
    ;

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { route?.startsWith(it.route) == true && it.route != "" }
    }
}
