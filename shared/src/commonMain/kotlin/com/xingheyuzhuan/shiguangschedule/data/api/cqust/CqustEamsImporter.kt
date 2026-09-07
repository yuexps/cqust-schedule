package com.xingheyuzhuan.shiguangschedule.data.api.cqust

import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport
import com.xingheyuzhuan.shiguangschedule.tool.cqustTripleDesEncrypt
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * 重庆科技大学树维教务系统导入结果
 */
data class CqustImportResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val studentId: String? = null,
    val semesterId: String? = null,
    val courses: List<CourseImportExport.ImportCourseJsonModel> = emptyList(),
    val timeSlots: List<CourseImportExport.TimeSlotJsonModel> = defaultCqustTimeSlots
)

/**
 * 重庆科技大学标准 11 节作息时间表
 */
val defaultCqustTimeSlots = listOf(
    CourseImportExport.TimeSlotJsonModel(1, "08:30", "09:15"),
    CourseImportExport.TimeSlotJsonModel(2, "09:25", "10:10"),
    CourseImportExport.TimeSlotJsonModel(3, "10:30", "11:15"),
    CourseImportExport.TimeSlotJsonModel(4, "11:25", "12:10"),
    CourseImportExport.TimeSlotJsonModel(5, "14:00", "14:45"),
    CourseImportExport.TimeSlotJsonModel(6, "14:55", "15:40"),
    CourseImportExport.TimeSlotJsonModel(7, "16:00", "16:45"),
    CourseImportExport.TimeSlotJsonModel(8, "16:55", "17:40"),
    CourseImportExport.TimeSlotJsonModel(9, "19:00", "19:45"),
    CourseImportExport.TimeSlotJsonModel(10, "19:55", "20:40"),
    CourseImportExport.TimeSlotJsonModel(11, "20:50", "21:35")
)

/**
 * 重庆科技大学树维教务系统（EAMS）全自动登录与课表解析器
 */
