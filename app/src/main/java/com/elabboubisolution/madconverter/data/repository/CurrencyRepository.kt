package com.elabboubisolution.madconverter.data.repository

import com.elabboubisolution.madconverter.data.local.CachedSnapshot
import com.elabboubisolution.madconverter.data.local.RateCache
import com.elabboubisolution.madconverter.data.remote.RateProvider
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateFetchResult
import com.elabboubisolution.madconverter.domain.model.RateSnapshot

sealed interface RatesResult {
    /**
     * @property isStale true when the provider has (or may have) published newer rates that
     *   could not be downloaded; the UI must then say the rates may not be current.
     * @property refreshError why the refresh failed, when [isStale].
     */
    data class Available(
        val snapshot: RateSnapshot,
        val isStale: Boolean,
        val refreshError: RateFetchError? = null,
    ) : RatesResult

    /** No rates at all: nothing cached and the download failed. */
    data class Unavailable(val error: RateFetchError) : RatesResult
}

interface CurrencyRepository {
    suspend fun getRates(): RatesResult
}

/**
 * Serves cached rates while the provider has nothing newer, downloads otherwise, and falls back
 * to the cache, flagged as stale, when the download fails.
 *
 * Cached rates count as current until the provider's announced next update
 * ([RateSnapshot.nextUpdateEpochSeconds], or [MAX_AGE_WITHOUT_SCHEDULE_MILLIS] if unknown).
 * Downloads are never closer than [MIN_FETCH_INTERVAL_MILLIS] apart, as the provider requires.
 */
class CachingCurrencyRepository(
    private val provider: RateProvider,
    private val cache: RateCache,
    private val clock: () -> Long = System::currentTimeMillis,
) : CurrencyRepository {

    override suspend fun getRates(): RatesResult {
        val cached = cache.read()
        val now = clock()
        if (cached != null && !cached.isRefreshDue(now)) {
            return RatesResult.Available(cached.snapshot, isStale = false)
        }

        return when (val result = provider.fetchLatestRates(BASE)) {
            is RateFetchResult.Success -> {
                cache.write(result.snapshot, fetchedAtEpochMillis = now)
                RatesResult.Available(result.snapshot, isStale = false)
            }
            is RateFetchResult.Failure ->
                if (cached != null) {
                    RatesResult.Available(cached.snapshot, isStale = true, refreshError = result.error)
                } else {
                    RatesResult.Unavailable(result.error)
                }
        }
    }

    companion object {
        /** One download with this base covers every supported pair. */
        val BASE = Currency.MAD
        const val MIN_FETCH_INTERVAL_MILLIS = 60 * 60 * 1000L
        const val MAX_AGE_WITHOUT_SCHEDULE_MILLIS = 24 * 60 * 60 * 1000L
    }
}

internal fun CachedSnapshot.isRefreshDue(nowMillis: Long): Boolean {
    val sinceFetch = nowMillis - fetchedAtEpochMillis
    // A clock set backwards makes the fetch time unreliable: refresh.
    if (sinceFetch < 0) return true
    if (sinceFetch < CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS) return false
    val nextUpdateMillis = snapshot.nextUpdateEpochSeconds?.times(1000)
    return if (nextUpdateMillis != null) {
        nowMillis >= nextUpdateMillis
    } else {
        sinceFetch >= CachingCurrencyRepository.MAX_AGE_WITHOUT_SCHEDULE_MILLIS
    }
}
