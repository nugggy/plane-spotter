package au.plane.spotter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class Browse(val category: Category? = null, val localOnly: Boolean = false) : Screen
    data class Detail(val id: String) : Screen
    data object Quiz : Screen
    data class Compare(val firstId: String? = null) : Screen
    data object Log : Screen
    data object Guide : Screen
    data object About : Screen
    data class Scanner(val mode: ScanMode = ScanMode.CAMERA, val accidents: Boolean = false) : Screen
    data class Accident(val id: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SpotterStore(applicationContext)
        setContent {
            PlaneSpotterTheme { App(store) }
        }
    }
}

/** State shared by the update dialog and the "check for updates" button on the About screen. */
class UpdateState {
    var available by mutableStateOf<UpdateInfo?>(null)
    var showDialog by mutableStateOf(false)
    var progress by mutableStateOf<Float?>(null)
    var error by mutableStateOf<String?>(null)
    var lastCheckMessage by mutableStateOf<String?>(null)
}

@Composable
fun App(store: SpotterStore) {
    val context = LocalContext.current
    val planes = remember { PlaneRepo.all(context) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val updates = remember { UpdateState() }
    val scope = rememberCoroutineScope()

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // Check the update channel every time the app opens.
    LaunchedEffect(Unit) {
        Updater.check()?.let {
            updates.available = it
            updates.showDialog = true
        }
    }

    fun checkNow() {
        scope.launch {
            updates.lastCheckMessage = "Checking..."
            val info = Updater.check()
            if (info != null) {
                updates.available = info
                updates.showDialog = true
                updates.lastCheckMessage = "Version ${info.version} is available."
            } else {
                updates.lastCheckMessage = "You have the latest version (or there is no internet right now)."
            }
        }
    }

    fun startUpdate(info: UpdateInfo) {
        if (!Updater.canInstall(context)) {
            updates.error = "Android needs permission for Plane Spotter to install updates. " +
                "Turn on \"Allow from this source\", then come back and tap Update again."
            Updater.openInstallPermissionSettings(context)
            return
        }
        updates.error = null
        updates.progress = 0f
        scope.launch {
            try {
                val file = Updater.download(context, info) { updates.progress = it }
                updates.progress = null
                updates.showDialog = false
                Updater.install(context, file)
            } catch (e: Exception) {
                updates.progress = null
                updates.error = "The download didn't work. Check the internet connection and try again."
            }
        }
    }

    BackHandler(enabled = stack.size > 1) { back() }

    // Each entry on the back stack keeps its own saved state (filters, map position and so on).
    val saveable = rememberSaveableStateHolder()
    val current = stack.last()
    saveable.SaveableStateProvider("${stack.lastIndex}:$current") {
    when (val screen = current) {
        Screen.Home -> HomeScreen(planes, store, updates, ::go)
        is Screen.Browse -> BrowseScreen(planes, store, screen, ::go, ::back)
        is Screen.Detail -> planes.firstOrNull { it.id == screen.id }?.let { DetailScreen(it, planes, store, ::go, ::back) }
        Screen.Quiz -> QuizScreen(planes, store, ::back)
        is Screen.Compare -> CompareScreen(planes, screen.firstId, ::go, ::back)
        Screen.Log -> LogScreen(planes, store, ::go, ::back)
        Screen.Guide -> GuideScreen(::back)
        Screen.About -> AboutScreen(planes, updates, ::checkNow, ::back)
        is Screen.Scanner -> SkyScannerScreen(planes, store, screen, ::go, ::back)
        is Screen.Accident -> AccidentRepo.all(context).firstOrNull { it.id == screen.id }?.let { AccidentScreen(it, planes, ::go, ::back) }
    }
    }

    val info = updates.available
    if (updates.showDialog && info != null) {
        AlertDialog(
            onDismissRequest = { if (updates.progress == null) updates.showDialog = false },
            title = { Text("New version available") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Plane Spotter ${info.version} is ready. You have ${BuildConfig.VERSION_NAME}.")
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What's new", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                        Text(info.notes)
                    }
                    updates.progress?.let {
                        Spacer(Modifier.height(16.dp))
                        Text("Downloading...")
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    }
                    updates.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = updates.progress == null, onClick = { startUpdate(info) }) { Text("Update now") }
            },
            dismissButton = {
                TextButton(enabled = updates.progress == null, onClick = { updates.showDialog = false }) { Text("Later") }
            },
        )
    }
}
