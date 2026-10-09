package com.andik.wecker.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andik.wecker.R
import com.andik.wecker.model.Alarm
import com.andik.wecker.model.Format
import android.content.BroadcastReceiver
import com.andik.wecker.data.AlarmStore
import com.andik.wecker.ui.RingingActivity

/**
 * Notfall-Klingeln, wenn der Klingeldienst nicht starten darf (z. B. Akku-Modus „Eingeschränkt“,
 * Herstellerbeschränkungen, ungenauer Ersatzwecker ohne Startfreigabe). Statt still zu bleiben,
 * kommt eine Benachrichtigung, deren Ton sich bis zum Antippen/Wegwischen wiederholt (INSISTENT).
 */
object EmergencyAlarm {
    private const val CHANNEL_ID = "notfall"
    private const val NOTIFICATION_BASE = 2000

    private fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Wecker (Notfall)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Klingelt, falls der normale Weckton nicht gestartet werden kann"
                setSound(
                    Uri.parse("android.resource://${context.packageName}/${R.raw.fallback_alarm}"),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 700)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true) // wirkt nur, wenn der Nutzer es zulässt; sonst ignoriert
            },
        )
    }

    fun ring(context: Context, alarm: Alarm) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val notificationId = NOTIFICATION_BASE + alarm.id
        // Vollbild/Antippen: der Klingelbildschirm (über der Sperre, auch vor dem ersten Entsperren)
        // im Notfall-Modus – sein „Beenden“ entfernt diese Benachrichtigung.
        val open = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, RingingActivity::class.java)
                .putExtra(RingingActivity.EXTRA_EMERGENCY_NOTIFICATION, notificationId)
                .putExtra(RingingActivity.EXTRA_EMERGENCY_LABEL, alarm.label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            context, notificationId,
            Intent(context, EmergencyStopReceiver::class.java).putExtra(EmergencyStopReceiver.EXTRA_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ringMinutes = AlarmStore.settings.value.ringMinutes
        val title = alarm.label.ifBlank { "Wecker" } + " – " + Format.time(alarm.hour, alarm.minute)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText("Der normale Weckton konnte nicht starten.")
            .addAction(0, "Beenden", stop)
            .apply { if (ringMinutes > 0) setTimeoutAfter(ringMinutes * 60_000L) }
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }
}

/** „Beenden“ in der Notfall-Benachrichtigung – funktioniert auch auf dem Sperrbildschirm. */
class EmergencyStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id >= 0) NotificationManagerCompat.from(context).cancel(id)
    }

    companion object {
        const val EXTRA_ID = "notificationId"
    }
}
