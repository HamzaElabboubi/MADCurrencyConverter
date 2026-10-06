package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import java.text.Normalizer
import java.util.Locale

/** Currencies to list in the picker: favorites first, then every other match. */
data class CurrencyListing(
    val favorites: List<Currency>,
    val others: List<Currency>,
) {
    val isEmpty: Boolean get() = favorites.isEmpty() && others.isEmpty()
}

/** Pure search logic for the currency picker (no Android dependencies). */
object CurrencySearch {

    private val DIACRITICS = Regex("\\p{Mn}+")

    /**
     * Matches [query] against the ISO code, the name in [locale] and the English name, ignoring
     * case and accents ("etats" finds "Dollar des États-Unis", "pound" finds GBP in French).
     * Better matches come first: exact code, code prefix, name word prefix, then anywhere.
     * Ties keep the [Currency] declaration order.
     */
    fun search(
        query: String,
        locale: Locale,
        favorites: Set<Currency>,
        currencies: List<Currency> = Currency.entries,
    ): CurrencyListing {
        val needle = normalize(query)
        val matches = if (needle.isEmpty()) {
            currencies
        } else {
            currencies
                .mapNotNull { currency -> rank(currency, needle, locale)?.let { currency to it } }
                .sortedBy { it.second }
                .map { it.first }
        }
        return CurrencyListing(
            favorites = matches.filter { it in favorites },
            others = matches.filterNot { it in favorites },
        )
    }

    private fun rank(currency: Currency, needle: String, locale: Locale): Int? {
        val code = currency.code.lowercase(Locale.ROOT)
        val names = listOf(currency.displayName(locale), currency.displayName(Locale.ENGLISH)).map(::normalize)
        return when {
            code == needle -> 0
            code.startsWith(needle) -> 1
            names.any { name -> name.split(' ', '-').any { it.startsWith(needle) } } -> 2
            names.any { needle in it } -> 3
            else -> null
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
}
