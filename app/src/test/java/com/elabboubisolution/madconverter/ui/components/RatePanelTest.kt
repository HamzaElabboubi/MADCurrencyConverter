package com.elabboubisolution.madconverter.ui.components

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.viewmodel.ConverterUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatePanelTest {

    private val loaded = 1_791_158_551L

    @Test
    fun `initial load without cached data shows the placeholder`() {
        val state = ConverterUiState(isLoading = true)
        assertEquals(RatePanel.InitialLoading, ratePanel(state, lastError = null))
    }

    @Test
    fun `loading while cached rates are available keeps them visible`() {
        val state = ConverterUiState(isLoading = true, lastUpdatedEpochSeconds = loaded)
        val panel = ratePanel(state, lastError = null) as RatePanel.Available
        assertTrue(panel.isRefreshing)
        assertFalse(panel.isStale)
    }

    @Test
    fun `current cached rates show no warning`() {
        val panel = ratePanel(ConverterUiState(isLoading = false, lastUpdatedEpochSeconds = loaded), null)
        assertEquals(RatePanel.Available(false, null, false, null, false), panel)
    }

    @Test
    fun `stale cached rates show the warning with its reason`() {
        val state = ConverterUiState(
            isLoading = false,
            lastUpdatedEpochSeconds = loaded,
            isStale = true,
            refreshError = RateFetchError.NoConnection,
        )
        val panel = ratePanel(state, null) as RatePanel.Available
        assertTrue(panel.isStale)
        assertEquals(RateFetchError.NoConnection, panel.refreshError)
        assertFalse(panel.isRefreshing)
    }

    @Test
    fun `network failure without cached data shows the error, never a result`() {
        val state = ConverterUiState(amountInput = "1000", isLoading = false, error = RateFetchError.NoConnection)
        assertEquals(RatePanel.Unavailable(RateFetchError.NoConnection, isRetrying = false), ratePanel(state, null))
        assertNull(state.result)
    }

    @Test
    fun `retry without cached data keeps the error card with a progress indicator`() {
        // The view model clears `error` while retrying; the remembered error keeps the card.
        val retrying = ConverterUiState(isLoading = true, error = null)
        assertEquals(
            RatePanel.Unavailable(RateFetchError.Timeout, isRetrying = true),
            ratePanel(retrying, lastError = RateFetchError.Timeout),
        )
    }

    @Test
    fun `successful recovery after retry clears error and warning`() {
        val recovered = ConverterUiState(isLoading = false, lastUpdatedEpochSeconds = loaded)
        // Even with a remembered error, available rates win: no contradictory messages.
        val panel = ratePanel(recovered, lastError = RateFetchError.NoConnection) as RatePanel.Available
        assertFalse(panel.isStale)
        assertNull(panel.refreshError)
        assertNull(panel.missingRate)
    }

    @Test
    fun `a refresh error is not shown when the rates are not stale`() {
        val state = ConverterUiState(lastUpdatedEpochSeconds = loaded, isLoading = false, refreshError = RateFetchError.Timeout)
        assertNull((ratePanel(state, null) as RatePanel.Available).refreshError)
    }

    @Test
    fun `missing rate offers retry only when the stale banner does not`() {
        val fresh = ConverterUiState(isLoading = false, lastUpdatedEpochSeconds = loaded, missingRate = Currency.GBP)
        assertTrue((ratePanel(fresh, null) as RatePanel.Available).missingRateRetry)

        val stale = fresh.copy(isStale = true, refreshError = RateFetchError.NoConnection)
        val panel = ratePanel(stale, null) as RatePanel.Available
        assertTrue(panel.isStale)
        assertEquals(Currency.GBP, panel.missingRate)
        assertFalse("only one Retry on screen", panel.missingRateRetry)
    }
}
