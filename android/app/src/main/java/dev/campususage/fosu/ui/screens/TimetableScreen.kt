package dev.campususage.fosu.ui.screens

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.fosu.data.*
import dev.campususage.fosu.ui.*
import dev.campususage.fosu.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val GridTimeWidth = 46.dp
private val GridDayWidth = 58.dp
private val GridSectionHeight = 64.dp
private val GridHeaderHeight = 54.dp

@Composable
fun TimetableScreen(vm: FosuViewModel) {
    val data = vm.data
    val settings = data.settings
    val week = vm.timetableWeek
    val currentWeek = TimetableMath.currentWeek(settings, vm.today)
    val todayWeekday = TimetableMath.todayWeekday(vm.today)
    val isCurrentWeek = week == currentWeek
    val weekStart = TimetableMath.weekStart(settings, week)
    val weekEnd = TimetableMath.weekEnd(settings, week)
    val weekCourses = TimetableMath.coursesForWeek(data.courses, week)
    val conflicts = conflictCount(weekCourses)
    val openChaoxing = rememberChaoxingImport(vm)

    var editor by remember { mutableStateOf<Course?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showTerm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Course?>(null) }
    val gridScroll = rememberScrollState()

    fun newCourse(weekday: Int, section: Int): Course = Course(
        id = "",
        name = "",
        weekday = weekday,
        startSection = section,
        endSection = section,
        weeks = "1-${settings.totalWeeks}"
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "TIMETABLE · 学习通",
                title = "课表",
                subtitle = "第 $week 周 · ${data.courses.size} 门课程 · ${settings.sectionTimes.size} 节",
                trailing = {
                    IconButton(onClick = {
                        editor = newCourse(if (isCurrentWeek) todayWeekday else 1, 1)
                    }) { Icon(Icons.Default.Add, "添加课程", tint = Pink) }
                }
            )
        }

        item {
            GlassCard(accent = ChaoxingBlue) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setWeek(week - 1) }, enabled = week > 1) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            "上一周",
                            tint = if (week > 1) TextPrimary else TextMuted.copy(alpha = 0.35f)
                        )
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("第 $week 周", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text(
                            "${weekStart.format(DateTimeFormatter.ofPattern("M 月 d 日"))} — ${weekEnd.format(DateTimeFormatter.ofPattern("M 月 d 日"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    IconButton(
                        onClick = { vm.setWeek(week + 1) },
                        enabled = week < settings.totalWeeks
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            "下一周",
                            tint = if (week < settings.totalWeeks) TextPrimary else TextMuted.copy(alpha = 0.35f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrentWeek) {
                        Pill("本周", color = ChaoxingBlue, filled = false)
                    } else {
                        TextButton(onClick = vm::goToCurrentWeek) {
                            Text("回到本周", style = MaterialTheme.typography.labelMedium, color = ChaoxingBlue)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showTerm = true }) {
                        Text("学期设置", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    }
                    TextButton(onClick = { showImport = true }) {
                        Text("手动导入", style = MaterialTheme.typography.labelMedium, color = Pink)
                    }
                }
            }
        }

        item {
            PrimaryAction(
                "登录学习通同步课表",
                Modifier.fillMaxWidth(),
                Icons.Default.Sync,
                onClick = openChaoxing
            )
        }

        item {
            GlassCard(accent = ChaoxingBlue, contentPadding = PaddingValues(8.dp)) {
                ChaoxingWeekGrid(
                    settings = settings,
                    week = week,
                    courses = weekCourses,
                    todayWeekday = if (isCurrentWeek) todayWeekday else -1,
                    scrollState = gridScroll,
                    onEmptyClick = { weekday, section -> editor = newCourse(weekday, section) },
                    onCourseClick = { editor = it }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("左右滑动查看 7 天", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("学习通同步", style = MaterialTheme.typography.labelSmall, color = ChaoxingBlue)
                }
            }
        }

        if (conflicts > 0) {
            item {
                InfoNotice("本周有 $conflicts 处课程时间重叠，请点课程核对节次与周次。", accent = Danger)
            }
        }

        if (weekCourses.isEmpty()) {
            item {
                GlassCard {
                    EmptyState(
                        icon = Icons.Default.CalendarMonth,
                        title = "第 $week 周还没有课程",
                        body = "登录学习通后会自动同步学生课表；也可以手动添加或粘贴课程。"
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryAction("手动导入", icon = Icons.Default.FileUpload) {
                                showImport = true
                            }
                            PrimaryAction("登录学习通", icon = Icons.Default.Sync) {
                                openChaoxing()
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("课程清单 · ${data.courses.size} 门", "添加") {
                editor = newCourse(1, 1)
            }
        }

        if (data.courses.isNotEmpty()) {
            item {
                GlassCard {
                    data.courses
                        .sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.name }))
                        .forEachIndexed { index, course ->
                            ChaoxingCourseRow(
                                course = course,
                                settings = settings,
                                today = vm.today,
                                onEdit = { editor = course },
                                onDelete = { deleteTarget = course }
                            )
                            if (index < data.courses.lastIndex) AppDivider()
                        }
                }
            }
        }

        item {
            InfoNotice(
                "课表在 App 内登录学习通后，从学生课表接口同步。只保存课程名称、教师、地点、星期、节次和周次；登录 Cookie 只留在本机 WebView。"
            )
        }
    }

    editor?.let { initial ->
        CourseEditorDialog(
            initial = initial,
            sectionTimes = settings.sectionTimes,
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
    if (showTerm) {
        TermSettingsDialog(settings, vm.today, { showTerm = false }, vm::updateTerm)
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
private fun ChaoxingWeekGrid(
    settings: Settings,
    week: Int,
    courses: List<Course>,
    todayWeekday: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    onEmptyClick: (Int, Int) -> Unit,
    onCourseClick: (Course) -> Unit
) {
    val days = (1..7).toList()
    val sectionCount = TimetableSections.sectionCount(settings, courses)
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Row(Modifier.height(GridHeaderHeight)) {
            Box(
                Modifier
                    .width(GridTimeWidth)
                    .fillMaxHeight()
                    .background(Surface.copy(alpha = 0.55f))
                    .border(0.4.dp, Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("节次", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("时间", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
            days.forEach { weekday ->
                DayHeader(
                    settings = settings,
                    week = week,
                    weekday = weekday,
                    isToday = weekday == todayWeekday
                )
            }
        }
        Row(Modifier.height(GridSectionHeight * sectionCount.toFloat())) {
            TimeRail(settings.sectionTimes, sectionCount)
            days.forEach { weekday ->
                DayColumn(
                    weekday = weekday,
                    isToday = weekday == todayWeekday,
                    sectionCount = sectionCount,
                    courses = courses.filter { it.weekday == weekday },
                    onEmptyClick = onEmptyClick,
                    onCourseClick = onCourseClick
                )
            }
        }
    }
}

@Composable
private fun DayHeader(
    settings: Settings,
    week: Int,
    weekday: Int,
    isToday: Boolean
) {
    val date = TimetableMath.dateForWeekday(settings, week, weekday)
    Column(
        Modifier
            .width(GridDayWidth)
            .fillMaxHeight()
            .background(if (isToday) ChaoxingBlue.copy(alpha = 0.16f) else Color.Transparent)
            .border(0.4.dp, Color.White.copy(alpha = 0.06f))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            TimetableMath.weekdayFullLabels[weekday - 1],
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) ChaoxingBlue else TextMuted
        )
        Text(
            date.format(DateTimeFormatter.ofPattern("M/d")),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) ChaoxingBlue else TextPrimary
        )
        if (isToday) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .background(ChaoxingBlue, CircleShape)
            )
        }
    }
}

@Composable
private fun TimeRail(times: List<String>, sectionCount: Int) {
    Column(Modifier.width(GridTimeWidth)) {
        for (section in 1..sectionCount) {
            val range = TimetableSections.timeAt(times, section)
            val start = range.substringBefore('-').trim()
            val end = range.substringAfter('-', "").trim()
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(GridSectionHeight)
                    .border(0.4.dp, Color.White.copy(alpha = 0.06f))
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    section.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (start.isNotBlank()) {
                    Text(start, fontSize = 7.sp, color = TextMuted, textAlign = TextAlign.Center)
                }
                if (end.isNotBlank()) {
                    Text(end, fontSize = 7.sp, color = TextMuted.copy(alpha = 0.65f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    weekday: Int,
    isToday: Boolean,
    sectionCount: Int,
    courses: List<Course>,
    onEmptyClick: (Int, Int) -> Unit,
    onCourseClick: (Course) -> Unit
) {
    Box(
        Modifier
            .width(GridDayWidth)
            .height(GridSectionHeight * sectionCount.toFloat())
            .background(if (isToday) ChaoxingBlue.copy(alpha = 0.045f) else Color.Transparent)
    ) {
        Column(Modifier.fillMaxSize()) {
            for (section in 1..sectionCount) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(GridSectionHeight)
                        .border(0.4.dp, Color.White.copy(alpha = 0.055f))
                        .clickable { onEmptyClick(weekday, section) }
                )
            }
        }
        courses
            .sortedBy { it.startSection }
            .forEach { course ->
                val span = (course.endSection - course.startSection + 1).coerceAtLeast(1)
                CourseBlock(
                    course = course,
                    span = span,
                    onClick = { onCourseClick(course) }
                )
            }
    }
}

@Composable
private fun CourseBlock(course: Course, span: Int, onClick: () -> Unit) {
    val accent = CourseColors[course.colorIndex.coerceIn(0, CourseColors.lastIndex)]
    Column(
        Modifier
            .offset(y = GridSectionHeight * (course.startSection - 1).toFloat() + 2.dp)
            .fillMaxWidth()
            .height(GridSectionHeight * span.toFloat() - 4.dp)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.94f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .semantics {
                contentDescription = listOf(
                    course.name,
                    course.teacher,
                    course.room,
                    course.sectionLabel,
                    course.weekLabel
                ).filter { it.isNotBlank() }.joinToString(" · ")
            },
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            course.name,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = if (span >= 2) 3 else 2,
            overflow = TextOverflow.Ellipsis
        )
        if (course.room.isNotBlank()) {
            Text(
                course.room,
                fontSize = 7.sp,
                lineHeight = 9.sp,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (span >= 2 && course.teacher.isNotBlank()) {
            Text(
                course.teacher,
                fontSize = 7.sp,
                lineHeight = 9.sp,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChaoxingCourseRow(
    course: Course,
    settings: Settings,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = CourseColors[course.colorIndex.coerceIn(0, CourseColors.lastIndex)]
    val nextDate = TimetableMath.nextCourseDate(course, settings, today)
    val time = TimetableSections.timeRange(settings.sectionTimes, course.startSection, course.endSection)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(5.dp).height(64.dp).background(accent, RoundedCornerShape(4.dp)))
        Column(
            Modifier.padding(start = 10.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(course.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "教师：${course.teacher.ifBlank { "未填写" }}    地点：${course.room.ifBlank { "未填写" }}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                "${TimetableMath.weekdayFullLabels.getOrElse(course.weekday - 1) { "周${course.weekday}" }} · ${course.sectionLabel}" +
                    (if (time.isBlank()) "" else " · $time") +
                    " · ${course.weekLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = accent
            )
            Text(
                if (nextDate == null) "本学期已结束"
                else "下次：${nextDate.format(DateTimeFormatter.ofPattern("M 月 d 日"))}",
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
    sectionTimes: List<String>,
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
    var colorIndex by remember { mutableIntStateOf(initial.colorIndex.coerceIn(0, CourseColors.lastIndex)) }
    var error by remember { mutableStateOf<String?>(null) }
    val startValue = start.toIntOrNull()
    val endValue = end.toIntOrNull()
    val preview = if (startValue != null && endValue != null) {
        TimetableSections.timeRange(sectionTimes, startValue, endValue)
    } else {
        ""
    }

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
                        label = { Text("开始节次") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("结束节次") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    )
                }
                Text(
                    "节次范围 1-${TimetableSections.MAX_SECTION}" +
                        (if (preview.isBlank()) "" else "，当前时间 $preview"),
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
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CourseColors.forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(27.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.85f))
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
                    "学习通同步失败时，可以把课程按下面格式粘贴进来：\n课程名|教师|地点|周一|1-2|1-16\n也支持 JSON 课程数组。",
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
                    SecondaryAction("粘贴", icon = Icons.Default.ContentPaste) {
                        clipboardText(context)?.let { text = it }
                    }
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
                    "周次可留空，默认 1-16。手动导入只写本机，不会修改学习通里的课程。",
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

private fun conflictCount(courses: List<Course>): Int {
    var count = 0
    for (i in courses.indices) {
        for (j in i + 1 until courses.size) {
            val a = courses[i]
            val b = courses[j]
            if (a.weekday == b.weekday &&
                a.startSection <= b.endSection &&
                b.startSection <= a.endSection
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
