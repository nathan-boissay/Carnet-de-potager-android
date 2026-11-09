package corsica.universita.carnet.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * Stockage clé-valeur asynchrone DataStore (Chapitre 2, Slide 26 du Jour 5) :
 * utilisé pour les valeurs isolées comme le jeton FCM de l'appareil (équivalent de localStorage).
 */
class Settings(context: Context) {
    private val store = context.applicationContext.settingsStore

    val fcmToken: Flow<String?> = store.data.map { prefs ->
        prefs[KEY_FCM_TOKEN]
    }

    suspend fun saveFcmToken(token: String) {
        store.edit { prefs ->
            prefs[KEY_FCM_TOKEN] = token
        }
    }

    companion object {
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
    }
}

