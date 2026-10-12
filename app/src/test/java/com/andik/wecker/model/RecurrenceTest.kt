package com.andik.wecker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurrenceTest {
    private val seven = LocalTime.of(7, 0)
    // Donnerstag, 8. Oktober 2026, 12:00
    private val now = LocalDateTime.of(2026, 10, 8, 12, 0)

    private fun dates(r: Recurrence, n: Int = 5, after: LocalDateTime = now) =
        r.nextOccurrences(seven, after, n).map { it.toLocalDate().toString() }

    @Test fun once_todayIfTimeAhead_elseTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 8, 13, 0), Recurrence().nextOccurrence(LocalTime.of(13, 0), now))
        assertEquals(LocalDateTime.of(2026, 10, 9, 7, 0), Recurrence().nextOccurrence(seven, now))
    }

    @Test fun once_fixedDate_inPastGivesNull() {
        assertNull(Recurrence(startDate = LocalDate.of(2026, 10, 1)).nextOccurrence(seven, now))
        assertEquals(LocalDateTime.of(2026, 12, 24, 7, 0),
            Recurrence(startDate = LocalDate.of(2026, 12, 24)).nextOccurrence(seven, now))
    }

    @Test fun everySecondWeekMonday_anchoredOnStartWeek() {
        // Start Mo 12.10. → 12.10., 26.10., 9.11., 23.11., 7.12.
        val r = Recurrence(RepeatType.WEEKLY, interval = 2, startDate = LocalDate.of(2026, 10, 12), weekdays = setOf(MONDAY))
        assertEquals(listOf("2026-10-12", "2026-10-26", "2026-11-09", "2026-11-23", "2026-12-07"), dates(r))
    }

    @Test fun everySecondWeek_startMidWeek_skipsEarlierDaysOfStartWeek() {
        // Start Do 8.10.: Montag dieser Woche (5.10.) liegt vor dem Start → erst wieder Mo 19.10.
        val r = Recurrence(RepeatType.WEEKLY, interval = 2, startDate = LocalDate.of(2026, 10, 8), weekdays = setOf(MONDAY, FRIDAY))
        assertEquals(listOf("2026-10-09", "2026-10-19", "2026-10-23", "2026-11-02"), dates(r, 4))
    }

    @Test fun weekly_acrossYearBoundary_keepsRhythm() {
        // Mo 21.12.2026 + 2 Wochen = Mo 4.1.2027 (ISO-Woche 1), dann 18.1.
        val r = Recurrence(RepeatType.WEEKLY, interval = 2, startDate = LocalDate.of(2026, 12, 21), weekdays = setOf(MONDAY))
        assertEquals(listOf("2026-12-21", "2027-01-04", "2027-01-18"), dates(r, 3))
    }

    @Test fun firstTuesdayOfMonth() {
        // 1. Dienstage: Nov 3., Dez 1., Jan 5., Feb 2., März 2. (Okt 6. liegt schon zurück)
        val r = Recurrence(RepeatType.MONTHLY_WEEKDAY, startDate = LocalDate.of(2026, 10, 8), weekdays = setOf(TUESDAY), ordinals = setOf(1))
        assertEquals(listOf("2026-11-03", "2026-12-01", "2027-01-05", "2027-02-02", "2027-03-02"), dates(r))
    }

    @Test fun lastFridayOfMonth() {
        // Okt 30., Nov 27., Dez 25., Jan 29.
        val r = Recurrence(RepeatType.MONTHLY_WEEKDAY, startDate = LocalDate.of(2026, 10, 8), weekdays = setOf(FRIDAY), ordinals = setOf(LAST_ORDINAL))
        assertEquals(listOf("2026-10-30", "2026-11-27", "2026-12-25", "2027-01-29"), dates(r, 4))
    }

    @Test fun firstAndThirdThursday_everySecondMonth() {
        // Anker Oktober: Okt 15. (3. Do; 1. Do war der 1.10. → vorbei), Dez 3., Dez 17., Feb 4., Feb 18.
        val r = Recurrence(RepeatType.MONTHLY_WEEKDAY, interval = 2, startDate = LocalDate.of(2026, 10, 1), weekdays = setOf(THURSDAY), ordinals = setOf(1, 3))
        assertEquals(listOf("2026-10-15", "2026-12-03", "2026-12-17", "2027-02-04", "2027-02-18"), dates(r))
    }

    @Test fun monthlyDay31_clampsToLastDay() {
        val r = Recurrence(RepeatType.MONTHLY_DAY, startDate = LocalDate.of(2026, 10, 8), dayOfMonth = 31)
        assertEquals(listOf("2026-10-31", "2026-11-30", "2026-12-31", "2027-01-31", "2027-02-28"), dates(r))
    }

    @Test fun monthlyLastDay_leapYear() {
        val r = Recurrence(RepeatType.MONTHLY_DAY, startDate = LocalDate.of(2028, 1, 1), dayOfMonth = LAST_DAY)
        assertEquals(listOf("2028-01-31", "2028-02-29", "2028-03-31"), dates(r, 3))
    }

    @Test fun everyThirdDay() {
        val r = Recurrence(RepeatType.DAILY, interval = 3, startDate = LocalDate.of(2026, 10, 7))
        assertEquals(listOf("2026-10-10", "2026-10-13", "2026-10-16"), dates(r, 3))
    }

    @Test fun yearly_feb29_fallsBackToFeb28() {
        val r = Recurrence(RepeatType.YEARLY, startDate = LocalDate.of(2028, 2, 29))
        assertEquals(listOf("2028-02-29", "2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29"),
            dates(r, after = LocalDateTime.of(2028, 1, 1, 0, 0)))
    }

    @Test fun endDate_stopsSeries() {
        val r = Recurrence(RepeatType.WEEKLY, startDate = LocalDate.of(2026, 10, 8), weekdays = setOf(MONDAY), endDate = LocalDate.of(2026, 10, 26))
        assertEquals(listOf("2026-10-12", "2026-10-19", "2026-10-26"), dates(r))
    }

    @Test fun sameDay_beforeTime_ringsToday() {
        val r = Recurrence(RepeatType.WEEKLY, startDate = LocalDate.of(2026, 10, 1), weekdays = setOf(THURSDAY))
        assertEquals(LocalDateTime.of(2026, 10, 8, 7, 0), r.nextOccurrence(seven, LocalDateTime.of(2026, 10, 8, 6, 59)))
        assertEquals(LocalDateTime.of(2026, 10, 15, 7, 0), r.nextOccurrence(seven, LocalDateTime.of(2026, 10, 8, 7, 0)))
    }

    @Test fun describe_texts() {
        Format.texts = XmlTexts("de")
        val today = LocalDate.of(2026, 10, 8)
        assertEquals("Jede 2. Woche: Mo.",
            Format.describe(Recurrence(RepeatType.WEEKLY, interval = 2, startDate = today, weekdays = setOf(MONDAY)), today))
        assertEquals("Jeden 1. Dienstag im Monat",
            Format.describe(Recurrence(RepeatType.MONTHLY_WEEKDAY, startDate = today, weekdays = setOf(TUESDAY), ordinals = setOf(1)), today))
        assertEquals("Jeden 1. und letzten Freitag im Monat",
            Format.describe(Recurrence(RepeatType.MONTHLY_WEEKDAY, startDate = today, weekdays = setOf(FRIDAY), ordinals = setOf(LAST_ORDINAL, 1)), today))
        assertEquals("Mo.–Fr.",
            Format.describe(Recurrence(RepeatType.WEEKLY, startDate = today, weekdays = setOf(MONDAY, TUESDAY, java.time.DayOfWeek.WEDNESDAY, THURSDAY, FRIDAY)), today))
        assertEquals("Monatlich am letzten Tag",
            Format.describe(Recurrence(RepeatType.MONTHLY_DAY, startDate = today, dayOfMonth = LAST_DAY), today))
    }

    @Test fun describe_texts_english() {
        Format.texts = XmlTexts(null)
        val today = LocalDate.of(2026, 10, 8)
        assertEquals("Every 2 weeks: Mon",
            Format.describe(Recurrence(RepeatType.WEEKLY, interval = 2, startDate = today, weekdays = setOf(MONDAY)), today))
        assertEquals("Every first and last Friday of the month",
            Format.describe(Recurrence(RepeatType.MONTHLY_WEEKDAY, startDate = today, weekdays = setOf(FRIDAY), ordinals = setOf(LAST_ORDINAL, 1)), today))
        assertEquals("Monthly on day 15 · until Thu, Dec 31",
            Format.describe(Recurrence(RepeatType.MONTHLY_DAY, startDate = today, dayOfMonth = 15, endDate = LocalDate.of(2026, 12, 31)), today))
        assertEquals("Yearly on Oct 8", Format.describe(Recurrence(RepeatType.YEARLY, startDate = today), today))
    }

    @Test fun dates_and_durations_german() {
        Format.texts = XmlTexts("de")
        assertEquals("Do., 8. Okt.", Format.date(LocalDate.of(2026, 10, 8)))
        assertEquals("in 2 Tagen 3 Std.", Format.until(0, -(2 * 24 + 3) * 3_600_000L))
        assertEquals("in 1 Tag", Format.until(0, -24 * 3_600_000L))
        assertEquals("07:05", Format.time(7, 5))
    }

    @Test fun describe_mixed_genders_italian_and_portuguese() {
        val today = LocalDate.of(2026, 10, 8)
        val r = Recurrence(RepeatType.MONTHLY_WEEKDAY, startDate = today, weekdays = setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY), ordinals = setOf(2))
        Format.texts = XmlTexts("it")
        assertEquals("Ogni mese: il secondo sabato, la seconda domenica", Format.describe(r, today))
        Format.texts = XmlTexts("pt")
        assertEquals("Todos os meses: a segunda sexta-feira",
            Format.describe(r.copy(weekdays = setOf(FRIDAY)), today))
        assertEquals("qui., 8 de out.", Format.date(today))
        Format.texts = XmlTexts("fr")
        assertEquals("Chaque premier et dernier vendredi du mois",
            Format.describe(r.copy(weekdays = setOf(FRIDAY), ordinals = setOf(1, LAST_ORDINAL)), today))
    }
}
