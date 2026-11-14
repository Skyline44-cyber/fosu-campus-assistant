package dev.campususage.fosu.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.MonthDay
import java.util.UUID

data class ParsedElectricity(
    val date: LocalDate?,
    val meter: BigDecimal?,
    val balance: BigDecimal?,
    val room: String?
)

data class ParsedTimetable(
    val courses: List<Course>,
    val errors: List<String>
)

object ElectricityTextParser {
    private val fullDate = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})""")
    private val shortDate = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日?""")
    private val room = Regex("""(?:房间|宿舍|房号|寝室|宿舍号)\s*[:：]?\s*([A-Za-z0-9\u4e00-\u9fa5#\-]{1,24})""")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedElectricity? {
        if (text.isBlank()) return null
        val normalized = text
            .replace('：', ':')
            .replace('，', ',')
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")

        val meter = amountAfter(
            normalized,
            listOf("电表读数", "当前读数", "表底读数", "剩余电量", "可用电量", "剩余电量(kWh)", "剩余电量（kWh）")
        )
        val balance = amountAfter(
            normalized,
            listOf("剩余金额", "账户余额", "剩余电费", "可退金额", "余额")
        )

        val date = parseDate(normalized, today)
        val roomName = room.find(normalized)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.trimEnd('电', '费', '余', '额', '量', '度')
            ?.takeIf { it.isNotBlank() && it != "号" }

        if (meter == null && balance == null) return null
        return ParsedElectricity(date, meter, balance, roomName)
    }

    private fun amountAfter(text: String, labels: List<String>): BigDecimal? {
        for (label in labels) {
            val regex = Regex(Regex.escape(label) + """[^\d\-]{0,12}(-?\d+(?:\.\d+)?)""")
            val raw = regex.find(text)?.groupValues?.getOrNull(1) ?: continue
            raw.toBigDecimalOrNull()?.let { return it }
        }
        return null
    }

    private fun parseDate(text: String, today: LocalDate): LocalDate? {
        fullDate.find(text)?.let { match ->
            val (year, month, day) = match.destructured
            runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()?.let { return it }
        }
        shortDate.find(text)?.let { match ->
            val (month, day) = match.destructured
            val monthDay = runCatching { MonthDay.of(month.toInt(), day.toInt()) }.getOrNull() ?: return@let
            var candidate = monthDay.atYear(today.year)
            if (candidate.isAfter(today.plusDays(31))) candidate = candidate.minusYears(1)
            return candidate
        }
        return null
    }
}

object TimetableTextParser {
    fun parse(text: String): ParsedTimetable {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ParsedTimetable(emptyList(), listOf("导入内容为空"))
        }
        return if (trimmed.startsWith("[") || trimmed.startsWith("{")) parseJson(trimmed) else parseLines(trimmed)
    }

    private fun parseJson(text: String): ParsedTimetable {
        val errors = mutableListOf<String>()
        val courses = mutableListOf<Course>()
        val array = runCatching {
            if (text.startsWith("[")) JSONArray(text)
            else JSONObject(text).getJSONArray("courses")
        }.getOrElse {
            return ParsedTimetable(emptyList(), listOf("JSON 格式不正确：${it.message ?: "无法解析"}"))
        }

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item == null) {
                errors += "第 ${i + 1} 项不是对象"
                continue
            }
            val name = firstString(item, "name", "course", "课程", "课程名").trim()
            if (name.isBlank()) {
                errors += "第 ${i + 1} 项缺少课程名"
                continue
            }
            val weekday = parseWeekday(firstString(item, "weekday", "day", "星期", "周几"))
            if (weekday == null) {
                errors += "第 ${i + 1} 项星期无法识别"
                continue
            }
            val start = firstInt(item, "start", "startSection", "开始节")
            val end = firstInt(item, "end", "endSection", "结束节") ?: start
            if (start == null || end == null ||
                start !in 1..TimetableSections.MAX_SECTION ||
                end !in start..TimetableSections.MAX_SECTION
            ) {
                errors += "第 ${i + 1} 项节次不合法"
                continue
            }
            val weeks = firstString(item, "weeks", "week", "周次").ifBlank { "1-16" }
            val teacher = firstString(item, "teacher", "教师", "老师")
            val room = firstString(item, "room", "location", "地点", "教室")
            courses += Course(
                id = UUID.randomUUID().toString(),
                name = name.take(40),
                teacher = teacher.take(30),
                room = room.take(30),
                weekday = weekday,
                startSection = start,
                endSection = end,
                weeks = weeks.take(40),
                colorIndex = (i % 4)
            )
        }
        return ParsedTimetable(courses, errors)
    }

    private fun parseLines(text: String): ParsedTimetable {
        val errors = mutableListOf<String>()
        val courses = mutableListOf<Course>()
        val delimiter = Regex("""\s*\|+\s*|\t+""")
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val parts = line.split(delimiter).map { it.trim() }
            if (parts.size < 5) {
                errors += "第 ${index + 1} 行字段不足（至少 5 段）"
                return@forEachIndexed
            }
            val name = parts[0]
            if (name.isBlank()) {
                errors += "第 ${index + 1} 行课程名为空"
                return@forEachIndexed
            }
            val weekday = parseWeekday(parts[3])
            if (weekday == null) {
                errors += "第 ${index + 1} 行星期无法识别"
                return@forEachIndexed
            }
            val sections = parseSections(parts[4])
            if (sections == null) {
                errors += "第 ${index + 1} 行节次无法识别"
                return@forEachIndexed
            }
            val weeks = parts.getOrNull(5)?.takeIf { it.isNotBlank() } ?: "1-16"
            courses += Course(
                id = UUID.randomUUID().toString(),
                name = name.take(40),
                teacher = parts[1].take(30),
                room = parts[2].take(30),
                weekday = weekday,
                startSection = sections.first,
                endSection = sections.second,
                weeks = weeks.take(40),
                colorIndex = courses.size % 4
            )
        }
        return ParsedTimetable(courses, errors)
    }

    fun parseWeekday(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val compact = value.trim().lowercase()
            .replace("星期", "")
            .replace("礼拜", "")
            .replace("周", "")
        compact.toIntOrNull()?.let { return it.takeIf { day -> day in 1..7 } }
        return when (compact) {
            "一", "mon", "monday" -> 1
            "二", "tue", "tues", "tuesday" -> 2
            "三", "wed", "wednesday" -> 3
            "四", "thu", "thur", "thursday" -> 4
            "五", "fri", "friday" -> 5
            "六", "sat", "saturday" -> 6
            "日", "天", "sun", "sunday" -> 7
            else -> null
        }
    }

    private fun parseSections(value: String): Pair<Int, Int>? {
        val numbers = Regex("""\d+""").findAll(value)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        if (numbers.isEmpty()) return null
        val start = numbers.first()
        val end = numbers.getOrNull(1) ?: start
        if (start !in 1..TimetableSections.MAX_SECTION ||
            end !in start..TimetableSections.MAX_SECTION
        ) return null
        return start to end
    }

    private fun firstString(item: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (item.has(key) && !item.isNull(key)) {
                val value = item.optString(key).trim()
                if (value.isNotEmpty() && value != "null") return value
            }
        }
        return ""
    }

    private fun firstInt(item: JSONObject, vararg keys: String): Int? {
        for (key in keys) {
            if (!item.has(key) || item.isNull(key)) continue
            when (val value = item.opt(key)) {
                is Number -> return value.toInt()
                is String -> value.trim().toIntOrNull()?.let { return it }
            }
        }
        return null
    }
}
