package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal

class ConversionHistoryTest {

    private val window = ConversionHistory.DEDUP_WINDOW_MILLIS

    private fun entry(
        id: String,
        time: Long,
        amount: String = "1000",
        from: Currency = Currency.MAD,
        to: Currency = Currency.USD,
        converted: String = "100.81",
        rate: String = "0.100812",
        stale: Boolean = false,
    ) = HistoryEntry(id, from, to, BigDecimal(amount), BigDecimal(converted), BigDecimal(rate), time, stale)

    @Test
    fun `new entries go on top`() {
        val list = ConversionHistory.add(listOf(entry("a", 0)), entry("b", window, amount = "5", converted = "0.50"))

        assertEquals(listOf("b", "a"), list.map { it.id })
    }

    @Test
    fun `same conversion within the window is not duplicated`() {
        val existing = listOf(entry("a", 1_000))

        val result = ConversionHistory.add(existing, entry("b", 1_000 + window - 1))

        assertSame(existing, result)
    }

    @Test
    fun `numerically equal amounts count as the same conversion`() {
        val existing = listOf(entry("a", 0, amount = "1000"))

        assertSame(existing, ConversionHistory.add(existing, entry("b", 10, amount = "1000.00")))
    }

    @Test
    fun `same conversion after the window is recorded again`() {
        val list = ConversionHistory.add(listOf(entry("a", 0)), entry("b", window))

        assertEquals(listOf("b", "a"), list.map { it.id })
    }

    @Test
    fun `a different amount, pair or rate is a new entry`() {
        val base = listOf(entry("a", 0))

        assertEquals(2, ConversionHistory.add(base, entry("b", 1, amount = "999", converted = "100.71")).size)
        assertEquals(2, ConversionHistory.add(base, entry("c", 1, to = Currency.EUR, converted = "89.73")).size)
        assertEquals(2, ConversionHistory.add(base, entry("d", 1, rate = "0.1003", converted = "100.30")).size)
    }

    @Test
    fun `only the most recent entry is compared`() {
        var list = listOf(entry("a", 0))
        list = ConversionHistory.add(list, entry("b", 1, to = Currency.EUR, converted = "89.73"))
        list = ConversionHistory.add(list, entry("c", 2))

        assertEquals(listOf("c", "b", "a"), list.map { it.id })
    }

    @Test
    fun `keeps at most 50 entries and drops the oldest`() {
        var list = emptyList<HistoryEntry>()
        repeat(55) { i -> list = ConversionHistory.add(list, entry("e$i", i * window, amount = "$i", converted = "$i")) }

        assertEquals(ConversionHistory.MAX_ENTRIES, list.size)
        assertEquals("e54", list.first().id)
        assertEquals("e5", list.last().id)
    }

    @Test
    fun `stale flag is kept`() {
        val list = ConversionHistory.add(emptyList(), entry("a", 0, stale = true))

        assertEquals(true, list.single().wasStale)
    }
}
