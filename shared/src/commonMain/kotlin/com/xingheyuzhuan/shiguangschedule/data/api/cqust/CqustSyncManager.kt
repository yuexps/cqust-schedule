package com.xingheyuzhuan.shiguangschedule.data.api.cqust

import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTable
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableDao
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseConversionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 重科课表同步服务管理器
 * 负责后台调用教务接口拉取课程并安全导入本地数据库
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class CqustSyncManager(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseConversionRepository: CourseConversionRepository,
    private val courseTableDao: CourseTableDao
) {

    /**
     * 登录教务并同步导入课表
     *
     * @param studentId 学号
     * @param passwordRaw 密码
     * @return 成功返回导入的课程门数，失败返回异常
     */
    suspend fun syncCourses(
        studentId: String,
        passwordRaw: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val sid = studentId.trim()
        val pwd = passwordRaw.trim()

        if (sid.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("学号不能为空"))
        }
        if (pwd.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("密码不能为空"))
        }

        // 调用树维教务接口
        val result = CqustEamsImporter.loginAndFetchCourses(sid, pwd)
        if (!result.success) {
            return@withContext Result.failure(
                Exception(result.errorMessage ?: "教务登录或课表获取失败，请检查账号密码或校园网络")
            )
        }

        try {
            // 确保拥有默认课表
            val currentSettings = appSettingsRepository.getAppSettings().first()
            var tableId = currentSettings.currentCourseTableId
            if (tableId.isEmpty()) {
                val allTables = courseTableDao.getAllCourseTables().first()
                tableId = if (allTables.isNotEmpty()) {
                    allTables.first().id
                } else {
                    val newId = Uuid.random().toString()
                    courseTableDao.insert(
                        CourseTable(
                            id = newId,
                            name = "重科课表",
                            createdAt = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                    newId
                }
                appSettingsRepository.insertOrUpdateAppSettings(
                    currentSettings.copy(currentCourseTableId = tableId)
                )
            }

            // 导入课程与作息时间
            courseConversionRepository.importCoursesFromList(tableId, result.courses)
            courseConversionRepository.importTimeSlots(tableId, result.timeSlots)

            // 默认自动记住密码，保存重科登录凭证并刷新最后同步时间
            appSettingsRepository.updateCqustLoginInfo(
                studentId = sid,
                passwordRaw = pwd,
                semesterId = result.semesterId ?: "561"
            )

            Result.success(result.courses.size)
        } catch (e: Exception) {
            Result.failure(Exception("课表数据解析入库失败: ${e.message}", e))
        }
    }

    /**
     * 智能静默同步课表（限频防抖）
     * 仅当已登录且距离上次同步超过冷却时间（默认 6 小时）时，在后台异步拉取最新课表；
     * 若未联网或教务维护导致失败，静默忽略并完整保留本地数据，绝不打扰用户。
     *
     * @param cooldownHours 冷却时长（小时），默认 6 小时
     * @param force 是否强制跳过冷却期
     * @return Result<Boolean> true 表示实际触发并成功同步，false 表示处于冷却期未触发或同步失败
     */
    suspend fun silentSyncIfNeeded(
        cooldownHours: Long = 6L,
        force: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val settings = appSettingsRepository.getAppSettings().first()
            if (!settings.cqustIsLoggedIn || settings.cqustStudentId.isBlank() || settings.cqustPassword.isBlank()) {
                return@withContext Result.success(false)
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val lastSync = settings.cqustLastSyncTime
            val cooldownMillis = cooldownHours * 3600 * 1000L

            if (!force && (now - lastSync) < cooldownMillis) {
                return@withContext Result.success(false)
            }

            val syncResult = syncCourses(settings.cqustStudentId, settings.cqustPassword)
            if (syncResult.isSuccess) {
                appSettingsRepository.updateCqustLastSyncTime(now)
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (_: Exception) {
            Result.success(false)
        }
    }
}
