package com.andik.wecker.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    private val LONG = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val MONTHS = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez.")
    private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

    fun shortDay(d: DayOfWeek) = SHORT[d.value - 1]
    fun longDay(d: DayOfWeek) = LONG[d.value - 1]

    fun time(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)
    fun time(t: java.time.temporal.TemporalAccessor): String = TIME.format(t)

    /** „Do., 8. Okt.“ bzw. mit Jahr „Do., 8. Okt. 2026“ */
    fun date(d: LocalDate, withYear: Boolean = false): String {
        val base = "${shortDay(d.dayOfWeek)}., ${d.dayOfMonth}. ${MONTHS[d.monthValue - 1]}"
        return if (withYear) "$base ${d.year}" else base
    }

    /** „Heute“, „Morgen“ oder das Datum. */
    fun relativeDay(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        else -> date(d, withYear = d.year != today.year)
    }

    /** „in 2 Tagen 3 Std.“, „in 9 Std. 30 Min.“, „in 5 Min.“ */
    fun until(millis: Long, now: Long): String {
        val totalMin = ((millis - now + 59_999) / 60_000).coerceAtLeast(0)
        val days = totalMin / (60 * 24)
        val hours = (totalMin / 60) % 24
        val minutes = totalMin % 60
        return when {
            totalMin < 1 -> "in weniger als 1 Min."
            days > 0 -> "in $days ${if (days == 1L) "Tag" else "Tagen"}" + if (hours > 0) " $hours Std." else ""
            hours > 0 -> "in $hours Std." + if (minutes > 0) " $minutes Min." else ""
            else -> "in $minutes Min."
        }
    }

    fun ramp(seconds: Int): String = when {
        seconds == 0 -> "Aus – sofort volle Lautstärke"
        seconds < 60 -> "In $seconds Sekunden lauter werden"
        seconds == 60 -> "In 1 Minute lauter werden"
        else -> "In ${seconds / 60} Minuten lauter werden"
    }

    fun weekdayList(days: Set<DayOfWeek>): String = when {
        days.size == 7 -> "Jeden Tag"
        days == WORKDAYS -> "Mo.–Fr."
        else -> days.sorted().joinToString(", ") { shortDay(it) + "." }
    }

    private fun joinAnd(items: List<String>): String =
        if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " und " + items.last()

    fun ordinalWord(o: Int) = if (o == LAST_ORDINAL) "letzten" else "$o."

    fun unit(type: RepeatType, n: Int): String = when (type) {
        RepeatType.DAILY -> if (n == 1) "Tag" else "Tage"
        RepeatType.WEEKLY -> if (n == 1) "Woche" else "Wochen"
        RepeatType.MONTHLY_DAY, RepeatType.MONTHLY_WEEKDAY -> if (n == 1) "Monat" else "Monate"
        RepeatType.YEARLY -> if (n == 1) "Jahr" else "Jahre"
        RepeatType.ONCE -> ""
    }

    fun typeName(type: RepeatType): String = when (type) {
        RepeatType.ONCE -> "Einmalig"
        RepeatType.DAILY -> "Täglich / alle n Tage"
        RepeatType.WEEKLY -> "Wöchentlich / alle n Wochen"
        RepeatType.MONTHLY_DAY -> "Monatlich an einem Tag"
        RepeatType.MONTHLY_WEEKDAY -> "Monatlich an einem Wochentag"
        RepeatType.YEARLY -> "Jährlich"
    }

    fun typeHint(type: RepeatType): String = when (type) {
        RepeatType.ONCE -> "Klingelt ein Mal"
        RepeatType.DAILY -> "z. B. jeden 3. Tag"
        RepeatType.WEEKLY -> "z. B. jede 2. Woche montags"
        RepeatType.MONTHLY_DAY -> "z. B. am 15. oder am letzten Tag"
        RepeatType.MONTHLY_WEEKDAY -> "z. B. am 1. Dienstag im Monat"
        RepeatType.YEARLY -> "z. B. jedes Jahr am 8. Okt."
    }

    /** Kurze Beschreibung der Regel für die Weckerliste. */
    fun describe(r: Recurrence, today: LocalDate = LocalDate.now()): String {
        val n = r.interval.coerceAtLeast(1)
        val start = r.startDate ?: today
        val base = when (r.type) {
            RepeatType.ONCE -> r.startDate?.let { "Einmalig, ${relativeDay(it, today)}" } ?: "Einmalig"
            RepeatType.DAILY -> if (n == 1) "Täglich" else "Jeden $n. Tag"
            RepeatType.WEEKLY -> {
                val days = weekdayList(r.effectiveWeekdays)
                when {
                    n == 1 && r.effectiveWeekdays.size == 7 -> "Täglich"
                    n == 1 -> days
                    else -> "Jede $n. Woche: $days"
                }
            }
            RepeatType.MONTHLY_DAY -> {
                val day = if (r.dayOfMonth == LAST_DAY) "letzten Tag" else "${r.dayOfMonth}."
                if (n == 1) "Monatlich am $day" else "Jeden $n. Monat am $day"
            }
            RepeatType.MONTHLY_WEEKDAY -> {
                val ords = r.ordinals.sortedBy { if (it == LAST_ORDINAL) 99 else it }.map(::ordinalWord)
                val days = joinAnd(r.effectiveWeekdays.sorted().map(::longDay))
                if (n == 1) "Jeden ${joinAnd(ords)} $days im Monat"
                else "Jeden $n. Monat am ${joinAnd(ords)} $days"
            }
            RepeatType.YEARLY -> {
                val d = "${start.dayOfMonth}. ${MONTHS[start.monthValue - 1]}"
                if (n == 1) "Jährlich am $d" else "Jedes $n. Jahr am $d"
            }
        }
        return if (r.isRepeating && r.endDate != null) "$base · bis ${date(r.endDate)}" else base
    }
}
