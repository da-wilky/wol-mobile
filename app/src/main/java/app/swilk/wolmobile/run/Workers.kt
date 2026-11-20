package app.swilk.wolmobile.run

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.swilk.wolmobile.data.ActionState
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.RunLog
import app.swilk.wolmobile.data.RunState
import app.swilk.wolmobile.db
import kotlinx.coroutines.delay

const val KEY_ACTION_ID = "actionId"
const val KEY_HOST_ID = "hostId"
const val KEY_EXPECT_UP = "expectUp"

private const val RESULT_DISPLAY_MS = 4_000L
private const val LOG_SIZE = 100
private const val OUTPUT_LIMIT = 8_000

class RunActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = context.db
        val action = db.actions().get(inputData.getLong(KEY_ACTION_ID, -1)) ?: return Result.success()
        val host = action.hostId?.let { db.hosts().get(it) }
        val upBefore = if (action.type == ActionType.SSH && host != null) checkHost(context, host) else null

        suspend fun show(state: RunState) {
            db.runs().setState(ActionState(action.id, state))
            refreshWidgets(context)
        }

        show(RunState.RUNNING)
        val outcome = runAction(context, action)
        db.runs().insertLog(
            RunLog(
                label = action.label, at = System.currentTimeMillis(), ok = outcome.ok,
                message = outcome.message, output = outcome.output.take(OUTPUT_LIMIT),
            ),
        )
        db.runs().trimLog(LOG_SIZE)
        if (!outcome.ok) notifyFailure(context, action.label, outcome)
        show(if (outcome.ok) RunState.OK else RunState.FAILED)

        if (outcome.ok && host?.probeAddress != null) {
            when {
                action.type == ActionType.WAKE -> enqueuePoll(context, host.id, expectUp = true)
                upBefore != null -> enqueuePoll(context, host.id, expectUp = !upBefore)
            }
        }
        delay(RESULT_DISPLAY_MS)
        show(RunState.IDLE)
        return Result.success()
    }
}

/** Polls a host after an action until it reaches the expected state or gives up. */
class PollHostWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val host = applicationContext.db.hosts().get(inputData.getLong(KEY_HOST_ID, -1)) ?: return Result.success()
        val expectUp = inputData.getBoolean(KEY_EXPECT_UP, true)
        repeat(24) {
            val up = checkHost(applicationContext, host)
            refreshWidgets(applicationContext)
            if (up == null || up == expectUp) return Result.success()
            delay(5_000)
        }
        return Result.success()
    }
}

class StatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        checkAllHosts(applicationContext)
        return Result.success()
    }
}
