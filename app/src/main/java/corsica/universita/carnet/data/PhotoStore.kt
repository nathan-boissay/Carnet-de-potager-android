package corsica.universita.carnet.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stockage des photos sur le disque dans `filesDir/photos/` (Chapitre 2, Slide 15 du Jour 5) :
 * - On ne met jamais les octets d'une photo dans Room, seulement son chemin (`photoPath`).
 * - La suppression d'une plante supprime aussi son fichier photo.
 * - `FileProvider` génère une URI `content://` avec droit de lecture temporaire pour le partage.
 */
class PhotoStore(private val appContext: Context) {

    val photosDir: File
        get() = File(appContext.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File =
        File(photosDir, "plant_${System.currentTimeMillis()}.jpg")

    suspend fun savePhoto(bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val file = newPhotoFile()
            file.writeBytes(bytes)
            file.absolutePath
        }

    suspend fun deletePhoto(path: String?) =
        withContext(Dispatchers.IO) {
            if (!path.isNullOrBlank()) {
                runCatching { File(path).delete() }
            }
        }

    fun uriFor(path: String): Uri =
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            File(path),
        )
}

