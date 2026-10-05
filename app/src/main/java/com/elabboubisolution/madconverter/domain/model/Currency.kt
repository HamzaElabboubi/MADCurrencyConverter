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

    companion object {
        fun fromCode(code: String): Currency? = entries.firstOrNull { it.code == code }
    }
}
