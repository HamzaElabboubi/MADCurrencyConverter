package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.ConversionText
import com.elabboubisolution.madconverter.ui.format.formatLastUpdated
import com.elabboubisolution.madconverter.ui.format.ltr
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.ui.theme.tabularFigures
import java.math.BigDecimal

private const val PROVIDER_URL = "https://www.exchangerate-api.com"

/** Exchange rate (4 decimals), provider update time and the attribution required by the provider. */
@Composable
fun RateInfo(
    from: Currency,
    to: Currency,
    rate: BigDecimal,
    lastUpdatedEpochSeconds: Long?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = ltr(ConversionText.rate(from, to, rate)),
            style = MaterialTheme.typography.titleMedium.tabularFigures(),
        )
        if (lastUpdatedEpochSeconds != null) {
            Text(
                text = stringResource(R.string.last_updated, formatLastUpdated(lastUpdatedEpochSeconds)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProviderAttribution()
    }
}

/**
 * The attribution ExchangeRate-API requires, linking to its site. A clickable text rather than an
 * inline link: TalkBack then offers it as an action ("Double-tap to open website"), and the touch
 * target is at least 48dp tall (the text stays at the top, the extra height below it).
 */
@Composable
private fun ProviderAttribution() {
    val uriHandler = LocalUriHandler.current
    Text(
        text = stringResource(R.string.attribution),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.open_website)) {
                // No browser installed: nothing to open, but never crash.
                runCatching { uriHandler.openUri(PROVIDER_URL) }
            }
            .heightIn(min = MIN_TOUCH_TARGET),
    )
}

private val MIN_TOUCH_TARGET = 48.dp

@PreviewLightDark
@Composable
private fun RateInfoPreview() {
    MADCurrencyConverterTheme {
        Surface {
            RateInfo(
                from = Currency.MAD,
                to = Currency.USD,
                rate = BigDecimal("0.100812"),
                lastUpdatedEpochSeconds = 1791158551L,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
