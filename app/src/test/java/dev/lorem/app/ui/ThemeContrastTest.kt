package dev.lorem.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.ui.theme.LoremDarkColors
import dev.lorem.app.ui.theme.LoremLightColors
import org.junit.Assert.*
import org.junit.Test

class ThemeContrastTest {
    @Test fun `semantic text pairs meet normal text contrast and essential borders remain visible`() {
        for (scheme in listOf(LoremLightColors, LoremDarkColors)) with(scheme) {
            val pairs = listOf(
                onBackground to background, onSurface to surface, onSurfaceVariant to surface,
                onSurfaceVariant to surfaceVariant, onPrimary to primary, onPrimaryContainer to primaryContainer,
                onSecondary to secondary, onSecondaryContainer to secondaryContainer,
                onTertiary to tertiary, onTertiaryContainer to tertiaryContainer,
                onError to error, onErrorContainer to errorContainer,
                error to background, primary to background,
            )
            for ((foreground, background) in pairs) {
                assertTrue("Text contrast ${contrast(foreground, background)} for $foreground on $background", contrast(foreground, background) >= 4.5)
            }
            for (background in listOf(surface, background)) {
                assertTrue("Selected indicator contrast", contrast(primary, background) >= 3)
                assertTrue("Control border contrast", contrast(outline, background) >= 3)
            }
        }
    }
    @Test fun `system mode follows device while explicit modes override it`() {
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, ThemePreference.SYSTEM.isDark(systemDark))
            assertFalse(ThemePreference.LIGHT.isDark(systemDark))
            assertTrue(ThemePreference.DARK.isDark(systemDark))
        }
    }
    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}
