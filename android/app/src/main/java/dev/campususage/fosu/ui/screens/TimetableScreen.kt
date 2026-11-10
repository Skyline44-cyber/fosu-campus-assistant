package dev.campususage.fosu.ui.screens

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.fosu.TimetableLoginActivity
import dev.campususage.fosu.data.*
import dev.campususage.fosu.ui.*
import dev.campususage.fosu.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SmallSectionHeight = 72.dp
private val BigColumnWidth = 46.dp
private val TimeColumnWidth = 52.dp
private val DayWidth = 64.dp
private val GridHeaderHeight = 56.dp

@Composable
fun TimetableScreen(vm: FosuViewModel) {
    val data = vm.data
    val context = LocalContext.current
    val week = vm.timetableWeek
    val weekStart = TimetableMath.weekStart(data.settings, week)
    val weekEnd = TimetableMath.weekEnd(data.settings, week)
    val currentWeek = TimetableMath.currentWeek(data.settings, vm.today)
    val todayWeekday = TimetableMath.todayWeekday(vm.today)
    val isCurrentWeek = week == currentWeek
    val weekScheduled = TeachingCalendar.scheduleForWeek(data.settings, week, data.courses)
    val weekCourses = weekScheduled.map { it.course }.distinctBy { it.id }
    val weekHolidays = TeachingCalendar.holidaysInWeek(data.settings, week)
    var editor by remember { mutableStateOf<Course?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showHolidays by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Course?>(null) }
    val gridScroll = rememberScrollState()

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
    val openLogin = {
        loginLauncher.launch(Intent(context, TimetableLoginActivity::class.java))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "TIMETABLE · 100 教务",
                title = "课表",
                subtitle = "第 $week 周 · 6 大节 14 小节 · 本地课程 ${data.courses.size} 门",
                trailing = {
                    IconButton(onClick = {
                        editor = Course(
                            id = "",
                            name = "",
                            weekday = if (isCurrentWeek) todayWeekday else 1,
                            startSection = 1,
                            endSection = 1,
                            weeks = "1-${data.settings.totalWeeks}"
                        )
                    }) { Icon(Icons.Default.Add, "添加课程", tint = Pink) }
                }
            )
        }

        item {
            GlassCard(accent = Blue) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setWeek(week - 1) }, enabled = week > 1) {
                        Icon(Icons.Default.ChevronLeft, "上一周", tint = if (week > 1) TextPrimary else TextMuted.copy(alpha = 0.35f))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("第 $week 周", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text(
                            "${weekStart.format(DateTimeFormatter.ofPattern("M 月 d 日"))} — ${weekEnd.format(DateTimeFormatter.ofPattern("M 月 d 日"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = { vm.setWeek(week + 1) }, enabled = week < data.settings.totalWeeks) {
                        Icon(
                            Icons.Default.ChevronRight,
                            "下一周",
                            tint = if (week < data.settings.totalWeeks) TextPrimary else TextMuted.copy(alpha = 0.35f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrentWeek) Pill("本周", color = Mint, filled = false) else TextButton(onClick = vm::goToCurrentWeek) {
                        Text("回到本周", style = MaterialTheme.typography.labelMedium, color = Blue)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showHolidays = true }) {
                        Text("节假日 ${data.settings.holidays.size}", style = MaterialTheme.typography.labelMedium, color = Danger)
                    }
                    TextButton(onClick = { showImport = true }) {
                        Text("手动导入", style = MaterialTheme.typography.labelMedium, color = Pink)
                    }
                }
            }
        }

        item {
            PrimaryAction(
                "登录 100 教务导入课表",
                Modifier.fillMaxWidth(),
                Icons.Default.School,
                onClick = openLogin
            )
        }

        if (weekHolidays.isNotEmpty()) {
            item {
                InfoNotice(
                    "本周含节假日：" + weekHolidays.joinToString("、") {
                        it.format(DateTimeFormatter.ofPattern("M/d"))
                    } + "。这些日期不排课，日期游标只前进、不消耗课程。",
                    accent = Danger
                )
            }
        }

        item {
            GlassCard(accent = Purple, contentPadding = PaddingValues(8.dp)) {
                WeekGrid(
                    settings = data.settings,
                    week = week,
                    courses = weekScheduled,
                    todayWeekday = if (isCurrentWeek) todayWeekday else -1,
                    scrollState = gridScroll,
                    onEmptyClick = { weekday, section ->
                        editor = Course(
                            id = "",
                            name = "",
                            weekday = weekday,
                            startSection = section,
                            endSection = section,
                            weeks = "1-${data.settings.totalWeeks}"
                        )
                    },
                    onCourseClick = { editor = it }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("左右滑动查看 7 天", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("6 大节 · 14 小节", style = MaterialTheme.typography.labelSmall, color = Purple)
                }
            }
        }

        item {
            val conflictCount = conflictCount(weekScheduled)
            if (conflictCount > 0) {
                InfoNotice("本周有 $conflictCount 处课程时间重叠。请点课程核对节次与周次。", accent = Danger)
            }
        }

        item {
            SectionTitle(
                if (weekCourses.isEmpty()) "本周课程" else "本周课程 · ${weekCourses.size} 门",
                if (weekCourses.isEmpty()) "添加" else "手动导入"
            ) {
                if (weekCourses.isEmpty()) {
                    editor = Course(
                        id = "",
                        name = "",
                        weekday = if (isCurrentWeek) todayWeekday else 1,
                        startSection = 1,
                        endSection = 1,
                        weeks = "1-${data.settings.totalWeeks}"
                    )
                } else {
                    showImport = true
                }
            }
        }

        if (weekCourses.isEmpty()) {
            item {
                GlassCard {
                    EmptyState(
                        icon = Icons.Default.CalendarMonth,
                        title = "第 $week 周还没有课程",
                        body = "登录 100 教务，进入学生课表后抓取导入；也可以手动添加或粘贴课程。"
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryAction("手动导入", icon = Icons.Default.FileUpload, onClick = { showImport = true })
                            PrimaryAction("登录教务", icon = Icons.Default.School, onClick = openLogin)
                        }
                    }
                }
            }
        } else {
            item {
                GlassCard {
                    weekCourses
                        .sortedWith(compareBy({ it.weekday }, { it.startSection }))
                        .forEachIndexed { index, course ->
                            CourseListRow(
                                course = course,
                                shifted = weekScheduled.firstOrNull { it.course.id == course.id }?.shifted == true,
                                today = vm.today,
                                settings = data.settings,
                                onEdit = { editor = course },
                                onDelete = { deleteTarget = course }
                            )
                            if (index < weekCourses.lastIndex) AppDivider()
                        }
                }
            }
        }

        item {
            InfoNotice(
                "课表在 App 内登录 100 教务后从当前页面抓取。只保存课程名称、教师、地点、星期、节次和周次，不抓取备注信息。登录由学校页面处理，App 不保存教务密码。"
            )
        }
    }

    editor?.let { initial ->
        CourseEditorDialog(
            initial = initial,
            onDismiss = { editor = null },
            onSave = { course ->
                val result = vm.saveCourse(course)
                if (result.ok) vm.notify(result.message)
                result
            }
        )
    }
    if (showImport) {
        TimetableImportDialog(
            onDismiss = { showImport = false },
            onImport = { text, replace ->
                val result = vm.importTimetable(text, replace)
                if (result.ok) vm.notify(result.message)
                result
            }
        )
    }
    if (showHolidays) {
        HolidaySettingsDialog(
            initial = data.settings.holidays,
            onDismiss = { showHolidays = false },
            onSave = { dates ->
                val result = vm.updateHolidays(dates)
                if (result.ok) vm.notify(result.message)
                result
            }
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除这门课？",
            body = "${target.name} · ${TimetableMath.weekdayFullLabels.getOrElse(target.weekday - 1) { "周${target.weekday}" }} ${target.sectionLabel}",
            confirmText = "删除",
            destructive = true,
            onConfirm = { vm.deleteCourse(target.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun WeekGrid(
    settings: Settings,
    week: Int,
    courses: List<ScheduledCourse>,
    todayWeekday: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    onEmptyClick: (Int, Int) -> Unit,
    onCourseClick: (Course) -> Unit
) {
    val days = (1..7).toList()
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Row(Modifier.height(GridHeaderHeight)) {
            Box(
                Modifier
                    .width(BigColumnWidth + TimeColumnWidth)
                    .fillMaxHeight()
                    .background(Surface.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("大节", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("小节 / 时间", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
            days.forEach { weekday ->
                val date = TimetableMath.dateForWeekday(settings, week, weekday)
                val holiday = TeachingCalendar.isHoliday(date, settings)
                val isToday = weekday == todayWeekday && !holiday
                Column(
                    Modifier
                        .width(DayWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (holiday) Danger.copy(alpha = 0.10f) else if (isToday) Pink.copy(alpha = 0.10f) else Color.Transparent)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        TimetableMath.weekdayLabels[weekday - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            holiday -> Danger
                            isToday -> Pink
                            else -> TextMuted
                        }
                    )
                    Text(
                        date.format(DateTimeFormatter.ofPattern("M/d")),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            holiday -> Danger
                            isToday -> Pink
                            else -> TextPrimary
                        }
                    )
                    if (holiday) Text("休", fontSize = 8.sp, color = Danger)
                }
            }
        }
        Row {
            BigSectionColumn()
            TimeColumn()
            days.forEach { weekday ->
                val date = TimetableMath.dateForWeekday(settings, week, weekday)
                val holiday = TeachingCalendar.isHoliday(date, settings)
                val isToday = weekday == todayWeekday && !holiday
                DayColumn(
                    weekday = weekday,
                    holiday = holiday,
                    isToday = isToday,
                    courses = courses,
                    onEmptyClick = onEmptyClick,
                    onCourseClick = onCourseClick
                )
            }
        }
    }
}

@Composable
private fun BigSectionColumn() {
    Column(Modifier.width(BigColumnWidth)) {
        SectionSchedule.big.forEachIndexed { index, big ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SmallSectionHeight * big.count.toFloat())
                    .background(if (index % 2 == 0) Surface.copy(alpha = 0.45f) else Surface.copy(alpha = 0.25f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    big.label,
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeColumn() {
    Column(Modifier.width(TimeColumnWidth)) {
        SectionSchedule.small.forEach { section ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(SmallSectionHeight)
                    .border(0.4.dp, Color.White.copy(alpha = 0.06f))
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("${section.index}", fontSize = 9.sp, color = TextMuted)
                Text(section.start, fontSize = 7.sp, color = TextMuted.copy(alpha = 0.85f))
                Text(section.end, fontSize = 7.sp, color = TextMuted.copy(alpha = 0.60f))
            }
        }
    }
}

@Composable
private fun DayColumn(
    weekday: Int,
    holiday: Boolean,
    isToday: Boolean,
    courses: List<ScheduledCourse>,
    onEmptyClick: (Int, Int) -> Unit,
    onCourseClick: (Course) -> Unit
) {
    Box(
        Modifier
            .width(DayWidth)
            .height(SmallSectionHeight * SectionSchedule.COUNT.toFloat())
            .background(
                when {
                    holiday -> Danger.copy(alpha = 0.045f)
                    isToday -> Pink.copy(alpha = 0.035f)
                    else -> Color.Transparent
                }
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(SectionSchedule.COUNT) { index ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(SmallSectionHeight)
                        .border(0.4.dp, Color.White.copy(alpha = 0.055f))
                        .then(
                            if (holiday) Modifier else Modifier.clickable { onEmptyClick(weekday, index + 1) }
                        )
                )
            }
        }
        if (holiday) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("节假日", fontSize = 9.sp, color = Danger.copy(alpha = 0.8f))
            }
        } else {
            courses
                .filter { it.date.dayOfWeek.value == weekday }
                .sortedBy { it.course.startSection }
                .forEach { scheduled ->
                    CourseBlock(
                        course = scheduled.course,
                        shifted = scheduled.shifted,
                        onClick = { onCourseClick(scheduled.course) }
                    )
                }
        }
    }
}

@Composable
private fun CourseBlock(course: Course, shifted: Boolean, onClick: () -> Unit) {
    val accent = CourseColors[course.colorIndex.coerceIn(0, CourseColors.lastIndex)]
    val span = (course.endSection - course.startSection + 1).coerceAtLeast(1)
    val top = SmallSectionHeight * (course.startSection - 1).toFloat()
    val height = SmallSectionHeight * span.toFloat() - 4.dp
    Column(
        Modifier
            .offset(y = top + 2.dp)
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.15f))))
            .border(0.8.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(3.dp)
            .semantics { contentDescription = course.fullLabel },
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            course.name,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (course.teacher.isNotBlank()) {
            Text(course.teacher, fontSize = 7.sp, lineHeight = 9.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (course.room.isNotBlank()) {
            Text(course.room, fontSize = 7.sp, lineHeight = 9.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (shifted) "调 · ${course.timeLabel}" else course.timeLabel,
            fontSize = 7.sp,
            lineHeight = 9.sp,
            color = accent,
            maxLines = 1
        )
        Text(course.weekLabel, fontSize = 7.sp, lineHeight = 9.sp, color = TextMuted, maxLines = 1)
    }
}

@Composable
private fun CourseListRow(
    course: Course,
    shifted: Boolean,
    today: LocalDate,
    settings: Settings,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = CourseColors[course.colorIndex.coerceIn(0, CourseColors.lastIndex)]
    val nextDate = TeachingCalendar.nextCourseDate(course, settings, today)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(4.dp).height(58.dp).background(accent, RoundedCornerShape(4.dp)))
        Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "教师：${course.teacher.ifBlank { "未填写" }}    地点：${course.room.ifBlank { "未填写" }}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                (if (shifted) "调课 · " else "") +
                    "${TimetableMath.weekdayFullLabels.getOrElse(course.weekday - 1) { "周${course.weekday}" }} · ${course.timeLabel} · ${course.weekLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = accent
            )
            Text(
                if (nextDate == null) "本学期已结束" else "下次：${nextDate.format(DateTimeFormatter.ofPattern("M 月 d 日"))}（节假日自动跳过）",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Delete, "删除课程", Modifier.size(17.dp), tint = TextMuted.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun CourseEditorDialog(
    initial: Course,
    onDismiss: () -> Unit,
    onSave: (Course) -> ActionResult
) {
    var name by remember { mutableStateOf(initial.name) }
    var teacher by remember { mutableStateOf(initial.teacher) }
    var room by remember { mutableStateOf(initial.room) }
    var weekday by remember { mutableIntStateOf(initial.weekday) }
    var start by remember { mutableStateOf(initial.startSection.toString()) }
    var end by remember { mutableStateOf(initial.endSection.toString()) }
    var weeks by remember { mutableStateOf(initial.weeks) }
    var colorIndex by remember { mutableIntStateOf(initial.colorIndex.coerceIn(0, 3)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (initial.id.isBlank()) "添加课程" else "编辑课程",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it.take(30) },
                        label = { Text("教师") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it.take(30) },
                        label = { Text("地点") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    )
                }
                Text("星期", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimetableMath.weekdayFullLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = weekday == index + 1,
                            onClick = { weekday = index + 1 },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("开始小节") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("结束小节") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    )
                }
                Text(
                    "可用小节：1-14。下课时间会自动从课表时间表读取，并在课程卡片中显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = weeks,
                    onValueChange = { weeks = it.take(40) },
                    label = { Text("周次") },
                    placeholder = { Text("1-16 或 1-8,10-16") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Text("颜色", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CourseColors.forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.8f))
                                .border(
                                    if (colorIndex == index) 2.dp else 0.dp,
                                    if (colorIndex == index) TextPrimary else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { colorIndex = index }
                        )
                    }
                }
                Text(
                    "周次支持 1-16、1-8,10-16、1-16单、1-16双。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val startSection = start.toIntOrNull()
                val endSection = end.toIntOrNull()
                if (startSection == null || endSection == null) {
                    error = "节次应为数字"
                    return@TextButton
                }
                val result = onSave(
                    initial.copy(
                        name = name,
                        teacher = teacher,
                        room = room,
                        weekday = weekday,
                        startSection = startSection,
                        endSection = endSection,
                        weeks = weeks,
                        colorIndex = colorIndex
                    )
                )
                if (result.ok) onDismiss() else error = result.message
            }) { Text("保存", color = Pink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) } }
    )
}

