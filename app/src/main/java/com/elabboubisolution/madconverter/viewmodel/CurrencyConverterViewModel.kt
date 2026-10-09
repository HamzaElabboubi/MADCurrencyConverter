package com.elabboubisolution.madconverter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elabboubisolution.madconverter.MadConverterApplication
import com.elabboubisolution.madconverter.data.local.CurrencyPair
import com.elabboubisolution.madconverter.data.local.CurrencyPairStore
import com.elabboubisolution.madconverter.data.local.FavoritesStore
import com.elabboubisolution.madconverter.data.local.FeePreferenceStore
import com.elabboubisolution.madconverter.data.local.HistoryStore
import com.elabboubisolution.madconverter.data.repository.CurrencyRepository
import com.elabboubisolution.madconverter.data.repository.RatesResult
import com.elabboubisolution.madconverter.domain.AmountInput
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.ConversionResult
import com.elabboubisolution.madconverter.domain.CurrencyConverter
import com.elabboubisolution.madconverter.domain.QuickConversions
import com.elabboubisolution.madconverter.domain.RealCost
import com.elabboubisolution.madconverter.domain.RealCostEstimate
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
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
 * @property feePercent Real Cost fee the user chose (0–[RealCost.MAX_FEE_PERCENT]).
 * @property realCost [result] with [feePercent] applied; null when there is no result.
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
    val feePercent: BigDecimal = BigDecimal.ZERO,
    val realCost: RealCostEstimate? = null,
) {
    /** Rates (live or cached) are loaded, so the rate, update time and result can be shown. */
    val hasRates: Boolean get() = lastUpdatedEpochSeconds != null
}

class CurrencyConverterViewModel(
    private val repository: CurrencyRepository,
    private val favoritesStore: FavoritesStore,
    private val historyStore: HistoryStore,
    private val feePreferenceStore: FeePreferenceStore,
    private val currencyPairStore: CurrencyPairStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private val _isPairRestored = MutableStateFlow(false)

    /**
     * True once the startup read of the saved pair has finished (pair applied or none saved).
     * The activity holds its first frame until then, so the default pair never flashes first.
     */
    val isPairRestored: StateFlow<Boolean> = _isPairRestored.asStateFlow()

    /**
     * Waits until [isPairRestored], at most [timeoutMillis]; returns at once if already restored
     * (e.g. after a rotation). True if restored in time. On false the caller shows the screen
     * anyway: the saved pair is applied when the read finishes, unless the user chose one.
     */
    suspend fun awaitPairRestored(timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) { isPairRestored.first { it } } ?: false

    private var snapshot: RateSnapshot? = null

    /** Last fee written to storage, to skip redundant writes. */
    private var persistedFeePercent: BigDecimal? = null
    private var loadJob: Job? = null

    /**
     * Set by the first user change of the pair. From then on the in-memory pair is the truth:
     * a saved pair read later (slow startup) is discarded instead of replacing the user's choice.
     */
    private var pairChangedByUser = false

    /**
     * Latest pair to persist. One collector writes it, so writes happen in order and a slow
     * write can never land after a newer one; intermediate pairs may be skipped (conflated).
     * Null until the user changes the pair: startup never writes the default over a saved pair.
     */
    private val pairToSave = MutableStateFlow<CurrencyPair?>(null)

    init {
        loadRates()
        restoreSavedPair()
        viewModelScope.launch {
            pairToSave.filterNotNull().collect { pair ->
                try {
                    currencyPairStore.save(pair)
                } catch (_: IOException) {
                    // Not saved: the pair still applies in memory and the next change retries.
                }
            }
        }
        viewModelScope.launch {
            favoritesStore.favorites.collect { favorites -> updateState { it.copy(favorites = favorites) } }
        }
        viewModelScope.launch {
            val saved = feePreferenceStore.feePercent.first()
            persistedFeePercent = saved
            updateState { it.copy(feePercent = saved) }
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
    fun onFromCurrencySelected(currency: Currency) = changePair {
        if (currency == it.to) it.copy(from = currency, to = it.from) else it.copy(from = currency)
    }

    fun onToCurrencySelected(currency: Currency) = changePair {
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
    fun onHistoryEntrySelected(entry: HistoryEntry) = changePair {
        it.copy(from = entry.from, to = entry.to, amountInput = entry.amount.toPlainString())
    }

    fun onHistoryEntryDeleted(id: String) {
        viewModelScope.launch { historyStore.delete(id) }
    }

    fun onHistoryCleared() {
        viewModelScope.launch { historyStore.clear() }
    }

    /**
     * Updates the Real Cost estimate immediately without saving, so typing a custom percentage
     * never writes to storage. Values outside 0–[RealCost.MAX_FEE_PERCENT] are ignored.
     */
    fun onFeePercentChanged(percent: BigDecimal) {
        if (percent.signum() < 0 || percent > RealCost.MAX_FEE_PERCENT) return
        updateState { it.copy(feePercent = percent) }
    }

    /** Saves the current fee (preset chosen or Real Cost closed); no-op if unchanged. */
    fun onFeePercentCommitted() {
        val percent = _uiState.value.feePercent
        if (persistedFeePercent?.compareTo(percent) == 0) return
        persistedFeePercent = percent
        viewModelScope.launch { feePreferenceStore.setFeePercent(percent) }
    }

    private fun recordCurrentConversion() {
        val state = _uiState.value
        val result = state.result ?: return
        viewModelScope.launch { historyStore.record(result, wasStale = state.isStale) }
    }

    fun onSwapCurrencies() = changePair { it.copy(from = it.to, to = it.from) }

    fun onRetry() = loadRates()

    fun onToggleFavorite(currency: Currency) {
        val isFavorite = currency in _uiState.value.favorites
        viewModelScope.launch { favoritesStore.setFavorite(currency, !isFavorite) }
    }

    /** Applies the saved pair unless the user already chose one (see [pairChangedByUser]). */
    private fun restoreSavedPair() {
        viewModelScope.launch {
            try {
                // Unreadable preferences count as nothing saved: the default pair stays.
                val saved = try {
                    currencyPairStore.read()
                } catch (_: IOException) {
                    null
                } ?: return@launch
                updateState { if (pairChangedByUser) it else it.copy(from = saved.from, to = saved.to) }
            } finally {
                _isPairRestored.value = true
            }
        }
    }

    /**
     * A user change of source and/or target: applied in one state update (no intermediate
     * pair is ever visible or saved), then queued for saving.
     */
    private fun changePair(transform: (ConverterUiState) -> ConverterUiState) {
        pairChangedByUser = true
        updateState(transform)
        val state = _uiState.value
        // Defensive: a pair is always two currencies, never saved otherwise.
        if (state.from != state.to) pairToSave.value = CurrencyPair(state.from, state.to)
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
                    app.container.feePreferenceStore,
                    app.container.currencyPairStore,
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
        realCost = null,
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
                realCost = result?.let { RealCost.estimate(it, state.feePercent) },
            )
        }
    }
}
