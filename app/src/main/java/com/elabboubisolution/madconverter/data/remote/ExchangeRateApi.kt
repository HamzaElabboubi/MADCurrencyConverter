package com.elabboubisolution.madconverter.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * ExchangeRate-API open access endpoint (no API key). Terms require the attribution
 * "Rates By Exchange Rate API" linking to https://www.exchangerate-api.com, and calling
 * at most about once per hour per device (HTTP 429 for 20 minutes otherwise).
 */
interface ExchangeRateApi {

    @GET("v6/latest/{base}")
    suspend fun latestRates(@Path("base") baseCode: String): LatestRatesDto

    companion object {
        const val BASE_URL = "https://open.er-api.com/"

        fun create(client: OkHttpClient, json: Json, baseUrl: String = BASE_URL): ExchangeRateApi =
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ExchangeRateApi::class.java)
    }
}
