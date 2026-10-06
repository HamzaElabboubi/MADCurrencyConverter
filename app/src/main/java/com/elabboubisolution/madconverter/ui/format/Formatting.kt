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
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

const val RATE_FRACTION_DIGITS = 4

/**
 * Locale-aware decimal formatting with exactly [minDigits]..[maxDigits] fraction digits.
 * Digits and separators follow the locale (e.g. "2 794,61" in French, Arabic-Indic digits
 * for "ar", Latin digits for "ar-MA"). Presentation only: never parse the result back.
 */
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

/** A percentage such as "2.75%", "2,75 %" (fr) or "٢٫٧٥٪" (ar); [percent] is 2.75 for 2.75%. */
fun formatPercent(percent: BigDecimal, locale: Locale): String {
    val format = NumberFormat.getPercentInstance(locale)
    format.minimumFractionDigits = 0
    format.maximumFractionDigits = 2
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(percent.movePointLeft(2))
}

/** The locale's percent sign, e.g. "%" or "٪". */
fun percentSign(locale: Locale): String = DecimalFormatSymbols.getInstance(locale).percent.toString()

enum class RelativeDay { TODAY, YESTERDAY, EARLIER_OR_LATER }

/** Calendar-day position of [epochMillis] relative to [nowMillis] in [timeZone]. */
fun relativeDay(epochMillis: Long, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): RelativeDay {
    fun dayKey(millis: Long) = Calendar.getInstance(timeZone).run {
        timeInMillis = millis
        get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    }
    val day = dayKey(epochMillis)
    val yesterday = Calendar.getInstance(timeZone).run {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, -1)
        dayKey(timeInMillis)
    }
    return when (day) {
        dayKey(nowMillis) -> RelativeDay.TODAY
        yesterday -> RelativeDay.YESTERDAY
        else -> RelativeDay.EARLIER_OR_LATER
    }
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
fun formatLastUpdated(epochSeconds: Long): String = formatTimestamp(epochSeconds * 1000, showYesterday = false)

/** "Today, 12:45", "Yesterday, 18:20" or a short localized date and time. */
@Composable
fun formatHistoryTimestamp(epochMillis: Long): String = formatTimestamp(epochMillis, showYesterday = true)

/** Dates and times use the app's language and the user's 12/24-hour setting (DateUtils). */
@Composable
private fun formatTimestamp(epochMillis: Long, showYesterday: Boolean): String {
    val context = LocalContext.current
    val time = DateUtils.formatDateTime(context, epochMillis, DateUtils.FORMAT_SHOW_TIME)
    return when (relativeDay(epochMillis, System.currentTimeMillis())) {
        RelativeDay.TODAY -> stringResource(R.string.today_at, time)
        RelativeDay.YESTERDAY if showYesterday -> stringResource(R.string.yesterday_at, time)
        else -> DateUtils.formatDateTime(
            context,
            epochMillis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
}
