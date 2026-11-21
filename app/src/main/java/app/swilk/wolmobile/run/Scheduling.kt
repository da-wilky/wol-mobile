package app.swilk.wolmobile.run

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.swilk.wolmobile.db
import java.util.concurrent.TimeUnit

private const val PERIODIC_STATUS = "status-periodic"

fun enqueueRun(context: Context, actionId: Long) {
    val request = OneTimeWorkRequestBuilder<RunActionWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(workDataOf(KEY_ACTION_ID to actionId))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork("run-$actionId", ExistingWorkPolicy.KEEP, request)
}

fun enqueuePoll(context: Context, label: String, hostId: Long, expectUp: Boolean) {
    val request = OneTimeWorkRequestBuilder<PollHostWorker>()
        .setInputData(workDataOf(KEY_LABEL to label, KEY_HOST_ID to hostId, KEY_EXPECT_UP to expectUp))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork("poll-$hostId", ExistingWorkPolicy.REPLACE, request)
}

fun enqueueStatusCheck(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        "status-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<StatusWorker>().build(),
    )
}

/** Periodic checks run only while some widget shows host status. */
suspend fun syncPeriodicStatus(context: Context) {
    val workManager = WorkManager.getInstance(context)
    if (context.db.widgets().list().any { it.showStatus }) {
        val request = PeriodicWorkRequestBuilder<StatusWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_STATUS, ExistingPeriodicWorkPolicy.KEEP, request)
    } else {
        workManager.cancelUniqueWork(PERIODIC_STATUS)
    }
}
