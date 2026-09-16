package com.xingheyuzhuan.shiguangschedule.tool

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import org.jetbrains.compose.resources.stringResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.error_sync_calendar_failed

/**
 * Android 端日历权限请求启动器实现
 */
@Composable
actual fun rememberCalendarPermissionLauncher(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val permissionDeniedMsg = stringResource(Res.string.error_sync_calendar_failed)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isReadGranted = permissions[Manifest.permission.READ_CALENDAR] == true
        val isWriteGranted = permissions[Manifest.permission.WRITE_CALENDAR] == true
        if (isReadGranted && isWriteGranted) {
            onGranted()
        } else {
            ToastManager.show(permissionDeniedMsg)
        }
    }

    return {
        val hasRead = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        val hasWrite = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (hasRead && hasWrite) {
            onGranted()
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }
}
