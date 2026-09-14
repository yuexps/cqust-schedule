package com.xingheyuzhuan.shiguangschedule.data.api.cqust

import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport
import com.xingheyuzhuan.shiguangschedule.tool.cqustTripleDesEncrypt
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
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
    val timeSlots: List<CourseImportExport.TimeSlotJsonModel> = defaultCqustTimeSlots,
    val statusCode: Int? = null,
    val isTimeout: Boolean = false
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
    /** 优先使用 IPv6 API，不可用时回退到默认 IPv4 */
    private val BASE_URLS = listOf(
        "http://jwnew.cqust.edu.ex2.http.80.ipv6.cqust.edu.cn/eams",
        "http://jwnew.cqust.edu.cn/eams"
    )
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val httpClient = HttpClient {
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 8000
            socketTimeoutMillis = 15000
        }
    }

    /**
     * 执行学号+密码自动登录并抓取课表
     */
    suspend fun loginAndFetchCourses(
        studentId: String,
        passwordRaw: String,
        targetSemesterId: String? = null,
        notifyFallback: Boolean = true
    ): CqustImportResult {
        var lastResult = CqustImportResult(false, "教务系统连接失败")
        for ((index, baseUrl) in BASE_URLS.withIndex()) {
            if (index > 0 && notifyFallback) {
                val reason = when {
                    lastResult.statusCode != null -> "异常(${lastResult.statusCode})"
                    lastResult.isTimeout -> "超时"
                    else -> "失败"
                }
                ToastManager.show("IPv6 $reason，切换 IPv4 尝试中...")
            }
            lastResult = fetchCourses(baseUrl, studentId, passwordRaw, targetSemesterId)
            if (lastResult.success || lastResult.errorMessage?.contains("密码") == true) {
                return lastResult
            }
        }
        return lastResult
    }

    private suspend fun fetchCourses(
        baseUrl: String,
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

            // 1. 获取登录页并检测是否返回 200 OK
            val loginPageResp = httpClient.get("$baseUrl/login.action?cqustadminweb=1") {
                applyCommonHeaders()
            }
            if (loginPageResp.status != HttpStatusCode.OK) {
                return CqustImportResult(
                    success = false,
                    errorMessage = "教务登录页无法正常访问(HTTP ${loginPageResp.status.value})",
                    statusCode = loginPageResp.status.value
                )
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
            val loginResp = httpClient.post("$baseUrl/login.action") {
                applyCommonHeaders("$baseUrl/login.action?cqustadminweb=1")
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
            val homeResp = httpClient.get("$baseUrl/home.action") {
                applyCommonHeaders("$baseUrl/login.action")
            }
            updateCookies(homeResp)

            // 5. 访问 courseTableForStd.action 提取 ids 与当前学期
            val stdResp = httpClient.get("$baseUrl/courseTableForStd.action") {
                applyCommonHeaders("$baseUrl/home.action")
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
            val tableResp = httpClient.post("$baseUrl/courseTableForStd!courseTable.action") {
                applyCommonHeaders("$baseUrl/courseTableForStd.action")
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(tableBody)
            }
            updateCookies(tableResp)
            val tableHtml = tableResp.bodyAsText()

            if (!tableHtml.contains("TaskActivity") && !tableHtml.contains("未安排时间任务列表")) {
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
            val isTimeout = e is HttpRequestTimeoutException ||
                    e::class.simpleName?.contains("Timeout", ignoreCase = true) == true ||
                    e.message?.contains("timeout", ignoreCase = true) == true
            CqustImportResult(
                success = false,
                errorMessage = if (isTimeout) "网络连接超时" else "导入过程发生异常: ${e.message}",
                isTimeout = isTimeout
            )
        }
    }

    /**
     * 解析重庆科技大学课表 HTML 中嵌有的 TaskActivity 脚本及未安排时间任务列表
     */
    private fun parseCourseTable(html: String): List<CourseImportExport.ImportCourseJsonModel> {
        val mergedList = mutableListOf<CourseImportExport.ImportCourseJsonModel>()
        val cleanNameSuffixRegex = Regex("""\([A-Za-z0-9._-]+\)$""")

        val scriptMatch = Regex("""var\s+table0\s*=\s*new\s+CourseTable[\s\S]*?</script>""").find(html)
        if (scriptMatch != null) {
            val scriptContent = scriptMatch.value
            val lines = scriptContent.split('\n')
            var currentActivity: RawActivity? = null
            val rawSlots = mutableListOf<RawSlot>()

            val actRegex = Regex("""activity\s*=\s*new\s+TaskActivity\((.*)\);""")
            val indexRegex = Regex("""index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+);""")
            val stringItemRegex = Regex(""""([^"]*)"""")

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
        }

        // 解析未安排时间任务列表（实践、实训、待排课程）
        val unarrangedMatch = Regex("""未安排时间任务列表[\s\S]*?<table[^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE).find(html)
        if (unarrangedMatch != null) {
            val tableContent = unarrangedMatch.groupValues[1]
            val trRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
            val tdRegex = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
            val htmlTagRegex = Regex("""<[^>]+>""")
            val rangeRegex = Regex("""^(\d+)\s*[-~至]\s*(\d+)$""")

            for (trMatch in trRegex.findAll(tableContent)) {
                val trHtml = trMatch.groupValues[1]
                val tds = tdRegex.findAll(trHtml).map {
                    it.groupValues[1].replace(htmlTagRegex, "").trim()
                }.toList()

                // tds[0] 为序号，tds[2] 课程名，tds[5] 教师，tds[6] 周次
                if (tds.size >= 8 && tds[0].all { it.isDigit() } && tds[0].isNotEmpty()) {
                    val rawName = tds[2]
                    val cleanName = rawName.replace(cleanNameSuffixRegex, "").trim().ifEmpty { rawName }
                    val teacher = tds.getOrNull(5).orEmpty()
                    val weeksStr = tds.getOrNull(6).orEmpty()

                    val weeks = mutableListOf<Int>()
                    if (weeksStr.isNotEmpty()) {
                        val parts = weeksStr.split(',', '，')
                        for (p in parts) {
                            val trimmed = p.trim()
                            val rangeMatch = rangeRegex.find(trimmed)
                            if (rangeMatch != null) {
                                val start = rangeMatch.groupValues[1].toIntOrNull() ?: 0
                                val end = rangeMatch.groupValues[2].toIntOrNull() ?: 0
                                for (w in start..end) weeks.add(w)
                            } else {
                                trimmed.toIntOrNull()?.let { weeks.add(it) }
                            }
                        }
                    }

                    mergedList.add(
                        CourseImportExport.ImportCourseJsonModel(
                            name = cleanName,
                            teacher = teacher,
                            position = "待定",
                            day = 0,
                            startSection = 0,
                            endSection = 0,
                            weeks = weeks.distinct().sorted()
                        )
                    )
                }
            }
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
