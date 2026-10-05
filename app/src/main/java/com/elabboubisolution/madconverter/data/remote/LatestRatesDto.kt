package com.elabboubisolution.madconverter.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Response of `GET /v6/latest/{base}`. Errors are reported with HTTP 200 and
 * `"result": "error"`, so every field except [result] is optional.
 * Rates are kept as raw JSON primitives so they can be parsed to BigDecimal without
 * going through Double.
 */
@Serializable
data class LatestRatesDto(
    val result: String,
    @SerialName("error-type") val errorType: String? = null,
    @SerialName("base_code") val baseCode: String? = null,
    @SerialName("time_last_update_unix") val timeLastUpdateUnix: Long? = null,
    @SerialName("time_next_update_unix") val timeNextUpdateUnix: Long? = null,
    val rates: Map<String, JsonPrimitive>? = null,
)
