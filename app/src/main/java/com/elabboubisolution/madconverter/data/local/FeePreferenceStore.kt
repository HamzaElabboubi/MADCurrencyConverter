package com.elabboubisolution.madconverter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elabboubisolution.madconverter.domain.FeeInput
import com.elabboubisolution.madconverter.domain.RealCost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.math.BigDecimal

/** The Real Cost fee percentage the user last chose. Stays on the device. */
interface FeePreferenceStore {
    val feePercent: Flow<BigDecimal>
    suspend fun setFeePercent(percent: BigDecimal)
}

/** Stored as a plain decimal string; anything unreadable or out of range reads as 0%. */
class DataStoreFeePreferenceStore(private val dataStore: DataStore<Preferences>) : FeePreferenceStore {

    override val feePercent: Flow<BigDecimal> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            when (val parsed = prefs[FEE_KEY]?.let(RealCost::parseFeePercent)) {
                is FeeInput.Valid -> parsed.percent
                else -> BigDecimal.ZERO
            }
        }
        .distinctUntilChanged()

    override suspend fun setFeePercent(percent: BigDecimal) {
        dataStore.edit { it[FEE_KEY] = percent.toPlainString() }
    }

    internal companion object {
        val FEE_KEY = stringPreferencesKey("real_cost_fee_percent")
    }
}
