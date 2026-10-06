package com.elabboubisolution.madconverter.domain.model

import java.util.Locale

/**
 * Currencies supported by the app, in the order they are listed in the picker.
 *
 * Entry names are ISO 4217 codes. Display names and minor-unit digits come from the platform's
 * ISO/CLDR data (localized for free); only the short symbols are curated here because the
 * platform's symbols are inconsistent across locales. Adding a currency only requires a new
 * entry, provided ExchangeRate-API publishes it.
 *
 * @property symbol conventional short symbol, or null when the ISO code is what people use.
 */
enum class Currency(val symbol: String?) {
    MAD("DH"),
    EUR("€"),
    USD("$"),
    GBP("£"),
    CAD("CA$"),
    CHF(null),
    AED(null),
    SAR(null),
    TRY("₺"),
    JPY("¥"),
    CNY("CN¥");

    val code: String get() = name

    /** Number of minor-unit digits (e.g. 2 for MAD, 0 for JPY), from ISO 4217 data. */
    val fractionDigits: Int get() = javaCurrency.defaultFractionDigits

    /** Localized name with a capitalized first letter, e.g. "Moroccan Dirham" / "Dirham marocain". */
    fun displayName(locale: Locale): String =
        javaCurrency.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }

    private val javaCurrency: java.util.Currency get() = java.util.Currency.getInstance(code)

    companion object {
        fun fromCode(code: String): Currency? = entries.firstOrNull { it.code == code }
    }
}
