package com.elabboubisolution.madconverter.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The native window theme (shown before Compose draws, and as the Android 12+ splash background)
 * must match the Compose surfaces in light and dark mode, or startup flashes the wrong color.
 */
class WindowThemeTest {

    private fun file(path: String): File =
        listOf("src/main/res/$path", "app/src/main/res/$path").map(::File).first { it.exists() }

    private fun elements(path: String, tag: String): List<Element> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(path)).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun windowColor(folder: String): Color {
        val hex = elements("$folder/colors.xml", "color").single { it.getAttribute("name") == "window_background" }
            .textContent.trim().removePrefix("#")
        return Color(hex.toLong(16).toInt())
    }

    private fun theme(folder: String): Element =
        elements("$folder/themes.xml", "style").single { it.getAttribute("name") == "Theme.MADCurrencyConverter" }

    private fun Element.item(name: String): String {
        val items = getElementsByTagName("item")
        return (0 until items.length).map { items.item(it) as Element }
            .single { it.getAttribute("name") == name }.textContent.trim()
    }

    @Test
    fun `window background matches the Compose surface in light and dark mode`() {
        assertEquals(LightColorScheme.surface.toArgb(), windowColor("values").toArgb())
        assertEquals(DarkColorScheme.surface.toArgb(), windowColor("values-night").toArgb())
    }

    @Test
    fun `light window uses a light parent and dark system bar icons`() {
        val light = theme("values")
        assertEquals("android:Theme.Material.Light.NoActionBar", light.getAttribute("parent"))
        assertEquals("@color/window_background", light.item("android:windowBackground"))
        assertEquals("true", light.item("android:windowLightStatusBar"))
        assertEquals("true", light.item("android:windowLightNavigationBar"))
    }

    @Test
    fun `dark window uses a dark parent and light system bar icons`() {
        val dark = theme("values-night")
        assertEquals("android:Theme.Material.NoActionBar", dark.getAttribute("parent"))
        assertEquals("@color/window_background", dark.item("android:windowBackground"))
        assertEquals("false", dark.item("android:windowLightStatusBar"))
        assertEquals("false", dark.item("android:windowLightNavigationBar"))
    }
}
