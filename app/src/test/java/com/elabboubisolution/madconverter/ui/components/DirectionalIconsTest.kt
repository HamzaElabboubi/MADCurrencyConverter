package com.elabboubisolution.madconverter.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Directional icons must flip in RTL: the Quick Conversions chevron points to the end of the
 * row, which is the left side in Arabic.
 */
class DirectionalIconsTest {

    private fun file(path: String): File =
        listOf("src/main/res/$path", "app/src/main/res/$path").map(::File).first { it.exists() }

    private fun autoMirrored(drawable: String): String =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file("drawable/$drawable.xml")).documentElement
            .getAttribute("android:autoMirrored")

    @Test
    fun `quick conversions chevron is mirrored in RTL`() {
        assertEquals("true", autoMirrored("ic_chevron_right"))
    }
}
