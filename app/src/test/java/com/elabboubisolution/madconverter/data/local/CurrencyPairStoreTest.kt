package com.elabboubisolution.madconverter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elabboubisolution.madconverter.data.local.DataStoreCurrencyPairStore.Companion.FROM_KEY
import com.elabboubisolution.madconverter.data.local.DataStoreCurrencyPairStore.Companion.TO_KEY
import com.elabboubisolution.madconverter.data.local.RateCacheTest.Companion.testDataStore
import com.elabboubisolution.madconverter.domain.model.Currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.math.BigDecimal

class CurrencyPairStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val file by lazy { File(tmp.root, "user_preferences.preferences_pb") }

    /** Opens the preferences file as a new process would (previous instances closed). */
    private fun openDataStore(): DataStore<Preferences> {
        scopes.forEach { it.cancel() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return testDataStore(scope, file)
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun `nothing saved reads as null so the default pair applies`() = runTest {
        assertNull(DataStoreCurrencyPairStore(openDataStore()).read())
    }

    @Test
    fun `saved pair survives a restart`() = runTest {
        DataStoreCurrencyPairStore(openDataStore()).save(CurrencyPair(Currency.EUR, Currency.MAD))

        assertEquals(CurrencyPair(Currency.EUR, Currency.MAD), DataStoreCurrencyPairStore(openDataStore()).read())
    }

    @Test
    fun `the last save wins`() = runTest {
        val store = DataStoreCurrencyPairStore(openDataStore())
        store.save(CurrencyPair(Currency.EUR, Currency.MAD))
        store.save(CurrencyPair(Currency.GBP, Currency.JPY))

        assertEquals(CurrencyPair(Currency.GBP, Currency.JPY), DataStoreCurrencyPairStore(openDataStore()).read())
    }

    @Test
    fun `unsupported, malformed or identical codes read as null`() = runTest {
        val dataStore = openDataStore()
        val store = DataStoreCurrencyPairStore(dataStore)
        listOf("XYZ" to "MAD", "EUR" to "", "eur" to "MAD", "USD" to "USD").forEach { (from, to) ->
            dataStore.edit {
                it[FROM_KEY] = from
                it[TO_KEY] = to
            }
            assertNull("$from -> $to", store.read())
        }
    }

    @Test
    fun `a partially stored pair reads as null`() = runTest {
        val dataStore = openDataStore()
        val store = DataStoreCurrencyPairStore(dataStore)

        dataStore.edit { it[FROM_KEY] = "EUR" }
        assertNull("target missing", store.read())

        dataStore.edit {
            it.remove(FROM_KEY)
            it[TO_KEY] = "EUR"
        }
        assertNull("source missing", store.read())
    }

    @Test
    fun `saving the pair keeps favorites, the fee and unknown keys`() = runTest {
        val dataStore = openDataStore()
        val favorites = DataStoreFavoritesStore(dataStore)
        val fees = DataStoreFeePreferenceStore(dataStore)
        val otherKey = stringPreferencesKey("some_future_setting")
        favorites.setFavorite(Currency.JPY, isFavorite = true)
        fees.setFeePercent(BigDecimal("2.5"))
        dataStore.edit { it[otherKey] = "kept" }

        DataStoreCurrencyPairStore(dataStore).save(CurrencyPair(Currency.USD, Currency.JPY))

        val reopened = openDataStore()
        assertEquals(DataStoreFavoritesStore.DEFAULT_FAVORITES + Currency.JPY, DataStoreFavoritesStore(reopened).favorites.first())
        assertEquals(BigDecimal("2.5"), DataStoreFeePreferenceStore(reopened).feePercent.first())
        assertEquals("kept", reopened.data.first()[otherKey])
        assertEquals(CurrencyPair(Currency.USD, Currency.JPY), DataStoreCurrencyPairStore(reopened).read())
    }

    @Test
    fun `a read failure reads as null`() = runTest {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk error") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("disk error")
        }

        assertNull(DataStoreCurrencyPairStore(failing).read())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a pair of the same currency cannot be created`() {
        CurrencyPair(Currency.MAD, Currency.MAD)
    }
}
