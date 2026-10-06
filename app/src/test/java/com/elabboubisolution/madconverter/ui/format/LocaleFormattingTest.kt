package com.elabboubisolution.madconverter.ui.format

import com.elabboubisolution.madconverter.domain.AmountInput
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.CurrencyConverter
import com.elabboubisolution.madconverter.domain.CurrencySearch
import com.elabboubisolution.madconverter.domain.FeeInput
import com.elabboubisolution.madconverter.domain.RealCost
import com.elabboubisolution.madconverter.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.text.Bidi
import java.util.Locale
import java.util.TimeZone

/** Presentation-only tests: business logic stays locale-independent. */
class LocaleFormattingTest {

    private val en = Locale.US
    private val fr = Locale.FRANCE
    private val ar = Locale.forLanguageTag("ar")
    private val arMa = Locale.forLanguageTag("ar-MA")
    private val amount = BigDecimal("2794.61")

    // --- Decimals ---

    @Test
    fun `decimal and grouping separators follow the locale`() {
        assertEquals("2,794.61", formatDecimal(amount, en, 2))
        assertEquals("2 794,61", formatDecimal(amount, fr, 2))
        // Arabic: Arabic-Indic digits with Arabic separators; Morocco: Latin digits.
        assertEquals("٢٬٧٩٤٫٦١", formatDecimal(amount, ar, 2))
        assertEquals("2.794,61", formatDecimal(amount, arMa, 2))
    }

    @Test
    fun `zero decimal currencies stay without decimals in every locale`() {
        val jpy = BigDecimal("15840")
        val digits = Currency.JPY.fractionDigits
        assertEquals("15,840", formatDecimal(jpy, en, digits))
        assertEquals("15 840", formatDecimal(jpy, fr, digits))
        assertEquals("١٥٬٨٤٠", formatDecimal(jpy, ar, digits))
        assertEquals("15.840", formatDecimal(jpy, arMa, digits)) // "." groups thousands in Morocco
    }

    // --- Percentages ---

    @Test
    fun `percentages follow the locale`() {
        assertEquals("2.75%", formatPercent(BigDecimal("2.75"), en))
        assertEquals("3%", formatPercent(BigDecimal("3"), en))
        assertEquals("2,75 %", formatPercent(BigDecimal("2.75"), fr))
        assertTrue(formatPercent(BigDecimal("2.75"), ar).startsWith("٢٫٧٥٪"))
        assertEquals("%", percentSign(en))
        assertEquals("٪", percentSign(ar))
    }

    @Test
    fun `percentage presentation never changes the stored value`() {
        // The fee field and storage keep ASCII; only the label is localized.
        assertEquals(FeeInput.Valid(BigDecimal("2.75")), RealCost.parseFeePercent(normalizeNumericInput("٢٫٧٥")))
    }

    // --- Dates ---

    @Test
    fun `today and yesterday follow calendar days in the device time zone`() {
        val casablanca = TimeZone.getTimeZone("Africa/Casablanca")
        val now = 1_791_291_600_000L // 2026-10-06 13:00 UTC = 14:00 in Casablanca (UTC+1)
        val hour = 3_600_000L

        assertEquals(RelativeDay.TODAY, relativeDay(now, now, casablanca))
        assertEquals(RelativeDay.TODAY, relativeDay(now - 13 * hour, now, casablanca)) // 01:00 local
        assertEquals(RelativeDay.YESTERDAY, relativeDay(now - 15 * hour, now, casablanca)) // 23:00 the day before
        assertEquals(RelativeDay.YESTERDAY, relativeDay(now - 37 * hour, now, casablanca))
        assertEquals(RelativeDay.EARLIER_OR_LATER, relativeDay(now - 39 * hour, now, casablanca))
        assertEquals(RelativeDay.EARLIER_OR_LATER, relativeDay(now + 24 * hour, now, casablanca))
    }

    @Test
    fun `yesterday works across a year boundary`() {
        val utc = TimeZone.getTimeZone("UTC")
        val newYear = 1_798_761_600_000L // 2027-01-01 00:00 UTC

        assertEquals(RelativeDay.YESTERDAY, relativeDay(newYear - 1, newYear + 1, utc))
    }

