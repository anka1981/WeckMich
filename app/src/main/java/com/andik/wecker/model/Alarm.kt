package com.andik.wecker.model

import java.time.LocalTime

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "",
    val recurrence: Recurrence = Recurrence(),
    /** Weckton-URI; `null` = Standard-Weckton des Systems. */
    val ringtone: String? = null,
    /** Anzeigename des Wecktons. */
    val ringtoneName: String? = null,
    val vibrate: Boolean = true,
    /** Schlummerdauer dieses Weckers in Minuten; `null` = Standard aus den Einstellungen. */
    val snoozeMinutes: Int? = null,
    /** Sekunden bis zur vollen Lautstärke für diesen Wecker; `null` = Standard, 0 = sofort laut. */
    val rampSeconds: Int? = null,
    /** Epoch-Millis, bis zu denen der Wecker schlummert; `null` = kein Schlummern aktiv. */
    val snoozeUntil: Long? = null,
    /** Probewecker: wird nach dem Klingeln automatisch gelöscht. */
    val temporary: Boolean = false,
    /** Zuletzt beim System geplanter Klingelzeitpunkt (Epoch-Millis), um verpasste Wecker zu erkennen. */
    val scheduledAt: Long? = null,
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)

    /**
     * Zustand nach einem verpassten Klingeln (Gerät war aus): `this`, wenn nichts verpasst wurde,
     * sonst ein bereinigter Wecker – einmalige Wecker gehen aus, Schlummern verfällt – oder `null`,
     * wenn ein Probewecker verpasst wurde und gelöscht werden kann.
     */
    fun afterMissed(now: Long): Alarm? {
        val at = scheduledAt ?: return this
        if (at > now - MISSED_GRACE_MILLIS) return this
        if (temporary) return null
        return copy(
            enabled = enabled && recurrence.isRepeating,
            snoozeUntil = null,
            scheduledAt = null,
        )
    }

    companion object {
        /** Ein Wecker gilt erst als verpasst, wenn sein Zeitpunkt länger als das zurückliegt. */
        const val MISSED_GRACE_MILLIS = 60_000L
    }
}

val SNOOZE_CHOICES = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60)
val RAMP_CHOICES = listOf(0, 15, 30, 60, 120, 300)

data class Settings(
    val snoozeMinutes: Int = 10,
    /** Nach so vielen Minuten verstummt ein klingelnder Wecker von selbst; 0 = nie. */
    val ringMinutes: Int = 10,
    /** Sekunden, über die die Lautstärke ansteigt; 0 = sofort volle Lautstärke. */
    val rampSeconds: Int = 0,
)
