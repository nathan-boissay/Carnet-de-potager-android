package corsica.universita.carnet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import corsica.universita.carnet.ui.notes.PlantDetailScreen
import corsica.universita.carnet.ui.notes.PotagerListScreen
import corsica.universita.carnet.ui.notes.PotagerViewModel
import kotlinx.serialization.Serializable

/**
 * Routes typées `@Serializable` (Jour 4 Chapitre 3 Slide 46 & TD A7 Slide 102) :
 * - `NotesList` : écran principal du potager (liste + formulaire + outbox).
 * - `NoteDetail(id: Long)` : écran de détail d'une plante (Luxmètre natif, GPS, CameraX).
 */
@Serializable
data object NotesList

@Serializable
data class NoteDetail(val id: Long)

@Composable
fun CarnetNavigation(
    viewModel: PotagerViewModel,
    openedPlantId: Long?,
    onConsumedPlantId: () -> Unit,
) {
    val navController = rememberNavController()

    // Si une notification ou un PendingIntent (`--el noteId 3`) demande d'ouvrir une plante précise
    LaunchedEffect(openedPlantId) {
        if (openedPlantId != null && openedPlantId > 0L) {
            navController.navigate(NoteDetail(id = openedPlantId))
            onConsumedPlantId()
        }
    }

    NavHost(
        navController = navController,
        startDestination = NotesList,
    ) {
        composable<NotesList> {
            PotagerListScreen(
                onOpenPlant = { id -> navController.navigate(NoteDetail(id = id)) },
                viewModel = viewModel,
            )
        }
        composable<NoteDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<NoteDetail>()
            PlantDetailScreen(
                plantId = route.id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

