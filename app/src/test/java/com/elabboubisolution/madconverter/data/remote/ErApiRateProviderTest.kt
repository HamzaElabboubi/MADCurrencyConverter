package com.elabboubisolution.madconverter.data.remote

import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateFetchError
import com.elabboubisolution.madconverter.domain.model.RateFetchResult
import com.elabboubisolution.madconverter.testing.SampleRates
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class ErApiRateProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: ErApiRateProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = providerFor(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `success response is mapped to a snapshot with exact decimal rates`() = runTest {
        server.enqueue(json(200, successBody()))

        val result = provider.fetchLatestRates(Currency.MAD)

        val snapshot = (result as RateFetchResult.Success).snapshot
        assertEquals(Currency.MAD, snapshot.base)
        assertEquals(BigDecimal("1"), snapshot.rates[Currency.MAD])
        assertEquals(BigDecimal("0.100812"), snapshot.rates[Currency.USD])
        assertEquals(BigDecimal("0.089729"), snapshot.rates[Currency.EUR])
        assertEquals(BigDecimal("0.076291"), snapshot.rates[Currency.GBP])
        assertEquals(BigDecimal("15.839832"), snapshot.rates[Currency.JPY])
        assertEquals("all supported currencies, unsupported ones ignored", Currency.entries.toSet(), snapshot.rates.keys)
        assertEquals(1791158551L, snapshot.lastUpdatedEpochSeconds)
        assertEquals(1791246061L, snapshot.nextUpdateEpochSeconds)
        assertEquals("/v6/latest/MAD", server.takeRequest().target)
    }

    @Test
    fun `api error reported in body maps to Service error with its type`() = runTest {
        server.enqueue(json(200, """{"result":"error","error-type":"unsupported-code"}"""))

        assertFailure(RateFetchError.Service(httpCode = null, detail = "unsupported-code"))
    }

    @Test
    fun `http 500 maps to Service error with status code`() = runTest {
        server.enqueue(json(500, "{}"))

        assertFailure(RateFetchError.Service(httpCode = 500, detail = null))
    }

    @Test
    fun `http 429 maps to RateLimited`() = runTest {
        server.enqueue(json(429, "{}"))

        assertFailure(RateFetchError.RateLimited)
    }

    @Test
    fun `malformed json maps to InvalidResponse`() = runTest {
        server.enqueue(json(200, """{"result":"success","rates":{"""))

        assertFailure(RateFetchError.InvalidResponse)
    }

    @Test
    fun `body without result field maps to InvalidResponse`() = runTest {
        server.enqueue(json(200, """{"base_code":"MAD"}"""))

        assertFailure(RateFetchError.InvalidResponse)
    }

    @Test
    fun `unexpected base currency maps to InvalidResponse`() = runTest {
        server.enqueue(json(200, successBody(base = "USD")))

        assertFailure(RateFetchError.InvalidResponse)
    }

    @Test
    fun `non numeric or non positive rate maps to InvalidResponse`() = runTest {
        server.enqueue(json(200, successBody(rates = withUsd("\"abc\""))))
        assertFailure(RateFetchError.InvalidResponse)

        server.enqueue(json(200, successBody(rates = withUsd("0"))))
        assertFailure(RateFetchError.InvalidResponse)

        server.enqueue(json(200, successBody(rates = withUsd("-0.1"))))
        assertFailure(RateFetchError.InvalidResponse)
    }

    @Test
    fun `currency missing from the response is left out without failing the others`() = runTest {
        server.enqueue(json(200, successBody(rates = SampleRates.ratesJson(Currency.GBP, Currency.JPY))))

        val snapshot = (provider.fetchLatestRates(Currency.MAD) as RateFetchResult.Success).snapshot

        assertEquals(Currency.entries.toSet() - Currency.GBP - Currency.JPY, snapshot.rates.keys)
        assertEquals(BigDecimal("0.100812"), snapshot.rates[Currency.USD])
    }

    @Test
    fun `response without any supported currency besides the base maps to RateUnavailable`() = runTest {
        val onlyBase = SampleRates.ratesJson(*(Currency.entries - Currency.MAD).toTypedArray())
        server.enqueue(json(200, successBody(rates = onlyBase)))

        assertFailure(RateFetchError.RateUnavailable(Currency.entries - Currency.MAD))
    }

    @Test
    fun `slow server maps to Timeout`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .setHeader("Content-Type", "application/json")
                .body(successBody())
                .headersDelay(2, TimeUnit.SECONDS)
                .build()
        )
        val impatientProvider = providerFor(
            server.url("/").toString(),
            OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build(),
        )

        val result = impatientProvider.fetchLatestRates(Currency.MAD)

        assertEquals(RateFetchResult.Failure(RateFetchError.Timeout), result)
    }

    @Test
    fun `unreachable server maps to NoConnection`() = runTest {
        val url = server.url("/").toString()
        server.close()

        val result = providerFor(url).fetchLatestRates(Currency.MAD)

        assertEquals(RateFetchResult.Failure(RateFetchError.NoConnection), result)
    }

    private suspend fun assertFailure(expected: RateFetchError) {
        val result = provider.fetchLatestRates(Currency.MAD)
        assertTrue("Expected failure but was $result", result is RateFetchResult.Failure)
        assertEquals(expected, (result as RateFetchResult.Failure).error)
    }

    private fun providerFor(baseUrl: String, client: OkHttpClient = OkHttpClient()) =
        ErApiRateProvider(ExchangeRateApi.create(client, Json { ignoreUnknownKeys = true }, baseUrl))

    private fun json(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    /** Shape of a real open.er-api.com response; [rates] defaults to [SampleRates]. */
    private fun successBody(base: String = "MAD", rates: String = SampleRates.ratesJson()): String = """
        {
          "result":"success",
          "provider":"https://www.exchangerate-api.com",
          "time_last_update_unix":1791158551,
          "time_last_update_utc":"Mon, 05 Oct 2026 00:02:31 +0000",
          "time_next_update_unix":1791246061,
          "time_next_update_utc":"Tue, 06 Oct 2026 00:21:01 +0000",
          "time_eol_unix":0,
          "base_code":"$base",
          "rates":$rates
        }
    """.trimIndent()

    private fun withUsd(value: String) = SampleRates.ratesJson(override = mapOf(Currency.USD to value))
}
