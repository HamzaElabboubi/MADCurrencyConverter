package com.elabboubisolution.madconverter.ui.format

import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class ConversionTextTest {

    private val madToUsd = Conversion(
        from = Currency.MAD,
        to = Currency.USD,
        amount = BigDecimal("1000"),
        convertedAmount = BigDecimal("100.81"),
        rate = BigDecimal("0.100812"),
    )

    @Test
    fun `copy and share text in English`() {
        assertEquals(
            "1,000 MAD ≈ 100.81 USD\n1 MAD = 0.1008 USD",
            ConversionText.shareText(madToUsd, Locale.US),
        )
    }

    @Test
    fun `copy and share text in French uses French separators`() {
        val text = ConversionText.shareText(madToUsd, Locale.FRANCE)
        // French groups thousands with a narrow no-break space (U+202F) and uses a decimal comma.
        assertEquals("1 000 MAD ≈ 100,81 USD\n1 MAD = 0,1008 USD", text)
    }

    @Test
    fun `typed decimals are kept and zero decimal currencies have none`() {
        val toJpy = Conversion(Currency.MAD, Currency.JPY, BigDecimal("1234.5"), BigDecimal("19554"), BigDecimal("15.839832"))

        assertEquals("1,234.5 MAD ≈ 19,554 JPY\n1 MAD = 15.8398 JPY", ConversionText.shareText(toJpy, Locale.US))
    }

    @Test
    fun `share text has no branding, link or advertising`() {
        val text = ConversionText.shareText(madToUsd, Locale.US)

        assertFalse(text.contains("http"))
        assertFalse(text.contains("Converter"))
        assertEquals(2, text.lines().size)
    }

    @Test
    fun `screen pieces match the shared text`() {
        assertEquals("1,000 MAD", ConversionText.amount(madToUsd.amount, Currency.MAD, Locale.US))
        assertEquals("≈ 100.81 USD", ConversionText.approximate(madToUsd.convertedAmount, Currency.USD, Locale.US))
        assertEquals("100.81 USD", ConversionText.money(madToUsd.convertedAmount, Currency.USD, Locale.US))
        assertEquals("1 MAD = 0.1008 USD", ConversionText.rate(Currency.MAD, Currency.USD, madToUsd.rate, Locale.US))
    }
}
