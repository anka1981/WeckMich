package com.andik.wecker.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andik.wecker.model.Format
import com.andik.wecker.model.LAST_DAY
import com.andik.wecker.model.LAST_ORDINAL
import com.andik.wecker.model.Recurrence
import com.andik.wecker.model.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Passt eine Regel beim Wechsel der Art an, damit sie gleich etwas Sinnvolles beschreibt. */
fun Recurrence.withType(type: RepeatType, today: LocalDate = LocalDate.now()): Recurrence {
    if (type == this.type) return this
    if (type == RepeatType.ONCE) return Recurrence()
    val start = startDate ?: today
    val ordinal = Recurrence.ordinalOf(start).let { if (it > 4) LAST_ORDINAL else it }
    return copy(
        type = type,
        startDate = start,
        interval = interval.coerceIn(1, Recurrence.maxInterval(type)),
        weekdays = weekdays.ifEmpty { setOf(start.dayOfWeek) },
        dayOfMonth = if (this.type == RepeatType.MONTHLY_DAY) dayOfMonth else start.dayOfMonth,
        ordinals = if (this.type == RepeatType.MONTHLY_WEEKDAY) ordinals else setOf(ordinal),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurrenceDialog(
    initial: Recurrence,
    time: LocalTime,
    pausedUntil: LocalDate? = null,
    onDismiss: () -> Unit,
    onConfirm: (Recurrence) -> Unit,
) {
    var r by rememberSaveable(stateSaver = RecurrenceSaver) { mutableStateOf(initial) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Wiederholung") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Abbrechen") } },
                    actions = { TextButton(onClick = { onConfirm(r) }) { Text("Fertig") } },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                RepeatType.entries.forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = r.type == type, role = Role.RadioButton) { r = r.withType(type, today) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = r.type == type, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(Format.typeName(type), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                Format.typeHint(type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                val section = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)

                if (r.isRepeating) {
                    Column(section) {
                        SectionTitle("Intervall")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (r.interval == 1) "Jede(n)" else "Alle", Modifier.padding(end = 8.dp))
                            Stepper(r.interval, 1..Recurrence.maxInterval(r.type), "${r.interval}") {
                                r = r.copy(interval = it)
                            }
                            Text(Format.unit(r.type, r.interval), Modifier.padding(start = 8.dp))
                        }
                    }
                }

                when (r.type) {
                    RepeatType.WEEKLY -> Column(section) {
                        SectionTitle("An diesen Tagen")
                        WeekdayChips(r.weekdays) { r = r.copy(weekdays = it) }
                    }
                    RepeatType.MONTHLY_DAY -> Column(section) {
                        SectionTitle("Am Tag")
                        val last = r.dayOfMonth == LAST_DAY
                        if (!last) {
                            Stepper(r.dayOfMonth, 1..31, "${r.dayOfMonth}.") { r = r.copy(dayOfMonth = it) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = last, onCheckedChange = {
                                r = r.copy(dayOfMonth = if (it) LAST_DAY else (r.startDate ?: today).dayOfMonth)
                            })
                            Text("Letzter Tag des Monats")
                        }
                        if (!last && r.dayOfMonth > 28) {
                            Hint("In kürzeren Monaten klingelt der Wecker am letzten Tag des Monats.")
                        }
                    }
                    RepeatType.MONTHLY_WEEKDAY -> Column(section) {
                        SectionTitle("Welcher im Monat")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 4, LAST_ORDINAL).forEach { o ->
                                FilterChip(
                                    selected = o in r.ordinals,
                                    onClick = {
                                        val s = if (o in r.ordinals) r.ordinals - o else r.ordinals + o
                                        if (s.isNotEmpty()) r = r.copy(ordinals = s)
                                    },
                                    label = { Text(if (o == LAST_ORDINAL) "Letzter" else "$o.") },
                                )
                            }
                        }
                        SectionTitle("Wochentag")
                        WeekdayChips(r.weekdays) { r = r.copy(weekdays = it) }
                    }
                    RepeatType.YEARLY -> Column(section) {
                        Hint("Der Wecker klingelt jeweils am Tag und Monat des Startdatums.")
                    }
                    else -> Unit
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                if (r.isRepeating) {
                    ListItem(
                        headlineContent = { Text("Beginnt am") },
                        supportingContent = {
                            Text(Format.date(r.startDate ?: today, withYear = true) +
                                if (r.interval > 1) " – von hier aus wird das Intervall gezählt" else "")
                        },
                        leadingContent = { Icon(Icons.Filled.Event, null) },
                        modifier = Modifier.clickable { pickStart = true },
                    )
                    ListItem(
                        headlineContent = { Text("Endet") },
                        supportingContent = { Text(r.endDate?.let { "Nach dem ${Format.date(it, withYear = true)}" } ?: "Nie") },
                        leadingContent = { Icon(Icons.Filled.EventBusy, null) },
                        trailingContent = r.endDate?.let {
                            { IconButton(onClick = { r = r.copy(endDate = null) }) { Icon(Icons.Filled.Clear, "Kein Ende") } }
                        },
                        modifier = Modifier.clickable { pickEnd = true },
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("Datum") },
                        supportingContent = {
                            Text(r.startDate?.let { Format.date(it, withYear = true) } ?: "Nächstes Mal, wenn die Uhrzeit erreicht ist")
                        },
                        leadingContent = { Icon(Icons.Filled.Event, null) },
                        trailingContent = r.startDate?.let {
                            { IconButton(onClick = { r = r.copy(startDate = null) }) { Icon(Icons.Filled.Clear, "Kein Datum") } }
                        },
                        modifier = Modifier.clickable { pickStart = true },
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Column(section) {
                    SectionTitle("Nächste Termine")
                    // Wie die Planung: Termine während einer Pause fallen aus.
                    val upcoming = remember(r, time, pausedUntil) {
                        val now = LocalDateTime.now()
                        val resume = pausedUntil?.takeIf { r.isRepeating && it.isAfter(now.toLocalDate()) }?.atStartOfDay()?.minusSeconds(1)
                        r.nextOccurrences(time, if (resume != null && resume.isAfter(now)) resume else now, 8)
                    }
                    if (upcoming.isEmpty()) {
                        Text("Keine weiteren Termine", color = MaterialTheme.colorScheme.error)
                    }
                    upcoming.forEach {
                        Text(
                            "${Format.date(it.toLocalDate(), withYear = it.year != today.year)} · ${Format.time(it)}",
                            Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }

    if (pickStart) {
        DatePickDialog(r.startDate ?: today, onDismiss = { pickStart = false }) {
            pickStart = false
            // Ein Enddatum vor dem neuen Start wird auf den Start gezogen – früher wurde es still zu
            // „Nie“, und der Wecker hätte unbegrenzt weitergeklingelt.
            r = r.copy(startDate = it, endDate = r.endDate?.let { e -> if (e.isBefore(it)) it else e })
        }
    }
    if (pickEnd) {
        DatePickDialog(
            r.endDate ?: (r.startDate ?: today).plusMonths(3),
            onDismiss = { pickEnd = false },
            minDate = r.startDate ?: today,
        ) {
            pickEnd = false
            r = r.copy(endDate = it)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = d in selected,
                onClick = {
                    val s = if (d in selected) selected - d else selected + d
                    if (s.isNotEmpty()) onChange(s)
                },
                label = { Text(Format.shortDay(d)) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** Speichert eine [Recurrence] über Drehen und Prozess-Tod hinweg (als einfache Liste). */
private val RecurrenceSaver = listSaver<Recurrence, Any?>(
    save = { r ->
        listOf(
            r.type.name, r.interval, r.startDate?.toString(), r.endDate?.toString(),
            r.weekdays.map { it.value }.joinToString(","), r.dayOfMonth, r.ordinals.joinToString(","),
        )
    },
    restore = { l ->
        Recurrence(
            type = RepeatType.valueOf(l[0] as String),
            interval = l[1] as Int,
            startDate = (l[2] as String?)?.let(LocalDate::parse),
            endDate = (l[3] as String?)?.let(LocalDate::parse),
            weekdays = (l[4] as String).split(",").filter { it.isNotBlank() }.map { DayOfWeek.of(it.toInt()) }.toSet(),
            dayOfMonth = l[5] as Int,
            ordinals = (l[6] as String).split(",").filter { it.isNotBlank() }.map { it.toInt() }.toSet(),
        )
    },
)
