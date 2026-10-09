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
                "Benachrichtigungen sind aus",
                "Ohne sie erscheint der klingelnde Wecker nicht auf dem Bildschirm.",
                "Erlauben",
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                critical = true,
            )
        }

        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            result += Issue(
                "Vollbildanzeige nicht erlaubt",
                "Der Wecker kann dann nicht über dem Sperrbildschirm erscheinen.",
                "Erlauben",
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
                critical = true,
            )
        }

        if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            result += Issue(
                "Genaue Wecker nicht erlaubt",
                "Ohne diese Berechtigung kann Android den Wecker verzögern.",
                "Erlauben",
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg),
                critical = true,
            )
        }

        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            result += Issue(
                "Weckerlautstärke steht auf 0",
                "Der Wecker würde nur vibrieren. Stell die Weckerlautstärke in den Toneinstellungen hoch.",
                "Töne",
                Intent(Settings.ACTION_SOUND_SETTINGS),
                critical = true,
            )
        }

        when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> result += Issue(
                "„Nicht stören“ steht auf „Lautlos“",
                "In diesem Modus lässt Android auch keine Wecker klingeln.",
                "Ändern",
                Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
                critical = true,
            )
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> if (Build.VERSION.SDK_INT >= 28 && !alarmsAllowedInDnd(nm)) {
                result += Issue(
                    "„Nicht stören“ blockiert Wecker",
                    "Erlaube im aktiven Ruhemodus die Kategorie „Wecker“.",
                    "Ändern",
                    Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
                    critical = true,
                )
            }
        }

        // Akku „Eingeschränkt“: Android selbst lässt Wecker-Apps trotzdem klingeln (im Emulator geprüft),
        // manche Hersteller blockieren den Klingeldienst dann aber – dafür gibt es das Notfall-Klingeln.
        if (Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted) {
            result += Issue(
                "Akku-Nutzung ist „Eingeschränkt“",
                "Manche Hersteller blockieren den Wecker dann im Hintergrund. Sicherer ist „Optimiert“ oder „Nicht eingeschränkt“.",
                "Ändern",
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
                critical = false,
            )
        }

        // Klingel-Kanal einzeln abgeschaltet oder heruntergestuft: dann kein Vollbild und keine Knöpfe
        // zum Beenden/Schlummern, obwohl der Ton läuft.
        val channel = nm.getNotificationChannel(AlarmService.CHANNEL_ID)
        if (notificationsAllowed && channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) {
            result += Issue(
                "Benachrichtigung „Klingelnder Wecker“ abgeschwächt",
                "Der klingelnde Wecker erscheint dann nicht auf dem Bildschirm. Stell die Kategorie wieder auf „Pop-up/Dringend“.",
                "Ändern",
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL_ID),
                critical = true,
            )
        }

        if (AlarmStore.loadProblem) {
            result += Issue(
                "Gespeicherte Wecker waren beschädigt",
                "Einige Wecker konnten nicht gelesen werden – bitte die Liste prüfen und fehlende Wecker neu anlegen. Bereits geplante klingeln trotzdem.",
                null, null, critical = true,
            )
        }
        if (AlarmStore.writeFailed.value) {
            result += Issue(
                "Wecker konnten nicht gespeichert werden",
                "Vermutlich ist der Speicher voll. Nach einem Neustart fehlen sonst die letzten Änderungen.",
                "Speicher", Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), critical = true,
            )
        }

        val power = context.getSystemService(PowerManager::class.java)
        if (!power.isIgnoringBatteryOptimizations(context.packageName)) {
            result += Issue(
                "Akku-Optimierung ist aktiv",
                "Android selbst lässt Wecker trotzdem klingeln, manche Hersteller beenden die App aber im " +
                    "Hintergrund. Eine Ausnahme macht den Wecker auf solchen Geräten zuverlässiger.",
                "Ausnahme",
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
