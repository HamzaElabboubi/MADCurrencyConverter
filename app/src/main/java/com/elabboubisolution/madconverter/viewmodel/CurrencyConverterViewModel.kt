package com.elabboubisolution.madconverter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elabboubisolution.madconverter.MadConverterApplication
import com.elabboubisolution.madconverter.data.local.FavoritesStore
import com.elabboubisolution.madconverter.data.local.HistoryStore
import com.elabboubisolution.madconverter.data.repository.CurrencyRepository
import com.elabboubisolution.madconverter.data.repository.RatesResult
import com.elabboubisolution.madconverter.domain.AmountInput
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.ConversionResult
import com.elabboubisolution.madconverter.domain.CurrencyConverter
import com.elabboubisolution.madconverter.domain.QuickConversions
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import com.elabboubisolution.madconverter.domain.model.RateFetchError
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
 * @property isLoading a download is in progress (rates already shown, if any, stay visible).
 * @property error why no rates at all could be loaded.
 * @property isStale the displayed rates come from the local cache and may not be current.
 * @property refreshError why fresh rates could not be downloaded, when [isStale].
 * @property missingRate a selected currency the loaded rates do not cover.
 * @property rate value of 1 [from] in [to], unrounded.
 * @property result conversion of a valid, non-empty amount.
 * @property favorites currencies the user starred, listed first in the currency picker.
 * @property quickConversions [result]'s amount converted into up to 3 other favorites, from the
 *   same rates; empty when there is no result.
 * @property history copied/shared conversions, newest first (historical values, not current).
 */
data class ConverterUiState(
    val amountInput: String = "",
    val from: Currency = Currency.MAD,
    val to: Currency = Currency.USD,
    val isLoading: Boolean = true,
    val error: RateFetchError? = null,
    val isStale: Boolean = false,
    val refreshError: RateFetchError? = null,
    val amountError: AmountError? = null,
    val missingRate: Currency? = null,
    val rate: BigDecimal? = null,
    val result: Conversion? = null,
    val lastUpdatedEpochSeconds: Long? = null,
    val favorites: Set<Currency> = emptySet(),
    val quickConversions: List<Conversion> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
) {
    /** Rates (live or cached) are loaded, so the rate, update time and result can be shown. */
    val hasRates: Boolean get() = lastUpdatedEpochSeconds != null
}

class CurrencyConverterViewModel(
    private val repository: CurrencyRepository,
    private val favoritesStore: FavoritesStore,
    private val historyStore: HistoryStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private var snapshot: RateSnapshot? = null
    private var loadJob: Job? = null

    init {
        loadRates()
        viewModelScope.launch {
            favoritesStore.favorites.collect { favorites -> updateState { it.copy(favorites = favorites) } }
        }
        viewModelScope.launch {
            historyStore.entries.collect { history -> updateState { it.copy(history = history) } }
        }
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

    /** Makes a quick conversion the main one: only the target changes. */
    fun onQuickConversionSelected(currency: Currency) = onToCurrencySelected(currency)

    /** The user copied the main result: the only events, with [onResultShared], that record history. */
    fun onResultCopied() = recordCurrentConversion()

    fun onResultShared() = recordCurrentConversion()

    /**
     * Restores the inputs of a past conversion. The result is recalculated from the current
     * rates; the stored historical amount is never shown as today's value.
     */
    fun onHistoryEntrySelected(entry: HistoryEntry) = updateState {
        it.copy(from = entry.from, to = entry.to, amountInput = entry.amount.toPlainString())
    }

    fun onHistoryEntryDeleted(id: String) {
        viewModelScope.launch { historyStore.delete(id) }
    }

    fun onHistoryCleared() {
        viewModelScope.launch { historyStore.clear() }
    }

    private fun recordCurrentConversion() {
        val state = _uiState.value
        val result = state.result ?: return
        viewModelScope.launch { historyStore.record(result, wasStale = state.isStale) }
    }

    fun onSwapCurrencies() = updateState { it.copy(from = it.to, to = it.from) }

    fun onRetry() = loadRates()

    fun onToggleFavorite(currency: Currency) {
        val isFavorite = currency in _uiState.value.favorites
        viewModelScope.launch { favoritesStore.setFavorite(currency, !isFavorite) }
    }

    private fun loadRates() {
        if (loadJob?.isActive == true) return
        updateState { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.getRates()) {
                is RatesResult.Available -> {
                    snapshot = result.snapshot
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = null,
                            isStale = result.isStale,
                            refreshError = result.refreshError,
                        )
                    }
                }
                is RatesResult.Unavailable -> updateState { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    private fun updateState(transform: (ConverterUiState) -> ConverterUiState) {
        _uiState.update { withDerivedFields(transform(it), snapshot) }
    }

    companion object {
        private const val MAX_INPUT_LENGTH = 24

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MadConverterApplication
                CurrencyConverterViewModel(
                    app.container.currencyRepository,
                    app.container.favoritesStore,
                    app.container.historyStore,
                )
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
        quickConversions = emptyList(),
        lastUpdatedEpochSeconds = snapshot?.lastUpdatedEpochSeconds,
    )
    if (snapshot == null) return base

    return when (val unit = CurrencyConverter.convert(BigDecimal.ONE, state.from, state.to, snapshot)) {
        is ConversionResult.MissingRate -> base.copy(missingRate = unit.currency)
        is ConversionResult.Success -> {
            val result = (amount as? AmountInput.Valid)?.let {
                (CurrencyConverter.convert(it.value, state.from, state.to, snapshot) as? ConversionResult.Success)
                    ?.conversion
            }
            base.copy(
                rate = unit.conversion.rate,
                result = result,
                quickConversions = result?.let {
                    QuickConversions.convert(it.amount, state.from, state.to, state.favorites, snapshot)
                }.orEmpty(),
            )
        }
    }
}
