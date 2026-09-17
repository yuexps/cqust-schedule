package com.xingheyuzhuan.shiguangschedule

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.xingheyuzhuan.shiguangschedule.data.model.StartScreen
import com.xingheyuzhuan.shiguangschedule.ui.components.AdaptiveNavigationScaffold
import com.xingheyuzhuan.shiguangschedule.ui.schedule.WeeklyScheduleScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.SettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.SettingsViewModel
import com.xingheyuzhuan.shiguangschedule.ui.settings.additional.LanguageSettingScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.additional.MoreOptionsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.additional.OpenSourceLicensesScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.contribution.ContributionScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.conversion.CourseTableConversionScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.coursemanagement.CourseInstanceListScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.coursemanagement.CourseNameListScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.coursetables.ManageCourseTablesScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.notification.NotificationSettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.style.StyleSettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.themesettings.ThemeSettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.time.ComboScheduleEditScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.time.SingleScheduleEditScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.time.TimeScheduleManagementScreen

import com.xingheyuzhuan.shiguangschedule.ui.theme.ShiguangScheduleTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xingheyuzhuan.shiguangschedule.data.api.cqust.CqustSyncManager
import com.xingheyuzhuan.shiguangschedule.tool.UpdateChecker
import com.xingheyuzhuan.shiguangschedule.tool.UpdatePlatform
import com.xingheyuzhuan.shiguangschedule.tool.UpdateStatus
import com.xingheyuzhuan.shiguangschedule.ui.cqust.CqustLoginScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.additional.UpdateResultDialog
import com.xingheyuzhuan.shiguangschedule.ui.theme.ShiguangScheduleTheme
import com.xingheyuzhuan.shiguangschedule.ui.today.TodayScheduleScreen
import kotlin.time.Clock
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named

@Composable
fun App() {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val cqustSyncManager: CqustSyncManager = koinInject()
    val updateChecker: UpdateChecker = koinInject()
    val appVersionName: String = koinInject(named("AppVersionName"))

    var autoUpdateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    var showAutoUpdateDialog by remember { mutableStateOf(false) }

    if (state.isReady) {
        val isLoggedIn = state.appSettings.cqustIsLoggedIn

        // 仅在已登录状态下进入应用时触发静默同步（内部有 6 小时冷却机制与异常静默兜底）
        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn) {
                cqustSyncManager.silentSyncIfNeeded()
            }
        }

        // 冷启动时静默检查更新（24 小时冷却防抖，仅在发现新版本时弹窗提示）
        LaunchedEffect(state.isReady, state.appSettings.autoCheckUpdateEnabled) {
            if (state.appSettings.autoCheckUpdateEnabled) {
                val currentTime = Clock.System.now().toEpochMilliseconds()
                val cooldown = 24 * 60 * 60 * 1000L // 24小时防抖冷却
                if (currentTime - state.appSettings.lastUpdateCheckTime > cooldown) {
                    viewModel.onUpdateCheckTimeRecorded(currentTime)
                    val status = updateChecker.checkUpdate(UpdatePlatform.GITHUB, appVersionName)
                    if (status is UpdateStatus.Found) {
                        autoUpdateStatus = status
                        showAutoUpdateDialog = true
                    }
                }
            }
        }

        ShiguangScheduleTheme(settings = state.appSettings) {
            val startDest = remember(isLoggedIn, state.appSettings.startScreen) {
                if (!isLoggedIn) {
                    Destination.CqustLogin
                } else {
                    when (state.appSettings.startScreen) {
                        StartScreen.COURSE_SCHEDULE -> Destination.CourseSchedule
                        StartScreen.TODAY_SCHEDULE -> Destination.TodaySchedule
                    }
                }
            }
            AppNavigation(
                startDestination = startDest,
                isLoggedIn = isLoggedIn
            )

            // 发现新版本时弹出的全局更新提醒弹窗
            UpdateResultDialog(
                showDialog = showAutoUpdateDialog,
                updateStatus = autoUpdateStatus,
                onDismiss = {
                    showAutoUpdateDialog = false
                    autoUpdateStatus = UpdateStatus.Idle
                },
                onDownloadClick = { targetUrl ->
                    updateChecker.launchUpdate(targetUrl)
                }
            )
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {}
    }
}

