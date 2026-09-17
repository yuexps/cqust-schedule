package com.xingheyuzhuan.shiguangschedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableConfig
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWithWeeks
import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeSlot
import com.xingheyuzhuan.shiguangschedule.data.model.AppSettingsModel
import com.xingheyuzhuan.shiguangschedule.data.model.ScheduleGridStyle
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseTableRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.StyleSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.TimeScheduleRepository
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.ScheduleGridStyleComposed.Companion.toComposedStyle
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.ScheduleModeProto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 课表展示块：封装单次或冲突课程
 * startSection/endSection：逻辑节次偏移量（0.0 代表网格最顶端：第一节课顶部 / 或者是24小时模式下的 00:00）
 */
data class MergedCourseBlock(
    val day: Int,
    val startSection: Float,
    val endSection: Float,
    val courses: List<CourseWithWeeks>,
    val needsProportionalRendering: Boolean = false,
    val isVisualDemoted: Boolean = false,
    val nonActiveRanges: List<Pair<Float, Float>> = emptyList(),
    val clusterCourses: List<CourseWithWeeks> = emptyList()
)

data class WeeklyScheduleUiState(
    val style: ScheduleGridStyle = ScheduleGridStyle(),
    val showWeekends: Boolean = false,
    val totalWeeks: Int = 20,
    val timeSlots: List<TimeSlot> = emptyList(),
    val courseCache: Map<String, List<MergedCourseBlock>> = emptyMap(),
    val currentMergedCourses: List<MergedCourseBlock> = emptyList(),
    val isSemesterSet: Boolean = false,
    val semesterStartDate: LocalDate? = null,
    val firstDayOfWeek: Int = DayOfWeek.MONDAY.isoDayNumber,
    val weekIndexInPager: Int? = null,
    val currentWeekNumber: Int? = null,
    val pagerMondayDate: LocalDate = getTodayLocalDate().startOfWeek(DayOfWeek.MONDAY),
    val currentSectionIndex: Int = -1,
    val daysUntilStart: Long = 0,
    val isReady: Boolean = false
)

/**
 * 规范化课程坐标的中间对象
 */
private data class NormalizedCourse(
    val raw: CourseWithWeeks,
    val start: Float,
    val end: Float
)

/**
 * 辅助函数：获取当前系统时区的当前日期
 */
