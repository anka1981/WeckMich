package com.andik.wecker.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class RepeatType { ONCE, DAILY, WEEKLY, MONTHLY_DAY, MONTHLY_WEEKDAY, YEARLY }

/** Ordinal für „letzter Wochentag im Monat“. */
const val LAST_ORDINAL = -1

/** Tag im Monat für „letzter Tag des Monats“. */
const val LAST_DAY = 0

/**
 * Wiederholungsregel eines Weckers.
 *
 * [startDate] ist bei wiederholenden Regeln der Anker: Ab diesem Tag gilt die Regel, und von ihm aus
 * wird das Intervall gezählt („jede 2. Woche“ heißt: die Woche des Startdatums, dann jede zweite).
 * Bei [RepeatType.ONCE] ist es das gewünschte Datum oder `null` für „nächstes Mal, wenn die Uhrzeit
 * erreicht ist“.
 */
data class Recurrence(
    val type: RepeatType = RepeatType.ONCE,
    val interval: Int = 1,
    val startDate: LocalDate? = null,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int = 1,
    val ordinals: Set<Int> = setOf(1),
    val endDate: LocalDate? = null,
) {
    val isRepeating: Boolean get() = type != RepeatType.ONCE

    private val anchor: LocalDate get() = startDate ?: DEFAULT_ANCHOR

    /** Wochentage der Regel; ohne Auswahl der Wochentag des Startdatums. */
    val effectiveWeekdays: Set<DayOfWeek>
        get() = weekdays.ifEmpty { setOf(anchor.dayOfWeek) }

    fun occursOn(date: LocalDate): Boolean {
        if (type == RepeatType.ONCE) return startDate == null || date == startDate
        val start = anchor
        if (date.isBefore(start)) return false
        if (endDate != null && date.isAfter(endDate)) return false
        val n = interval.coerceAtLeast(1)
        return when (type) {
            RepeatType.DAILY -> ChronoUnit.DAYS.between(start, date) % n == 0L
            RepeatType.WEEKLY ->
                date.dayOfWeek in effectiveWeekdays &&
                    ChronoUnit.WEEKS.between(weekStart(start), weekStart(date)) % n == 0L
            RepeatType.MONTHLY_DAY ->
                monthsBetween(start, date) % n == 0L && date.dayOfMonth == dayInMonth(date)
            RepeatType.MONTHLY_WEEKDAY ->
                monthsBetween(start, date) % n == 0L &&
                    date.dayOfWeek in effectiveWeekdays &&
                    ordinalMatches(date)
            RepeatType.YEARLY ->
                (date.year - start.year) % n == 0 &&
                    date.month == start.month &&
                    date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
            RepeatType.ONCE -> true
        }
    }

    /** Erster Zeitpunkt mit Uhrzeit [time], der echt nach [after] liegt, oder `null`, wenn es keinen mehr gibt. */
    fun nextOccurrence(time: LocalTime, after: LocalDateTime): LocalDateTime? {
        if (type == RepeatType.ONCE) {
            startDate?.let { d ->
                val dt = d.atTime(time)
                return if (dt.isAfter(after)) dt else null
            }
            val today = after.toLocalDate().atTime(time)
            return if (today.isAfter(after)) today else today.plusDays(1)
        }
        var d = after.toLocalDate()
        if (d.isBefore(anchor)) d = anchor
        repeat(MAX_SEARCH_DAYS) {
            if (endDate != null && d.isAfter(endDate)) return null
            if (occursOn(d)) {
                val dt = d.atTime(time)
                if (dt.isAfter(after)) return dt
            }
            d = d.plusDays(1)
        }
        return null
    }

    fun nextOccurrences(time: LocalTime, after: LocalDateTime, count: Int): List<LocalDateTime> {
        val result = mutableListOf<LocalDateTime>()
        var from = after
        while (result.size < count) {
            val next = nextOccurrence(time, from) ?: break
            result += next
            if (type == RepeatType.ONCE) break
            from = next
        }
        return result
    }

    private fun dayInMonth(date: LocalDate): Int =
        if (dayOfMonth == LAST_DAY) date.lengthOfMonth() else minOf(dayOfMonth, date.lengthOfMonth())

    private fun ordinalMatches(date: LocalDate): Boolean {
        val ordinal = ordinalOf(date)
        val isLast = date.plusWeeks(1).month != date.month
        return ordinal in ordinals || (isLast && LAST_ORDINAL in ordinals)
    }

    companion object {
        /** Ein Montag – Anker für Regeln ohne Startdatum. */
        private val DEFAULT_ANCHOR: LocalDate = LocalDate.of(2024, 1, 1)
        private const val MAX_SEARCH_DAYS = 366 * 11

        /** Längste erlaubte Intervalle je Regelart. */
        fun maxInterval(type: RepeatType): Int = when (type) {
            RepeatType.DAILY -> 365
            RepeatType.WEEKLY -> 52
            RepeatType.MONTHLY_DAY, RepeatType.MONTHLY_WEEKDAY -> 24
            RepeatType.YEARLY -> 10
            RepeatType.ONCE -> 1
        }

        /** 1 = erster, 2 = zweiter … 5 = fünfter dieses Wochentags im Monat. */
        fun ordinalOf(date: LocalDate): Int = (date.dayOfMonth - 1) / 7 + 1

        fun weekStart(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        fun monthsBetween(a: LocalDate, b: LocalDate): Long =
            (b.year * 12L + b.monthValue) - (a.year * 12L + a.monthValue)
    }
}
