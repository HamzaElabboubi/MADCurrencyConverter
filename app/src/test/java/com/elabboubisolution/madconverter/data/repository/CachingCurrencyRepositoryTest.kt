package com.elabboubisolution.madconverter.data.repository

import com.elabboubisolution.madconverter.data.local.CachedSnapshot
import com.elabboubisolution.madconverter.data.local.RateCache
import com.elabboubisolution.madconverter.data.local.RateCacheTest.Companion.SAMPLE_SNAPSHOT
import com.elabboubisolution.madconverter.data.local.RateCacheTest.Companion.testDataStore
import com.elabboubisolution.madconverter.data.remote.RateProvider
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateFetchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CachingCurrencyRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
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
        cache = RateCache(testDataStore(scope, File(tmp.root, "rates.preferences_pb")), Json)
        repository = CachingCurrencyRepository(provider, cache, clock = { now })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `no cache - downloads, returns current rates and caches them`() = runTest {
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)

        val result = repository.getRates()

        assertEquals(RatesResult.Available(SAMPLE_SNAPSHOT, isStale = false), result)
        assertEquals(CachedSnapshot(SAMPLE_SNAPSHOT, FETCHED_AT), cache.read())
        assertEquals(listOf(Currency.MAD), provider.requestedBases)
    }

    @Test
    fun `no cache and download fails - unavailable with the error`() = runTest {
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)

        assertEquals(RatesResult.Unavailable(RateFetchError.NoConnection), repository.getRates())
    }

    @Test
    fun `cache before the announced next update - served as current without network`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis - 1

        val result = repository.getRates()

        assertEquals(RatesResult.Available(SAMPLE_SNAPSHOT, isStale = false), result)
        assertEquals(0, provider.calls)
    }

    @Test
    fun `cache past the next update - downloads newer rates`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis
        provider.next = RateFetchResult.Success(newerSnapshot)

        val result = repository.getRates()

        assertEquals(RatesResult.Available(newerSnapshot, isStale = false), result)
        assertEquals(CachedSnapshot(newerSnapshot, nextUpdateMillis), cache.read())
    }

    @Test
    fun `cache past the next update and download fails - cached rates flagged stale`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = nextUpdateMillis + 1
        provider.next = RateFetchResult.Failure(RateFetchError.Timeout)

        val result = repository.getRates()

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

        repository.getRates()
        assertEquals(0, provider.calls)

        now = fetchedJustBefore + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS
        provider.next = RateFetchResult.Success(newerSnapshot)
        repository.getRates()
        assertEquals(1, provider.calls)
    }

    @Test
    fun `without an announced next update, cache expires after 24 hours`() = runTest {
        val unscheduled = SAMPLE_SNAPSHOT.copy(nextUpdateEpochSeconds = null)
        cache.write(unscheduled, FETCHED_AT)

        now = FETCHED_AT + CachingCurrencyRepository.MAX_AGE_WITHOUT_SCHEDULE_MILLIS - 1
        assertEquals(RatesResult.Available(unscheduled, isStale = false), repository.getRates())
        assertEquals(0, provider.calls)

        now = FETCHED_AT + CachingCurrencyRepository.MAX_AGE_WITHOUT_SCHEDULE_MILLIS
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        assertEquals(
            RatesResult.Available(unscheduled, isStale = true, refreshError = RateFetchError.NoConnection),
            repository.getRates(),
        )
    }

    @Test
    fun `clock set before the fetch time triggers a refresh`() = runTest {
        cache.write(SAMPLE_SNAPSHOT, FETCHED_AT)
        now = FETCHED_AT - 1
        provider.next = RateFetchResult.Success(SAMPLE_SNAPSHOT)

        repository.getRates()

        assertEquals(1, provider.calls)
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
