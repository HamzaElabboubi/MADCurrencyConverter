package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.localizedName
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme

/** Shows the selected currency (code + name) and opens the currency picker when tapped. */
@Composable
fun CurrencySelectorField(
    label: String,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = currency.localizedName()
    val description = stringResource(R.string.change_currency, label, currency.code, name)
    val clickLabel = stringResource(R.string.action_change_currency)
    OutlinedCard(
        modifier = modifier
            .clip(CardDefaults.outlinedShape)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = currency.code,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(painter = painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
        }
    }
}

@PreviewLightDark
@Composable
private fun CurrencySelectorFieldPreview() {
    MADCurrencyConverterTheme {
        Surface {
            CurrencySelectorField(
                label = "To",
                currency = Currency.AED,
                onClick = {},
                modifier = Modifier.padding(16.dp).width(170.dp),
            )
        }
    }
}
