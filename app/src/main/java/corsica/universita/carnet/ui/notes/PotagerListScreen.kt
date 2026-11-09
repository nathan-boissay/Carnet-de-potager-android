package corsica.universita.carnet.ui.notes

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import corsica.universita.carnet.R
import corsica.universita.carnet.data.Plant
import corsica.universita.carnet.data.WaterUrgency
import corsica.universita.carnet.notif.Notifications
import corsica.universita.carnet.ui.permissions.PermissionState
import corsica.universita.carnet.ui.permissions.rememberPermissionFlow

/**
 * Écran principal de l'application (TD A1 -> A6 + Projet Carnet de Potager) :
 * - Formulaire d'ajout de plante avec brouillon persistant (`SavedStateHandle`).
 * - Bandeau Outbox indiquant le nombre de fiches `synced = 0` en attente de WorkManager.
 * - Bouton de permission `POST_NOTIFICATIONS` + test d'alerte d'arrosage + copie du jeton FCM.
 * - Liste réactive `LazyColumn` alimentée par `Room` (`Flow` -> `StateFlow` -> `collectAsStateWithLifecycle`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PotagerListScreen(
    onOpenPlant: (Long) -> Unit,
    viewModel: PotagerViewModel = viewModel(factory = PotagerViewModel.Factory),
) {
    val context = LocalContext.current
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val fcmToken by viewModel.fcmToken.collectAsStateWithLifecycle()

    val draftName by viewModel.draftName.collectAsStateWithLifecycle()
    val draftCategory by viewModel.draftCategory.collectAsStateWithLifecycle()
    val draftInterval by viewModel.draftInterval.collectAsStateWithLifecycle()
    val draftNotes by viewModel.draftNotes.collectAsStateWithLifecycle()

    var showNotifRationale by rememberSaveable { mutableStateOf(false) }

    val notifPermissionFlow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionFlow(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    if (showNotifRationale && notifPermissionFlow != null) {
        AlertDialog(
            onDismissRequest = { showNotifRationale = false },
            title = { Text("Pourquoi autoriser les notifications ?") },
            text = {
                Text(
                    "Carnet de Potager utilise les notifications pour vous prévenir quand une plante a soif " +
                        "et lorsque vos fiches créées hors-ligne au fond du jardin ont été synchronisées."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showNotifRationale = false
                    notifPermissionFlow.request()
                }) {
                    Text("Continuer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifRationale = false }) {
                    Text("Plus tard")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🌱 Carnet de Potager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.plants_count,
                                plants.size,
                                plants.size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    // Bouton très clair pour ouvrir la version Web / PWA dans le navigateur
                    FilledTonalButton(
                        onClick = { PlantIntents.openPwaInBrowser(context) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Ouvrir le Web",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Site Web",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))

                // Barre d'état : Outbox WorkManager (TD A5) + Alertes d'arrosage (TD A6)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            if (pendingSyncCount > 0) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.pending_sync_count,
                                        pendingSyncCount,
                                        pendingSyncCount,
                                    )
                                )
                            } else {
                                Text("✓ Synchronisé (Room + Serveur)")
                            }
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (pendingSyncCount > 0) {
                                Color(0xFFFFF3E0)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        )
                    )

                    // Bouton Notifications (Machine à états TD A6 / A7)
                    val state = notifPermissionFlow?.state ?: PermissionState.Granted
                    when (state) {
                        PermissionState.NotAsked -> {
                            OutlinedButton(onClick = { notifPermissionFlow?.request() }) {
                                Icon(Icons.Default.Notifications, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Activer les alertes")
                            }
                        }
                        PermissionState.DeniedOnce -> {
                            OutlinedButton(onClick = { showNotifRationale = true }) {
                                Icon(Icons.Default.Notifications, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Expliquer & activer alertes")
                            }
                        }
                        PermissionState.DeniedForever -> {
                            OutlinedButton(onClick = { notifPermissionFlow?.openSettings() }) {
                                Icon(Icons.Default.Notifications, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Réglages notifications")
                            }
                        }
                        PermissionState.Granted -> {
                            FilledTonalButton(
                                onClick = {
                                    val thirsty = plants.filter { it.waterStatus.needsWater }
                                    Notifications.showWateringAlert(context, thirsty)
                                }
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Tester alerte arrosage")
                            }
                        }
                    }
                }
            }

            // Formulaire d'ajout d'une plante (brouillon dans SavedStateHandle)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "➕ Nouvelle plantation (survit à la rotation et à am kill)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = draftName,
                            onValueChange = viewModel::onDraftNameChange,
                            label = { Text(stringResource(R.string.draft_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = draftCategory,
                                onValueChange = viewModel::onDraftCategoryChange,
                                label = { Text(stringResource(R.string.draft_category_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = draftInterval,
                                onValueChange = viewModel::onDraftIntervalChange,
                                label = { Text("Arrosage (j)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(120.dp),
                            )
                        }
                        OutlinedTextField(
                            value = draftNotes,
                            onValueChange = viewModel::onDraftNotesChange,
                            label = { Text(stringResource(R.string.draft_notes_hint)) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = viewModel::addPlant,
                            enabled = draftName.isNotBlank(),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.add_plant))
                        }
                    }
                }
            }

            // Liste réactive des fiches de plantes (LazyColumn avec clé stable `it.id`)
            items(plants, key = { it.id }) { plant ->
                PlantCardItem(
                    plant = plant,
                    onOpen = { onOpenPlant(plant.id) },
                    onWaterToday = { viewModel.waterPlantToday(plant) },
                    onTogglePin = { viewModel.togglePinned(plant) },
                    onShare = { PlantIntents.sharePlant(context, plant) },
                    onDelete = { viewModel.deletePlant(plant.id) },
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PlantCardItem(
    plant: Plant,
    onOpen: () -> Unit,
    onWaterToday: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val waterStatus = plant.waterStatus
    val statusColor = when (waterStatus.urgency) {
        WaterUrgency.LATE -> Color(0xFFC62828)
        WaterUrgency.TODAY -> Color(0xFFEF6C00)
        WaterUrgency.OK -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = if (plant.pinned) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plant.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = plant.category,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "Planté le ${plant.plantedAt} · Arrosage tous les ${plant.waterInterval}j",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // TD A4 : Épingler une plante en tête de liste (colonne `pinned` v2)
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (plant.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Épingler",
                        tint = if (plant.pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // Statut d'arrosage + Badge de synchronisation Outbox (TD A5) + Luxmètre/GPS/Photo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "💧 Prochain arrosage : ${waterStatus.label}",
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Surface(
                    color = if (plant.synced) Color(0xFFE8F5E9) else Color(0xFFFFF8E1),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = if (plant.synced) "✓ envoyée" else "⏳ en attente",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (plant.synced) Color(0xFF1B5E20) else Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            if (plant.lastLux != null || plant.lat != null || !plant.photoPath.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (plant.lastLux != null) {
                        Text(
                            text = "☀️ ${plant.lastLux.toInt()} lx",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (plant.lat != null && plant.lng != null) {
                        Text(
                            text = "📍 GPS (±${plant.accuracy?.toInt() ?: 0} m)",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (!plant.photoPath.isNullOrBlank()) {
                        Text(
                            text = "📷 Photo jointe",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (plant.notes.isNotBlank()) {
                Text(
                    text = plant.notes,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onWaterToday) {
                    Icon(Icons.Default.WaterDrop, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Arrosé aujourd'hui")
                }

                Row {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Lux / Photo / GPS")
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Partager")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                }
            }
        }
    }
}

private fun copyFcmToken(context: Context, cachedToken: String?) {
    val copyToClipboard: (String) -> Unit = { token ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FCM Token", token))
        Toast.makeText(context, "Jeton FCM copié : ${token.take(16)}…", Toast.LENGTH_SHORT).show()
    }

    if (!cachedToken.isNullOrBlank()) {
        copyToClipboard(cachedToken)
        return
    }

    runCatching {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result else null
            if (!token.isNullOrBlank()) {
                copyToClipboard(token)
            } else {
                Toast.makeText(
                    context,
                    "Play Services / Firebase indisponible sur cet appareil (voir Logcat)",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }.onFailure {
        Toast.makeText(context, "Jeton FCM non disponible", Toast.LENGTH_SHORT).show()
    }
}

