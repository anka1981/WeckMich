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
