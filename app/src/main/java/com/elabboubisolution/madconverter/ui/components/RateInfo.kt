package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.ConversionText
import com.elabboubisolution.madconverter.ui.format.formatLastUpdated
import com.elabboubisolution.madconverter.ui.format.ltr
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
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
            style = MaterialTheme.typography.titleMedium,
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

@Composable
private fun ProviderAttribution() {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    )
    val text = buildAnnotatedString {
        withLink(LinkAnnotation.Url(PROVIDER_URL, linkStyles)) {
            append(stringResource(R.string.attribution))
        }
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall)
}

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
