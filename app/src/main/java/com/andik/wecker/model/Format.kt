package com.andik.wecker.model

import com.andik.wecker.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/**
 * Alle sprachabhängigen Formatierungen. Die Satzbausteine stehen in den Ressourcen; hier wird nur
 * zusammengesetzt, damit jede Sprache ihre eigene Wortstellung und Grammatik bekommt.
 */
object Format {
    /** Wird über [init] gesetzt, in Tests mit einer eigenen Implementierung. */
    lateinit var texts: Texts

    /**
     * Idempotent; wird auch aus [com.andik.wecker.data.AlarmStore.init] aufgerufen, also auf jedem
     * Weg zum Klingeln – Android startet die App z. B. für ein Backup ohne eigene Application-Klasse.
     */
    @Synchronized
    fun init(context: android.content.Context) {
        if (!::texts.isInitialized) texts = AndroidTexts(context)
    }

    private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    private fun s(id: Int, vararg args: Any) = texts.str(id, *args)
    private fun pattern(id: Int) = DateTimeFormatter.ofPattern(s(id), texts.locale)

    /** Kurzform für Tageskreise und Auswahlchips („Mo“). */
    fun shortDay(d: DayOfWeek) = texts.array(R.array.weekdays_short)[d.value - 1]

    /** Abkürzung in Aufzählungen („Mo.“). */
    fun abbrDay(d: DayOfWeek) = texts.array(R.array.weekdays_abbr)[d.value - 1]
    fun longDay(d: DayOfWeek) = texts.array(R.array.weekdays_long)[d.value - 1]

    /** Uhrzeit nach der 12-/24-Stunden-Einstellung des Geräts. */
    fun time(hour: Int, minute: Int): String = time(java.time.LocalTime.of(hour, minute))
    fun time(t: TemporalAccessor): String =
        DateTimeFormatter.ofPattern(if (texts.is24Hour) "HH:mm" else "h:mm a", texts.locale).format(t)

    /** „Do., 8. Okt.“ bzw. mit Jahr „Do., 8. Okt. 2026“ – Muster je Sprache. */
    fun date(d: LocalDate, withYear: Boolean = false): String =
        pattern(if (withYear) R.string.fmt_date_year else R.string.fmt_date).format(d)

    /** „Heute“, „Morgen“ oder das Datum. */
    fun relativeDay(d: LocalDate, today: LocalDate): String = when (d) {
        today -> s(R.string.today)
        today.plusDays(1) -> s(R.string.tomorrow)
        else -> date(d, withYear = d.year != today.year)
    }

    /** „Heute“/„Morgen“ mitten im Satz klein („Einmalig, heute“); ein Datum bleibt, wie es ist. */
    private fun inSentence(word: String, d: LocalDate, today: LocalDate) =
        if (d == today || d == today.plusDays(1)) word.lowercase(texts.locale) else word

    /** „in 2 Tagen 3 Std.“, „in 9 Std. 30 Min.“, „in 5 Min.“ */
    fun until(millis: Long, now: Long): String {
        val totalMin = ((millis - now + 59_999) / 60_000).coerceAtLeast(0)
        val days = (totalMin / (60 * 24)).toInt()
        val hours = ((totalMin / 60) % 24).toInt()
        val minutes = (totalMin % 60).toInt()
        val parts = when {
            totalMin < 1 -> return s(R.string.until_less_than_minute)
            days > 0 -> listOfNotNull(texts.plural(R.plurals.until_days, days), hours.takeIf { it > 0 }?.let { texts.plural(R.plurals.hours_short, it) })
            hours > 0 -> listOfNotNull(texts.plural(R.plurals.hours_short, hours), minutes.takeIf { it > 0 }?.let { texts.plural(R.plurals.minutes_short, it) })
            else -> listOf(texts.plural(R.plurals.minutes_short, minutes))
        }
        return s(R.string.until, parts.joinToString(" "))
    }

    fun minutes(n: Int) = texts.plural(R.plurals.minutes, n)

    fun ramp(seconds: Int): String = when {
        seconds == 0 -> s(R.string.ramp_off)
        seconds < 60 || seconds % 60 != 0 -> texts.plural(R.plurals.ramp_seconds, seconds)
        else -> texts.plural(R.plurals.ramp_minutes, seconds / 60)
    }

    fun weekdayList(days: Set<DayOfWeek>): String = when {
        days.size == 7 -> s(R.string.every_day)
        days == WORKDAYS -> s(R.string.workdays)
        else -> days.sorted().joinToString(s(R.string.list_separator)) { abbrDay(it) }
    }

    /** „a, b und c“ */
    fun joinAnd(items: List<String>): String =
        if (items.size <= 1) items.joinToString()
        else s(R.string.list_and, items.dropLast(1).joinToString(s(R.string.list_separator)), items.last())

    /** Einheit ohne Zahl („Woche“/„Wochen“) für den Intervall-Stepper. */
    fun unit(type: RepeatType, n: Int): String = when (type) {
        RepeatType.DAILY -> texts.plural(R.plurals.unit_days, n)
        RepeatType.WEEKLY -> texts.plural(R.plurals.unit_weeks, n)
        RepeatType.MONTHLY_DAY, RepeatType.MONTHLY_WEEKDAY -> texts.plural(R.plurals.unit_months, n)
        RepeatType.YEARLY -> texts.plural(R.plurals.unit_years, n)
        RepeatType.ONCE -> ""
    }

