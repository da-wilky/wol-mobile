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
import app.swilk.wolmobile.db
import app.swilk.wolmobile.tile.ActionTile1
import app.swilk.wolmobile.tile.ActionTile2
import app.swilk.wolmobile.tile.ActionTile3
import app.swilk.wolmobile.ui.runIntent
import app.swilk.wolmobile.widget.GridWidget
import app.swilk.wolmobile.widget.SingleWidget

suspend fun refreshWidgets(context: Context) {
    SingleWidget().updateAll(context)
    GridWidget().updateAll(context)
}

fun refreshTiles(context: Context) {
    for (tile in listOf(ActionTile1::class.java, ActionTile2::class.java, ActionTile3::class.java)) {
        TileService.requestListeningState(context, ComponentName(context, tile))
    }
}

/** Propagates action changes to every surface that shows actions. */
suspend fun onActionsChanged(context: Context) {
    val actions = context.db.actions().list()
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    shortcutManager.dynamicShortcuts = actions.filter { it.shortcut }
        .take(shortcutManager.maxShortcutCountPerActivity)
        .map {
            ShortcutInfo.Builder(context, "action-${it.id}")
                .setShortLabel(it.label)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut))
                .setIntent(runIntent(context, it.id).setAction(Intent.ACTION_VIEW))
                .build()
        }
    refreshTiles(context)
    refreshWidgets(context)
}
