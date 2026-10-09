package com.elabboubisolution.madconverter.ui.components

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.viewmodel.ConverterUiState

/**
 * What the rate area of the screen shows. Pure presentation: derived from [ConverterUiState]
 * without changing how or when rates are loaded.
 */
sealed interface RatePanel {

    /** First load with nothing to show yet: a placeholder that keeps the result's place. */
    data object InitialLoading : RatePanel

    /**
     * No rates at all (nothing cached, download failed). Never shows a result.
     * [isRetrying]: Retry is running; the card stays in place with a progress indicator
     * instead of disappearing, so it does not flicker.
     */
    data class Unavailable(val error: RateFetchError, val isRetrying: Boolean) : RatePanel

    /**
     * Rates (live or cached) are shown.
     * @property isStale cached rates that could not be refreshed: show the warning banner.
     * @property refreshError why the refresh failed, when [isStale].
     * @property isRefreshing a download is running (Retry pressed).
     * @property missingRate a selected currency the rates do not cover.
     * @property missingRateRetry the missing-rate card offers Retry; not when the stale banner
     *   already does, so there is only ever one Retry.
     */
    data class Available(
        val isStale: Boolean,
        val refreshError: RateFetchError?,
        val isRefreshing: Boolean,
        val missingRate: Currency?,
        val missingRateRetry: Boolean,
    ) : RatePanel
}

/**
 * @param lastError the error shown before the current load started. The view model clears
 *   [ConverterUiState.error] when Retry starts; remembering it keeps the error card visible
 *   (with a progress indicator) until the new result arrives.
 */
fun ratePanel(state: ConverterUiState, lastError: RateFetchError?): RatePanel = when {
    state.hasRates -> RatePanel.Available(
        isStale = state.isStale,
        refreshError = state.refreshError.takeIf { state.isStale },
        isRefreshing = state.isLoading,
        missingRate = state.missingRate,
        missingRateRetry = state.missingRate != null && !state.isStale,
    )
    state.error != null -> RatePanel.Unavailable(state.error, isRetrying = false)
    state.isLoading && lastError != null -> RatePanel.Unavailable(lastError, isRetrying = true)
    else -> RatePanel.InitialLoading
}
