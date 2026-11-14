package dev.campususage.fosu.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.campususage.fosu.BuildConfig
import dev.campususage.fosu.data.*
import dev.campususage.fosu.ui.*
import dev.campususage.fosu.ui.theme.*

@Composable
fun ProfileScreen(vm: FosuViewModel) {
    val data = vm.data
    val context = LocalContext.current
    var showRoom by remember { mutableStateOf(false) }
    var showTerm by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showClearLogin by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf<OfficialService?>(null) }
    val openTimetableLogin = rememberChaoxingImport(vm)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "PROFILE · LOCAL FIRST",
                title = "我的",
                subtitle = "电费本地记录 · 课表来自学习通",
                trailing = { Pill("v${BuildConfig.VERSION_NAME}", color = Purple) }
            )
        }

        item {
            GlassCard(accent = Pink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .background(Pink.copy(alpha = 0.15f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.School, null, Modifier.size(27.dp), tint = Pink)
                    }
                    Column(Modifier.padding(start = 13.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("佛大同学", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text(data.settings.campus.label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("电费 ${data.readings.size} 条", color = Pink)
                    Pill("课程 ${data.courses.size} 门", color = Blue)
                    Pill("本机保存", color = Mint)
                }
                Text(
                    "本应用是非官方工具，与佛山大学、佛大一卡通及学习通均无隶属关系。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        item { SectionTitle("我的设置") }
        item {
            GlassCard {
                SettingRow(
                    icon = Icons.Default.Bolt,
                    title = "宿舍与电价",
                    subtitle = "${data.settings.roomName.ifBlank { "未设置" }} · ¥${data.settings.tariff.toPlainString()} / kWh",
                    onClick = { showRoom = true }
                )
                AppDivider()
                SettingRow(
                    icon = Icons.Default.CalendarMonth,
                    title = "学期与节次",
                    subtitle = "第 ${TimetableMath.currentWeek(data.settings, vm.today)} 周 · 共 ${data.settings.totalWeeks} 周 · ${data.settings.sectionTimes.size} 节",
                    onClick = { showTerm = true }
                )
            }
        }

        item { SectionTitle("数据") }
        item {
            GlassCard(accent = Blue) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("本地记录", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(
                            "${data.readings.size} 条电费 · ${data.courses.size} 门课程",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Text("JSON v2", style = MaterialTheme.typography.labelSmall, color = Purple)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryAction("复制备份", icon = Icons.Default.ContentCopy, onClick = {
                        OfficialLauncher.copy(context, vm.exportBackup(), "备份已复制")
                    })
                    SecondaryAction("分享备份", icon = Icons.Default.Share, onClick = {
                        OfficialLauncher.share(context, "佛大校园助手备份", vm.exportBackup())
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryAction("恢复备份", icon = Icons.Default.Restore, onClick = { showBackup = true })
                    SecondaryAction("清空数据", icon = Icons.Default.DeleteForever, onClick = { showClear = true })
                }
                Text(
                    "备份包含房间、电价、电费记录与课程，不包含学习通密码或 WebView 登录 Cookie。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        item { SectionTitle("官方入口") }
        item {
            GlassCard {
                ServiceRow(
                    title = "佛大一卡通",
                    subtitle = "企业微信 → 工作台 → 缴电费 / 电费查询",
                    icon = Icons.Default.Bolt,
                    accent = Pink
                ) { guide = OfficialServices.ECARD }
                AppDivider()
                ServiceRow(
                    title = "学习通课表",
                    subtitle = "在 App 内登录并同步学生课表",
                    icon = Icons.Default.School,
                    accent = Blue
                ) { openTimetableLogin() }
                AppDivider()
                LinkRow(
                    title = "清除学习通登录",
                    subtitle = "删除 WebView 保存的学习通 Cookie",
                    icon = Icons.Default.Logout,
                    onClick = { showClearLogin = true }
                )
            }
        }

        item {
            InfoNotice(
                "登录学习通时，账号密码只输入学习通官方网页，App 不读取也不保存。同步完成后只保存课程名称、教师、地点、星期、节次和周次。"
            )
        }

        item {
            GlassCard {
                Text("关于佛大校园助手", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Text(
                    "功能：佛大一卡通电费台账、学习通学生课表同步、7 天周课表、数据备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }

    if (showRoom) {
        RoomSettingsDialog(data.settings, { showRoom = false }, vm::updateRoom)
    }
    if (showTerm) {
        TermSettingsDialog(data.settings, vm.today, { showTerm = false }, vm::updateTerm)
    }
    if (showBackup) {
        BackupImportDialog(
            onDismiss = { showBackup = false },
            onImport = { text ->
                val result = vm.importBackup(text)
                if (result.ok) vm.notify(result.message)
                result
            }
        )
    }
    if (showClear) {
        ConfirmDialog(
            title = "清空全部本地数据？",
            body = "电费记录、课程、房间与学期设置都会被删除，且无法撤销。建议先复制备份。",
            confirmText = "全部清空",
            destructive = true,
            onConfirm = vm::clearAll,
            onDismiss = { showClear = false }
        )
    }
    if (showClearLogin) {
        ConfirmDialog(
            title = "清除学习通登录？",
            body = "会删除 WebView 中的学习通 Cookie 和网页存储，下次同步需要重新登录。电费记录和已导入课程不受影响。",
            confirmText = "清除登录",
            destructive = true,
            onConfirm = { OfficialLauncher.clearChaoxingLogin(context) },
            onDismiss = { showClearLogin = false }
        )
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
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = Pink)
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(19.dp), tint = TextMuted)
    }
}

@Composable
private fun ServiceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(19.dp), tint = TextMuted)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = Blue)
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp), tint = TextMuted)
    }
}

@Composable
private fun BackupImportDialog(
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
        title = { Text("恢复备份", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "粘贴由本应用导出的 JSON 备份。恢复会覆盖当前所有电费记录、课程和设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                SecondaryAction("读取剪贴板", icon = Icons.Default.ContentPaste, onClick = {
                    clipboardText(context)?.let { text = it }
                })
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(20000) },
                    label = { Text("JSON 备份") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onImport(text)
                if (result.ok) onDismiss() else error = result.message
            }) { Text("恢复", color = Pink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) } }
    )
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
