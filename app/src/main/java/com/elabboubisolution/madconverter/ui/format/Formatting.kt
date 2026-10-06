package com.elabboubisolution.madconverter.ui.format

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

const val RATE_FRACTION_DIGITS = 4

/** Locale-aware decimal formatting with exactly [minDigits]..[maxDigits] fraction digits. */
fun formatDecimal(value: BigDecimal, locale: Locale, minDigits: Int, maxDigits: Int = minDigits): String {
    val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
    format.minimumFractionDigits = minDigits
    format.maximumFractionDigits = maxDigits
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(value)
}

/** Formats a typed amount without adding or dropping the decimals the user entered. */
fun formatTypedAmount(value: BigDecimal, locale: Locale): String {
    val digits = value.scale().coerceAtLeast(0)
    return formatDecimal(value, locale, minDigits = 0, maxDigits = digits)
}

@Composable
fun currentLocale(): Locale = LocalLocale.current.platformLocale

/** Currency name in the current UI language. */
@Composable
fun Currency.localizedName(): String = displayName(currentLocale())

@Composable
fun RateFetchError.message(): String = when (this) {
    RateFetchError.NoConnection -> stringResource(R.string.error_no_connection)
    RateFetchError.Timeout -> stringResource(R.string.error_timeout)
    RateFetchError.RateLimited -> stringResource(R.string.error_rate_limited)
    is RateFetchError.Service -> stringResource(R.string.error_service)
    RateFetchError.InvalidResponse -> stringResource(R.string.error_invalid_response)
    is RateFetchError.RateUnavailable -> stringResource(R.string.error_rates_unavailable)
}

/** "Today, 10:30" for today, otherwise a short localized date and time. */
@Composable
fun formatLastUpdated(epochSeconds: Long): String {
    val context = LocalContext.current
    val millis = epochSeconds * 1000
    return if (DateUtils.isToday(millis)) {
        stringResource(R.string.today_at, DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME))
    } else {
        DateUtils.formatDateTime(
            context,
            millis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
}

/** "Today, 12:45", "Yesterday, 18:20" or a short localized date and time. */
@Composable
fun formatHistoryTimestamp(epochMillis: Long): String {
    val context = LocalContext.current
    val time = DateUtils.formatDateTime(context, epochMillis, DateUtils.FORMAT_SHOW_TIME)
    return when {
        DateUtils.isToday(epochMillis) -> stringResource(R.string.today_at, time)
        DateUtils.isToday(epochMillis + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.yesterday_at, time)
        else -> DateUtils.formatDateTime(
            context,
            epochMillis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
}