@Composable
fun AppNavigation(
    startDestination: Destination,
    isLoggedIn: Boolean
) {
    val backStack = rememberNavBackStack(
        configuration = navSavedStateConfig,
        startDestination
    )

    val currentDestination = backStack.lastOrNull() as? Destination ?: startDestination

    var navHideFraction by remember { mutableFloatStateOf(0f) }

    val onNavigate: (Destination) -> Unit = remember(backStack) {
        { dest ->
            if (dest.isMainScreen) {
                if (backStack.lastOrNull() != dest) {
                    backStack.clear()
                    backStack.add(dest)
                }
            } else {
                if (backStack.lastOrNull() != dest) {
                    backStack.add(dest)
                }
            }
        }
    }

    val onBack: () -> Unit = remember(backStack) {
        {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }

    val animSpec = tween<IntOffset>(300)

    AdaptiveNavigationScaffold(
        currentDestination = currentDestination,
        onTabSelected = onNavigate,
        showNavigation = currentDestination.isMainScreen,
        navHideFractionProvider = { navHideFraction }
    ) { _ ->
        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = animSpec) togetherWith
                        slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeOut()
            },
            popTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeIn() togetherWith
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec)
            },
            predictivePopTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeIn() togetherWith
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec)
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            )
        ) { key ->
            val destination = key as Destination

            NavEntry(
                key = key,
                metadata = metadata {
                    put(ShiguangNavMetadata.IsMainScreenKey, destination.isMainScreen)
                }
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenContent(
                        targetDest = destination,
                        onNavigate = onNavigate,
                        onBack = onBack,
                        isLoggedIn = isLoggedIn,
                        onNavHideFractionChanged = { navHideFraction = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenContent(
    targetDest: Destination,
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    isLoggedIn: Boolean,
    onNavHideFractionChanged: (Float) -> Unit
) {
    when (targetDest) {
        Destination.CourseSchedule -> WeeklyScheduleScreen(
            onNavigate = onNavigate,
            onBack = onBack,
            onNavHideFractionChanged = onNavHideFractionChanged
        )
        Destination.Settings -> SettingsScreen(onNavigate, onBack)
        Destination.TodaySchedule -> TodayScheduleScreen(onNavigate, onBack)
        Destination.ManageCourseTables -> ManageCourseTablesScreen(onBack)
        Destination.CourseTableConversion -> CourseTableConversionScreen(onNavigate, onBack)
        Destination.NotificationSettings -> NotificationSettingsScreen(onBack)
        Destination.MoreOptions -> MoreOptionsScreen(onNavigate, onBack)
        Destination.OpenSourceLicenses -> OpenSourceLicensesScreen(onBack)
        Destination.ContributionList -> ContributionScreen(onBack)
        Destination.CourseManagementList -> CourseNameListScreen(onNavigate, onBack)
        Destination.StyleSettings -> StyleSettingsScreen(onBack)
        Destination.ThemeSettings -> ThemeSettingsScreen(onBack)
        Destination.LanguageSettings -> LanguageSettingScreen(onBack)
        Destination.CqustLogin -> CqustLoginScreen(
            onLoginSuccess = { onNavigate(Destination.CourseSchedule) },
            onBack = if (isLoggedIn) onBack else null
        )

        Destination.TimeScheduleManagement -> TimeScheduleManagementScreen(
            onBack = onBack,
            onEditSingleSchedule = { tableId, isPublic, copyFromId ->
                onNavigate(Destination.SingleScheduleEdit(tableId, isPublic, copyFromId))
            },
            onEditComboSchedule = { comboId, copyFromId ->
                onNavigate(Destination.ComboScheduleEdit(comboId, copyFromId))
            }
        )

        // 单一/公共作息编辑页面路由
        is Destination.SingleScheduleEdit -> SingleScheduleEditScreen(
            tableId = targetDest.tableId,
            isPublic = targetDest.isPublic,
            copyFromId = targetDest.copyFromId,
            onBack = onBack
        )

        // 组合作息编辑页面路由
        is Destination.ComboScheduleEdit -> ComboScheduleEditScreen(
            comboId = targetDest.comboId,
            copyFromId = targetDest.copyFromId,
            onBack = onBack
        )

        is Destination.CourseManagementDetail -> CourseInstanceListScreen(
            courseName = targetDest.courseName,
            onNavigateBack = onBack,
            onNavigate = onNavigate
        )
    }
}