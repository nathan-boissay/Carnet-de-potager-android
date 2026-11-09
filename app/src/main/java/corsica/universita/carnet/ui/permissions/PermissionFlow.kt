package corsica.universita.carnet.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Les 4 états d'une permission runtime (Chapitre 6, Slides 78-82 & TD A7 du Jour 5).
 * Sépare explicitement `NotAsked` et `DeniedForever` grâce au drapeau `askedBefore`
 * sauvegardé dans le Bundle (`rememberSaveable`).
 */
enum class PermissionState {
    NotAsked,
    Granted,
    DeniedOnce,
    DeniedForever,
}

class PermissionFlow(
    private val activity: Activity,
    private val permissions: List<String>,
    private val requireAll: Boolean,
    private val askedBefore: () -> Boolean,
    private val markAsked: () -> Unit,
) {
    private var launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>? = null

    var state: PermissionState by mutableStateOf(compute(afterResult = false))
        private set

    fun attach(launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>) {
        this.launcher = launcher
        refresh()
    }

    fun refresh() {
        state = compute(afterResult = false)
    }

    fun request() {
        markAsked()
        launcher?.launch(permissions.toTypedArray())
    }

    fun onResult(@Suppress("UNUSED_PARAMETER") result: Map<String, Boolean>) {
        state = compute(afterResult = true)
    }

    fun openSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null),
        )
        activity.startActivity(intent)
    }

    private fun granted(): Boolean {
        val checks = permissions.map { perm ->
            ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
        }
        return if (requireAll) checks.all { it } else checks.any { it }
    }

    private fun rationale(): Boolean =
        permissions.any { perm ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }

    private fun compute(afterResult: Boolean): PermissionState = when {
        granted() -> PermissionState.Granted
        rationale() -> PermissionState.DeniedOnce
        askedBefore() || afterResult -> PermissionState.DeniedForever
        else -> PermissionState.NotAsked
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Aucune Activity trouvée dans le Context")
}

/**
 * Composable qui crée le `PermissionFlow`, l'attache au `RequestMultiplePermissions()`
 * et relit automatiquement l'état au retour des réglages système (`ON_RESUME`).
 */
@Composable
fun rememberPermissionFlow(
    vararg permissions: String,
    requireAll: Boolean = true,
): PermissionFlow {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val key = permissions.joinToString(",")
    var asked by rememberSaveable(key) { mutableStateOf(false) }

    val flow = remember(activity, key, requireAll) {
        PermissionFlow(
            activity = activity,
            permissions = permissions.toList(),
            requireAll = requireAll,
            askedBefore = { asked },
            markAsked = { asked = true },
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        flow.onResult(result)
    }

    flow.attach(launcher)

    DisposableEffect(lifecycleOwner, flow) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                flow.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return flow
}

