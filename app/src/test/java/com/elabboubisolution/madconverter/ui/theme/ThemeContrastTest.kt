package com.elabboubisolution.madconverter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 AA contrast of the brand color schemes: 4.5:1 for text, 3:1 for icons, outlines
 * and other essential non-text indicators. Pairs mirror how the components use the roles.
 */
class ThemeContrastTest {

    private fun channel(c: Float): Double =
        if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = contrast(fg, bg)
        assertTrue("$name: %.2f < %.1f".format(ratio, minimum), ratio >= minimum)
    }

    /** Text (4.5:1) on the backgrounds the app puts it on. */
    private fun textPairs(s: ColorScheme, x: ExtendedColors) = listOf(
        // Screen, fields, rate info, attribution link, hints, field errors
        Triple("onSurface/surface", s.onSurface, s.surface),
        Triple("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface),
        Triple("primary/surface", s.primary, s.surface),
        Triple("error/surface", s.error, s.surface),
        Triple("onBackground/background", s.onBackground, s.background),
        // Bottom sheets (surfaceContainerLow) and dialogs (surfaceContainerHigh)
        Triple("onSurface/sheet", s.onSurface, s.surfaceContainerLow),
        Triple("onSurfaceVariant/sheet", s.onSurfaceVariant, s.surfaceContainerLow),
        Triple("primary/sheet", s.primary, s.surfaceContainerLow),
        Triple("onSurface/dialog", s.onSurface, s.surfaceContainerHigh),
        Triple("primary/dialog", s.primary, s.surfaceContainerHigh),
        // Main result card
        Triple("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onPrimary/primary", s.onPrimary, s.primary),
        // Selected picker row, selected fee chip, swap button
        Triple("onSurface/secondaryContainer", s.onSurface, s.secondaryContainer),
        Triple("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        // Error card and its Retry button
        Triple("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer),
        Triple("onError/error", s.onError, s.error),
        // Stale banner and the History "stale rate" note
        Triple("onWarningContainer/warningContainer", x.onWarningContainer, x.warningContainer),
        Triple("onWarning/warning", x.onWarning, x.warning),
        Triple("warning/sheet", x.warning, s.surfaceContainerLow),
        Triple("warning/surface", x.warning, s.surface),
        Triple("inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface), // snackbar
    )

    /** Icons, outlines and indicators (3:1). */
    private fun uiPairs(s: ColorScheme) = listOf(
        Triple("outline/surface", s.outline, s.surface),
        Triple("primary icon/secondaryContainer", s.primary, s.secondaryContainer), // selected check
        Triple("onSurfaceVariant icon/sheet", s.onSurfaceVariant, s.surfaceContainerLow), // star, delete
        Triple("primary indicator/surface", s.primary, s.surface), // focused field, progress
    )

    @Test
    fun `light scheme meets WCAG AA`() {
        textPairs(LightColorScheme, LightExtendedColors).forEach { (n, fg, bg) -> assertContrast("light $n", fg, bg, 4.5) }
        uiPairs(LightColorScheme).forEach { (n, fg, bg) -> assertContrast("light $n", fg, bg, 3.0) }
    }

    @Test
    fun `dark scheme meets WCAG AA`() {
        textPairs(DarkColorScheme, DarkExtendedColors).forEach { (n, fg, bg) -> assertContrast("dark $n", fg, bg, 4.5) }
        uiPairs(DarkColorScheme).forEach { (n, fg, bg) -> assertContrast("dark $n", fg, bg, 3.0) }
    }

    @Test
    fun `brand references are used`() {
        assertEquals(Color(0xFFF5F7F4), LightColorScheme.background)
        assertEquals(Color(0xFF123B35), LightColorScheme.onPrimaryContainer)
        assertEquals(Color(0xFF123B35), DarkColorScheme.primaryContainer)
    }

    @Test
    fun `warning, error and stale colors are distinct`() {
        listOf(LightColorScheme to LightExtendedColors, DarkColorScheme to DarkExtendedColors).forEach { (s, x) ->
            assertNotEquals(s.errorContainer, x.warningContainer)
            assertNotEquals(s.primaryContainer, x.warningContainer)
            assertNotEquals(s.error, x.warning)
        }
    }

    @Test
    fun `no template purple remains`() {
        val template = setOf(
            Color(0xFF6650A4), Color(0xFFD0BCFF), Color(0xFF625B71),
            Color(0xFFCCC2DC), Color(0xFF7D5260), Color(0xFFEFB8C8),
        )
        listOf(LightColorScheme, DarkColorScheme).forEach { s ->
            listOf(s.primary, s.secondary, s.tertiary, s.primaryContainer, s.surface, s.surfaceTint).forEach {
                assertTrue("template color $it", it !in template)
            }
        }
    }
}
