package dev.lorem.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight,
)

internal val LoremTypography = Typography(
    displayLarge = style(48, 56, FontWeight.Bold).copy(fontFeatureSettings = "tnum"),
    displayMedium = style(40, 48, FontWeight.Bold).copy(fontFeatureSettings = "tnum"),
    displaySmall = style(32, 40, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
    headlineLarge = style(28, 36, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(20, 28, FontWeight.SemiBold),
    titleMedium = style(18, 26, FontWeight.SemiBold),
    titleSmall = style(16, 24, FontWeight.SemiBold),
    bodyLarge = style(16, 24), bodyMedium = style(16, 24), bodySmall = style(14, 20),
    labelLarge = style(16, 24, FontWeight.Medium),
    labelMedium = style(12, 18, FontWeight.Medium), labelSmall = style(12, 16, FontWeight.Medium),
)
