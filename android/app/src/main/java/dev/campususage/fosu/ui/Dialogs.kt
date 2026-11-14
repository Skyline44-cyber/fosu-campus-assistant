package dev.campususage.fosu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.fosu.data.OfficialLauncher
import dev.campususage.fosu.data.OfficialService
import dev.campususage.fosu.ui.theme.*

@Composable
fun ServiceGuideDialog(
    service: OfficialService,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    val appName = if (service.packageName == OfficialLauncher.WEWORK_PACKAGE) "企业微信" else "官方应用"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(service.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text(service.subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                service.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(Pink.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", fontSize = 10.sp, color = Pink)
                        }
                        Text(
                            step,
                            Modifier.padding(start = 9.dp, top = 2.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
                InfoNotice(
                    "小程序运行在微信/企业微信沙箱内，本应用不能直接读取其中的账号或余额。这里提供官方入口，数据由你主动导入或记录。"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpen(); onDismiss() }) { Text("打开$appName", color = Pink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后", color = TextMuted) }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String = "确定",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (destructive) Danger else Pink)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
        }
    )
}

@Composable
fun OnboardingDialog(onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("佛大 · 校园日常", style = MaterialTheme.typography.labelSmall, color = Pink, letterSpacing = 1.4.sp)
                Text("电费与课表，\n放在一个地方。", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                OnboardingPoint(
                    Icons.Default.Bolt,
                    "电费",
                    "从企业微信「佛大一卡通」查看余额，回到这里记账或粘贴导入，自动生成用电趋势。"
                )
                OnboardingPoint(
                    Icons.Default.School,
                    "课表",
                    "在 App 内登录学习通，自动同步学生课表；也可以手动添加或粘贴课程。"
                )
                OnboardingPoint(
                    Icons.Default.Lock,
                    "登录由学习通页面处理",
                    "App 不读取、不保存你的学习通密码；只把同步到的课程写入本机。"
                )
                Text(
                    "本应用为非官方工具，与佛山大学及上述服务无隶属关系。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text("开始使用", color = Pink) }
        }
    )
}

@Composable
private fun OnboardingPoint(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(34.dp)
                .background(Pink.copy(alpha = 0.10f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = Pink)
        }
        Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
