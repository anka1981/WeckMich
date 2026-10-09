package com.andik.wecker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.andik.wecker.data.AlarmStore
import com.andik.wecker.model.Alarm
import com.andik.wecker.model.RepeatType
import com.andik.wecker.ui.MainActivity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/** Speichern und Planen von Weckern an einer Stelle, damit beides nie auseinanderläuft. */
object Alarms {
    const val EXTRA_ID = "alarmId"
    const val ACTION_FIRE = "com.andik.wecker.FIRE"

    /** Nächster Klingelzeitpunkt (Wiederholungsregel oder Schlummern, was früher ist) in Epoch-Millis. */
    fun nextFireMillis(a: Alarm, now: Long = System.currentTimeMillis()): Long? {
        val zone = ZoneId.systemDefault()
        val regular = a.nextRegularOccurrence(LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone))
            ?.atZone(zone)?.toInstant()?.toEpochMilli()
        val snooze = a.snoozeUntil?.takeIf { it > now }
        return listOfNotNull(regular, snooze).minOrNull()
    }

    /** Speichert den Wecker, plant ihn neu und liefert den nächsten Klingelzeitpunkt. */
    fun save(context: Context, alarm: Alarm, now: Long = System.currentTimeMillis()): Long? {
        val at = nextFireMillis(alarm, now)
        AlarmStore.upsert(alarm.copy(scheduledAt = at))
        arm(context, alarm.id, at)
        return at
    }

    fun delete(context: Context, id: Int) {
        alarmManager(context).cancel(firePendingIntent(context, id))
        AlarmStore.delete(id)
    }

    /**
     * Plant alle Wecker neu (App-Start, Neustart, Zeit- oder Zeitzonenwechsel). Wecker, deren Zeitpunkt
     * verstrichen ist, ohne dass sie geklingelt haben (Gerät war aus), werden dabei bereinigt.
     */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val updated = AlarmStore.alarms.value.mapNotNull { a ->
            val fixed = a.afterMissed(now)
            if (fixed !== a && a.scheduledAt != null && !a.temporary) {
                MissedNotifier.notify(context, a, a.scheduledAt, "Das Gerät war zu dieser Zeit aus")
            }
            if (fixed == null) alarmManager(context).cancel(firePendingIntent(context, a.id))
            fixed?.copy(scheduledAt = nextFireMillis(fixed, now))
        }
        AlarmStore.replaceAll(updated)
        updated.forEach { arm(context, it.id, it.scheduledAt) }
    }

    fun snooze(context: Context, id: Int) {
        val a = AlarmStore.get(id) ?: return
        val minutes = AlarmStore.snoozeMinutesFor(a)
        save(context, a.copy(snoozeUntil = System.currentTimeMillis() + minutes * 60_000L))
    }

    /**
     * Wird beim Auslösen aufgerufen. Liefert den Wecker, der jetzt klingeln soll, oder `null`,
     * wenn die Auslösung veraltet ist (Wecker inzwischen aus oder gelöscht).
     */
    fun onFired(context: Context, id: Int): Alarm? {
        val a = AlarmStore.get(id) ?: return null
        val now = System.currentTimeMillis()
        val isSnooze = a.snoozeUntil != null && abs(now - a.snoozeUntil) < 5 * 60_000L
        if (!isSnooze && !a.enabled) {
            save(context, a)
            return null
        }
        val updated = when {
            isSnooze -> a.copy(snoozeUntil = null)
            a.recurrence.type == RepeatType.ONCE -> a.copy(enabled = false, snoozeUntil = null)
            else -> a.copy(snoozeUntil = null)
        }
        // Eine Minute Vorlauf, damit ein minimal zu früh ausgelöster Wecker nicht gleich nochmal geplant wird.
        save(context, updated, now + 60_000L)
        return updated
    }

    /** Übergibt den Zeitpunkt dem System; `null` entfernt einen geplanten Wecker. */
    private fun arm(context: Context, id: Int, at: Long?) {
        val am = alarmManager(context)
        val pi = firePendingIntent(context, id)
        if (at == null) {
            am.cancel(pi)
            return
        }
        val show = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // setAlarmClock ersetzt einen vorhandenen Wecker mit demselben PendingIntent; er ist vom Doze- und
        // Stromsparmodus ausgenommen und erlaubt dem Empfänger, den Klingeldienst zu starten.
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun firePendingIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context, id,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
