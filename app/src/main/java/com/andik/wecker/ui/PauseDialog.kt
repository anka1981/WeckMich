package com.andik.wecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.andik.wecker.model.PauseUnit
import java.time.LocalDate
import java.time.LocalDateTime

private fun day(d: LocalDate, today: LocalDate) = Format.date(d, withYear = d.year != today.year)

/** „Pausiert bis einschließlich Di., 8. Dez.“ – der letzte ausgesetzte Tag, nicht der Tag danach:
 * „wieder ab Mi., 9. Dez.“ las sich, als klinge der Wecker an diesem Mittwoch. */
fun pauseText(alarm: Alarm, today: LocalDate): String? =
    alarm.pausedUntil?.takeIf { alarm.isPaused(today) }?.let {
        "Pausiert bis einschließlich ${day(it.minusDays(1), today)}"
    }

/** „Es entfallen 2 Termine: Mo., 12. Okt. · Mo., 19. Okt.“ – bei vielen nur die ersten. */
private fun skippedText(skipped: List<LocalDateTime>, today: LocalDate): String {
    if (skipped.isEmpty()) return "Es entfällt kein Termin."
    val shown = skipped.take(4).joinToString(" · ") { day(it.toLocalDate(), today) }
    val rest = skipped.size - 4
    val more = when { rest <= 0 -> ""; rest == 1 -> " und 1 weiterer"; else -> " und $rest weitere" }
    return if (skipped.size == 1) "Es entfällt 1 Termin: $shown" else "Es entfallen ${skipped.size} Termine: $shown$more"
}

private fun nextText(next: LocalDateTime?, today: LocalDate): String =
    next?.let { "Nächster Termin: ${day(it.toLocalDate(), today)}, ${Format.time(it)}" }
        ?: "Danach gibt es keinen Termin mehr."

/**
 * Setzt einen wiederholenden Wecker für eine Zeit aus: ab heute für n Tage/Wochen/Monate oder bis
 * einschließlich eines gewählten Datums. [onPause] bekommt den ersten Tag, an dem er wieder klingelt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseDialog(
    alarm: Alarm,
    onDismiss: () -> Unit,
    onPause: (resume: LocalDate, amount: Int, unit: PauseUnit) -> Unit,
    onResume: () -> Unit,
) {
    val today = LocalDate.now()
    // Startet mit der zuletzt für diesen Wecker gewählten Dauer.
    var amount by rememberSaveable { mutableIntStateOf(alarm.lastPauseAmount) }
    var unit by rememberSaveable { mutableStateOf(alarm.lastPauseUnit) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    // Gezählt wird ab heute, wie man „2 Wochen Pause“ versteht. Damit klar ist, was das bewirkt
    // (z. B. abends nach der Weckzeit: „1 Tag“ lässt nichts ausfallen), listet der Dialog die
    // ausfallenden Termine auf.
    // Wie nach dem Bestätigen: Aussetzen schaltet den Wecker ein – die Vorschau rechnet entsprechend.
    val armed = remember(alarm) { alarm.copy(enabled = true, snoozeUntil = null) }
    // „Termine“ höchstens so viele, wie es noch gibt (z. B. bei einem Enddatum).
    val remainingCount = remember(armed) { armed.copy(pausedUntil = null).occurrencesSkippedBy(java.time.LocalDate.MAX, LocalDateTime.now(), limit = PauseUnit.OCCURRENCES.max).size }
    val maxAmount = if (unit == PauseUnit.OCCURRENCES) remainingCount.coerceAtLeast(1) else unit.max
    if (amount > maxAmount) amount = maxAmount
    val resume = remember(alarm, unit, amount) {
        if (unit == PauseUnit.OCCURRENCES) armed.resumeAfterSkipping(amount, LocalDateTime.now())
        else unit.plus(today, amount)
    }
    val skipped = remember(alarm, resume) { armed.occurrencesSkippedBy(resume, LocalDateTime.now()) }
    val next = remember(alarm, resume) {
        armed.copy(pausedUntil = resume).nextRegularOccurrence(LocalDateTime.now())
    }
    val currentNext = remember(armed) { armed.nextRegularOccurrence(LocalDateTime.now()) }
    val paused = alarm.isPaused(today)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wecker aussetzen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (paused) {
                    Text("Aktuell", style = MaterialTheme.typography.titleSmall)
                    Text(pauseText(alarm, today).orEmpty(), color = MaterialTheme.colorScheme.primary)
                    Text(nextText(currentNext, today), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onResume, modifier = Modifier.padding(start = 0.dp)) { Text("Pause beenden") }
                    HorizontalDivider()
                }
                Text(
                    ((if (paused) "Neue Pause (ersetzt die aktuelle) – " else "") +
                        if (unit == PauseUnit.OCCURRENCES) "die nächsten Termine auslassen:" else "ab heute aussetzen für")
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                )
                Stepper(amount, 1..maxAmount, "$amount", what = "Anzahl") { amount = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PauseUnit.entries.forEach { u ->
                        FilterChip(
                            selected = u == unit,
                            onClick = { unit = u },
                            label = { Text(if (amount == 1) u.one else u.many) },
                        )
                    }
                }
                Text(
                    "Ausgesetzt bis einschließlich ${day(resume.minusDays(1), today)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(skippedText(skipped, today), style = MaterialTheme.typography.bodyMedium)
                Text(
                    nextText(next, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { pickDate = true }) { Text("Stattdessen bis einschließlich Datum …") }
            }
        },
        confirmButton = { TextButton(onClick = { onPause(resume, amount, unit) }) { Text(if (paused) "Pause ersetzen" else "Aussetzen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )

    if (pickDate) {
        DatePickDialog(
            alarm.pausedUntil?.minusDays(1)?.takeIf { it.isAfter(today) } ?: today.plusWeeks(1),
            onDismiss = { pickDate = false },
            minDate = today,
        ) { lastPausedDay ->
            pickDate = false
            // Ein Datum in der Vergangenheit setzt nichts aus (der DatePicker lässt es nicht zu).
            if (!lastPausedDay.isBefore(today)) onPause(lastPausedDay.plusDays(1), amount, unit)
        }
    }
}
