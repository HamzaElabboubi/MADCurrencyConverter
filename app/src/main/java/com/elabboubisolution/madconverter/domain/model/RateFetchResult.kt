package com.elabboubisolution.madconverter.domain.model

sealed interface RateFetchResult {
    data class Success(val snapshot: RateSnapshot) : RateFetchResult
    data class Failure(val error: RateFetchError) : RateFetchResult
}

sealed interface RateFetchError {
    /** The server could not be reached (offline, DNS failure, connection refused or reset). */
    data object NoConnection : RateFetchError

    data object Timeout : RateFetchError

    /** The provider throttled this device (HTTP 429). */
    data object RateLimited : RateFetchError

    /** Non-2xx HTTP status ([httpCode]) or an error reported in the body ([detail]). */
    data class Service(val httpCode: Int?, val detail: String?) : RateFetchError

    /** The body could not be parsed or contains inconsistent values. */
    data object InvalidResponse : RateFetchError

    /** The response is valid but has no rate for any supported currency other than the base. */
    data class RateUnavailable(val missing: List<Currency>) : RateFetchError
}
