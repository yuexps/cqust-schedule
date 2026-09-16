package com.xingheyuzhuan.shiguangschedule.tool

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

actual object PlatformUpdateStrategy : KoinComponent {
    actual val isUpdateSupported: Boolean = true

    private val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal")

    private fun getDeviceAbi(): String {
        val deviceAbis = Build.SUPPORTED_ABIS
        val knownSplits = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        return deviceAbis.firstOrNull { it in knownSplits } ?: "universal"
    }

    actual fun parseTargetUrl(response: ApiReleaseResponse): String? {

        val abiUrlMap = mutableMapOf<String, String>()

        // 1. 精准匹配
        for (asset in response.assets) {
            val fileName = asset.name.lowercase()
            if (!fileName.endsWith(".apk")) continue

            for (abi in SUPPORTED_ABIS) {
                if (fileName.contains("-$abi-") || fileName.endsWith("-$abi.apk")) {
                    abiUrlMap[abi] = asset.downloadUrl
                    break
                }
            }
        }

        // 2. 宽松匹配
        if (abiUrlMap.isEmpty()) {
            for (asset in response.assets) {
                val fileName = asset.name.lowercase()
                if (!fileName.endsWith(".apk")) continue

                for (abi in SUPPORTED_ABIS) {
                    if (fileName.contains(abi)) {
                        abiUrlMap[abi] = asset.downloadUrl
                        break
                    }
                }
            }
        }

        val deviceAbi = getDeviceAbi()
        return abiUrlMap[deviceAbi]
            ?: abiUrlMap["universal"]
            ?: abiUrlMap.values.firstOrNull()
    }

    actual fun openUrl(url: String) {
        val context: Context = get()
        try {
            val uri = url.toUri()
            // 若为 APK 直接下载链接，优先调用系统 DownloadManager 进行后台下载
            if (url.endsWith(".apk", ignoreCase = true) || url.contains("/download/", ignoreCase = true)) {
                try {
                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    if (downloadManager != null) {
                        val fileName = uri.lastPathSegment?.takeIf { it.endsWith(".apk", ignoreCase = true) }
                            ?: "cqust-schedule-update.apk"
                        val request = DownloadManager.Request(uri).apply {
                            setTitle("正在下载重科课表更新")
                            setDescription(fileName)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                            setMimeType("application/vnd.android.package-archive")
                        }
                        downloadManager.enqueue(request)
                        Toast.makeText(context, "已开始下载更新包，请查看通知栏进度", Toast.LENGTH_SHORT).show()
                        return
                    }
                } catch (_: Exception) {
                    // DownloadManager 异常时回退到轻量浏览器打开
                }
            }

            // 轻量保底：调用系统浏览器打开链接
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            // 忽略未找到 Activity 等异常
        }
    }
}