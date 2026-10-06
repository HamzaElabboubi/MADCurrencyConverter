package com.elabboubisolution.madconverter.viewmodel

import com.elabboubisolution.madconverter.data.local.FavoritesStore
import com.elabboubisolution.madconverter.data.repository.CurrencyRepository
import com.elabboubisolution.madconverter.data.repository.RatesResult
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import com.elabboubisolution.madconverter.testing.SampleRates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val snapshot = SampleRates.snapshot
    private val favorites = FakeFavoritesStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts loading then shows rate once loaded`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites)
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

    private fun loadedViewModel(rates: RateSnapshot = snapshot): CurrencyConverterViewModel {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites)
        repository.complete(RatesResult.Available(rates, isStale = false))
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    private class FakeFavoritesStore : FavoritesStore {
        private val state = MutableStateFlow(setOf(Currency.MAD, Currency.EUR, Currency.USD))
        override val favorites: StateFlow<Set<Currency>> = state

        /** Persisted writes, i.e. user favorite toggles. */
        var writes = 0
            private set

        override suspend fun setFavorite(currency: Currency, isFavorite: Boolean) {
            writes++
            state.value = if (isFavorite) state.value + currency else state.value - currency
        }

        /** Simulates favorites changed elsewhere (not counted as a write). */
        fun set(vararg currencies: Currency) {
            state.value = currencies.toSet()
        }
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
