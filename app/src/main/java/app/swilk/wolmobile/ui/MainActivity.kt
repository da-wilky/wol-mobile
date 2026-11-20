package app.swilk.wolmobile.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

const val EXTRA_OPEN_LOG = "openLog"

@Serializable data object Home : NavKey
@Serializable data class EditHost(val id: Long?) : NavKey
@Serializable data class EditTarget(val id: Long?) : NavKey
@Serializable data class EditAction(val id: Long?) : NavKey
@Serializable data class KeyDetail(val id: Long) : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object LogRoute : NavKey

private fun runtimePermissions() = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
}.toTypedArray()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openLog = intent.getBooleanExtra(EXTRA_OPEN_LOG, false)
        setContent {
            WolTheme {
                val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                LaunchedEffect(Unit) {
                    permissions.launch(runtimePermissions())
                }
                val backStack = if (openLog) rememberNavBackStack(Home, LogRoute) else rememberNavBackStack(Home)
                val back: () -> Unit = { backStack.removeLastOrNull() }
                NavDisplay(
                    backStack = backStack,
                    onBack = back,
                    entryProvider = entryProvider {
                        entry<Home> { HomeScreen(open = { backStack.add(it) }) }
                        entry<EditHost> { HostEditor(it.id, done = back) }
                        entry<EditTarget> { TargetEditor(it.id, done = back) }
                        entry<EditAction> { ActionEditor(it.id, done = back) }
                        entry<KeyDetail> { KeyScreen(it.id, done = back) }
                        entry<SettingsRoute> { SettingsScreen(done = back) }
                        entry<LogRoute> { LogScreen(done = back) }
                    },
                )
            }
        }
    }
}
