package com.elabboubisolution.madconverter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal

class RateCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cache: RateCache

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = testDataStore(scope, File(tmp.root, "rates.preferences_pb"))
        cache = RateCache(dataStore, Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `empty cache reads as null`() = runTest {
        assertNull(cache.read())
    }

    @Test
    fun `written snapshot round trips with exact decimals`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, fetchedAtEpochMillis = 1_791_160_000_000L)

        val read = cache.read()

        assertEquals(CachedSnapshot(SAMPLE_SNAPSHOT, 1_791_160_000_000L), read)
        assertEquals("0.100812", read?.snapshot?.rates?.get(Currency.USD)?.toPlainString())
    }

    @Test
    fun `later write replaces the previous snapshot`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, fetchedAtEpochMillis = 1L)
        val newer = SAMPLE_SNAPSHOT.copy(lastUpdatedEpochSeconds = 1_791_246_061L)

        cache.write(newer, fetchedAtEpochMillis = 2L)

        assertEquals(CachedSnapshot(newer, 2L), cache.read())
    }

    @Test
    fun `corrupt json reads as null instead of crashing`() = runTest {
        dataStore.edit { it[RateCache.SNAPSHOT_KEY] = "{not json" }

        assertNull(cache.read())
    }

    @Test
    fun `invalid stored rate invalidates the entry`() = runTest {
        dataStore.edit {
            it[RateCache.SNAPSHOT_KEY] =
                """{"base":"MAD","rates":{"MAD":"1","USD":"-3"},"lastUpdatedEpochSeconds":1,"fetchedAtEpochMillis":1}"""
        }

        assertNull(cache.read())
    }

    @Test
    fun `unsupported currencies in storage are ignored`() = runTest {
        dataStore.edit {
            it[RateCache.SNAPSHOT_KEY] =
                """{"base":"MAD","rates":{"MAD":"1","USD":"0.1","XYZ":"5"},"lastUpdatedEpochSeconds":1,"fetchedAtEpochMillis":1}"""
        }

        val rates = cache.read()?.snapshot?.rates

        assertEquals(setOf(Currency.MAD, Currency.USD), rates?.keys)
    }

    companion object {
        val SAMPLE_SNAPSHOT = RateSnapshot(
            base = Currency.MAD,
            rates = mapOf(
                Currency.MAD to BigDecimal("1"),
                Currency.USD to BigDecimal("0.100812"),
                Currency.EUR to BigDecimal("0.089729"),
                Currency.GBP to BigDecimal("0.076291"),
            ),
            lastUpdatedEpochSeconds = 1_791_158_551L,
            nextUpdateEpochSeconds = 1_791_246_061L,
        )

        fun testDataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }
}
