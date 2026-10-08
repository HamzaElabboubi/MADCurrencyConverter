package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.ConversionText
import com.elabboubisolution.madconverter.ui.format.ltr
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.ui.theme.tabularFigures
import java.math.BigDecimal

/** Smallest size the main result shrinks to; small enough for the largest amounts at 200% font size. */
private val RESULT_MIN_FONT_SIZE = 12.sp

/** Main result, with Copy and Share actions (the only actions that record history). */
@Composable
fun ResultCard(
    conversion: Conversion,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onRealCost: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 8.dp, bottom = 8.dp)) {
            Column(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = ltr(ConversionText.amount(conversion.amount, conversion.from)),
                    style = MaterialTheme.typography.titleMedium.tabularFigures(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                // One line that shrinks to fit: a large result is never split inside the number
                // (e.g. "100,305,000,000." / "00 USD" at 200% font size).
                val resultStyle = MaterialTheme.typography.displaySmall.tabularFigures()
                Text(
                    text = ltr(ConversionText.approximate(conversion.convertedAmount, conversion.to)),
                    style = resultStyle,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = RESULT_MIN_FONT_SIZE,
                        maxFontSize = resultStyle.fontSize,
                    ),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onRealCost != null) {
                    TextButton(
                        onClick = onRealCost,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) { Text(stringResource(R.string.real_cost_open)) }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onCopy) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy),
                        contentDescription = stringResource(R.string.copy_conversion),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = stringResource(R.string.share_conversion),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
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
            onCopy = {},
            onShare = {},
            modifier = Modifier.padding(16.dp),
            onRealCost = {},
        )
    }
}
