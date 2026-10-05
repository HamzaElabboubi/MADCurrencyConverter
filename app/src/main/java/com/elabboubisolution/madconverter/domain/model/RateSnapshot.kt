package com.elabboubisolution.madconverter.domain.model

import java.math.BigDecimal

/**
 * Exchange rates published by the provider at a given time.
 *
 * @property rates value of 1 unit of [base] in each currency (the base itself maps to 1).
 * @property lastUpdatedEpochSeconds when the provider last refreshed these rates.
 * @property nextUpdateEpochSeconds when the provider expects to publish new rates, if known.
 */
data class RateSnapshot(
    val base: Currency,
    val rates: Map<Currency, BigDecimal>,
    val lastUpdatedEpochSeconds: Long,
    val nextUpdateEpochSeconds: Long?,
)
