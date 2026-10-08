package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.CurrencyConverter
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.formatDecimal
import com.elabboubisolution.madconverter.ui.format.normalizeNumericInput
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.viewmodel.AmountError

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: Currency,
    error: AmountError?,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(normalizeNumericInput(it)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.amount_label)) },
        suffix = { Text(currency.code) },
        textStyle = MaterialTheme.typography.headlineSmall,
        singleLine = true,
        isError = error != null,
        supportingText = error?.let {
            {
                Text(
                    when (it) {
                        AmountError.INVALID -> stringResource(R.string.error_invalid_amount)
                        AmountError.TOO_LARGE -> stringResource(
                            R.string.error_amount_too_large,
                            formatDecimal(CurrencyConverter.MAX_AMOUNT, minDigits = 2),
                        )
                    }
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

@PreviewLightDark
@Composable
private fun AmountFieldPreview() {
    MADCurrencyConverterTheme {
        Surface {
            AmountField("1000", {}, Currency.MAD, error = null, modifier = Modifier.padding(16.dp))
        }
    }
}

@PreviewLightDark
@Composable
private fun AmountFieldInvalidPreview() {
    MADCurrencyConverterTheme {
        Surface {
            AmountField("12a", {}, Currency.MAD, error = AmountError.INVALID, modifier = Modifier.padding(16.dp))
        }
    }
}

@PreviewLightDark
@Composable
private fun AmountFieldTooLargePreview() {
    MADCurrencyConverterTheme {
        Surface {
            AmountField("5000000000000", {}, Currency.MAD, AmountError.TOO_LARGE, Modifier.padding(16.dp))
        }
    }
}
