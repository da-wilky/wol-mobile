package app.swilk.wolmobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.db
import app.swilk.wolmobile.run.enqueueRun
import kotlinx.coroutines.launch

private const val EXTRA_ACTION_ID = "actionId"

/** Unique data URI per action so PendingIntents for different actions don't collapse into one. */
fun runIntent(context: Context, actionId: Long): Intent =
    Intent(context, RunActionActivity::class.java)
        .setData(Uri.parse("wolmobile://action/$actionId"))
        .putExtra(EXTRA_ACTION_ID, actionId)

fun startRun(context: Context, action: Action) {
    enqueueRun(context, action.id)
    Toast.makeText(context, "Running ${action.label}", Toast.LENGTH_SHORT).show()
}

/** Invisible entry point for shortcuts, tiles and widgets: confirms if the action asks for it, then runs it. */
class RunActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_ACTION_ID, -1)
        lifecycleScope.launch {
            val action = db.actions().get(id)
            when {
                action == null -> {
                    Toast.makeText(this@RunActionActivity, "Action no longer exists", Toast.LENGTH_SHORT).show()
                    finish()
                }
                !action.confirmBeforeRun -> {
                    startRun(this@RunActionActivity, action)
                    finish()
                }
                else -> setContent {
                    WolTheme {
                        ConfirmRunDialog(
                            action,
                            onConfirm = { startRun(this@RunActionActivity, action); finish() },
                            onDismiss = ::finish,
                        )
                    }
                }
            }
        }
    }
}
