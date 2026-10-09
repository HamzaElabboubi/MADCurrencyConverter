package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.formatDecimal
import com.elabboubisolution.madconverter.ui.format.localizedName
import com.elabboubisolution.madconverter.ui.format.ltr
import com.elabboubisolution.madconverter.ui.theme.Dimens
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.ui.theme.tabularFigures
import java.math.BigDecimal

/** Smallest size a Quick Conversions amount shrinks to (largest amounts at 200% font size). */
private val AMOUNT_MIN_FONT_SIZE = 12.sp

/** Secondary list of the amount converted into favorites; tapping a row makes it the main target. */
@Composable
fun QuickConversionsSection(
    conversions: List<Conversion>,
    onSelect: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.quick_conversions_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            conversions.forEachIndexed { index, conversion ->
                if (index > 0) HorizontalDivider()
                QuickConversionRow(conversion = conversion, onClick = { onSelect(conversion.to) })
            }
        }
    }
}

@Composable
private fun QuickConversionRow(conversion: Conversion, onClick: () -> Unit) {
    val currency = conversion.to
    val name = currency.localizedName()
    val amount = formatDecimal(conversion.convertedAmount, minDigits = currency.fractionDigits)
    val description = stringResource(R.string.quick_conversion_description, currency.code, name, amount)
    val clickLabel = stringResource(R.string.action_make_main)
    // Outer row: the chevron is measured first, so it always keeps its place.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = Dimens.CardPadding, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = currency.code,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .clearAndSetSemantics {},
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {},
            )
            // The name gives way first. Only when the amount alone does not fit (huge amounts
            // at large font sizes) does it shrink, on one line: never clipped or split.
            val amountStyle = MaterialTheme.typography.bodyLarge.tabularFigures()
            Text(
                text = ltr(stringResource(R.string.approx_amount, amount)),
                style = amountStyle,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = AMOUNT_MIN_FONT_SIZE, maxFontSize = amountStyle.fontSize),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        // Tapping makes this the main target. Decorative only: the row already announces
        // its action ("Make main conversion"). Auto-mirrored, so it points to the end in RTL.
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val previewConversions = listOf(
    Conversion(Currency.MAD, Currency.EUR, BigDecimal("1000"), BigDecimal("89.73"), BigDecimal("0.089729")),
    Conversion(Currency.MAD, Currency.AED, BigDecimal("1000"), BigDecimal("368.29"), BigDecimal("0.368289")),
    Conversion(Currency.MAD, Currency.JPY, BigDecimal("1000"), BigDecimal("15840"), BigDecimal("15.839832")),
)

@PreviewLightDark
@Composable
private fun QuickConversionsPreview() {
    MADCurrencyConverterTheme {
        Surface {
            QuickConversionsSection(previewConversions, onSelect = {}, modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 320, locale = "fr")
@Composable
private fun QuickConversionsNarrowFrenchPreview() {
    MADCurrencyConverterTheme {
        Surface {
            QuickConversionsSection(previewConversions, onSelect = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
