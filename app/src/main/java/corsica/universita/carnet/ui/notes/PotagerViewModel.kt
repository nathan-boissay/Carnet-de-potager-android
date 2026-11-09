package corsica.universita.carnet.ui.notes

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.carnet.CarnetContainer
import corsica.universita.carnet.data.Plant
import corsica.universita.carnet.data.PlantsRepository
import corsica.universita.carnet.data.local.Settings
import corsica.universita.carnet.data.todayIsoString
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "Carnet"

private const val KEY_DRAFT_NAME = "draft_name"
private const val KEY_DRAFT_CATEGORY = "draft_category"
private const val KEY_DRAFT_INTERVAL = "draft_interval"
private const val KEY_DRAFT_NOTES = "draft_notes"

/**
 * ViewModel principal de l'application (Jour 4 Chapitre 3-4 & Jour 5 Chapitre 1-3) :
 * - Survit aux changements de configuration (rotations).
 * - Garde tous les champs du brouillon en cours de saisie dans `SavedStateHandle` (Bundle)
 *   afin qu'ils survivent aussi à la mort du process (`adb shell am kill corsica.universita.carnet`).
 * - Expose la liste des plantes sous forme de `StateFlow<List<Plant>>` via `stateIn(WhileSubscribed(5_000))`.
 */
class PotagerViewModel(
    private val savedState: SavedStateHandle,
    private val repository: PlantsRepository,
    settings: Settings,
) : ViewModel() {

    val plants: StateFlow<List<Plant>> = repository.plants
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val pendingSyncCount: StateFlow<Int> = repository.plants
        .map { list -> list.count { !it.synced } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    val fcmToken: StateFlow<String?> = settings.fcmToken
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    // Brouillon du formulaire stocké dans le Bundle (SavedStateHandle) -> survit à `am kill`
    val draftName: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_NAME, "")
    val draftCategory: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_CATEGORY, "Solanacée")
    val draftInterval: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_INTERVAL, "3")
    val draftNotes: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_NOTES, "")

    init {
        Log.d(TAG, "PotagerViewModel créé — instance ${System.identityHashCode(this)}")
        viewModelScope.launch {
            repository.ensureSeedPlants()
        }
    }

    fun onDraftNameChange(value: String) {
        savedState[KEY_DRAFT_NAME] = value
    }

    fun onDraftCategoryChange(value: String) {
        savedState[KEY_DRAFT_CATEGORY] = value
    }

    fun onDraftIntervalChange(value: String) {
        savedState[KEY_DRAFT_INTERVAL] = value.filter { it.isDigit() }
    }

    fun onDraftNotesChange(value: String) {
        savedState[KEY_DRAFT_NOTES] = value
    }

    fun addPlant() {
        val name = draftName.value.trim()
        if (name.isEmpty()) return
        val category = draftCategory.value.trim().ifEmpty { "Potager" }
        val interval = draftInterval.value.toIntOrNull()?.coerceAtLeast(1) ?: 3
        val notes = draftNotes.value.trim()

        viewModelScope.launch {
            repository.addPlant(
                name = name,
                category = category,
                plantedAt = todayIsoString(),
                waterInterval = interval,
                notes = notes,
            )
        }

        // Réinitialisation du brouillon dans le SavedStateHandle
        savedState[KEY_DRAFT_NAME] = ""
        savedState[KEY_DRAFT_NOTES] = ""
    }

    fun waterPlantToday(plant: Plant) {
        viewModelScope.launch {
            repository.waterPlantToday(plant)
        }
    }

    fun togglePinned(plant: Plant) {
        viewModelScope.launch {
            repository.togglePinned(plant)
        }
    }

    fun deletePlant(id: Long) {
        viewModelScope.launch {
            repository.deletePlant(id)
        }
    }

    /**
     * TD A3 : Quand l'application reçoit un texte partagé (`ACTION_SEND`) ou un App Link (`ACTION_VIEW`),
     * pré-remplit le brouillon du formulaire (et le nom de la plante si vide).
     */
    fun receiveSharedText(sharedText: String) {
        val clean = sharedText.trim()
        if (clean.isEmpty()) return
        Log.d(TAG, "Texte / Lien reçu par Intent dans PotagerViewModel : $clean")
        if (draftName.value.isBlank()) {
            savedState[KEY_DRAFT_NAME] = "Plante partagée"
        }
        val existingNotes = draftNotes.value
        savedState[KEY_DRAFT_NOTES] = if (existingNotes.isBlank()) {
            clean
        } else {
            "$existingNotes\n$clean"
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "PotagerViewModel.onCleared — instance ${System.identityHashCode(this)}")
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PotagerViewModel(
                    savedState = createSavedStateHandle(),
                    repository = CarnetContainer.repository,
                    settings = CarnetContainer.settings,
                )
            }
        }
    }
}

