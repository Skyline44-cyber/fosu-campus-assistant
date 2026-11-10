package dev.campususage.fosu

import dev.campususage.fosu.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class WeekSpecTest {
    @Test
    fun parsesRangesAndLists() {
        assertTrue(WeekSpec.includes("1-16", 1))
        assertTrue(WeekSpec.includes("1-16", 16))
        assertFalse(WeekSpec.includes("1-16", 17))
        assertTrue(WeekSpec.includes("1,3,5", 3))
        assertFalse(WeekSpec.includes("1,3,5", 4))
        assertTrue(WeekSpec.includes("1-8,10-16", 10))
        assertFalse(WeekSpec.includes("1-8,10-16", 9))
    }

    @Test
    fun parsesParityAndChineseLabels() {
        assertTrue(WeekSpec.includes("1-16单", 3))
        assertFalse(WeekSpec.includes("1-16单", 4))
        assertTrue(WeekSpec.includes("1-16双", 4))
        assertTrue(WeekSpec.includes("第1-16周", 8))
        assertTrue(WeekSpec.includes("1-8单,10-16双", 12))
        assertFalse(WeekSpec.includes("1-8单,10-16双", 11))
        assertFalse(WeekSpec.includes("1-8单,10-16双", 9))
    }

    @Test
    fun currentWeekClampsToTerm() {
        val settings = Settings(termStart = LocalDate.of(2026, 9, 7), totalWeeks = 20)
        assertEquals(1, TimetableMath.currentWeek(settings, LocalDate.of(2026, 9, 9)))
        assertEquals(2, TimetableMath.currentWeek(settings, LocalDate.of(2026, 9, 14)))
        assertEquals(20, TimetableMath.currentWeek(settings, LocalDate.of(2027, 3, 1)))
    }
}

class ElectricityTextParserTest {
    @Test
    fun parsesChineseOfficialText() {
        val parsed = ElectricityTextParser.parse(
            "房间：A1-302\n余额：45.60 元\n剩余电量 128.4 kWh\n2026-11-08",
            LocalDate.of(2026, 11, 9)
        )!!
        assertEquals("A1-302", parsed.room)
        assertEquals(BigDecimal("45.60"), parsed.balance)
        assertEquals(BigDecimal("128.4"), parsed.meter)
        assertEquals(LocalDate.of(2026, 11, 8), parsed.date)
    }

    @Test
    fun parsesShortDateAndBalanceOnly() {
        val parsed = ElectricityTextParser.parse("剩余金额 12.5\n11月8日", LocalDate.of(2026, 11, 9))!!
        assertEquals(BigDecimal("12.5"), parsed.balance)
        assertNull(parsed.meter)
        assertEquals(LocalDate.of(2026, 11, 8), parsed.date)
    }

    @Test
    fun ignoresTextWithoutAmounts() {
        assertNull(ElectricityTextParser.parse("佛大一卡通 电费查询"))
    }
}

