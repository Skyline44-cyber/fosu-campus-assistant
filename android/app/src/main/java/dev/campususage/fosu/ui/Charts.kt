package dev.campususage.fosu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.fosu.data.ChartPoint
import dev.campususage.fosu.ui.theme.*
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun UsageBarChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    accent: Color = Pink,
    height: Int = 122
) {
    if (points.isEmpty()) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "记录两次电表读数后，这里会出现用电趋势",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        return
    }

    val data = points.takeLast(14)
    val maximum = max(1.0, ceil(data.maxOf { it.value }))
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .semantics { contentDescription = "最近 ${data.size} 次抄表区间的用电量柱状图" }
    ) {
        val left = 2.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val top = 8.dp.toPx()
        val step = (size.width - left) / data.size
        val barWidth = (step * 0.52f).coerceAtLeast(4.dp.toPx())
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(152, 165, 188)
            textSize = 9.sp.toPx()
        }
        drawLine(Color.White.copy(alpha = 0.07f), Offset(left, bottom), Offset(size.width, bottom), 1f)
        data.forEachIndexed { index, point ->
            val ratio = (point.value / maximum).coerceIn(0.0, 1.0).toFloat()
            val barHeight = max(2.dp.toPx(), ratio * (bottom - top))
            val x = left + index * step + (step - barWidth) / 2f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(accent, Purple.copy(alpha = 0.55f))),
                topLeft = Offset(x, bottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            if (index == 0 || index == data.lastIndex || index % 4 == 0) {
                val label = point.date.format(DateTimeFormatter.ofPattern("M/d"))
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x - 2.dp.toPx(),
                    bottom + 14.dp.toPx(),
                    paint
                )
            }
        }
        drawContext.canvas.nativeCanvas.drawText(
            String.format(java.util.Locale.ROOT, "%.1f kWh", maximum),
            left,
            top + 2.dp.toPx(),
            paint
        )
    }
}