@Composable
private fun TimetableImportDialog(
    onDismiss: () -> Unit,
    onImport: (String, Boolean) -> ActionResult
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = { Text("手动导入课表", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "如果 100 教务页面抓取失败，可以把课程按下面格式粘贴进来：\n课程名|教师|地点|周一|1-2|1-16\n也支持 JSON 课程数组。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !replace,
                        onClick = { replace = false },
                        label = { Text("追加", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = replace,
                        onClick = { replace = true },
                        label = { Text("替换全部", style = MaterialTheme.typography.labelSmall) }
                    )
                    Spacer(Modifier.weight(1f))
                    SecondaryAction("粘贴", icon = Icons.Default.ContentPaste, onClick = {
                        clipboardText(context)?.let { text = it }
                    })
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(6000) },
                    label = { Text("课程文本 / JSON") },
                    placeholder = { Text("高等数学|张三|C5-101|周一|1-2|1-16\n大学英语|李四|B2-203|周三|3-4|1-16") },
                    minLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Text(
                    "周次可留空，默认 1-16。导入只写本机，不会修改教务系统里的课程。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onImport(text, replace)
                if (result.ok) onDismiss() else error = result.message
            }) { Text("导入", color = Pink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) } }
    )
}

private fun conflictCount(courses: List<ScheduledCourse>): Int {
    var count = 0
    for (i in courses.indices) {
        for (j in i + 1 until courses.size) {
            val a = courses[i]
            val b = courses[j]
            if (a.date == b.date &&
                a.course.startSection <= b.course.endSection &&
                b.course.startSection <= a.course.endSection
            ) {
                count++
            }
        }
    }
    return count
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
