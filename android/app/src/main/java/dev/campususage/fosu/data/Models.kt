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
    val sectionsPerDay: Int = SectionSchedule.COUNT,
    val holidays: Set<LocalDate> = emptySet(),
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
    val sectionLabel: String get() = if (startSection == endSection) "${startSection}节" else "$startSection-${endSection}节"
    val timeLabel: String get() = SectionSchedule.timeRange(startSection, endSection)
    val weekLabel: String get() = if (weeks.contains("周")) weeks else "${weeks}周"
    val fullLabel: String get() = listOf(name, teacher, room, timeLabel, weekLabel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
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

    fun courseOccurs(course: Course, week: Int): Boolean = WeekSpec.includes(course.weeks, week)

    fun todayWeekday(today: LocalDate = LocalDate.now()): Int = today.dayOfWeek.value

    fun dateForWeekday(settings: Settings, week: Int, weekday: Int): LocalDate =
        weekStart(settings, week).plusDays((weekday - 1).toLong())
}

data class SmallSection(
    val index: Int,
    val bigIndex: Int,
    val start: String,
    val end: String
)

data class BigSection(
    val index: Int,
    val label: String,
    val startSection: Int,
    val endSection: Int
) {
    val count: Int get() = endSection - startSection + 1
}

data class ScheduledCourse(
    val course: Course,
    val date: LocalDate,
    val shifted: Boolean
)

/**
 * The 14 small sections and their 6 big-section groups, using the school timetable
 * times shown in the reference material.
 */
object SectionSchedule {
    val small = listOf(
        SmallSection(1, 1, "08:00", "08:40"),
        SmallSection(2, 1, "08:45", "09:25"),
        SmallSection(3, 2, "09:40", "10:20"),
        SmallSection(4, 2, "10:25", "11:05"),
        SmallSection(5, 2, "11:10", "11:50"),
        SmallSection(6, 3, "13:30", "14:10"),
        SmallSection(7, 3, "14:15", "14:55"),
        SmallSection(8, 4, "15:10", "15:50"),
        SmallSection(9, 4, "15:55", "16:35"),
        SmallSection(10, 4, "16:40", "17:20"),
        SmallSection(11, 5, "18:30", "19:10"),
        SmallSection(12, 5, "19:15", "19:55"),
        SmallSection(13, 6, "20:05", "20:45"),
        SmallSection(14, 6, "20:50", "21:30")
    )

    val big = listOf(
        BigSection(1, "第一大节", 1, 2),
        BigSection(2, "第二大节", 3, 5),
        BigSection(3, "第三大节", 6, 7),
        BigSection(4, "第四大节", 8, 10),
        BigSection(5, "第五大节", 11, 12),
        BigSection(6, "第六大节", 13, 14)
    )

    val COUNT: Int get() = small.size

    fun smallSection(index: Int): SmallSection? = small.firstOrNull { it.index == index }

    fun bigSection(index: Int): BigSection? = big.firstOrNull { it.index == index }

    fun bigFor(section: Int): BigSection? =
        big.firstOrNull { section in it.startSection..it.endSection }

    fun timeRange(start: Int, end: Int): String {
        val first = smallSection(start) ?: return ""
        val last = smallSection(end) ?: first
        return "${first.start}-${last.end}"
    }

    fun bigTimeRange(section: Int): String =
        bigFor(section)?.let { timeRange(it.startSection, it.endSection) }.orEmpty()
}

object TeachingCalendar {
    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun isHoliday(date: LocalDate, settings: Settings): Boolean = date in settings.holidays

    fun isTeachingDay(date: LocalDate, settings: Settings): Boolean =
        !isWeekend(date) && !isHoliday(date, settings)

    /** Advance a date cursor one day at a time, skipping weekends and holidays. */
    fun nextTeachingDay(date: LocalDate, settings: Settings): LocalDate {
        var cursor = date
        repeat(370) {
            if (isTeachingDay(cursor, settings)) return cursor
            cursor = cursor.plusDays(1)
        }
        return cursor
    }

    /**
     * Find the next real date for a course. A holiday only advances the date
     * cursor; the course is moved to the next teaching day instead of being
     * consumed or dropped.
     */
    fun nextCourseDate(course: Course, settings: Settings, from: LocalDate = LocalDate.now()): LocalDate? {
        val firstWeek = maxOf(1, TimetableMath.currentWeek(settings, from))
        for (week in firstWeek..settings.totalWeeks) {
            if (!WeekSpec.includes(course.weeks, week)) continue
            val scheduled = TimetableMath.weekStart(settings, week)
                .plusDays((course.weekday - 1).toLong())
            val actual = nextTeachingDay(scheduled, settings)
            if (!actual.isBefore(from)) return actual
        }
        return null
    }

    fun scheduleForWeek(settings: Settings, week: Int, courses: List<Course>): List<ScheduledCourse> {
        val start = TimetableMath.weekStart(settings, week)
        val end = start.plusDays(6)
        return courses.flatMap { course ->
            (1..settings.totalWeeks).flatMap { sourceWeek ->
                if (!WeekSpec.includes(course.weeks, sourceWeek)) {
                    emptyList<ScheduledCourse>()
                } else {
                    val scheduled = TimetableMath.weekStart(settings, sourceWeek)
                        .plusDays((course.weekday - 1).toLong())
                    val actual = nextTeachingDay(scheduled, settings)
                    if (actual.isBefore(start) || actual.isAfter(end)) emptyList()
                    else listOf(ScheduledCourse(course, actual, actual != scheduled))
                }
            }
        }.distinctBy { it.course.id to it.date }
            .sortedWith(compareBy({ it.date }, { it.course.startSection }))
    }

    fun holidaysInWeek(settings: Settings, week: Int): List<LocalDate> {
        val start = TimetableMath.weekStart(settings, week)
        return (0L..6L).map { start.plusDays(it) }.filter { isHoliday(it, settings) }
    }
}

/** Supports 1-16, 1,3,5, 1-8,10-16, 1-16单, 1-16双 and per-segment parity. */
object WeekSpec {
    fun includes(spec: String, week: Int): Boolean {
        if (week <= 0) return false
        val text = spec.trim()
        if (text.isEmpty() || text == "全周" || text == "每周" || text == "全部") return true

        val normalized = text
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace(';', ',')

        val hasOdd = normalized.contains("单")
        val hasEven = normalized.contains("双")
        val globalParity = when {
            hasOdd && !hasEven -> 1
            hasEven && !hasOdd -> 0
            else -> null
        }

        var matched = false
        normalized.split(',').forEach { raw ->
            if (raw.isBlank()) return@forEach
            val segmentOdd = raw.contains("单")
            val segmentEven = raw.contains("双")
            val parity = when {
                segmentOdd && !segmentEven -> 1
                segmentEven && !segmentOdd -> 0
                else -> globalParity
            }
            val numbers = Regex("\\d+").findAll(raw)
                .mapNotNull { it.value.toIntOrNull() }
                .toList()
            if (numbers.isEmpty()) return@forEach
            val from = numbers.first()
            val to = if (numbers.size >= 2) numbers[1] else from
            if (from <= 0 || to <= 0) return@forEach
            val low = minOf(from, to)
            val high = maxOf(from, to)
            if (week in low..high && (parity == null || week % 2 == parity)) matched = true
        }
        return matched
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
