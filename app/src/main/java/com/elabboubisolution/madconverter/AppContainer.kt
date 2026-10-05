package com.elabboubisolution.madconverter

import com.elabboubisolution.madconverter.data.remote.ErApiRateProvider
import com.elabboubisolution.madconverter.data.remote.ExchangeRateApi
import com.elabboubisolution.madconverter.data.remote.RateProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container, created once by [MadConverterApplication].
 * Data sources, repositories and ViewModel factories are wired here as they are added.
 */
class AppContainer {

    val json: Json = Json {
        ignoreUnknownKeys = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NETWORK_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
        .build()

    val rateProvider: RateProvider = ErApiRateProvider(ExchangeRateApi.create(okHttpClient, json))

    private companion object {
        const val NETWORK_TIMEOUT_SECONDS = 10L
    }
}
