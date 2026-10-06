package com.elabboubisolution.madconverter.domain.model

import java.math.BigDecimal

/**
 * A conversion the user copied or shared, as calculated at [timestampMillis].
 * [convertedAmount] and [rate] are historical values, never presented as current ones.
 *
 * @property wasStale the rate came from a cache that could not be refreshed at the time.
 */
data class HistoryEntry(
    val id: String,
    val from: Currency,
    val to: Currency,
    val amount: BigDecimal,
    val convertedAmount: BigDecimal,
    val rate: BigDecimal,
    val timestampMillis: Long,
    val wasStale: Boolean,
)
