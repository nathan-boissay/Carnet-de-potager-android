package corsica.universita.carnet.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import corsica.universita.carnet.CarnetContainer
import corsica.universita.carnet.notif.Notifications
import retrofit2.HttpException
import java.io.IOException

private const val TAG = "Carnet"

/**
 * Worker de l'Outbox (Chapitre 4, Slide 55 & TD A5 du Jour 5) :
 * - Instancié par WorkManager quand la contrainte `NetworkType.CONNECTED` est remplie
 *   et que l'OS ouvre une fenêtre de maintenance (hors Doze).
 * - Distingue `IOException` (panne réseau -> `Result.retry()`) de `HttpException`
 *   (refus 4xx/5xx du serveur -> `Result.failure()`).
 * - Affiche une notification silencieuse sur le canal `sync` quand des fiches ont été envoyées (TD A6).
 */
class SyncWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = try {
        Log.d(TAG, "SyncWorker RUNNING — envoi des fiches en attente…")
        val count = CarnetContainer.repository.syncPending()
        if (count > 0) {
            Notifications.showSyncDone(applicationContext, count)
        }
        Result.success(workDataOf("count" to count))
    } catch (e: IOException) {
        Log.w(TAG, "SyncWorker IOException (réseau coupé) -> retry()", e)
        Result.retry()
    } catch (e: HttpException) {
        Log.e(TAG, "SyncWorker HttpException (${e.code()}) -> failure()", e)
        Result.failure()
    }
}

