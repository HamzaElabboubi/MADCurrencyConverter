package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Pure conversion logic (no Android dependencies). All arithmetic uses [BigDecimal]. */
object CurrencyConverter {

    /** Largest amount accepted from user input, to keep results readable on screen. */
    val MAX_AMOUNT: BigDecimal = BigDecimal("999999999999.99")

    private val MATH_CONTEXT: MathContext = MathContext.DECIMAL128
    private val AMOUNT_PATTERN = Regex("""^(\d+([.,]\d*)?|[.,]\d+)$""")
    private val WHITESPACE = Regex("""[\s  ]""")

    /**
     * Parses a user-typed amount. Either `,` or `.` is accepted as the decimal separator,
     * at most once; spaces (including the non-breaking spaces used in French grouping) are
     * ignored. Grouping separators like "1.000,50" are rejected as ambiguous, as are signs
     * and exponents.
     */
    fun parseAmount(input: String): AmountInput {
        val compact = input.replace(WHITESPACE, "")
        if (compact.isEmpty()) return AmountInput.Empty
        if (!AMOUNT_PATTERN.matches(compact)) return AmountInput.Invalid

        val value = BigDecimal(compact.replace(',', '.'))
        return if (value > MAX_AMOUNT) AmountInput.TooLarge else AmountInput.Valid(value)
    }

    fun convert(
        amount: BigDecimal,
        from: Currency,
        to: Currency,
        snapshot: RateSnapshot,
    ): ConversionResult {
        val fromRate = snapshot.rates[from] ?: return ConversionResult.MissingRate(from)
        val toRate = snapshot.rates[to] ?: return ConversionResult.MissingRate(to)

        val rate = if (from == to) BigDecimal.ONE else toRate.divide(fromRate, MATH_CONTEXT)
        // Multiply before dividing so the only rounding before the final one is the division.
        val exact = if (from == to) amount else amount.multiply(toRate).divide(fromRate, MATH_CONTEXT)

        return ConversionResult.Success(
            Conversion(
                from = from,
                to = to,
                amount = amount,
                convertedAmount = exact.setScale(to.fractionDigits, RoundingMode.HALF_UP),
                rate = rate,
            )
        )
    }
}

sealed interface AmountInput {
    data object Empty : AmountInput
    data object Invalid : AmountInput
    data object TooLarge : AmountInput
    data class Valid(val value: BigDecimal) : AmountInput
}

/**
 * @property convertedAmount rounded half-up to the target currency's minor units.
 * @property rate value of 1 [from] in [to], unrounded (34 significant digits).
 */
data class Conversion(
    val from: Currency,
    val to: Currency,
    val amount: BigDecimal,
    val convertedAmount: BigDecimal,
    val rate: BigDecimal,
)

sealed interface ConversionResult {
    data class Success(val conversion: Conversion) : ConversionResult
    data class MissingRate(val currency: Currency) : ConversionResult
}
