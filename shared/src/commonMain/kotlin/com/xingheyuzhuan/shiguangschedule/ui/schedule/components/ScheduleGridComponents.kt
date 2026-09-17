package com.xingheyuzhuan.shiguangschedule.ui.schedule.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWithWeeks
import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeSlot
import com.xingheyuzhuan.shiguangschedule.ui.schedule.MergedCourseBlock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 课表项统一调度接口
 */
interface ISingleSchedulable {
    val columnIndex: Int
    val subColumnIndex: Int
    val subColumnCount: Int
    val startSection: Float
    val endSection: Float
    val courseWrapper: CourseWithWeeks
    val parentBlock: MergedCourseBlock
}

/**
 * 1. 数据收拢：外部向课表传递的纯展示数据与基础配置
 */
@Immutable
data class ScheduleGridViewState(
    val dates: List<String>,
    val currentYear: String,
    val currentWeek: String? = null,
    val timeSlots: List<TimeSlot>,
    val mergedCourses: List<MergedCourseBlock>,
    val showWeekends: Boolean,
    val todayIndex: Int,
    val firstDayOfWeek: Int,
    val currentSectionIndex: Int = -1
)

/**
 * 2. 动作收拢：外部业务逻辑响应的交互回调接口
 */
interface ScheduleGridActions {
    fun onCourseBlockClicked(block: MergedCourseBlock)
    fun onTimeSlotClicked() {}
}

/**
 * 3. 运行状态收拢：课表网格滚动状态管理类
 */
@Stable
class ScheduleGridState(
    val gridScrollState: ScrollState
)

/**
 * 方便在 Composable 中创建并记住 ScheduleGridState 实例的快捷函数
 */
@Composable
fun rememberScheduleGridState(
    gridScrollState: ScrollState = rememberScrollState()
): ScheduleGridState {
    return remember(gridScrollState) {
        ScheduleGridState(gridScrollState)
    }
}

/**
 * 课表顶部日期/星期标头组件
 */
