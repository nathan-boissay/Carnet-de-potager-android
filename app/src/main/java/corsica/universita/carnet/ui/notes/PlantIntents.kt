package corsica.universita.carnet.ui.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import corsica.universita.carnet.BuildConfig
import corsica.universita.carnet.CarnetContainer
import corsica.universita.carnet.data.Plant
import java.io.File

/**
 * Les trois gestes d'Intents sortants du TD A3 + partage enrichi avec photo du TD A7 :
 * 1. Partager une fiche de plante (texte seul ou texte + photo JPEG via `FileProvider`).
 * 2. Ouvrir l'URL de la PWA `https://carnetdepotager.vercel.app/` dans le navigateur même si l'App Link est vérifié.
 */
object PlantIntents {

    fun sharePlant(context: Context, plant: Plant) {
        val shareText = buildString {
            appendLine("🌱 Fiche Potager : ${plant.name} (${plant.category})")
            appendLine("💧 Arrosage : tous les ${plant.waterInterval} jour(s) — statut : ${plant.waterStatus.label}")
            appendLine("📅 Planté le : ${plant.plantedAt} (dernier arrosage : ${plant.lastWatered})")
            if (plant.lastLux != null) {
                appendLine("☀️ Ensoleillement mesuré : ${plant.lastLux.toInt()} lux")
            }
            if (plant.lat != null && plant.lng != null) {
                appendLine("📍 Parcelle : %.5f, %.5f".format(plant.lat, plant.lng))
            }
            if (plant.notes.isNotBlank()) {
                appendLine("📝 ${plant.notes}")
            }
            append("🔗 https://${BuildConfig.PWA_HOST}/")
        }

        val hasPhoto = !plant.photoPath.isNullOrBlank() && File(plant.photoPath).exists()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Fiche Potager : ${plant.name}")
            putExtra(Intent.EXTRA_TEXT, shareText)

            if (hasPhoto) {
                type = "image/jpeg"
                val uri = CarnetContainer.photoStore.uriFor(plant.photoPath!!)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }

        context.startActivity(
            Intent.createChooser(sendIntent, "Partager la fiche « ${plant.name} » via…")
        )
    }

    /**
     * Ouvre la PWA explicitement dans un navigateur Web (Chrome, Samsung Internet, Firefox, etc.).
     * On force la résolution sur les packages de navigateurs pour éviter que le système
     * ne propose la galerie/photos ou ne reboucle sur l'application elle-même.
     */
    fun openPwaInBrowser(context: Context, plantId: String? = null) {
        val url = if (plantId != null) {
            "https://${BuildConfig.PWA_HOST}/plant/$plantId"
        } else {
            "https://${BuildConfig.PWA_HOST}/"
        }
        val pwaUri = Uri.parse(url)

        val directIntent = Intent(Intent.ACTION_VIEW, pwaUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // 1. Liste les navigateurs installés sur le téléphone
        val pm = context.packageManager
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolveInfos = pm.queryIntentActivities(browserIntent, 0)

        // Filtre pour exclure notre propre application du choix
        val browserPackages = resolveInfos
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName && !it.contains("gallery") && !it.contains("photos") }
            .distinct()

        // 2. Si un navigateur connu existe (ex: Chrome, Samsung Browser, Firefox), on le cible
        val preferred = browserPackages.firstOrNull {
            it.contains("chrome") || it.contains("browser") || it.contains("firefox") || it.contains("opera")
        } ?: browserPackages.firstOrNull()

        if (preferred != null) {
            directIntent.setPackage(preferred)
        }

        runCatching {
            context.startActivity(directIntent)
        }.onFailure {
            // Fallback générique
            directIntent.setPackage(null)
            context.startActivity(Intent.createChooser(directIntent, "Ouvrir avec un navigateur").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}

