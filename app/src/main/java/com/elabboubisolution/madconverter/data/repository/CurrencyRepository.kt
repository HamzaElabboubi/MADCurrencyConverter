package com.elabboubisolution.madconverter.data.repository

import com.elabboubisolution.madconverter.data.local.CachedSnapshot
import com.elabboubisolution.madconverter.data.local.RateCache
import com.elabboubisolution.madconverter.data.remote.RateProvider
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateFetchResult
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import java.io.IOException

sealed interface RatesResult {
    /**
     * @property isStale true when the provider has (or may have) published newer rates that
     *   could not be downloaded; the UI must then say the rates may not be current.
     * @property refreshError why the refresh failed, when [isStale].
     * @property refreshDueAtMillis when these rates stop being current, so [CurrencyRepository.getRates]
     *   would download (the time of the call if that is already the case); null if unknown.
     */
    data class Available(
        val snapshot: RateSnapshot,
        val isStale: Boolean,
        val refreshError: RateFetchError? = null,
        val refreshDueAtMillis: Long? = null,
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
 * A cache lacking some supported currencies (e.g. saved before currencies were added) is also
 * refreshed, without being reported as stale if that download fails.
 * Downloads are never closer than [MIN_FETCH_INTERVAL_MILLIS] apart, as the provider requires.
 * A download that cannot be saved (storage full or failing) is still returned and kept in memory,
 * so it is served and throttled like a cached one for the rest of the process.
 */
class CachingCurrencyRepository(
    private val provider: RateProvider,
    private val cache: RateCache,
    private val clock: () -> Long = System::currentTimeMillis,
) : CurrencyRepository {

    /** The latest download, when saving it failed; cleared once a download is saved. */
    private var unsaved: CachedSnapshot? = null

    override suspend fun getRates(): RatesResult {
        val cached = listOfNotNull(cache.read(), unsaved).maxByOrNull { it.fetchedAtEpochMillis }
        val now = clock()
        val scheduleDue = cached?.isRefreshDue(now) ?: true
        if (cached != null && !scheduleDue && !cached.needsMissingCurrencies(now)) {
            return RatesResult.Available(cached.snapshot, isStale = false, refreshDueAtMillis = cached.refreshDueAtMillis(now))
        }

        return when (val result = provider.fetchLatestRates(BASE)) {
            is RateFetchResult.Success -> {
                save(result.snapshot, fetchedAtEpochMillis = now)
                RatesResult.Available(
                    result.snapshot,
                    isStale = false,
                    refreshDueAtMillis = CachedSnapshot(result.snapshot, now).refreshDueAtMillis(now),
                )
            }
            is RateFetchResult.Failure ->
                if (cached != null) {
                    RatesResult.Available(
                        snapshot = cached.snapshot,
                        isStale = scheduleDue,
                        refreshError = result.error.takeIf { scheduleDue },
                        refreshDueAtMillis = cached.refreshDueAtMillis(now),
                    )
                } else {
                    RatesResult.Unavailable(result.error)
                }
        }
    }

    private suspend fun save(snapshot: RateSnapshot, fetchedAtEpochMillis: Long) {
        unsaved = try {
            cache.write(snapshot, fetchedAtEpochMillis)
            null
        } catch (_: IOException) {
            CachedSnapshot(snapshot, fetchedAtEpochMillis)
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
    if (!isPastMinFetchInterval(nowMillis)) return false
    val nextUpdateMillis = snapshot.nextUpdateEpochSeconds?.times(1000)
    return if (nextUpdateMillis != null) {
        nowMillis >= nextUpdateMillis
    } else {
        sinceFetch >= CachingCurrencyRepository.MAX_AGE_WITHOUT_SCHEDULE_MILLIS
    }
}

/**
 * The first time at which [isRefreshDue] holds: the provider's next update, but never within
 * [CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS] of the last download. [nowMillis] if
 * it already holds. Missing currencies are not included: they are only refreshed on demand.
 */
internal fun CachedSnapshot.refreshDueAtMillis(nowMillis: Long): Long {
    if (isRefreshDue(nowMillis)) return nowMillis
    val scheduled = snapshot.nextUpdateEpochSeconds?.times(1000)
        ?: (fetchedAtEpochMillis + CachingCurrencyRepository.MAX_AGE_WITHOUT_SCHEDULE_MILLIS)
    return maxOf(scheduled, fetchedAtEpochMillis + CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS)
}

/** True when supported currencies are missing from the cache and a download is allowed again. */
internal fun CachedSnapshot.needsMissingCurrencies(nowMillis: Long): Boolean =
    !snapshot.rates.keys.containsAll(Currency.entries) && isPastMinFetchInterval(nowMillis)

private fun CachedSnapshot.isPastMinFetchInterval(nowMillis: Long): Boolean {
    val sinceFetch = nowMillis - fetchedAtEpochMillis
    return sinceFetch < 0 || sinceFetch >= CachingCurrencyRepository.MIN_FETCH_INTERVAL_MILLIS
}
