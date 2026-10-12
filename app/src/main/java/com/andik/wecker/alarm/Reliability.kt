package com.andik.wecker.alarm

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andik.wecker.R
import com.andik.wecker.data.AlarmStore

/** Ein Umstand, der das Klingeln verhindern oder stören kann, samt passender Einstellungsseite. */
data class Issue(
    val title: String,
    val text: String,
    val action: String?,
    val intent: Intent?,
    /** Kritisch = Wecker klingelt sonst sicher nicht; sonst nur eine Empfehlung. */
    val critical: Boolean,
)

object Reliability {
    fun issues(context: Context): List<Issue> {
        val pkg = Uri.parse("package:${context.packageName}")
        val nm = context.getSystemService(NotificationManager::class.java)
        val result = mutableListOf<Issue>()

        val notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)
        if (!notificationsAllowed) {
            result += Issue(
                context.getString(R.string.issue_notifications_title),
                context.getString(R.string.issue_notifications_text),
                context.getString(R.string.action_allow),
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                critical = true,
            )
        }

        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            result += Issue(
                context.getString(R.string.issue_fullscreen_title),
                context.getString(R.string.issue_fullscreen_text),
                context.getString(R.string.action_allow),
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
                critical = true,
            )
        }

        if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            result += Issue(
                context.getString(R.string.issue_exact_title),
                context.getString(R.string.issue_exact_text),
                context.getString(R.string.action_allow),
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg),
                critical = true,
            )
        }

        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            result += Issue(
                context.getString(R.string.issue_volume_title),
                context.getString(R.string.issue_volume_text),
                context.getString(R.string.action_sounds),
                Intent(Settings.ACTION_SOUND_SETTINGS),
                critical = true,
            )
        }

        when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> result += Issue(
                context.getString(R.string.issue_dnd_silent_title),
                context.getString(R.string.issue_dnd_silent_text),
                context.getString(R.string.action_change),
                Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
                critical = true,
            )
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> if (Build.VERSION.SDK_INT >= 28 && !alarmsAllowedInDnd(nm)) {
                result += Issue(
                    context.getString(R.string.issue_dnd_blocks_title),
                    context.getString(R.string.issue_dnd_blocks_text),
                    context.getString(R.string.action_change),
                    Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
                    critical = true,
                )
            }
        }

        // Akku „Eingeschränkt“: Android selbst lässt Wecker-Apps trotzdem klingeln (im Emulator geprüft),
        // manche Hersteller blockieren den Klingeldienst dann aber – dafür gibt es das Notfall-Klingeln.
        if (Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted) {
            result += Issue(
                context.getString(R.string.issue_restricted_title),
                context.getString(R.string.issue_restricted_text),
                context.getString(R.string.action_change),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
                critical = false,
            )
        }

        // Klingel-Kanal einzeln abgeschaltet oder heruntergestuft: dann kein Vollbild und keine Knöpfe
        // zum Beenden/Schlummern, obwohl der Ton läuft.
        val channel = nm.getNotificationChannel(AlarmService.CHANNEL_ID)
        if (notificationsAllowed && channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) {
            result += Issue(
                context.getString(R.string.issue_channel_title),
                context.getString(R.string.issue_channel_text),
                context.getString(R.string.action_change),
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL_ID),
                critical = true,
            )
        }

        if (AlarmStore.loadProblem) {
            result += Issue(
                context.getString(R.string.issue_damaged_title),
                context.getString(R.string.issue_damaged_text),
                null, null, critical = true,
            )
        }
        if (AlarmStore.writeFailed.value) {
            result += Issue(
                context.getString(R.string.issue_write_title),
                context.getString(R.string.issue_write_text),
                context.getString(R.string.action_storage), Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), critical = true,
            )
        }

        val power = context.getSystemService(PowerManager::class.java)
        if (!power.isIgnoringBatteryOptimizations(context.packageName)) {
            result += Issue(
                context.getString(R.string.issue_battery_title),
                context.getString(R.string.issue_battery_text),
                context.getString(R.string.action_exception),
                @Suppress("BatteryLife")
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                critical = false,
            )
        }
        return result
    }

    @androidx.annotation.RequiresApi(28)
    private fun alarmsAllowedInDnd(nm: NotificationManager): Boolean = runCatching {
        nm.notificationPolicy.priorityCategories and
            NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
    }.getOrDefault(true)
}
