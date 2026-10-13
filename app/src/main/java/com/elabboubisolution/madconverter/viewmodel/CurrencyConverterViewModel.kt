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
import kotlinx.coroutines.delay
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
import java.util.Calendar
import java.util.TimeZone

enum class AmountError { INVALID, TOO_LARGE }

/**
 * Everything the converter screen renders. Fields after [to] are derived from the inputs
 * and the loaded rates; only the ViewModel computes them.
 *
 * @property isLoading a download is in progress (rates already shown, if any, stay visible).
 * @property error why no rates at all could be loaded (never set while rates are shown). Kept
 *   while Retry runs, so the error card stays in place; cleared when rates arrive, replaced by
 *   the new error if Retry fails.
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
 * @property freshnessCheckedAtMillis when rate freshness was last checked: the "now" the
 *   last-updated time is shown relative to ("Today, ..."); null to use the current time.
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
    val freshnessCheckedAtMillis: Long? = null,
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
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
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

    /**
     * The only running rate load (initial, Retry, foreground or scheduled check): a new load is
     * never started while it is active, so the repository is never called concurrently.
     */
    private var loadJob: Job? = null

    /** When the displayed rates stop being current (from the repository); null: unknown or now. */
    private var refreshDueAtMillis: Long? = null

    /** After a failed download, automatic checks wait until then (Retry does not); 0 after a success. */
    private var autoRefreshNotBeforeMillis = 0L

    /**
     * The one automatic recovery attempt of the current failure sequence (failed downloads since
     * the last successful one) has been made. Reset only by a successful download.
     */
    private var recoveryAttempted = false

    /** The screen is visible: freshness is then also checked at the deadline and at midnight. */
    private var isInForeground = false
    private var freshnessJob: Job? = null

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
        loadRates(silent = false)
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

    fun onHistoryEntryDeleted(id: String) = persist { historyStore.delete(id) }

    fun onHistoryCleared() = persist { historyStore.clear() }

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
        persist(
            // Not saved: forget it, so committing the same fee again retries. A newer fee
            // committed meanwhile is left alone.
            onFailure = { if (persistedFeePercent?.compareTo(percent) == 0) persistedFeePercent = null },
        ) { feePreferenceStore.setFeePercent(percent) }
    }

    private fun recordCurrentConversion() {
        val state = _uiState.value
        val result = state.result ?: return
        persist { historyStore.record(result, wasStale = state.isStale) }
    }

    fun onSwapCurrencies() = changePair { it.copy(from = it.to, to = it.from) }

    fun onRetry() = loadRates(silent = false)

    /**
     * The screen became visible (lifecycle STARTED): checks rate freshness now, then again at the
     * rates' refresh deadline and at midnight (for "Today") while it stays visible.
     */
    fun onForeground() {
        isInForeground = true
        checkFreshness()
    }

    /** The screen is no longer visible: no scheduled check runs until [onForeground]. */
    fun onBackground() {
        isInForeground = false
        freshnessJob?.cancel()
        freshnessJob = null
    }

    fun onToggleFavorite(currency: Currency) {
        val isFavorite = currency in _uiState.value.favorites
        persist { favoritesStore.setFavorite(currency, !isFavorite) }
    }

    /**
     * Runs a write to local storage. A storage failure ([IOException], e.g. a full disk) must not
     * crash the app: nothing was saved, and the stores' flows keep showing what is stored.
     */
    private fun persist(onFailure: () -> Unit = {}, write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (_: IOException) {
                onFailure()
            }
        }
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

    /**
     * Loads rates through the repository, which serves the cache while it is current and
     * otherwise downloads (at most once an hour). A [silent] load (automatic check) leaves the
     * screen as it is until the result arrives: no progress indicator, nothing announced.
     * Retry during a silent load shows its progress and waits for that same load.
     */
    private fun loadRates(silent: Boolean) {
        if (!silent) updateState { it.copy(isLoading = true) }
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val result = repository.getRates()
            val now = clock()
            when (result) {
                is RatesResult.Available -> {
                    snapshot = result.snapshot
                    refreshDueAtMillis = result.refreshDueAtMillis
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = null,
                            isStale = result.isStale,
                            refreshError = result.refreshError,
                            freshnessCheckedAtMillis = now,
                        )
                    }
                }
                is RatesResult.Unavailable -> {
                    refreshDueAtMillis = null
                    updateState {
                        if (snapshot != null) {
                            // Rates are already shown (e.g. the cache became unreadable): keep them
                            // usable, but never as current ones.
                            it.copy(
                                isLoading = false,
                                error = null,
                                isStale = true,
                                refreshError = result.error,
                                freshnessCheckedAtMillis = now,
                            )
                        } else {
                            it.copy(isLoading = false, error = result.error, freshnessCheckedAtMillis = now)
                        }
                    }
                }
            }
            val downloadFailed = result is RatesResult.Unavailable || (result as RatesResult.Available).isStale
            if (downloadFailed) {
                autoRefreshNotBeforeMillis = now + AUTO_REFRESH_BACKOFF_MILLIS
            } else {
                autoRefreshNotBeforeMillis = 0L
                recoveryAttempted = false
            }
            scheduleFreshnessCheck()
        }
    }

    private val isRateDue: Boolean get() = snapshot == null || (refreshDueAtMillis ?: clock()) <= clock()

    /**
     * Compares the rates with the clock. Loads them (silently) when they are due or missing,
     * unless a load is running or a recent automatic attempt failed; otherwise only updates the
     * "now" of the last-updated time and schedules the next check.
     * [isRecovery]: called by the recovery timer; a load it starts uses up the recovery attempt.
     */
    private fun checkFreshness(isRecovery: Boolean = false) {
        val now = clock()
        updateState { it.copy(freshnessCheckedAtMillis = now) }
        if (isRateDue && now >= autoRefreshNotBeforeMillis && loadJob?.isActive != true) {
            if (isRecovery) recoveryAttempted = true
            loadRates(silent = true) // schedules the next check when it completes
        } else {
            scheduleFreshnessCheck()
        }
    }

    /**
     * While visible, one timer: at the rates' refresh deadline, at the next midnight (so "Today"
     * stays right) or, after a failed download, at the end of the pause, whichever comes first.
     * That recovery attempt is made once per failure sequence: if it fails too, nothing is
     * retried before the next midnight check, foreground return or Retry. No timer while not
     * visible, so a pending recovery is dropped then (a foreground return checks anyway).
     */
    private fun scheduleFreshnessCheck() {
        freshnessJob?.cancel()
        freshnessJob = null
        if (!isInForeground) return
        val now = clock()
        val deadline = refreshDueAtMillis?.takeIf { snapshot != null && it > now } ?: Long.MAX_VALUE
        // Only in the future: once the pause is over, a load is running (it reschedules when done).
        val recoveryAt = autoRefreshNotBeforeMillis
            .takeIf { it > now && !recoveryAttempted && isRateDue }
            ?: Long.MAX_VALUE
        val wakeAt = minOf(deadline, recoveryAt, nextMidnightMillis(now))
        freshnessJob = viewModelScope.launch {
            delay(wakeAt - now)
            freshnessJob = null
            checkFreshness(isRecovery = wakeAt == recoveryAt)
        }
    }

    private fun nextMidnightMillis(nowMillis: Long): Long = Calendar.getInstance(timeZone()).run {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, 1)
        timeInMillis
    }

    private fun updateState(transform: (ConverterUiState) -> ConverterUiState) {
        _uiState.update { withDerivedFields(transform(it), snapshot) }
    }

    companion object {
        private const val MAX_INPUT_LENGTH = 24

        /** Pause before an automatic check tries again after a failed download (Retry does not wait). */
        const val AUTO_REFRESH_BACKOFF_MILLIS = 15 * 60 * 1000L

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
