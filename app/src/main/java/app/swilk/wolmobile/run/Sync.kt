package app.swilk.wolmobile.run

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.db
import app.swilk.wolmobile.tile.ActionTile1
import app.swilk.wolmobile.tile.ActionTile2
import app.swilk.wolmobile.tile.ActionTile3
import app.swilk.wolmobile.ui.runIntent
import app.swilk.wolmobile.widget.ActionWidget

suspend fun refreshWidgets(context: Context) = ActionWidget().updateAll(context)

fun refreshTiles(context: Context) {
    for (tile in listOf(ActionTile1::class.java, ActionTile2::class.java, ActionTile3::class.java)) {
        TileService.requestListeningState(context, ComponentName(context, tile))
    }
}

/** Propagates action changes to every surface that shows actions. Pinned shortcuts can't be removed, only disabled. */
suspend fun onActionsChanged(context: Context) {
    val actions = context.db.actions().list()
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    shortcutManager.dynamicShortcuts = actions.filter { it.shortcut }
        .take(shortcutManager.maxShortcutCountPerActivity)
        .map { actionShortcut(context, it) }
    val pinned = shortcutManager.pinnedShortcuts.map { it.id }.toSet()
    shortcutManager.updateShortcuts(actions.filter { shortcutId(it) in pinned }.map { actionShortcut(context, it) })
    shortcutManager.disableShortcuts((pinned - actions.map(::shortcutId).toSet()).toList(), "Action deleted")
    refreshTiles(context)
    refreshWidgets(context)
}

/** False if the launcher can't pin shortcuts; otherwise it asks the user where to place it. */
fun pinShortcut(context: Context, action: Action): Boolean {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    if (!shortcutManager.isRequestPinShortcutSupported) return false
    return shortcutManager.requestPinShortcut(actionShortcut(context, action), null)
}

private fun shortcutId(action: Action) = "action-${action.id}"

private fun actionShortcut(context: Context, action: Action): ShortcutInfo =
    ShortcutInfo.Builder(context, shortcutId(action))
        .setShortLabel(action.label)
        .setIcon(Icon.createWithResource(context, if (action.type == ActionType.WAKE) R.drawable.ic_shortcut_wake else R.drawable.ic_shortcut_ssh))
        .setIntent(runIntent(context, action.id).setAction(Intent.ACTION_VIEW))
        .build()
