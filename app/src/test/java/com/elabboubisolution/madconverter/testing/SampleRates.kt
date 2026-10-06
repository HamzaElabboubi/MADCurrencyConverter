package com.elabboubisolution.madconverter.testing

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import java.math.BigDecimal

/**
 * Real MAD-based rates from open.er-api.com: MAD/USD/EUR/GBP from 2026-10-05 (the values
 * the original expected results were computed from), the other currencies from 2026-10-06.
 */
object SampleRates {

    val rates: Map<Currency, BigDecimal> = mapOf(
        Currency.MAD to BigDecimal("1"),
        Currency.EUR to BigDecimal("0.089729"),
        Currency.USD to BigDecimal("0.100812"),
        Currency.GBP to BigDecimal("0.076291"),
        Currency.CAD to BigDecimal("0.142842"),
        Currency.CHF to BigDecimal("0.08329"),
        Currency.AED to BigDecimal("0.368289"),
        Currency.SAR to BigDecimal("0.376061"),
        Currency.TRY to BigDecimal("4.927874"),
        Currency.JPY to BigDecimal("15.839832"),
        Currency.CNY to BigDecimal("0.673351"),
    )

    val snapshot = RateSnapshot(
        base = Currency.MAD,
        rates = rates,
        lastUpdatedEpochSeconds = 1_791_158_551L,
        nextUpdateEpochSeconds = 1_791_246_061L,
    )

    /** JSON `rates` object as returned by the API, optionally without some currencies. */
    fun ratesJson(vararg omit: Currency, override: Map<Currency, String> = emptyMap()): String =
        rates.filterKeys { it !in omit }
            .map { (currency, rate) -> "\"${currency.code}\":${override[currency] ?: rate.toPlainString()}" }
            .plus("\"XAU\":0.0001") // a currency the app does not support
            .joinToString(",", prefix = "{", postfix = "}")
}
