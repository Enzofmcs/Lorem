package dev.lorem.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val LoremLightColors = lightColorScheme(
    primary = Color(0xFF006B55), onPrimary = Color.White,
    primaryContainer = Color(0xFFD1F5E7), onPrimaryContainer = Color(0xFF004333),
    secondary = Color(0xFF526177), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBF2), onSecondaryContainer = Color(0xFF142238),
    tertiary = Color(0xFF805500), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEABD), onTertiaryContainer = Color(0xFF573900),
    error = Color(0xFFB4233A), onError = Color.White,
    errorContainer = Color(0xFFFFE0E5), onErrorContainer = Color(0xFF7E1025),
    background = Color(0xFFF5F7FA), onBackground = Color(0xFF142238),
    surface = Color.White, onSurface = Color(0xFF142238),
    surfaceVariant = Color(0xFFE6EBF2), onSurfaceVariant = Color(0xFF526177),
    outline = Color(0xFF6C7A8E), outlineVariant = Color(0xFFE6EBF2),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE6EBF2),
    surfaceContainerHighest = Color(0xFFE6EBF2), surfaceBright = Color.White,
    surfaceDim = Color(0xFFE6EBF2), inverseSurface = Color(0xFF151F30),
    inverseOnSurface = Color(0xFFF1F5FA), inversePrimary = Color(0xFF6EE7BE),
    surfaceTint = Color(0xFF006B55),
)

internal val LoremDarkColors = darkColorScheme(
    primary = Color(0xFF6EE7BE), onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF164C3F), onPrimaryContainer = Color(0xFFB6F5DD),
    secondary = Color(0xFFA8B6CC), onSecondary = Color(0xFF0B1220),
    secondaryContainer = Color(0xFF26354B), onSecondaryContainer = Color(0xFFF1F5FA),
    tertiary = Color(0xFFF5CD79), onTertiary = Color(0xFF0B1220),
    tertiaryContainer = Color(0xFF513B10), onTertiaryContainer = Color(0xFFFFE3A9),
    error = Color(0xFFFF8FA3), onError = Color(0xFF0B1220),
    errorContainer = Color(0xFF601D30), onErrorContainer = Color(0xFFFFD9E1),
    background = Color(0xFF0B1220), onBackground = Color(0xFFF1F5FA),
    surface = Color(0xFF151F30), onSurface = Color(0xFFF1F5FA),
    surfaceVariant = Color(0xFF26354B), onSurfaceVariant = Color(0xFFA8B6CC),
    outline = Color(0xFF889AB3), outlineVariant = Color(0xFF26354B),
    surfaceContainerLowest = Color(0xFF0B1220), surfaceContainerLow = Color(0xFF151F30),
    surfaceContainer = Color(0xFF151F30), surfaceContainerHigh = Color(0xFF26354B),
    surfaceContainerHighest = Color(0xFF26354B), surfaceBright = Color(0xFF26354B),
    surfaceDim = Color(0xFF0B1220), inverseSurface = Color(0xFFF5F7FA),
    inverseOnSurface = Color(0xFF142238), inversePrimary = Color(0xFF006B55),
    surfaceTint = Color(0xFF6EE7BE),
)
