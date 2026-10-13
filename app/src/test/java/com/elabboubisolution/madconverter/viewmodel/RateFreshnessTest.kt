package com.elabboubisolution.madconverter.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.elabboubisolution.madconverter.data.local.RateCache
import com.elabboubisolution.madconverter.data.remote.RateProvider
import com.elabboubisolution.madconverter.data.repository.CachingCurrencyRepository
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateFetchResult
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds

/**
 * Rate freshness while the app stays alive (foreground returns, the visible-screen timer and
 * their interaction with loads), with the real repository and rate cache. Virtual time drives
 * both the clock and the timers, so nothing depends on real delays.
 *
 * Timeline (UTC): the app starts on 2026-10-05 at 10:00 and downloads [SampleRates.snapshot];
 * it stays current until its announced next update, 2026-10-06 00:21:01. Midnight in between.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RateFreshnessTest {

    private val dispatcher = StandardTestDispatcher()
    private val scheduler get() = dispatcher.scheduler
    private fun now() = START + scheduler.currentTime

    private val provider = FakeProvider()
    private val store = InMemoryDataStore()
    private val repository = CachingCurrencyRepository(provider, RateCache(store, Json), clock = ::now)
    private val history = FakeHistoryStore()

    private val snapshot = SampleRates.snapshot
    private val nextUpdate = checkNotNull(snapshot.nextUpdateEpochSeconds) * 1000
    private val newer = snapshot.copy(
        lastUpdatedEpochSeconds = checkNotNull(snapshot.nextUpdateEpochSeconds),
        nextUpdateEpochSeconds = checkNotNull(snapshot.nextUpdateEpochSeconds) + 86_400,
    )

    private val viewModels = mutableListOf<CurrencyConverterViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    /**
     * runTest, ending with every view model out of the foreground: while visible, its timer
     * always schedules the next check, so virtual time would never become idle.
     */
    private fun freshnessTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher, timeout = 30.seconds) {
        try {
            block()
        } finally {
            viewModels.forEach { it.onBackground() }
        }
    }

    private fun newViewModel() = CurrencyConverterViewModel(
        repository,
        FakeFavoritesStore(),
        history,
        FakeFeePreferenceStore(),
        FakeCurrencyPairStore(),
        clock = ::now,
        timeZone = { UTC },
    ).also { viewModels += it }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Started at [START] with current rates downloaded; not yet in the foreground. */
    private fun startedViewModel(): CurrencyConverterViewModel {
        provider.next = RateFetchResult.Success(snapshot)
        val viewModel = newViewModel()
        scheduler.runCurrent()
        assertEquals(1, provider.calls)
        assertFalse(viewModel.uiState.value.isStale)
        return viewModel
    }

    private fun advanceTo(millis: Long) {
        scheduler.advanceTimeBy(millis - now())
        scheduler.runCurrent()
    }

    private fun CurrencyConverterViewModel.returnToForeground() {
        onForeground()
        scheduler.runCurrent()
    }

    // 1
    @Test
    fun `foreground return with current rates makes no request`() = freshnessTest {
        val viewModel = startedViewModel()
        viewModel.returnToForeground()
        viewModel.onBackground()

        advanceTo(START + 3 * HOUR)
        viewModel.returnToForeground()

        assertEquals(1, provider.calls)
        assertFalse(viewModel.uiState.value.isStale)
    }

    // 2, 8 and state preservation
    @Test
    fun `foreground return after the rates are due refreshes them once, silently`() = freshnessTest {
        val viewModel = startedViewModel()
        viewModel.onFromCurrencySelected(Currency.EUR)
        viewModel.onAmountChange("250")
        advanceTo(nextUpdate + HOUR)
        provider.next = RateFetchResult.Success(newer)
        provider.gate = CompletableDeferred()

        viewModel.returnToForeground()
        val during = viewModel.uiState.value
        assertFalse("silent: no progress indicator", during.isLoading)
        assertEquals(
            RatePanel.Available(isStale = false, refreshError = null, isRefreshing = false, missingRate = null, missingRateRetry = false),
            ratePanel(during),
        )
        provider.gate?.complete(Unit)
        scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(2, provider.calls)
        assertEquals(newer.lastUpdatedEpochSeconds, state.lastUpdatedEpochSeconds)
        assertFalse(state.isStale)
        assertNull(state.refreshError)
        assertEquals(Currency.EUR, state.from)
        assertEquals(Currency.USD, state.to)
        assertEquals("250", state.amountInput)
        assertEquals(BigDecimal("280.88"), state.result?.convertedAmount)
    }

    // 3, 9
    @Test
    fun `offline foreground return keeps the rates usable and marks them stale`() = freshnessTest {
        val viewModel = startedViewModel()
        viewModel.onAmountChange("1000")
        advanceTo(nextUpdate + HOUR)
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)

        viewModel.returnToForeground()

        val state = viewModel.uiState.value
        assertEquals(2, provider.calls)
        assertTrue(state.isStale)
        assertEquals(RateFetchError.NoConnection, state.refreshError)
        assertNull(state.error)
        assertEquals(BigDecimal("100.81"), state.result?.convertedAmount)

        viewModel.onResultCopied()
        viewModel.onResultShared()
        scheduler.runCurrent()
        assertEquals(listOf(true, true), history.recorded.map { it.second })
    }

    // 4
    @Test
    fun `rapid foreground returns make one request, and a failure is not retried at once`() = freshnessTest {
        val viewModel = startedViewModel()
        advanceTo(nextUpdate + HOUR)
        provider.next = RateFetchResult.Failure(RateFetchError.Timeout)
        provider.gate = CompletableDeferred()

        repeat(3) {
            viewModel.returnToForeground()
            viewModel.onBackground()
        }
        viewModel.returnToForeground()
        assertEquals(2, provider.calls)
        provider.gate?.complete(Unit)
        scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isStale)

        provider.gate = null
        repeat(3) {
            viewModel.onBackground()
            viewModel.returnToForeground()
        }
        assertEquals("within the pause after a failure", 2, provider.calls)

        viewModel.onBackground()
        advanceTo(now() + CurrencyConverterViewModel.AUTO_REFRESH_BACKOFF_MILLIS)
        viewModel.returnToForeground()
        assertEquals(3, provider.calls)
    }

    // 5
    @Test
    fun `foreground return during the initial load makes no second request`() = freshnessTest {
        provider.next = RateFetchResult.Success(snapshot)
        provider.gate = CompletableDeferred()
        val viewModel = newViewModel()
        scheduler.runCurrent()

        viewModel.returnToForeground()
        assertTrue("the initial load keeps its placeholder", viewModel.uiState.value.isLoading)
        provider.gate?.complete(Unit)
        scheduler.runCurrent()

        assertEquals(1, provider.calls)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(snapshot.lastUpdatedEpochSeconds, viewModel.uiState.value.lastUpdatedEpochSeconds)
    }

    // 5, Retry
    @Test
    fun `retry during a foreground check joins it, shows progress, and makes no second request`() = freshnessTest {
        val viewModel = startedViewModel()
        advanceTo(nextUpdate + HOUR)
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        viewModel.returnToForeground()
        assertTrue(viewModel.uiState.value.isStale)

        // Retry is not held back by the pause after a failure.
        provider.next = RateFetchResult.Success(newer)
        provider.gate = CompletableDeferred()
        viewModel.onRetry()
        scheduler.runCurrent()
        assertEquals(3, provider.calls)
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onRetry()
        viewModel.returnToForeground()
        provider.gate?.complete(Unit)
        scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(3, provider.calls)
        assertFalse(state.isLoading)
        assertFalse(state.isStale)
        assertNull(state.refreshError)
    }

    // 5, Retry
    @Test
    fun `retry pressed during a silent check shows progress until that check ends`() = freshnessTest {
        val viewModel = startedViewModel()
        advanceTo(nextUpdate + HOUR)
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        viewModel.returnToForeground()
        viewModel.onBackground()
        advanceTo(now() + CurrencyConverterViewModel.AUTO_REFRESH_BACKOFF_MILLIS)
        provider.gate = CompletableDeferred()
        viewModel.returnToForeground()
        assertFalse("the check itself is silent", viewModel.uiState.value.isLoading)

        viewModel.onRetry()
        scheduler.runCurrent()
        assertEquals("no second request", 3, provider.calls)
        assertTrue("Retry shows its progress", viewModel.uiState.value.isLoading)
        val panel = ratePanel(viewModel.uiState.value) as RatePanel.Available
        assertTrue("spinner in the stale banner", panel.isStale && panel.isRefreshing)

        provider.next = RateFetchResult.Success(newer)
        provider.gate?.complete(Unit)
        scheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isStale)
        assertEquals(3, provider.calls)
    }

    // 6, 12
    @Test
    fun `while visible, checks at midnight then refreshes at the deadline`() = freshnessTest {
        val viewModel = startedViewModel()
        viewModel.returnToForeground()
        assertEquals(START, viewModel.uiState.value.freshnessCheckedAtMillis)
        provider.next = RateFetchResult.Success(newer)

        advanceTo(MIDNIGHT)
        assertEquals("midnight: new 'now' for the label, no request", MIDNIGHT, viewModel.uiState.value.freshnessCheckedAtMillis)
        assertEquals(1, provider.calls)

        advanceTo(nextUpdate - 1)
        assertEquals(1, provider.calls)
        advanceTo(nextUpdate)
        assertEquals(2, provider.calls)
        assertEquals(newer.lastUpdatedEpochSeconds, viewModel.uiState.value.lastUpdatedEpochSeconds)
        assertEquals(nextUpdate, viewModel.uiState.value.freshnessCheckedAtMillis)

        // The newer rates are current for a day: the next wake-up is the following midnight.
        advanceTo(MIDNIGHT + DAY - 1)
        assertEquals(2, provider.calls)
        assertEquals(nextUpdate, viewModel.uiState.value.freshnessCheckedAtMillis)
    }

    // 6 (failure while visible); 13.4: exactly one recovery, no repeat after a second failure
    @Test
    fun `a failed refresh while visible is retried once after the pause, not in a loop`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()

        advanceTo(nextUpdate + BACKOFF - 1)
        assertEquals(2, provider.calls)
        advanceTo(nextUpdate + BACKOFF)
        assertEquals("one recovery attempt", 3, provider.calls)
        assertTrue("still failing: the stale state stays", viewModel.uiState.value.isStale)
        assertEquals(RateFetchError.NoConnection, viewModel.uiState.value.refreshError)

        advanceTo(MIDNIGHT + DAY - 1)
        assertEquals("no second recovery for the same failures", 3, provider.calls)
        advanceTo(MIDNIGHT + DAY)
        assertEquals("the midnight check", 4, provider.calls)
        advanceTo(MIDNIGHT + DAY + 3 * HOUR)
        assertEquals("a failed midnight check does not arm another recovery", 4, provider.calls)
    }

    // 13.4: successful recovery
    @Test
    fun `a successful recovery clears the stale state silently and resumes normal scheduling`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()
        provider.next = RateFetchResult.Success(newer)
        provider.gate = CompletableDeferred()

        advanceTo(nextUpdate + BACKOFF)
        assertEquals(3, provider.calls)
        val during = viewModel.uiState.value
        assertFalse("silent: no progress indicator", during.isLoading)
        assertEquals(
            RatePanel.Available(isStale = true, refreshError = RateFetchError.NoConnection, isRefreshing = false, missingRate = null, missingRateRetry = false),
            ratePanel(during),
        )
        provider.gate?.complete(Unit)
        provider.gate = null
        scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isStale)
        assertNull(state.refreshError)
        assertEquals(newer.lastUpdatedEpochSeconds, state.lastUpdatedEpochSeconds)

        // Normal scheduling: the newer rates are refreshed at their own deadline, and a new
        // failure there gets its own recovery attempt.
        val newerDeadline = checkNotNull(newer.nextUpdateEpochSeconds) * 1000
        provider.next = RateFetchResult.Failure(RateFetchError.Timeout)
        advanceTo(newerDeadline - 1)
        assertEquals(3, provider.calls)
        advanceTo(newerDeadline)
        assertEquals(4, provider.calls)
        advanceTo(newerDeadline + BACKOFF)
        assertEquals(5, provider.calls)
    }

    // 13.4: background cancellation
    @Test
    fun `leaving the foreground cancels the pending recovery`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()
        viewModel.onBackground()

        advanceTo(nextUpdate + 3 * HOUR)
        assertEquals(2, provider.calls)
        assertTrue(viewModel.uiState.value.isStale)
    }

    // 13.4: foreground return during the pause
    @Test
    fun `a foreground return during the pause keeps one recovery at its end`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()
        viewModel.onBackground()
        advanceTo(nextUpdate + 5 * MINUTE)
        viewModel.returnToForeground()
        assertEquals("not before the pause ends", 2, provider.calls)
        viewModel.onBackground()
        viewModel.returnToForeground()

        advanceTo(nextUpdate + BACKOFF)
        assertEquals(3, provider.calls)
        advanceTo(MIDNIGHT + DAY - 1)
        assertEquals(3, provider.calls)
    }

    // 13.4: foreground return after the pause
    @Test
    fun `a foreground return after the pause checks at once, then recovers once if that fails`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()
        viewModel.onBackground()
        val returnAt = nextUpdate + BACKOFF + 10 * MINUTE
        advanceTo(returnAt)
        assertEquals(2, provider.calls)

        viewModel.returnToForeground()
        assertEquals("immediate silent check", 3, provider.calls)
        assertFalse(viewModel.uiState.value.isLoading)

        // The recovery was never made (the screen was hidden when it was due): it follows this failure.
        advanceTo(returnAt + BACKOFF - 1)
        assertEquals(3, provider.calls)
        advanceTo(returnAt + BACKOFF)
        assertEquals(4, provider.calls)
        advanceTo(MIDNIGHT + DAY - 1)
        assertEquals(4, provider.calls)
    }

    // 13.4: manual Retry during the pause
    @Test
    fun `a failed Retry during the pause postpones the single recovery`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()
        val retryAt = nextUpdate + 5 * MINUTE
        advanceTo(retryAt)

        viewModel.onRetry()
        scheduler.runCurrent()
        assertEquals("Retry is not held back by the pause", 3, provider.calls)
        assertTrue(viewModel.uiState.value.isStale)

        advanceTo(nextUpdate + BACKOFF)
        assertEquals("not 15 minutes after the first failure", 3, provider.calls)
        advanceTo(retryAt + BACKOFF)
        assertEquals("15 minutes after the Retry", 4, provider.calls)
        advanceTo(MIDNIGHT + DAY - 1)
        assertEquals(4, provider.calls)
    }

    // 13.4: manual Retry during the pause
    @Test
    fun `a successful Retry during the pause cancels the recovery`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()
        advanceTo(nextUpdate + 5 * MINUTE)
        provider.next = RateFetchResult.Success(newer)

        viewModel.onRetry()
        scheduler.runCurrent()
        assertEquals(3, provider.calls)
        assertFalse(viewModel.uiState.value.isStale)

        advanceTo(nextUpdate + 3 * HOUR)
        assertEquals(3, provider.calls)
    }

    // 13.4: Retry joining the in-flight recovery
    @Test
    fun `Retry during the recovery joins it, shows progress and makes no second request`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()
        provider.gate = CompletableDeferred()
        advanceTo(nextUpdate + BACKOFF)
        assertEquals(3, provider.calls)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.onRetry()
        viewModel.onBackground()
        viewModel.returnToForeground()
        scheduler.runCurrent()
        assertEquals("joins the running recovery", 3, provider.calls)
        assertTrue("Retry shows its progress", viewModel.uiState.value.isLoading)

        provider.next = RateFetchResult.Success(newer)
        provider.gate?.complete(Unit)
        provider.gate = null
        scheduler.runCurrent()
        assertEquals(3, provider.calls)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isStale)
    }

    // 13.4: the pause ending while a Retry is running
    @Test
    fun `a recovery due while a Retry is running waits for it instead of spinning`() = freshnessTest {
        val viewModel = visibleViewModelFailedAtDeadline()
        advanceTo(nextUpdate + BACKOFF - MINUTE)
        provider.gate = CompletableDeferred()
        viewModel.onRetry()
        scheduler.runCurrent()
        assertEquals(3, provider.calls)

        // Past the end of the first pause, the Retry still running.
        advanceTo(nextUpdate + BACKOFF + 5 * MINUTE)
        assertEquals(3, provider.calls)
        val retryEndedAt = now()
        provider.gate?.complete(Unit)
        provider.gate = null
        scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isStale)

        advanceTo(retryEndedAt + BACKOFF)
        assertEquals(4, provider.calls)
        advanceTo(MIDNIGHT + DAY - 1)
        assertEquals(4, provider.calls)
    }

    // 13.4: no-cache recovery
    @Test
    fun `no rates at all - the recovery loads them silently while the error card stays`() = freshnessTest {
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        val viewModel = newViewModel()
        scheduler.runCurrent()
        viewModel.returnToForeground()
        assertEquals(1, provider.calls)

        provider.next = RateFetchResult.Success(snapshot)
        provider.gate = CompletableDeferred()
        advanceTo(START + BACKOFF - 1)
        assertEquals(1, provider.calls)
        advanceTo(START + BACKOFF)
        assertEquals(2, provider.calls)
        assertEquals(
            "silent: the error card stays without a spinner",
            RatePanel.Unavailable(RateFetchError.NoConnection, isRetrying = false),
            ratePanel(viewModel.uiState.value),
        )

        provider.gate?.complete(Unit)
        provider.gate = null
        scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.hasRates)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isStale)
    }

    // 13.4: no-cache recovery that fails
    @Test
    fun `no rates at all - a failed recovery keeps the error card and is not repeated`() = freshnessTest {
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        val viewModel = newViewModel()
        scheduler.runCurrent()
        viewModel.returnToForeground()

        provider.next = RateFetchResult.Failure(RateFetchError.Timeout)
        advanceTo(START + BACKOFF)
        assertEquals(2, provider.calls)
        assertEquals(RatePanel.Unavailable(RateFetchError.Timeout, isRetrying = false), ratePanel(viewModel.uiState.value))

        advanceTo(MIDNIGHT - 1)
        assertEquals(2, provider.calls)
    }

    // 13.4: midnight before the end of the pause
    @Test
    fun `midnight during the pause does not add or advance the recovery`() = freshnessTest {
        val viewModel = visibleViewModelFailedAt(MIDNIGHT - 10 * MINUTE)

        advanceTo(MIDNIGHT)
        assertEquals("midnight: label only", 2, provider.calls)
        assertEquals(MIDNIGHT, viewModel.uiState.value.freshnessCheckedAtMillis)
        advanceTo(MIDNIGHT + 5 * MINUTE - 1)
        assertEquals(2, provider.calls)
        advanceTo(MIDNIGHT + 5 * MINUTE)
        assertEquals(3, provider.calls)
        advanceTo(MIDNIGHT + DAY - 1)
        assertEquals(3, provider.calls)
    }

    // 13.4: the pause ending exactly at midnight
    @Test
    fun `a recovery due exactly at midnight makes one request`() = freshnessTest {
        val viewModel = visibleViewModelFailedAt(MIDNIGHT - BACKOFF)

        advanceTo(MIDNIGHT)
        assertEquals(3, provider.calls)
        assertEquals(MIDNIGHT, viewModel.uiState.value.freshnessCheckedAtMillis)
        advanceTo(MIDNIGHT + DAY - 1)
        assertEquals("that wake-up was the recovery: none follows", 3, provider.calls)
    }

    /**
     * Visible, with the rates downloaded at [START] (due at [nextUpdate]) refreshed and failing
     * at that deadline: stale, in the pause after the failure.
     */
    private fun visibleViewModelFailedAtDeadline(): CurrencyConverterViewModel {
        val viewModel = startedViewModel()
        viewModel.returnToForeground()
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        advanceTo(nextUpdate)
        assertEquals(2, provider.calls)
        assertTrue(viewModel.uiState.value.isStale)
        return viewModel
    }

    /** Like [visibleViewModelFailedAtDeadline], with rates announcing their next update at [deadline]. */
    private fun visibleViewModelFailedAt(deadline: Long): CurrencyConverterViewModel {
        provider.next = RateFetchResult.Success(snapshot.copy(nextUpdateEpochSeconds = deadline / 1000))
        val viewModel = newViewModel()
        scheduler.runCurrent()
        viewModel.returnToForeground()
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        advanceTo(deadline)
        assertEquals(2, provider.calls)
        assertTrue(viewModel.uiState.value.isStale)
        return viewModel
    }

    // 7
    @Test
    fun `leaving the foreground cancels the scheduled check`() = freshnessTest {
        val viewModel = startedViewModel()
        viewModel.returnToForeground()
        viewModel.onBackground()

        advanceTo(nextUpdate + 2 * DAY)
        assertEquals(1, provider.calls)
        assertEquals(START, viewModel.uiState.value.freshnessCheckedAtMillis)

        provider.next = RateFetchResult.Success(newer)
        viewModel.returnToForeground()
        assertEquals(2, provider.calls)
    }

    // 8
    @Test
    fun `back online after the pause, a foreground return clears the stale warning`() = freshnessTest {
        val viewModel = startedViewModel()
        advanceTo(nextUpdate + HOUR)
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        viewModel.returnToForeground()
        viewModel.onBackground()
        assertTrue(viewModel.uiState.value.isStale)

        advanceTo(now() + CurrencyConverterViewModel.AUTO_REFRESH_BACKOFF_MILLIS)
        provider.next = RateFetchResult.Success(newer)
        viewModel.returnToForeground()

        val state = viewModel.uiState.value
        assertFalse(state.isStale)
        assertNull(state.refreshError)
        viewModel.onAmountChange("1000")
        viewModel.onResultCopied()
        scheduler.runCurrent()
        assertFalse(history.recorded.single().second)
    }

    // 10
    @Test
    fun `rates kept only in memory still respect the one hour limit`() = freshnessTest {
        store.writeFailure = IOException("disk full")
        val viewModel = startedViewModel()
        // The provider publishes late: the same rates again, their announced update already past.
        advanceTo(nextUpdate + MINUTE)
        provider.next = RateFetchResult.Success(snapshot)
        viewModel.returnToForeground()
        viewModel.onBackground()
        assertEquals(2, provider.calls)
        val downloadedAt = now()

        // Nothing was saved, and the rates are past their next update: only the in-memory copy
        // of the download keeps these foreground returns from requesting again.
        repeat(3) {
            advanceTo(now() + 15 * MINUTE)
            viewModel.returnToForeground()
            viewModel.onBackground()
        }
        assertEquals(2, provider.calls)
        assertFalse(viewModel.uiState.value.isStale)

        advanceTo(downloadedAt + HOUR)
        viewModel.returnToForeground()
        assertEquals(3, provider.calls)
    }

    // 11
    @Test
    fun `unreadable cache and failed refresh on foreground return keep the rates, marked stale`() = freshnessTest {
        val viewModel = startedViewModel()
        viewModel.onAmountChange("1000")
        advanceTo(nextUpdate + HOUR)
        store.readFailure = true
        provider.next = RateFetchResult.Failure(RateFetchError.Timeout)

        viewModel.returnToForeground()

        val state = viewModel.uiState.value
        assertTrue(state.hasRates)
        assertTrue(state.isStale)
        assertEquals(RateFetchError.Timeout, state.refreshError)
        assertNull("never the no-rates error card", state.error)
        assertEquals(BigDecimal("100.81"), state.result?.convertedAmount)
    }

    // 12
    @Test
    fun `foreground return updates the time the last-updated label refers to`() = freshnessTest {
        val viewModel = startedViewModel()
        assertEquals("set by the load", START, viewModel.uiState.value.freshnessCheckedAtMillis)

        advanceTo(START + 3 * HOUR)
        viewModel.returnToForeground()

        assertEquals(START + 3 * HOUR, viewModel.uiState.value.freshnessCheckedAtMillis)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `no rates at all - a foreground return tries again after the pause, keeping the error card`() = freshnessTest {
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        val viewModel = newViewModel()
        scheduler.runCurrent()
        viewModel.returnToForeground()
        assertEquals("not right after the failed initial load", 1, provider.calls)
        viewModel.onBackground()

        advanceTo(now() + CurrencyConverterViewModel.AUTO_REFRESH_BACKOFF_MILLIS)
        provider.gate = CompletableDeferred()
        viewModel.returnToForeground()
        assertEquals(2, provider.calls)
        assertEquals(
            "silent: the error card stays without a spinner",
            RatePanel.Unavailable(RateFetchError.NoConnection, isRetrying = false),
            ratePanel(viewModel.uiState.value),
        )

        provider.next = RateFetchResult.Success(snapshot)
        provider.gate?.complete(Unit)
        scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.hasRates)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `no rates - retry during a silent check keeps the error card and shows progress`() = freshnessTest {
        provider.next = RateFetchResult.Failure(RateFetchError.NoConnection)
        val viewModel = newViewModel()
        scheduler.runCurrent()
        advanceTo(now() + CurrencyConverterViewModel.AUTO_REFRESH_BACKOFF_MILLIS)
        provider.gate = CompletableDeferred()
        viewModel.returnToForeground()
        assertEquals(2, provider.calls)

        viewModel.onRetry()
        scheduler.runCurrent()
        assertEquals("joins the running check", 2, provider.calls)
        assertEquals(RatePanel.Unavailable(RateFetchError.NoConnection, isRetrying = true), ratePanel(viewModel.uiState.value))

        provider.next = RateFetchResult.Failure(RateFetchError.Timeout)
        provider.gate?.complete(Unit)
        scheduler.runCurrent()
        assertEquals(RatePanel.Unavailable(RateFetchError.Timeout, isRetrying = false), ratePanel(viewModel.uiState.value))
        assertEquals(2, provider.calls)
    }

    private class FakeProvider : RateProvider {
        var next: RateFetchResult? = null

        /** When set, a request waits for it (slow network). */
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
            private set

        override suspend fun fetchLatestRates(base: Currency): RateFetchResult {
            calls++
            gate?.await()
            return checkNotNull(next) { "Unexpected network call" }
        }
    }

    /** Preferences kept in memory (no I/O thread), with reads and writes that can be made to fail. */
    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        var writeFailure: Throwable? = null
        var readFailure = false

        override val data: Flow<Preferences> = flow {
            if (readFailure) throw IOException("unreadable")
            emitAll(state)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writeFailure?.let { throw it }
            return transform(state.value).also { state.value = it }
        }
    }

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val BACKOFF = CurrencyConverterViewModel.AUTO_REFRESH_BACKOFF_MILLIS

        /** 2026-10-05 10:00 UTC. */
        const val START = 1_791_194_400_000L

        /** 2026-10-06 00:00 UTC. */
        const val MIDNIGHT = 1_791_244_800_000L
    }
}
