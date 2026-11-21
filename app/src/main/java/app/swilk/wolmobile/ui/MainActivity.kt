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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

const val EXTRA_OPEN_LOG = "openLog"

@Serializable data object Home : NavKey
@Serializable data class EditHost(val id: Long?) : NavKey
@Serializable data class EditNetwork(val id: Long?) : NavKey
@Serializable data class EditTarget(val id: Long?) : NavKey
@Serializable data class EditAction(val id: Long?) : NavKey
@Serializable data class KeyDetail(val id: Long) : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data class LogRoute(val focus: Long? = null) : NavKey

private fun runtimePermissions() = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
}.toTypedArray()

// The page being left stays on top while it slides away.
private fun popTransition() = (slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }).apply { targetContentZIndex = -1f }

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
                val backStack = if (openLog) rememberNavBackStack(Home, LogRoute()) else rememberNavBackStack(Home)
                val back: () -> Unit = { backStack.removeLastOrNull() }
                NavDisplay(
                    backStack = backStack,
                    onBack = back,
                    transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 } },
                    popTransitionSpec = { popTransition() },
                    predictivePopTransitionSpec = { popTransition() },
                    entryProvider = entryProvider {
                        entry<Home> { HomeScreen(open = { backStack.add(it) }) }
                        entry<EditHost> { HostEditor(it.id, done = back) }
                        entry<EditNetwork> { NetworkEditor(it.id, done = back) }
                        entry<EditTarget> { TargetEditor(it.id, done = back) }
                        entry<EditAction> { ActionEditor(it.id, done = back) }
                        entry<KeyDetail> { KeyScreen(it.id, done = back) }
                        entry<SettingsRoute> { SettingsScreen(done = back) }
                        entry<LogRoute> { LogScreen(it.focus, done = back) }
                    },
                )
            }
        }
    }
}
