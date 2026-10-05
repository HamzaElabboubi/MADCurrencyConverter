package com.elabboubisolution.madconverter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.RateSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.math.BigDecimal

/** A snapshot saved locally, with the time this device downloaded it. */
data class CachedSnapshot(
    val snapshot: RateSnapshot,
    val fetchedAtEpochMillis: Long,
)

/**
 * Persists the last successfully fetched [RateSnapshot] in DataStore as JSON.
 * Rates are stored as strings so they round-trip as exact [BigDecimal] values.
 * Unreadable or corrupt data is treated as an empty cache.
 */
class RateCache(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {

    suspend fun read(): CachedSnapshot? {
        val raw = try {
            dataStore.data.first()[SNAPSHOT_KEY]
        } catch (_: IOException) {
            null
        } ?: return null

        val dto = try {
            json.decodeFromString<CachedSnapshotDto>(raw)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return dto.toCachedSnapshot()
    }

    suspend fun write(snapshot: RateSnapshot, fetchedAtEpochMillis: Long) {
        val dto = CachedSnapshotDto(
            base = snapshot.base.code,
            rates = snapshot.rates.entries.associate { (currency, rate) -> currency.code to rate.toPlainString() },
            lastUpdatedEpochSeconds = snapshot.lastUpdatedEpochSeconds,
            nextUpdateEpochSeconds = snapshot.nextUpdateEpochSeconds,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        )
        dataStore.edit { it[SNAPSHOT_KEY] = json.encodeToString(dto) }
    }

    internal companion object {
        val SNAPSHOT_KEY = stringPreferencesKey("rate_snapshot_v1")
    }
}

@Serializable
private data class CachedSnapshotDto(
    val base: String,
    val rates: Map<String, String>,
    val lastUpdatedEpochSeconds: Long,
    val nextUpdateEpochSeconds: Long? = null,
    val fetchedAtEpochMillis: Long,
)

/** Currencies no longer supported are dropped; any malformed value invalidates the whole entry. */
private fun CachedSnapshotDto.toCachedSnapshot(): CachedSnapshot? {
    val baseCurrency = Currency.fromCode(base) ?: return null
    val parsedRates = buildMap {
        for ((code, value) in rates) {
            val currency = Currency.fromCode(code) ?: continue
            val rate = value.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
            put(currency, rate)
        }
    }
    return CachedSnapshot(
        snapshot = RateSnapshot(
            base = baseCurrency,
            rates = parsedRates,
            lastUpdatedEpochSeconds = lastUpdatedEpochSeconds,
            nextUpdateEpochSeconds = nextUpdateEpochSeconds,
        ),
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )
}
