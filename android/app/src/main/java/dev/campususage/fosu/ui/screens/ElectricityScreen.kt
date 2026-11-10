package dev.campususage.fosu.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.fosu.data.*
import dev.campususage.fosu.ui.*
import dev.campususage.fosu.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun ElectricityScreen(vm: FosuViewModel) {
    val data = vm.data
    val context = LocalContext.current
    val sorted = ElectricityMath.sorted(data.readings)
    val latestBalance = ElectricityMath.latestBalance(data.readings)
    val month = YearMonth.from(vm.today)
    val monthKwh = ElectricityMath.usageInMonth(data.readings, month)
    val average = ElectricityMath.averageDailyKwh(data.readings, vm.today)
    val estimatedDays = ElectricityMath.estimatedDays(latestBalance?.balance, data.settings.tariff, average)
    val points = ElectricityMath.chartPoints(data.readings, 30)
    val usages = remember(data.readings) { usageByReading(sorted) }

    var showRoom by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ElectricityReading?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "ELECTRICITY · 佛大一卡通",
                title = "电费",
                subtitle = "${data.settings.campus.label} · ${data.settings.roomName.ifBlank { "未设置房间" }} · 估算电价 ¥${data.settings.tariff.toPlainString()}",
                trailing = {
                    IconButton(onClick = { showRoom = true }) {
                        Icon(Icons.Default.Settings, "宿舍设置", tint = TextMuted)
                    }
                }
            )
        }

        if (data.settings.roomName.isBlank()) {
            item {
                GlassCard(accent = Pink) {
                    Text("先告诉我住哪里", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "房间只用于本机区分记录，不会上传。电价用于估算，以学校账单为准。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    SecondaryAction("设置宿舍", icon = Icons.Default.Edit, onClick = { showRoom = true })
                }
            }
        }

        item {
            BalanceCard(
                balance = latestBalance?.balance,
                date = latestBalance?.date,
                room = data.settings.roomName,
                tariff = data.settings.tariff,
                onOfficial = { showGuide = true },
                onRecord = { showAdd = true }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "${month.monthValue} 月用电",
                    value = kwh(monthKwh),
                    unit = "kWh",
                    icon = Icons.Default.Bolt,
                    accent = Blue,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "近 7 日日均",
                    value = kwh(average),
                    unit = "kWh",
                    icon = Icons.Default.Insights,
                    accent = Purple,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("本月电费约 ¥" + money(monthKwh.multiply(data.settings.tariff)), color = Blue)
                Pill(
                    "预计可用 " + (estimatedDays?.toString() ?: "—") + " 天",
                    color = if ((estimatedDays ?: 0) <= 3) Danger else Mint
                )
            }
        }

        item {
            GlassCard(accent = Purple) {
                SectionTitle("抄表区间", if (points.isEmpty()) "记一笔" else null) { showAdd = true }
                UsageBarChart(points)
                Text(
                    "每个柱子代表两次抄表之间的用电量，不把区间用量摊到每一天。预计可用天数 = 余额 ÷（电价 × 日均用电）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        item { SectionTitle("电费记录", "导入官方结果") { showImport = true } }

        if (sorted.isEmpty()) {
            item {
                GlassCard {
                    EmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = "还没有电费记录",
                        body = "打开佛大一卡通查看后，把余额或电表读数记进来。"
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryAction("官方入口", icon = Icons.Default.OpenInNew, onClick = { showGuide = true })
                            PrimaryAction("记一笔", icon = Icons.Default.Add, onClick = { showAdd = true })
                        }
                    }
                }
            }
        } else {
            item {
                GlassCard {
                    sorted.asReversed().forEachIndexed { index, reading ->
                        ReadingRow(
                            reading = reading,
                            usage = usages[reading.id],
                            tariff = data.settings.tariff,
                            onDelete = { deleteTarget = reading }
                        )
                        if (index < sorted.lastIndex) AppDivider()
                    }
                }
            }
        }

        item {
            InfoNotice("余额与读数由你从官方页面复制或手动记录。应用没有学校账号密码，也不会自动查询「佛大一卡通」。")
        }
    }

    if (showRoom) {
        RoomSettingsDialog(initial = data.settings, onDismiss = { showRoom = false }, onSave = vm::updateRoom)
    }
    if (showAdd) {
        AddReadingDialog(
            today = vm.today,
            lastMeter = sorted.lastOrNull { it.meter != null }?.meter,
            onDismiss = { showAdd = false },
            onSave = { date, meter, balance, note ->
                val result = vm.addReading(date, meter, balance, note)
                if (result.ok) vm.notify(result.message)
                result
            }
        )
    }
    if (showImport) {
        ImportElectricityDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val result = vm.importElectricity(text)
                if (result.ok) vm.notify(result.message)
                result
            }
        )
    }
    if (showGuide) {
        ServiceGuideDialog(
            service = OfficialServices.ECARD,
            onDismiss = { showGuide = false },
            onOpen = {
                if (!OfficialLauncher.openService(context, OfficialServices.ECARD)) {
                    vm.notify("未能打开企业微信，请手动打开并进入佛大一卡通")
                }
            }
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除这条记录？",
            body = "${target.date} · ${target.balance?.let { "余额 ¥${money(it)}" } ?: "读数 ${kwh(target.meter)} kWh"}",
            confirmText = "删除",
            destructive = true,
            onConfirm = { vm.deleteReading(target.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun BalanceCard(
    balance: BigDecimal?,
    date: LocalDate?,
    room: String,
    tariff: BigDecimal,
    onOfficial: () -> Unit,
    onRecord: () -> Unit
) {
    val shape = RoundedCornerShape(27.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Pink.copy(alpha = 0.28f), Purple.copy(alpha = 0.16f), SurfaceElevated)
                )
            )
            .border(0.8.dp, Pink.copy(alpha = 0.30f), shape)
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.055f), size.width * 0.45f, Offset(size.width * 0.94f, 0f))
            drawCircle(Blue.copy(alpha = 0.06f), size.width * 0.28f, Offset(0f, size.height))
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill("佛大一卡通 · 余额", color = Pink)
                Spacer(Modifier.weight(1f))
                Text(
                    date?.format(DateTimeFormatter.ofPattern("M 月 d 日")) ?: "还没有记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (balance == null) "—" else "¥ ${money(balance)}",
                    fontFamily = FontFamily.Serif,
                    fontSize = 42.sp,
                    color = TextPrimary
                )
                Text(
                    " 元",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            Text(
                "${room.ifBlank { "未设置房间" }} · 估算电价 ¥${tariff.stripTrailingZeros().toPlainString()} / kWh",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrimaryAction("官方查询", Modifier.weight(1f), Icons.Default.OpenInNew, onClick = onOfficial)
                SecondaryAction("记一笔", Modifier.weight(1f), Icons.Default.Add, onClick = onRecord)
            }
        }
    }
}

