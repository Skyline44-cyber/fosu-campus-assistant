package dev.campususage.fosu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Background = Color(0xFF0B0E18)
val Surface = Color(0xFF151B29)
val SurfaceElevated = Color(0xFF1C2435)
val SurfaceSoft = Color(0xFF222C40)
val TextPrimary = Color(0xFFE9ECFB)
val TextMuted = Color(0xFF98A5BC)
val Pink = Color(0xFFF2A8CB)
val Blue = Color(0xFFBFE6FF)
val Purple = Color(0xFFB8A7F4)
val Mint = Color(0xFF8FE0C5)
val Danger = Color(0xFFFF9AA2)
val Outline = Color(0xFF344057)
val Ink = Color(0xFF141A2B)

val ChaoxingBlue = Color(0xFF4A90E2)

/** The saturated palette used by Chaoxing's course blocks. */
val CourseColors = listOf(
    Color(0xFF4A90E2),
    Color(0xFF5B8FF9),
    Color(0xFF61C0BF),
    Color(0xFFF6BD16),
    Color(0xFF7262FD),
    Color(0xFF78D3F8),
    Color(0xFFE8684A),
    Color(0xFF9A60B4)
)

private val AppColors = darkColorScheme(
    primary = Pink,
    onPrimary = Ink,
    secondary = Blue,
    onSecondary = Ink,
    tertiary = Purple,
    onTertiary = Ink,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextMuted,
    outline = Outline,
    error = Danger,
    onError = Ink
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.8.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun FosuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
