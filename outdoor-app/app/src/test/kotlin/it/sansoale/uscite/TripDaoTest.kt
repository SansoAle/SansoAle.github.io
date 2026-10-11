package it.sansoale.uscite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.sansoale.uscite.data.db.AppDatabase
import it.sansoale.uscite.data.db.CostDraft
import it.sansoale.uscite.data.db.HutEntity
import it.sansoale.uscite.data.db.MountainDetailEntity
import it.sansoale.uscite.data.db.PhotoEntity
import it.sansoale.uscite.data.db.StatsColumns
import it.sansoale.uscite.data.db.TrackEntity
import it.sansoale.uscite.data.db.TripEntity
import it.sansoale.uscite.data.db.TripGraph
import it.sansoale.uscite.data.db.VanCostEntity
import it.sansoale.uscite.data.db.VanStageEntity
import it.sansoale.uscite.data.backup.BackupData
import it.sansoale.uscite.model.CostCategory
import it.sansoale.uscite.model.MountainDifficulty
import it.sansoale.uscite.model.StatField
import it.sansoale.uscite.model.TripType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Application semplice: UsciteApp inizializza MapLibre, che richiede librerie native non disponibili sulla JVM.
@Config(sdk = [34], application = android.app.Application::class)
class TripDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun graph(type: TripType = TripType.MOUNTAIN, title: String = "Monte Rosa") = TripGraph(
        trip = TripEntity(
            type = type,
            title = title,
            startDate = 19_000,
            endDate = 19_001,
            stats = StatsColumns(distanceM = 12_000.0, elevationGainM = 1_100.0),
            overriddenStats = setOf(StatField.ELEVATION_GAIN),
        ),
        mountain = MountainDetailEntity(0, MountainDifficulty.EE, null, "Punta Gnifetti", 4554.0),
        van = null,
        bike = null,
        huts = listOf(HutEntity(tripId = 0, name = "Capanna Margherita", elevationM = 4554.0)),
        stages = listOf(VanStageEntity(tripId = 0, name = "Alagna")),
        costs = listOf(CostDraft(VanCostEntity(tripId = 0, category = CostCategory.PARKING, amountCents = 1500), 0)),
        tracks = listOf(
            TrackEntity(
                tripId = 0, name = "Salita", gpxFile = "a.gpx", pointsFile = "a.pts", simplified = "", pointCount = 10,
                minLat = 45.0, maxLat = 46.0, minLon = 7.0, maxLon = 8.0,
            ),
        ),
        photos = listOf(
            PhotoEntity(tripId = 0, fileName = "1.jpg"),
            PhotoEntity(tripId = 0, fileName = "2.jpg"),
        ),
        coverPhotoIndex = 1,
        tags = listOf("4000", "ghiacciaio", "4000"),
        companions = listOf("Luca"),
    )

    @Test
    fun `salva e rilegge un'uscita completa`() = runTest {
        val dao = db.tripDao()
        val id = dao.saveTripGraph(graph())
        val trip = dao.getTrip(id)!!
        assertEquals("Monte Rosa", trip.trip.title)
        assertEquals(setOf(StatField.ELEVATION_GAIN), trip.trip.overriddenStats)
        assertEquals("Punta Gnifetti", trip.mountain!!.summit)
        assertEquals(1, trip.huts.size)
        assertEquals(trip.stages.single().id, trip.costs.single().stageId)
        assertEquals(2, trip.photos.size)
        assertEquals(trip.sortedPhotos[1].id, trip.trip.coverPhotoId)
        assertEquals(listOf("4000", "ghiacciaio"), trip.tags.map { it.name }.sorted())

        val summaries = dao.observeSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals(2, summaries.single().photoCount)
        assertEquals("Punta Gnifetti", summaries.single().summit)
    }

    @Test
    fun `modifica rimuove i dati non piu presenti e i tag inutilizzati`() = runTest {
        val dao = db.tripDao()
        val id = dao.saveTripGraph(graph())
        val saved = dao.getTrip(id)!!
        dao.saveTripGraph(
            graph(type = TripType.BIKE, title = "Giro in bici").copy(
                trip = saved.trip.copy(type = TripType.BIKE, title = "Giro in bici"),
                mountain = null,
                huts = emptyList(),
                stages = emptyList(),
                costs = emptyList(),
                tracks = emptyList(),
                photos = listOf(saved.sortedPhotos[0]),
                coverPhotoIndex = 0,
                tags = listOf("estate"),
            ),
        )
        val updated = dao.getTrip(id)!!
        assertEquals(TripType.BIKE, updated.trip.type)
        assertNull(updated.mountain)
        assertEquals(0, updated.tracks.size)
        assertEquals(1, updated.photos.size)
        assertEquals(listOf("estate"), dao.observeTagNames().first())
    }

    @Test
    fun `backup JSON - esporta e ripristina`() = runTest {
        db.tripDao().saveTripGraph(graph())
        db.tripDao().saveTripGraph(graph(title = "Seconda"))
        val exported = db.backupDao().exportAll()
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val text = json.encodeToString(BackupData.serializer(), exported)
        val decoded = json.decodeFromString(BackupData.serializer(), text)
        assertEquals(exported, decoded)

        db.backupDao().replaceAll(BackupData())
        assertEquals(0, db.tripDao().observeSummaries().first().size)

        db.backupDao().replaceAll(decoded)
        val restored = db.tripDao().observeSummaries().first()
        assertEquals(2, restored.size)
        assertEquals(exported, db.backupDao().exportAll())
    }
}
