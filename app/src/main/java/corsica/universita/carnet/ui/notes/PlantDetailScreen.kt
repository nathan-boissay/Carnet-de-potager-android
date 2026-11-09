package corsica.universita.carnet.ui.notes

import android.Manifest
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import corsica.universita.carnet.CarnetContainer
import corsica.universita.carnet.data.sensor.classifyLux
import corsica.universita.carnet.ui.permissions.PermissionFlow
import corsica.universita.carnet.ui.permissions.PermissionState
import corsica.universita.carnet.ui.permissions.rememberPermissionFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "Carnet"

/**
 * Écran de détail d'une plante (TD A7 + Capacité Native du Projet Jour 5) :
 * 1. CAPACITÉ NATIVE : Luxmètre matériel en direct (`Sensor.TYPE_LIGHT`) via `LightSensorSource`
 *    et `collectAsStateWithLifecycle()`, avec diagnostic d'ensoleillement potager et enregistrement dans Room.
 * 2. PERMISSIONS RUNTIME (`PermissionFlow`) :
 *    - Position GPS précise ou approximative (`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, `requireAll = false`).
 *    - Caméra en pipeline (`CameraX` lié au `LifecycleOwner` -> `filesDir/photos/` -> `FileProvider`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    plantId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = CarnetContainer.repository
    val lightSensorSource = CarnetContainer.lightSensorSource

    val plant by remember(plantId) {
        repository.observePlant(plantId)
    }.collectAsStateWithLifecycle(initialValue = null)

    // Lecture en temps réel du capteur de luminosité physique (uniquement quand l'écran est STARTED)
    val liveSensorLux by remember(lightSensorSource) {
        lightSensorSource.observeLux()
    }.collectAsStateWithLifecycle(initialValue = plant?.lastLux ?: 4_500f)

    var simulatedLux by rememberSaveable { mutableFloatStateOf(4_500f) }
    val activeLux = if (lightSensorSource.isAvailable) liveSensorLux else simulatedLux
    val exposureLevel = classifyLux(activeLux)

    // Permissions Runtime (Chapitre 6 & TD A7)
    val cameraFlow = rememberPermissionFlow(Manifest.permission.CAMERA, requireAll = true)
    val locationFlow = rememberPermissionFlow(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        requireAll = false, // Approximative acceptée (Slide 83 & 101 du Jour 5)
    )

    var showCameraRationale by rememberSaveable { mutableStateOf(false) }
    var showLocationRationale by rememberSaveable { mutableStateOf(false) }
    var showCameraPreview by rememberSaveable { mutableStateOf(false) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val currentPlant = plant

    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text("Pourquoi la caméra ?") },
            text = {
                Text(
                    "La caméra permet de photographier l'évolution de votre plante et d'enregistrer " +
                        "le cliché dans le répertoire privé de l'application (filesDir/photos/)."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showCameraRationale = false
                    cameraFlow.request()
                }) {
                    Text("Autoriser la caméra")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraRationale = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showLocationRationale) {
        AlertDialog(
            onDismissRequest = { showLocationRationale = false },
            title = { Text("Pourquoi la localisation ?") },
            text = {
                Text(
                    "La position GPS (précise ou approximative) sert à géolocaliser la parcelle de cette " +
                        "plante au jardin. L'application fonctionne même si vous choisissez « Approximative »."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showLocationRationale = false
                    locationFlow.request()
                }) {
                    Text("Autoriser la position")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationRationale = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentPlant?.name ?: "Fiche plante #$plantId",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                        )
                    }
                },
                actions = {
                    if (currentPlant != null) {
                        // Bouton explicite pour ouvrir cette fiche sur le Web
                        FilledTonalButton(
                            onClick = { PlantIntents.openPwaInBrowser(context, currentPlant.id.toString()) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Voir sur le Web",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Web",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(
                            onClick = { PlantIntents.sharePlant(context, currentPlant) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Partager la fiche",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    ) { innerPadding ->
        if (currentPlant == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Chargement de la plante #$plantId depuis Room…")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Résumé de la fiche et bouton d'arrosage
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${currentPlant.name} (${currentPlant.category})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "📅 Planté le ${currentPlant.plantedAt} · Dernier arrosage : ${currentPlant.lastWatered}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "💧 Prochain arrosage : ${currentPlant.waterStatus.label} (tous les ${currentPlant.waterInterval} jours)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (currentPlant.notes.isNotBlank()) {
                        Text(
                            text = currentPlant.notes,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    repository.waterPlantToday(currentPlant)
                                    Toast.makeText(context, "💧 Arrosage enregistré !", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Arrosé aujourd'hui")
                        }

                        OutlinedButton(
                            onClick = { PlantIntents.sharePlant(context, currentPlant) }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Partager (FileProvider)")
                        }
                    }
                }
            }

            // 2. CAPACITÉ NATIVE (Chapitre 8) : Luxmètre potager via Sensor.TYPE_LIGHT
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "☀️ Luxmètre Natif (Sensor.TYPE_LIGHT)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Capacité accordée par l'OS et refusée aux PWA (AmbientLightSensor)",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    Text(
                        text = "Capteur matériel : ${lightSensorSource.sensorName}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text(
                        text = "${activeLux.toInt()} lux — ${exposureLevel.title}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    LinearProgressIndicator(
                        progress = { (activeLux / 25_000f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )

                    if (!lightSensorSource.isAvailable) {
                        Text(
                            text = "Émulateur sans capteur optique : déplacez le curseur ou utilisez Extended Controls > Virtual sensors",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Slider(
                            value = simulatedLux,
                            onValueChange = { simulatedLux = it },
                            valueRange = 100f..25_000f,
                        )
                    }

                    Text(
                        text = exposureLevel.recommendation,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "🌿 Plantes adaptées : ${exposureLevel.matchingCategories}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (currentPlant.lastLux != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "✓ Dernier relevé enregistré sur cette parcelle : ${currentPlant.lastLux.toInt()} lux (${classifyLux(currentPlant.lastLux).title})",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                repository.updateLux(currentPlant.id, activeLux)
                                Toast.makeText(
                                    context,
                                    "☀️ ${activeLux.toInt()} lux enregistré dans Room pour ${currentPlant.name}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    ) {
                        Text("Enregistrer ce relevé (${activeLux.toInt()} lx) sur la fiche")
                    }
                }
            }

            // 3. TD A7 : Localisation précise ou approximative (PermissionFlow)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "📍 Position de la parcelle au jardin (TD A7)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    if (currentPlant.lat != null && currentPlant.lng != null) {
                        Text(
                            text = "Coordonnées : %.5f, %.5f ( à ${currentPlant.accuracy?.toInt() ?: 0} m près )".format(
                                currentPlant.lat,
                                currentPlant.lng,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            text = "Aucune position enregistrée pour cette plante.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    PermissionActionButton(
                        flow = locationFlow,
                        notAskedLabel = "Relever la position GPS",
                        deniedOnceLabel = "Relever la position (expliquer)",
                        deniedForeverLabel = "Ouvrir les réglages (position refusée)",
                        grantedLabel = if (isFetchingLocation) "Lecture GPS en cours…" else "Mettre à jour la position GPS",
                        onShowRationale = { showLocationRationale = true },
                        onGranted = {
                            if (isFetchingLocation) return@PermissionActionButton
                            isFetchingLocation = true
                            scope.launch {
                                val fix = CarnetContainer.locationSource.current()
                                isFetchingLocation = false
                                if (fix != null) {
                                    repository.updateLocation(
                                        plantId = currentPlant.id,
                                        lat = fix.latitude,
                                        lng = fix.longitude,
                                        accuracy = fix.accuracy,
                                    )
                                    val precisionLabel = if (fix.precise) "précise" else "approximative"
                                    Toast.makeText(
                                        context,
                                        "Position $precisionLabel enregistrée (à ${fix.accuracy.toInt()} m près)",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Position indisponible (activez le GPS ou adb emu geo fix)",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }

            // 4. TD A7 : Caméra en pipeline (CameraX -> filesDir/photos/ -> FileProvider)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "📷 Photo de la plante (CameraX → filesDir/photos/)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    SavedPhotoView(photoPath = currentPlant.photoPath)

                    PermissionActionButton(
                        flow = cameraFlow,
                        notAskedLabel = "Prendre une photo",
                        deniedOnceLabel = "Prendre une photo (expliquer)",
                        deniedForeverLabel = "Ouvrir les réglages (caméra refusée)",
                        grantedLabel = if (showCameraPreview) "Fermer l'aperçu CameraX" else "Ouvrir CameraX & prendre une photo",
                        onShowRationale = { showCameraRationale = true },
                        onGranted = {
                            showCameraPreview = !showCameraPreview
                        }
                    )

                    if (showCameraPreview && cameraFlow.state == PermissionState.Granted) {
                        CameraXCaptureBox(
                            onPhotoSaved = { savedPath ->
                                showCameraPreview = false
                                scope.launch {
                                    repository.updatePhoto(currentPlant.id, savedPath)
                                    Toast.makeText(
                                        context,
                                        "📷 Photo enregistrée dans filesDir/photos/",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Un bouton par état de permission (Chapitre 6, Slide 82 du Jour 5) :
 * - `NotAsked` -> lance le dialogue système.
 * - `DeniedOnce` -> affiche d'abord l'explication (`AlertDialog`) puis relance le dialogue.
 * - `DeniedForever` (après 2 refus) -> ouvre l'écran des réglages de l'application dans l'OS.
 * - `Granted` -> exécute l'action matérielle (GPS ou CameraX).
 */
