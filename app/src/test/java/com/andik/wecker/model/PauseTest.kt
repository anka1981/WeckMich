package com.andik.wecker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate
import java.time.LocalDateTime

class PauseTest {
    // Freitag, 9. Oktober 2026, 12:00
    private val now = LocalDateTime.of(2026, 10, 9, 12, 0)
    private val daily = Alarm(
        id = 1, hour = 7, minute = 0,
        recurrence = Recurrence(RepeatType.DAILY, startDate = LocalDate.of(2026, 10, 1)),
    )

    @Test fun `without pause the next day rings`() {
        assertEquals(LocalDateTime.of(2026, 10, 10, 7, 0), daily.nextRegularOccurrence(now))
    }

    @Test fun `paused alarm resumes on the first day after the pause`() {
        val paused = daily.copy(pausedUntil = LocalDate.of(2026, 10, 23))
        assertTrue(paused.isPaused(now.toLocalDate()))
        assertEquals(LocalDateTime.of(2026, 10, 23, 7, 0), paused.nextRegularOccurrence(now))
    }

    @Test fun `alarm at midnight rings on the resume day`() {
        val paused = daily.copy(hour = 0, minute = 0, pausedUntil = LocalDate.of(2026, 10, 23))
        assertEquals(LocalDateTime.of(2026, 10, 23, 0, 0), paused.nextRegularOccurrence(now))
    }

    @Test fun `pause skips to the next real occurrence of the rule`() {
        // Jede 2. Woche montags ab Mo 12.10.: 12.10., 26.10., 9.11. – Pause bis 27.10. → 9.11.
        val weekly = daily.copy(
            recurrence = Recurrence(RepeatType.WEEKLY, interval = 2, startDate = LocalDate.of(2026, 10, 12), weekdays = setOf(MONDAY)),
            pausedUntil = LocalDate.of(2026, 10, 27),
        )
        assertEquals(LocalDateTime.of(2026, 11, 9, 7, 0), weekly.nextRegularOccurrence(now))
    }

    @Test fun `expired pause no longer counts`() {
        val expired = daily.copy(pausedUntil = LocalDate.of(2026, 10, 9))
        assertFalse(expired.isPaused(now.toLocalDate()))
        assertEquals(LocalDateTime.of(2026, 10, 10, 7, 0), expired.nextRegularOccurrence(now))
    }

    @Test fun `one-time alarms ignore a pause`() {
        val once = daily.copy(recurrence = Recurrence(), pausedUntil = LocalDate.of(2026, 12, 1))
        assertFalse(once.isPaused(now.toLocalDate()))
        assertEquals(LocalDateTime.of(2026, 10, 10, 7, 0), once.nextRegularOccurrence(now))
    }
}

class SkippedOccurrencesTest {
    private val now = LocalDateTime.of(2026, 10, 9, 20, 0) // Fr., 20:00 – heutige 07:00 ist vorbei
    private val daily = Alarm(id = 1, hour = 7, minute = 0, recurrence = Recurrence(RepeatType.DAILY, startDate = LocalDate.of(2026, 10, 1)))

    @Test fun `one day from today in the evening skips nothing`() {
        assertEquals(emptyList<LocalDateTime>(), daily.occurrencesSkippedBy(LocalDate.of(2026, 10, 10), now))
    }

    @Test fun `two weeks of a monday alarm skip two mondays`() {
        val monday = daily.copy(hour = 19, recurrence = Recurrence(RepeatType.WEEKLY, startDate = LocalDate.of(2026, 10, 1), weekdays = setOf(MONDAY)))
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 12, 19, 0), LocalDateTime.of(2026, 10, 19, 19, 0)),
            monday.occurrencesSkippedBy(LocalDate.of(2026, 10, 23), now),
        )
    }

    @Test fun `three days of a daily alarm skip the next two mornings`() {
        assertEquals(2, daily.occurrencesSkippedBy(LocalDate.of(2026, 10, 12), now).size)
    }
}

class SkipCountTest {
    private val now = LocalDateTime.of(2026, 10, 9, 20, 0)
    private val monday = Alarm(id = 1, hour = 19, minute = 0,
        recurrence = Recurrence(RepeatType.WEEKLY, interval = 2, startDate = LocalDate.of(2026, 10, 12), weekdays = setOf(MONDAY)))

    @Test fun `skipping two occurrences resumes after the second one`() {
        // Jede 2. Woche Mo ab 12.10.: 12.10., 26.10., 9.11. → 2 auslassen → wieder ab 27.10., nächster 9.11.
        val resume = monday.resumeAfterSkipping(2, now)
        assertEquals(LocalDate.of(2026, 10, 27), resume)
        assertEquals(2, monday.occurrencesSkippedBy(resume, now).size)
        assertEquals(LocalDateTime.of(2026, 11, 9, 19, 0), monday.copy(pausedUntil = resume).nextRegularOccurrence(now))
    }
}

class DstTest {
    private val berlin = java.time.ZoneId.of("Europe/Berlin")
    private val daily0230 = Alarm(id = 1, hour = 2, minute = 30, recurrence = Recurrence(RepeatType.DAILY, startDate = LocalDate.of(2026, 10, 1)))

    @Test fun `in the repeated autumn hour nothing is planned in the past`() {
        // 25.10.2026: 02:30 MESZ = 00:30 UTC hat geklungen; jetzt 02:10 MEZ = 01:10 UTC.
        val now = java.time.Instant.parse("2026-10-25T01:10:00Z")
        val next = daily0230.nextRegularInstant(now, berlin)!!
        // Erlaubt: 02:30 MEZ (01:30 UTC) – liegt in der Zukunft. Nie ein Zeitpunkt <= jetzt.
        org.junit.Assert.assertTrue(next.isAfter(now))
        org.junit.Assert.assertEquals(java.time.Instant.parse("2026-10-25T01:30:00Z"), next)
    }

    @Test fun `the occurrence that already rang is not rung again in the repeated hour`() {
        val now = java.time.Instant.parse("2026-10-25T01:10:00Z") // 02:10 MEZ, 02:30 MESZ hat geklungen
        val next = daily0230.nextRegularInstant(now, berlin, lastRung = LocalDateTime.of(2026, 10, 25, 2, 30))!!
        org.junit.Assert.assertEquals(java.time.Instant.parse("2026-10-26T01:30:00Z"), next)
    }

    @Test fun `spring gap rings at 0330`() {
        val now = java.time.Instant.parse("2027-03-27T12:00:00Z")
        val next = daily0230.nextRegularInstant(now, berlin)!!
        org.junit.Assert.assertEquals(java.time.Instant.parse("2027-03-28T01:30:00Z"), next) // 03:30 MESZ
    }
}
