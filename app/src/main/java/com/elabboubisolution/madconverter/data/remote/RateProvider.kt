package com.elabboubisolution.madconverter.data.remote

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchResult

/** Source of live exchange rates. Implementations must not throw except for cancellation. */
interface RateProvider {
    suspend fun fetchLatestRates(base: Currency): RateFetchResult
}
