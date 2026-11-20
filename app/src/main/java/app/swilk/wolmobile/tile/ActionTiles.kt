package app.swilk.wolmobile.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import app.swilk.wolmobile.data.get
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.enqueueRun
import app.swilk.wolmobile.ui.runIntent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class ActionTileService(private val slot: Int) : TileService() {
    private val scope = MainScope()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun action() = db.settings().get().tiles[slot]?.let { db.actions().get(it) }

    override fun onStartListening() {
        scope.launch {
            val action = action()
            val tile = qsTile ?: return@launch
            tile.label = action?.label ?: "WoL tile ${slot + 1}"
            tile.state = if (action == null) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        scope.launch {
            val action = action() ?: return@launch
            val run = Runnable {
                if (action.confirmBeforeRun) {
                    openActivity(runIntent(this@ActionTileService, action.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    enqueueRun(this@ActionTileService, action.id)
                    Toast.makeText(this@ActionTileService, "Running ${action.label}", Toast.LENGTH_SHORT).show()
                }
            }
            if (isLocked) unlockAndRun(run) else run.run()
        }
    }

    private fun openActivity(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, slot, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

class ActionTile1 : ActionTileService(0)
class ActionTile2 : ActionTileService(1)
class ActionTile3 : ActionTileService(2)
