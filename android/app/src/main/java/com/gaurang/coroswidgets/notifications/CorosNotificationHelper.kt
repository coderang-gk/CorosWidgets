package com.gaurang.coroswidgets.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gaurang.coroswidgets.MainActivity
import com.gaurang.coroswidgets.R
import com.gaurang.coroswidgets.data.LastRunData

object CorosNotificationHelper {

    private const val PREFS_NAME = "coros_notification_prefs"
    private const val KEY_LAST_NOTIFIED_ID = "last_notified_run_id"
    private const val CHANNEL_ID = "coros_run_analysis_channel"
    private const val NOTIFICATION_ID = 202610

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "COROS AI Run Analysis"
            val descriptionText = "Post-run AI coaching verdicts, physiological insights, and actionable tips"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = ContextCompat.getColor(context, android.R.color.system_accent1_200)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun maybeNotifyNewRun(context: Context, run: LastRunData) {
        if (run.name.isBlank() || run.distanceKm <= 0.0) return

        val runId = "${run.date}_${run.name}_${run.distanceKm}"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_ID, null)

        // Only notify if this is a newly arrived workout (and not initial first-time install launch)
        if (lastNotified == null) {
            prefs.edit().putString(KEY_LAST_NOTIFIED_ID, runId).apply()
            return
        }

        if (lastNotified == runId) {
            return
        }

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_RUN_DETAIL, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (run.aiVerdictHeadline.isNotBlank()) {
            "🏃 ${run.aiVerdictHeadline}"
        } else {
            "🏃 Run Analyzed: ${run.name}"
        }

        val summaryText = buildString {
            if (run.aiExecutiveSummary.isNotBlank()) {
                append(run.aiExecutiveSummary)
            } else if (run.physiologyNote.isNotBlank()) {
                append(run.physiologyNote)
            }
            if (run.coachGuidance.isNotBlank()) {
                append("\n\n👉 ").append(run.coachGuidance)
            }
        }

        val contentLine = String.format(
            "%.1f km · %s · Avg HR %d bpm (%s)",
            run.distanceKm,
            run.avgPace,
            run.avgHr,
            run.verdictBadge
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (summaryText.isNotBlank()) "$contentLine\n\n$summaryText" else contentLine)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())

        prefs.edit().putString(KEY_LAST_NOTIFIED_ID, runId).apply()
    }
}
