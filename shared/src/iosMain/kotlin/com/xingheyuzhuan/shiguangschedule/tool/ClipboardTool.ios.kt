package com.xingheyuzhuan.shiguangschedule.tool

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual fun clipEntryOf(text: String): ClipEntry =
    ClipEntry.withPlainText(text)
