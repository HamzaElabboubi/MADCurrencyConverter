package com.elabboubisolution.madconverter.domain.model

/**
 * Currencies supported by the app. Entry names are ISO 4217 codes, so adding a currency
 * only requires a new entry here (plus its display strings in the UI layer).
 */
enum class Currency {
    MAD,
    USD,
    EUR,
    GBP;

    val code: String get() = name

    /** Number of minor-unit digits (2 for all current entries), from the JDK's ISO 4217 data. */
    val fractionDigits: Int get() = java.util.Currency.getInstance(code).defaultFractionDigits

    companion object {
        fun fromCode(code: String): Currency? = entries.firstOrNull { it.code == code }
    }
}
