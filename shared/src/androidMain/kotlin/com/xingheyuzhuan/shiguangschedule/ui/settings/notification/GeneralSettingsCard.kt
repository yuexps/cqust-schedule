package com.xingheyuzhuan.shiguangschedule.ui.settings.notification

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.item_auto_mode
import shiguangschedule.shared.generated.resources.item_course_reminder
import shiguangschedule.shared.generated.resources.item_exact_alarm_permission
import shiguangschedule.shared.generated.resources.item_remind_time_before
import shiguangschedule.shared.generated.resources.remind_time_minutes_format
import shiguangschedule.shared.generated.resources.status_authorized
import shiguangschedule.shared.generated.resources.status_disabled
import shiguangschedule.shared.generated.resources.status_enabled
import shiguangschedule.shared.generated.resources.status_unauthorized

/**
 * 课程提醒二级设置页面内容（Android 平台专属）
 */
@Composable
fun GeneralSettingsCard(
    uiState: NotificationSettingsUiState,
    currentModeText: String?,
    onReminderToggle: (Boolean) -> Unit,
    onCompatWearableToggle: (Boolean) -> Unit,
    onAutoModeClick: () -> Unit,
    onRemindTimeClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onSyncCalendarClick: () -> Unit,
    onAutoSyncCalendarToggle: (Boolean) -> Unit,
    onCalendarRemindTimeClick: () -> Unit,
    onNotificationPermissionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExactAlarmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 分组 1：系统日历同步
        SettingsGroup(title = "系统日历同步") {
            Text(
                text = "将课程日程写入系统日历，由系统日历服务管理提醒，无需应用常驻后台。若使用日历提醒，不建议同时开启课前应用通知，以免产生重复提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            GroupDivider()

            SettingItemRow(
                title = "立即同步至系统日历",
                subtitle = "将当前课表日程写入本地系统日历",
                currentValue = if (uiState.isSyncingCalendar) "正在同步..." else null,
                enabled = !uiState.isSyncingCalendar,
                onClick = onSyncCalendarClick
            )

            GroupDivider()

            SettingItemRow(
                title = "提前提醒时间",
                subtitle = "设置写入系统日历的日程提醒时间",
                currentValue = if (uiState.calendarRemindBeforeMinutes == 0) "准时提醒" else "${uiState.calendarRemindBeforeMinutes} 分钟",
                onClick = onCalendarRemindTimeClick
            )

            GroupDivider()

            SettingItemRow(
                title = "课表变动自动同步",
                subtitle = "从教务系统更新课表后自动刷新日历日程",
                showChevron = false,
                trailing = {
                    Switch(
                        checked = uiState.autoSyncToCalendar,
                        onCheckedChange = onAutoSyncCalendarToggle
                    )
                },
                onClick = { onAutoSyncCalendarToggle(!uiState.autoSyncToCalendar) }
            )
        }

        // 分组 2：课前应用通知
        SettingsGroup(title = "课前应用通知") {
            SettingItemRow(
                title = stringResource(Res.string.item_course_reminder),
                subtitle = "在课程开始前发送本地系统通知",
                showChevron = false,
                trailing = {
                    Switch(
                        checked = uiState.reminderEnabled,
                        onCheckedChange = { targetState ->
                            if (targetState) {
                                if (hasExactAlarmPermission(context)) {
                                    onReminderToggle(true)
                                } else {
                                    showExactAlarmDialog = true
                                }
                            } else {
                                onReminderToggle(false)
                            }
                        }
                    )
                },
                onClick = {
                    val targetState = !uiState.reminderEnabled
                    if (targetState) {
                        if (hasExactAlarmPermission(context)) {
                            onReminderToggle(true)
                        } else {
                            showExactAlarmDialog = true
                        }
                    } else {
                        onReminderToggle(false)
                    }
                }
            )

            GroupDivider()

            SettingItemRow(
                title = stringResource(Res.string.item_remind_time_before),
                subtitle = "设置上课前接收通知的提前量",
                currentValue = stringResource(Res.string.remind_time_minutes_format, uiState.remindBeforeMinutes),
                enabled = uiState.reminderEnabled,
                onClick = onRemindTimeClick
            )

            GroupDivider()

            SettingItemRow(
                title = "兼容穿戴设备通知",
                subtitle = "改善手环或手表的通知同步兼容性，停用实时进度",
                showChevron = false,
                enabled = uiState.reminderEnabled,
                trailing = {
                    Switch(
                        checked = uiState.compatWearableSync,
                        enabled = uiState.reminderEnabled,
                        onCheckedChange = onCompatWearableToggle
                    )
                },
                onClick = {
                    if (uiState.reminderEnabled) {
                        onCompatWearableToggle(!uiState.compatWearableSync)
                    }
                }
            )
        }

        // 分组 3：上课免打扰
        SettingsGroup(title = "上课免打扰") {
            SettingItemRow(
                title = stringResource(Res.string.item_auto_mode),
                subtitle = "上课期间自动切换为勿扰或静音，下课后恢复",
                currentValue = currentModeText,
                onClick = onAutoModeClick
            )
        }

        // 分组 4：后台运行与系统权限
        SettingsGroup(title = "后台运行与系统权限") {
            Text(
                text = "为保障应用通知准时送达，避免后台进程被系统终止，建议检查并配置以下系统权限：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            GroupDivider()

            // 系统通知权限
            val notificationStatusText = if (uiState.notificationPermissionStatus) {
                stringResource(Res.string.status_enabled)
            } else {
                stringResource(Res.string.status_disabled)
            }
            val notificationStatusColor = if (uiState.notificationPermissionStatus) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

            SettingItemRow(
                title = "系统通知权限",
                subtitle = "允许应用在状态栏与通知栏发送提醒",
                currentValue = notificationStatusText,
                currentValueColor = notificationStatusColor,
                onClick = onNotificationPermissionClick
            )

            // 精确闹钟权限 (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GroupDivider()
                val statusText = if (uiState.exactAlarmStatus) {
                    stringResource(Res.string.status_enabled)
                } else {
                    stringResource(Res.string.status_disabled)
                }
                val statusColor = if (uiState.exactAlarmStatus) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }

                SettingItemRow(
                    title = stringResource(Res.string.item_exact_alarm_permission),
                    subtitle = "允许应用在设定的精确时间点触发提醒",
                    currentValue = statusText,
                    currentValueColor = statusColor,
                    onClick = { openExactAlarmSettings(context) }
                )
            }

            GroupDivider()

            // 勿扰权限
            val dndStatusText = if (uiState.dndPermissionStatus) {
                stringResource(Res.string.status_authorized)
            } else {
                stringResource(Res.string.status_unauthorized)
            }
            val dndStatusColor = if (uiState.dndPermissionStatus) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

            SettingItemRow(
                title = "勿扰访问权限",
                subtitle = "用于上课时自动开启勿扰或静音模式",
                currentValue = dndStatusText,
                currentValueColor = dndStatusColor,
                onClick = { openDndSettings(context) }
            )

            GroupDivider()

            // 忽略电池优化
            SettingItemRow(
                title = "电池优化白名单",
                subtitle = "防止系统在休眠或省电时延缓或拦截提醒",
                currentValue = "去设置",
                onClick = onBatteryOptimizationClick
            )

            GroupDivider()

            // 后台与自启动
            SettingItemRow(
                title = "后台运行与自启动",
                subtitle = "前往系统应用设置允许后台活动与自启",
                currentValue = "去设置",
                onClick = onAppSettingsClick
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    // 开启提醒但缺少精确闹钟权限时的引导弹窗
    if (showExactAlarmDialog) {
        ExactAlarmPermissionGuideDialog(
            onDismiss = { showExactAlarmDialog = false }
        )
    }
}

/**
 * 分组卡片容器
 */
@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

/**
 * 分组项内部轻量分割线
 */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}