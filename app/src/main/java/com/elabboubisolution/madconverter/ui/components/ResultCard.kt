package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.currentLocale
import com.elabboubisolution.madconverter.ui.format.formatDecimal
import com.elabboubisolution.madconverter.ui.format.formatTypedAmount
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import java.math.BigDecimal

@Composable
fun ResultCard(conversion: Conversion, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = stringResource(
                    R.string.source_amount,
                    formatTypedAmount(conversion.amount, locale),
                    conversion.from.code,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(
                    R.string.converted_amount,
                    formatDecimal(conversion.convertedAmount, locale, minDigits = conversion.to.fractionDigits),
                    conversion.to.code,
                ),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ResultCardPreview() {
    MADCurrencyConverterTheme {
        ResultCard(
            conversion = Conversion(
                from = Currency.MAD,
                to = Currency.USD,
                amount = BigDecimal("1000"),
                convertedAmount = BigDecimal("100.81"),
                rate = BigDecimal("0.100812"),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
