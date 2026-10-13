package com.elabboubisolution.madconverter.testing

import com.elabboubisolution.madconverter.data.local.CurrencyPair
import com.elabboubisolution.madconverter.data.local.CurrencyPairStore
import com.elabboubisolution.madconverter.data.local.FavoritesStore
import com.elabboubisolution.madconverter.data.local.FeePreferenceStore
import com.elabboubisolution.madconverter.data.local.HistoryStore
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import java.io.IOException
import java.math.BigDecimal

// In-memory stores for view model tests, shared by every view model test class.

/** In-memory pair store; gates let a test hold a read or a write to stage a race. */
class FakeCurrencyPairStore : CurrencyPairStore {
    var saved: CurrencyPair? = null
    val writes = mutableListOf<CurrencyPair>()
    var saveCalls = 0
        private set

    /** When set, [read] waits for it (slow startup read). */
    var readGate: CompletableDeferred<Unit>? = null

    /** When set, [save] waits for it before writing (slow write). */
    var saveGate: CompletableDeferred<Unit>? = null

    var failNextSave = false

    var failRead = false

    /** Returns what was stored when the read started, like a slow disk read. */
    override suspend fun read(): CurrencyPair? {
        val stored = saved
        readGate?.await()
        if (failRead) throw IOException("unreadable")
        return stored
    }

    override suspend fun save(pair: CurrencyPair) {
        saveCalls++
        saveGate?.await()
        if (failNextSave) {
            failNextSave = false
            throw IOException("disk full")
        }
        writes += pair
        saved = pair
    }
}

class FakeFeePreferenceStore : FeePreferenceStore {
    var saved: BigDecimal = BigDecimal.ZERO
    val writes = mutableListOf<BigDecimal>()
    var failNextWrite = false
    override val feePercent get() = flowOf(saved)

    override suspend fun setFeePercent(percent: BigDecimal) {
        if (failNextWrite) {
            failNextWrite = false
            throw IOException("disk full")
        }
        writes += percent
        saved = percent
    }
}

class FakeHistoryStore : HistoryStore {
    val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
    override val entries: StateFlow<List<HistoryEntry>> = state
    val recorded = mutableListOf<Pair<Conversion, Boolean>>()
    val deleted = mutableListOf<String>()
    var clears = 0
        private set

    /** Number of next writes (record, delete or clear) that fail like a full disk. */
    var failingWrites = 0

    private fun failIfRequested() {
        if (failingWrites > 0) {
            failingWrites--
            throw IOException("disk full")
        }
    }

    override suspend fun record(conversion: Conversion, wasStale: Boolean) {
        failIfRequested()
        recorded += conversion to wasStale
    }

    override suspend fun delete(id: String) {
        failIfRequested()
        deleted += id
    }

    override suspend fun clear() {
        failIfRequested()
        clears++
    }
}

class FakeFavoritesStore : FavoritesStore {
    private val state = MutableStateFlow(setOf(Currency.MAD, Currency.EUR, Currency.USD))
    override val favorites: StateFlow<Set<Currency>> = state

    /** Persisted writes, i.e. user favorite toggles. */
    var writes = 0
        private set

    var failNextWrite = false

    override suspend fun setFavorite(currency: Currency, isFavorite: Boolean) {
        if (failNextWrite) {
            failNextWrite = false
            throw IOException("disk full")
        }
        writes++
        state.value = if (isFavorite) state.value + currency else state.value - currency
    }

    /** Simulates favorites changed elsewhere (not counted as a write). */
    fun set(vararg currencies: Currency) {
        state.value = currencies.toSet()
    }
}