    /** „Jede“/„Alle“ vor dem Intervall-Stepper – das Geschlecht hängt in vielen Sprachen an der Einheit. */
    fun intervalPrefix(type: RepeatType, n: Int): String = when (type) {
        RepeatType.DAILY -> texts.plural(R.plurals.interval_prefix_days, n)
        RepeatType.WEEKLY -> texts.plural(R.plurals.interval_prefix_weeks, n)
        RepeatType.MONTHLY_DAY, RepeatType.MONTHLY_WEEKDAY -> texts.plural(R.plurals.interval_prefix_months, n)
        RepeatType.YEARLY -> texts.plural(R.plurals.interval_prefix_years, n)
        RepeatType.ONCE -> ""
    }

    fun typeName(type: RepeatType): String = s(
        when (type) {
            RepeatType.ONCE -> R.string.type_once
            RepeatType.DAILY -> R.string.type_daily
            RepeatType.WEEKLY -> R.string.type_weekly
            RepeatType.MONTHLY_DAY -> R.string.type_monthly_day
            RepeatType.MONTHLY_WEEKDAY -> R.string.type_monthly_weekday
            RepeatType.YEARLY -> R.string.type_yearly
        },
    )

    fun typeHint(type: RepeatType): String = s(
        when (type) {
            RepeatType.ONCE -> R.string.hint_once
            RepeatType.DAILY -> R.string.hint_daily
            RepeatType.WEEKLY -> R.string.hint_weekly
            RepeatType.MONTHLY_DAY -> R.string.hint_monthly_day
            RepeatType.MONTHLY_WEEKDAY -> R.string.hint_monthly_weekday
            RepeatType.YEARLY -> R.string.hint_yearly
        },
    )

    /** Beschriftung der Auswahlchips „1.“ … „4.“, „Letzter“. */
    fun ordinalChip(o: Int): String = texts.array(R.array.ordinal_chips)[ordinalIndex(o)]

    private fun ordinalIndex(o: Int) = if (o == LAST_ORDINAL || o !in 1..4) 4 else o - 1

    /**
     * „1. und letzten Freitag“. Sprachen mit grammatischem Geschlecht (z. B. „la prima domenica“,
     * „il primo lunedì“) bekommen je Geschlecht eine eigene Gruppe mit passenden Ordinalzahlen.
     */
    private fun weekdayOrdinals(r: Recurrence): String {
        val ords = r.ordinals.sortedBy { if (it == LAST_ORDINAL) 99 else it }
        val genders = texts.array(R.array.weekday_gender)
        val groups = r.effectiveWeekdays.sorted().groupBy { genders[it.value - 1] }
        // Gruppen mit Komma statt „und“ – sonst verschwimmen sie: „il primo lunedì, la prima domenica“.
        return groups.map { (gender, days) ->
                val words = texts.array(if (gender == "f") R.array.ordinals_f else R.array.ordinals_m)
                s(R.string.weekday_group, joinAnd(ords.map { words[ordinalIndex(it)] }), joinAnd(days.map(::longDay)))
            }.joinToString(s(R.string.list_separator))
    }

    /** Kurze Beschreibung der Regel für die Weckerliste. */
    fun describe(r: Recurrence, today: LocalDate = LocalDate.now()): String {
        val n = r.interval.coerceAtLeast(1)
        val start = r.startDate ?: today
        val base = when (r.type) {
            RepeatType.ONCE -> r.startDate?.let { s(R.string.desc_once_on, inSentence(relativeDay(it, today), it, today)) } ?: s(R.string.desc_once)
            RepeatType.DAILY -> if (n == 1) s(R.string.desc_daily) else s(R.string.desc_every_n_days, n)
            RepeatType.WEEKLY -> {
                val days = weekdayList(r.effectiveWeekdays)
                when {
                    n == 1 && r.effectiveWeekdays.size == 7 -> s(R.string.desc_daily)
                    n == 1 -> days
                    else -> s(R.string.desc_every_n_weeks, n, if (r.effectiveWeekdays.size == 7) days.lowercase(texts.locale) else days)
                }
            }
            RepeatType.MONTHLY_DAY -> when {
                r.dayOfMonth == LAST_DAY && n == 1 -> s(R.string.desc_monthly_last_day)
                r.dayOfMonth == LAST_DAY -> s(R.string.desc_every_n_months_last_day, n)
                n == 1 -> s(R.string.desc_monthly_on_day, r.dayOfMonth)
                else -> s(R.string.desc_every_n_months_on_day, n, r.dayOfMonth)
            }
            RepeatType.MONTHLY_WEEKDAY ->
                if (n == 1) s(R.string.desc_monthly_weekday, weekdayOrdinals(r))
                else s(R.string.desc_every_n_months_weekday, n, weekdayOrdinals(r))
            RepeatType.YEARLY -> {
                val d = pattern(R.string.fmt_day_month).format(start)
                if (n == 1) s(R.string.desc_yearly, d) else s(R.string.desc_every_n_years, n, d)
            }
        }
        return if (r.isRepeating && r.endDate != null) s(R.string.desc_with_end, base, date(r.endDate)) else base
    }
}