@Composable
fun DayHeader(
    style: ScheduleGridStyleComposed,
    displayDays: List<String>,
    dates: List<String>,
    currentYear: String,
    currentWeek: String?,
    todayIndex: Int,
    lineColor: Color,
    textColor: Color,
    subTextColor: Color,
    strokeWidthPx: Float
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(style.dayHeaderHeight)) {
        val shouldShowDate = !style.hideDateUnderDay && maxHeight >= 42.dp

        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(style.timeColumnWidth)
                    .fillMaxHeight()
                    .drawBehind {
                        if (!style.hideGridLines) {
                            drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidthPx)
                            drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidthPx)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentYear,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = subTextColor,
                        style = TextStyle.Default
                    )

                    if (!currentWeek.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = currentWeek,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = subTextColor,
                            style = TextStyle.Default
                        )
                    }
                }
            }

            val displayDaysCount = displayDays.size

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        if (!style.hideGridLines) {
                            val cellWidth = size.width / displayDaysCount
                            for (i in 1..displayDaysCount) {
                                val x = i * cellWidth
                                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokeWidthPx)
                            }
                            drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidthPx)
                        }
                    }
            ) {
                displayDays.forEachIndexed { index, day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (index == todayIndex) MaterialTheme.colorScheme.primaryContainer.copy(0.4f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = day,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                style = TextStyle(
                                    lineHeight = 16.sp
                                )
                            )

                            if (shouldShowDate && dates.size > index) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dates[index],
                                    fontSize = 10.sp,
                                    color = subTextColor,
                                    maxLines = 1,
                                    style = TextStyle(
                                        lineHeight = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 课表左侧时间轴/节次轴组件
 */
@Composable
fun TimeColumn(
    style: ScheduleGridStyleComposed,
    timeSlots: List<TimeSlot>,
    maxGridSections: Int,
    is24HourMode: Boolean,
    onTimeSlotClicked: () -> Unit,
    modifier: Modifier,
    lineColor: Color,
    currentSectionIndex: Int = -1,
    textColor: Color,
    subTextColor: Color,
    strokeWidthPx: Float
) {
    val currentHour = remember {
        try {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        } catch (e: Exception) {
            -1
        }
    }

    Column(modifier.width(style.timeColumnWidth)) {
        for (index in 0 until maxGridSections) {
            val isCurrentHourActive = if (is24HourMode) {
                currentHour == index && currentSectionIndex != -1
            } else {
                index + 1 == currentSectionIndex
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(style.sectionHeight)
                    .clickable { onTimeSlotClicked() }
                    .background(if (isCurrentHourActive) MaterialTheme.colorScheme.primaryContainer.copy(0.4f) else Color.Transparent)
                    .drawBehind {
                        if (!style.hideGridLines) {
                            drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidthPx)
                            if (!is24HourMode) {
                                drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidthPx)
                            }
                        }
                    },
                contentAlignment = if (is24HourMode) Alignment.TopCenter else Alignment.Center
            ) {
                val h = maxHeight

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (is24HourMode) Arrangement.Top else Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .then(if (is24HourMode) Modifier.offset(y = (-7).dp) else Modifier)
                ) {
                    if (is24HourMode) {
                        val formatHourStr = "${index.toString().padStart(2, '0')}:00"
                        Text(
                            text = formatHourStr,
                            fontSize = if (h < 32.dp) 11.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCurrentHourActive) MaterialTheme.colorScheme.primary else textColor
                        )
                    } else {
                        val slot = timeSlots.getOrNull(index)
                        if (slot != null) {
                            Text(
                                text = slot.alias ?: slot.number.toString(),
                                fontSize = if (h < 32.dp) 11.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!style.hideSectionTime) {
                                when {
                                    h >= 52.dp -> {
                                        Spacer(Modifier.height(2.dp))
                                        TimeText(slot.startTime, subTextColor)
                                        TimeText(slot.endTime, subTextColor)
                                    }
                                    h >= 38.dp -> {
                                        Text(text = "${slot.startTime}-${slot.endTime}", fontSize = 8.sp, color = subTextColor, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeText(text: String, color: Color) {
    Text(text = text, fontSize = 10.sp, color = color, style = TextStyle(lineHeight = 1.em))
}

/**
 * 计算扁平化的可调度项列表
 */
fun calculateSingleSchedulables(
    mergedCourses: List<MergedCourseBlock>,
    firstDayOfWeek: Int,
    showWeekends: Boolean
): List<ISingleSchedulable> {
    val list = mutableListOf<ISingleSchedulable>()
    mergedCourses.forEach { block ->
        val displayIdx = mapDayToDisplayIndex(block.day, firstDayOfWeek, showWeekends)
        if (displayIdx != -1) {
            val layoutInfo = block.nonActiveRanges.firstOrNull() ?: (0f to 1f)
            block.courses.firstOrNull()?.let { course ->
                list.add(object : ISingleSchedulable {
                    override val columnIndex = displayIdx
                    override val subColumnIndex = layoutInfo.first.toInt()
                    override val subColumnCount = layoutInfo.second.toInt()
                    override val startSection = block.startSection
                    override val endSection = block.endSection
                    override val courseWrapper = course
                    override val parentBlock = block.copy(courses = listOf(course))
                })
            }
        }
    }
    return list
}

fun rearrangeDays(originalDays: List<String>, firstDayOfWeek: Int): List<String> {
    val startIndex = (firstDayOfWeek - 1).coerceIn(0, 6)
    return originalDays.subList(startIndex, originalDays.size) + originalDays.subList(0, startIndex)
}

fun mapDayToDisplayIndex(courseDay: Int, firstDayOfWeek: Int, showWeekends: Boolean): Int {
    val idx = (courseDay - firstDayOfWeek + 7) % 7
    return if (idx >= if (showWeekends) 7 else 5) -1 else idx
}