package com.andik.wecker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class MissedAlarmTest {
    private val now = 1_800_000_000_000L
    private val once = Alarm(id = 1, hour = 7, minute = 0)
    private val weekly = once.copy(
        recurrence = Recurrence(RepeatType.WEEKLY, startDate = LocalDate.of(2026, 10, 1), weekdays = setOf(DayOfWeek.MONDAY)),
    )

    @Test fun notYetDue_unchanged() {
        val a = once.copy(scheduledAt = now + 3_600_000)
        assertSame(a, a.afterMissed(now))
    }

    @Test fun withinGrace_unchanged() {
        // Ein gerade klingelnder Wecker (Auslösung läuft noch) darf nicht als verpasst gelten.
        val a = once.copy(scheduledAt = now - 30_000)
        assertSame(a, a.afterMissed(now))
    }

    @Test fun missedOnce_isDisabled() {
        val fixed = once.copy(scheduledAt = now - 3_600_000).afterMissed(now)!!
        assertFalse(fixed.enabled)
        assertNull(fixed.scheduledAt)
    }

    @Test fun missedRepeating_staysOn() {
        val fixed = weekly.copy(scheduledAt = now - 3_600_000).afterMissed(now)!!
        assertTrue(fixed.enabled)
    }

    @Test fun missedSnooze_isCleared() {
        val fixed = weekly.copy(snoozeUntil = now - 3_600_000, scheduledAt = now - 3_600_000).afterMissed(now)!!
        assertNull(fixed.snoozeUntil)
        assertTrue(fixed.enabled)
    }

    @Test fun missedTestAlarm_isRemoved() {
        assertNull(once.copy(temporary = true, enabled = false, scheduledAt = now - 3_600_000).afterMissed(now))
    }

    @Test fun neverScheduled_unchanged() {
        assertEquals(once, once.afterMissed(now))
    }
}
