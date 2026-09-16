package com.xingheyuzhuan.shiguangschedule.ui.theme

import androidx.compose.ui.graphics.Color

// 默认主题色
val DefaultThemeColor = Color(0xFF73CAF8)

/**
 * 预设主题色定义
 */
data class ThemePreset(
    val name: String,
    val color: Color
)

/**
 * 经典预设主题色集合
 */
val ThemePresets = listOf(
    ThemePreset("晴空蓝", Color(0xFF73CAF8)),
    ThemePreset("可爱粉", Color(0xFFFF69B4)),
    ThemePreset("清新绿", Color(0xFF34D399)),
    ThemePreset("活力橙", Color(0xFFFB923C)),
    ThemePreset("优雅紫", Color(0xFFA855F7)),
    ThemePreset("薄荷青", Color(0xFF14B8A6))
)