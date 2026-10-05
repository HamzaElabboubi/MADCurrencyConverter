package com.elabboubisolution.madconverter.data.remote

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateFetchResult
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.math.BigDecimal

class ErApiRateProvider(private val api: ExchangeRateApi) : RateProvider {

    override suspend fun fetchLatestRates(base: Currency): RateFetchResult {
        val dto = try {
            api.latestRates(base.code)
        } catch (e: CancellationException) {
            throw e
        } catch (_: InterruptedIOException) {
            // Covers SocketTimeoutException (connect/read) and OkHttp's call timeout.
            return RateFetchResult.Failure(RateFetchError.Timeout)
        } catch (_: IOException) {
            return RateFetchResult.Failure(RateFetchError.NoConnection)
        } catch (e: HttpException) {
            val error = if (e.code() == HTTP_TOO_MANY_REQUESTS) {
                RateFetchError.RateLimited
            } else {
                RateFetchError.Service(httpCode = e.code(), detail = null)
            }
            return RateFetchResult.Failure(error)
        } catch (_: SerializationException) {
            return RateFetchResult.Failure(RateFetchError.InvalidResponse)
        }
        return dto.toRateFetchResult(base)
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

internal fun LatestRatesDto.toRateFetchResult(
    requestedBase: Currency,
    required: Collection<Currency> = Currency.entries,
): RateFetchResult {
    when (result) {
        "success" -> Unit
        "error" -> return RateFetchResult.Failure(RateFetchError.Service(httpCode = null, detail = errorType))
        else -> return RateFetchResult.Failure(RateFetchError.InvalidResponse)
    }

    val lastUpdated = timeLastUpdateUnix
    if (baseCode != requestedBase.code || rates == null || lastUpdated == null) {
        return RateFetchResult.Failure(RateFetchError.InvalidResponse)
    }

    val parsed = mutableMapOf<Currency, BigDecimal>()
    val missing = mutableListOf<Currency>()
    for (currency in required) {
        val raw = rates[currency.code]
        if (raw == null) {
            missing += currency
            continue
        }
        parsed[currency] = raw.toPositiveDecimalOrNull()
            ?: return RateFetchResult.Failure(RateFetchError.InvalidResponse)
    }
    if (missing.isNotEmpty()) {
        return RateFetchResult.Failure(RateFetchError.RateUnavailable(missing))
    }

    return RateFetchResult.Success(
        RateSnapshot(
            base = requestedBase,
            rates = parsed,
            lastUpdatedEpochSeconds = lastUpdated,
            nextUpdateEpochSeconds = timeNextUpdateUnix,
        )
    )
}

private fun JsonPrimitive.toPositiveDecimalOrNull(): BigDecimal? {
    if (isString) return null
    val value = content.toBigDecimalOrNull() ?: return null
    return value.takeIf { it.signum() > 0 }
}
