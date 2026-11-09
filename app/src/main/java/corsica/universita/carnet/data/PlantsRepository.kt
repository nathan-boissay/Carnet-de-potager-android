package corsica.universita.carnet.data

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import corsica.universita.carnet.data.local.PlantDao
import corsica.universita.carnet.data.local.PlantEntity
import corsica.universita.carnet.data.local.toEntity
import corsica.universita.carnet.data.remote.PlantDto
import corsica.universita.carnet.data.remote.SyncApi
import corsica.universita.carnet.work.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private const val TAG = "Carnet"

/**
 * Source de vérité unique de l'application (Chapitre 1, Slide 6-8 du Jour 5) :
 * - Sert aussi bien l'écran (`PotagerViewModel`), le worker (`SyncWorker`) que le service (`CarnetMessagingService`).
 * - L'écran ne lit que `plants` (Flow émis par Room).
 * - Chaque écriture met à jour Room immédiatement (`synced = false`) puis planifie `enqueueSync()` via WorkManager.
 */
class PlantsRepository(
    private val dao: PlantDao,
    private val api: SyncApi,
    private val workManager: WorkManager,
    private val photoStore: PhotoStore,
) {

    val plants: Flow<List<Plant>> =
        dao.all().map { entities -> entities.map { it.toPlant() } }

    fun observePlant(id: Long): Flow<Plant?> =
        dao.observeById(id).map { it?.toPlant() }

    suspend fun getPlant(id: Long): Plant? =
        dao.get(id)?.toPlant()

    /**
     * Hydratation initiale des 3 fiches repères de la PWA (`Tomate Marmande`, `Basilic Grand Vert`,
     * `Courgette Ronde de Nice`) lors du tout premier lancement si la base Room est vide.
     */
    suspend fun ensureSeedPlants() {
        if (dao.count() > 0) return

        Log.d(TAG, "🌱 Première exécution Android : insertion des 3 fiches repères dans Room")
        val today = todayIsoString()
        val now = System.currentTimeMillis()

        val seeds = listOf(
            PlantEntity(
                name = "Tomate Marmande",
                category = "Solanacée",
                plantedAt = "2026-03-15",
                waterInterval = 3,
                lastWatered = today,
                notes = "Sol riche et exposition plein soleil (> 10 000 lx). Pailler généreusement au pied pour préserver l'humidité sans mouiller les feuilles.",
                createdAt = now - 3_000,
                pinned = true,
                synced = false,
            ),
            PlantEntity(
                name = "Basilic Grand Vert",
                category = "Aromatique",
                plantedAt = "2026-04-10",
                waterInterval = 2,
                lastWatered = "2026-11-05", // En retard d'arrosage pour la démo !
                notes = "Aime la chaleur et une exposition mi-ombre à ensoleillée. Pincer l'extrémité des tiges pour stimuler la ramification.",
                createdAt = now - 2_000,
                pinned = false,
                synced = false,
            ),
            PlantEntity(
                name = "Courgette Ronde de Nice",
                category = "Cucurbitacée",
                plantedAt = "2026-04-20",
                waterInterval = 4,
                lastWatered = today,
                notes = "Plante non coureuse très productive en plein soleil. Apport de compost mûr requis et arrosage régulier et profond.",
                createdAt = now - 1_000,
                pinned = false,
                synced = false,
            ),
        )

        for (seed in seeds) {
            dao.upsert(seed)
        }
        enqueueSync()
    }

    suspend fun addPlant(
        name: String,
        category: String,
        plantedAt: String,
        waterInterval: Int,
        notes: String,
    ): Long {
        val cleanDate = plantedAt.ifBlank { todayIsoString() }
        val id = dao.upsert(
            PlantEntity(
                name = name.trim(),
                category = category.trim().ifBlank { "Potager" },
                plantedAt = cleanDate,
                waterInterval = waterInterval.coerceAtLeast(1),
                lastWatered = cleanDate,
                notes = notes.trim(),
                createdAt = System.currentTimeMillis(),
                pinned = false,
                synced = false,
            )
        )
        enqueueSync()
        return id
    }

    suspend fun updatePlant(plant: Plant) {
        dao.upsert(plant.copy(synced = false).toEntity())
        enqueueSync()
    }

    suspend fun waterPlantToday(plant: Plant) {
        dao.upsert(
            plant.copy(
                lastWatered = todayIsoString(),
                synced = false,
            ).toEntity()
        )
        enqueueSync()
    }

    suspend fun togglePinned(plant: Plant) {
        dao.upsert(
            plant.copy(
                pinned = !plant.pinned,
                synced = false,
            ).toEntity()
        )
        enqueueSync()
    }

    suspend fun updateLocation(plantId: Long, lat: Double, lng: Double, accuracy: Float) {
        val current = dao.get(plantId) ?: return
        dao.upsert(
            current.copy(
                lat = lat,
                lng = lng,
                accuracy = accuracy,
                synced = false,
            )
        )
        enqueueSync()
    }

    suspend fun updatePhoto(plantId: Long, newPhotoPath: String) {
        val current = dao.get(plantId) ?: return
        if (!current.photoPath.isNullOrBlank() && current.photoPath != newPhotoPath) {
            photoStore.deletePhoto(current.photoPath)
        }
        dao.upsert(
            current.copy(
                photoPath = newPhotoPath,
                synced = false,
            )
        )
        enqueueSync()
    }

    suspend fun updateLux(plantId: Long, lux: Float) {
        val current = dao.get(plantId) ?: return
        dao.upsert(
            current.copy(
                lastLux = lux,
                synced = false,
            )
        )
        enqueueSync()
    }

    suspend fun deletePlant(id: Long) {
        val entity = dao.get(id) ?: return
        photoStore.deletePhoto(entity.photoPath)
        dao.delete(entity)
    }

    /**
     * Planification de l'Outbox dans WorkManager (Chapitre 4, Slide 54 du Jour 5) :
     * - Une seule contrainte `NetworkType.CONNECTED` (ne pas aggraver l'attente sous Doze).
     * - Réessai avec backoff exponentiel à partir de 30 s.
     * - Un seul travail `"sync"` en file (`ExistingWorkPolicy.KEEP`).
     */
    fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "sync",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Exécuté par `SyncWorker.doWork()` en arrière-plan (même application fermée ou tuée).
     */
    suspend fun syncPending(): Int {
        val unsyncedList = dao.unsynced()
        if (unsyncedList.isEmpty()) return 0

        val sentIds = mutableListOf<Long>()
        for (entity in unsyncedList) {
            api.push(
                PlantDto(
                    id = entity.id,
                    name = entity.name,
                    category = entity.category,
                    plantedAt = entity.plantedAt,
                    waterInterval = entity.waterInterval,
                    lastWatered = entity.lastWatered,
                    notes = entity.notes,
                    createdAt = entity.createdAt,
                )
            )
            sentIds += entity.id
        }

        if (sentIds.isNotEmpty()) {
            dao.markSynced(sentIds)
        }
        Log.d(TAG, "✅ Outbox synchronisée : ${sentIds.size} fiche(s) envoyée(s)")
        return sentIds.size
    }
}

