package com.xingheyuzhuan.shiguangschedule.tool

import androidx.compose.ui.platform.ClipEntry

/**
 * 将纯文本转换为适用于 LocalClipboard 的 ClipEntry
 */
expect fun clipEntryOf(text: String): ClipEntry
