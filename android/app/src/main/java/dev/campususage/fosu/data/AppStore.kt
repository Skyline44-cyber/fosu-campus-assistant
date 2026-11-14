package dev.campususage.fosu.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AppData {
        val raw = prefs.getString(KEY_DATA, null) ?: return AppData()
        return runCatching { parse(raw) }.getOrElse { AppData() }
    }

    fun save(data: AppData) {
        prefs.edit { putString(KEY_DATA, toJson(data).toString()) }
    }

    fun export(data: AppData): String = toJson(data).toString(2)

    fun parseBackup(text: String): AppData {
        val trimmed = text.trim()
        require(trimmed.startsWith("{")) { "备份内容应为 JSON 对象" }
        val json = runCatching { JSONObject(trimmed) }.getOrElse {
            throw IllegalArgumentException("JSON 格式不正确：${it.message ?: "无法解析"}")
        }
        val hasData = json.has("settings") || json.has("readings") || json.has("courses")
        require(hasData) { "没有找到 settings / readings / courses 字段" }
        return parse(trimmed)
    }

    private fun parse(text: String): AppData {
        val json = JSONObject(text)
        val settingsJson = json.optJSONObject("settings") ?: JSONObject()
        val settings = Settings(
            roomName = settingsJson.optString("roomName", "").take(30),
            campus = Campus.from(settingsJson.optString("campus")),
            tariff = settingsJson.optString("tariff").toBigDecimalOrNull()
                ?.takeIf { it.signum() > 0 } ?: BigDecimal("0.65"),
            termStart = settingsJson.optString("termStart").toLocalDateOrNull()
                ?: LocalDate.now().with(java.time.DayOfWeek.MONDAY),
            totalWeeks = settingsJson.optInt("totalWeeks", 20).coerceIn(1, 30),
            sectionTimes = TimetableSections.normalized(
                parseStringArray(settingsJson.optJSONArray("sectionTimes"))
            ),
            guideSeen = settingsJson.optBoolean("guideSeen", false)
        )

        val readings = mutableListOf<ElectricityReading>()
        json.optJSONArray("readings")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val date = item.optString("date").toLocalDateOrNull() ?: continue
                val meter = item.optString("meter").toBigDecimalOrNull()
                val balance = item.optString("balance").toBigDecimalOrNull()
                if (meter == null && balance == null) continue
                readings += ElectricityReading(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    date = date,
                    meter = meter,
                    balance = balance,
                    note = item.optString("note", "").take(80),
                    source = runCatching { ReadingSource.valueOf(item.optString("source")) }
                        .getOrDefault(ReadingSource.MANUAL),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }

        val courses = mutableListOf<Course>()
        json.optJSONArray("courses")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").trim().take(40)
                val weekday = item.optInt("weekday", -1)
                val start = item.optInt("startSection", -1)
                val end = item.optInt("endSection", -1)
                if (name.isBlank() || weekday !in 1..7 ||
                    start !in 1..TimetableSections.MAX_SECTION ||
                    end !in start..TimetableSections.MAX_SECTION
                ) continue
                courses += Course(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    teacher = item.optString("teacher", "").take(30),
                    room = item.optString("room", "").take(30),
                    weekday = weekday,
                    startSection = start,
                    endSection = end,
                    weeks = item.optString("weeks", "1-16").ifBlank { "1-16" }.take(40),
                    colorIndex = item.optInt("colorIndex", i % 8).coerceIn(0, 7),
                    note = item.optString("note", "").take(120)
                )
            }
        }

        return AppData(settings, readings, courses)
    }

    fun toJson(data: AppData): JSONObject {
        val settings = JSONObject()
            .put("roomName", data.settings.roomName)
            .put("campus", data.settings.campus.name)
            .put("tariff", data.settings.tariff.toPlainString())
            .put("termStart", data.settings.termStart.toString())
            .put("totalWeeks", data.settings.totalWeeks)
            .put("sectionTimes", JSONArray(data.settings.sectionTimes))
            .put("guideSeen", data.settings.guideSeen)

        val readings = JSONArray()
        data.readings.forEach { reading ->
            val item = JSONObject()
                .put("id", reading.id)
                .put("date", reading.date.toString())
                .put("note", reading.note)
                .put("source", reading.source.name)
                .put("createdAt", reading.createdAt)
            reading.meter?.let { item.put("meter", it.toPlainString()) }
            reading.balance?.let { item.put("balance", it.toPlainString()) }
            readings.put(item)
        }

        val courses = JSONArray()
        data.courses.forEach { course ->
            courses.put(
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
                    .put("note", course.note)
            )
        }

        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("app", "佛大校园助手")
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", settings)
            .put("readings", readings)
            .put("courses", courses)
    }

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    companion object {
        private const val PREFS = "fosu_campus_data_v1"
        private const val KEY_DATA = "app_data"
        private const val SCHEMA_VERSION = 2
    }
}
