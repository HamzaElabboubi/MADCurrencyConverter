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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

const val RATE_FRACTION_DIGITS = 4

/** The percent sign used with every percentage, in every language. */
const val PERCENT_SIGN = "%"

/**
 * The app's single numeric format, identical in English, French and Arabic: ASCII digits 0-9,
 * "." as the decimal separator and "," for thousands, e.g. "1,250.50". Locale-specific digits
 * and separators are deliberately not used. Fixed symbols, so the result never depends on the
 * device or app language.
 */
private fun westernSymbols(): DecimalFormatSymbols = DecimalFormatSymbols(Locale.ROOT).apply {
    zeroDigit = '0'
    decimalSeparator = '.'
    groupingSeparator = ','
    minusSign = '-'
}

/**
 * Decimal formatting with exactly [minDigits]..[maxDigits] fraction digits, e.g. "2,794.61".
 * Presentation only: never parse the result back.
 */
fun formatDecimal(value: BigDecimal, minDigits: Int, maxDigits: Int = minDigits): String {
    val format = DecimalFormat("#,##0", westernSymbols())
    format.minimumFractionDigits = minDigits
    format.maximumFractionDigits = maxDigits
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(value)
}

/**
 * Formats a typed amount with exactly the decimals the user entered, never adding or dropping
 * any: "1250.50" -> "1,250.50", "1250.00" -> "1,250.00", "1250.5" -> "1,250.5", "1250" -> "1,250".
 * Relies on the [BigDecimal] scale, which parsing, conversion and history storage all keep.
 */
fun formatTypedAmount(value: BigDecimal): String {
    val digits = value.scale().coerceAtLeast(0)
    return formatDecimal(value, minDigits = digits, maxDigits = digits)
}

/** A percentage such as "2.75%" or "3%"; [percent] is 2.75 for 2.75%. */
fun formatPercent(percent: BigDecimal): String = formatDecimal(percent, minDigits = 0, maxDigits = 2) + PERCENT_SIGN

/**
 * Replaces any non-ASCII decimal digit (Arabic-Indic, Eastern Arabic-Indic, ...) with 0-9 and
 * leaves every other character alone. Used for platform-formatted dates and times.
 */
fun asciiDigits(text: String): String = buildString(text.length) {
    for (c in text) {
        val digit = Character.digit(c, 10)
        append(if (digit >= 0 && c !in '0'..'9') '0' + digit else c)
    }
}

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

/**
 * Dates and times use the app's language and the user's 12/24-hour setting (DateUtils),
 * with Western digits like every other number in the app.
 */
@Composable
private fun formatTimestamp(epochMillis: Long, showYesterday: Boolean): String {
    val context = LocalContext.current
    val time = DateUtils.formatDateTime(context, epochMillis, DateUtils.FORMAT_SHOW_TIME)
    val text = when (relativeDay(epochMillis, System.currentTimeMillis())) {
        RelativeDay.TODAY -> stringResource(R.string.today_at, time)
        RelativeDay.YESTERDAY if showYesterday -> stringResource(R.string.yesterday_at, time)
        else -> DateUtils.formatDateTime(
            context,
            epochMillis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
    return asciiDigits(text)
}
