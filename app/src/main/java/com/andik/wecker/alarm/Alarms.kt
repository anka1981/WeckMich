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
import java.time.temporal.ChronoUnit

/** Speichern und Planen von Weckern an einer Stelle, damit beides nie auseinanderläuft. */
object Alarms {
    const val EXTRA_ID = "alarmId"
    const val ACTION_FIRE = "com.andik.wecker.FIRE"

    /** Nächster Klingelzeitpunkt (Wiederholungsregel oder Schlummern, was früher ist) in Epoch-Millis. */
    fun nextFireMillis(a: Alarm, now: Long = System.currentTimeMillis()): Long? {
        // Zeitumstellungs-sicher und nie für den zuletzt geklungenen Termin (siehe nextRegularInstant).
        val regular = a.nextRegularInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault(), a.lastRung)
            ?.toEpochMilli()
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
            // Gerade fällig (z. B. Neustart um 06:00:20 für 06:00): unverändert scharf schalten – der
            // AlarmManager löst einen vergangenen Zeitpunkt sofort aus. Früher wurde ab "jetzt" neu
            // gerechnet und der fällige Termin still übersprungen.
            val due = a.scheduledAt
            if (due != null && due <= now && due > now - Alarm.MISSED_GRACE_MILLIS) return@mapNotNull a
            val fixed = a.afterMissed(now)
            if (fixed !== a && a.scheduledAt != null && !a.temporary) {
                MissedNotifier.notify(context, a, a.scheduledAt, "Nicht ausgelöst – Gerät aus oder Uhrzeit verstellt")
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
        // Schlummern ist fällig, sobald seine Zeit (fast) erreicht ist – ohne Obergrenze: eine verspätete
        // Zustellung klingelt trotzdem, statt als "veraltet" verworfen zu werden.
        val isSnooze = a.snoozeUntil != null && now >= a.snoozeUntil - EARLY_TOLERANCE_MILLIS
        if (!isSnooze && !a.enabled) {
            save(context, a)
            return null
        }
        val zone = ZoneId.systemDefault()
        val updated = when {
            isSnooze -> a.copy(snoozeUntil = null)
            else -> {
                // Den geklungenen Termin merken: er wird nie erneut geplant (Zeitumstellung, zu frühe
                // Zustellung, Uhr zurückgestellt).
                val rung = LocalDateTime.ofInstant(Instant.ofEpochMilli(a.scheduledAt ?: now), zone)
                    .truncatedTo(ChronoUnit.MINUTES)
                a.copy(
                    snoozeUntil = null,
                    lastRung = rung,
                    enabled = a.enabled && a.recurrence.type != RepeatType.ONCE,
                )
            }
        }
        save(context, updated, now)
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

    private const val EARLY_TOLERANCE_MILLIS = 60_000L

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun firePendingIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context, id,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
