package com.elabboubisolution.madconverter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.elabboubisolution.madconverter.data.local.DataStoreFavoritesStore
import com.elabboubisolution.madconverter.data.local.DataStoreFeePreferenceStore
import com.elabboubisolution.madconverter.data.local.DataStoreHistoryStore
import com.elabboubisolution.madconverter.data.local.FeePreferenceStore
import com.elabboubisolution.madconverter.data.local.FavoritesStore
import com.elabboubisolution.madconverter.data.local.HistoryStore
import com.elabboubisolution.madconverter.data.local.RateCache
import com.elabboubisolution.madconverter.data.remote.ErApiRateProvider
import com.elabboubisolution.madconverter.data.remote.ExchangeRateApi
import com.elabboubisolution.madconverter.data.remote.RateProvider
import com.elabboubisolution.madconverter.data.repository.CachingCurrencyRepository
import com.elabboubisolution.madconverter.data.repository.CurrencyRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.ratesDataStore: DataStore<Preferences> by preferencesDataStore(name = "rates")
private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "conversion_history")

/**
 * Manual dependency container, created once by [MadConverterApplication].
 * Data sources, repositories and ViewModel factories are wired here as they are added.
 */
class AppContainer(context: Context) {

    val json: Json = Json {
        ignoreUnknownKeys = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NETWORK_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
        .build()

    private val rateProvider: RateProvider = ErApiRateProvider(ExchangeRateApi.create(okHttpClient, json))

    val currencyRepository: CurrencyRepository = CachingCurrencyRepository(
        provider = rateProvider,
        cache = RateCache(context.applicationContext.ratesDataStore, json),
    )

    val favoritesStore: FavoritesStore =
        DataStoreFavoritesStore(context.applicationContext.userPreferencesDataStore)

    val feePreferenceStore: FeePreferenceStore =
        DataStoreFeePreferenceStore(context.applicationContext.userPreferencesDataStore)

    val historyStore: HistoryStore = DataStoreHistoryStore(context.applicationContext.historyDataStore, json)

    private companion object {
        const val NETWORK_TIMEOUT_SECONDS = 10L
    }
}
