package com.andik.wecker.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andik.wecker.R
import com.andik.wecker.model.Alarm
import com.andik.wecker.model.Format
import com.andik.wecker.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

/** Meldet Wecker, die nicht (zu Ende) geklingelt haben – wie „Verpasster Wecker“ bei der Android-Uhr. */
object MissedNotifier {
    private const val CHANNEL_ID = "verpasst"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_missed_desc)
            },
        )
    }

    /** Scheitert nie nach außen – der Aufrufer ist oft gerade dabei, einen Wecker klingeln zu lassen. */
    fun notify(context: Context, alarm: Alarm, at: Long, reason: String) {
        runCatching { post(context, alarm, at, reason) }
    }

    private fun post(context: Context, alarm: Alarm, at: Long, reason: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.missed_title, Format.time(time)) + alarm.label.takeIf { it.isNotBlank() }?.let { " – $it" }.orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText("${Format.relativeDay(time.toLocalDate(), java.time.LocalDate.now())}: $reason")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(MISSED_BASE_ID + alarm.id, notification) }
    }

    private const val MISSED_BASE_ID = 1000
}
