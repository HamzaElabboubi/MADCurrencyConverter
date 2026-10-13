package com.elabboubisolution.madconverter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.elabboubisolution.madconverter.data.local.CachedSnapshot
import com.elabboubisolution.madconverter.data.local.RateCache
import com.elabboubisolution.madconverter.data.local.RateCacheTest.Companion.SAMPLE_SNAPSHOT
import com.elabboubisolution.madconverter.data.local.RateCacheTest.Companion.testDataStore
import com.elabboubisolution.madconverter.data.remote.RateProvider
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateFetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class CachingCurrencyRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: FailingWritesDataStore
    private lateinit var cache: RateCache
    private val provider = FakeProvider()
    private var now = FETCHED_AT
    private lateinit var repository: CachingCurrencyRepository

    // Snapshot published at 00:02 UTC, next update announced for the following day at 00:21 UTC.
    private val nextUpdateSeconds = checkNotNull(SAMPLE_SNAPSHOT.nextUpdateEpochSeconds)
    private val nextUpdateMillis = nextUpdateSeconds * 1000
    private val newerSnapshot = SAMPLE_SNAPSHOT.copy(
        lastUpdatedEpochSeconds = nextUpdateSeconds,
        nextUpdateEpochSeconds = nextUpdateSeconds + 86_400,
    )

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = FailingWritesDataStore(testDataStore(scope, File(tmp.root, "rates.preferences_pb")))
        cache = RateCache(dataStore, Json)
        repository = CachingCurrencyRepository(provider, cache, clock = { now })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `no cache - downloads, returns current rates and caches them`() = runTest {
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)

        val result = repository.getRates().ignoringDue()

        assertEquals(RatesResult.Available(SAMPLE_SNAPSHOT, isStale = false), result)
        assertEquals(CachedSnapshot(SAMPLE_SNAPSHOT, FETCHED_AT), cache.read())
        assertEquals(listOf(Currency.MAD), provider.requestedBases)
    }

    @Test
    fun `no cache and download fails - unavailable with the error`() = runTest {
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)

        assertEquals(RatesResult.Unavailable(RateFetchError.NoConnection), repository.getRates().ignoringDue())
    }

    @Test
    fun `cache before the announced next update - served as current without network`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis - 1

        val result = repository.getRates().ignoringDue()

        assertEquals(RatesResult.Available(SAMPLE_SNAPSHOT, isStale = false), result)
        assertEquals(0, provider.calls)
    }

    @Test
    fun `cache past the next update - downloads newer rates`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis
        provider.next = RateFetchResult.Success(newerSnapshot)

        val result = repository.getRates().ignoringDue()

        assertEquals(RatesResult.Available(newerSnapshot, isStale = false), result)
        assertEquals(CachedSnapshot(newerSnapshot, nextUpdateMillis), cache.read())
    }

    @Test
    fun `cache past the next update and download fails - cached rates flagged stale`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis + 1
        provider.next = RateFetchResult.Failure(RateFetchError.Timeout)

        val result = repository.getRates().ignoringDue()

        assertEquals(
            RatesResult.Available(SAMPLE_SNAPSHOT, isStale = true, refreshError = RateFetchError.Timeout),
            result,
        )
        assertEquals("a failed refresh must not erase the cache", CachedSnapshot(SAMPLE_SNAPSHOT, FETCHED_AT), cache.read())
    }

    @Test
    fun `never downloads twice within an hour, even past the next update`() = runTest {
        val fetchedJustBefore = nextUpdateMillis - 1000
        cache.write(SAMPLE_SNAPSHOT, fetchedJustBefore)
        now = fetchedJustBefore + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS - 1

        repository.getRates().ignoringDue()
        assertEquals(0, provider.calls)

        now = fetchedJustBefore + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS
        provider.next = RateFetchResult.Success(newerSnapshot)
        repository.getRates().ignoringDue()
        assertEquals(1, provider.calls)
    }

    @Test
    fun `without an announced next update, cache expires after 24 hours`() = runTest {
        val unscheduled = SAMPLE_SNAPSHOT.copy(nextUpdateEpochSeconds = null)
        cache.write(unscheduled, FETCHED_AT)

        now = FETCHED_AT + CachingCurrencyRepository.MAX_AGE_WITHOUT_SCHEDULE_MILLIS - 1
        assertEquals(RatesResult.Available(unscheduled, isStale = false), repository.getRates().ignoringDue())
        assertEquals(0, provider.calls)

        now = FETCHED_AT + CachingCurrencyRepository.MAX_AGE_WITHOUT_SCHEDULE_MILLIS
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        assertEquals(
            RatesResult.Available(unscheduled, isStale = true, refreshError = RateFetchError.NoConnection),
            repository.getRates().ignoringDue(),
        )
    }

    @Test
    fun `clock set before the fetch time triggers a refresh`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = FETCHED_AT - 1
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)

        repository.getRates().ignoringDue()

        assertEquals(1, provider.calls)
    }

    @Test
    fun `cache saved before currencies were added is refreshed even before the next update`() = runTest {
        val oldFourCurrencies = SAMPLE_SNAPSHOT.copy(
            rates = SAMPLE_SNAPSHOT.rates.filterKeys { it in setOf(Currency.MAD, Currency.USD, Currency.EUR, Currency.GBP) },
        )
        cache.write(oldFourCurrencies, FETCHED_AT)
        now = FETCHED_AT + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)

        val result = repository.getRates().ignoringDue()

        assertEquals(RatesResult.Available(SAMPLE_SNAPSHOT, isStale = false), result)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `incomplete cache still respects the one hour minimum between downloads`() = runTest {
        val incomplete = SAMPLE_SNAPSHOT.copy(rates = SAMPLE_SNAPSHOT.rates - Currency.JPY)
        cache.write(incomplete, FETCHED_AT)
        now = FETCHED_AT + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS - 1

        assertEquals(RatesResult.Available(incomplete, isStale = false), repository.getRates().ignoringDue())
        assertEquals(0, provider.calls)
    }

    @Test
    fun `failed refresh of an incomplete but current cache is not reported as stale`() = runTest {
        val incomplete = SAMPLE_SNAPSHOT.copy(rates = SAMPLE_SNAPSHOT.rates - Currency.JPY)
        cache.write(incomplete, FETCHED_AT)
        now = nextUpdateMillis - 1
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)

        val result = repository.getRates().ignoringDue()

        assertEquals(RatesResult.Available(incomplete, isStale = false, refreshError = null), result)
        assertEquals(1, provider.calls)
    }

    // --- Storage write failures ---

    @Test
    fun `download succeeds but saving fails - downloaded rates are still returned as current`() = runTest {
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)
        dataStore.writeFailure = IOException("disk full")

        val result = repository.getRates().ignoringDue()

        assertEquals(RatesResult.Available(SAMPLE_SNAPSHOT, isStale = false), result)
        assertNull("nothing could be saved", cache.read())
    }

    @Test
    fun `saving fails - a newer download replaces the older cache only in memory`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis
        provider.next = RateFetchResult.Success(newerSnapshot)
        dataStore.writeFailure = IOException("disk full")

        assertEquals(RatesResult.Available(newerSnapshot, isStale = false), repository.getRates().ignoringDue())
        assertEquals("the old cache is left intact", CachedSnapshot(SAMPLE_SNAPSHOT, FETCHED_AT), cache.read())

        // Still before the newer snapshot's next update: served from memory, no new download.
        now = nextUpdateMillis + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS
        assertEquals(RatesResult.Available(newerSnapshot, isStale = false), repository.getRates().ignoringDue())
        assertEquals(1, provider.calls)
    }

    @Test
    fun `unsaved download still counts for the one hour minimum between downloads`() = runTest {
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)
        dataStore.writeFailure = IOException("disk full")
        repository.getRates().ignoringDue()

        now = FETCHED_AT + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS - 1
        assertEquals(RatesResult.Available(SAMPLE_SNAPSHOT, isStale = false), repository.getRates().ignoringDue())
        assertEquals(1, provider.calls)
    }

    @Test
    fun `unsaved rates past their next update are refreshed, and stale if that fails`() = runTest {
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)
        dataStore.writeFailure = IOException("disk full")
        repository.getRates().ignoringDue()

        now = nextUpdateMillis
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        assertEquals(
            RatesResult.Available(SAMPLE_SNAPSHOT, isStale = true, refreshError = RateFetchError.NoConnection),
            repository.getRates().ignoringDue(),
        )
        assertEquals(2, provider.calls)
    }

    @Test
    fun `once storage works again the next download is saved`() = runTest {
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)
        dataStore.writeFailure = IOException("disk full")
        repository.getRates().ignoringDue()

        dataStore.writeFailure = null
        now = nextUpdateMillis
        provider.next = RateFetchResult.Success(newerSnapshot)

        assertEquals(RatesResult.Available(newerSnapshot, isStale = false), repository.getRates().ignoringDue())
        assertEquals(CachedSnapshot(newerSnapshot, nextUpdateMillis), cache.read())
    }

    @Test
    fun `cancellation while saving is not swallowed`() = runTest {
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)
        dataStore.writeFailure = CancellationException("cancelled")

        try {
            repository.getRates().ignoringDue()
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
        }
    }

    // --- Refresh deadline (when the rates stop being current) ---

    @Test
    fun `current cache is due at the announced next update`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)

        assertEquals(nextUpdateMillis, repository.getRates().dueAt())
    }

    @Test
    fun `fresh download is due at its next update`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis
        provider.next = RateFetchResult.Success(newerSnapshot)

        assertEquals(checkNotNull(newerSnapshot.nextUpdateEpochSeconds) * 1000, repository.getRates().dueAt())
    }

    @Test
    fun `download just before the next update is due only an hour later`() = runTest {
        now = nextUpdateMillis - 1000
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)

        assertEquals(now + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS, repository.getRates().dueAt())
    }

    @Test
    fun `without an announced next update the cache is due 24 hours after the download`() = runTest {
        cache.write(SAMPLE_SNAPSHOT.copy(nextUpdateEpochSeconds = null), FETCHED_AT)

        assertEquals(FETCHED_AT + CachingCurrencyRepository.MAX_AGE_WITHOUT_SCHEDULE_MILLIS, repository.getRates().dueAt())
    }

    @Test
    fun `stale rates are already due`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis + 5
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)

        assertEquals(now, repository.getRates().dueAt())
    }

    @Test
    fun `unsaved download is due at its next update`() = runTest {
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)
        dataStore.writeFailure = IOException("disk full")
        repository.getRates()

        now = FETCHED_AT + 1
        assertEquals(nextUpdateMillis, repository.getRates().dueAt())
        assertEquals(1, provider.calls)
    }

    /** The other tests compare everything except the refresh deadline, covered above. */
    private fun RatesResult.ignoringDue(): RatesResult =
        if (this is RatesResult.Available) copy(refreshDueAtMillis = null) else this

    private fun RatesResult.dueAt(): Long? = (this as RatesResult.Available).refreshDueAtMillis

    /** A real DataStore whose writes can be made to fail (full disk, I/O error, cancellation). */
    private class FailingWritesDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        var writeFailure: Throwable? = null

        override val data = delegate.data

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writeFailure?.let { throw it }
            return delegate.updateData(transform)
        }
    }

    private class FakeProvider : RateProvider {
        var next: RateFetchResult? = null
        var calls = 0
            private set
        val requestedBases = mutableListOf<Currency>()

        override suspend fun fetchLatestRates(base: Currency): RateFetchResult {
            calls++
            requestedBases += base
            return checkNotNull(next) { "Unexpected network call" }
        }
    }

    private companion object {
        /** 2026-10-05 01:00 UTC, about an hour after the sample snapshot was published. */
        const val FETCHED_AT = 1_791_162_000_000L
    }
}
