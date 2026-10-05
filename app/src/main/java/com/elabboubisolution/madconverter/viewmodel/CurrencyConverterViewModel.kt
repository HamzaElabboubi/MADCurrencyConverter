package com.elabboubisolution.madconverter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elabboubisolution.madconverter.MadConverterApplication
import com.elabboubisolution.madconverter.data.remote.RateProvider
import com.elabboubisolution.madconverter.domain.AmountInput
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.ConversionResult
import com.elabboubisolution.madconverter.domain.CurrencyConverter
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateFetchResult
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

enum class AmountError { INVALID, TOO_LARGE }

/**
 * Everything the converter screen renders. Fields after [to] are derived from the inputs
 * and the loaded rates; only the ViewModel computes them.
 *
 * @property error why rates could not be loaded (null while loading or once loaded).
 * @property missingRate a selected currency the loaded rates do not cover.
 * @property rate value of 1 [from] in [to], unrounded.
 * @property result conversion of a valid, non-empty amount.
 */
data class ConverterUiState(
    val amountInput: String = "",
    val from: Currency = Currency.MAD,
    val to: Currency = Currency.USD,
    val isLoading: Boolean = true,
    val error: RateFetchError? = null,
    val amountError: AmountError? = null,
    val missingRate: Currency? = null,
    val rate: BigDecimal? = null,
    val result: Conversion? = null,
    val lastUpdatedEpochSeconds: Long? = null,
)

class CurrencyConverterViewModel(
    private val rateProvider: RateProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private var snapshot: RateSnapshot? = null
    private var loadJob: Job? = null

    init {
        loadRates()
    }

    fun onAmountChange(input: String) {
        if (input.length > MAX_INPUT_LENGTH) return
        updateState { it.copy(amountInput = input) }
    }

    /** Picking the currency already on the other side swaps the two. */
    fun onFromCurrencySelected(currency: Currency) = updateState {
        if (currency == it.to) it.copy(from = currency, to = it.from) else it.copy(from = currency)
    }

    fun onToCurrencySelected(currency: Currency) = updateState {
        if (currency == it.from) it.copy(from = it.to, to = currency) else it.copy(to = currency)
    }

    fun onSwapCurrencies() = updateState { it.copy(from = it.to, to = it.from) }

    fun onRetry() = loadRates()

    private fun loadRates() {
        if (loadJob?.isActive == true) return
        updateState { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = rateProvider.fetchLatestRates(RATES_BASE)) {
                is RateFetchResult.Success -> {
                    snapshot = result.snapshot
                    updateState { it.copy(isLoading = false, error = null) }
                }
                is RateFetchResult.Failure -> updateState { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    private fun updateState(transform: (ConverterUiState) -> ConverterUiState) {
        _uiState.update { withDerivedFields(transform(it), snapshot) }
    }

    companion object {
        /** One request with this base covers every supported pair. */
        private val RATES_BASE = Currency.MAD
        private const val MAX_INPUT_LENGTH = 24

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MadConverterApplication
                CurrencyConverterViewModel(app.container.rateProvider)
            }
        }
    }
}

internal fun withDerivedFields(state: ConverterUiState, snapshot: RateSnapshot?): ConverterUiState {
    val amount = CurrencyConverter.parseAmount(state.amountInput)
    val amountError = when (amount) {
        AmountInput.Invalid -> AmountError.INVALID
        AmountInput.TooLarge -> AmountError.TOO_LARGE
        AmountInput.Empty, is AmountInput.Valid -> null
    }
    val base = state.copy(
        amountError = amountError,
        missingRate = null,
        rate = null,
        result = null,
        lastUpdatedEpochSeconds = snapshot?.lastUpdatedEpochSeconds,
    )
    if (snapshot == null) return base

    return when (val unit = CurrencyConverter.convert(BigDecimal.ONE, state.from, state.to, snapshot)) {
        is ConversionResult.MissingRate -> base.copy(missingRate = unit.currency)
        is ConversionResult.Success -> base.copy(
            rate = unit.conversion.rate,
            result = (amount as? AmountInput.Valid)?.let {
                (CurrencyConverter.convert(it.value, state.from, state.to, snapshot) as? ConversionResult.Success)
                    ?.conversion
            },
        )
    }
}
