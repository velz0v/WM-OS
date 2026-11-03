package dev.wmos.ui.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.wmos.AppContainer
import dev.wmos.ui.anim.pressScale
import dev.wmos.ui.anim.rememberPressSource
import dev.wmos.ui.screens.ailab.AiLabScreen
import dev.wmos.ui.screens.autopilot.AutopilotScreen
import dev.wmos.ui.screens.backup.BackupScreen
import dev.wmos.ui.screens.brain.BrainScreen
import dev.wmos.ui.screens.browse.ActionLogScreen
import dev.wmos.ui.screens.browse.FolderScreen
import dev.wmos.ui.screens.browse.ProjectScreen
import dev.wmos.ui.screens.browse.TrashScreen
import dev.wmos.ui.screens.files.FilesScreen
import dev.wmos.ui.screens.graus.GrausScreen
import dev.wmos.ui.screens.home.HomeScreen
import dev.wmos.ui.screens.inbox.InboxScreen
import dev.wmos.ui.screens.memory.MemoryScreen
import dev.wmos.ui.screens.more.MoreScreen
import dev.wmos.ui.screens.note.NoteEditorScreen
import dev.wmos.ui.screens.plan.PlanScreen
import dev.wmos.ui.screens.quick.QuickScreen
import dev.wmos.ui.screens.reminder.ReminderEditorScreen
import dev.wmos.ui.screens.search.SearchScreen
import dev.wmos.ui.screens.settings.AboutScreen
import dev.wmos.ui.screens.settings.SettingsScreen
import dev.wmos.ui.screens.task.TaskDetailScreen
import dev.wmos.ui.theme.Lime
import dev.wmos.ui.theme.OnLime
import kotlinx.coroutines.launch

private const val NAV_ANIM_MS = 280

