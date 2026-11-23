package app.swilk.wolmobile.run

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.swilk.wolmobile.R
import app.swilk.wolmobile.ui.EXTRA_OPEN_LOG
import app.swilk.wolmobile.ui.MainActivity

private const val CHANNEL_FAILURES = "failures"

fun createNotificationChannels(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL_FAILURES, "Failed actions", NotificationManager.IMPORTANCE_DEFAULT),
    )
}

fun notifyFailure(context: Context, label: String, outcome: Outcome) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val open = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_LOG, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val text = listOf(outcome.message, outcome.output).filter { it.isNotBlank() }.joinToString("\n")
    // Command output can be sensitive; the lock screen only shows which action failed.
    val public = Notification.Builder(context, CHANNEL_FAILURES)
        .setSmallIcon(R.drawable.ic_power)
        .setContentTitle("$label failed")
        .build()
    val notification = Notification.Builder(context, CHANNEL_FAILURES)
        .setSmallIcon(R.drawable.ic_power)
        .setContentTitle("$label failed")
        .setContentText(outcome.message)
        .setStyle(Notification.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .setPublicVersion(public)
        .build()
    context.getSystemService(NotificationManager::class.java).notify(label.hashCode(), notification)
}