@Composable
private fun ReadingRow(
    reading: ElectricityReading,
    usage: BigDecimal?,
    tariff: BigDecimal,
    onDelete: () -> Unit
) {
    val accent = when (reading.source) {
        ReadingSource.OFFICIAL -> Pink
        ReadingSource.MANUAL -> Blue
        ReadingSource.IMPORTED -> Purple
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .background(accent, androidx.compose.foundation.shape.CircleShape)
        )
        Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reading.date.format(DateTimeFormatter.ofPattern("yyyy.M.d")),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.width(7.dp))
                Pill(reading.source.label, color = accent)
            }
            Text(
                buildString {
                    reading.balance?.let { append("余额 ¥${money(it)}") }
                    if (reading.balance != null && reading.meter != null) append("  ·  ")
                    reading.meter?.let { append("读数 ${kwh(it)} kWh") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            if (usage != null) {
                Text(
                    "较上次 +${kwh(usage)} kWh · 约 ¥${money(usage.multiply(tariff).setScale(2, RoundingMode.HALF_UP))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = accent
                )
            }
            if (reading.note.isNotBlank()) {
                Text(
                    reading.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Delete, "删除记录", Modifier.size(17.dp), tint = TextMuted.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun AddReadingDialog(
    today: LocalDate,
    lastMeter: BigDecimal?,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, String, String) -> ActionResult
) {
    var date by remember { mutableStateOf(today) }
    var meter by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = { Text("记一笔电费", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DateField("日期", date, { date = it })
                OutlinedTextField(
                    value = meter,
                    onValueChange = { meter = it.take(12) },
                    label = { Text("电表读数（kWh，选填）") },
                    placeholder = { Text(lastMeter?.let { "上次 ${kwh(it)}" } ?: "例如 128.40") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it.take(12) },
                    label = { Text("账户余额（元，选填）") },
                    placeholder = { Text("例如 45.60") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    label = { Text("备注（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Text(
                    "余额和读数至少填一项。第一次填读数只作为起点，第二次才会产生区间用量。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onSave(date, meter, balance, note)
                if (result.ok) onDismiss() else error = result.message
            }) { Text("保存", color = Pink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) } }
    )
}

@Composable
private fun ImportElectricityDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> ActionResult
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = { Text("导入官方查询结果", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "从佛大一卡通复制余额、剩余电量或房间信息，粘贴到这里。支持「余额」「剩余金额」「剩余电量」「电表读数」等文字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                SecondaryAction("读取剪贴板", icon = Icons.Default.ContentPaste, onClick = {
                    clipboardText(context)?.let { text = it }
                })
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    label = { Text("粘贴查询结果") },
                    placeholder = { Text("房间 A1-302\n余额 45.60 元\n剩余电量 128.4 kWh") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Text(
                    "只读取文本中的数字，不会保存账号、密码或页面截图。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onImport(text)
                if (result.ok) onDismiss() else error = result.message
            }) { Text("解析并保存", color = Pink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) } }
    )
}

private fun usageByReading(sorted: List<ElectricityReading>): Map<String, BigDecimal> {
    val result = mutableMapOf<String, BigDecimal>()
    var previousMeter: BigDecimal? = null
    var previousDate: LocalDate? = null
    sorted.forEach { reading ->
        val meter = reading.meter ?: return@forEach
        val beforeMeter = previousMeter
        val beforeDate = previousDate
        if (beforeMeter != null && beforeDate != null && reading.date.isAfter(beforeDate)) {
            val delta = meter.subtract(beforeMeter)
            if (delta.signum() >= 0) result[reading.id] = delta
        }
        previousMeter = meter
        previousDate = reading.date
    }
    return result
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
