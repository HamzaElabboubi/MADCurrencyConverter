package com.elabboubisolution.madconverter.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTest {

    private val t = Typography

    @Test
    fun `the conversion result is the largest text and the screen title stays below it`() {
        val result = t.displaySmall.fontSize.value
        listOf(t.headlineSmall, t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium)
            .forEach { assertTrue("${it.fontSize} < $result", it.fontSize.value < result) }
        assertTrue(t.titleLarge.fontSize < t.headlineSmall.fontSize) // title below the typed amount
        assertEquals(FontWeight.Medium, t.displaySmall.fontWeight)
    }

    @Test
    fun `hierarchy decreases from amount to supporting text`() {
        val sizes = listOf(t.displaySmall, t.headlineSmall, t.titleLarge, t.titleMedium, t.bodyMedium, t.bodySmall)
            .map { it.fontSize.value }
        assertEquals(sizes.sortedDescending(), sizes)
    }

    @Test
    fun `no text style is below 11sp and body text is at least 12sp`() {
        listOf(t.bodyLarge, t.bodyMedium, t.bodySmall).forEach { assertTrue(it.fontSize.value >= 12f) }
        listOf(t.labelLarge, t.labelMedium, t.labelSmall).forEach { assertTrue(it.fontSize.value >= 11f) }
    }

    @Test
    fun `system font only, so Arabic keeps platform shaping`() {
        listOf(t.displaySmall, t.headlineSmall, t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall)
            .forEach { assertEquals(FontFamily.Default, it.fontFamily) }
    }

    @Test
    fun `tabular figures only add the tnum feature`() {
        val base = t.displaySmall
        assertNull(base.fontFeatureSettings)
        val tabular = base.tabularFigures()
        assertEquals("tnum", tabular.fontFeatureSettings)
        assertEquals(base.copy(fontFeatureSettings = "tnum"), tabular)
    }
}
