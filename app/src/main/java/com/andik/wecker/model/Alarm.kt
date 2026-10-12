package com.andik.wecker.model

import com.andik.wecker.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
    /** Ausgesetzt: erster Tag, an dem der Wecker wieder klingeln darf; `null` = nicht pausiert.
     * Nur für wiederholende Wecker – Termine davor werden übersprungen. */
    val pausedUntil: LocalDate? = null,
    /** Zuletzt im Aussetzen-Dialog gewählte Dauer – der Dialog startet beim nächsten Mal damit. */
    val lastPauseAmount: Int = 2,
    val lastPauseUnit: PauseUnit = PauseUnit.WEEKS,
    /** Ortszeit des zuletzt geklungenen regulären Termins – wird nie erneut geplant. */
    val lastRung: LocalDateTime? = null,
    /** Zeitzone, in der [lastRung] galt; in einer anderen Zone wird er ignoriert. */
    val lastRungZone: String? = null,
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)

    /** Pausiert am Tag [today]? (Eine abgelaufene Pause zählt nicht mehr.) */
    fun isPaused(today: LocalDate): Boolean =
        recurrence.isRepeating && pausedUntil != null && pausedUntil.isAfter(today)

    /** Erster Tag nach dem [count]-ten kommenden Termin – eine Pause bis dahin lässt genau die
     * nächsten [count] Termine ausfallen. Gibt es weniger Termine, ab dem Tag nach dem letzten. */
    fun resumeAfterSkipping(count: Int, now: LocalDateTime): LocalDate {
        var from = now
        var last: LocalDateTime? = null
        repeat(count) {
            val next = recurrence.nextOccurrence(time, from) ?: return@repeat
            last = next
            from = next
        }
        return (last?.toLocalDate() ?: now.toLocalDate()).plusDays(1)
    }

    /** Termine der Regel nach [now], die vor [resume] liegen – also durch eine Pause bis
     * ausschließlich [resume] ausfallen würden. Höchstens [limit] Stück. */
    fun occurrencesSkippedBy(resume: LocalDate, now: LocalDateTime, limit: Int = 1000): List<LocalDateTime> {
        val result = mutableListOf<LocalDateTime>()
        var from = now
        while (result.size < limit) {
            val next = recurrence.nextOccurrence(time, from) ?: break
            if (!next.toLocalDate().isBefore(resume)) break
            result += next
            from = next
        }
        return result
    }

    /**
     * Nächster regulärer Klingelzeitpunkt als Instant, echt nach [now] und nie für einen Termin bis
     * einschließlich [lastRung] (Ortszeit des zuletzt geklungenen Termins).
     *
     * Bei der Zeitumstellung im Herbst gibt es die Uhrzeiten zwischen 02:00 und 03:00 zweimal;
     * `LocalDateTime.atZone` nimmt dann die erste (Sommerzeit). Hatte ein Wecker um 02:30 schon
     * geklingelt, lag dieser Zeitpunkt in der zweiten Runde in der Vergangenheit – der AlarmManager
     * löste sofort aus, die Neuplanung fand wieder denselben Zeitpunkt: eine Klingelschleife bis
     * 02:30 Winterzeit. [lastRung] verhindert das (und ein zweites Klingeln um 02:30 Winterzeit),
     * und ein Termin, dessen erste Variante schon vorbei ist, bekommt die spätere.
     */
    fun nextRegularInstant(
        now: Instant,
        zone: ZoneId,
        lastRung: LocalDateTime? = null,
        lastRungZone: String? = zone.id,
    ): Instant? {
        val nowLocal = LocalDateTime.ofInstant(now, zone)
        // lastRung zählt nur in derselben Zone und höchstens etwas über jetzt hinaus (doppelte Stunde
        // im Herbst): Nach einem Zeitzonenwechsel nach Westen oder einer zurückgestellten Uhr fielen
        // sonst Termine bis zum alten lastRung still aus.
        val relevant = lastRung?.takeIf {
            (lastRungZone == null || lastRungZone == zone.id) &&
                it.isAfter(nowLocal) && !it.isAfter(nowLocal.plusHours(LAST_RUNG_HORIZON_HOURS))
        }
        var after = relevant ?: nowLocal
        repeat(4) {
            val local = nextRegularOccurrence(after) ?: return null
            val zoned = local.atZone(zone)
            if (zoned.toInstant().isAfter(now)) return zoned.toInstant()
            val later = zoned.withLaterOffsetAtOverlap().toInstant()
            if (later.isAfter(now)) return later
            after = local
        }
        return null
    }

    /** Nächster regulärer Termin nach [now] – ohne Schlummern, mit Berücksichtigung einer Pause. */
    fun nextRegularOccurrence(now: LocalDateTime): LocalDateTime? {
        if (!enabled) return null
        val resume = pausedUntil?.takeIf { isPaused(now.toLocalDate()) }?.atStartOfDay()
        // nextOccurrence liefert Termine echt nach "after" – eine Sekunde vor Mitternacht des
        // Wiederbeginn-Tags lässt auch einen Wecker um 00:00 an diesem Tag zu.
        val after = if (resume != null && resume.isAfter(now)) resume.minusSeconds(1) else now
        return recurrence.nextOccurrence(time, after)
    }

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
        private const val LAST_RUNG_HORIZON_HOURS = 2L

        /** Ein Wecker gilt erst als verpasst, wenn sein Zeitpunkt länger als das zurückliegt. */
        const val MISSED_GRACE_MILLIS = 60_000L
    }
}

/** [label] ist die Plural-Ressource für die Einheit („Woche“/„Wochen“). */
enum class PauseUnit(val label: Int, val max: Int) {
    DAYS(R.plurals.unit_days, 365),
    WEEKS(R.plurals.unit_weeks, 52),
    MONTHS(R.plurals.unit_months, 24),
    /** Die nächsten n Termine auslassen – kein Kalenderzeitraum, siehe [Alarm.resumeAfterSkipping]. */
    OCCURRENCES(R.plurals.unit_occurrences, 99);

    /** Wiederbeginn für einen Kalenderzeitraum ab [from]; nicht für [OCCURRENCES]. */
    fun plus(from: LocalDate, amount: Int): LocalDate = when (this) {
        DAYS -> from.plusDays(amount.toLong())
        WEEKS -> from.plusWeeks(amount.toLong())
        MONTHS -> from.plusMonths(amount.toLong())
        OCCURRENCES -> error("OCCURRENCES hat keinen festen Zeitraum")
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
    /** Display nach unten drehen schlummert einen klingelnden Wecker. */
    val flipToSnooze: Boolean = true,
)
