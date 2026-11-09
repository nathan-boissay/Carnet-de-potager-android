package corsica.universita.carnet.data.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val precise: Boolean,
)

/**
 * Lecture de la position GPS ou approximative (Chapitre 6, Slide 84 du Jour 5).
 * Transforme le callback du `FusedLocationProviderClient` en fonction `suspend` annulable.
 */
class LocationSource(private val appContext: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): Fix? {
        val fine = has(ACCESS_FINE_LOCATION)
        val coarse = has(ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return null

        val priority = if (fine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val cancel = CancellationTokenSource()
        val loc: Location? = suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(priority, cancel.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cancel.cancel() }
        }

        return loc?.let {
            Fix(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracy = it.accuracy,
                precise = fine,
            )
        }
    }
}

