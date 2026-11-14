package dev.campususage.fosu.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Calls the same timetable endpoint used by the Chaoxing web app after a
 * successful WebView login. The WebView owns the cookie jar; this client only
 * receives the cookie header and never sees a password.
 */
class ChaoxingClient(
    cookieHeader: String,
    private val userAgent: String
) {
    private val cookieHeader = cookieHeader.replace("\r", "").replace("\n", "").trim()

    fun fetchAll(onProgress: (week: Int, total: Int) -> Unit = { _, _ -> }): ChaoxingTimetable {
        if (cookieHeader.isBlank()) {
            throw ChaoxingException("还没有登录学习通，请先在页面中完成登录")
        }

        val meta = requestWeek(null)
        val maxWeek = (meta.maxWeek ?: 20).coerceIn(1, 30)
        onProgress(1, maxWeek)

        val weeks = mutableListOf<ChaoxingWeek>()
        for (week in 1..maxWeek) {
            onProgress(week, maxWeek)
            weeks += requestWeek(week)
        }

        val timetable = ChaoxingParser.merge(weeks)
        if (timetable.courses.isEmpty()) {
            throw ChaoxingException("学习通没有返回课程，请确认账号已绑定学生课表")
        }
        return timetable
    }

    private fun requestWeek(week: Int?): ChaoxingWeek {
        val body = if (week == null) "" else "week=$week"
        val primary = runCatching { execute("POST", body, week ?: 1) }.getOrElse { error ->
            if (week == null) throw error
            execute("GET", "", week)
        }
        return primary
    }

    private fun execute(method: String, form: String, requestedWeek: Int): ChaoxingWeek {
        val url = if (method == "GET" && form.isBlank()) {
            URL("$API_URL?week=$requestedWeek")
        } else {
            URL(API_URL)
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Cookie", cookieHeader)
            setRequestProperty("User-Agent", userAgent.ifBlank { DEFAULT_USER_AGENT })
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            setRequestProperty("Referer", "https://kb.chaoxing.com/pc/curriculum")
            setRequestProperty("Origin", "https://kb.chaoxing.com")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Cache-Control", "no-cache")
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }

        try {
            if (method == "POST") {
                connection.outputStream.use { output ->
                    output.write(form.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()

            if (code !in 200..299) {
                throw ChaoxingException("学习通接口 HTTP $code，请稍后重试")
            }
            if (text.isBlank()) {
                throw ChaoxingException("学习通接口没有返回内容")
            }
            if (text.contains("passport2.chaoxing.com") || text.contains("用户登录")) {
                throw ChaoxingException("学习通登录已失效，请重新登录")
            }
            return ChaoxingParser.parseWeek(text, requestedWeek)
        } catch (error: ChaoxingException) {
            throw error
        } catch (error: Exception) {
            throw ChaoxingException("连接学习通失败：${error.message ?: "网络异常"}")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API_URL = "https://kb.chaoxing.com/pc/curriculum/getMyLessons"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}
