package dev.campususage.fosu.data

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Campus(val label: String) {
    XIANXI("仙溪校区"),
    JIANGWAN("江湾校区"),
    HEBIN("河滨校区");

    companion object {
        fun from(value: String?): Campus = entries.firstOrNull { it.name == value } ?: XIANXI
    }
}

enum class ReadingSource(val label: String) {
    OFFICIAL("官方导入"),
    MANUAL("手记"),
    IMPORTED("文本导入")
}

data class Settings(
    val roomName: String = "",
    val campus: Campus = Campus.XIANXI,
    val tariff: BigDecimal = BigDecimal("0.65"),
    val termStart: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val totalWeeks: Int = 20,
    val sectionTimes: List<String> = TimetableSections.defaultTimes,
    val guideSeen: Boolean = false
)

data class ElectricityReading(
    val id: String,
    val date: LocalDate,
    val meter: BigDecimal?,
    val balance: BigDecimal?,
    val note: String = "",
    val source: ReadingSource = ReadingSource.MANUAL,
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasValue: Boolean get() = meter != null || balance != null
}

data class Course(
    val id: String,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: String = "1-16",
    val colorIndex: Int = 0,
    val note: String = ""
) {
    val sectionLabel: String
        get() = if (startSection == endSection) "${startSection}节" else "$startSection-${endSection}节"

    val weekLabel: String
        get() = if (weeks.contains("周")) weeks else "${weeks}周"
}

data class AppData(
    val settings: Settings = Settings(),
    val readings: List<ElectricityReading> = emptyList(),
    val courses: List<Course> = emptyList()
)

data class UsageInterval(
    val from: LocalDate,
    val to: LocalDate,
    val days: Long,
    val kwh: BigDecimal
)

data class ChartPoint(
    val date: LocalDate,
    val value: Double
)

data class ActionResult(
    val ok: Boolean,
    val message: String
)

/**
 * Section times used by the Chaoxing-style week grid.
 *
 * Chaoxing returns the school's real time table in
 * `data.curriculum.lessonTimeConfigArray`; those values are imported into
 * [Settings.sectionTimes]. The defaults below are only used before the first
 * import and by the manual editor.
 */
object TimetableSections {
    const val MAX_SECTION = 20
    const val DEFAULT_COUNT = 12

    val defaultTimes = listOf(
        "08:00-08:45",
        "08:55-09:40",
        "10:00-10:45",
        "10:55-11:40",
        "14:00-14:45",
        "14:55-15:40",
        "16:00-16:45",
        "16:55-17:40",
        "19:00-19:45",
        "19:55-20:40",
        "20:50-21:35",
        "21:45-22:30"
    )

    fun normalized(raw: List<String>): List<String> {
        val cleaned = raw.mapNotNull(::normalize)
        return cleaned.ifEmpty { defaultTimes }
    }

    fun normalize(raw: String): String? {
        val match = Regex("""(\d{1,2})\s*:\s*(\d{2})\s*[-~至]\s*(\d{1,2})\s*:\s*(\d{2})""")
            .find(raw.trim()) ?: return null
        val (h1, m1, h2, m2) = match.destructured
        val startHour = h1.toIntOrNull() ?: return null
        val startMinute = m1.toIntOrNull() ?: return null
        val endHour = h2.toIntOrNull() ?: return null
        val endMinute = m2.toIntOrNull() ?: return null
        if (startHour !in 0..23 || endHour !in 0..23) return null
        if (startMinute !in 0..59 || endMinute !in 0..59) return null
        return "%02d:%02d-%02d:%02d".format(startHour, startMinute, endHour, endMinute)
    }

    fun sectionCount(settings: Settings, courses: List<Course> = emptyList()): Int {
        val lastCourse = courses.maxOfOrNull { it.endSection } ?: 0
        return maxOf(settings.sectionTimes.size, lastCourse, DEFAULT_COUNT)
            .coerceIn(1, MAX_SECTION)
    }

    fun timeAt(times: List<String>, section: Int): String =
        times.getOrNull(section - 1).orEmpty()

    fun timeRange(times: List<String>, start: Int, end: Int): String {
        val first = timeAt(times, start).substringBefore('-').trim()
        val last = timeAt(times, end).substringAfter('-', "").trim()
        return if (first.isNotBlank() && last.isNotBlank()) "$first-$last" else ""
    }
}

object TimetableMath {
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val weekdayFullLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekStart(settings: Settings, week: Int): LocalDate =
        settings.termStart.with(DayOfWeek.MONDAY).plusWeeks((week - 1).toLong())

    fun weekEnd(settings: Settings, week: Int): LocalDate = weekStart(settings, week).plusDays(6)

    fun currentWeek(settings: Settings, today: LocalDate = LocalDate.now()): Int {
        val termMonday = settings.termStart.with(DayOfWeek.MONDAY)
        val todayMonday = today.with(DayOfWeek.MONDAY)
        val raw = ChronoUnit.WEEKS.between(termMonday, todayMonday).toInt() + 1
        return raw.coerceIn(1, settings.totalWeeks.coerceAtLeast(1))
    }

    fun todayWeekday(today: LocalDate = LocalDate.now()): Int = today.dayOfWeek.value

    fun dateForWeekday(settings: Settings, week: Int, weekday: Int): LocalDate =
        weekStart(settings, week).plusDays((weekday - 1).toLong())

    fun coursesForWeek(courses: List<Course>, week: Int): List<Course> =
        courses.filter { WeekSpec.includes(it.weeks, week) }
            .sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.name }))

    fun nextCourseDate(course: Course, settings: Settings, from: LocalDate = LocalDate.now()): LocalDate? {
        val firstWeek = maxOf(1, currentWeek(settings, from))
        for (week in firstWeek..settings.totalWeeks) {
            if (!WeekSpec.includes(course.weeks, week)) continue
            val date = dateForWeekday(settings, week, course.weekday)
            if (!date.isBefore(from)) return date
        }
        return null
    }
}

