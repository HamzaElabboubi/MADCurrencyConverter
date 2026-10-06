package com.elabboubisolution.madconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencyTest {

    @Test
    fun `all V1_5 currencies are supported, MAD first`() {
        assertEquals(
            listOf("MAD", "EUR", "USD", "GBP", "CAD", "CHF", "AED", "SAR", "TRY", "JPY", "CNY"),
            Currency.entries.map { it.code },
        )
    }

    @Test
    fun `every code is a valid ISO 4217 currency`() {
        Currency.entries.forEach { java.util.Currency.getInstance(it.code) }
    }

    @Test
    fun `minor units follow ISO 4217`() {
        assertEquals(0, Currency.JPY.fractionDigits)
        (Currency.entries - Currency.JPY).forEach { assertEquals("$it", 2, it.fractionDigits) }
    }

    @Test
    fun `display names are localized and capitalized`() {
        assertEquals("Moroccan Dirham", Currency.MAD.displayName(Locale.ENGLISH))
        assertEquals("Dirham marocain", Currency.MAD.displayName(Locale.FRENCH))
        assertEquals("Japanese Yen", Currency.JPY.displayName(Locale.ENGLISH))
        Currency.entries.forEach { currency ->
            val name = currency.displayName(Locale.FRENCH)
            assertTrue("$currency: $name", name.first().isUpperCase() && name != currency.code)
        }
    }

    @Test
    fun `curated symbols`() {
        assertEquals("DH", Currency.MAD.symbol)
        assertEquals("€", Currency.EUR.symbol)
        assertEquals("¥", Currency.JPY.symbol)
        assertNull("CHF is written as its code", Currency.CHF.symbol)
    }

    @Test
    fun `fromCode resolves supported codes only`() {
        assertEquals(Currency.TRY, Currency.fromCode("TRY"))
        assertNull(Currency.fromCode("XAU"))
        assertNull(Currency.fromCode("mad"))
    }
}
