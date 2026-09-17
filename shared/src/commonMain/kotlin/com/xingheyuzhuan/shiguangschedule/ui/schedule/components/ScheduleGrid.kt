package com.xingheyuzhuan.shiguangschedule.ui.schedule.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.ScheduleModeProto
import org.jetbrains.compose.resources.stringArrayResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.week_days_short_names

/**
 * 纯只读周课表网格组件，专注高效渲染与课程详情交互
 */
@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@Composable
fun ScheduleGrid(
    state: ScheduleGridState,
    viewState: ScheduleGridViewState,
    actions: ScheduleGridActions,
    style: ScheduleGridStyleComposed,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        val density = LocalDensity.current

        val pageTextColor = style.pageTextColor ?: MaterialTheme.colorScheme.onSurface
        val pageSubTextColor = pageTextColor.copy(alpha = 0.7f)
        val weekDays = stringArrayResource(Res.array.week_days_short_names).toList()
        val reorderedWeekDays = rearrangeDays(weekDays, viewState.firstDayOfWeek)
        val displayDays = if (viewState.showWeekends) reorderedWeekDays else reorderedWeekDays.take(5)

        val displayDaysCount = displayDays.size
        val is24HourMode = style.scheduleMode == ScheduleModeProto.TIME_24H_MODE
        val maxGridSections = if (is24HourMode) 24 else viewState.timeSlots.size

        val totalGridHeight = style.sectionHeight * maxGridSections
        val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        val strokeWidthPx = 1f

        val singleSchedulables = remember(viewState.mergedCourses, viewState.firstDayOfWeek, viewState.showWeekends) {
            calculateSingleSchedulables(viewState.mergedCourses, viewState.firstDayOfWeek, viewState.showWeekends)
        }
        val sectionHeightPx = with(density) { style.sectionHeight.toPx() }

        Column(Modifier.fillMaxSize()) {
            DayHeader(
                style = style,
                displayDays = displayDays,
                dates = viewState.dates,
                currentYear = viewState.currentYear,
                currentWeek = viewState.currentWeek,
                todayIndex = viewState.todayIndex,
                lineColor = gridLineColor,
                textColor = pageTextColor,
                subTextColor = pageSubTextColor,
                strokeWidthPx = strokeWidthPx
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(state = state.gridScrollState)
            ) {
                TimeColumn(
                    style = style,
                    timeSlots = viewState.timeSlots,
                    maxGridSections = maxGridSections,
                    is24HourMode = is24HourMode,
                    onTimeSlotClicked = { actions.onTimeSlotClicked() },
                    modifier = Modifier.height(totalGridHeight),
                    lineColor = gridLineColor,
                    currentSectionIndex = viewState.currentSectionIndex,
                    textColor = pageTextColor,
                    subTextColor = pageSubTextColor,
                    strokeWidthPx = strokeWidthPx
                )

                Layout(
                    content = {
                        singleSchedulables.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .padding(style.courseBlockOuterPadding)
                                    .pointerInput(item) {
                                        detectTapGestures(
                                            onTap = { actions.onCourseBlockClicked(item.parentBlock) }
                                        )
                                    }
                            ) {
                                CourseBlock(
                                    courseWrapper = item.courseWrapper,
                                    isVisualDemoted = item.parentBlock.isVisualDemoted,
                                    style = style,
                                    timeSlots = viewState.timeSlots
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .height(totalGridHeight)
                        .weight(1f)
                        .drawBehind {
                            if (style.hideGridLines) return@drawBehind
                            val cellWidth = size.width / displayDaysCount
                            for (i in 1..displayDaysCount) {
                                val x = i * cellWidth
                                drawLine(gridLineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokeWidthPx)
                            }
                            for (i in 1..maxGridSections) {
                                val y = i * sectionHeightPx
                                drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = strokeWidthPx)
                            }
                        }
                ) { measurables, constraints ->
                    val cellWidth = constraints.maxWidth / displayDaysCount

                    val placeables = measurables.mapIndexed { index, measurable ->
                        val item = singleSchedulables[index]
                        val originalHeightPx = ((item.endSection - item.startSection) * sectionHeightPx).toInt()
                        measurable.measure(
                            Constraints.fixed(
                                cellWidth / item.subColumnCount,
                                originalHeightPx
                            )
                        )
                    }

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeables.forEachIndexed { index, placeable ->
                            val item = singleSchedulables[index]
                            val xPosition = item.columnIndex * cellWidth + item.subColumnIndex * (cellWidth / item.subColumnCount)
                            val yPosition = (item.startSection * sectionHeightPx).toInt()
                            placeable.placeRelative(xPosition, yPosition)
                        }
                    }
                }
            }
        }
    }
}