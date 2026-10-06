package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.Currency.AED
import com.elabboubisolution.madconverter.domain.model.Currency.CAD
import com.elabboubisolution.madconverter.domain.model.Currency.EUR
import com.elabboubisolution.madconverter.domain.model.Currency.GBP
import com.elabboubisolution.madconverter.domain.model.Currency.JPY
import com.elabboubisolution.madconverter.domain.model.Currency.MAD
import com.elabboubisolution.madconverter.domain.model.Currency.TRY
import com.elabboubisolution.madconverter.domain.model.Currency.USD
import com.elabboubisolution.madconverter.testing.SampleRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Expected amounts computed independently with Python's `decimal` module from [SampleRates]. */
class QuickConversionsTest {

    private val snapshot = SampleRates.snapshot

    private fun quick(
        amount: String = "1000",
        from: Currency = MAD,
        to: Currency = USD,
        favorites: Set<Currency>,
    ) = QuickConversions.convert(BigDecimal(amount), from, to, favorites, snapshot)

    private fun targets(from: Currency = MAD, to: Currency = USD, favorites: Set<Currency>) =
        quick(from = from, to = to, favorites = favorites).map { it.to }

    @Test
    fun `only favorites are used`() {
        assertEquals(listOf(EUR, JPY), targets(favorites = setOf(EUR, JPY)))
    }

    @Test
    fun `source and target are excluded, order follows the picker`() {
        // Spec example: source MAD, target USD, favorites MAD, USD, EUR, AED, GBP.
        assertEquals(listOf(EUR, GBP, AED), targets(favorites = setOf(MAD, USD, EUR, AED, GBP)))
    }

    @Test
    fun `source currency is excluded`() {
        assertTrue(EUR !in targets(from = EUR, to = USD, favorites = setOf(EUR, MAD, GBP)))
    }

    @Test
    fun `target currency is excluded`() {
        assertTrue(GBP !in targets(from = MAD, to = GBP, favorites = setOf(GBP, EUR, MAD)))
    }

    @Test
    fun `no duplicates even when source equals a favorite and target too`() {
        val result = targets(from = EUR, to = EUR, favorites = Currency.entries.toSet())
        assertEquals(result.distinct(), result)
        assertTrue(EUR !in result)
    }

    @Test
    fun `at most three results`() {
        assertEquals(listOf(EUR, GBP, CAD), targets(favorites = Currency.entries.toSet()))
    }

    @Test
    fun `fewer than three eligible favorites are not padded`() {
        // Default favorites MAD, EUR, USD with MAD -> USD leave only EUR.
        assertEquals(listOf(EUR), targets(favorites = setOf(MAD, EUR, USD)))
    }

    @Test
    fun `no eligible favorite gives an empty list`() {
        assertEquals(emptyList<Currency>(), targets(favorites = setOf(MAD, USD)))
        assertEquals(emptyList<Currency>(), targets(favorites = emptySet()))
    }

    @Test
    fun `MAD to several favorites`() {
        val amounts = quick(favorites = setOf(EUR, GBP, AED)).associate { it.to to it.convertedAmount }

        assertEquals(mapOf(EUR to BigDecimal("89.73"), GBP to BigDecimal("76.29"), AED to BigDecimal("368.29")), amounts)
    }

    @Test
    fun `non MAD source to several favorites, including a zero decimal currency`() {
        val result = quick(amount = "250.5", from = EUR, to = USD, favorites = setOf(MAD, EUR, USD, TRY, JPY))

        assertEquals(
            listOf(MAD to BigDecimal("2791.74"), TRY to BigDecimal("13757.34"), JPY to BigDecimal("44221")),
            result.map { it.to to it.convertedAmount },
        )
        assertTrue(result.all { it.from == EUR && it.amount == BigDecimal("250.5") })
    }

    @Test
    fun `decimal amount`() {
        val amounts = quick(amount = "1234.56", favorites = setOf(EUR, GBP, AED)).map { it.convertedAmount }

        assertEquals(listOf(BigDecimal("110.78"), BigDecimal("94.19"), BigDecimal("454.67")), amounts)
    }

    @Test
    fun `results are identical to the main conversion logic`() {
        quick(amount = "987.65", favorites = Currency.entries.toSet()).forEach { quick ->
            val main = CurrencyConverter.convert(BigDecimal("987.65"), MAD, quick.to, snapshot)
            assertEquals((main as ConversionResult.Success).conversion, quick)
        }
    }

    @Test
    fun `favorite without a rate is skipped and the next one takes its place`() {
        val withoutGbp = snapshot.copy(rates = snapshot.rates - GBP)

        val result = QuickConversions.convert(BigDecimal.TEN, MAD, USD, setOf(EUR, GBP, CAD, AED), withoutGbp)

        assertEquals(listOf(EUR, CAD, AED), result.map { it.to })
    }
}
