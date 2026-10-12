package com.elabboubisolution.madconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot

/**
 * The launcher icon (Phase 12.9), from the approved 3D logo: adaptive layers, safe zone, themed
 * silhouette, legacy bitmaps and the Play listing icon. Assets come from
 * design/launcher-icon/generate_launcher_icon.py.
 */
class LauncherIconTest {

    private val densities = mapOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0, "xxxhdpi" to 4.0)

    private fun file(path: String): File =
        listOf("src/main/$path", "app/src/main/$path").map(::File).first { it.exists() }

    private fun designFile(path: String): File =
        listOf("../design/$path", "design/$path").map(::File).first { it.exists() }

    private fun elements(path: String, tag: String): List<Element> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(path)).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun png(path: String): BufferedImage = ImageIO.read(file(path))

    private fun BufferedImage.alpha(x: Int, y: Int) = getRGB(x, y) ushr 24
    private fun BufferedImage.rgb(x: Int, y: Int) = getRGB(x, y).let { Triple(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) }

    @Test
    fun `manifest uses the launcher mipmaps`() {
        val app = elements("AndroidManifest.xml", "application").single()
        assertEquals("@mipmap/ic_launcher", app.getAttribute("android:icon"))
        assertEquals("@mipmap/ic_launcher_round", app.getAttribute("android:roundIcon"))
    }

    @Test
    fun `adaptive icons declare background, foreground and monochrome layers`() {
        listOf("ic_launcher", "ic_launcher_round").forEach { name ->
            val path = "res/mipmap-anydpi-v26/$name.xml"
            fun layer(tag: String) = elements(path, tag).single().getAttribute("android:drawable")
            assertEquals(name, "@mipmap/ic_launcher_background", layer("background"))
            assertEquals(name, "@mipmap/ic_launcher_foreground", layer("foreground"))
            assertEquals(name, "@drawable/ic_launcher_monochrome", layer("monochrome"))
        }
    }

    @Test
    fun `template icon layers are gone`() {
        listOf("ic_launcher_background", "ic_launcher_foreground").forEach { name ->
            assertFalse(name, listOf("src/main/res/drawable/$name.xml", "app/src/main/res/drawable/$name.xml").any { File(it).exists() })
        }
    }

    @Test
    fun `layers exist at every density as 108dp bitmaps`() {
        densities.forEach { (density, factor) ->
            val size = Math.round(108 * factor).toInt()
            listOf("ic_launcher_background", "ic_launcher_foreground").forEach { name ->
                val image = png("res/mipmap-$density/$name.png")
                assertEquals("$density/$name", size to size, image.width to image.height)
            }
        }
    }

    @Test
    fun `background is an opaque deep green`() {
        val image = png("res/mipmap-xxxhdpi/ic_launcher_background.png")
        var r = 0L
        var g = 0L
        var b = 0L
        for (y in 0 until image.height) for (x in 0 until image.width) {
            assertEquals("opaque at ($x, $y)", 255, image.alpha(x, y))
            val (pr, pg, pb) = image.rgb(x, y)
            r += pr; g += pg; b += pb
        }
        val n = image.width * image.height
        val mean = Triple(r / n, g / n, b / n)
        assertTrue("green dominates: $mean", mean.second > mean.first + 25 && mean.second > mean.third)
        assertTrue("dark: $mean", mean.second < 110)
    }

    @Test
    fun `foreground holds the gold and turquoise arrows inside the 66dp safe zone`() {
        val image = png("res/mipmap-xxxhdpi/ic_launcher_foreground.png") // 4 px per dp
        var gold = 0
        var turquoise = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val a = image.alpha(x, y)
            val radiusDp = hypot(x + 0.5 - 216, y + 0.5 - 216) / 4
            if (radiusDp > 33.0) assertTrue("visible at ${radiusDp}dp", a <= 8)
            if (a == 255) {
                val (r, g, b) = image.rgb(x, y)
                if (r > 180 && g > 120 && b < 120) gold++
                if (g > 150 && b > 120 && r < 120) turquoise++
            }
        }
        assertTrue("gold arrow: $gold px", gold > 2_000)
        assertTrue("turquoise arrow: $turquoise px", turquoise > 2_000)
        assertEquals("transparent corner", 0, image.alpha(0, 0))
    }

    @Test
    fun `monochrome is one solid silhouette inside the safe zone`() {
        val path = elements("res/drawable/ic_launcher_monochrome.xml", "path").single()
        assertTrue("opaque fill", path.getAttribute("android:fillColor").uppercase().startsWith("#FF"))
        val points = pathPoints(path.getAttribute("android:pathData"))
        points.forEach { (x, y) ->
            val r = hypot(x - 54.0, y - 54.0)
            assertTrue("point ($x, $y) is ${r}dp from the center", r <= 33.0)
        }
        assertEquals("two arrows", 2, Regex("Z").findAll(path.getAttribute("android:pathData")).count())
    }

    @Test
    fun `legacy bitmaps exist at every density in the right size`() {
        densities.forEach { (density, factor) ->
            val size = Math.round(48 * factor).toInt()
            listOf("ic_launcher", "ic_launcher_round").forEach { name ->
                val webp = file("res/mipmap-$density/$name.webp")
                assertEquals("$density/$name", size to size, webpSize(webp.readBytes()))
            }
        }
    }

    @Test
    fun `play listing icon is an opaque 512px square outside the packaged resources`() {
        val image = ImageIO.read(designFile("play-store/currency_converter_play_icon_512.png"))
        assertEquals(512 to 512, image.width to image.height)
        listOf(0 to 0, 511 to 0, 0 to 511, 511 to 511, 256 to 256).forEach { (x, y) -> assertEquals(255, image.alpha(x, y)) }
    }

    /** Endpoints of absolute M/L/A commands (arcs also bounded by their radius). */
    private fun pathPoints(data: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        Regex("""([MLA])([^MLAZ]*)""").findAll(data).forEach { m ->
            val n = m.groupValues[2].trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { it.toDouble() }
            when (m.groupValues[1]) {
                "M", "L" -> points += n[0] to n[1]
                "A" -> {
                    assertTrue("arc radius ${n[0]}dp", n[0] <= 33.0)
                    points += n[5] to n[6]
                }
            }
        }
        assertTrue("path has points", points.isNotEmpty())
        return points
    }

    /** Width and height from a lossless (VP8L) WebP header, as written by the generator. */
    private fun webpSize(bytes: ByteArray): Pair<Int, Int> {
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WEBP", String(bytes, 8, 4))
        assertEquals("lossless WebP", "VP8L", String(bytes, 12, 4))
        assertEquals(0x2F, bytes[20].toInt() and 0xFF)
        val bits = ByteBuffer.wrap(bytes, 21, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
    }
}
