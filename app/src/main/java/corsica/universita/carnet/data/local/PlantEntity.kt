package corsica.universita.carnet.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import corsica.universita.carnet.data.Plant

/**
 * Table SQLite `plants` gérée par Room (Chapitre 2 du Jour 5).
 * Évolue de la v1 à la v4 via des migrations SQL explicites sans perte de données :
 * - v1 : id, name, category, plantedAt, waterInterval, lastWatered, notes, createdAt
 * - v2 (TD A4) : ajout de `pinned`
 * - v3 (TD A5) : ajout de `synced` (Outbox)
 * - v4 (TD A7 + Projet) : ajout de `lat`, `lng`, `accuracy`, `photoPath`, `lastLux`
 */
@Entity(tableName = "plants")
data class PlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val plantedAt: String,
    val waterInterval: Int,
    val lastWatered: String,
    val notes: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val lat: Double? = null,
    @ColumnInfo(defaultValue = "NULL") val lng: Double? = null,
    @ColumnInfo(defaultValue = "NULL") val accuracy: Float? = null,
    @ColumnInfo(defaultValue = "NULL") val photoPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val lastLux: Float? = null,
) {
    fun toPlant(): Plant = Plant(
        id = id,
        name = name,
        category = category,
        plantedAt = plantedAt,
        waterInterval = waterInterval,
        lastWatered = lastWatered,
        notes = notes,
        createdAt = createdAt,
        pinned = pinned,
        synced = synced,
        lat = lat,
        lng = lng,
        accuracy = accuracy,
        photoPath = photoPath,
        lastLux = lastLux,
    )
}

fun Plant.toEntity(): PlantEntity = PlantEntity(
    id = id,
    name = name,
    category = category,
    plantedAt = plantedAt,
    waterInterval = waterInterval,
    lastWatered = lastWatered,
    notes = notes,
    createdAt = createdAt,
    pinned = pinned,
    synced = synced,
    lat = lat,
    lng = lng,
    accuracy = accuracy,
    photoPath = photoPath,
    lastLux = lastLux,
)

