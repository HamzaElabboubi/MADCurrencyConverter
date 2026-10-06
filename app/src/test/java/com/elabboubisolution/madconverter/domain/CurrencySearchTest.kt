package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencySearchTest {

    private val defaultFavorites = setOf(Currency.MAD, Currency.EUR, Currency.USD)

    private fun search(query: String, locale: Locale = Locale.ENGLISH, favorites: Set<Currency> = emptySet()) =
        CurrencySearch.search(query, locale, favorites)

    private fun codes(query: String, locale: Locale = Locale.ENGLISH) =
        search(query, locale).others.map { it.code }

    @Test
    fun `empty query lists every currency with favorites first`() {
        val listing = search("  ", favorites = defaultFavorites)

        assertEquals(listOf(Currency.MAD, Currency.EUR, Currency.USD), listing.favorites)
        assertEquals(Currency.entries - defaultFavorites, listing.others)
    }

    @Test
    fun `search by exact code, any case`() {
        assertEquals("JPY", codes("jpy").first())
        assertEquals("CHF", codes("CHF").first())
    }

    @Test
    fun `search by code prefix ranks codes before name matches`() {
        // "ca" is the CAD code prefix, and also inside other names (e.g. "Moroccan", "American").
        assertEquals("CAD", codes("ca").first())
    }

    @Test
    fun `search by English name`() {
        assertEquals(listOf("JPY"), codes("yen"))
        assertEquals(listOf("MAD", "AED"), codes("dirham"))
        assertTrue("dollar finds USD and CAD", codes("dollar").containsAll(listOf("USD", "CAD")))
        assertEquals(listOf("TRY"), codes("turkish"))
    }

    @Test
    fun `search by name in French, accent insensitive`() {
        assertEquals(listOf("GBP", "TRY"), codes("livre", Locale.FRENCH))
        assertEquals("MAD", codes("marocain", Locale.FRENCH).single())
        assertTrue(codes("etats-unis", Locale.FRENCH).contains("USD"))
        assertTrue(codes("émirats", Locale.FRENCH).contains("AED"))
    }

    @Test
    fun `English names still match when the UI is in French`() {
        assertEquals(listOf("GBP"), codes("pound", Locale.FRENCH))
    }

    @Test
    fun `matches are split between favorites and others`() {
        val listing = search("dirham", favorites = setOf(Currency.AED))

        assertEquals(listOf(Currency.AED), listing.favorites)
        assertEquals(listOf(Currency.MAD), listing.others)
    }

    @Test
    fun `no match gives an empty listing`() {
        assertTrue(search("bitcoin", favorites = defaultFavorites).isEmpty)
    }
}
