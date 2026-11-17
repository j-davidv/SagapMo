package com.sagapmo.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFFA3082C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFBECEF), onPrimaryContainer = Color(0xFFA3082C),
    background = Color(0xFFF3F4EF), onBackground = Color(0xFF202725),
    surface = Color.White, onSurface = Color(0xFF202725), onSurfaceVariant = Color(0xFF59635E),
    surfaceVariant = Color(0xFFF7F8F4), surfaceContainer = Color(0xFFF7F8F4),
    surfaceContainerLow = Color(0xFFF7F8F4), surfaceContainerHigh = Color.White,
    outline = Color(0xFF9DA99F), outlineVariant = Color(0xFFDCE1D9),
    secondary = Color(0xFF202725), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBECEF), onSecondaryContainer = Color(0xFFA3082C),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB1C1), onPrimary = Color(0xFF390816),
    primaryContainer = Color(0xFF45212A), onPrimaryContainer = Color(0xFFFFB1C1),
    background = Color(0xFF111813), onBackground = Color(0xFFE5ECE5),
    surface = Color(0xFF1C241F), onSurface = Color(0xFFE5ECE5), onSurfaceVariant = Color(0xFFB2BEB4),
    surfaceVariant = Color(0xFF252F28), surfaceContainer = Color(0xFF252F28),
    surfaceContainerLow = Color(0xFF252F28), surfaceContainerHigh = Color(0xFF1C241F),
    outline = Color(0xFFB2BEB4), outlineVariant = Color(0xFF445147),
    secondary = Color(0xFFE5ECE5), onSecondary = Color(0xFF1C241F),
    secondaryContainer = Color(0xFF45212A), onSecondaryContainer = Color(0xFFFFB1C1),
)
data class RatingColors(val good: Color, val goodBg: Color, val fair: Color, val fairBg: Color, val poor: Color, val poorBg: Color)
val LocalRatingColors = staticCompositionLocalOf { RatingColors(Color(0xFF096644), Color(0xFFE6F4EB), Color(0xFF805000), Color(0xFFFFF1D6), Color(0xFFA12626), Color(0xFFFCEBEA)) }

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
private val AppTypography = Typography(
    headlineLarge = type(36, 44, FontWeight.SemiBold),
    titleLarge = type(24, 29, FontWeight.Bold),
    titleMedium = type(22, 28, FontWeight.SemiBold),
    titleSmall = type(20, 26, FontWeight.SemiBold),
    bodyLarge = type(15, 23, FontWeight.SemiBold),
    bodyMedium = type(14, 24), bodySmall = type(12, 18),
    labelLarge = type(15, 22, FontWeight.SemiBold),
    labelMedium = type(13, 20, FontWeight.SemiBold),
    labelSmall = type(12, 16, FontWeight.SemiBold),
)
@Composable fun SagapMoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ratings = if (darkTheme) RatingColors(Color(0xFF9BD9B5), Color(0xFF193D2B), Color(0xFFEFC778), Color(0xFF42351A), Color(0xFFFFB3AD), Color(0xFF482826)) else LocalRatingColors.current
    CompositionLocalProvider(LocalRatingColors provides ratings) {
        MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = AppTypography, content = content)
    }
}
