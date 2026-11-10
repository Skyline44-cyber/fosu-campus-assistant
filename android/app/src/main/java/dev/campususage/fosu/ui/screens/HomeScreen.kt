package dev.campususage.fosu.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.fosu.TimetableLoginActivity
import dev.campususage.fosu.data.*
import dev.campususage.fosu.ui.*
import dev.campususage.fosu.ui.theme.*

@Composable
fun HomeScreen(vm: FosuViewModel) {
    val data = vm.data
    val context = LocalContext.current
    val week = TimetableMath.currentWeek(data.settings, vm.today)
    val teachingToday = TeachingCalendar.isTeachingDay(vm.today, data.settings)
    val todayHoliday = TeachingCalendar.isHoliday(vm.today, data.settings)
    val nextTeachingDay = TeachingCalendar.nextTeachingDay(vm.today, data.settings)
    val todayCourses = if (!teachingToday) emptyList() else TeachingCalendar
        .scheduleForWeek(data.settings, week, data.courses)
        .filter { it.date == vm.today }
        .map { it.course }
        .distinctBy { it.id }
        .sortedBy { it.startSection }
    val balanceReading = ElectricityMath.latestBalance(data.readings)
    val points = ElectricityMath.chartPoints(data.readings)
    var guide by remember { mutableStateOf<OfficialService?>(null) }

    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val json = result.data?.getStringExtra(TimetableWebContract.EXTRA_COURSES_JSON)
            val replace = result.data?.getBooleanExtra(TimetableWebContract.EXTRA_REPLACE, true) ?: true
            if (!json.isNullOrBlank()) {
                val imported = vm.importTimetable(json, replace)
                vm.notify(imported.message)
            }
        }
    }
    val openTimetableLogin = {
        loginLauncher.launch(Intent(context, TimetableLoginActivity::class.java))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "FOSU · CAMPUS",
                title = "佛大校园助手",
                subtitle = "${data.settings.campus.label} · ${data.settings.roomName.ifBlank { "还没设置宿舍" }}"
            )
        }

        item {
            HeroCard(
                week = week,
                todayText = "${vm.today.monthValue} 月 ${vm.today.dayOfMonth} 日 · ${weekdayText(vm.today.dayOfWeek.value)}",
                onElectricity = { vm.go("electricity") },
                onTimetable = { vm.go("timetable") }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "一卡通余额",
                    value = money(balanceReading?.balance),
                    unit = if (balanceReading?.balance == null) "" else "元",
                    icon = Icons.Default.Bolt,
                    accent = Pink,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.go("electricity") }
                )
                MetricCard(
                    label = "今天有课",
                    value = todayCourses.size.toString(),
                    unit = "门",
                    icon = Icons.Default.CalendarMonth,
                    accent = Blue,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.go("timetable") }
                )
            }
        }

        item { SectionTitle("今天的课", "完整课表") { vm.go("timetable") } }
        item {
            GlassCard(accent = Blue) {
                if (todayCourses.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.CalendarMonth,
                        title = if (teachingToday) "今天没有课" else if (todayHoliday) "今天是节假日" else "今天周末不上课",
                        body = if (teachingToday) {
                            "第 $week 周的这一天是空的，去安排一点自己的时间。"
                        } else {
                            "日期游标已跳过今天，下个教学日是 ${nextTeachingDay.monthValue} 月 ${nextTeachingDay.dayOfMonth} 日。"
                        }
                    )
                } else {
                    todayCourses.take(4).forEachIndexed { index, course ->
                        CourseRow(course)
                        if (index < minOf(todayCourses.size, 4) - 1) AppDivider()
                    }
                    if (todayCourses.size > 4) {
                        Text(
                            "还有 ${todayCourses.size - 4} 门课，去课表查看",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        item { SectionTitle("快捷入口") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(
                    title = "佛大一卡通",
                    subtitle = "企业微信 · 电费",
                    icon = Icons.Default.Bolt,
                    accent = Pink,
                    modifier = Modifier.weight(1f)
                ) { guide = OfficialServices.ECARD }
                QuickTile(
                    title = "100 教务",
                    subtitle = "登录后导入课表",
                    icon = Icons.Default.School,
                    accent = Blue,
                    modifier = Modifier.weight(1f)
                ) { openTimetableLogin() }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(
                    title = "导入电费",
                    subtitle = "粘贴官方结果",
                    icon = Icons.Default.FileDownload,
                    accent = Purple,
                    modifier = Modifier.weight(1f)
                ) { vm.go("electricity") }
                QuickTile(
                    title = "导入课表",
                    subtitle = "登录 100 教务",
                    icon = Icons.Default.School,
                    accent = Mint,
                    modifier = Modifier.weight(1f)
                ) { openTimetableLogin() }
            }
        }

        item {
            GlassCard(accent = Purple) {
                SectionTitle("最近用电", "电费页") { vm.go("electricity") }
                UsageBarChart(points)
                Text(
                    if (points.isEmpty()) "只有余额也能记账；填入两次电表读数后会生成区间用量。"
                    else "按抄表区间绘制，不做每日估算。共 ${points.size} 个区间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        item {
            InfoNotice(
                "电费来自企业微信「佛大一卡通」，课表来自 App 内登录的 100 教务。App 不读取教务密码，只保存你确认导入的课程。"
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Default.School, null, Modifier.size(12.dp), tint = TextMuted)
                Text(
                    "  本地优先 · 非官方工具",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }

    guide?.let { service ->
        ServiceGuideDialog(
            service = service,
            onDismiss = { guide = null },
            onOpen = {
                if (!OfficialLauncher.openService(context, service)) {
                    vm.notify("未能打开${service.title}，请手动打开企业微信")
                }
            }
        )
    }
}

@Composable
private fun HeroCard(
    week: Int,
    todayText: String,
    onElectricity: () -> Unit,
    onTimetable: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Pink.copy(alpha = 0.24f), Purple.copy(alpha = 0.14f), SurfaceElevated)
                )
            )
            .border(0.8.dp, Pink.copy(alpha = 0.28f), shape)
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                Color.White.copy(alpha = 0.05f),
                radius = size.width * 0.48f,
                center = Offset(size.width * 0.96f, size.height * 0.04f)
            )
            drawCircle(
                Pink.copy(alpha = 0.07f),
                radius = size.width * 0.32f,
                center = Offset(size.width * 0.05f, size.height * 0.96f)
            )
        }
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill("非官方 · 本地优先", color = Pink)
                Spacer(Modifier.weight(1f))
                Text("第 $week 周", style = MaterialTheme.typography.labelMedium, color = Blue)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "把佛大的日常，\n过得清楚一点。",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontFamily = FontFamily.Serif
                )
                Text(
                    "$todayText · 电费与课表都在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrimaryAction("记一笔电费", Modifier.weight(1f), Icons.Default.Bolt, onClick = onElectricity)
                SecondaryAction("看本周课表", Modifier.weight(1f), Icons.Default.CalendarMonth, onClick = onTimetable)
            }
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(modifier = modifier, accent = accent, onClick = onClick, contentPadding = PaddingValues(13.dp)) {
        Box(
            Modifier
                .size(32.dp)
                .background(accent.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CourseRow(course: Course, modifier: Modifier = Modifier) {
    val accent = CourseColors[course.colorIndex.coerceIn(0, CourseColors.lastIndex)]
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(38.dp)
                .background(accent, RoundedCornerShape(4.dp))
        )
        Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                course.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(course.sectionLabel, style = MaterialTheme.typography.labelSmall, color = accent)
                if (course.room.isNotBlank()) {
                    Text("  ·  ${course.room}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                if (course.teacher.isNotBlank()) {
                    Text("  ·  ${course.teacher}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }
        Icon(Icons.Default.Room, null, Modifier.size(15.dp), tint = TextMuted.copy(alpha = 0.7f))
    }
}
