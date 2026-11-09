package corsica.universita.carnet.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) vérifié à la compilation par KSP (Chapitre 2 du Jour 5).
 */
@Dao
interface PlantDao {

    @Query("SELECT * FROM plants ORDER BY pinned DESC, createdAt DESC")
    fun all(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE id = :id")
    fun observeById(id: Long): Flow<PlantEntity?>

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun get(id: Long): PlantEntity?

    @Query("SELECT COUNT(*) FROM plants")
    suspend fun count(): Int

    @Query("SELECT * FROM plants WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun unsynced(): List<PlantEntity>

    @Query("UPDATE plants SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Upsert
    suspend fun upsert(plant: PlantEntity): Long

    @Delete
    suspend fun delete(plant: PlantEntity)
}

