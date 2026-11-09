package corsica.universita.carnet.notif

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import corsica.universita.carnet.MainActivity
import corsica.universita.carnet.R
import corsica.universita.carnet.data.Plant

private const val TAG = "Carnet"

/**
 * Gestion des canaux de notification, des notifications locales et des `PendingIntent`
 * (Chapitre 5, Slides 66-70 & TD A6 du Jour 5).
 */
object Notifications {

    const val CHANNEL_SYNC = "sync"
    const val CHANNEL_HUB = "hub"

    const val EXTRA_PLANT_ID = "noteId" // Compatible avec `noteId` des commandes adb du TD A6

    private const val ID_SYNC = 1001
    private const val ID_WATERING = 2001

    /**
     * Appelé dès `CarnetApp.onCreate()` avant tout écran afin que les canaux existent
     * même lorsqu'un push FCM réveille l'application fermée.
     */
    fun createChannels(ctx: Context) {
        val manager = NotificationManagerCompat.from(ctx)

        val syncChannel = NotificationChannelCompat.Builder(
            CHANNEL_SYNC,
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName(ctx.getString(R.string.channel_sync_name))
            .setDescription(ctx.getString(R.string.channel_sync_desc))
            .build()

        val hubChannel = NotificationChannelCompat.Builder(
            CHANNEL_HUB,
            NotificationManagerCompat.IMPORTANCE_HIGH,
        )
            .setName(ctx.getString(R.string.channel_hub_name))
            .setDescription(ctx.getString(R.string.channel_hub_desc))
            .build()

        manager.createNotificationChannelsCompat(listOf(syncChannel, hubChannel))
    }

    fun canPostNotifications(ctx: Context): Boolean {
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * Construit un `PendingIntent` immuable avec un `requestCode` distinct par plante
     * (évite le piège de la Slide 69 où toutes les notifications ouvrent la même fiche).
     */
    fun openCarnet(ctx: Context, plantId: Long?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (plantId != null) {
                putExtra(EXTRA_PLANT_ID, plantId)
            }
        }
        val requestCode = plantId?.toInt() ?: 0
        return PendingIntent.getActivity(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun showSyncDone(ctx: Context, count: Int) {
        val notification = NotificationCompat.Builder(ctx, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_sync_title))
            .setContentText(
                ctx.resources.getQuantityString(R.plurals.notif_sync_text, count, count)
            )
            .setContentIntent(openCarnet(ctx, plantId = null))
            .setAutoCancel(true)
            .build()

        post(ctx, ID_SYNC, notification)
    }

    fun showHub(ctx: Context, title: String, body: String, plantId: Long?) {
        val notification = NotificationCompat.Builder(ctx, CHANNEL_HUB)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openCarnet(ctx, plantId = plantId))
            .setAutoCancel(true)
            .build()

        val notifId = plantId?.toInt()?.plus(3000) ?: (System.currentTimeMillis() % 10000).toInt()
        post(ctx, notifId, notification)
    }

    fun showWateringAlert(ctx: Context, thirstyPlants: List<Plant>) {
        if (thirstyPlants.isEmpty()) {
            showHub(
                ctx = ctx,
                title = "🌱 Carnet de Potager",
                body = "Tout va bien au jardin : aucune plante n'a besoin d'arrosage aujourd'hui !",
                plantId = null,
            )
            return
        }

        val firstPlant = thirstyPlants.first()
        val names = thirstyPlants.joinToString(", ") { it.name }
        val body = "${thirstyPlants.size} plante(s) ont besoin d'eau : $names. Touchez pour ouvrir ${firstPlant.name}."

        val notification = NotificationCompat.Builder(ctx, CHANNEL_HUB)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💧 Arrosage du potager")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openCarnet(ctx, plantId = firstPlant.id))
            .setAutoCancel(true)
            .build()

        post(ctx, ID_WATERING, notification)
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, notification: android.app.Notification) {
        if (!canPostNotifications(ctx)) {
            Log.w(TAG, "Notification ignorée (permission POST_NOTIFICATIONS non accordée ou canal coupé)")
            return
        }
        NotificationManagerCompat.from(ctx).notify(id, notification)
        Log.d(TAG, "🔔 Notification affichée (id=$id)")
    }
}

