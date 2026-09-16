package com.xingheyuzhuan.shiguangschedule.tool

import androidx.compose.runtime.Composable

/**
 * 跨平台日历权限请求启动器
 */
@Composable
expect fun rememberCalendarPermissionLauncher(onGranted: () -> Unit): () -> Unit
