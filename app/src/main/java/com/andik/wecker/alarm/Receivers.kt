package com.andik.wecker.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.andik.wecker.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.ACTION_FIRE) return
        WakeBridge.acquire(context) // bis der Klingeldienst seinen eigenen Wakelock hat
        AlarmStore.init(context)
        val id = intent.getIntExtra(Alarms.EXTRA_ID, -1)
        val known = Alarms.onFired(context, id)
        // Waren die gespeicherten Wecker unlesbar, ist eine Auslösung mit unbekannter ID trotzdem ein
        // gestellter Wecker – lieber mit Standardton klingeln als still bleiben. (Gelöschte Wecker
        // werden beim Löschen beim AlarmManager abgemeldet und kommen hier nicht an.)
        val unknown = known == null && AlarmStore.get(id) == null && (AlarmStore.loadProblem || id in AlarmStore.unreadableIds)
        if (known == null && !unknown) {
            WakeBridge.release()
            return
        }
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_RING)
                    .putExtra(Alarms.EXTRA_ID, id)
                    .putExtra(AlarmService.EXTRA_UNKNOWN, unknown)
                    .putExtra(AlarmService.EXTRA_FIRED_AT, System.currentTimeMillis()),
            )
        } catch (e: Exception) {
            // Dienststart verweigert (Akku „Eingeschränkt“, Herstellerbeschränkung …): Notfall-Klingeln.
            WakeBridge.release()
            val now = java.time.LocalTime.now()
            EmergencyAlarm.ring(context, known ?: com.andik.wecker.model.Alarm(id = id, hour = now.hour, minute = now.minute))
        }
    }
}

/** Nach Neustart, Zeit- oder Zeitzonenwechsel und App-Update alle Wecker neu planen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        AlarmStore.init(context)
        Alarms.rescheduleAll(context)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
