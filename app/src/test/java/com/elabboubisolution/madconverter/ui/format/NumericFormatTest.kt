package com.elabboubisolution.madconverter.ui.format

import com.elabboubisolution.madconverter.domain.AmountInput
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.ConversionResult
import com.elabboubisolution.madconverter.domain.CurrencyConverter
import com.elabboubisolution.madconverter.domain.FeeInput
import com.elabboubisolution.madconverter.domain.RealCost
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.testing.SampleRates
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.math.BigDecimal
import java.text.Bidi
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Regression tests for the app's single numeric format: Western digits 0-9, "." decimal and
 * "," thousands in English, French and Arabic alike. Each case runs under every app language
 * (as the JVM default locale) to prove the output never depends on it.
 */
class NumericFormatTest {

    private val languages = listOf("en-US", "fr-FR", "ar", "ar-EG", "ar-MA").map(Locale::forLanguageTag)
    private val originalDefault = Locale.getDefault()

    @After
    fun restoreDefaultLocale() = Locale.setDefault(originalDefault)

    /** Asserts [block] gives [expected] whichever language is active. */
    private fun assertInEveryLanguage(expected: String, block: () -> String) {
        languages.forEach { language ->
            Locale.setDefault(language)
            assertEquals("in ${language.toLanguageTag()}", expected, block())
        }
    }

    private fun assertWesternDigitsOnly(text: String) {
        assertTrue("non-ASCII digit in \"$text\"", text.none { it.isDigit() && it !in '0'..'9' })
    }

    private val madToUsd = Conversion(
        from = Currency.MAD,
        to = Currency.USD,
        amount = BigDecimal("1250.5"),
        convertedAmount = BigDecimal("125.43"),
        rate = BigDecimal("0.100304"),
    )

    // --- Main conversion, rate line, Quick Conversions, History ---

    @Test
    fun `main conversion is identical in every language`() {
        assertInEveryLanguage("1,250.5 MAD") { ConversionText.amount(madToUsd.amount, Currency.MAD) }
        assertInEveryLanguage("≈ 125.43 USD") { ConversionText.approximate(madToUsd.convertedAmount, Currency.USD) }
    }

    @Test
    fun `exchange rate line uses Western digits including the leading 1`() {
        assertInEveryLanguage("1 MAD = 0.1003 USD") { ConversionText.rate(Currency.MAD, Currency.USD, madToUsd.rate) }
    }

    @Test
    fun `quick conversion and history amounts are identical in every language`() {
        assertInEveryLanguage("100.28") { formatDecimal(BigDecimal("100.28"), minDigits = Currency.USD.fractionDigits) }
        assertInEveryLanguage("1,000 MAD → 100.28 USD") {
            "${ConversionText.amount(BigDecimal("1000"), Currency.MAD)} → " +
                ConversionText.money(BigDecimal("100.28"), Currency.USD)
        }
    }

    @Test
    fun `separators are dot for decimals and comma for thousands`() {
        assertInEveryLanguage("1,234,567.89") { formatDecimal(BigDecimal("1234567.891"), minDigits = 2) }
        assertInEveryLanguage("19,822 JPY") { ConversionText.money(BigDecimal("19822"), Currency.JPY) }
        assertInEveryLanguage("0.00 USD") { ConversionText.money(BigDecimal.ZERO, Currency.USD) }
    }

    @Test
    fun `rounding and typed decimals are unchanged`() {
        assertInEveryLanguage("0.1009") { formatDecimal(BigDecimal("0.10085"), minDigits = 4) } // HALF_UP
        assertInEveryLanguage("1,250.5 MAD") { ConversionText.amount(BigDecimal("1250.5"), Currency.MAD) }
    }

    // --- Typed decimals: shown exactly as entered ---