    // --- Bidi: currency codes and amounts inside Arabic (RTL) text ---

    private fun levels(text: String): IntArray {
        val bidi = Bidi(text, Bidi.DIRECTION_RIGHT_TO_LEFT)
        return IntArray(text.length) { bidi.getLevelAt(it) }
    }

    @Test
    fun `without isolation a rate line is split and reordered inside Arabic text`() {
        val line = ConversionText.rate(Currency.MAD, Currency.USD, BigDecimal("0.100812"), en)
        val text = "سعر الصرف: $line"
        val levels = levels(text)
        val start = text.indexOf(line)
        // The space after "1" falls back to the RTL level, so "1" is displayed apart:
        // visually "MAD = 0.1008 USD 1".
        val inner = (start until start + line.length).map { levels[it] }
        assertTrue("expected mixed levels but was $inner", inner.toSet().size > 1)
        assertEquals(1, levels[start + 1] % 2)
    }

    @Test
    fun `isolated rate keeps left to right reading order inside Arabic text`() {
        val line = ConversionText.rate(Currency.MAD, Currency.USD, BigDecimal("0.100812"), en)
        val text = "سعر الصرف: " + ltr(line)
        val levels = levels(text)
        val start = text.indexOf(line)

        val inner = (start until start + line.length).map { levels[it] }.toSet()
        assertEquals("one LTR level for the whole expression", 1, inner.size)
        assertEquals("even level = left to right", 0, inner.single() % 2)
    }

    @Test
    fun `source to target stays source first under RTL`() {
        val label = "${ConversionText.amount(BigDecimal("1000"), Currency.MAD, en)} → " +
            ConversionText.money(BigDecimal("100.28"), Currency.USD, en)
        val text = "تحويل " + ltr(label)
        val levels = levels(text)
        val start = text.indexOf(label)

        // Entire label is one LTR run, so visually "1,000 MAD → 100.28 USD" (source left of target).
        assertEquals(1, (start until start + label.length).map { levels[it] % 2 }.toSet().size)
        assertEquals(0, levels[start] % 2)
        assertTrue(label.indexOf("MAD") < label.indexOf("→") && label.indexOf("→") < label.indexOf("USD"))
    }

    @Test
    fun `isolation is display only, copied text stays clean`() {
        val shared = ConversionText.shareText(
            Conversion(Currency.MAD, Currency.USD, BigDecimal("1000"), BigDecimal("100.28"), BigDecimal("0.100284")),
            ar,
        )
        assertTrue(shared.none { it == '⁦' || it == '⁩' })
        assertEquals("⁦x⁩", ltr("x"))
    }

    // --- Typed digits ---

    @Test
    fun `digits typed on an Arabic keyboard become ASCII before parsing`() {
        assertEquals("1250.50", normalizeNumericInput("١٢٥٠٫٥٠"))
        assertEquals("1250,50", normalizeNumericInput("١٢٥٠،٥٠"))
        assertEquals("1234", normalizeNumericInput("۱۲۳۴"))
        assertEquals("12.5", normalizeNumericInput("12.5"))
        assertEquals(AmountInput.Valid(BigDecimal("1250.50")), CurrencyConverter.parseAmount(normalizeNumericInput("١٢٥٠٫٥٠")))
    }

    @Test
    fun `Arabic grouping separator is still rejected like other grouping`() {
        assertEquals(AmountInput.Invalid, CurrencyConverter.parseAmount(normalizeNumericInput("١٬٠٠٠")))
    }

    // --- Currency names and search in Arabic ---

    @Test
    fun `currency names and search work in Arabic`() {
        assertEquals("درهم مغربي", Currency.MAD.displayName(ar))
        val dirhams = CurrencySearch.search("درهم", ar, favorites = emptySet()).others
        assertEquals(listOf(Currency.MAD, Currency.AED), dirhams)
        assertTrue(CurrencySearch.search("دولار", ar, emptySet()).others.containsAll(listOf(Currency.USD, Currency.CAD)))
        assertEquals(Currency.EUR, CurrencySearch.search("EUR", ar, emptySet()).others.first())
    }
}
