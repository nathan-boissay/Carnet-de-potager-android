package corsica.universita.carnet.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Niveau d'ensoleillement potager calculé à partir de la mesure en Lux du capteur matériel.
 */
enum class SunExposureLevel(
    val title: String,
    val recommendation: String,
    val matchingCategories: String,
) {
    SHADE(
        title = "☁️ Ombre (< 1 000 lx)",
        recommendation = "Luminosité faible : adapté aux semis d'intérieur ou plantes d'ombre (menthe, mâche).",
        matchingCategories = "Semis sous abri, Menthe, Épinard d'hiver"
    ),
    PARTIAL_SHADE(
        title = "⛅ Mi-ombre (1 000 – 10 000 lx)",
        recommendation = "Lumière tamisée idéale pour les aromatiques fragiles et légumes-feuilles.",
        matchingCategories = "Basilic Grand Vert, Laitue, Persil, Radis"
    ),
    FULL_SUN(
        title = "☀️ Plein soleil (> 10 000 lx)",
        recommendation = "Ensoleillement fort optimal pour la fructification des légumes du soleil !",
        matchingCategories = "Tomate Marmande, Courgette Ronde de Nice, Aubergine, Poivron"
    )
}

fun classifyLux(lux: Float): SunExposureLevel = when {
    lux < 1_000f -> SunExposureLevel.SHADE
    lux < 10_000f -> SunExposureLevel.PARTIAL_SHADE
    else -> SunExposureLevel.FULL_SUN
}

/**
 * CAPACITÉ NATIVE (Chapitre 8, Slide 107 du Jour 5) :
 * Lecture en temps réel du capteur matériel de luminosité ambiante (`Sensor.TYPE_LIGHT`).
 *
 * Pourquoi la PWA ne peut pas le faire :
 * L'API web `AmbientLightSensor` fait partie de la colonne rouge de la grille des capacités PWA
 * (refusée par défaut dans les navigateurs pour des raisons d'empreinte/fingerprinting).
 * Sur Android, l'OS expose `SensorManager` directement sans permission runtime et le `callbackFlow`
 * coupe automatiquement le capteur dès que l'écran quitte le premier plan (`awaitClose`).
 */
class LightSensorSource(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    val isAvailable: Boolean
        get() = lightSensor != null

    val sensorName: String
        get() = lightSensor?.name ?: "Capteur de lumière absent (mode simulation)"

    fun observeLux(): Flow<Float> = callbackFlow {
        val sensor = lightSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val lux = event?.values?.firstOrNull() ?: return
                trySend(lux)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_UI,
        )

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}

