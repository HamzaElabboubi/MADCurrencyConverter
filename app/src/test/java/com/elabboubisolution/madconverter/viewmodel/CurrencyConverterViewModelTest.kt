package com.elabboubisolution.madconverter.viewmodel

import com.elabboubisolution.madconverter.data.local.FavoritesStore
import com.elabboubisolution.madconverter.data.local.FeePreferenceStore
import com.elabboubisolution.madconverter.data.local.HistoryStore
import com.elabboubisolution.madconverter.data.repository.CurrencyRepository
import com.elabboubisolution.madconverter.data.repository.RatesResult
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import com.elabboubisolution.madconverter.testing.SampleRates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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
    private val history = FakeHistoryStore()
    private val fees = FakeFeePreferenceStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts loading then shows rate once loaded`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
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

    private fun loadedViewModel(rates: RateSnapshot = snapshot): CurrencyConverterViewModel {
        val repository = FakeRepository()
        val viewModel = CurrencyConverterViewModel(repository, favorites, history, fees)
        repository.complete(RatesResult.Available(rates, isStale = false))
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    private class FakeFeePreferenceStore : FeePreferenceStore {
        var saved: BigDecimal = BigDecimal.ZERO
        val writes = mutableListOf<BigDecimal>()
        override val feePercent get() = flowOf(saved)

        override suspend fun setFeePercent(percent: BigDecimal) {
            writes += percent
            saved = percent
        }
    }

    private class FakeHistoryStore : HistoryStore {
        val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
        override val entries: StateFlow<List<HistoryEntry>> = state
        val recorded = mutableListOf<Pair<Conversion, Boolean>>()
        val deleted = mutableListOf<String>()
        var clears = 0
            private set

        override suspend fun record(conversion: Conversion, wasStale: Boolean) {
            recorded += conversion to wasStale
        }

        override suspend fun delete(id: String) {
            deleted += id
        }

        override suspend fun clear() {
            clears++
        }
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