class TimetableParserTest {
    @Test
    fun parsesPipeLines() {
        val result = TimetableTextParser.parse(
            "高等数学|张三|C5-101|周一|1-2|1-16\n大学英语|李四|B2-203|星期三|3-4|1-16单"
        )
        assertEquals(2, result.courses.size)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.courses[0].weekday)
        assertEquals(2, result.courses[0].endSection)
        assertEquals(3, result.courses[1].weekday)
    }

    @Test
    fun reportsBadLinesButKeepsGoodOnes() {
        val result = TimetableTextParser.parse("坏行\n高等数学|张三|C5-101|周一|1-2|1-16")
        assertEquals(1, result.courses.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun parsesJsonFromWebExtraction() {
        val result = TimetableTextParser.parse(
            """[{"name":"数据结构","teacher":"王五","room":"A3-201","weekday":2,"start":5,"end":6,"weeks":"1-8"}]"""
        )
        assertEquals(1, result.courses.size)
        assertEquals("数据结构", result.courses[0].name)
        assertEquals(2, result.courses[0].weekday)
        assertEquals(5, result.courses[0].startSection)
    }
}

class SectionScheduleTest {
    @Test
    fun hasSixBigAndFourteenSmallSections() {
        assertEquals(6, SectionSchedule.big.size)
        assertEquals(14, SectionSchedule.COUNT)
        assertEquals(3, SectionSchedule.big[1].count)
        assertEquals(3, SectionSchedule.big[3].count)
        assertEquals(2, SectionSchedule.big[0].count)
        assertEquals(2, SectionSchedule.big[2].count)
    }

    @Test
    fun formatsRealSectionTimes() {
        assertEquals("08:00-09:25", SectionSchedule.timeRange(1, 2))
        assertEquals("09:40-11:50", SectionSchedule.timeRange(3, 5))
        assertEquals("20:05-21:30", SectionSchedule.timeRange(13, 14))
    }
}

class TeachingCalendarTest {
    private val course = Course(
        id = "c1",
        name = "大学英语",
        weekday = 2,
        startSection = 3,
        endSection = 4,
        weeks = "1-16"
    )

    @Test
    fun holidayOnlyAdvancesDateCursor() {
        val settings = Settings(
            termStart = LocalDate.of(2026, 11, 9),
            holidays = setOf(LocalDate.of(2026, 11, 10))
        )
        assertFalse(TeachingCalendar.isTeachingDay(LocalDate.of(2026, 11, 10), settings))
        assertEquals(LocalDate.of(2026, 11, 11), TeachingCalendar.nextTeachingDay(LocalDate.of(2026, 11, 10), settings))
        assertEquals(LocalDate.of(2026, 11, 11), TeachingCalendar.nextCourseDate(course, settings, LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun holidayCourseMovesToNextTeachingDay() {
        val settings = Settings(
            termStart = LocalDate.of(2026, 11, 9),
            holidays = setOf(LocalDate.of(2026, 11, 10))
        )
        val scheduled = TeachingCalendar.scheduleForWeek(settings, 1, listOf(course))
        assertEquals(1, scheduled.size)
        assertEquals(LocalDate.of(2026, 11, 11), scheduled[0].date)
        assertTrue(scheduled[0].shifted)
    }

    @Test
    fun normalWeekdayIsNotShifted() {
        val settings = Settings(termStart = LocalDate.of(2026, 11, 9))
        val scheduled = TeachingCalendar.scheduleForWeek(settings, 1, listOf(course))
        assertEquals(LocalDate.of(2026, 11, 10), scheduled[0].date)
        assertFalse(scheduled[0].shifted)
    }
}

class ElectricityMathTest {
    private fun reading(day: Int, meter: String, balance: String? = null) = ElectricityReading(
        id = "r$day",
        date = LocalDate.of(2026, 11, day),
        meter = meter.toBigDecimal(),
        balance = balance?.toBigDecimal(),
        createdAt = day.toLong()
    )

    @Test
    fun buildsUsageIntervals() {
        val intervals = ElectricityMath.intervals(listOf(reading(1, "100"), reading(3, "106"), reading(8, "116")))
        assertEquals(2, intervals.size)
        assertEquals(BigDecimal("6"), intervals[0].kwh)
        assertEquals(2L, intervals[0].days)
        assertEquals(5L, intervals[1].days)
    }

    @Test
    fun ignoresMeterResets() {
        val intervals = ElectricityMath.intervals(listOf(reading(1, "100"), reading(2, "90")))
        assertTrue(intervals.isEmpty())
    }

    @Test
    fun proratesMonthlyUsage() {
        val usage = ElectricityMath.usageInMonth(
            listOf(
                ElectricityReading("a", LocalDate.of(2026, 10, 30), "100".toBigDecimal(), null, createdAt = 1),
                ElectricityReading("b", LocalDate.of(2026, 11, 2), "112".toBigDecimal(), null, createdAt = 2)
            ),
            YearMonth.of(2026, 11)
        )
        assertEquals(BigDecimal("4.00"), usage)
    }

    @Test
    fun estimatesRemainingDays() {
        assertEquals(4, ElectricityMath.estimatedDays(BigDecimal("10"), BigDecimal("0.5"), 5.0) ?: -1)
        assertNull(ElectricityMath.estimatedDays(null, BigDecimal("0.5"), 5.0))
    }
}
