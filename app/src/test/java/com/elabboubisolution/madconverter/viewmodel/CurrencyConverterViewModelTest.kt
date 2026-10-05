package com.elabboubisolution.madconverter.viewmodel

import com.elabboubisolution.madconverter.data.repository.CurrencyRepository
import com.elabboubisolution.madconverter.data.repository.RatesResult
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val snapshot = RateSnapshot(
        base = Currency.MAD,
        rates = mapOf(
            Currency.MAD to BigDecimal("1"),
            Currency.USD to BigDecimal("0.100812"),
            Currency.EUR to BigDecimal("0.089729"),
            Currency.GBP to BigDecimal("0.076291"),
        ),
        lastUpdatedEpochSeconds = 1791158551L,
        nextUpdateEpochSeconds = 1791246061L,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts loading then shows rate once loaded`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository)
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
        val viewModel = CurrencyConverterViewModel(repository)
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
        val viewModel = CurrencyConverterViewModel(repository)
        repository.complete(RatesResult.Unavailable(RateFetchError.NoConnection))
        dispatcher.scheduler.runCurrent()

        assertEquals(RateFetchError.NoConnection, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.onRetry()
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        repository.complete(RatesResult.Available(snapshot, isStale = false))
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.error)
        assertEquals(2, repository.calls)
    }

    @Test
    fun `retry while a request is running does not start another`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository)
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
        val viewModel = CurrencyConverterViewModel(repository)
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
        val viewModel = CurrencyConverterViewModel(repository)
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

    private fun loadedViewModel(rates: RateSnapshot = snapshot): CurrencyConverterViewModel {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository)
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
