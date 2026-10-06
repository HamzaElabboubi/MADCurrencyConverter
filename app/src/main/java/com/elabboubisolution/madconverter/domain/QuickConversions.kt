package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import java.math.BigDecimal

/** Extra conversions of the current amount into the user's favorite currencies. */
object QuickConversions {

    const val MAX_ITEMS = 3

    /**
     * Converts [amount] from [from] into up to [max] favorites, using [CurrencyConverter] and the
     * same [snapshot] as the main conversion. The source and the main target are excluded, and
     * favorites follow the [Currency] declaration order (the picker order). Favorites without a
     * rate in [snapshot] are skipped so the next eligible favorite can take their place.
     */
    fun convert(
        amount: BigDecimal,
        from: Currency,
        to: Currency,
        favorites: Set<Currency>,
        snapshot: RateSnapshot,
        max: Int = MAX_ITEMS,
    ): List<Conversion> =
        Currency.entries.asSequence()
            .filter { it in favorites && it != from && it != to }
            .mapNotNull { target ->
                (CurrencyConverter.convert(amount, from, target, snapshot) as? ConversionResult.Success)?.conversion
            }
            .take(max)
            .toList()
}
