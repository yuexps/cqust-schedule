package com.xingheyuzhuan.shiguangschedule.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableConfig
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableConfigDao
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableDao
import com.xingheyuzhuan.shiguangschedule.data.model.AppSettingsModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 应用配置领域仓库
 *
 * 核心职责：
 * 1. 协调全局偏好设置 (DataStore) 与课表物理配置 (Room) 之间的数据流。
 * 2. 提供时间维度计算算法（周次偏移、日期回溯）。
 */
@Single
class AppSettingsRepository(
    @Named("AppSettings") private val dataStore: DataStore<Preferences>,
    private val courseTableDao: CourseTableDao,
    private val courseTableConfigDao: CourseTableConfigDao
) {
    private val DATE_FORMATTER = LocalDate.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()
    }

    /**
     * 课表配置模板
     * 当 DataStore 选中的课表在数据库中尚未初始化配置时，以此模板为基础进行创建。
     */
    private val COURSE_CONFIG_TEMPLATE = CourseTableConfig(
        courseTableId = "",
        showWeekends = false,
        semesterStartDate = null,
        semesterTotalWeeks = 20,
        firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber
    )

    // 应用全局设置 (DataStore)

    /**
     * 获取应用设置数据流。
     */
    fun getAppSettings(): Flow<AppSettingsModel> = dataStore.data.map { prefs ->
        val dbFirstTableId = courseTableDao.getFirstTableOnce()?.id ?: ""

        AppSettingsModel.fromPreferences(prefs, dbFirstTableId)
    }

    /**
     * 获取一次性的应用设置快照。
     */
    suspend fun getAppSettingsOnce(): AppSettingsModel {
        return getAppSettings().first()
    }

    /**
     * 更新应用设置。
     * 将对象解构并原子化地写入 DataStore。
     */
    suspend fun insertOrUpdateAppSettings(newSettings: AppSettingsModel) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_CURRENT_COURSE_TABLE_ID] = newSettings.currentCourseTableId
            prefs[AppSettingsModel.KEY_REMINDER_ENABLED] = newSettings.reminderEnabled
            prefs[AppSettingsModel.KEY_REMIND_BEFORE_MINUTES] = newSettings.remindBeforeMinutes
            prefs[AppSettingsModel.KEY_SKIPPED_DATES] = newSettings.skippedDates
            prefs[AppSettingsModel.KEY_AUTO_MODE_ENABLED] = newSettings.autoModeEnabled
            prefs[AppSettingsModel.KEY_AUTO_CONTROL_MODE] = newSettings.autoControlMode.value
            prefs[AppSettingsModel.KEY_COMPAT_WEARABLE_SYNC] = newSettings.compatWearableSync
            prefs[AppSettingsModel.KEY_SHOW_NON_CURRENT_WEEK_COURSES] = newSettings.showNonCurrentWeekCourses
            prefs[AppSettingsModel.KEY_START_SCREEN] = newSettings.startScreen.value
            prefs[AppSettingsModel.KEY_THEME_MODE] = newSettings.themeMode.value
            prefs[AppSettingsModel.KEY_USE_DYNAMIC_COLOR] = newSettings.useDynamicColor
            prefs[AppSettingsModel.KEY_CUSTOM_LIGHT_PRIMARY] = newSettings.customLightPrimary
            prefs[AppSettingsModel.KEY_CUSTOM_DARK_PRIMARY] = newSettings.customDarkPrimary
            prefs[AppSettingsModel.KEY_CQUST_STUDENT_ID] = newSettings.cqustStudentId
            prefs[AppSettingsModel.KEY_CQUST_PASSWORD] = newSettings.cqustPassword
            prefs[AppSettingsModel.KEY_CQUST_SEMESTER_ID] = newSettings.cqustSemesterId
            prefs[AppSettingsModel.KEY_CQUST_IS_LOGGED_IN] = newSettings.cqustIsLoggedIn
            prefs[AppSettingsModel.KEY_CQUST_LAST_SYNC_TIME] = newSettings.cqustLastSyncTime
            prefs[AppSettingsModel.KEY_AUTO_CHECK_UPDATE_ENABLED] = newSettings.autoCheckUpdateEnabled
            prefs[AppSettingsModel.KEY_LAST_UPDATE_CHECK_TIME] = newSettings.lastUpdateCheckTime
        }
    }

    // 课表具体物理配置 (Room)

    /**
     * 根据课表ID获取一次性配置快照。
     * 防御性保证：无论历史库数据为何值，读出的每周首日均锁定为周一。
     */
    suspend fun getCourseConfigOnce(tableId: String): CourseTableConfig? {
        return courseTableConfigDao.getConfigOnce(tableId)?.copy(firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber)
    }

    /**
     * 根据课表ID实时获取配置数据流。
     * 防御性保证：流中发射的每周首日均锁定为周一。
     */
    fun getCourseTableConfigFlow(courseTableId: String): Flow<CourseTableConfig?> {
        return courseTableConfigDao.getConfigById(courseTableId).map { config ->
            config?.copy(firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber)
        }
    }

    /**
     * 更新或插入特定课表的物理配置。
     * 强制锁定每周起始日为周一，并确保开学起始日期对齐到当周周一。
     */
    suspend fun insertOrUpdateCourseConfig(newConfig: CourseTableConfig) {
        val alignedStartDate = newConfig.semesterStartDate?.let { dateStr ->
            try {
                val parsed = LocalDate.parse(dateStr, DATE_FORMATTER)
                getPreviousOrSameDayOfWeek(parsed, DayOfWeek.MONDAY).format(DATE_FORMATTER)
            } catch (_: Exception) {
                dateStr
            }
        }
        val constrainedConfig = newConfig.copy(
            firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber,
            semesterStartDate = alignedStartDate
        )
        courseTableConfigDao.insertOrUpdate(constrainedConfig)
    }

    // 业务算法 (时间、周次计算)

    /**
     * 核心周次偏移算法。全系统基准已锁定周一。
     */
    fun getWeekIndexAtDate(
        targetDate: LocalDate,
        startDateStr: String?,
        firstDayOfWeekInt: Int = DayOfWeek.MONDAY.isoDayNumber
    ): Int? {
        if (startDateStr.isNullOrEmpty()) return null
        return try {
            val parsedStartDate = LocalDate.parse(startDateStr, DATE_FORMATTER)

            val alignedStartDate = getPreviousOrSameDayOfWeek(parsedStartDate, DayOfWeek.MONDAY)
            val alignedTargetDate = getPreviousOrSameDayOfWeek(targetDate, DayOfWeek.MONDAY)

            val diffDays = alignedTargetDate.toEpochDays() - alignedStartDate.toEpochDays()
            val diffWeeks = (diffDays / 7).toInt()
            diffWeeks + 1
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 基于当前数据库/DataStore状态计算当前自然周次。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun calculateCurrentWeekFromDb(): Flow<Int?> = getAppSettings().flatMapLatest { appSettings ->
        val currentCourseId = appSettings.currentCourseTableId.ifEmpty {
            return@flatMapLatest flowOf(null)
        }
        courseTableConfigDao.getConfigById(currentCourseId).map { config ->
            if (config == null) return@map null
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val rawWeek = getWeekIndexAtDate(
                targetDate = today,
                startDateStr = config.semesterStartDate,
                firstDayOfWeekInt = DayOfWeek.MONDAY.isoDayNumber
            ) ?: return@map null
            if (rawWeek in 1..config.semesterTotalWeeks) rawWeek else null
        }
    }

    /**
     * 根据目标周数反推开学日期。
     */
    suspend fun setSemesterStartDateFromWeek(week: Int?) {
        val appSettings = getAppSettingsOnce()
        val currentCourseId = appSettings.currentCourseTableId.ifEmpty { return }

        val currentConfig = courseTableConfigDao.getConfigOnce(currentCourseId)
            ?: COURSE_CONFIG_TEMPLATE.copy(courseTableId = currentCourseId)

        val newStartDate = if (week != null) {
            calculateSemesterStartDate(week, DayOfWeek.MONDAY.isoDayNumber)
        } else {
            null
        }

        val updatedConfig = currentConfig.copy(semesterStartDate = newStartDate)
        insertOrUpdateCourseConfig(updatedConfig)
    }

    /**
     * 设置当前课表的学期起始日期（传入毫秒时间戳）。
     */
    suspend fun setSemesterStartDate(dateMillis: Long) {
        val selectedDate = Instant.fromEpochMilliseconds(dateMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val alignedStartDate = getPreviousOrSameDayOfWeek(selectedDate, DayOfWeek.MONDAY)
        setSemesterStartDate(alignedStartDate.toString())
    }

    /**
     * 设置当前课表的学期起始日期（传入 YYYY-MM-DD 字符串）。
     */
    suspend fun setSemesterStartDate(dateStr: String) {
        val alignedDateStr = try {
            val parsed = LocalDate.parse(dateStr)
            getPreviousOrSameDayOfWeek(parsed, DayOfWeek.MONDAY).toString()
        } catch (_: Exception) {
            dateStr
        }

        val appSettings = getAppSettingsOnce()
        var currentCourseId = appSettings.currentCourseTableId
        if (currentCourseId.isEmpty()) {
            val firstTable = courseTableDao.getAllCourseTables().first().firstOrNull()
            if (firstTable != null) {
                currentCourseId = firstTable.id
                insertOrUpdateAppSettings(appSettings.copy(currentCourseTableId = currentCourseId))
            } else {
                return
            }
        }

        val currentConfig = courseTableConfigDao.getConfigOnce(currentCourseId)
            ?: COURSE_CONFIG_TEMPLATE.copy(courseTableId = currentCourseId)

        val updatedConfig = currentConfig.copy(semesterStartDate = alignedDateStr)
        insertOrUpdateCourseConfig(updatedConfig)
    }

    /**
     * 辅助函数：根据目标周数反推开学日期。
     */
    private fun calculateSemesterStartDate(week: Int, firstDayOfWeekInt: Int): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val firstDayOfWeek = DayOfWeek(firstDayOfWeekInt)
        val startOfThisWeek = getPreviousOrSameDayOfWeek(today, firstDayOfWeek)
        val daysToSubtract = (week - 1) * 7
        val semesterStartDate = LocalDate.fromEpochDays(startOfThisWeek.toEpochDays() - daysToSubtract)
        return semesterStartDate.format(DATE_FORMATTER)
    }

    /**
     * 对齐日期到指定每周首日的指定星期几。
     */
    private fun getPreviousOrSameDayOfWeek(date: LocalDate, targetDayOfWeek: DayOfWeek): LocalDate {
        val currentDay = date.dayOfWeek.isoDayNumber
        val targetDay = targetDayOfWeek.isoDayNumber
        val daysToSubtract = if (currentDay >= targetDay) {
            currentDay - targetDay
        } else {
            7 - (targetDay - currentDay)
        }
        return LocalDate.fromEpochDays(date.toEpochDays() - daysToSubtract)
    }

    /**
     * 更新重科登录状态及凭证
     */
    suspend fun updateCqustLoginInfo(
        studentId: String,
        passwordRaw: String,
        semesterId: String
    ) {
        dataStore.edit { preferences ->
            preferences[AppSettingsModel.KEY_CQUST_STUDENT_ID] = studentId
            preferences[AppSettingsModel.KEY_CQUST_PASSWORD] = passwordRaw
            preferences[AppSettingsModel.KEY_CQUST_SEMESTER_ID] = semesterId
            preferences[AppSettingsModel.KEY_CQUST_IS_LOGGED_IN] = true
            preferences[AppSettingsModel.KEY_CQUST_LAST_SYNC_TIME] = Clock.System.now().toEpochMilliseconds()
        }
    }

    /**
     * 更新上次成功同步课表的时间戳
     */
    suspend fun updateCqustLastSyncTime(timestamp: Long = Clock.System.now().toEpochMilliseconds()) {
        dataStore.edit { preferences ->
            preferences[AppSettingsModel.KEY_CQUST_LAST_SYNC_TIME] = timestamp
        }
    }

    /**
     * 退出重科登录，清除凭证
     */
    suspend fun clearCqustLoginInfo() {
        dataStore.edit { preferences ->
            preferences[AppSettingsModel.KEY_CQUST_STUDENT_ID] = ""
            preferences[AppSettingsModel.KEY_CQUST_PASSWORD] = ""
            preferences[AppSettingsModel.KEY_CQUST_SEMESTER_ID] = ""
            preferences[AppSettingsModel.KEY_CQUST_IS_LOGGED_IN] = false
            preferences[AppSettingsModel.KEY_CQUST_LAST_SYNC_TIME] = 0L
        }
    }

    /**
     * 更新是否开启课表更新后自动同步至系统日历
     */
    suspend fun updateAutoSyncToCalendar(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AppSettingsModel.KEY_AUTO_SYNC_TO_CALENDAR] = enabled
        }
    }

    /**
     * 更新系统日历提前提醒分钟数
     */
    suspend fun updateCalendarRemindBeforeMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[AppSettingsModel.KEY_CALENDAR_REMIND_BEFORE_MINUTES] = minutes
        }
    }
}