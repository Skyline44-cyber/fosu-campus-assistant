package dev.campususage.fosu

import dev.campususage.fosu.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
    fun compressesWeekNumbers() {
        assertEquals("1-3,5,8-9", WeekSpec.compress(listOf(1, 2, 3, 5, 8, 9)))
        assertEquals("1-16", WeekSpec.compress((1..16).toList()))
        assertEquals("", WeekSpec.compress(emptyList()))
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
    fun parsesJsonFromManualImport() {
        val result = TimetableTextParser.parse(
            """[{"name":"数据结构","teacher":"王五","room":"A3-201","weekday":2,"start":5,"end":6,"weeks":"1-8"}]"""
        )
        assertEquals(1, result.courses.size)
        assertEquals("数据结构", result.courses[0].name)
        assertEquals(2, result.courses[0].weekday)
        assertEquals(5, result.courses[0].startSection)
    }
}

class TimetableSectionsTest {
    @Test
    fun usesTwelveDefaultSections() {
        assertEquals(12, TimetableSections.defaultTimes.size)
        assertEquals("08:00-08:45", TimetableSections.defaultTimes[0])
        assertEquals("08:00-09:40", TimetableSections.timeRange(TimetableSections.defaultTimes, 1, 2))
        assertEquals(12, TimetableSections.sectionCount(Settings()))
    }

    @Test
    fun acceptsSchoolTimesFromChaoxing() {
        val normalized = TimetableSections.normalized(listOf("8:00-8:50", "8:55-9:45"))
        assertEquals(listOf("08:00-08:50", "08:55-09:45"), normalized)
        assertEquals("08:00-09:45", TimetableSections.timeRange(normalized, 1, 2))
    }

    @Test
    fun nextCourseDateUsesRealWeekday() {
        val settings = Settings(termStart = LocalDate.of(2026, 9, 7), totalWeeks = 16)
        val course = Course(
            id = "c1",
            name = "大学英语",
            weekday = 2,
            startSection = 3,
            endSection = 4,
            weeks = "2-16"
        )
        assertEquals(
            LocalDate.of(2026, 9, 15),
            TimetableMath.nextCourseDate(course, settings, LocalDate.of(2026, 9, 7))
        )
        assertNull(TimetableMath.nextCourseDate(course, settings, LocalDate.of(2027, 2, 1)))
    }
}

class ChaoxingParserTest {
    private val firstWeek = LocalDate.of(2026, 9, 7)
    private val firstWeekMillis = firstWeek.atStartOfDay(ZoneId.of("Asia/Shanghai"))
        .toInstant().toEpochMilli()

    private fun response(week: Int, lessonWeeks: String = week.toString()): String {
        return """
            {
              "result": 1,
              "msg": "success",
              "data": {
                "curriculum": {
                  "maxWeek": 16,
                  "firstWeekDate": $firstWeekMillis,
                  "lessonTimeConfigArray": ["8:00-8:45", "8:55-9:40", "10:00-10:45", "10:55-11:40"]
                },
                "lessonArray": [
                  {
                    "lessonId": "L1",
                    "name": "高等数学",
                    "teacherName": "张三",
                    "location": "C5-101",
                    "dayOfWeek": 1,
                    "beginNumber": 1,
                    "length": 2,
                    "weeks": "$lessonWeeks"
                  },
                  {
                    "lessonId": "L2",
                    "name": "大学英语",
                    "teacherName": "李四",
                    "location": "B2-203",
                    "dayOfWeek": 3,
                    "beginNumber": 3,
                    "length": 2,
                    "weeks": "$lessonWeeks"
                  }
                ]
              }
            }
        """.trimIndent()
    }

    @Test
    fun parsesCurriculumAndLessons() {
        val parsed = ChaoxingParser.parseWeek(response(6), 6)
        assertEquals(16, parsed.maxWeek ?: -1)
        assertEquals(firstWeek, parsed.firstWeekDate)
        assertEquals(4, parsed.sectionTimes.size)
        assertEquals(2, parsed.lessons.size)
        assertEquals("高等数学", parsed.lessons[0].name)
        assertEquals("张三", parsed.lessons[0].teacher)
        assertEquals(1, parsed.lessons[0].weekday)
        assertEquals(2, parsed.lessons[0].endSection)
        assertEquals(setOf(6), parsed.lessons[0].weeks)
    }

    @Test
    fun mergesWeeksIntoOneCourse() {
        val merged = ChaoxingParser.merge(
            listOf(
                ChaoxingParser.parseWeek(response(6), 6),
                ChaoxingParser.parseWeek(response(7), 7)
            )
        )
        assertEquals(2, merged.courses.size)
        assertEquals(16, merged.maxWeek)
        assertEquals("6-7", merged.courses[0].weeks)
        assertEquals(firstWeek, merged.firstWeekDate)
        assertEquals(
            listOf("08:00-08:45", "08:55-09:40", "10:00-10:45", "10:55-11:40"),
            merged.sectionTimes
        )
    }

    @Test
    fun rejectsLoginFailureJson() {
        val error = runCatching {
            ChaoxingParser.parseWeek("""{"result":0,"data":"用户信息异常，请登录后再试","msg":"用户信息异常，请登录后再试"}""", 1)
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is ChaoxingException)
        assertTrue(error!!.message!!.contains("登录"))
    }

    @Test
    fun encodedTimetableCanBeDecoded() {
        val merged = ChaoxingParser.merge(listOf(ChaoxingParser.parseWeek(response(6), 6)))
        val decoded = ChaoxingParser.decode(ChaoxingParser.encode(merged))
        assertNotNull(decoded)
        assertEquals(2, decoded!!.courses.size)
        assertEquals(16, decoded.maxWeek)
        assertEquals(firstWeek, decoded.firstWeekDate)
        assertEquals("高等数学", decoded.courses[0].name)
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
