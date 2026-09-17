package com.xingheyuzhuan.shiguangschedule.data.repository

import androidx.room3.Transaction
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseDao
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTable
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableConfig
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableDao
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWeekDao
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWithWeeks
import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeSlot
import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 课表数据仓库，负责处理所有与课表、课程相关的业务逻辑和数据操作。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class CourseTableRepository(
    private val courseTableDao: CourseTableDao,
    private val courseDao: CourseDao,
    private val courseWeekDao: CourseWeekDao,
    private val timeScheduleRepository: TimeScheduleRepository,
    private val appSettingsRepository: AppSettingsRepository
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // 当仓库被依赖注入创建时，自动触发保护/种子数据填充逻辑
        repositoryScope.launch {
            seedDefaultData()
        }
    }

    /**
     * 种入初始默认数据（当不存在任何课表时触发）
     */
    private suspend fun seedDefaultData() {
        if (courseTableDao.getAllCourseTables().first().isNotEmpty()) {
            return
        }

        val tableId = Uuid.random().toString()
        val defaultCourseTable = CourseTable(
            id = tableId,
            name = "我的课表",
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        courseTableDao.insert(defaultCourseTable)

        val defaultConfig = CourseTableConfig(
            courseTableId = tableId,
            showWeekends = false,
            semesterTotalWeeks = 20,
            firstDayOfWeek = 1
        )
        appSettingsRepository.insertOrUpdateCourseConfig(defaultConfig)

        val exclusiveTable = TimeTable(
            id = tableId,
            name = null,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            defaultClassDuration = 45,
            defaultBreakDuration = 10
        )
        val defaultTimeSlotsForNewTable = defaultTimeSlots.map {
            it.copy(timeTableId = tableId)
        }
        timeScheduleRepository.saveExclusiveTimeTable(exclusiveTable, defaultTimeSlotsForNewTable)

        println("数据库初始化数据已完成写入")
    }

    /**
     * 获取所有课表，返回一个数据流。
     */
    fun getAllCourseTables(): Flow<List<CourseTable>> {
        return courseTableDao.getAllCourseTables()
    }

    /**
     * 获取指定课表ID的完整课程（包含周数）。
     */
    fun getCoursesWithWeeksByTableId(tableId: String): Flow<List<CourseWithWeeks>> {
        return courseDao.getCoursesWithWeeksByTableId(tableId)
    }

    /**
     * 创建一个新的课表。
     * 负责生成 ID 并执行插入操作，并**同步**为新课表创建默认时间段和配置。
     *
     * @param name 新课表的名称
     */
    @Transaction
    suspend fun createNewCourseTable(name: String) {
        val newTable = CourseTable(
            id = Uuid.random().toString(),
            name = name,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        courseTableDao.insert(newTable)

        val exclusiveTable = TimeTable(
            id = newTable.id,
            name = null,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        val defaultTimeSlotsForNewTable = defaultTimeSlots.map {
            it.copy(timeTableId = newTable.id)
        }
        timeScheduleRepository.saveExclusiveTimeTable(exclusiveTable, defaultTimeSlotsForNewTable)

        // 3. 插入默认课表配置
        val newConfig = CourseTableConfig(courseTableId = newTable.id)
        appSettingsRepository.insertOrUpdateCourseConfig(newConfig)
    }

    /**
     * 更新一个课表。
     */
    suspend fun updateCourseTable(courseTable: CourseTable) {
        courseTableDao.update(courseTable)
    }

    /**
     * 删除一个课表，并确保至少保留一个。
     *
     * @return 如果删除成功返回 true，否则返回 false。
     */
    suspend fun deleteCourseTable(courseTable: CourseTable): Boolean {
        val allTables = courseTableDao.getAllCourseTables().first()
        if (allTables.size <= 1) {
            return false
        }
        courseTableDao.delete(courseTable)
        return true
    }

    /**
     * 专门用于根据课程ID更新其颜色索引。
     * 这是实现无效颜色自动修复机制所需的关键方法（用于历史数据迁移）。
     *
     * @param courseId 课程的唯一ID。
     * @param newColorInt 新的颜色索引值 (0 到 11)。
     */
    suspend fun updateCourseColor(courseId: String, newColorInt: Int) {
        courseDao.updateCourseColorById(courseId, newColorInt)
    }





    /**
     * 获取指定课表、周次和星期下的课程，并以数据流形式返回。
     * 这个方法专为 UI 层提供实时更新的数据。
     */
    fun getCoursesForDay(
        courseTableId: String,
        weekNumber: Int,
        day: Int
    ): Flow<List<CourseWithWeeks>> {
        // 直接调用底层的 DAO 方法
        return courseDao.getCoursesWithWeeksByDayAndWeek(
            courseTableId = courseTableId,
            day = day,
            weekNumber = weekNumber
        )
    }

    /**
     * 根据物理日期和配置，获取该周的所有课程。
     */
    fun getCoursesWithWeeksByDate(
        courseTableId: String,
        targetDate: LocalDate,
        config: CourseTableConfig
    ): Flow<List<CourseWithWeeks>> {
        val weekNumber = appSettingsRepository.getWeekIndexAtDate(
            targetDate = targetDate,
            startDateStr = config.semesterStartDate,
            firstDayOfWeekInt = config.firstDayOfWeek
        )

        // 如果周次为 null（说明没设开学日期）
        if (weekNumber == null) {
            return flowOf(emptyList())
        }

        // 调用 DAO 层的精准查询方法（按周次过滤）
        return courseDao.getCoursesWithWeeksByTableAndWeek(courseTableId, weekNumber)
    }
}

private val defaultTimeSlots = listOf(
    TimeSlot(number = 1, startTime = "08:00", endTime = "08:45", timeTableId = "placeholder"),
    TimeSlot(number = 2, startTime = "08:50", endTime = "09:35", timeTableId = "placeholder"),
    TimeSlot(number = 3, startTime = "09:50", endTime = "10:35", timeTableId = "placeholder"),
    TimeSlot(number = 4, startTime = "10:40", endTime = "11:25", timeTableId = "placeholder"),
    TimeSlot(number = 5, startTime = "11:30", endTime = "12:15", timeTableId = "placeholder"),
    TimeSlot(number = 6, startTime = "14:00", endTime = "14:45", timeTableId = "placeholder"),
    TimeSlot(number = 7, startTime = "14:50", endTime = "15:35", timeTableId = "placeholder"),
    TimeSlot(number = 8, startTime = "15:45", endTime = "16:30", timeTableId = "placeholder"),
    TimeSlot(number = 9, startTime = "16:35", endTime = "17:20", timeTableId = "placeholder"),
    TimeSlot(number = 10, startTime = "18:30", endTime = "19:15", timeTableId = "placeholder"),
    TimeSlot(number = 11, startTime = "19:20", endTime = "20:05", timeTableId = "placeholder"),
    TimeSlot(number = 12, startTime = "20:10", endTime = "20:55", timeTableId = "placeholder"),
    TimeSlot(number = 13, startTime = "21:10", endTime = "21:55", timeTableId = "placeholder")
)