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

class FavoritesStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val file by lazy { File(tmp.root, "user_preferences.preferences_pb") }

    /** A fresh store on the same file, as after an app restart. */
    private fun openStore(): Pair<DataStoreFavoritesStore, CoroutineScope> {
        scopes.forEach { it.cancel() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreFavoritesStore(testDataStore(scope, file)) to scope
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun `defaults to MAD, EUR and USD`() = runTest {
        val (store, _) = openStore()

        assertEquals(setOf(Currency.MAD, Currency.EUR, Currency.USD), store.favorites.first())
    }

    @Test
    fun `add and remove favorites`() = runTest {
        val (store, _) = openStore()

        store.setFavorite(Currency.JPY, isFavorite = true)
        assertEquals(setOf(Currency.MAD, Currency.EUR, Currency.USD, Currency.JPY), store.favorites.first())

        store.setFavorite(Currency.USD, isFavorite = false)
        assertEquals(setOf(Currency.MAD, Currency.EUR, Currency.JPY), store.favorites.first())
    }

    @Test
    fun `adding twice or removing a non favorite is harmless`() = runTest {
        val (store, _) = openStore()

        store.setFavorite(Currency.EUR, isFavorite = true)
        store.setFavorite(Currency.CHF, isFavorite = false)

        assertEquals(setOf(Currency.MAD, Currency.EUR, Currency.USD), store.favorites.first())
    }

    @Test
    fun `removing every favorite is kept, defaults do not come back`() = runTest {
        val (store, _) = openStore()
        listOf(Currency.MAD, Currency.EUR, Currency.USD).forEach { store.setFavorite(it, isFavorite = false) }

        assertEquals(emptySet<Currency>(), store.favorites.first())
        assertEquals(emptySet<Currency>(), openStore().first.favorites.first())
    }

    @Test
    fun `favorites persist across store instances`() = runTest {
        val (store, _) = openStore()
        store.setFavorite(Currency.AED, isFavorite = true)
        store.setFavorite(Currency.MAD, isFavorite = false)

        val (reopened, _) = openStore()

        assertEquals(setOf(Currency.EUR, Currency.USD, Currency.AED), reopened.favorites.first())
    }

    @Test
    fun `unknown stored codes are ignored`() = runTest {
        val (_, scope) = openStore()
        testDataStore(scope, File(tmp.root, "other.preferences_pb")).let { dataStore ->
            dataStore.edit { it[DataStoreFavoritesStore.FAVORITES_KEY] = setOf("MAD", "XYZ", "GBP") }
            assertEquals(setOf(Currency.MAD, Currency.GBP), DataStoreFavoritesStore(dataStore).favorites.first())
        }
    }
}