/** Supports 1-16, 1,3,5, 1-8,10-16, 1-16单, 1-16双 and per-segment parity. */
object WeekSpec {
    fun includes(spec: String, week: Int): Boolean {
        if (week <= 0) return false
        return expand(spec, maxOf(week, 30)).contains(week)
    }

    fun expand(spec: String, maxWeek: Int = 30): Set<Int> {
        val text = spec.trim()
        if (text.isEmpty() || text == "全周" || text == "每周" || text == "全部") {
            return (1..maxWeek.coerceAtLeast(1)).toSet()
        }

        val normalized = text
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace(';', ',')
            .replace("第", "")
            .replace("周", "")

        val hasOdd = normalized.contains("单")
        val hasEven = normalized.contains("双")
        val globalParity = when {
            hasOdd && !hasEven -> 1
            hasEven && !hasOdd -> 0
            else -> null
        }

        val result = linkedSetOf<Int>()
        normalized.split(',').forEach { raw ->
            if (raw.isBlank()) return@forEach
            val segmentOdd = raw.contains("单")
            val segmentEven = raw.contains("双")
            val parity = when {
                segmentOdd && !segmentEven -> 1
                segmentEven && !segmentOdd -> 0
                else -> globalParity
            }
            val numbers = Regex("""\d+""").findAll(raw)
                .mapNotNull { it.value.toIntOrNull() }
                .toList()
            if (numbers.isEmpty()) return@forEach
            val from = numbers.first()
            val to = if (numbers.size >= 2) numbers[1] else from
            if (from <= 0 || to <= 0) return@forEach
            val low = minOf(from, to)
            val high = maxOf(from, to)
            for (week in low..high) {
                if (week <= maxWeek && (parity == null || week % 2 == parity)) result += week
            }
        }
        return result
    }

    fun compress(weeks: Collection<Int>): String {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var previous = start
        for (week in sorted.drop(1)) {
            if (week == previous + 1) {
                previous = week
            } else {
                parts += if (start == previous) "$start" else "$start-$previous"
                start = week
                previous = week
            }
        }
        parts += if (start == previous) "$start" else "$start-$previous"
        return parts.joinToString(",")
    }
}

object ElectricityMath {
    fun sorted(readings: List<ElectricityReading>): List<ElectricityReading> =
        readings.sortedWith(compareBy({ it.date }, { it.createdAt }))

    fun latest(readings: List<ElectricityReading>): ElectricityReading? = sorted(readings).lastOrNull()

    fun latestBalance(readings: List<ElectricityReading>): ElectricityReading? =
        sorted(readings).lastOrNull { it.balance != null }

    fun intervals(readings: List<ElectricityReading>): List<UsageInterval> {
        val withMeter = sorted(readings).filter { it.meter != null }
        return withMeter.zipWithNext().mapNotNull { (previous, current) ->
            val previousMeter = previous.meter ?: return@mapNotNull null
            val currentMeter = current.meter ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(previous.date, current.date)
            val delta = currentMeter.subtract(previousMeter)
            if (days <= 0L || delta.signum() < 0) null
            else UsageInterval(previous.date, current.date, days, delta)
        }
    }

    fun usageInMonth(readings: List<ElectricityReading>, month: java.time.YearMonth): BigDecimal {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        return intervals(readings).fold(BigDecimal.ZERO) { sum, interval ->
            val start = maxOf(interval.from, monthStart)
            val end = minOf(interval.to, monthEnd)
            val overlap = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0L)
            if (overlap <= 0L || interval.days <= 0L) sum
            else sum + interval.kwh.multiply(BigDecimal(overlap))
                .divide(BigDecimal(interval.days), 4, java.math.RoundingMode.HALF_UP)
        }.setScale(2, java.math.RoundingMode.HALF_UP)
    }

    fun averageDailyKwh(
        readings: List<ElectricityReading>,
        today: LocalDate = LocalDate.now(),
        windowDays: Int = 7
    ): Double? {
        if (windowDays <= 0) return null
        val start = today.minusDays((windowDays - 1).toLong())
        val intervals = intervals(readings)
        val weighted = intervals.sumOf { interval ->
            val overlapStart = maxOf(interval.from, start)
            val overlapEnd = minOf(interval.to, today)
            val overlap = ChronoUnit.DAYS.between(overlapStart, overlapEnd).coerceAtLeast(0L)
            if (overlap <= 0L || interval.days <= 0L) 0.0
            else interval.kwh.toDouble() * overlap.toDouble() / interval.days.toDouble()
        }
        if (weighted <= 0.0) return null
        val firstCovered = intervals
            .map { maxOf(it.from, start) }
            .filter { !it.isAfter(today) }
            .minOrNull() ?: return null
        val coveredDays = (ChronoUnit.DAYS.between(firstCovered, today) + 1).coerceIn(1, windowDays.toLong())
        return weighted / coveredDays.toDouble()
    }

    fun estimatedDays(balance: BigDecimal?, tariff: BigDecimal, dailyKwh: Double?): Int? {
        if (balance == null || dailyKwh == null || dailyKwh <= 0.0 || tariff.signum() <= 0) return null
        val dailyCost = tariff.toDouble() * dailyKwh
        if (dailyCost <= 0.0) return null
        return (balance.toDouble() / dailyCost).toInt().coerceIn(0, 999)
    }

    fun chartPoints(readings: List<ElectricityReading>, limit: Int = 14): List<ChartPoint> =
        intervals(readings).takeLast(limit).map { ChartPoint(it.to, it.kwh.toDouble()) }
}
