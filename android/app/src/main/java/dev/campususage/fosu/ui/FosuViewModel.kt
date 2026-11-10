package dev.campususage.fosu.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import dev.campususage.fosu.data.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class FosuViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppStore(application)

    var data by mutableStateOf(store.load())
        private set

    var page by mutableStateOf("home")
        private set

    var today by mutableStateOf(LocalDate.now())
        private set

    var timetableWeek by mutableIntStateOf(TimetableMath.currentWeek(data.settings, LocalDate.now()))
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    fun onResume() {
        val previousToday = today
        val nextToday = LocalDate.now()
        val previousCurrent = TimetableMath.currentWeek(data.settings, previousToday)
        val nextCurrent = TimetableMath.currentWeek(data.settings, nextToday)
        today = nextToday
        if (timetableWeek == previousCurrent || timetableWeek !in 1..data.settings.totalWeeks) {
            timetableWeek = nextCurrent
        }
    }

    fun go(destination: String) {
        if (destination in setOf("home", "electricity", "timetable", "profile")) page = destination
    }

    fun notify(message: String) {
        notice = message
    }

    fun clearNotice() {
        notice = null
    }

    fun markGuideSeen() {
        update { it.copy(settings = it.settings.copy(guideSeen = true)) }
    }

    fun updateRoom(roomName: String, campus: Campus, tariff: BigDecimal): ActionResult {
        if (tariff.signum() <= 0) return ActionResult(false, "电价必须大于 0")
        if (tariff > BigDecimal("10")) return ActionResult(false, "电价看起来过高，请核对")
        update {
            it.copy(
                settings = it.settings.copy(
                    roomName = roomName.trim().take(30),
                    campus = campus,
                    tariff = tariff
                )
            )
        }
        return ActionResult(true, "宿舍信息已保存")
    }

    fun updateTerm(termStart: LocalDate, totalWeeks: Int): ActionResult {
        if (totalWeeks !in 1..30) return ActionResult(false, "学期周数应在 1-30 之间")
        update {
            it.copy(
                settings = it.settings.copy(
                    termStart = termStart,
                    totalWeeks = totalWeeks,
                    sectionsPerDay = SectionSchedule.COUNT
                )
            )
        }
        timetableWeek = TimetableMath.currentWeek(data.settings, today)
        return ActionResult(true, "学期设置已保存")
    }

    fun updateHolidays(dates: List<LocalDate>): ActionResult {
        val normalized = dates.distinct().sorted()
        update { it.copy(settings = it.settings.copy(holidays = normalized.toSet())) }
        return ActionResult(true, "已保存 ${normalized.size} 个节假日")
    }

    fun setWeek(week: Int) {
        timetableWeek = week.coerceIn(1, data.settings.totalWeeks)
    }

    fun goToCurrentWeek() {
        timetableWeek = TimetableMath.currentWeek(data.settings, today)
    }

    fun addReading(
        date: LocalDate,
        meterText: String,
        balanceText: String,
        note: String,
        source: ReadingSource = ReadingSource.MANUAL
    ): ActionResult {
        if (date.isAfter(today)) return ActionResult(false, "不能记录未来日期")
        val meter = parseAmount(meterText)
        val balance = parseAmount(balanceText)
        if (meterText.isNotBlank() && meter == null) return ActionResult(false, "电表读数格式不正确")
        if (balanceText.isNotBlank() && balance == null) return ActionResult(false, "余额格式不正确")
        if (meter == null && balance == null) return ActionResult(false, "电表读数与余额至少填写一项")
        if (meter != null && meter.signum() < 0) return ActionResult(false, "电表读数不能为负数")

        val previousMeter = data.readings
            .filter { it.meter != null && !it.date.isAfter(date) }
            .maxWithOrNull(compareBy<ElectricityReading>({ it.date }, { it.createdAt }))
            ?.meter
        if (meter != null && previousMeter != null && meter < previousMeter) {
            return ActionResult(false, "读数小于上次 ${previousMeter.toPlainString()} kWh，请核对是否换表")
        }

        val reading = ElectricityReading(
            id = UUID.randomUUID().toString(),
            date = date,
            meter = meter,
            balance = balance,
            note = note.trim().take(80),
            source = source
        )
        update { it.copy(readings = it.readings + reading) }
        return ActionResult(true, "已记录 ${date} 的电费数据")
    }

    fun deleteReading(id: String) {
        update { it.copy(readings = it.readings.filterNot { reading -> reading.id == id }) }
    }

    fun importElectricity(text: String): ActionResult {
        val parsed = ElectricityTextParser.parse(text, today)
            ?: return ActionResult(false, "没有识别到余额或电表读数，请检查粘贴内容")
        val date = parsed.date?.takeUnless { it.isAfter(today) } ?: today
        val roomChanged = parsed.room != null && data.settings.roomName.isBlank()
        if (roomChanged) {
            update { it.copy(settings = it.settings.copy(roomName = parsed.room.orEmpty())) }
        }
        val result = addReading(
            date = date,
            meterText = parsed.meter?.toPlainString().orEmpty(),
            balanceText = parsed.balance?.toPlainString().orEmpty(),
            note = "从佛大一卡通查询结果导入",
            source = ReadingSource.OFFICIAL
        )
        if (!result.ok) return result
        val bits = buildList {
            parsed.balance?.let { add("余额 ¥${it.stripTrailingZeros().toPlainString()}") }
            parsed.meter?.let { add("读数 ${it.stripTrailingZeros().toPlainString()} kWh") }
            if (roomChanged) add("房间 ${parsed.room}")
        }
        return ActionResult(true, "已导入 " + bits.joinToString(" / "))
    }

    fun saveCourse(course: Course): ActionResult {
        if (course.name.isBlank()) return ActionResult(false, "课程名不能为空")
        if (course.weekday !in 1..7) return ActionResult(false, "请选择星期")
        if (course.startSection !in 1..SectionSchedule.COUNT ||
            course.endSection !in course.startSection..SectionSchedule.COUNT
        ) {
            return ActionResult(false, "节次范围不正确（1-${SectionSchedule.COUNT} 小节）")
        }
        if (course.weeks.isBlank()) return ActionResult(false, "请填写周次，例如 1-16")
        val normalized = course.copy(
            id = course.id.ifBlank { UUID.randomUUID().toString() },
            name = course.name.trim().take(40),
            teacher = course.teacher.trim().take(30),
            room = course.room.trim().take(30),
            weeks = course.weeks.trim().take(40)
        )
        val exists = data.courses.any { it.id == normalized.id }
        update {
            it.copy(
                courses = if (exists) it.courses.map { old -> if (old.id == normalized.id) normalized else old }
                else it.courses + normalized
            )
        }
        return ActionResult(true, if (exists) "课程已更新" else "课程已添加")
    }

    fun deleteCourse(id: String) {
        update { it.copy(courses = it.courses.filterNot { it.id == id }) }
    }

    fun importTimetable(text: String, replace: Boolean): ActionResult {
        val parsed = TimetableTextParser.parse(text)
        if (parsed.courses.isEmpty()) {
            return ActionResult(false, parsed.errors.firstOrNull() ?: "没有解析到课程")
        }
        val courses = if (replace) parsed.courses else data.courses + parsed.courses
        update { it.copy(courses = courses) }
        goToCurrentWeek()
        val warning = if (parsed.errors.isEmpty()) "" else "；${parsed.errors.size} 行未识别"
        return ActionResult(true, "已导入 ${parsed.courses.size} 门课程$warning")
    }

    fun exportBackup(): String = store.export(data)

    fun importBackup(text: String): ActionResult {
        return runCatching {
            val imported = store.parseBackup(text)
            data = imported
            store.save(imported)
            timetableWeek = TimetableMath.currentWeek(imported.settings, today)
            ActionResult(true, "备份已恢复：${imported.readings.size} 条电费记录，${imported.courses.size} 门课程")
        }.getOrElse {
            ActionResult(false, it.message ?: "备份格式不正确")
        }
    }

    fun clearAll() {
        data = AppData(Settings(guideSeen = true))
        store.save(data)
        timetableWeek = TimetableMath.currentWeek(data.settings, today)
        page = "home"
    }

    private fun update(transform: (AppData) -> AppData) {
        data = transform(data)
        store.save(data)
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val cleaned = raw.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isBlank()) return null
        return cleaned.toBigDecimalOrNull()
    }
}
