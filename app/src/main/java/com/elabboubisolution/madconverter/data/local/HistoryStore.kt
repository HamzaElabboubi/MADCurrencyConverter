package com.elabboubisolution.madconverter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.ConversionHistory
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

/** On-device history of copied/shared conversions. Nothing here ever leaves the device. */
interface HistoryStore {
    /** Newest first. */
    val entries: Flow<List<HistoryEntry>>
    suspend fun record(conversion: Conversion, wasStale: Boolean)
    suspend fun delete(id: String)
    suspend fun clear()
}

/**
 * Keeps the history as one JSON list in its own DataStore file: at most
 * [ConversionHistory.MAX_ENTRIES] small records, always read and rewritten atomically together.
 * Amounts and rates are stored as strings so they round-trip as exact BigDecimals.
 * Corrupt data reads as an empty history; entries with unsupported currencies are dropped.
 */
class DataStoreHistoryStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : HistoryStore {

    override val entries: Flow<List<HistoryEntry>> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[HISTORY_KEY]) }
        .distinctUntilChanged()

    override suspend fun record(conversion: Conversion, wasStale: Boolean) {
        val entry = HistoryEntry(
            id = newId(),
            from = conversion.from,
            to = conversion.to,
            amount = conversion.amount,
            convertedAmount = conversion.convertedAmount,
            rate = conversion.rate,
            timestampMillis = clock(),
            wasStale = wasStale,
        )
        update { ConversionHistory.add(it, entry) }
    }

    override suspend fun delete(id: String) = update { entries -> entries.filterNot { it.id == id } }

    override suspend fun clear() {
        dataStore.edit { it.remove(HISTORY_KEY) }
    }

    private suspend fun update(transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        dataStore.edit { prefs ->
            prefs[HISTORY_KEY] = encode(transform(decode(prefs[HISTORY_KEY])))
        }
    }

    private fun decode(raw: String?): List<HistoryEntry> {
        if (raw == null) return emptyList()
        val dtos = try {
            json.decodeFromString<List<HistoryEntryDto>>(raw)
        } catch (_: SerializationException) {
            return emptyList()
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        return dtos.mapNotNull { it.toEntry() }
    }

    private fun encode(entries: List<HistoryEntry>): String =
        json.encodeToString(entries.map { it.toDto() })

    internal companion object {
        val HISTORY_KEY = stringPreferencesKey("conversion_history_v1")
    }
}

@Serializable
private data class HistoryEntryDto(
    val id: String,
    val from: String,
    val to: String,
    val amount: String,
    val convertedAmount: String,
    val rate: String,
    val timestampMillis: Long,
    val wasStale: Boolean,
)

private fun HistoryEntry.toDto() = HistoryEntryDto(
    id = id,
    from = from.code,
    to = to.code,
    amount = amount.toPlainString(),
    convertedAmount = convertedAmount.toPlainString(),
    rate = rate.toPlainString(),
    timestampMillis = timestampMillis,
    wasStale = wasStale,
)

private fun HistoryEntryDto.toEntry(): HistoryEntry? = HistoryEntry(
    id = id,
    from = Currency.fromCode(from) ?: return null,
    to = Currency.fromCode(to) ?: return null,
    amount = amount.toBigDecimalOrNull() ?: return null,
    convertedAmount = convertedAmount.toBigDecimalOrNull() ?: return null,
    rate = rate.toBigDecimalOrNull() ?: return null,
    timestampMillis = timestampMillis,
    wasStale = wasStale,
)
