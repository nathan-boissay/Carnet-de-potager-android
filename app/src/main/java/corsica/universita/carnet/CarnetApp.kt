package corsica.universita.carnet

import android.app.Application
import android.content.Context
import androidx.work.WorkManager
import corsica.universita.carnet.data.PhotoStore
import corsica.universita.carnet.data.PlantsRepository
import corsica.universita.carnet.data.local.CarnetDatabase
import corsica.universita.carnet.data.local.Settings
import corsica.universita.carnet.data.location.LocationSource
import corsica.universita.carnet.data.remote.okHttp
import corsica.universita.carnet.data.remote.syncApi
import corsica.universita.carnet.data.sensor.LightSensorSource
import corsica.universita.carnet.notif.Notifications

/**
 * Conteneur d'injection de dépendances léger initialisé une seule fois par process
 * dans `CarnetApp.onCreate()` (Chapitre 1, Slide 8 & TD A4 Slide 93 du Jour 5).
 */
object CarnetContainer {
    lateinit var database: CarnetDatabase
        private set
    lateinit var repository: PlantsRepository
        private set
    lateinit var settings: Settings
        private set
    lateinit var photoStore: PhotoStore
        private set
    lateinit var locationSource: LocationSource
        private set
    lateinit var lightSensorSource: LightSensorSource
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        database = CarnetDatabase.build(appContext)
        settings = Settings(appContext)
        photoStore = PhotoStore(appContext)
        locationSource = LocationSource(appContext)
        lightSensorSource = LightSensorSource(appContext)

        val client = okHttp(appContext)
        val api = syncApi(client)
        val workManager = WorkManager.getInstance(appContext)

        repository = PlantsRepository(
            dao = database.plantDao(),
            api = api,
            workManager = workManager,
            photoStore = photoStore,
        )
    }
}

/**
 * Classe `Application` créée par l'OS au démarrage du process avant tout écran, worker ou service :
 * 1. Initialise `CarnetContainer` (Room, Retrofit/OkHttp, WorkManager, DataStore, Capteur Lux).
 * 2. Crée immédiatement les canaux de notification (`sync` et `hub`) avant toute notification ou push FCM.
 */
class CarnetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CarnetContainer.init(this)
        Notifications.createChannels(this)
    }
}