@Composable
private fun PermissionActionButton(
    flow: PermissionFlow,
    notAskedLabel: String,
    deniedOnceLabel: String,
    deniedForeverLabel: String,
    grantedLabel: String,
    onShowRationale: () -> Unit,
    onGranted: () -> Unit,
) {
    when (flow.state) {
        PermissionState.NotAsked -> {
            OutlinedButton(onClick = { flow.request() }) {
                Text(notAskedLabel)
            }
        }
        PermissionState.DeniedOnce -> {
            OutlinedButton(onClick = onShowRationale) {
                Text(deniedOnceLabel)
            }
        }
        PermissionState.DeniedForever -> {
            OutlinedButton(onClick = { flow.openSettings() }) {
                Text(deniedForeverLabel)
            }
        }
        PermissionState.Granted -> {
            Button(onClick = onGranted) {
                Text(grantedLabel)
            }
        }
    }
}

@Composable
private fun SavedPhotoView(photoPath: String?) {
    if (photoPath.isNullOrBlank()) {
        Text(
            text = "Aucune photo enregistrée dans filesDir/photos/ pour cette plante.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    var bitmap by remember(photoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(photoPath) {
        bitmap = withContext(Dispatchers.IO) {
            val file = File(photoPath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Photo de la plante",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
        Text(
            text = "Fichier local : $photoPath",
            style = MaterialTheme.typography.labelSmall,
        )
    } else {
        Text(
            text = "Chemin en base : $photoPath",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CameraXCaptureBox(
    onPhotoSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }.onFailure { err ->
                        Log.e(TAG, "Erreur d'initialisation CameraX", err)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        )

        Button(
            onClick = {
                val outputFile = CarnetContainer.photoStore.newPhotoFile()
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            Log.d(TAG, "Photo CameraX écrite dans ${outputFile.absolutePath}")
                            onPhotoSaved(outputFile.absolutePath)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Échec de capture CameraX", exception)
                            Toast.makeText(
                                context,
                                "Erreur CameraX : ${exception.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                )
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Capturer la photo")
        }
    }
}

