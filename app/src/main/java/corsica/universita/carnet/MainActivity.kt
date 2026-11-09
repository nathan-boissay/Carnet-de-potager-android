package corsica.universita.carnet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleEventObserver
import corsica.universita.carnet.notif.Notifications
import corsica.universita.carnet.ui.CarnetNavigation
import corsica.universita.carnet.ui.notes.PotagerViewModel
import corsica.universita.carnet.ui.theme.CarnetTheme

private const val TAG = "Carnet"

/**
 * L'unique `Activity` de l'application Compose (Jour 4 Chapitres 1, 3, 4 & TD A2-A3, Jour 5 TD A6) :
 * - Enregistre un `LifecycleEventObserver` qui trace chaque étape (`ON_CREATE`, `ON_START`, `ON_RESUME`,
 *   `ON_PAUSE`, `ON_STOP`, `ON_DESTROY`) et l'identifiant d'instance dans Logcat (`adb logcat -s Carnet`).
 * - Réutilise l'instance existante grâce à `android:launchMode="singleTop"` via `onNewIntent`.
 * - Gère les 3 types d'Intents entrants :
 *   1. `ACTION_SEND` (`text/plain`) : texte partagé depuis Chrome ou une autre app (TD A3).
 *   2. `ACTION_VIEW` (`https://carnetdepotager.vercel.app/...`) : App Link vérifié (TD A3).
 *   3. `EXTRA_PLANT_ID` (`noteId`) : clic sur une notification de plante ou commande `adb shell am start --el noteId 3` (TD A6).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: PotagerViewModel by viewModels { PotagerViewModel.Factory }
    private var openedPlantId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d(
            TAG,
            "onCreate, restauré depuis Bundle : ${savedInstanceState != null} — instance ${System.identityHashCode(this)}"
        )

        // TD A2 : Observer chaque transition du cycle de vie dans Logcat
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                Log.d(
                    TAG,
                    "Activity ${event.name} — instance ${System.identityHashCode(this)}"
                )
            }
        )

        handleIntent(intent)

        setContent {
            CarnetTheme {
                CarnetNavigation(
                    viewModel = viewModel,
                    openedPlantId = openedPlantId,
                    onConsumedPlantId = { openedPlantId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // 1. TD A6 : Ouverture directe d'une plante depuis un PendingIntent de notification (`noteId`)
        if (intent.hasExtra(Notifications.EXTRA_PLANT_ID)) {
            val id = intent.getLongExtra(Notifications.EXTRA_PLANT_ID, -1L)
                .takeIf { it > 0L }
                ?: intent.getIntExtra(Notifications.EXTRA_PLANT_ID, -1).toLong().takeIf { it > 0L }

            if (id != null) {
                Log.d(TAG, "PendingIntent / Notification reçu -> ouverture de la plante id=$id")
                openedPlantId = id
                intent.removeExtra(Notifications.EXTRA_PLANT_ID)
                return
            }
        }

        // 2. TD A3 : Partage entrant (ACTION_SEND) ou App Link (ACTION_VIEW)
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { shared ->
                    Log.d(TAG, "ACTION_SEND reçu : $shared")
                    viewModel.receiveSharedText(shared)
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    Log.d(TAG, "ACTION_VIEW reçu (App Link) : $uri")
                    // Si l'URL contient ?plantId=2 ou ?noteId=2, ouvre directement la plante
                    val targetId = (uri.getQueryParameter("plantId") ?: uri.getQueryParameter("noteId"))
                        ?.toLongOrNull()
                    if (targetId != null) {
                        openedPlantId = targetId
                    } else {
                        viewModel.receiveSharedText(uri.toString())
                    }
                }
            }
        }
    }
}