private fun getTodayLocalDate(): LocalDate {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

/**
 * 辅助函数：获取当前系统时区的当前时间
 */
private fun getCurrentLocalTime(): LocalTime {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
}

/**
 * 辅助扩展：计算当前日期所在周的起始日期（周一/周日等）
 */
private fun LocalDate.startOfWeek(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
    val dayOfWeek = this.dayOfWeek.isoDayNumber
    val targetIso = firstDayOfWeek.isoDayNumber
    val diff = (dayOfWeek - targetIso + 7) % 7
    return this.minus(diff, DateTimeUnit.DAY)
}

/**
 * 辅助函数：格式化 LocalTime 为 HH:mm 格式
 */
private fun LocalTime.formatToHHmm(): String {
    val hourStr = hour.toString().padStart(2, '0')
    val minuteStr = minute.toString().padStart(2, '0')
    return "$hourStr:$minuteStr"
}

@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
@KoinViewModel
class WeeklyScheduleViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val timeScheduleRepository: TimeScheduleRepository,
    private val styleSettingsRepository: StyleSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyScheduleUiState())
    val uiState: StateFlow<WeeklyScheduleUiState> = _uiState.asStateFlow()

    private val _pagerMondayDate = MutableStateFlow(
        getTodayLocalDate().startOfWeek(DayOfWeek.MONDAY)
    )

    private val appSettingsFlow = appSettingsRepository.getAppSettings()
    private val styleFlow = styleSettingsRepository.styleFlow

    private val courseTableConfigFlow = appSettingsFlow.flatMapLatest { settings ->
        val tableId = settings.currentCourseTableId
        if (tableId.isNotEmpty()) {
            appSettingsRepository.getCourseTableConfigFlow(tableId)
        } else {
            flowOf(null)
        }
    }

    private val timeSlotsFlow = combine(
        appSettingsFlow,
        _pagerMondayDate
    ) { settings, pagerDate ->
        val tableId = settings.currentCourseTableId
        val today = getTodayLocalDate()
        val currentWeekMonday = today.startOfWeek(DayOfWeek.MONDAY)
        val isCurrentWeek = pagerDate == currentWeekMonday
        val targetDateForSidebar = if (isCurrentWeek) today else pagerDate
        tableId to targetDateForSidebar
    }.flatMapLatest { (tableId, targetDate) ->
        if (tableId.isNotEmpty()) {
            timeScheduleRepository.observeEffectiveTimeSlots(tableId, targetDate)
        } else {
            flowOf(emptyList())
        }
    }

    private val currentCoursesFlow = combine(
        _pagerMondayDate,
        appSettingsFlow,
        courseTableConfigFlow,
        styleFlow
    ) { date, settings, config, style ->
        val tableId = settings.currentCourseTableId
        val mode = style.toComposedStyle().scheduleMode

        if (config != null && tableId.isNotEmpty()) {
            val window = listOf(
                date.minus(1, DateTimeUnit.WEEK),
                date,
                date.plus(1, DateTimeUnit.WEEK)
            )

            val today = getTodayLocalDate()
            val currentWeekMonday = today.startOfWeek(DayOfWeek.MONDAY)

            combine(window.map { day ->
                val pageWeekNum = appSettingsRepository.getWeekIndexAtDate(
                    targetDate = day,
                    startDateStr = config.semesterStartDate,
                    firstDayOfWeekInt = config.firstDayOfWeek
                )

                val isWithinSemester = pageWeekNum != null && pageWeekNum in 1..config.semesterTotalWeeks

                val coursesFlow = if (settings.showNonCurrentWeekCourses && isWithinSemester) {
                    courseTableRepository.getCoursesWithWeeksByTableId(tableId).map { allCourses ->
                        allCourses.filter { cw ->
                            cw.weeks.any { it.weekNumber >= pageWeekNum }
                        }
                    }
                } else {
                    courseTableRepository.getCoursesWithWeeksByDate(tableId, day, config)
                }

                val isCurrentWeekWindow = day == currentWeekMonday
                val targetDateForSlots = if (isCurrentWeekWindow) today else day
                val daySlotsFlow = timeScheduleRepository.observeEffectiveTimeSlots(tableId, targetDateForSlots)

                combine(coursesFlow, daySlotsFlow) { courses, daySlots ->
                    day.toString() to mergeCourses(courses, daySlots, pageWeekNum ?: -1, mode)
                }
            }) { results -> results.toMap() }
        } else {
            flowOf(emptyMap())
        }
    }.flatMapLatest { it }

    init {
        viewModelScope.launch {
            val configAndTimeFlow = combine(
                appSettingsFlow,
                courseTableConfigFlow,
                styleFlow,
                _pagerMondayDate
            ) { settings, config, style, mondayDate ->
                ScheduleConfigPackage(settings, config, style, mondayDate)
            }

            combine(configAndTimeFlow, currentCoursesFlow, timeSlotsFlow) { configPkg, cache, timeSlots ->
                val config = configPkg.config
                val startDate = config?.semesterStartDate?.takeIf { it.isNotBlank() }?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (e: Exception) {
                        null
                    }
                }
                val firstDayOfWeekInt = config?.firstDayOfWeek ?: DayOfWeek.MONDAY.isoDayNumber
                val totalWeeks = config?.semesterTotalWeeks ?: 20
                val today = getTodayLocalDate()

                val currentWeekNum = appSettingsRepository.getWeekIndexAtDate(
                    targetDate = today,
                    startDateStr = config?.semesterStartDate,
                    firstDayOfWeekInt = firstDayOfWeekInt
                )

                val weekIndex = appSettingsRepository.getWeekIndexAtDate(
                    targetDate = configPkg.mondayDate,
                    startDateStr = config?.semesterStartDate,
                    firstDayOfWeekInt = firstDayOfWeekInt
                )

                val currentSectionIndex = calculateCurrentSectionIndex(timeSlots)

                val daysUntil = if (startDate != null && today < startDate) {
                    today.daysUntil(startDate).toLong()
                } else 0L

                val currentWeekCourses = cache[configPkg.mondayDate.toString()] ?: emptyList()
                fixInvalidCourseColors(currentWeekCourses.flatMap { it.courses }, configPkg.style)

                val previousState = _uiState.value

                WeeklyScheduleUiState(
                    style = configPkg.style,
                    showWeekends = config?.showWeekends ?: false,
                    totalWeeks = totalWeeks,
                    courseCache = cache,
                    currentMergedCourses = cache[configPkg.mondayDate.toString()] ?: emptyList(),
                    timeSlots = timeSlots,
                    isSemesterSet = startDate != null,
                    semesterStartDate = startDate,
                    firstDayOfWeek = firstDayOfWeekInt,
                    weekIndexInPager = weekIndex,
                    currentWeekNumber = currentWeekNum,
                    pagerMondayDate = configPkg.mondayDate,
                    currentSectionIndex = currentSectionIndex,
                    daysUntilStart = daysUntil,
                    isReady = true
                )
            }.collect { _uiState.value = it }
        }
    }

    private fun calculateCurrentSectionIndex(timeSlots: List<TimeSlot>): Int {
        if (timeSlots.isEmpty()) return -1
        val now = getCurrentLocalTime()
        val currentMinutes = now.hour * 60 + now.minute

        timeSlots.forEachIndexed { index, slot ->
            val startParts = slot.startTime.split(":")
            val endParts = slot.endTime.split(":")

            if (startParts.size == 2 && endParts.size == 2) {
                val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
                val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()

                if (currentMinutes in startMinutes until endMinutes) {
                    return index + 1
                }
            }
        }
        return -1
    }

    fun updatePagerDate(newDate: LocalDate) = _pagerMondayDate.update { newDate }

    fun switchCourseTable(tableId: String) {
        viewModelScope.launch {
            val currentSettings = appSettingsRepository.getAppSettingsOnce()
            val newSettings = currentSettings.copy(currentCourseTableId = tableId)
            appSettingsRepository.insertOrUpdateAppSettings(newSettings)
        }
    }

    private fun fixInvalidCourseColors(courses: List<CourseWithWeeks>, style: ScheduleGridStyle) {
        viewModelScope.launch {
            val validRange = style.courseColorMaps.indices
            courses.forEach { cw ->
                if (cw.course.colorInt !in validRange) {
                    courseTableRepository.updateCourseColor(cw.course.id, style.generateRandomColorIndex())
                }
            }
        }
    }

    /**
     * 核心统一时间换算器：将任意 [LocalTime] 转化为网格上的 Float 纵坐标
     * @return 距离网格最顶部的浮点偏置量（1.0f 代表第 1 个格子的顶部起点）
     */
    private fun timeToGridScale(
        time: LocalTime,
        timeSlots: List<TimeSlot>,
        mode: ScheduleModeProto
    ): Float {
        return when (mode) {
            ScheduleModeProto.TIME_24H_MODE -> {
                val currentMinutes = time.hour * 60 + time.minute
                val hourOffset = currentMinutes.toFloat() / 60f
                1.0f + hourOffset
            }
            ScheduleModeProto.SECTION_MODE -> {
                if (timeSlots.isEmpty()) return 1.0f
                val sortedSlots = timeSlots.sortedBy { it.number }

                val firstSlotStart = LocalTime.parse(sortedSlots.first().startTime)
                val lastSlotEnd = LocalTime.parse(sortedSlots.last().endTime)

                if (time <= firstSlotStart) return 1.0f
                if (time >= lastSlotEnd) return (sortedSlots.size + 1).toFloat()

                val currentSlot = sortedSlots.find {
                    val s = LocalTime.parse(it.startTime)
                    val e = LocalTime.parse(it.endTime)
                    time in s..e
                }

                if (currentSlot != null) {
                    val sTime = LocalTime.parse(currentSlot.startTime)
                    val eTime = LocalTime.parse(currentSlot.endTime)
                    val duration = (eTime.toSecondOfDay() - sTime.toSecondOfDay()) / 60
                    val safeDuration = if (duration <= 0) 1 else duration
                    val elapsedMinutes = (time.toSecondOfDay() - sTime.toSecondOfDay()) / 60
                    return currentSlot.number.toFloat() + (elapsedMinutes.toFloat() / safeDuration)
                }

                val nextSlot = sortedSlots.find { LocalTime.parse(it.startTime) > time }
                nextSlot?.number?.toFloat() ?: (sortedSlots.size + 1).toFloat()
            }
        }
    }

    /**
     * 无损展平排版调度引擎保持原有完美逻辑
     */
    fun mergeCourses(
        courses: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>,
        currentWeek: Int,
        mode: ScheduleModeProto = ScheduleModeProto.SECTION_MODE
    ): List<MergedCourseBlock> {
        if (timeSlots.isEmpty() && mode == ScheduleModeProto.SECTION_MODE) return emptyList()

        val maxSection = if (mode == ScheduleModeProto.TIME_24H_MODE) 24f else timeSlots.size.toFloat()
        val limit = maxSection + 1.0f
        val minSafeHeight = if (mode == ScheduleModeProto.TIME_24H_MODE) 0.0f else 0.3f

        val normalizedList = courses.mapNotNull { cw ->
            try {
                val c = cw.course

                val (sTime, eTime) = if (c.isCustomTime) {
                    LocalTime.parse(c.customStartTime ?: return@mapNotNull null) to
                            LocalTime.parse(c.customEndTime ?: return@mapNotNull null)
                } else {
                    val startSlot = timeSlots.find { it.number == c.startSection } ?: return@mapNotNull null
                    val endSlot = timeSlots.find { it.number == c.endSection } ?: return@mapNotNull null
                    LocalTime.parse(startSlot.startTime) to LocalTime.parse(endSlot.endTime)
                }

                val s = timeToGridScale(sTime, timeSlots, mode)
                val e = timeToGridScale(eTime, timeSlots, mode)

                var finalStart = s
                var finalEnd = e
                if (finalStart >= limit) {
                    finalEnd = limit
                    finalStart = limit - minSafeHeight
                } else if (finalEnd <= 1.0f) {
                    finalStart = 1.0f
                    finalEnd = 1.0f + minSafeHeight
                }

                if (finalEnd - finalStart < minSafeHeight) {
                    if (finalEnd + minSafeHeight <= limit) {
                        finalEnd = finalStart + minSafeHeight
                    } else {
                        finalStart = finalEnd - minSafeHeight
                    }
                }

                NormalizedCourse(cw, finalStart.coerceIn(1.0f, limit - 0.1f), finalEnd.coerceIn(1.0f + 0.1f, limit))
            } catch (e: Exception) { null }
        }

        val result = mutableListOf<MergedCourseBlock>()

        normalizedList.groupBy { it.raw.course.day }.forEach { (day, dailyCourses) ->
            if (dailyCourses.isEmpty()) return@forEach

            val sorted = dailyCourses.sortedWith(
                compareBy<NormalizedCourse> { it.start }.thenByDescending { it.end - it.start }
            )

            val currentClusters = mutableListOf<MutableList<NormalizedCourse>>()

            for (item in sorted) {
                val targetCluster = currentClusters.find { cluster ->
                    cluster.any { existing ->
                        item.start < existing.end - 0.01f && item.end > existing.start + 0.01f
                    }
                }
                if (targetCluster != null) {
                    targetCluster.add(item)
                } else {
                    currentClusters.add(mutableListOf(item))
                }
            }

            for (cluster in currentClusters) {
                val columnEnds = mutableListOf<Float>()
                val itemToColumnIndex = mutableMapOf<NormalizedCourse, Int>()

                for (item in cluster) {
                    var assignedIndex = -1
                    for (i in columnEnds.indices) {
                        if (columnEnds[i] <= item.start + 0.01f) {
                            assignedIndex = i
                            columnEnds[i] = item.end
                            break
                        }
                    }
                    if (assignedIndex == -1) {
                        columnEnds.add(item.end)
                        assignedIndex = columnEnds.size - 1
                    }
                    itemToColumnIndex[item] = assignedIndex
                }

                val sortedClusterCourses = cluster.sortedWith(
                    compareBy<NormalizedCourse> { it.start }
                        .thenBy { itemToColumnIndex[it] ?: 0 }
                ).map { it.raw }

                val totalSubColumns = columnEnds.size

                for (item in cluster) {
                    val cw = item.raw
                    val isCurrentWeekActive = cw.weeks.any { it.weekNumber == currentWeek }
                    val myColumnIndex = itemToColumnIndex[item] ?: 0

                    result.add(
                        MergedCourseBlock(
                            day = day,
                            startSection = (item.start - 1f).coerceIn(0f, maxSection),
                            endSection = (item.end - 1f).coerceIn(0f, maxSection),
                            courses = listOf(cw),
                            needsProportionalRendering = (mode == ScheduleModeProto.TIME_24H_MODE) || cw.course.isCustomTime,
                            isVisualDemoted = !isCurrentWeekActive,
                            nonActiveRanges = listOf(myColumnIndex.toFloat() to totalSubColumns.toFloat()),
                            clusterCourses = sortedClusterCourses
                        )
                    )
                }
            }
        }
        return result
    }

    /**
     * 保存用户选定的学期起始日期
     */
    fun setSemesterStartDate(dateMillis: Long) {
        viewModelScope.launch {
            appSettingsRepository.setSemesterStartDate(dateMillis)
        }
    }
}

private data class ScheduleConfigPackage(
    val settings: AppSettingsModel,
    val config: CourseTableConfig?,
    val style: ScheduleGridStyle,
    val mondayDate: LocalDate
)