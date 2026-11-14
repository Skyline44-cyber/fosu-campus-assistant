package dev.campususage.fosu.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ChaoxingException(message: String) : Exception(message)

data class ChaoxingSectionTime(
    val section: Int,
    val start: String,
    val end: String
) {
    val label: String get() = "$start-$end"
}

data class ChaoxingLesson(
    val key: String,
    val name: String,
    val teacher: String,
    val room: String,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: Set<Int>
)

data class ChaoxingWeek(
    val week: Int,
    val maxWeek: Int?,
    val firstWeekDate: LocalDate?,
    val sectionTimes: List<ChaoxingSectionTime>,
    val lessons: List<ChaoxingLesson>
)

data class ChaoxingTimetable(
    val courses: List<Course>,
    val maxWeek: Int,
    val firstWeekDate: LocalDate?,
    val sectionTimes: List<String>,
    val observedWeeks: Set<Int>
)

/**
 * Parser for the response of
 * `POST https://kb.chaoxing.com/pc/curriculum/getMyLessons`.
 *
 * The endpoint returns the current week unless `week=N` is submitted. A full
 * term is therefore assembled by requesting every week and merging the same
 * lesson by its ID, day, section range, room and teacher.
 */
object ChaoxingParser {
    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun parseWeek(json: String, requestedWeek: Int): ChaoxingWeek {
        val root = runCatching { JSONObject(json) }.getOrElse {
            throw ChaoxingException("学习通返回的不是课表数据，登录状态可能已失效")
        }

        val result = when {
            root.has("result") -> root.optInt("result", -1)
            root.has("status") && root.opt("status") is Boolean ->
                if (root.optBoolean("status")) 1 else 0
            else -> 1
        }
        if (result == 0) {
            val message = firstString(root, "msg", "message", "error")
                .ifBlank { "学习通课表接口返回失败" }
            throw ChaoxingException(message)
        }

        val dataValue = root.opt("data")
        val dataObject = when (dataValue) {
            is JSONObject -> dataValue
            is String -> runCatching { JSONObject(dataValue) }.getOrNull()
            else -> null
        }
        val curriculum = dataObject?.optJSONObject("curriculum")
            ?: root.optJSONObject("curriculum")
            ?: JSONObject()

        val lessonsArray = firstArray(
            dataObject?.optJSONArray("lessonArray"),
            curriculum.optJSONArray("lessonArray"),
            dataObject?.optJSONArray("lessons"),
            curriculum.optJSONArray("lessons"),
            root.optJSONArray("lessonArray")
        )
        val maxWeek = firstInt(
            curriculum,
            "maxWeek", "maxWeekNum", "totalWeek", "weekCount", "totalWeeks"
        )?.takeIf { it > 0 }
            ?: firstInt(dataObject, "maxWeek", "maxWeekNum", "totalWeek", "weekCount")
        val firstWeekDate = parseDate(
            curriculum.opt("firstWeekDate") ?: curriculum.opt("firstWeekDateReal")
            ?: dataObject?.opt("firstWeekDate")
        )
        val sectionTimes = parseSectionTimes(
            curriculum.optJSONArray("lessonTimeConfigArray")
                ?: dataObject?.optJSONArray("lessonTimeConfigArray")
        )

        val lessons = mutableListOf<ChaoxingLesson>()
        if (lessonsArray != null) {
            for (index in 0 until lessonsArray.length()) {
                val raw = lessonsArray.optJSONObject(index) ?: continue
                val item = raw.optJSONObject("lesson") ?: raw
                parseLesson(item, requestedWeek)?.let { lessons += it }
            }
        }

        return ChaoxingWeek(
            week = requestedWeek,
            maxWeek = maxWeek,
            firstWeekDate = firstWeekDate,
            sectionTimes = sectionTimes,
            lessons = lessons
        )
    }

