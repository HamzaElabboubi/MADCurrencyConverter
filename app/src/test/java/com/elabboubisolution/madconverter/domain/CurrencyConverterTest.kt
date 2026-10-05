package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Expected values were computed independently with Python's `decimal` module
 * (34 significant digits, ROUND_HALF_UP to 2 decimals) from the rates below,
 * taken from a real open.er-api.com response on 2026-10-05.
 */
class CurrencyConverterTest {

    private val snapshot = RateSnapshot(
        base = Currency.MAD,
        rates = mapOf(
            Currency.MAD to BigDecimal("1"),
            Currency.USD to BigDecimal("0.100812"),
            Currency.EUR to BigDecimal("0.089729"),
            Currency.GBP to BigDecimal("0.076291"),
        ),
        lastUpdatedEpochSeconds = 1791158551L,
        nextUpdateEpochSeconds = 1791246061L,
    )

    // --- Conversions ---

    @Test
    fun `MAD to USD`() {
        val conversion = convert("1000", Currency.MAD, Currency.USD)

        assertEquals(BigDecimal("100.81"), conversion.convertedAmount)
        assertNumericEquals("0.100812", conversion.rate)
    }

    @Test
    fun `USD to MAD`() {
        val conversion = convert("100", Currency.USD, Currency.MAD)

        assertEquals(BigDecimal("991.95"), conversion.convertedAmount)
        assertNumericEquals("9.919454033250009919454033250009919", conversion.rate)
    }

    @Test
    fun `MAD to EUR`() {
        val conversion = convert("1000", Currency.MAD, Currency.EUR)

        assertEquals(BigDecimal("89.73"), conversion.convertedAmount)
        assertNumericEquals("0.089729", conversion.rate)
    }

    @Test
    fun `cross rate between two non base currencies`() {
        val conversion = convert("50", Currency.EUR, Currency.GBP)

        assertEquals(BigDecimal("42.51"), conversion.convertedAmount)
        assertNumericEquals("0.8502379386820314502557701523476245", conversion.rate)
    }

    @Test
    fun `decimal amount`() {
        // 1234.56 x 0.100812 = 124.45846272 -> 124.46
        val conversion = convert("1234.56", Currency.MAD, Currency.USD)

        assertEquals(BigDecimal("124.46"), conversion.convertedAmount)
    }

    @Test
    fun `zero amount converts to zero`() {
        val conversion = convert("0", Currency.MAD, Currency.USD)

        assertEquals(BigDecimal("0.00"), conversion.convertedAmount)
        assertNumericEquals("0.100812", conversion.rate)
    }

    @Test
    fun `same currency returns the amount with rate 1`() {
        val conversion = convert("12.5", Currency.MAD, Currency.MAD)

        assertEquals(BigDecimal("12.50"), conversion.convertedAmount)
        assertNumericEquals("1", conversion.rate)
    }

    @Test
    fun `very large amount keeps full precision without overflow or scientific notation`() {
        // 999999999999.99 x 0.100812 = 100811999999.99899188 -> 100812000000.00
        val toUsd = convert("999999999999.99", Currency.MAD, Currency.USD)
        assertEquals(BigDecimal("100812000000.00"), toUsd.convertedAmount)
        assertEquals("100812000000.00", toUsd.convertedAmount.toPlainString())

        // 999999999999.99 / 0.100812 = 9919454033249.9107... -> 9919454033249.91
        val toMad = convert("999999999999.99", Currency.USD, Currency.MAD)
        assertEquals(BigDecimal("9919454033249.91"), toMad.convertedAmount)
    }

    @Test
    fun `amounts above the maximum are rejected`() {
        assertEquals(AmountInput.Valid(BigDecimal("999999999999.99")), CurrencyConverter.parseAmount("999999999999.99"))
        assertEquals(AmountInput.TooLarge, CurrencyConverter.parseAmount("1000000000000"))
        assertEquals(AmountInput.TooLarge, CurrencyConverter.parseAmount("99999999999999999999999"))
    }

    // --- Missing rate ---

    @Test
    fun `missing target rate is reported`() {
        val withoutGbp = snapshot.copy(rates = snapshot.rates - Currency.GBP)

        val result = CurrencyConverter.convert(BigDecimal.TEN, Currency.MAD, Currency.GBP, withoutGbp)

        assertEquals(ConversionResult.MissingRate(Currency.GBP), result)
    }

    @Test
    fun `missing source rate is reported`() {
        val withoutEur = snapshot.copy(rates = snapshot.rates - Currency.EUR)

        val result = CurrencyConverter.convert(BigDecimal.TEN, Currency.EUR, Currency.MAD, withoutEur)

        assertEquals(ConversionResult.MissingRate(Currency.EUR), result)
    }

    // --- Parsing ---

    @Test
    fun `comma and dot are both accepted as decimal separator`() {
        val expected = BigDecimal("1234.56")
        assertNumericEquals(expected, valid("1234.56"))
        assertNumericEquals(expected, valid("1234,56"))
        assertNumericEquals(BigDecimal("0.5"), valid(",5"))
        assertNumericEquals(BigDecimal("0.5"), valid(".5"))
        assertNumericEquals(BigDecimal("12"), valid("12,"))
        assertNumericEquals(BigDecimal("12"), valid("12."))
    }

    @Test
    fun `comma and dot inputs give the same conversion`() {
        val withComma = convert("1234,56", Currency.MAD, Currency.USD)
        val withDot = convert("1234.56", Currency.MAD, Currency.USD)

        assertEquals(withDot.convertedAmount, withComma.convertedAmount)
    }

    @Test
    fun `spaces including non breaking ones are ignored`() {
        assertNumericEquals(BigDecimal("1000.5"), valid(" 1 000,5 "))
        assertNumericEquals(BigDecimal("1000.5"), valid("1 000,5"))
        assertNumericEquals(BigDecimal("1000.5"), valid("1 000,5"))
    }

    @Test
    fun `blank input is empty, not invalid`() {
        assertEquals(AmountInput.Empty, CurrencyConverter.parseAmount(""))
        assertEquals(AmountInput.Empty, CurrencyConverter.parseAmount("   "))
    }

    @Test
    fun `invalid amounts are rejected`() {
        listOf(
            "abc",
            "12a",
            "-5",
            "+5",
            "1e5",
            ".",
            ",",
            "1,2,3",
            "1.2.3",
            "1.000,50", // grouping + decimal separators are ambiguous
            "1,000.50",
            "NaN",
            "Infinity",
            "١٢٣", // Arabic-Indic digits
        ).forEach { input ->
            assertEquals("input \"$input\"", AmountInput.Invalid, CurrencyConverter.parseAmount(input))
        }
    }

    // --- Helpers ---

    private fun valid(input: String): BigDecimal {
        val parsed = CurrencyConverter.parseAmount(input)
        check(parsed is AmountInput.Valid) { "Expected \"$input\" to be valid but was $parsed" }
        return parsed.value
    }

    private fun convert(input: String, from: Currency, to: Currency): Conversion {
        val result = CurrencyConverter.convert(valid(input), from, to, snapshot)
        check(result is ConversionResult.Success) { "Expected success but was $result" }
        return result.conversion
    }

    private fun assertNumericEquals(expected: String, actual: BigDecimal) =
        assertNumericEquals(BigDecimal(expected), actual)

    private fun assertNumericEquals(expected: BigDecimal, actual: BigDecimal) {
        assertEquals("expected $expected but was $actual", 0, expected.compareTo(actual))
    }
}
