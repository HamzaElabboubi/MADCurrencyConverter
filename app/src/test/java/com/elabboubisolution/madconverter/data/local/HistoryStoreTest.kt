package com.elabboubisolution.madconverter.data.local

import androidx.datastore.preferences.core.edit
import com.elabboubisolution.madconverter.data.local.RateCacheTest.Companion.testDataStore
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.ConversionHistory
import com.elabboubisolution.madconverter.domain.model.Currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal

class HistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val file by lazy { File(tmp.root, "conversion_history.preferences_pb") }
    private var now = 1_791_250_000_000L
    private var nextId = 0

    /** A fresh store on the same file, as after an app restart. */
    private fun openStore(): DataStoreHistoryStore {
        scopes.forEach { it.cancel() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreHistoryStore(testDataStore(scope, file), Json, clock = { now }, newId = { "id${nextId++}" })
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    private fun conversion(amount: String = "1000", converted: String = "100.81") = Conversion(
        from = Currency.MAD,
        to = Currency.USD,
        amount = BigDecimal(amount),
        convertedAmount = BigDecimal(converted),
        rate = BigDecimal("0.100812"),
    )

    @Test
    fun `starts empty`() = runTest {
        assertEquals(emptyList<Any>(), openStore().entries.first())
    }

    @Test
    fun `recorded entry persists with exact values, time and stale flag`() = runTest {
        openStore().record(conversion(amount = "1234.50", converted = "124.45"), wasStale = true)

        val entry = openStore().entries.first().single()

        assertEquals("id0", entry.id)
        assertEquals(Currency.MAD, entry.from)
        assertEquals(Currency.USD, entry.to)
        assertEquals("1234.50", entry.amount.toPlainString())
        assertEquals("124.45", entry.convertedAmount.toPlainString())
        assertEquals("0.100812", entry.rate.toPlainString())
        assertEquals(now, entry.timestampMillis)
        assertEquals(true, entry.wasStale)
    }

    @Test
    fun `copy then share of the same result is stored once`() = runTest {
        val store = openStore()
        store.record(conversion(), wasStale = false)
        now += 5_000
        store.record(conversion(), wasStale = false)

        assertEquals(1, store.entries.first().size)
    }

    @Test
    fun `history is capped and the oldest entries are removed`() = runTest {
        val store = openStore()
        repeat(52) { i -> store.record(conversion(amount = "${i + 1}", converted = "$i"), wasStale = false) }

        val entries = store.entries.first()

        assertEquals(ConversionHistory.MAX_ENTRIES, entries.size)
        assertEquals("52", entries.first().amount.toPlainString())
        assertEquals("3", entries.last().amount.toPlainString())
    }

    @Test
    fun `delete removes only that entry`() = runTest {
        val store = openStore()
        store.record(conversion(amount = "1"), wasStale = false)
        store.record(conversion(amount = "2"), wasStale = false)

        store.delete("id0")

        assertEquals(listOf("2"), store.entries.first().map { it.amount.toPlainString() })
    }

    @Test
    fun `clear removes everything, also after restart`() = runTest {
        val store = openStore()
        store.record(conversion(amount = "1"), wasStale = false)
        store.record(conversion(amount = "2"), wasStale = false)

        store.clear()

        assertEquals(emptyList<Any>(), store.entries.first())
        assertEquals(emptyList<Any>(), openStore().entries.first())
    }

    @Test
    fun `corrupt data reads as empty history and is replaced by the next record`() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val dataStore = testDataStore(scope, File(tmp.root, "corrupt.preferences_pb"))
        dataStore.edit { it[DataStoreHistoryStore.HISTORY_KEY] = "[{not json" }
        val store = DataStoreHistoryStore(dataStore, Json, clock = { now }, newId = { "x" })

        assertEquals(emptyList<Any>(), store.entries.first())
        store.record(conversion(), wasStale = false)
        assertEquals(1, store.entries.first().size)
    }

    @Test
    fun `entries with unsupported currencies are dropped`() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val dataStore = testDataStore(scope, File(tmp.root, "unknown.preferences_pb"))
        dataStore.edit {
            it[DataStoreHistoryStore.HISTORY_KEY] = """[
                {"id":"a","from":"MAD","to":"XYZ","amount":"1","convertedAmount":"1","rate":"1","timestampMillis":1,"wasStale":false},
                {"id":"b","from":"MAD","to":"EUR","amount":"1","convertedAmount":"0.09","rate":"0.0897","timestampMillis":1,"wasStale":false}
            ]"""
        }

        assertEquals(listOf("b"), DataStoreHistoryStore(dataStore, Json).entries.first().map { it.id })
    }
}
