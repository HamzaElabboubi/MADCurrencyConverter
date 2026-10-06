package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.testing.SampleRates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** Expected values computed independently with Python's `decimal` module (ROUND_HALF_UP). */
class RealCostTest {

    /** The spec example: 250 EUR ≈ 2,492.00 MAD. */
    private val specExample =
        Conversion(Currency.EUR, Currency.MAD, BigDecimal("250"), BigDecimal("2492.00"), BigDecimal("9.968"))

    private fun estimate(conversion: Conversion, percent: String) = RealCost.estimate(conversion, BigDecimal(percent))

    private fun convert(amount: String, from: Currency, to: Currency) =
        (CurrencyConverter.convert(BigDecimal(amount), from, to, SampleRates.snapshot) as ConversionResult.Success).conversion

    private fun assertFeeAndTotal(fee: String, total: String, actual: RealCostEstimate) {
        assertEquals("fee", BigDecimal(fee), actual.fee)
        assertEquals("total", BigDecimal(total), actual.total)
    }

    // --- Calculation ---

    @Test
    fun `presets 0, 1, 2 and 3 percent on the spec example`() {
        assertFeeAndTotal("0.00", "2492.00", estimate(specExample, "0"))
        assertFeeAndTotal("24.92", "2516.92", estimate(specExample, "1"))
        assertFeeAndTotal("49.84", "2541.84", estimate(specExample, "2"))
        assertFeeAndTotal("74.76", "2566.76", estimate(specExample, "3"))
    }

    @Test
    fun `decimal custom fee`() {
        assertFeeAndTotal("68.53", "2560.53", estimate(specExample, "2.75"))
    }

    @Test
    fun `maximum allowed fee`() {
        assertFeeAndTotal("498.40", "2990.40", estimate(specExample, "20"))
    }

    @Test
    fun `MAD target from a real conversion`() {
        val eurToMad = convert("250", Currency.EUR, Currency.MAD) // 2786.17 MAD

        assertEquals(Currency.MAD, estimate(eurToMad, "3").currency)
        assertFeeAndTotal("83.59", "2869.76", estimate(eurToMad, "3"))
        assertFeeAndTotal("41.79", "2827.96", estimate(eurToMad, "1.5"))
        assertFeeAndTotal("118.41", "2904.58", estimate(eurToMad, "4.25"))
    }

    @Test
    fun `USD and EUR targets`() {
        assertFeeAndTotal("3.02", "103.83", estimate(convert("1000", Currency.MAD, Currency.USD), "3"))
        assertFeeAndTotal("2.52", "103.33", estimate(convert("1000", Currency.MAD, Currency.USD), "2.5"))
        assertFeeAndTotal("4.49", "228.81", estimate(convert("2500", Currency.MAD, Currency.EUR), "2"))
    }

    @Test
    fun `swapping the direction moves the estimate to the new target currency`() {
        val madToEur = estimate(convert("250", Currency.MAD, Currency.EUR), "3")

        assertEquals(Currency.EUR, madToEur.currency)
        assertFeeAndTotal("0.67", "23.10", madToEur)
    }

    @Test
    fun `JPY estimate has no decimals`() {
        val result = estimate(convert("1000", Currency.MAD, Currency.JPY), "3") // 15840 JPY

        assertFeeAndTotal("475", "16315", result)
        assertEquals(0, result.fee.scale())
        assertEquals(0, result.total.scale())
        assertFeeAndTotal("436", "16276", estimate(convert("1000", Currency.MAD, Currency.JPY), "2.75"))
    }

    @Test
    fun `decimal source amount`() {
        // 1234.56 MAD -> 94.19 GBP, 1.5% fee.
        assertFeeAndTotal("1.41", "95.60", estimate(convert("1234.56", Currency.MAD, Currency.GBP), "1.5"))
    }

    @Test
    fun `displayed converted amount plus fee always equals the total`() {
        listOf("0", "0.01", "1.5", "2.75", "3", "7.33", "19.99", "20").forEach { percent ->
            val result = estimate(specExample, percent)
            assertEquals(percent, result.total, result.convertedAmount + result.fee)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `estimate rejects a negative fee`() {
        estimate(specExample, "-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `estimate rejects a fee above the maximum`() {
        estimate(specExample, "20.01")
    }

    // --- Custom fee validation ---

    @Test
    fun `valid custom fees with comma or dot`() {
        mapOf("0" to "0", "1.5" to "1.5", "2,75" to "2.75", "4.25" to "4.25", " 3 " to "3", "20" to "20", "2." to "2")
            .forEach { (input, expected) ->
                val parsed = RealCost.parseFeePercent(input)
                check(parsed is FeeInput.Valid) { "\"$input\" should be valid but was $parsed" }
                assertEquals(input, 0, BigDecimal(expected).compareTo(parsed.percent))
            }
    }

    @Test
    fun `empty custom fee`() {
        assertEquals(FeeInput.Empty, RealCost.parseFeePercent(""))
        assertEquals(FeeInput.Empty, RealCost.parseFeePercent("   "))
    }

    @Test
    fun `invalid and negative custom fees are rejected`() {
        listOf("abc", "-1", "-0.5", "+2", "1e2", "NaN", "Infinity", "1.234", "1,2,3", "1.2.3", ".5", "%", "2%")
            .forEach { assertEquals(it, FeeInput.Invalid, RealCost.parseFeePercent(it)) }
    }

    @Test
    fun `fees above the maximum are too high`() {
        assertEquals(FeeInput.TooHigh, RealCost.parseFeePercent("20.01"))
        assertEquals(FeeInput.TooHigh, RealCost.parseFeePercent("21"))
        assertEquals(FeeInput.TooHigh, RealCost.parseFeePercent("100"))
    }
}
