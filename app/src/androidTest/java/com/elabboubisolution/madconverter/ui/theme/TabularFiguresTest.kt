package com.elabboubisolution.madconverter.ui.theme

import android.util.Log
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks on the device's real system font that [tabularFigures] gives every digit the same
 * width, through Compose's own text measurement (the path the UI uses).
 */
@RunWith(AndroidJUnit4::class)
class TabularFiguresTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(context),
        defaultDensity = Density(context),
        defaultLayoutDirection = LayoutDirection.Ltr,
    )

    private fun digitWidths(style: TextStyle): List<Int> =
        ('0'..'9').map { d -> measurer.measure(d.toString().repeat(10), style).size.width }

    @Test
    fun tabularFiguresGiveEveryDigitTheSameWidth() {
        listOf(Typography.displaySmall, Typography.headlineSmall, Typography.titleMedium, Typography.bodyLarge)
            .forEach { base ->
                val proportional = digitWidths(base)
                val tabular = digitWidths(base.tabularFigures())
                Log.i("TabularFiguresTest", "${base.fontSize}: default=$proportional tnum=$tabular")
                assertEquals("tnum widths at ${base.fontSize}: $tabular", 1, tabular.toSet().size)
            }
    }
}
