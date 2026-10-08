package com.elabboubisolution.madconverter.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Checks the English (default), French and Arabic string resources stay complete and consistent. */
class StringResourcesTest {

    private data class Res(val value: String, val translatable: Boolean)

    private fun load(folder: String): Map<String, Res> {
        val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml")
            .map(::File).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as Element
            e.getAttribute("name") to Res(e.textContent, e.getAttribute("translatable") != "false")
        }
    }

    private val en = load("values")
    private val fr = load("values-fr")
    private val ar = load("values-ar")
    private val translatable = en.filterValues { it.translatable }.keys

    private fun placeholders(text: String) = Regex("""%\d+\$[sd]""").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `French and Arabic translate exactly the translatable English strings`() {
        assertEquals(translatable, fr.keys)
        assertEquals(translatable, ar.keys)
    }

    @Test
    fun `brand names are never translated`() {
        listOf("app_name", "attribution").forEach { key ->
            assertFalse(en.getValue(key).translatable)
            assertFalse(key in fr || key in ar)
        }
        assertEquals("Rates By Exchange Rate API", en.getValue("attribution").value)
    }

    @Test
    fun `placeholders match in every language`() {
        translatable.forEach { key ->
            val expected = placeholders(en.getValue(key).value)
            assertEquals("fr $key", expected, placeholders(fr.getValue(key).value))
            assertEquals("ar $key", expected, placeholders(ar.getValue(key).value))
        }
    }

    @Test
    fun `no string is empty`() {
        (en + fr.mapKeys { "fr:${it.key}" } + ar.mapKeys { "ar:${it.key}" }).forEach { (key, res) ->
            assertTrue(key, res.value.isNotBlank())
        }
    }

    @Test
    fun `Arabic strings are written in Arabic`() {
        val arabicLetter = Regex("[\\u0600-\\u06FF]")
        ar.forEach { (key, res) ->
            val words = res.value.replace(Regex("""%\d+\$[sd]"""), "").trim()
            if (words.any { it.isLetter() }) assertTrue("ar $key: ${res.value}", arabicLetter.containsMatchIn(words))
        }
    }

    @Test
    fun `strings use Western digits in every language`() {
        (en + fr.mapKeys { "fr:${it.key}" } + ar.mapKeys { "ar:${it.key}" }).forEach { (key, res) ->
            assertFalse("$key: ${res.value}", res.value.any { it.isDigit() && it !in '0'..'9' })
        }
    }

    @Test
    fun `key terminology in Arabic`() {
        assertEquals("المبلغ", ar.getValue("amount_label").value)
        assertEquals("من", ar.getValue("from_label").value)
        assertEquals("إلى", ar.getValue("to_label").value)
        assertEquals("تحويلات سريعة", ar.getValue("quick_conversions_title").value)
        assertEquals("التكلفة التقديرية", ar.getValue("real_cost_open").value)
        assertEquals("الإجمالي التقديري", ar.getValue("real_cost_total").value)
        assertEquals("إعادة المحاولة", ar.getValue("retry").value)
        assertEquals("السجل", ar.getValue("history_title").value)
        assertTrue(ar.getValue("last_updated").value.startsWith("آخر تحديث"))
    }

    @Test
    fun `accessibility descriptions do not repeat TalkBack gesture hints`() {
        listOf(en, fr, ar).flatMap { it.values }.forEach { res ->
            assertFalse(res.value, Regex("double tap|appuyez deux fois|انقر مرتين", RegexOption.IGNORE_CASE).containsMatchIn(res.value))
        }
    }

    @Test
    fun `apostrophes are escaped in source files`() {
        listOf("values", "values-fr", "values-ar").forEach { folder ->
            val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml")
                .map(::File).first { it.exists() }
            file.readLines().filter { "<string" in it }.forEach { line ->
                val text = line.substringAfter('>').substringBeforeLast("</string>")
                assertFalse("$folder: $line", Regex("(?<!\\\\)'").containsMatchIn(text))
            }
        }
    }
}
