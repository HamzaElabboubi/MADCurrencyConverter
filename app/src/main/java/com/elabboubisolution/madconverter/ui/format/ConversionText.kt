package com.elabboubisolution.madconverter.ui.format

import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import java.math.BigDecimal

/**
 * Text of a conversion, shared by the screen and by Copy/Share so both always show the same
 * values. Pure JVM (no Android dependencies), in the app's single numeric format (see
 * [formatDecimal]), so the text is the same in every language.
 */
object ConversionText {

    /** Amount as typed, e.g. "1,000 MAD". */
    fun amount(value: BigDecimal, currency: Currency): String =
        "${formatTypedAmount(value)} ${currency.code}"

    /** Result in the currency's minor units, e.g. "100.81 USD". */
    fun money(value: BigDecimal, currency: Currency): String =
        "${formatDecimal(value, minDigits = currency.fractionDigits)} ${currency.code}"

    /** e.g. "≈ 100.81 USD". */
    fun approximate(value: BigDecimal, currency: Currency): String =
        "≈ ${money(value, currency)}"

    /** e.g. "1 MAD = 0.1008 USD". */
    fun rate(from: Currency, to: Currency, rate: BigDecimal): String =
        "1 ${from.code} = ${formatDecimal(rate, minDigits = RATE_FRACTION_DIGITS)} ${to.code}"

    /** Plain text for Copy and Share: "1,000 MAD ≈ 100.81 USD" then "1 MAD = 0.1008 USD". */
    fun shareText(conversion: Conversion): String =
        "${amount(conversion.amount, conversion.from)} " +
            "${approximate(conversion.convertedAmount, conversion.to)}\n" +
            rate(conversion.from, conversion.to, conversion.rate)
}
