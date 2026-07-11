package com.tepmex.paircompelo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.tepmex.paircompelo.core.FakeAppClock
import com.tepmex.paircompelo.data.db.PairCompEloDatabase
import com.tepmex.paircompelo.data.importexport.ImportExportService
import com.tepmex.paircompelo.data.importexport.ImportMode
import com.tepmex.paircompelo.data.prefs.SettingsDataStore
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.pairing.PairSelector
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoryDatabaseTest {

    private lateinit var db: PairCompEloDatabase
    private lateinit var repository: PreferenceRepository
    private lateinit var importExport: ImportExportService
    private val clock = FakeAppClock(Instant.parse("2026-01-01T00:00:00Z"))
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher + Job())

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PairCompEloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.preferencesDataStoreFile("test_settings") },
        )
        val settings = SettingsDataStore(dataStore)
        repository = PreferenceRepository(
            db = db,
            listDao = db.preferenceListDao(),
            itemDao = db.preferenceItemDao(),
            itemComparisonDao = db.itemComparisonDao(),
            listComparisonDao = db.listComparisonDao(),
            settingsDataStore = settings,
            clock = clock,
            pairSelector = PairSelector(),
        )
        importExport = ImportExportService(
            db = db,
            listDao = db.preferenceListDao(),
            itemDao = db.preferenceItemDao(),
            itemComparisonDao = db.itemComparisonDao(),
            listComparisonDao = db.listComparisonDao(),
            settingsDataStore = settings,
            repository = repository,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createListAndItems_compare_andUndo() = runTest(dispatcher) {
        val list = repository.createList("Films", "test")
        val a = repository.createItem(list.id, "Arrival", null, null)
        val b = repository.createItem(list.id, "Heat", null, null)
        repository.recordItemComparison(list.id, a.id, b.id, ComparisonOutcome.LEFT_WINS)
        val itemsAfter = repository.observeActiveItems(list.id).first()
        val winner = itemsAfter.first { it.id == a.id }
        val loser = itemsAfter.first { it.id == b.id }
        assertThat(winner.rating).isGreaterThan(loser.rating)
        assertThat(winner.winCount).isEqualTo(1)
        assertThat(loser.lossCount).isEqualTo(1)

        repository.undoLatestItemComparison(list.id)
        val afterUndo = repository.observeActiveItems(list.id).first()
        assertThat(afterUndo.first { it.id == a.id }.rating).isWithin(1e-6).of(1000.0)
        assertThat(afterUndo.first { it.id == a.id }.comparisonCount).isEqualTo(0)
    }

    @Test
    fun deleteList_cascadesItemsAndComparisons() = runTest(dispatcher) {
        val list = repository.createList("Games", null)
        val a = repository.createItem(list.id, "A", null, null)
        val b = repository.createItem(list.id, "B", null, null)
        repository.recordItemComparison(list.id, a.id, b.id, ComparisonOutcome.LEFT_WINS)
        repository.deleteList(list.id)
        assertThat(db.preferenceItemDao().getAll()).isEmpty()
        assertThat(db.itemComparisonDao().getAll()).isEmpty()
    }

    @Test
    fun exportImport_roundTrip() = runTest(dispatcher) {
        val list = repository.createList("Books", null)
        repository.createItem(list.id, "Dune", null, null)
        repository.createItem(list.id, "Neuromancer", null, null)
        val json = importExport.exportJson()
        repository.deleteAllData()
        assertThat(db.preferenceListDao().getAll()).isEmpty()
        val report = importExport.importJson(json, ImportMode.REPLACE)
        assertThat(report.listsImported).isEqualTo(1)
        assertThat(report.itemsImported).isEqualTo(2)
        assertThat(db.preferenceListDao().getAll()).hasSize(1)
    }

    @Test
    fun listComparison_doesNotChangeItemRatings() = runTest(dispatcher) {
        val l1 = repository.createList("L1", null)
        val l2 = repository.createList("L2", null)
        val item = repository.createItem(l1.id, "X", null, null)
        val before = repository.observeItem(item.id).first()!!.rating
        repository.recordListComparison(l1.id, l2.id, ComparisonOutcome.LEFT_WINS)
        val after = repository.observeItem(item.id).first()!!.rating
        assertThat(after).isWithin(1e-9).of(before)
        val lists = repository.observeActiveLists().first()
        assertThat(lists.first { it.id == l1.id }.rating)
            .isGreaterThan(lists.first { it.id == l2.id }.rating)
    }
}
