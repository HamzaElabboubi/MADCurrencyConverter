package com.elabboubisolution.madconverter.data.local

import androidx.datastore.preferences.core.edit
import com.elabboubisolution.madconverter.data.local.RateCacheTest.Companion.testDataStore
import com.elabboubisolution.madconverter.domain.model.Currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal

class FeePreferenceStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val file by lazy { File(tmp.root, "user_preferences.preferences_pb") }

    private fun open(): Pair<DataStoreFeePreferenceStore, CoroutineScope> {
        scopes.forEach { it.cancel() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreFeePreferenceStore(testDataStore(scope, file)) to scope
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun `defaults to 0 percent`() = runTest {
        assertEquals(0, BigDecimal.ZERO.compareTo(open().first.feePercent.first()))
    }

    @Test
    fun `saved fee persists across restarts`() = runTest {
        open().first.setFeePercent(BigDecimal("2.5"))

        assertEquals(BigDecimal("2.5"), open().first.feePercent.first())
    }

    @Test
    fun `corrupt or out of range stored values read as 0 percent`() = runTest {
        val (store, scope) = open()
        val dataStore = testDataStore(scope, File(tmp.root, "other.preferences_pb"))
        listOf("abc", "-3", "25", "NaN").forEach { stored ->
            dataStore.edit { it[DataStoreFeePreferenceStore.FEE_KEY] = stored }
            assertEquals(stored, 0, BigDecimal.ZERO.compareTo(DataStoreFeePreferenceStore(dataStore).feePercent.first()))
        }
        assertEquals(0, BigDecimal.ZERO.compareTo(store.feePercent.first()))
    }

    @Test
    fun `shares the preferences file with favorites without interfering`() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val shared = testDataStore(scope, File(tmp.root, "shared.preferences_pb"))
        val feeStore = DataStoreFeePreferenceStore(shared)
        val favorites = DataStoreFavoritesStore(shared)

        feeStore.setFeePercent(BigDecimal("3"))
        favorites.setFavorite(Currency.JPY, isFavorite = true)

        assertEquals(BigDecimal("3"), feeStore.feePercent.first())
        assertEquals(DataStoreFavoritesStore.DEFAULT_FAVORITES + Currency.JPY, favorites.favorites.first())
    }
}