    /** Typed input -> expected source text, e.g. "1250.50" -> "1,250.50". */
    private val typedAmounts = listOf(
        "1250" to "1,250",
        "1250.5" to "1,250.5",
        "1250.50" to "1,250.50",
        "1250.00" to "1,250.00",
        "1250,50" to "1,250.50", // comma input, normalized display
        "0.10" to "0.10",
    )

    private fun typed(input: String): BigDecimal =
        (CurrencyConverter.parseAmount(input) as AmountInput.Valid).value

    private fun convert(input: String, to: Currency = Currency.USD): Conversion =
        (CurrencyConverter.convert(typed(input), Currency.MAD, to, SampleRates.snapshot) as ConversionResult.Success)
            .conversion

    @Test
    fun `source amount keeps exactly the entered decimals in every language`() {
        typedAmounts.forEach { (input, shown) ->
            assertInEveryLanguage("$shown MAD") { ConversionText.amount(typed(input), Currency.MAD) }
        }
    }

    @Test
    fun `entered trailing zeros survive parsing and conversion without changing the value`() {
        val conversion = convert("1250.50")
        assertEquals("1250.50", conversion.amount.toPlainString())
        // Same numeric value and same result as without the zero.
        assertEquals(0, conversion.amount.compareTo(BigDecimal("1250.5")))
        assertEquals(convert("1250.5").convertedAmount, conversion.convertedAmount)
    }

    @Test
    fun `copy and share keep the entered decimals in every language`() {
        typedAmounts.forEach { (input, shown) ->
            val conversion = convert(input)
            val target = ConversionText.approximate(conversion.convertedAmount, Currency.USD)
            val rate = ConversionText.rate(Currency.MAD, Currency.USD, conversion.rate)
            assertInEveryLanguage("$shown MAD $target\n$rate") { ConversionText.shareText(conversion) }
        }
    }

    @Test
    fun `history label keeps the entered decimals after a storage round trip`() {
        typedAmounts.forEach { (input, shown) ->
            val conversion = convert(input)
            // History stores amounts with toPlainString() and reads them back with BigDecimal(String).
            val restored = BigDecimal(conversion.amount.toPlainString())
            assertInEveryLanguage("$shown MAD → ${ConversionText.money(conversion.convertedAmount, Currency.USD)}") {
                "${ConversionText.amount(restored, Currency.MAD)} → " +
                    ConversionText.money(BigDecimal(conversion.convertedAmount.toPlainString()), Currency.USD)
            }
        }
    }

    @Test
    fun `target amounts keep their currency rounding whatever the typed decimals`() {
        listOf("1250", "1250.5", "1250.50", "1250.00").forEach { input ->
            val usd = convert(input)
            assertEquals(2, usd.convertedAmount.scale())
            val jpy = convert(input, Currency.JPY)
            assertEquals(0, jpy.convertedAmount.scale())
            assertWesternDigitsOnly(ConversionText.money(jpy.convertedAmount, Currency.JPY))
            assertTrue(ConversionText.money(jpy.convertedAmount, Currency.JPY).matches(Regex("""[\d,]+ JPY""")))
        }
        assertInEveryLanguage("19,822 JPY") { ConversionText.money(BigDecimal("19822"), Currency.JPY) }
    }

    @Test
    fun `isolated source amount with trailing zeros keeps its order inside Arabic text`() {
        val label = "${ConversionText.amount(typed("1250.00"), Currency.MAD)} → 124.59 USD"
        val text = "تحويل " + ltr(label)
        val bidi = Bidi(text, Bidi.DIRECTION_RIGHT_TO_LEFT)
        val start = text.indexOf(label)
        val levels = (start until start + label.length).map { bidi.getLevelAt(it) }.toSet()
        assertEquals(1, levels.size)
        assertEquals(0, levels.single() % 2)
        assertTrue(label.startsWith("1,250.00 MAD"))
    }

    // --- Copy / Share ---

