package dev.campususage.fosu.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import dev.campususage.fosu.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val PagePadding = 18.dp

@Composable
fun FosuBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Background, Color(0xFF0D1220), Background)
                )
            )
    ) {
        StarField()
        Box(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun StarField() {
    val stars = remember {
        listOf(
            0.08f to 0.07f, 0.19f to 0.16f, 0.32f to 0.05f, 0.47f to 0.12f,
            0.63f to 0.06f, 0.78f to 0.15f, 0.91f to 0.08f, 0.12f to 0.31f,
            0.88f to 0.36f, 0.07f to 0.58f, 0.93f to 0.63f, 0.17f to 0.82f,
            0.83f to 0.89f, 0.42f to 0.94f, 0.68f to 0.91f, 0.55f to 0.42f
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Pink.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.18f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * 0.15f, size.height * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Blue.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width, size.height * 0.62f),
                radius = size.width * 0.8f
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width, size.height * 0.62f)
        )
        stars.forEachIndexed { index, (x, y) ->
            drawCircle(
                color = Color.White.copy(alpha = if (index % 3 == 0) 0.35f else 0.18f),
                radius = if (index % 4 == 0) 1.4.dp.toPx() else 0.9.dp.toPx(),
                center = Offset(size.width * x, size.height * y)
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(accent.copy(alpha = 0.15f), SurfaceElevated)
            )
        )
        .border(0.7.dp, Outline.copy(alpha = 0.75f), shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(
        clickable.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Pink, letterSpacing = 1.8.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = Pink, modifier = Modifier.padding(end = 7.dp), fontSize = 12.sp)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = Blue)
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Pink,
    filled: Boolean = false
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.10f))
            .border(0.7.dp, color.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) Ink else color,
            maxLines = 1
        )
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    GlassCard(modifier = modifier, accent = accent, onClick = onClick, contentPadding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(27.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = accent)
            }
            Text(
                label,
                Modifier.padding(start = 7.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = FontFamily.Serif, fontSize = 25.sp, color = TextPrimary)
            if (unit.isNotBlank()) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PrimaryAction(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Pink,
            contentColor = Ink,
            disabledContainerColor = SurfaceSoft,
            disabledContentColor = TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryAction(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Outline),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(17.dp), tint = Blue)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}

@Composable
fun InfoNotice(text: String, modifier: Modifier = Modifier, accent: Color = Blue) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.07f))
            .border(0.6.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = accent)
        Text(
            text,
            Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(50.dp)
                .background(Pink.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(25.dp), tint = Pink)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(body, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        action?.invoke()
    }
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = Color.White.copy(alpha = 0.06f))
}

@Composable
fun TinyAction(text: String, onClick: () -> Unit, color: Color = Blue) {    Text(
        text,
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onDateChange(LocalDate.of(year, month + 1, day)) },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(13.dp),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, Outline)
        ) {
            Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp), tint = Blue)
            Spacer(Modifier.width(8.dp))
            Text(date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")), color = TextPrimary)
        }
    }
}

@Composable
fun AddInline(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = Blue)
        Text(text, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = Blue)
    }
}

fun money(value: BigDecimal?): String =
    value?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "—"

fun kwh(value: BigDecimal?): String =
    value?.setScale(2, RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: "—"

fun kwh(value: Double?): String =
    value?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() } ?: "—"

fun dateText(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("M 月 d 日"))

fun weekdayText(weekday: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(weekday - 1) { "周$weekday" }
