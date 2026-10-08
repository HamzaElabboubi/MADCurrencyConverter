package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.FeeInput
import com.elabboubisolution.madconverter.domain.RealCost
import com.elabboubisolution.madconverter.domain.RealCostEstimate
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.ConversionText
import com.elabboubisolution.madconverter.ui.format.PERCENT_SIGN
import com.elabboubisolution.madconverter.ui.format.formatPercent
import com.elabboubisolution.madconverter.ui.format.ltr
import com.elabboubisolution.madconverter.ui.format.normalizeNumericInput
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.ui.theme.tabularFigures
import java.math.BigDecimal

/**
 * Real Cost sheet. Fee changes apply immediately; the chosen fee is saved when a preset is
 * picked ([onFeeCommitted]) or when the sheet closes, never while a custom value is typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealCostSheet(
    conversion: Conversion,
    estimate: RealCostEstimate,
    onFeeChanged: (BigDecimal) -> Unit,
    onFeeCommitted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            onFeeCommitted()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        RealCostContent(conversion, estimate, onFeeChanged, onFeeCommitted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RealCostContent(
    conversion: Conversion,
    estimate: RealCostEstimate,
    onFeeChanged: (BigDecimal) -> Unit,
    onFeeCommitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var customMode by rememberSaveable {
        mutableStateOf(RealCost.PRESETS.none { it.compareTo(estimate.feePercent) == 0 })
    }
    var customText by rememberSaveable {
        // Plain ASCII like typed input (normalized), which the parser accepts.
        mutableStateOf(if (customMode) estimate.feePercent.toPlainString() else "")
    }
    val customInput = if (customMode) RealCost.parseFeePercent(customText) else null
    val showBreakdown = !customMode || customInput is FeeInput.Valid

    Column(
        modifier = modifier
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.real_cost_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = ltr(
                "${ConversionText.amount(conversion.amount, conversion.from)} " +
                    ConversionText.approximate(conversion.convertedAmount, conversion.to),
            ),
            style = MaterialTheme.typography.titleMedium.tabularFigures(),
        )

        Text(
            text = stringResource(R.string.real_cost_fee_presets),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RealCost.PRESETS.forEach { preset ->
                FilterChip(
                    selected = !customMode && preset.compareTo(estimate.feePercent) == 0,
                    onClick = {
                        customMode = false
                        focusManager.clearFocus()
                        onFeeChanged(preset)
                        onFeeCommitted()
                    },
                    label = { Text(formatPercent(preset)) },
                )
            }
            FilterChip(
                selected = customMode,
                onClick = {
                    if (!customMode) {
                        customMode = true
                        customText = estimate.feePercent.toPlainString()
                    }
                },
                label = { Text(stringResource(R.string.fee_custom)) },
            )
        }

        if (customMode) {
            OutlinedTextField(
                value = customText,
                onValueChange = { typed ->
                    val text = normalizeNumericInput(typed)
                    if (text.length > 6) return@OutlinedTextField
                    customText = text
                    (RealCost.parseFeePercent(text) as? FeeInput.Valid)?.let { onFeeChanged(it.percent) }
                },
                label = { Text(stringResource(R.string.fee_custom_label)) },
                suffix = { Text(PERCENT_SIGN) },
                singleLine = true,
                isError = customInput == FeeInput.Invalid || customInput == FeeInput.TooHigh,
                supportingText = {
                    Text(
                        stringResource(
                            R.string.fee_custom_hint,
                            // ASCII like the field itself, which normalizes typed digits.
                            RealCost.MAX_FEE_PERCENT.toPlainString(),
                        ),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showBreakdown) {
            RealCostBreakdown(estimate)
        }

        Text(
            text = stringResource(R.string.real_cost_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RealCostBreakdown(estimate: RealCostEstimate) {
    val percent = ltr(formatPercent(estimate.feePercent))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BreakdownRow(
            label = stringResource(R.string.real_cost_converted),
            value = ltr(ConversionText.money(estimate.convertedAmount, estimate.currency)),
        )
        BreakdownRow(
            label = stringResource(R.string.real_cost_fee, percent),
            value = ltr(ConversionText.money(estimate.fee, estimate.currency)),
        )
        HorizontalDivider()
        BreakdownRow(
            label = stringResource(R.string.real_cost_total),
            value = ltr(ConversionText.money(estimate.total, estimate.currency)),
            emphasized = true,
        )
    }
}

/** Narrowest the label may get beside its amount before the amount moves below it. */
private val MIN_LABEL_WIDTH = 120.dp
private val LABEL_GAP = 12.dp

@Composable
private fun BreakdownRow(label: String, value: String, emphasized: Boolean = false) {
    val baseStyle = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    val weight = if (emphasized) FontWeight.SemiBold else baseStyle.fontWeight
    val labelStyle = baseStyle.copy(fontWeight = weight)
    val valueStyle = baseStyle.tabularFigures().copy(fontWeight = weight)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // The amount never wraps. Beside a label that keeps a readable width when it fits;
        // otherwise (huge amounts, large font sizes) below the label, end-aligned.
        val valueWidth = with(LocalDensity.current) {
            measurer.measure(value, valueStyle, maxLines = 1).size.width.toDp()
        }
        if (valueWidth + LABEL_GAP + MIN_LABEL_WIDTH <= maxWidth) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
            ) {
                Text(text = label, style = labelStyle, modifier = Modifier.weight(1f))
                Text(text = value, style = valueStyle, maxLines = 1)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = label, style = labelStyle)
                Text(
                    text = value,
                    style = valueStyle,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

private val previewConversion =
    Conversion(Currency.EUR, Currency.MAD, BigDecimal("250"), BigDecimal("2492.00"), BigDecimal("9.968"))

@PreviewLightDark
@Composable
private fun RealCostPreview() {
    MADCurrencyConverterTheme {
        Surface {
            RealCostContent(
                conversion = previewConversion,
                estimate = RealCost.estimate(previewConversion, BigDecimal("3")),
                onFeeChanged = {},
                onFeeCommitted = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 320, locale = "fr")
@Composable
private fun RealCostCustomFrenchPreview() {
    MADCurrencyConverterTheme {
        Surface {
            RealCostContent(
                conversion = previewConversion,
                estimate = RealCost.estimate(previewConversion, BigDecimal("2.75")),
                onFeeChanged = {},
                onFeeCommitted = {},
            )
        }
    }
}
