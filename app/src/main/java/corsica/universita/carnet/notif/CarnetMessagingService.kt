package corsica.universita.carnet.notif

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import corsica.universita.carnet.CarnetContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "Carnet"

/**
 * Service Firebase Cloud Messaging (Chapitre 5, Slide 71 & TD A6 du Jour 5) :
 * - Réveillé par l'OS (via Play Services) lorsqu'un push arrive, même si l'application est fermée ou tuée.
 * - `onNewToken` enregistre le jeton FCM de l'appareil dans `DataStore` (`Settings`).
 * - `onMessageReceived` traite les messages `data` (et `notification` au premier plan) et construit
 *   une notification avec `PendingIntent` pointant vers la plante visée (`noteId`).
 */
class CarnetMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Jeton FCM : $token")
        scope.launch {
            CarnetContainer.settings.saveFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = data["title"]
            ?: message.notification?.title
            ?: "🌱 Carnet de Potager"
        val body = data["body"]
            ?: message.notification?.body
            ?: "Nouveau rappel pour votre potager"
        val plantId = (data["noteId"] ?: data["plantId"])?.toLongOrNull()

        Log.d(TAG, "Push FCM reçu : title=$title, body=$body, plantId=$plantId")
        Notifications.showHub(
            ctx = this,
            title = title,
            body = body,
            plantId = plantId,
        )
    }
}

