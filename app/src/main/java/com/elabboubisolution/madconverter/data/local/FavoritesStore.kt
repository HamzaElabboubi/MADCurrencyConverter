package com.elabboubisolution.madconverter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.elabboubisolution.madconverter.domain.model.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The user's favorite currencies, shown first in the currency picker. */
interface FavoritesStore {
    val favorites: Flow<Set<Currency>>
    suspend fun setFavorite(currency: Currency, isFavorite: Boolean)
}

/**
 * Persists favorites in DataStore as ISO codes. Until the user changes anything,
 * [DEFAULT_FAVORITES] apply; codes of currencies no longer supported are ignored.
 */
class DataStoreFavoritesStore(private val dataStore: DataStore<Preferences>) : FavoritesStore {

    override val favorites: Flow<Set<Currency>> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[FAVORITES_KEY]?.toCurrencies() ?: DEFAULT_FAVORITES }
        .distinctUntilChanged()

    override suspend fun setFavorite(currency: Currency, isFavorite: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY] ?: DEFAULT_FAVORITES.map { it.code }.toSet()
            prefs[FAVORITES_KEY] = if (isFavorite) current + currency.code else current - currency.code
        }
    }

    private fun Set<String>.toCurrencies(): Set<Currency> = mapNotNull(Currency::fromCode).toSet()

    companion object {
        val DEFAULT_FAVORITES: Set<Currency> = setOf(Currency.MAD, Currency.EUR, Currency.USD)
        internal val FAVORITES_KEY = stringSetPreferencesKey("favorite_currencies")
    }
}
