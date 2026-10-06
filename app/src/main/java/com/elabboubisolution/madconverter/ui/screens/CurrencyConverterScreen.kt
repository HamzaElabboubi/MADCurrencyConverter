package com.elabboubisolution.madconverter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.ui.components.AmountField
import com.elabboubisolution.madconverter.ui.components.CurrencyPickerSheet
import com.elabboubisolution.madconverter.ui.components.CurrencySelectorField
import com.elabboubisolution.madconverter.ui.components.ErrorState
import com.elabboubisolution.madconverter.ui.components.HintMessage
import com.elabboubisolution.madconverter.ui.components.LoadingState
import com.elabboubisolution.madconverter.ui.components.QuickConversionsSection
import com.elabboubisolution.madconverter.ui.components.RateInfo
import com.elabboubisolution.madconverter.ui.components.ResultCard
import com.elabboubisolution.madconverter.ui.components.StaleRateBanner
import com.elabboubisolution.madconverter.ui.components.SwapButton
import com.elabboubisolution.madconverter.ui.format.message
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.viewmodel.AmountError
import com.elabboubisolution.madconverter.viewmodel.ConverterUiState
import com.elabboubisolution.madconverter.viewmodel.CurrencyConverterViewModel
import java.math.BigDecimal

@Composable
fun CurrencyConverterScreen(
    modifier: Modifier = Modifier,
    viewModel: CurrencyConverterViewModel = viewModel(factory = CurrencyConverterViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CurrencyConverterContent(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onFromSelected = viewModel::onFromCurrencySelected,
        onToSelected = viewModel::onToCurrencySelected,
        onSwap = viewModel::onSwapCurrencies,
        onRetry = viewModel::onRetry,
        onToggleFavorite = viewModel::onToggleFavorite,
        onQuickConversionSelected = viewModel::onQuickConversionSelected,
        modifier = modifier,
    )
}

private enum class PickerSide { FROM, TO }

@Composable
fun CurrencyConverterContent(
    state: ConverterUiState,
    onAmountChange: (String) -> Unit,
    onFromSelected: (Currency) -> Unit,
    onToSelected: (Currency) -> Unit,
    onSwap: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: (Currency) -> Unit,
    onQuickConversionSelected: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerSide by rememberSaveable { mutableStateOf<PickerSide?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )

            AmountField(
                value = state.amountInput,
                onValueChange = onAmountChange,
                currency = state.from,
                error = state.amountError,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CurrencySelectorField(
                    label = stringResource(R.string.from_label),
                    currency = state.from,
                    onClick = { pickerSide = PickerSide.FROM },
                    modifier = Modifier.weight(1f),
                )
                SwapButton(onClick = onSwap)
                CurrencySelectorField(
                    label = stringResource(R.string.to_label),
                    currency = state.to,
                    onClick = { pickerSide = PickerSide.TO },
                    modifier = Modifier.weight(1f),
                )
            }

            ConversionSection(state = state, onRetry = onRetry, onQuickConversionSelected = onQuickConversionSelected)
        }
    }

    pickerSide?.let { side ->
        CurrencyPickerSheet(
            title = stringResource(
                if (side == PickerSide.FROM) R.string.choose_from_currency else R.string.choose_to_currency,
            ),
            selected = if (side == PickerSide.FROM) state.from else state.to,
            favorites = state.favorites,
            onSelect = if (side == PickerSide.FROM) onFromSelected else onToSelected,
            onToggleFavorite = onToggleFavorite,
            onDismiss = { pickerSide = null },
        )
    }
}

@Composable
private fun ConversionSection(
    state: ConverterUiState,
    onRetry: () -> Unit,
    onQuickConversionSelected: (Currency) -> Unit,
) {
    if (!state.hasRates) {
        when {
            state.isLoading -> LoadingState()
            state.error != null -> ErrorState(message = state.error.message(), onRetry = onRetry)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.isStale) {
            StaleRateBanner(
                reason = state.refreshError?.message(),
                isRefreshing = state.isLoading,
                onRetry = onRetry,
            )
        }
        if (state.missingRate != null) {
            ErrorState(
                message = stringResource(R.string.error_missing_rate, state.missingRate.code),
                onRetry = onRetry,
            )
            return@Column
        }
        when {
            state.result != null -> ResultCard(conversion = state.result)
            // Invalid or too-large amounts are explained under the field itself.
            state.amountError == null -> HintMessage(stringResource(R.string.enter_amount_hint))
        }
        state.rate?.let { rate ->
            RateInfo(
                from = state.from,
                to = state.to,
                rate = rate,
                lastUpdatedEpochSeconds = state.lastUpdatedEpochSeconds,
            )
        }
        // Hidden without a result or without eligible favorites: never zero-value rows.
        if (state.quickConversions.isNotEmpty()) {
            QuickConversionsSection(
                conversions = state.quickConversions,
                onSelect = onQuickConversionSelected,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val previewConverted = ConverterUiState(
    amountInput = "1000",
    isLoading = false,
    rate = BigDecimal("0.100812"),
    result = Conversion(Currency.MAD, Currency.USD, BigDecimal("1000"), BigDecimal("100.81"), BigDecimal("0.100812")),
    lastUpdatedEpochSeconds = 1791158551L,
    quickConversions = listOf(
        Conversion(Currency.MAD, Currency.EUR, BigDecimal("1000"), BigDecimal("89.73"), BigDecimal("0.089729")),
        Conversion(Currency.MAD, Currency.GBP, BigDecimal("1000"), BigDecimal("76.29"), BigDecimal("0.076291")),
        Conversion(Currency.MAD, Currency.AED, BigDecimal("1000"), BigDecimal("368.29"), BigDecimal("0.368289")),
    ),
)

@Composable
private fun ContentPreview(state: ConverterUiState) {
    MADCurrencyConverterTheme {
        Surface {
            CurrencyConverterContent(state, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@PreviewLightDark
@Composable
private fun ConvertedPreview() = ContentPreview(previewConverted)

@PreviewLightDark
@Composable
private fun LoadingPreview() = ContentPreview(ConverterUiState(amountInput = "1000", isLoading = true))

@PreviewLightDark
@Composable
private fun NetworkErrorPreview() =
    ContentPreview(ConverterUiState(amountInput = "1000", isLoading = false, error = RateFetchError.NoConnection))

@Preview(showBackground = true)
@Composable
private fun EmptyAmountPreview() =
    ContentPreview(previewConverted.copy(amountInput = "", result = null, quickConversions = emptyList()))

@Preview(showBackground = true)
@Composable
private fun InvalidAmountPreview() =
    ContentPreview(previewConverted.copy(amountInput = "12a", result = null, quickConversions = emptyList(), amountError = AmountError.INVALID))

@Preview(showBackground = true)
@Composable
private fun MissingRatePreview() =
    ContentPreview(previewConverted.copy(to = Currency.GBP, rate = null, result = null, quickConversions = emptyList(), missingRate = Currency.GBP))

@PreviewLightDark
@Composable
private fun StalePreview() =
    ContentPreview(previewConverted.copy(isStale = true, refreshError = RateFetchError.NoConnection))

@Preview(showBackground = true, widthDp = 320, locale = "fr")
@Composable
private fun NarrowFrenchPreview() = ContentPreview(previewConverted.copy(amountInput = "1234,56"))
