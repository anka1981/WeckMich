package com.andik.wecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andik.wecker.model.Alarm
import com.andik.wecker.model.Format
import java.time.LocalDate
import java.time.LocalDateTime

enum class PauseUnit(val one: String, val many: String, val max: Int) {
    DAYS("Tag", "Tage", 365),
    WEEKS("Woche", "Wochen", 52),
    MONTHS("Monat", "Monate", 24);

    fun resumeDate(from: LocalDate, amount: Int): LocalDate = when (this) {
        DAYS -> from.plusDays(amount.toLong())
        WEEKS -> from.plusWeeks(amount.toLong())
        MONTHS -> from.plusMonths(amount.toLong())
    }
}

/** „Pausiert – wieder ab Fr., 23. Okt.“ */
fun pauseText(alarm: Alarm, today: LocalDate): String? =
    alarm.pausedUntil?.takeIf { alarm.isPaused(today) }?.let {
        "Pausiert – wieder ab ${Format.date(it, withYear = it.year != today.year)}"
    }

/**
 * Setzt einen wiederholenden Wecker für eine Zeit aus: ab heute für n Tage/Wochen/Monate oder bis
 * einschließlich eines gewählten Datums. [onPause] bekommt den ersten Tag, an dem er wieder klingelt.
 */
@Composable
fun PauseDialog(
    alarm: Alarm,
    onDismiss: () -> Unit,
    onPause: (resume: LocalDate) -> Unit,
    onResume: () -> Unit,
) {
    val today = LocalDate.now()
    var amount by rememberSaveable { mutableIntStateOf(2) }
    var unit by rememberSaveable { mutableStateOf(PauseUnit.WEEKS) }
    var pickDate by remember { mutableStateOf(false) }

    val resume = unit.resumeDate(today, amount)
    val next = remember(alarm, resume) {
        alarm.copy(pausedUntil = resume, snoozeUntil = null).nextRegularOccurrence(LocalDateTime.now())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wecker aussetzen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pauseText(alarm, today)?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onResume, modifier = Modifier.padding(start = 0.dp)) { Text("Pause beenden") }
                }
                Text("Ab heute aussetzen für", style = MaterialTheme.typography.titleSmall)
                Stepper(amount, 1..unit.max, "$amount") { amount = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PauseUnit.entries.forEach { u ->
                        FilterChip(
                            selected = u == unit,
                            onClick = { unit = u; amount = amount.coerceAtMost(u.max) },
                            label = { Text(if (amount == 1) u.one else u.many) },
                        )
                    }
                }
                Text(
                    "Klingelt wieder ab ${Format.date(resume, withYear = resume.year != today.year)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    next?.let { "Nächster Termin: ${Format.date(it.toLocalDate(), withYear = it.year != today.year)}, ${Format.time(it)}" }
                        ?: "Danach gibt es keinen Termin mehr.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { pickDate = true }) { Text("Stattdessen bis einschließlich Datum …") }
            }
        },
        confirmButton = { TextButton(onClick = { onPause(resume) }) { Text("Aussetzen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )

    if (pickDate) {
        DatePickDialog(alarm.pausedUntil?.minusDays(1)?.takeIf { it.isAfter(today) } ?: today.plusWeeks(1),
            onDismiss = { pickDate = false }) { lastPausedDay ->
            pickDate = false
            // Ein Datum heute oder in der Vergangenheit würde nichts aussetzen.
            if (lastPausedDay.isAfter(today.minusDays(1))) onPause(lastPausedDay.plusDays(1))
        }
    }
}