    @Test
    fun `copy and share text is identical in every language`() {
        assertInEveryLanguage("1,250.5 MAD ≈ 125.43 USD\n1 MAD = 0.1003 USD") { ConversionText.shareText(madToUsd) }
    }

    @Test
    fun `copied text has Western digits and no bidi isolates`() {
        languages.forEach { language ->
            Locale.setDefault(language)
            val text = ConversionText.shareText(madToUsd)
            assertWesternDigitsOnly(text)
            assertTrue(text.none { it == '⁦' || it == '⁩' })
        }
    }

    // --- Real Cost and the custom fee ---

    @Test
    fun `real cost breakdown and percentages are identical in every language`() {
        val estimate = RealCost.estimate(madToUsd, BigDecimal("2.75"))
        assertInEveryLanguage("2.75%") { formatPercent(estimate.feePercent) }
        assertInEveryLanguage("3%") { formatPercent(BigDecimal("3")) }
        assertInEveryLanguage("0%") { formatPercent(BigDecimal.ZERO) }
        assertInEveryLanguage("125.43 USD") { ConversionText.money(estimate.convertedAmount, estimate.currency) }
        assertInEveryLanguage("3.45 USD") { ConversionText.money(estimate.fee, estimate.currency) }
        assertInEveryLanguage("128.88 USD") { ConversionText.money(estimate.total, estimate.currency) }
        assertEquals("%", PERCENT_SIGN)
    }

    @Test
    fun `custom fee accepts both separators and keeps the stored value`() {
        assertEquals(FeeInput.Valid(BigDecimal("2.75")), RealCost.parseFeePercent("2.75"))
        assertEquals(FeeInput.Valid(BigDecimal("2.75")), RealCost.parseFeePercent("2,75"))
        assertEquals(FeeInput.Valid(BigDecimal("2.75")), RealCost.parseFeePercent(normalizeNumericInput("٢٫٧٥")))
        // The field shows the stored value as plain ASCII.
        assertEquals("2.75", BigDecimal("2.75").toPlainString())
    }

    // --- Error messages ---

    @Test
    fun `maximum amount in the error message is identical in every language`() {
        assertInEveryLanguage("999,999,999,999.99") { formatDecimal(CurrencyConverter.MAX_AMOUNT, minDigits = 2) }
    }

    // --- Dates and times ---

    @Test
    fun `platform dates in Arabic Egypt become Western digits, words untouched`() {
        val arEg = Locale.forLanguageTag("ar-EG")
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, arEg)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val raw = format.format(Date(1_791_291_600_000L)) // 2026-10-06 13:00 UTC
        assumeTrue("JVM formats ar-EG with Arabic-Indic digits", raw.any { it in '٠'..'٩' })

        val ascii = asciiDigits(raw)
        assertWesternDigitsOnly(ascii)
        assertTrue(ascii, "2026" in ascii)
        assertEquals(raw.filterNot { it.isDigit() }, ascii.filterNot { it.isDigit() })
    }

    @Test
    fun `asciiDigits only replaces digits`() {
        assertEquals("اليوم، 9:16 ص", asciiDigits("اليوم، ٩:١٦ ص"))
        assertEquals("12:45", asciiDigits("۱۲:۴۵"))
        assertEquals("Aujourd'hui, 09:16", asciiDigits("Aujourd'hui, 09:16"))
    }

    // --- Arabic RTL ---

    @Test
    fun `isolated Western-digit amounts keep their order inside Arabic text`() {
        val rate = ConversionText.rate(Currency.MAD, Currency.USD, madToUsd.rate)
        val text = "آخر سعر: " + ltr(rate)
        val bidi = Bidi(text, Bidi.DIRECTION_RIGHT_TO_LEFT)
        val start = text.indexOf(rate)

        val levels = (start until start + rate.length).map { bidi.getLevelAt(it) }.toSet()
        assertEquals("one run for \"$rate\"", 1, levels.size)
        assertEquals("even level = left to right", 0, levels.single() % 2)
    }
}
