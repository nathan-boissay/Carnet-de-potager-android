package corsica.universita.carnet.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * DTO envoyé au serveur d'écho (Chapitre 3, Slide 42 du Jour 5).
 * Distinct de `PlantEntity` (format du disque) et de `Plant` (format de l'UI).
 */
@Serializable
data class PlantDto(
    val id: Long,
    val name: String,
    val category: String,
    val plantedAt: String,
    val waterInterval: Int,
    val lastWatered: String,
    val notes: String,
    val createdAt: Long,
)

@Serializable
data class EchoResponse(
    val url: String = "",
    val json: PlantDto? = null,
)

interface SyncApi {
    @POST("post")
    suspend fun push(@Body body: PlantDto): EchoResponse
}

