package corsica.universita.carnet.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Modèle métier d'une plante utilisé par l'interface Compose (distinct de PlantEntity sur le disque
 * et de PlantDto sur le réseau, conformément au Chapitre 1 du Jour 5).
 */
data class Plant(
    val id: Long = 0,
    val name: String,
    val category: String,
    val plantedAt: String,
    val waterInterval: Int,
    val lastWatered: String,
    val notes: String,
    val createdAt: Long,
    val pinned: Boolean = false,
    val synced: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Float? = null,
    val photoPath: String? = null,
    val lastLux: Float? = null,
) {
    val waterStatus: WaterStatus
        get() = calculateWaterStatus(plantedAt, lastWatered, waterInterval)
}

enum class WaterUrgency {
    LATE,
    TODAY,
    OK
}

data class WaterStatus(
    val urgency: WaterUrgency,
    val label: String,
    val diffDays: Long,
    val needsWater: Boolean,
)

/**
 * Équivalent Kotlin exact de `calculateWaterStatus(plant)` dans `app.js` de la PWA.
 */
fun calculateWaterStatus(
    plantedAt: String,
    lastWatered: String,
    waterInterval: Int,
): WaterStatus {
    return try {
        val refStr = lastWatered.ifBlank { plantedAt }
        val refDate = LocalDate.parse(refStr)
        val nextWaterDate = refDate.plusDays(waterInterval.toLong().coerceAtLeast(1L))
        val today = LocalDate.now()
        val diffDays = ChronoUnit.DAYS.between(today, nextWaterDate)

        when {
            diffDays < 0 -> WaterStatus(
                urgency = WaterUrgency.LATE,
                label = "En retard (${kotlin.math.abs(diffDays)}j)",
                diffDays = diffDays,
                needsWater = true
            )
            diffDays == 0L -> WaterStatus(
                urgency = WaterUrgency.TODAY,
                label = "Aujourd'hui !",
                diffDays = 0L,
                needsWater = true
            )
            else -> WaterStatus(
                urgency = WaterUrgency.OK,
                label = "Dans $diffDays jour${if (diffDays > 1) "s" else ""}",
                diffDays = diffDays,
                needsWater = false
            )
        }
    } catch (_: Exception) {
        WaterStatus(
            urgency = WaterUrgency.TODAY,
            label = "À vérifier",
            diffDays = 0L,
            needsWater = true
        )
    }
}

fun todayIsoString(): String = LocalDate.now().toString()

