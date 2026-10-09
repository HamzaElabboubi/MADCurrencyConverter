package com.elabboubisolution.madconverter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elabboubisolution.madconverter.domain.model.Currency
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/** Source and target of the main conversion. Always two different currencies. */
data class CurrencyPair(val from: Currency, val to: Currency) {
    init {
        require(from != to) { "A pair needs two different currencies" }
    }
}

/** The last source/target pair the user chose, restored at startup. Stays on the device. */
interface CurrencyPairStore {
    /** The saved pair, or null when none was saved or it is unreadable. */
    suspend fun read(): CurrencyPair?

    suspend fun save(pair: CurrencyPair)
}

/**
 * Stored as two ISO codes, always written together in one atomic edit. A pair is only
 * restored whole: a missing or unsupported code, or the same code twice, reads as null so the
 * caller keeps its default pair rather than half of a stored one.
 */
class DataStoreCurrencyPairStore(private val dataStore: DataStore<Preferences>) : CurrencyPairStore {

    override suspend fun read(): CurrencyPair? {
        val prefs = dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val from = prefs[FROM_KEY]?.let(Currency::fromCode) ?: return null
        val to = prefs[TO_KEY]?.let(Currency::fromCode) ?: return null
        return if (from != to) CurrencyPair(from, to) else null
    }

    override suspend fun save(pair: CurrencyPair) {
        dataStore.edit { prefs ->
            prefs[FROM_KEY] = pair.from.code
            prefs[TO_KEY] = pair.to.code
        }
    }

    internal companion object {
        val FROM_KEY = stringPreferencesKey("last_from_currency")
        val TO_KEY = stringPreferencesKey("last_to_currency")
    }
}