@Composable
fun WmApp(container: AppContainer) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = TopLevelDestination.fromRoute(currentRoute)
    val openInbox by container.openInboxSignal.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.snackbar.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                withDismissAction = message.actionLabel != null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                message.onAction?.invoke()
            }
        }
    }

    LaunchedEffect(openInbox) {
        if (openInbox) {
            navController.navigate(Routes.INBOX) { launchSingleTop = true }
            container.openInboxSignal.value = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        bottomBar = {
            // Graus is a focused chat surface: no dock, the composer hugs the keyboard instead.
            if (topLevel != null && topLevel != TopLevelDestination.GRAUS) {
                WmBottomDock(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigateTopLevel(destination)
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            enterTransition = {
                fadeIn(tween(NAV_ANIM_MS)) + slideInVertically(tween(NAV_ANIM_MS)) { it / 12 }
            },
            exitTransition = { fadeOut(tween(NAV_ANIM_MS / 2)) },
            popEnterTransition = { fadeIn(tween(NAV_ANIM_MS)) },
            popExitTransition = {
                fadeOut(tween(NAV_ANIM_MS)) + slideOutVertically(tween(NAV_ANIM_MS)) { it / 12 }
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenBrain = { navController.navigate(Routes.BRAIN) },
                    onOpenGraus = { navController.navigate(Routes.GRAUS) },
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                    onOpenNote = { navController.navigate(Routes.note(it)) },
                    onOpenTask = { navController.navigate(Routes.task(it)) },
                    onOpenReminder = { navController.navigate(Routes.reminder(it)) },
                    onNewNote = {
                        scope.launch {
                            val id = container.brain.createItem()
                            navController.navigate(Routes.note(id))
                        }
                    },
                    onNewReminder = { navController.navigate(Routes.REMINDER_NEW) },
                    onOpenPlan = { navController.navigate(Routes.PLAN) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }

            composable(Routes.BRAIN) {
                BrainScreen(
                    onOpenFolder = { navController.navigate(Routes.folder(it)) },
                    onOpenItem = { navController.navigate(Routes.note(it)) },
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }

            composable(Routes.PLAN) {
                PlanScreen(
                    onOpenReminder = { navController.navigate(Routes.reminder(it)) },
                    onNewReminder = { navController.navigate(Routes.REMINDER_NEW) },
                    onOpenProject = { navController.navigate(Routes.project(it)) },
                    onOpenTask = { navController.navigate(Routes.task(it)) },
                    onNewTask = { navController.navigate(Routes.TASK_NEW) },
                )
            }

            composable(Routes.TASK_NEW) {
                TaskDetailScreen(
                    taskId = null,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.TASK,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) { entry ->
                TaskDetailScreen(
                    taskId = entry.arguments?.getString("taskId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onDeleted = {
                        navController.popBackStack(Routes.PLAN, inclusive = false)
                    },
                )
            }

            composable(Routes.GRAUS) {
                GrausScreen(
                    onOpenAiLab = { navController.navigate(Routes.AI_LAB) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.MORE) {
                MoreScreen(
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                    onOpenAiLab = { navController.navigate(Routes.AI_LAB) },
                    onOpenMemory = { navController.navigate(Routes.MEMORY) },
                    onOpenAutopilot = { navController.navigate(Routes.AUTOPILOT) },
                    onOpenFiles = { navController.navigate(Routes.FILES) },
                    onOpenQuick = { navController.navigate(Routes.QUICK) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                    onOpenActionLog = { navController.navigate(Routes.ACTION_LOG) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(
                route = Routes.NOTE,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                val itemId = entry.arguments?.getString("itemId").orEmpty()
                NoteEditorScreen(itemId = itemId, onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.FOLDER,
                arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
            ) { entry ->
                val folderId = entry.arguments?.getString("folderId").orEmpty()
                FolderScreen(
                    folderId = folderId,
                    onBack = { navController.popBackStack() },
                    onOpenFolder = { navController.navigate(Routes.folder(it)) },
                    onOpenItem = { navController.navigate(Routes.note(it)) },
                )
            }

            composable(
                route = Routes.PROJECT,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) { entry ->
                ProjectScreen(
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.REMINDER_NEW) {
                ReminderEditorScreen(
                    reminderId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.REMINDER,
                arguments = listOf(navArgument("reminderId") { type = NavType.StringType }),
            ) { entry ->
                ReminderEditorScreen(
                    reminderId = entry.arguments?.getString("reminderId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(Routes.INBOX) {
                InboxScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.TRASH) {
                TrashScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.AI_LAB) {
                AiLabScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.QUICK) {
                QuickScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.FILES) {
                FilesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.note(it)) },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.note(it)) },
                    onOpenTask = { navController.navigate(Routes.task(it)) },
                    onOpenReminder = { navController.navigate(Routes.reminder(it)) },
                    onOpenProject = { navController.navigate(Routes.project(it)) },
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                )
            }

            composable(Routes.AUTOPILOT) {
                AutopilotScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.MEMORY) {
                MemoryScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ACTION_LOG) {
                ActionLogScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Navigate to a top-level tab with the standard save/restore behaviour. */
private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Floating pill dock with a central lime Граус FAB, after the reference screens. */
@Composable
private fun WmBottomDock(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), ambientColor = Lime.copy(alpha = 0.08f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockItem(
                    icon = Icons.Outlined.Home,
                    label = TopLevelDestination.HOME.label,
                    selected = currentRoute?.startsWith(TopLevelDestination.HOME.route) == true,
                    onClick = { onNavigate(TopLevelDestination.HOME) },
                    modifier = Modifier.weight(1f),
                )
                DockItem(
                    icon = Icons.Outlined.AutoStories,
                    label = TopLevelDestination.BRAIN.label,
                    selected = currentRoute?.startsWith(TopLevelDestination.BRAIN.route) == true,
                    onClick = { onNavigate(TopLevelDestination.BRAIN) },
                    modifier = Modifier.weight(1f),
                )
                GrausFab(
                    onClick = { onNavigate(TopLevelDestination.GRAUS) },
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                DockItem(
                    icon = Icons.Outlined.Checklist,
                    label = TopLevelDestination.PLAN.label,
                    selected = currentRoute?.startsWith(TopLevelDestination.PLAN.route) == true,
                    onClick = { onNavigate(TopLevelDestination.PLAN) },
                    modifier = Modifier.weight(1f),
                )
                DockItem(
                    icon = Icons.Outlined.MoreHoriz,
                    label = TopLevelDestination.MORE.label,
                    selected = currentRoute?.startsWith(TopLevelDestination.MORE.route) == true,
                    onClick = { onNavigate(TopLevelDestination.MORE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Центральная кнопка: Граус доступен одним тапом из любой вкладки. */
@Composable
private fun GrausFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberPressSource()
    Box(
        modifier = modifier
            .size(54.dp)
            .pressScale(interaction, pressedScale = 0.88f)
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = Lime.copy(alpha = 0.45f),
                spotColor = Lime.copy(alpha = 0.45f),
            )
            .clip(CircleShape)
            .background(Lime)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.SmartToy,
            contentDescription = "Граус",
            tint = OnLime,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberPressSource()
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.86f
            selected -> 1.14f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dockIconScale",
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(240),
        label = "dockPillAlpha",
    )
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) Lime else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(240),
        label = "dockTint",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 46.dp else 40.dp,
        animationSpec = tween(240),
        label = "dockPillWidth",
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(width = pillWidth, height = 30.dp)
                    .graphicsLayer { alpha = pillAlpha }
                    .clip(CircleShape)
                    .background(Lime.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