    fun merge(weeks: List<ChaoxingWeek>): ChaoxingTimetable {
        if (weeks.isEmpty()) throw ChaoxingException("没有拿到学习通课表数据")

        val firstWeekDate = weeks.firstNotNullOfOrNull { it.firstWeekDate }
        val times = weeks.firstNotNullOfOrNull { week ->
            week.sectionTimes.takeIf { it.isNotEmpty() }
        }.orEmpty()
        val normalizedTimes = TimetableSections.normalized(times.map { it.label })

        val merged = linkedMapOf<String, MergedLesson>()
        var maxWeek = weeks.maxOfOrNull { it.maxWeek ?: 0 } ?: 0
        weeks.forEach { week ->
            maxWeek = maxOf(maxWeek, week.week)
            week.lessons.forEach { lesson ->
                maxWeek = maxOf(maxWeek, lesson.weeks.maxOrNull() ?: 0)
                val current = merged[lesson.key]
                if (current == null) {
                    merged[lesson.key] = MergedLesson(lesson, lesson.weeks.toMutableSet())
                } else {
                    current.weeks += lesson.weeks
                }
            }
        }

        val courses = merged.values.map { item ->
            val lesson = item.lesson
            val weeks = WeekSpec.compress(item.weeks)
            Course(
                id = stableId(lesson.key),
                name = lesson.name,
                teacher = lesson.teacher,
                room = lesson.room,
                weekday = lesson.weekday,
                startSection = lesson.startSection,
                endSection = lesson.endSection,
                weeks = weeks.ifBlank { "1" },
                colorIndex = colorIndex(lesson.name)
            )
        }.sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.name }))

        return ChaoxingTimetable(
            courses = courses,
            maxWeek = maxWeek.coerceIn(1, 30),
            firstWeekDate = firstWeekDate,
            sectionTimes = normalizedTimes,
            observedWeeks = weeks.map { it.week }.toSet()
        )
    }

    fun encode(timetable: ChaoxingTimetable): String {
        val courseArray = JSONArray()
        timetable.courses.forEach { course ->
            courseArray.put(
                JSONObject()
                    .put("id", course.id)
                    .put("name", course.name)
                    .put("teacher", course.teacher)
                    .put("room", course.room)
                    .put("weekday", course.weekday)
                    .put("startSection", course.startSection)
                    .put("endSection", course.endSection)
                    .put("weeks", course.weeks)
                    .put("colorIndex", course.colorIndex)
            )
        }
        return JSONObject()
            .put("schema", "chaoxing-timetable-v1")
            .put("maxWeek", timetable.maxWeek)
            .put("firstWeekDate", timetable.firstWeekDate?.toString().orEmpty())
            .put("sectionTimes", JSONArray(timetable.sectionTimes))
            .put("courses", courseArray)
            .toString()
    }

    fun decode(json: String): ChaoxingTimetable? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optString("schema") != "chaoxing-timetable-v1") return null
        val array = root.optJSONArray("courses") ?: return null
        val courses = mutableListOf<Course>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").trim().take(40)
            val weekday = item.optInt("weekday", -1)
            val start = item.optInt("startSection", -1)
            val end = item.optInt("endSection", -1)
            if (name.isBlank() || weekday !in 1..7) continue
            if (start !in 1..TimetableSections.MAX_SECTION ||
                end !in start..TimetableSections.MAX_SECTION
            ) continue
            courses += Course(
                id = item.optString("id").ifBlank { stableId("$name|$weekday|$start|$end") },
                name = name,
                teacher = item.optString("teacher").trim().take(30),
                room = item.optString("room").trim().take(30),
                weekday = weekday,
                startSection = start,
                endSection = end,
                weeks = item.optString("weeks", "1-16").ifBlank { "1-16" }.take(40),
                colorIndex = item.optInt("colorIndex", colorIndex(name)).coerceIn(0, 7)
            )
        }
        val times = root.optJSONArray("sectionTimes")?.let { raw ->
            buildList {
                for (i in 0 until raw.length()) {
                    raw.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.orEmpty()
        val first = root.optString("firstWeekDate").takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return ChaoxingTimetable(
            courses = courses,
            maxWeek = root.optInt("maxWeek", 20).coerceIn(1, 30),
            firstWeekDate = first,
            sectionTimes = TimetableSections.normalized(times),
            observedWeeks = emptySet()
        )
    }

    private fun parseLesson(item: JSONObject, requestedWeek: Int): ChaoxingLesson? {
        val name = firstString(item, "name", "courseName", "displayCourseName", "title")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(40)
        if (name.isBlank()) return null

        val weekday = firstInt(item, "dayOfWeek", "weekDay", "weekday", "day") ?: return null
        if (weekday !in 1..7) return null

        val start = firstInt(
            item,
            "beginNumber", "startSection", "start", "begin", "sectionStart", "startNumber"
        ) ?: return null
        val length = firstInt(item, "length", "sectionLength", "span", "count") ?: 1
        val end = firstInt(item, "endNumber", "endSection", "end", "sectionEnd")
            ?: (start + length - 1)
        if (start !in 1..TimetableSections.MAX_SECTION ||
            end !in start..TimetableSections.MAX_SECTION
        ) return null

        val teacher = firstString(item, "teacherName", "teacher", "teachers", "teacherNames")
            .take(30)
        val room = firstString(item, "location", "room", "place", "classroom", "address")
            .take(30)
        val lessonId = firstString(item, "lessonId", "lessonID", "id")
        val weeks = linkedSetOf(requestedWeek)
        parseWeeks(item.opt("weeks") ?: item.opt("week") ?: item.opt("weekNum")).forEach { weeks += it }

        return ChaoxingLesson(
            key = listOf(
                lessonId.ifBlank { "no-id" },
                name,
                teacher,
                room,
                weekday.toString(),
                start.toString(),
                end.toString()
            ).joinToString("|"),
            name = name,
            teacher = teacher,
            room = room,
            weekday = weekday,
            startSection = start,
            endSection = end,
            weeks = weeks
        )
    }

    private fun parseSectionTimes(array: JSONArray?): List<ChaoxingSectionTime> {
        if (array == null) return emptyList()
        val parsed = mutableListOf<ChaoxingSectionTime>()
        for (index in 0 until array.length()) {
            parseSectionTime(array.opt(index), index + 1)?.let { parsed += it }
        }
        return parsed.distinctBy { it.section }.sortedBy { it.section }
    }

    private fun parseSectionTime(value: Any?, fallbackSection: Int): ChaoxingSectionTime? {
        return when (value) {
            is String -> {
                val normalized = TimetableSections.normalize(value) ?: return null
                ChaoxingSectionTime(
                    fallbackSection,
                    normalized.substringBefore('-'),
                    normalized.substringAfter('-')
                )
            }
            is JSONObject -> {
                val section = firstInt(value, "section", "index", "beginNumber", "number")
                    ?: fallbackSection
                val start = firstString(value, "startTime", "start", "beginTime", "start_time")
                val end = firstString(value, "endTime", "end", "finishTime", "end_time")
                val range = if (start.isNotBlank() && end.isNotBlank()) "$start-$end"
                else firstString(value, "time", "range", "label")
                val normalized = TimetableSections.normalize(range) ?: return null
                ChaoxingSectionTime(
                    section.coerceIn(1, TimetableSections.MAX_SECTION),
                    normalized.substringBefore('-'),
                    normalized.substringAfter('-')
                )
            }
            else -> null
        }
    }

    private fun parseWeeks(value: Any?): Set<Int> {
        return when (value) {
            is Number -> setOf(value.toInt())
            is JSONArray -> buildSet {
                for (i in 0 until value.length()) {
                    when (val item = value.opt(i)) {
                        is Number -> add(item.toInt())
                        is String -> addAll(WeekSpec.expand(item, 30))
                    }
                }
            }
            is String -> WeekSpec.expand(value, 30)
            else -> emptySet()
        }
    }

    private fun parseDate(value: Any?): LocalDate? {
        return when (value) {
            is Number -> Instant.ofEpochMilli(value.toLong()).atZone(chinaZone).toLocalDate()
            is String -> {
                val text = value.trim()
                if (text.isBlank()) return null
                if (text.length >= 12 && text.all { it.isDigit() }) {
                    Instant.ofEpochMilli(text.toLong()).atZone(chinaZone).toLocalDate()
                } else {
                    runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
                }
            }
            else -> null
        }
    }

    private fun firstString(item: JSONObject?, vararg keys: String): String {
        if (item == null) return ""
        for (key in keys) {
            if (!item.has(key) || item.isNull(key)) continue
            val value = item.opt(key)
            val text = when (value) {
                is JSONArray -> buildList {
                    for (i in 0 until value.length()) {
                        value.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }.joinToString(",")
                else -> value?.toString().orEmpty().trim()
            }
            if (text.isNotEmpty() && text != "null") return text
        }
        return ""
    }

    private fun firstInt(item: JSONObject?, vararg keys: String): Int? {
        if (item == null) return null
        for (key in keys) {
            if (!item.has(key) || item.isNull(key)) continue
            when (val value = item.opt(key)) {
                is Number -> return value.toInt()
                is String -> value.trim().toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun firstArray(vararg arrays: JSONArray?): JSONArray? =
        arrays.firstOrNull { it != null && it.length() > 0 }

    private fun stableId(key: String): String =
        UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun colorIndex(name: String): Int =
        (name.hashCode() and Int.MAX_VALUE) % 8

    private data class MergedLesson(
        val lesson: ChaoxingLesson,
        val weeks: MutableSet<Int>
    )
}
