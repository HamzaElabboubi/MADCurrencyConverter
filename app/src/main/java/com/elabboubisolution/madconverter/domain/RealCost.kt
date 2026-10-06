package com.elabboubisolution.madconverter.domain

import com.elabboubisolution.madconverter.domain.model.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Estimate of what a purchase may cost after a user-chosen bank/card fee. Not a bank quotation.
 *
 * @property convertedAmount the main conversion result as displayed (target currency).
 * @property total [convertedAmount] + [fee], both already rounded, so displayed values add up.
 */
data class RealCostEstimate(
    val currency: Currency,
    val convertedAmount: BigDecimal,
    val feePercent: BigDecimal,
    val fee: BigDecimal,
    val total: BigDecimal,
)

sealed interface FeeInput {
    data object Empty : FeeInput
    data object Invalid : FeeInput
    data object TooHigh : FeeInput
    data class Valid(val percent: BigDecimal) : FeeInput
}

/** Pure Real Cost logic (no Android dependencies). All arithmetic uses [BigDecimal]. */
object RealCost {

    /**
     * Highest accepted fee. Card foreign-transaction fees are typically 0–5% (more with
     * dynamic currency conversion); 20% leaves headroom while rejecting typos such as "30".
     */
    val MAX_FEE_PERCENT: BigDecimal = BigDecimal("20")

    val PRESETS: List<BigDecimal> = listOf(0, 1, 2, 3).map(::BigDecimal)

    private const val MAX_FEE_DECIMALS = 2
    private val ONE_HUNDRED = BigDecimal(100)
    private val FEE_PATTERN = Regex("""^\d{1,3}([.,]\d{0,$MAX_FEE_DECIMALS})?$""")

    /**
     * Parses a custom percentage: digits with `,` or `.` and at most two decimals
     * ("1.5", "2,75"). Signs, exponents, NaN/Infinity and more decimals are invalid.
     */
    fun parseFeePercent(input: String): FeeInput {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return FeeInput.Empty
        if (!FEE_PATTERN.matches(trimmed)) return FeeInput.Invalid
        val value = BigDecimal(trimmed.replace(',', '.'))
        return if (value > MAX_FEE_PERCENT) FeeInput.TooHigh else FeeInput.Valid(value)
    }

    /**
     * fee = convertedAmount × feePercent / 100, rounded half-up to the target currency's minor
     * units (0 for JPY); total = convertedAmount + fee. Uses the already displayed converted
     * amount, so no exchange rate is involved.
     */
    fun estimate(conversion: Conversion, feePercent: BigDecimal): RealCostEstimate {
        require(feePercent.signum() >= 0 && feePercent <= MAX_FEE_PERCENT) { "Fee out of range: $feePercent" }
        val currency = conversion.to
        val fee = conversion.convertedAmount
            .multiply(feePercent)
            .divide(ONE_HUNDRED)
            .setScale(currency.fractionDigits, RoundingMode.HALF_UP)
        return RealCostEstimate(
            currency = currency,
            convertedAmount = conversion.convertedAmount,
            feePercent = feePercent,
            fee = fee,
            total = conversion.convertedAmount.add(fee),
        )
    }
}
