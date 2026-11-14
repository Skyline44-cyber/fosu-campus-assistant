package dev.campususage.fosu.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.campususage.fosu.data.*
import dev.campususage.fosu.ui.theme.*
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun RoomSettingsDialog(
    initial: Settings,
    onDismiss: () -> Unit,
    onSave: (String, Campus, BigDecimal) -> ActionResult
) {
    var room by remember { mutableStateOf(initial.roomName) }
    var campus by remember { mutableStateOf(initial.campus) }
    var tariff by remember { mutableStateOf(initial.tariff.toPlainString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = { Text("宿舍与电价", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it.take(30) },
                    label = { Text("宿舍 / 房间") },
                    placeholder = { Text("例如 A1-302") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Text("校区", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Campus.entries.forEach { item ->
                        FilterChip(
                            selected = campus == item,
                            onClick = { campus = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = tariff,
                    onValueChange = { tariff = it.take(8) },
                    label = { Text("电价（元 / kWh）") },
                    placeholder = { Text("0.65") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Text(
                    "电价用于估算，最终以学校账单为准。不同校区、宿舍或月份可能存在差异。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = tariff.trim().toBigDecimalOrNull()
                if (price == null) {
                    error = "请输入有效电价"
                    return@TextButton
                }
                val result = onSave(room, campus, price)
                if (result.ok) onDismiss() else error = result.message
            }) { Text("保存", color = Pink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
        }
    )
}

@Composable
fun TermSettingsDialog(
    initial: Settings,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Int) -> ActionResult
) {
    var start by remember { mutableStateOf(initial.termStart) }
    var totalWeeks by remember { mutableStateOf(initial.totalWeeks.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = { Text("学期设置", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DateField("学期第一周的周一", start, { start = it })
                OutlinedTextField(
                    value = totalWeeks,
                    onValueChange = { totalWeeks = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("总周数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Text(
                    "学习通同步后会自动写入学期第一周周一、总周数和节次时间；也可以在这里手动修正。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    "今天按此设置计算为第 ${TimetableMath.currentWeek(initial.copy(termStart = start), today)} 周。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val weeks = totalWeeks.toIntOrNull()
                if (weeks == null) {
                    error = "总周数应为数字"
                    return@TextButton
                }
                val result = onSave(start, weeks)
                if (result.ok) onDismiss() else error = result.message
            }) { Text("保存", color = Pink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
        }
    )
}
