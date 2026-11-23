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
const val KEY_LABEL = "label"

private const val RESULT_DISPLAY_MS = 4_000L
private const val LOG_SIZE = 100
private const val OUTPUT_LIMIT = 8_000
private const val POLL_ATTEMPTS = 24
private const val POLL_INTERVAL_MS = 5_000L

/** Logs the outcome and notifies on failure. */
private suspend fun report(context: Context, label: String, outcome: Outcome) {
    context.db.runs().insertLog(
        RunLog(
            label = label, at = System.currentTimeMillis(), ok = outcome.ok,
            message = outcome.message, output = outcome.output.take(OUTPUT_LIMIT),
        ),
    )
    context.db.runs().trimLog(LOG_SIZE)
    if (!outcome.ok) notifyFailure(context, label, outcome)
}

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
        report(context, action.label, outcome)
        show(if (outcome.ok) RunState.OK else RunState.FAILED)

        if (outcome.ok && host?.checkStatus == true) {
            when {
                action.type == ActionType.WAKE -> enqueuePoll(context, action.label, host.id, expectUp = true)
                upBefore != null -> enqueuePoll(context, action.label, host.id, expectUp = !upBefore)
            }
        }
        enqueueClearResult(context, action.id, RESULT_DISPLAY_MS)
        return Result.success()
    }
}

/** Polls a host after an action until it reaches the expected state or gives up. */
class PollHostWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val host = applicationContext.db.hosts().get(inputData.getLong(KEY_HOST_ID, -1)) ?: return Result.success()
        val expectUp = inputData.getBoolean(KEY_EXPECT_UP, true)
        repeat(POLL_ATTEMPTS) {
            val up = checkHost(applicationContext, host)
            refreshWidgets(applicationContext)
            if (up == null || up == expectUp) return Result.success()
            delay(POLL_INTERVAL_MS)
        }
        val state = if (expectUp) "did not come up" else "is still up"
        report(applicationContext, inputData.getString(KEY_LABEL).orEmpty(), Outcome(false, "${host.name} $state after ${POLL_ATTEMPTS * POLL_INTERVAL_MS / 60_000} minutes"))
        return Result.success()
    }
}

class StatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        checkAllHosts(applicationContext)
        return Result.success()
    }
}

/** Returns a finished action's widget cell to idle; separate so the run's unique work ends and the next tap isn't dropped. */
class ClearResultWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.db.runs().clearResult(inputData.getLong(KEY_ACTION_ID, -1))
        refreshWidgets(applicationContext)
        return Result.success()
    }
}