object CqustEamsImporter {
    private const val BASE_URL = "http://jwnew.cqust.edu.cn/eams"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val httpClient = HttpClient {
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
        }
    }

    /**
     * 执行学号+密码自动登录并抓取课表
     */
    suspend fun loginAndFetchCourses(
        studentId: String,
        passwordRaw: String,
        targetSemesterId: String? = null
    ): CqustImportResult {
        return try {
            val cookieMap = mutableMapOf<String, String>()

            fun updateCookies(response: HttpResponse) {
                val setCookies = response.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
                for (sc in setCookies) {
                    val firstPart = sc.substringBefore(';')
                    val eqIndex = firstPart.indexOf('=')
                    if (eqIndex > 0) {
                        val k = firstPart.substring(0, eqIndex).trim()
                        val v = firstPart.substring(eqIndex + 1).trim()
                        cookieMap[k] = v
                    }
                }
            }

            fun HttpRequestBuilder.applyCommonHeaders(referer: String? = null) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
                if (referer != null) {
                    header(HttpHeaders.Referrer, referer)
                }
                val cookieStr = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                if (cookieStr.isNotEmpty()) {
                    header(HttpHeaders.Cookie, cookieStr)
                }
            }

            // 1. 获取登录页并提取动态 salt 密钥
            val loginPageResp = httpClient.get("$BASE_URL/login.action?cqustadminweb=1") {
                applyCommonHeaders()
            }
            updateCookies(loginPageResp)
            val loginPageHtml = loginPageResp.bodyAsText()

            val saltRegex = Regex("""tripleDesEncrypt\(.*?,\s*['"]([^'"]+)['"]\)""")
            val saltMatch = saltRegex.find(loginPageHtml)
                ?: return CqustImportResult(false, "未能从教务登录页提取加密密钥，请检查校园网连接。")
            val salt = saltMatch.groupValues[1]

            // 2. 3DES 加密密码
            val encryptedPassword = cqustTripleDesEncrypt(passwordRaw, salt)

            // 3. 提交登录表单
            val postBody = "username=${studentId.encodeURLParameter()}&password=${encryptedPassword.encodeURLParameter()}&encodedPassword="
            val loginResp = httpClient.post("$BASE_URL/login.action") {
                applyCommonHeaders("$BASE_URL/login.action?cqustadminweb=1")
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(postBody)
            }
            updateCookies(loginResp)

            if (loginResp.status != HttpStatusCode.Found && loginResp.status != HttpStatusCode.OK) {
                return CqustImportResult(false, "登录失败：状态码 ${loginResp.status}，请核对学号和密码。")
            }

            val location = loginResp.headers[HttpHeaders.Location] ?: ""
            val loginRespText = loginResp.bodyAsText()

            // 提取教务系统标准 actionError 错误容器中的提示文案
            val actionErrorMatch = Regex("""<div[^>]*class=["'][^"']*actionError[^"']*["'][^>]*>[\s\S]*?<span>([^<]+)</span>""", RegexOption.IGNORE_CASE).find(loginRespText)
            val serverErrorMessage = actionErrorMatch?.groupValues?.get(1)?.trim()

            if (!serverErrorMessage.isNullOrBlank()) {
                return CqustImportResult(false, "登录失败：$serverErrorMessage，请核对学号或密码。")
            }

            if ((location.contains("login.action") && !location.contains("home.action")) ||
                loginRespText.contains("actionError") ||
                loginRespText.contains("ui-state-error") ||
                loginRespText.contains("密码错误") ||
                loginRespText.contains("用户名或密码错误") ||
                loginRespText.contains("账户不存在") ||
                loginRespText.contains("用户不存在")
            ) {
                return CqustImportResult(false, "学号或密码错误，请重新确认后输入。")
            }

            // 4. 访问首页初始化会话
            val homeResp = httpClient.get("$BASE_URL/home.action") {
                applyCommonHeaders("$BASE_URL/login.action")
            }
            updateCookies(homeResp)

            // 5. 访问 courseTableForStd.action 提取 ids 与当前学期
            val stdResp = httpClient.get("$BASE_URL/courseTableForStd.action") {
                applyCommonHeaders("$BASE_URL/home.action")
            }
            updateCookies(stdResp)
            val stdHtml = stdResp.bodyAsText()

            val idsRegex = Regex("""bg\.form\.addInput\(form,\s*["']ids["'],\s*["'](\d+)["']\)""")
            val idsMatch = idsRegex.find(stdHtml)
                ?: return CqustImportResult(false, "未能获取到学生 ID（ids），可能权限受限或登录超时。")
            val ids = idsMatch.groupValues[1]

            val defaultSemesterRegex = Regex("""value:\s*["'](\d+)["']""")
            val detectedSemesterId = targetSemesterId ?: defaultSemesterRegex.find(stdHtml)?.groupValues?.get(1) ?: "561"

            // 6. 请求课表数据
            val tableBody = "ignoreHead=1&setting.kind=std&startWeek=1&project.id=1&semester.id=$detectedSemesterId&ids=$ids"
            val tableResp = httpClient.post("$BASE_URL/courseTableForStd!courseTable.action") {
                applyCommonHeaders("$BASE_URL/courseTableForStd.action")
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(tableBody)
            }
            updateCookies(tableResp)
            val tableHtml = tableResp.bodyAsText()

            if (!tableHtml.contains("TaskActivity")) {
                return CqustImportResult(false, "未获取到课程数据，所选学期可能无排课记录。")
            }

            // 7. 解析课表 HTML
            val parsedCourses = parseCourseTable(tableHtml)

            CqustImportResult(
                success = true,
                studentId = studentId,
                semesterId = detectedSemesterId,
                courses = parsedCourses,
                timeSlots = defaultCqustTimeSlots
            )
        } catch (e: Exception) {
            CqustImportResult(false, "导入过程发生异常: ${e.message}")
        }
    }

    /**
     * 解析重庆科技大学课表 HTML 中嵌有的 TaskActivity 脚本
     */
    private fun parseCourseTable(html: String): List<CourseImportExport.ImportCourseJsonModel> {
        val scriptMatch = Regex("""var\s+table0\s*=\s*new\s+CourseTable[\s\S]*?</script>""").find(html)
            ?: return emptyList()
        val scriptContent = scriptMatch.value

        val lines = scriptContent.split('\n')
        var currentActivity: RawActivity? = null
        val rawSlots = mutableListOf<RawSlot>()

        val actRegex = Regex("""activity\s*=\s*new\s+TaskActivity\((.*)\);""")
        val indexRegex = Regex("""index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+);""")
        val stringItemRegex = Regex(""""([^"]*)"""")
        val cleanNameSuffixRegex = Regex("""\([A-Za-z0-9._-]+\)$""")

        for (rawLine in lines) {
            val line = rawLine.trim()
            val actFound = actRegex.find(line)
            if (actFound != null) {
                val argMatches = stringItemRegex.findAll(actFound.groupValues[1]).map { it.groupValues[1] }.toList()
                if (argMatches.size >= 7) {
                    val rawName = argMatches[3]
                    val cleanName = rawName.replace(cleanNameSuffixRegex, "").trim()
                    currentActivity = RawActivity(
                        teacher = argMatches[1],
                        name = cleanName.ifEmpty { rawName },
                        room = argMatches[5],
                        weeksStr = argMatches[6]
                    )
                }
            }

            val indexFound = indexRegex.find(line)
            if (indexFound != null && currentActivity != null) {
                val dayOfWeek = indexFound.groupValues[1].toIntOrNull() ?: 0 // 0=周一, 6=周日
                val unitIndex = indexFound.groupValues[2].toIntOrNull() ?: 0 // 0=第1节
                val day = dayOfWeek + 1
                val section = unitIndex + 1

                val weeks = mutableListOf<Int>()
                val weeksStr = currentActivity.weeksStr
                for (w in 1 until weeksStr.length) {
                    if (weeksStr[w] == '1') {
                        weeks.add(w)
                    }
                }

                rawSlots.add(
                    RawSlot(
                        name = currentActivity.name,
                        teacher = currentActivity.teacher,
                        position = currentActivity.room,
                        day = day,
                        section = section,
                        weeks = weeks
                    )
                )
            }
        }

        // 合并连续节次
        val groups = rawSlots.groupBy { "${it.name}|${it.teacher}|${it.position}|${it.day}|${it.weeks.joinToString(",")}" }
        val mergedList = mutableListOf<CourseImportExport.ImportCourseJsonModel>()

        for ((_, slots) in groups) {
            val sorted = slots.sortedBy { it.section }
            if (sorted.isEmpty()) continue

            var startSec = sorted[0].section
            var endSec = sorted[0].section

            for (i in 1 until sorted.size) {
                if (sorted[i].section == endSec + 1) {
                    endSec = sorted[i].section
                } else {
                    mergedList.add(
                        CourseImportExport.ImportCourseJsonModel(
                            name = sorted[0].name,
                            teacher = sorted[0].teacher,
                            position = sorted[0].position,
                            day = sorted[0].day,
                            startSection = startSec,
                            endSection = endSec,
                            weeks = sorted[0].weeks
                        )
                    )
                    startSec = sorted[i].section
                    endSec = sorted[i].section
                }
            }

            mergedList.add(
                CourseImportExport.ImportCourseJsonModel(
                    name = sorted[0].name,
                    teacher = sorted[0].teacher,
                    position = sorted[0].position,
                    day = sorted[0].day,
                    startSection = startSec,
                    endSection = endSec,
                    weeks = sorted[0].weeks
                )
            )
        }

        return mergedList
    }

    private data class RawActivity(
        val teacher: String,
        val name: String,
        val room: String,
        val weeksStr: String
    )

    private data class RawSlot(
        val name: String,
        val teacher: String,
        val position: String,
        val day: Int,
        val section: Int,
        val weeks: List<Int>
    )
}
