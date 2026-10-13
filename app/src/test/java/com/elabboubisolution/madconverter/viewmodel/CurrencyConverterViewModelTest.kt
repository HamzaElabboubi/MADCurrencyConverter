package com.elabboubisolution.madconverter.viewmodel

import com.elabboubisolution.madconverter.data.local.CurrencyPair
import com.elabboubisolution.madconverter.data.repository.CurrencyRepository
import com.elabboubisolution.madconverter.data.repository.RatesResult
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import com.elabboubisolution.madconverter.testing.FakeCurrencyPairStore
import com.elabboubisolution.madconverter.testing.FakeFavoritesStore
import com.elabboubisolution.madconverter.testing.FakeFeePreferenceStore
import com.elabboubisolution.madconverter.testing.FakeHistoryStore
import com.elabboubisolution.madconverter.testing.SampleRates
import com.elabboubisolution.madconverter.ui.components.RatePanel
import com.elabboubisolution.madconverter.ui.components.ratePanel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyConverterViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    /** Same limit as MainActivity's MAX_PAIR_WAIT_MILLIS. */
    private val startupWait = 500L

    private val snapshot = SampleRates.snapshot
    private val favorites = FakeFavoritesStore()
    private val history = FakeHistoryStore()
    private val fees = FakeFeePreferenceStore()
    private val pairs = FakeCurrencyPairStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts loading then shows rate once loaded`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.rate)

        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(0, BigDecimal("0.100812").compareTo(state.rate))
        assertEquals(1791158551L, state.lastUpdatedEpochSeconds)
        assertNull("empty amount must not produce a result", state.result)
        assertNull(state.amountError)
    }

    @Test
    fun `typing an amount converts it`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onAmountChange("1000")

        assertEquals(BigDecimal("100.81"), viewModel.uiState.value.result?.convertedAmount)
    }

    @Test
    fun `amount typed while loading is converted once rates arrive`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        viewModel.onAmountChange("1000")
        assertNull(viewModel.uiState.value.result)

        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()

        assertEquals(BigDecimal("100.81"), viewModel.uiState.value.result?.convertedAmount)
    }

    @Test
    fun `invalid and too large amounts are flagged without a result`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onAmountChange("12a")
        assertEquals(AmountError.INVALID, viewModel.uiState.value.amountError)
        assertNull(viewModel.uiState.value.result)

        viewModel.onAmountChange("1000000000000")
        assertEquals(AmountError.TOO_LARGE, viewModel.uiState.value.amountError)
        assertNull(viewModel.uiState.value.result)

        viewModel.onAmountChange("12,5")
        assertNull(viewModel.uiState.value.amountError)
        assertEquals(BigDecimal("1.26"), viewModel.uiState.value.result?.convertedAmount)
    }

    @Test
    fun `swap exchanges currencies and recomputes`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("100")

        viewModel.onSwapCurrencies()

        val state = viewModel.uiState.value
        assertEquals(Currency.USD, state.from)
        assertEquals(Currency.MAD, state.to)
        assertEquals(BigDecimal("991.95"), state.result?.convertedAmount)
    }

    @Test
    fun `selecting the currency of the other side swaps them`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onToCurrencySelected(Currency.MAD)

        assertEquals(Currency.USD, viewModel.uiState.value.from)
        assertEquals(Currency.MAD, viewModel.uiState.value.to)

        viewModel.onFromCurrencySelected(Currency.EUR)
        assertEquals(Currency.EUR, viewModel.uiState.value.from)
        assertEquals(Currency.MAD, viewModel.uiState.value.to)
    }

    @Test
    fun `network error is exposed and retry recovers`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        assertEquals(RateFetchError.NoConnection, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.onRetry()
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals("kept until the retry ends", RateFetchError.NoConnection, viewModel.uiState.value.error)

        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.error)
        assertEquals(2, repository.calls)
    }

    @Test
    fun `retry while a request is running does not start another`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        dispatcher.scheduler.runCurrent()

        viewModel.onRetry()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.calls)
    }

    @Test
    fun `missing rate for the selected currency is reported`() = runTest(dispatcher) {
        val withoutGbp = snapshot.copy(rates = snapshot.rates - Currency.GBP)
        val viewModel = loadedViewModel(withoutGbp)
        viewModel.onAmountChange("100")

        viewModel.onToCurrencySelected(Currency.GBP)

        val state = viewModel.uiState.value
        assertEquals(Currency.GBP, state.missingRate)
        assertNull(state.rate)
        assertNull(state.result)
    }

    @Test
    fun `stale cached rates are flagged and still convert`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = true, refreshError = RateFetchError.Timeout))
        dispatcher.scheduler.runCurrent()

        viewModel.onAmountChange("1000")

        val state = viewModel.uiState.value
        assertTrue(state.hasRates)
        assertTrue(state.isStale)
        assertEquals(RateFetchError.Timeout, state.refreshError)
        assertNull("stale data is not a load error", state.error)
        assertEquals(BigDecimal("100.81"), state.result?.convertedAmount)
    }

    @Test
    fun `retry from stale keeps showing rates while refreshing then clears the flag`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = true, refreshError = RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")

        viewModel.onRetry()
        dispatcher.scheduler.runCurrent()
        val refreshing = viewModel.uiState.value
        assertTrue(refreshing.isLoading)
        assertTrue(refreshing.hasRates)
        assertEquals(BigDecimal("100.81"), refreshing.result?.convertedAmount)

        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()
        val refreshed = viewModel.uiState.value
        assertFalse(refreshed.isLoading)
        assertFalse(refreshed.isStale)
        assertNull(refreshed.refreshError)
    }

    // --- Failed refresh while rates are displayed ---

    @Test
    fun `a failed refresh keeps the displayed rates and marks them stale`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")

        viewModel.onRetry()
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasRates)
        assertTrue(state.isStale)
        assertEquals(RateFetchError.NoConnection, state.refreshError)
        assertNull("not the no-rates error", state.error)
        assertEquals(1791158551L, state.lastUpdatedEpochSeconds)
        assertEquals(BigDecimal("100.81"), state.result?.convertedAmount)
        assertEquals(
            "stale banner with its reason, never the no-rates error card",
            RatePanel.Available(
                isStale = true,
                refreshError = RateFetchError.NoConnection,
                isRefreshing = false,
                missingRate = null,
                missingRateRetry = false,
            ),
            ratePanel(state),
        )
    }

    @Test
    fun `a failed missing-rate retry shows the stale banner as the only retry`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot.copy(rates = snapshot.rates - Currency.GBP), isStale = false))
        dispatcher.scheduler.runCurrent()
        viewModel.onToCurrencySelected(Currency.GBP)

        viewModel.onRetry()
        repository.complete(RatesResult.Unavailable(RateFetchError.Timeout))
        dispatcher.scheduler.runCurrent()

        assertEquals(
            RatePanel.Available(
                isStale = true,
                refreshError = RateFetchError.Timeout,
                isRefreshing = false,
                missingRate = Currency.GBP,
                missingRateRetry = false,
            ),
            ratePanel(viewModel.uiState.value),
        )
    }

    @Test
    fun `copy and share after a failed refresh are recorded as stale`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")
        viewModel.onRetry()
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        viewModel.onResultCopied()
        viewModel.onResultShared()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(true, true), history.recorded.map { it.second })
        assertEquals("copy and share never reload rates", 2, repository.calls)
    }

    @Test
    fun `a later failure replaces the reason of already stale rates`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = true, refreshError = RateFetchError.Timeout))
        dispatcher.scheduler.runCurrent()

        viewModel.onRetry()
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isStale)
        assertEquals(RateFetchError.NoConnection, state.refreshError)
        assertNull(state.error)
    }

    @Test
    fun `a successful refresh after the failure clears the stale status`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")
        viewModel.onRetry()
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        viewModel.onRetry()
        val refreshing = ratePanel(viewModel.uiState.value) as RatePanel.Available
        assertTrue("the stale banner stays while refreshing", refreshing.isStale && refreshing.isRefreshing)
        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isStale)
        assertNull(state.refreshError)
        assertNull(state.error)
        viewModel.onResultCopied()
        dispatcher.scheduler.runCurrent()
        assertFalse(history.recorded.single().second)
    }

    @Test
    fun `a failure without any displayed rates still shows the no-rates error`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        viewModel.onAmountChange("1000")
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(RateFetchError.NoConnection, state.error)
        assertFalse(state.hasRates)
        assertFalse(state.isStale)
        assertNull(state.refreshError)
        assertNull(state.result)
        assertEquals(RatePanel.Unavailable(RateFetchError.NoConnection, isRetrying = false), ratePanel(state))

        viewModel.onResultCopied()
        dispatcher.scheduler.runCurrent()
        assertEquals("nothing to record without rates", 0, history.recorded.size)
    }

    // --- Retry without any rates (error card state lives in the view model) ---

    @Test
    fun `retry without rates keeps the error and shows progress, from the state alone`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        viewModel.onRetry()
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertEquals("the error stays while retrying", RateFetchError.NoConnection, state.error)
        // A recreated screen (rotation) has nothing remembered: the state must suffice.
        assertEquals(
            RatePanel.Unavailable(RateFetchError.NoConnection, isRetrying = true),
            ratePanel(state),
        )
        assertEquals(2, repository.calls)
    }

    @Test
    fun `a successful retry without rates clears the error`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")

        viewModel.onRetry()
        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertFalse(state.isLoading)
        assertFalse(state.isStale)
        assertEquals(BigDecimal("100.81"), state.result?.convertedAmount)
        assertTrue(ratePanel(state) is RatePanel.Available)
    }

    @Test
    fun `a failed retry without rates shows the latest error`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        viewModel.onRetry()
        repository.complete(RatesResult.Unavailable(RateFetchError.Timeout))
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(RateFetchError.Timeout, state.error)
        assertFalse(state.isLoading)
        assertFalse(state.hasRates)
        assertEquals(RatePanel.Unavailable(RateFetchError.Timeout, isRetrying = false), ratePanel(state))
    }

    @Test
    fun `retry with stale rates shown never shows the no-rates error`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = true, refreshError = RateFetchError.Timeout))
        dispatcher.scheduler.runCurrent()

        viewModel.onRetry()
        dispatcher.scheduler.runCurrent()
        val retrying = ratePanel(viewModel.uiState.value) as RatePanel.Available
        assertTrue(retrying.isStale && retrying.isRefreshing)
        assertNull(viewModel.uiState.value.error)

        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()
        val failed = ratePanel(viewModel.uiState.value) as RatePanel.Available
        assertTrue(failed.isStale && !failed.isRefreshing)
        assertEquals(RateFetchError.NoConnection, failed.refreshError)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `conversions and rate use newly supported currencies`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")

        viewModel.onToCurrencySelected(Currency.JPY)
        assertEquals(BigDecimal("15840"), viewModel.uiState.value.result?.convertedAmount)

        viewModel.onFromCurrencySelected(Currency.CAD)
        viewModel.onToCurrencySelected(Currency.AED)
        viewModel.onAmountChange("500")
        val state = viewModel.uiState.value
        assertEquals(Currency.CAD, state.from)
        assertEquals(Currency.AED, state.to)
        assertEquals(BigDecimal("1289.15"), state.result?.convertedAmount)
    }

    @Test
    fun `swap works between new currencies and twice restores the pair`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onFromCurrencySelected(Currency.EUR)
        viewModel.onToCurrencySelected(Currency.TRY)
        viewModel.onAmountChange("100")

        viewModel.onSwapCurrencies()
        assertEquals(Currency.TRY, viewModel.uiState.value.from)
        assertEquals(Currency.EUR, viewModel.uiState.value.to)
        assertEquals("100", viewModel.uiState.value.amountInput)

        viewModel.onSwapCurrencies()
        assertEquals(Currency.EUR, viewModel.uiState.value.from)
        assertEquals(Currency.TRY, viewModel.uiState.value.to)
        assertEquals(BigDecimal("5491.95"), viewModel.uiState.value.result?.convertedAmount)
    }

    @Test
    fun `favorites from the store are exposed and toggled`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        assertEquals(setOf(Currency.MAD, Currency.EUR, Currency.USD), viewModel.uiState.value.favorites)

        viewModel.onToggleFavorite(Currency.JPY)
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf(Currency.MAD, Currency.EUR, Currency.USD, Currency.JPY), viewModel.uiState.value.favorites)

        viewModel.onToggleFavorite(Currency.EUR)
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf(Currency.MAD, Currency.USD, Currency.JPY), viewModel.uiState.value.favorites)
    }

    @Test
    fun `toggling a favorite does not change the conversion`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")
        val before = viewModel.uiState.value

        viewModel.onToggleFavorite(Currency.USD)
        dispatcher.scheduler.runCurrent()

        // Only favorites and the quick conversions derived from them may change.
        val after = viewModel.uiState.value
        assertEquals(before.copy(favorites = after.favorites, quickConversions = after.quickConversions), after)
    }

    // --- Quick conversions ---

    @Test
    fun `no quick conversions without an amount`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        favorites.set(Currency.EUR, Currency.GBP, Currency.AED)
        dispatcher.scheduler.runCurrent()

        assertEquals(emptyList<Currency>(), viewModel.uiState.value.quickConversions.map { it.to })

        viewModel.onAmountChange("12a")
        assertEquals(emptyList<Currency>(), viewModel.uiState.value.quickConversions.map { it.to })
    }

    @Test
    fun `quick conversions follow the amount and favorites`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        favorites.set(Currency.MAD, Currency.USD, Currency.EUR, Currency.AED, Currency.GBP)
        dispatcher.scheduler.runCurrent()

        viewModel.onAmountChange("1000")
        assertEquals(
            listOf(Currency.EUR to BigDecimal("89.73"), Currency.GBP to BigDecimal("76.29"), Currency.AED to BigDecimal("368.29")),
            viewModel.uiState.value.quickConversions.map { it.to to it.convertedAmount },
        )

        viewModel.onAmountChange("1234,56")
        assertEquals(
            listOf(BigDecimal("110.78"), BigDecimal("94.19"), BigDecimal("454.67")),
            viewModel.uiState.value.quickConversions.map { it.convertedAmount },
        )

        favorites.set(Currency.EUR)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(Currency.EUR), viewModel.uiState.value.quickConversions.map { it.to })
    }

    @Test
    fun `tapping a quick conversion changes only the target`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        favorites.set(Currency.MAD, Currency.USD, Currency.EUR, Currency.AED, Currency.GBP)
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")

        viewModel.onQuickConversionSelected(Currency.EUR)

        val state = viewModel.uiState.value
        assertEquals(Currency.MAD, state.from)
        assertEquals(Currency.EUR, state.to)
        assertEquals("1000", state.amountInput)
        assertEquals(BigDecimal("89.73"), state.result?.convertedAmount)
        // The previous target (USD) is a favorite again eligible, EUR is now excluded.
        assertEquals(listOf(Currency.USD, Currency.GBP, Currency.AED), state.quickConversions.map { it.to })
    }

    @Test
    fun `stale cached rates still produce quick conversions`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = true, refreshError = RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        viewModel.onAmountChange("1000")

        val state = viewModel.uiState.value
        assertTrue(state.isStale)
        assertEquals(listOf(Currency.EUR), state.quickConversions.map { it.to })
        assertEquals(BigDecimal("89.73"), state.quickConversions.single().convertedAmount)
    }

    @Test
    fun `typing and tapping quick conversions never reload rates`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()
        favorites.set(Currency.EUR, Currency.GBP, Currency.AED, Currency.JPY)
        dispatcher.scheduler.runCurrent()

        listOf("1", "10", "100", "1000", "1000,5").forEach(viewModel::onAmountChange)
        viewModel.onQuickConversionSelected(Currency.GBP)
        viewModel.onQuickConversionSelected(Currency.AED)
        viewModel.onSwapCurrencies()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.calls)
        assertEquals(0, favorites.writes)
    }

    // --- Copy / Share / history ---

    @Test
    fun `copy records the current main conversion`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")

        viewModel.onResultCopied()
        dispatcher.scheduler.runCurrent()

        val recorded = history.recorded.single()
        assertEquals(Currency.MAD, recorded.first.from)
        assertEquals(Currency.USD, recorded.first.to)
        assertEquals(BigDecimal("100.81"), recorded.first.convertedAmount)
        assertFalse(recorded.second)
    }

    @Test
    fun `share records the current main conversion`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("250")
        viewModel.onToCurrencySelected(Currency.EUR)

        viewModel.onResultShared()
        dispatcher.scheduler.runCurrent()

        assertEquals(Currency.EUR, history.recorded.single().first.to)
        assertEquals(BigDecimal("22.43"), history.recorded.single().first.convertedAmount)
    }

    @Test
    fun `typing, swapping, picking and quick conversions never record history`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        favorites.set(Currency.EUR, Currency.GBP, Currency.AED)
        dispatcher.scheduler.runCurrent()

        listOf("1", "10", "100", "1000", "1000,5").forEach(viewModel::onAmountChange)
        viewModel.onSwapCurrencies()
        viewModel.onSwapCurrencies()
        viewModel.onToCurrencySelected(Currency.JPY)
        viewModel.onQuickConversionSelected(Currency.GBP)
        viewModel.onToggleFavorite(Currency.CHF)
        dispatcher.scheduler.runCurrent()

        assertEquals(emptyList<Any>(), history.recorded)
    }

    @Test
    fun `nothing is recorded without a valid result`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onResultCopied()
        viewModel.onAmountChange("12a")
        viewModel.onResultShared()
        dispatcher.scheduler.runCurrent()

        assertEquals(emptyList<Any>(), history.recorded)
    }

    @Test
    fun `offline stale copy and share work and are recorded as stale without any refresh`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = true, refreshError = RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")

        viewModel.onResultCopied()
        viewModel.onResultShared()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(true, true), history.recorded.map { it.second })
        assertEquals(1, repository.calls)
    }

    @Test
    fun `history entries are exposed in the state`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val entry = historyEntry(amount = "500", converted = "50.00")

        history.state.value = listOf(entry)
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(entry), viewModel.uiState.value.history)
    }

    @Test
    fun `restoring an entry keeps source, target and amount but recalculates with current rates`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        // Recorded weeks ago at an older rate: 250 EUR -> 2795.20 MAD.
        val old = historyEntry(from = Currency.EUR, to = Currency.MAD, amount = "250", converted = "2795.20", rate = "11.1808")

        viewModel.onHistoryEntrySelected(old)

        val state = viewModel.uiState.value
        assertEquals(Currency.EUR, state.from)
        assertEquals(Currency.MAD, state.to)
        assertEquals("250", state.amountInput)
        // 250 / 0.089729 with the current sample rates, not the stored 2795.20.
        assertEquals(BigDecimal("2786.17"), state.result?.convertedAmount)
        assertEquals(emptyList<Any>(), history.recorded)
    }

    @Test
    fun `restoring a decimal amount keeps its exact value`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onHistoryEntrySelected(historyEntry(amount = "1234.56", converted = "124.46"))

        assertEquals("1234.56", viewModel.uiState.value.amountInput)
        assertEquals(BigDecimal("124.46"), viewModel.uiState.value.result?.convertedAmount)
    }

    @Test
    fun `entered trailing zeros are kept for the result, copy and restore`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1250.50")

        assertEquals("1250.50", viewModel.uiState.value.amountInput) // field text untouched
        assertEquals("1250.50", viewModel.uiState.value.result?.amount?.toPlainString())

        viewModel.onResultCopied()
        dispatcher.scheduler.runCurrent()
        assertEquals("1250.50", history.recorded.single().first.amount.toPlainString())

        viewModel.onHistoryEntrySelected(historyEntry(amount = "1250.00", converted = "126.02"))
        assertEquals("1250.00", viewModel.uiState.value.amountInput)
        assertEquals("1250.00", viewModel.uiState.value.result?.amount?.toPlainString())
    }

    @Test
    fun `delete and clear are forwarded to the store`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onHistoryEntryDeleted("abc")
        viewModel.onHistoryCleared()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf("abc"), history.deleted)
        assertEquals(1, history.clears)
    }

    // --- Real Cost ---

    @Test
    fun `no real cost without a valid amount`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        assertNull(viewModel.uiState.value.realCost)

        viewModel.onAmountChange("12a")
        assertNull(viewModel.uiState.value.realCost)

        viewModel.onAmountChange("")
        assertNull(viewModel.uiState.value.realCost)
    }

    @Test
    fun `no real cost when the rate is missing`() = runTest(dispatcher) {
        val viewModel = loadedViewModel(snapshot.copy(rates = snapshot.rates - Currency.GBP))
        viewModel.onAmountChange("100")

        viewModel.onToCurrencySelected(Currency.GBP)

        assertNull(viewModel.uiState.value.realCost)
    }

    @Test
    fun `real cost applies the fee to the main result in the target currency`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onFromCurrencySelected(Currency.EUR)
        viewModel.onToCurrencySelected(Currency.MAD)
        viewModel.onAmountChange("250")

        viewModel.onFeePercentChanged(BigDecimal("3"))

        val estimate = viewModel.uiState.value.realCost!!
        assertEquals(Currency.MAD, estimate.currency)
        assertEquals(BigDecimal("2786.17"), estimate.convertedAmount)
        assertEquals(BigDecimal("83.59"), estimate.fee)
        assertEquals(BigDecimal("2869.76"), estimate.total)

        viewModel.onSwapCurrencies()
        assertEquals(Currency.EUR, viewModel.uiState.value.realCost!!.currency)
        assertEquals(BigDecimal("23.10"), viewModel.uiState.value.realCost!!.total)
    }

    @Test
    fun `defaults to 0 percent and loads the saved fee`() = runTest(dispatcher) {
        assertEquals(BigDecimal.ZERO, loadedViewModel().uiState.value.feePercent)

        fees.saved = BigDecimal("2.5")
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")

        assertEquals(BigDecimal("2.5"), viewModel.uiState.value.feePercent)
        assertEquals(BigDecimal("103.33"), viewModel.uiState.value.realCost!!.total)
    }

    @Test
    fun `typing a custom fee does not write, committing writes once`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")

        listOf("2", "2.7", "2.75").forEach { viewModel.onFeePercentChanged(BigDecimal(it)) }
        dispatcher.scheduler.runCurrent()
        assertEquals(emptyList<BigDecimal>(), fees.writes)
        assertEquals(BigDecimal("2.75"), viewModel.uiState.value.realCost!!.feePercent)

        viewModel.onFeePercentCommitted()
        viewModel.onFeePercentCommitted()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(BigDecimal("2.75")), fees.writes)
    }

    @Test
    fun `committing an unchanged fee does not write`() = runTest(dispatcher) {
        fees.saved = BigDecimal("3")
        val viewModel = loadedViewModel()

        viewModel.onFeePercentChanged(BigDecimal("3.00"))
        viewModel.onFeePercentCommitted()
        dispatcher.scheduler.runCurrent()

        assertEquals(emptyList<BigDecimal>(), fees.writes)
    }

    @Test
    fun `out of range fees are ignored`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onFeePercentChanged(BigDecimal("2"))

        viewModel.onFeePercentChanged(BigDecimal("-1"))
        viewModel.onFeePercentChanged(BigDecimal("20.01"))

        assertEquals(BigDecimal("2"), viewModel.uiState.value.feePercent)
    }

    @Test
    fun `stale cached rates still give a real cost estimate`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = true, refreshError = RateFetchError.Timeout))
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")

        viewModel.onFeePercentChanged(BigDecimal("3"))

        assertTrue(viewModel.uiState.value.isStale)
        assertEquals(BigDecimal("103.83"), viewModel.uiState.value.realCost!!.total)
    }

    @Test
    fun `real cost never reloads rates nor records history`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()
        viewModel.onAmountChange("1000")

        // Opening, editing and closing the sheet only goes through these two calls.
        listOf("0", "1", "2", "3", "1.5", "4.25").forEach { viewModel.onFeePercentChanged(BigDecimal(it)) }
        viewModel.onFeePercentCommitted()
        viewModel.onFeePercentChanged(BigDecimal("2"))
        viewModel.onFeePercentCommitted()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.calls)
        assertEquals(emptyList<Any>(), history.recorded)
    }

    @Test
    fun `copy is unchanged by the real cost fee`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")
        viewModel.onFeePercentChanged(BigDecimal("3"))

        viewModel.onResultCopied()
        dispatcher.scheduler.runCurrent()

        assertEquals(BigDecimal("100.81"), history.recorded.single().first.convertedAmount)
    }

    private fun historyEntry(
        from: Currency = Currency.MAD,
        to: Currency = Currency.USD,
        amount: String,
        converted: String,
        rate: String = "0.100812",
    ) = HistoryEntry("h1", from, to, BigDecimal(amount), BigDecimal(converted), BigDecimal(rate), 0L, false)

    // --- Last selected currency pair (Phase 12.7) ---

    @Test
    fun `fresh install starts with MAD to USD and writes nothing`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        assertEquals(Currency.MAD, viewModel.uiState.value.from)
        assertEquals(Currency.USD, viewModel.uiState.value.to)
        assertEquals(emptyList<CurrencyPair>(), pairs.writes)
    }

    @Test
    fun `a saved pair is restored at startup without being written back`() = runTest(dispatcher) {
        pairs.saved = CurrencyPair(Currency.EUR, Currency.GBP)

        val viewModel = loadedViewModel()
        viewModel.onAmountChange("100")

        val state = viewModel.uiState.value
        assertEquals(Currency.EUR, state.from)
        assertEquals(Currency.GBP, state.to)
        assertEquals(Currency.GBP, state.result?.to)
        assertEquals(emptyList<CurrencyPair>(), pairs.writes)
    }

    @Test
    fun `pair restoration is reported once the saved pair has been read`() = runTest(dispatcher) {
        pairs.saved = CurrencyPair(Currency.EUR, Currency.GBP)
        val slowRead = CompletableDeferred<Unit>().also { pairs.readGate = it }
        val viewModel = loadedViewModel()
        assertFalse(viewModel.isPairRestored.value)

        slowRead.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.isPairRestored.value)
        assertEquals(Currency.EUR, viewModel.uiState.value.from)
    }

    @Test
    fun `pair restoration is reported even when nothing was saved`() = runTest(dispatcher) {
        assertTrue(loadedViewModel().isPairRestored.value)
    }

    // Startup wait (MainActivity shows the screen once awaitPairRestored returns).

    @Test
    fun `startup wait ends at once when nothing is saved`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val start = dispatcher.scheduler.currentTime

        assertTrue(viewModel.awaitPairRestored(startupWait))
        assertEquals("no waiting", start, dispatcher.scheduler.currentTime)
        assertEquals(Currency.MAD, viewModel.uiState.value.from)
    }

    @Test
    fun `startup wait ends as soon as a fast read restores the pair`() = runTest(dispatcher) {
        pairs.saved = CurrencyPair(Currency.EUR, Currency.GBP)
        val slowRead = CompletableDeferred<Unit>().also { pairs.readGate = it }
        val viewModel = loadedViewModel()
        val waited = async { viewModel.awaitPairRestored(startupWait) }

        dispatcher.scheduler.advanceTimeBy(30)
        slowRead.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertTrue(waited.isCompleted)
        assertTrue(waited.await())
        assertEquals("shown at 30ms, not at the limit", 30L, dispatcher.scheduler.currentTime)
        assertEquals(Currency.EUR, viewModel.uiState.value.from)
    }

    @Test
    fun `a read slower than the startup wait shows the default, then the saved pair`() = runTest(dispatcher) {
        pairs.saved = CurrencyPair(Currency.EUR, Currency.GBP)
        val slowRead = CompletableDeferred<Unit>().also { pairs.readGate = it }
        val viewModel = loadedViewModel()
        val waited = async { viewModel.awaitPairRestored(startupWait) }

        dispatcher.scheduler.advanceTimeBy(startupWait - 1)
        dispatcher.scheduler.runCurrent()
        assertFalse("still waiting just before the limit", waited.isCompleted)

        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        assertTrue("never waits longer than the limit", waited.isCompleted)
        assertFalse(waited.await())
        // The screen now shows the default pair...
        assertEquals(Currency.MAD, viewModel.uiState.value.from)
        assertEquals(Currency.USD, viewModel.uiState.value.to)

        // ...and the saved pair once the read finishes (300ms later here).
        dispatcher.scheduler.advanceTimeBy(300)
        slowRead.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(Currency.EUR, viewModel.uiState.value.from)
        assertEquals(Currency.GBP, viewModel.uiState.value.to)
        assertTrue(viewModel.isPairRestored.value)
        assertEquals("restoring is not a user change", emptyList<CurrencyPair>(), pairs.writes)
    }

    @Test
    fun `a user choice after the startup wait beats a late read`() = runTest(dispatcher) {
        pairs.saved = CurrencyPair(Currency.EUR, Currency.GBP)
        val slowRead = CompletableDeferred<Unit>().also { pairs.readGate = it }
        val viewModel = loadedViewModel()
        val waited = async { viewModel.awaitPairRestored(startupWait) }
        dispatcher.scheduler.advanceTimeBy(startupWait)
        dispatcher.scheduler.runCurrent()
        assertFalse(waited.await())

        viewModel.onToCurrencySelected(Currency.JPY)
        slowRead.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(Currency.MAD, viewModel.uiState.value.from)
        assertEquals(Currency.JPY, viewModel.uiState.value.to)
        assertEquals(CurrencyPair(Currency.MAD, Currency.JPY), pairs.saved)
    }

    @Test
    fun `an unreadable saved pair keeps the default and ends the startup wait`() = runTest(dispatcher) {
        pairs.saved = CurrencyPair(Currency.EUR, Currency.GBP)
        pairs.failRead = true

        val viewModel = loadedViewModel()

        assertTrue(viewModel.isPairRestored.value)
        assertTrue(viewModel.awaitPairRestored(startupWait))
        assertEquals(Currency.MAD, viewModel.uiState.value.from)
        assertEquals(Currency.USD, viewModel.uiState.value.to)
        assertEquals(emptyList<CurrencyPair>(), pairs.writes)
    }

    @Test
    fun `after a rotation the startup wait returns at once`() = runTest(dispatcher) {
        pairs.saved = CurrencyPair(Currency.EUR, Currency.GBP)
        val viewModel = loadedViewModel()
        assertTrue(viewModel.awaitPairRestored(startupWait))
        val start = dispatcher.scheduler.currentTime

        // The new Activity asks the same (retained) ViewModel again.
        assertTrue(viewModel.awaitPairRestored(startupWait))
        assertEquals(start, dispatcher.scheduler.currentTime)
    }

    @Test
    fun `source and target selections are saved`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onFromCurrencySelected(Currency.EUR)
        dispatcher.scheduler.runCurrent()
        assertEquals(CurrencyPair(Currency.EUR, Currency.USD), pairs.saved)

        viewModel.onToCurrencySelected(Currency.JPY)
        dispatcher.scheduler.runCurrent()
        assertEquals(CurrencyPair(Currency.EUR, Currency.JPY), pairs.saved)
    }

    @Test
    fun `picking the currency of the other side saves the swapped pair`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onToCurrencySelected(Currency.MAD)
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(CurrencyPair(Currency.USD, Currency.MAD)), pairs.writes)
    }

    @Test
    fun `swap is saved as one pair`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onSwapCurrencies()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(CurrencyPair(Currency.USD, Currency.MAD)), pairs.writes)
    }

    @Test
    fun `restoring a history entry saves both currencies in one write`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onHistoryEntrySelected(
            historyEntry(from = Currency.EUR, to = Currency.MAD, amount = "250", converted = "2795.20"),
        )
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(CurrencyPair(Currency.EUR, Currency.MAD)), pairs.writes)
    }

    @Test
    fun `choosing a quick conversion saves the new target`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")

        viewModel.onQuickConversionSelected(Currency.EUR)
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(CurrencyPair(Currency.MAD, Currency.EUR)), pairs.writes)
    }

    @Test
    fun `typing, favorites, fees and retry never save the pair`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onAmountChange("1000")
        viewModel.onToggleFavorite(Currency.GBP)
        viewModel.onFeePercentChanged(BigDecimal("2"))
        viewModel.onFeePercentCommitted()
        viewModel.onRetry()
        dispatcher.scheduler.runCurrent()

        assertEquals(emptyList<CurrencyPair>(), pairs.writes)
    }

    @Test
    fun `a saved pair read after the user changed the pair is ignored`() = runTest(dispatcher) {
        pairs.saved = CurrencyPair(Currency.GBP, Currency.JPY)
        val slowRead = CompletableDeferred<Unit>().also { pairs.readGate = it }
        val viewModel = loadedViewModel()
        assertEquals("not restored yet", Currency.MAD, viewModel.uiState.value.from)

        // The user picks EUR -> MAD before the saved pair has been read.
        viewModel.onFromCurrencySelected(Currency.EUR)
        viewModel.onToCurrencySelected(Currency.MAD)
        slowRead.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(Currency.EUR, viewModel.uiState.value.from)
        assertEquals(Currency.MAD, viewModel.uiState.value.to)
        assertEquals(CurrencyPair(Currency.EUR, Currency.MAD), pairs.saved)
    }

    @Test
    fun `rapid changes end with the final pair saved last`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onToCurrencySelected(Currency.EUR)
        viewModel.onSwapCurrencies()
        viewModel.onToCurrencySelected(Currency.GBP)
        viewModel.onFromCurrencySelected(Currency.JPY)
        dispatcher.scheduler.runCurrent()

        assertEquals(CurrencyPair(Currency.JPY, Currency.GBP), pairs.writes.last())
        assertEquals(CurrencyPair(Currency.JPY, Currency.GBP), pairs.saved)
    }

    @Test
    fun `a slow write cannot overwrite a newer pair`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val slowWrite = CompletableDeferred<Unit>().also { pairs.saveGate = it }

        viewModel.onToCurrencySelected(Currency.EUR)
        dispatcher.scheduler.runCurrent()
        assertEquals("first write started and is waiting", 1, pairs.saveCalls)

        viewModel.onToCurrencySelected(Currency.GBP)
        viewModel.onSwapCurrencies()
        dispatcher.scheduler.runCurrent()
        // One writer: newer pairs wait, so they can never be overtaken by the older write.
        assertEquals("no second write while the first is running", 1, pairs.saveCalls)
        slowWrite.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(
            listOf(CurrencyPair(Currency.MAD, Currency.EUR), CurrencyPair(Currency.GBP, Currency.MAD)),
            pairs.writes,
        )
        assertEquals(CurrencyPair(Currency.GBP, Currency.MAD), pairs.saved)
        assertEquals(Currency.GBP, viewModel.uiState.value.from)
    }

    @Test
    fun `a failed write keeps the pair in memory and later changes are still saved`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        pairs.failNextSave = true

        viewModel.onToCurrencySelected(Currency.EUR)
        dispatcher.scheduler.runCurrent()
        assertEquals(Currency.EUR, viewModel.uiState.value.to)
        assertNull(pairs.saved)

        viewModel.onToCurrencySelected(Currency.GBP)
        dispatcher.scheduler.runCurrent()
        assertEquals(CurrencyPair(Currency.MAD, Currency.GBP), pairs.saved)
    }

    @Test
    fun `a new process restores the last saved pair`() = runTest(dispatcher) {
        val first = loadedViewModel()
        first.onToCurrencySelected(Currency.EUR)
        first.onSwapCurrencies()
        dispatcher.scheduler.runCurrent()

        // Same store, new ViewModel: what a process restart sees.
        val second = loadedViewModel()

        assertEquals(Currency.EUR, second.uiState.value.from)
        assertEquals(Currency.MAD, second.uiState.value.to)
    }

    // --- Storage write failures ---

    @Test
    fun `a failed favorite write keeps the stored favorites and a later toggle works`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        favorites.failNextWrite = true

        viewModel.onToggleFavorite(Currency.GBP)
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf(Currency.MAD, Currency.EUR, Currency.USD), viewModel.uiState.value.favorites)

        viewModel.onToggleFavorite(Currency.GBP)
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf(Currency.MAD, Currency.EUR, Currency.USD, Currency.GBP), viewModel.uiState.value.favorites)
    }

    @Test
    fun `a failed history write records nothing, keeps the result and a later copy is recorded`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")
        history.failingWrites = 1

        viewModel.onResultCopied()
        dispatcher.scheduler.runCurrent()
        assertEquals(emptyList<Pair<Conversion, Boolean>>(), history.recorded)
        assertEquals(BigDecimal("100.81"), viewModel.uiState.value.result?.convertedAmount)

        viewModel.onResultShared()
        dispatcher.scheduler.runCurrent()
        assertEquals(BigDecimal("100.81"), history.recorded.single().first.convertedAmount)
    }

    @Test
    fun `failed history delete and clear leave the entries and can be repeated`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        history.failingWrites = 2

        viewModel.onHistoryEntryDeleted("abc")
        viewModel.onHistoryCleared()
        dispatcher.scheduler.runCurrent()
        assertEquals(emptyList<String>(), history.deleted)
        assertEquals(0, history.clears)

        viewModel.onHistoryEntryDeleted("abc")
        viewModel.onHistoryCleared()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("abc"), history.deleted)
        assertEquals(1, history.clears)
    }

    @Test
    fun `a failed fee save keeps the fee in memory and committing it again retries`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAmountChange("1000")
        fees.failNextWrite = true

        viewModel.onFeePercentChanged(BigDecimal("2"))
        viewModel.onFeePercentCommitted()
        dispatcher.scheduler.runCurrent()
        assertEquals(emptyList<BigDecimal>(), fees.writes)
        assertEquals(BigDecimal("2"), viewModel.uiState.value.feePercent)
        assertEquals(BigDecimal("2.02"), viewModel.uiState.value.realCost!!.fee)

        // Same fee again (e.g. Real Cost closed once more): not skipped as already saved.
        viewModel.onFeePercentCommitted()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(BigDecimal("2")), fees.writes)

        viewModel.onFeePercentCommitted()
        dispatcher.scheduler.runCurrent()
        assertEquals("saved now, so no redundant write", listOf(BigDecimal("2")), fees.writes)
    }

    @Test
    fun `a failed save of an older fee does not force a newer saved fee to be rewritten`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        fees.failNextWrite = true

        // The 1% save fails after 3% was already committed.
        viewModel.onFeePercentChanged(BigDecimal("1"))
        viewModel.onFeePercentCommitted()
        viewModel.onFeePercentChanged(BigDecimal("3"))
        viewModel.onFeePercentCommitted()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(BigDecimal("3")), fees.writes)

        viewModel.onFeePercentCommitted()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(BigDecimal("3")), fees.writes)
    }

    private fun loadedViewModel(rates: RateSnapshot = snapshot): CurrencyConverterViewModel {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees, pairs)
        repository.complete(RatesResult.Available(rates, isStale = false))
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    /** Each call suspends until the test completes it, so loading states can be observed. */
    private class FakeRepository : CurrencyRepository {
        var calls = 0
            private set
        private var pending = CompletableDeferred<RatesResult>()

        override suspend fun getRates(): RatesResult {
            calls++
            return pending.await().also { pending = CompletableDeferred() }
        }

        fun complete(result: RatesResult) {
            pending.complete(result)
        }
    }
}
