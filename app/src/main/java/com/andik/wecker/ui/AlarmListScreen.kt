package com.andik.wecker.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import com.andik.wecker.alarm.RingingState
import com.andik.wecker.alarm.AlarmService
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.andik.wecker.alarm.Alarms
import com.andik.wecker.alarm.Issue
import com.andik.wecker.alarm.Reliability
import com.andik.wecker.data.AlarmStore
import com.andik.wecker.model.Alarm
import com.andik.wecker.model.Format
import com.andik.wecker.model.Recurrence
import com.andik.wecker.model.RepeatType
import com.andik.wecker.model.RAMP_CHOICES
import com.andik.wecker.model.SNOOZE_CHOICES
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Speichert und meldet wie die Android-Uhr, wann der Wecker klingelt. */
fun saveAndAnnounce(context: Context, alarm: Alarm) {
    val at = Alarms.save(context, alarm)
    val msg = when {
        !alarm.enabled -> return
        at == null -> "Für diese Regel gibt es keinen weiteren Termin"
        else -> "Wecker klingelt ${Format.until(at, System.currentTimeMillis())}"
    }
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

/** Tippen auf einen Wochentag in der Karte – wie bei der Android-Uhr. */
fun Recurrence.toggleDay(d: DayOfWeek, today: LocalDate = LocalDate.now()): Recurrence = when (type) {
    RepeatType.WEEKLY, RepeatType.MONTHLY_WEEKDAY -> {
        val s = if (d in effectiveWeekdays) effectiveWeekdays - d else effectiveWeekdays + d
        when {
            s.isNotEmpty() -> copy(weekdays = s)
            type == RepeatType.WEEKLY -> Recurrence()
            else -> this
        }
    }
    // Beim Umwandeln in „wöchentlich“ bleiben Start und Ende erhalten – vorher ging ein Enddatum
    // durch einen Tipp auf einen Wochentag still verloren.
    RepeatType.DAILY ->
        if (interval == 1) Recurrence(RepeatType.WEEKLY, startDate = startDate ?: today, weekdays = DayOfWeek.entries.toSet() - d, endDate = endDate)
        else Recurrence(RepeatType.WEEKLY, startDate = startDate ?: today, weekdays = setOf(d), endDate = endDate)
    RepeatType.ONCE -> Recurrence(RepeatType.WEEKLY, startDate = today, weekdays = setOf(d))
    else -> Recurrence(RepeatType.WEEKLY, startDate = startDate ?: today, weekdays = setOf(d), endDate = endDate)
}

/** Ein einmaliger Wecker mit festem, schon vergangenem Datum würde beim Wiedereinschalten nie
 * klingeln – das Datum wird dann verworfen (nächstes Vorkommen der Uhrzeit, wie bei der Android-Uhr). */
fun Alarm.rearmed(): Alarm {
    val r = recurrence
    val stale = r.type == RepeatType.ONCE && r.startDate != null &&
        r.nextOccurrence(time, java.time.LocalDateTime.now()) == null
    return if (stale) copy(recurrence = r.copy(startDate = null)) else this
}

fun Recurrence.selectedDays(): Set<DayOfWeek> = when (type) {
    RepeatType.WEEKLY, RepeatType.MONTHLY_WEEKDAY -> effectiveWeekdays
    RepeatType.DAILY -> if (interval == 1) DayOfWeek.entries.toSet() else emptySet()
    else -> emptySet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val alarms by AlarmStore.alarms.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var issues by remember { mutableStateOf(emptyList<Issue>()) }
    var expandedId by rememberSaveable { mutableIntStateOf(-1) }
    var createNew by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { issues = Reliability.issues(context) }
    val writeFailed by AlarmStore.writeFailed.collectAsState()
    LaunchedEffect(writeFailed) { issues = Reliability.issues(context) }
    val ringing by RingingState.ringing.collectAsState()
    // „in X Std.“ nur aktualisieren, solange die App sichtbar ist – und dann genau zum Minutenwechsel.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000 + 50)
            }
        }
    }
    LaunchedEffect(alarms) { now = System.currentTimeMillis() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wecker") },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Einstellungen") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createNew = true }) { Icon(Icons.Filled.Add, "Wecker hinzufügen") }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Klingelt gerade ein Wecker, lässt er sich hier immer beenden – auch wenn Benachrichtigungen
            // oder die Vollbildanzeige fehlen und sonst kein Knopf erreichbar wäre.
            ringing?.let { r -> item(key = "ringing") { RingingBanner(r) } }
            items(issues.filter { it.critical }, key = { "issue-" + it.title }) { IssueCard(it) }
            if (alarms.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                        Text("Noch keine Wecker", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    now = now,
                    expanded = expandedId == alarm.id,
                    onToggleExpand = { expandedId = if (expandedId == alarm.id) -1 else alarm.id },
                    onDelete = {
                        Alarms.delete(context, alarm.id)
                        if (expandedId == alarm.id) expandedId = -1
                    },
                )
            }
        }
    }

    if (createNew) {
        val t = java.time.LocalTime.now().plusHours(1)
        TimePickDialog(t.hour, 0, onDismiss = { createNew = false }) { h, m ->
            createNew = false
            val alarm = Alarm(id = AlarmStore.nextId(), hour = h, minute = m)
            saveAndAnnounce(context, alarm)
            expandedId = alarm.id
        }
    }
}

@Composable
private fun RingingBanner(alarm: Alarm) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Klingelt: ${alarm.label.ifBlank { "Wecker" }} ${Format.time(alarm.hour, alarm.minute)}",
                Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { AlarmService.send(context, AlarmService.ACTION_SNOOZE) }) { Text("Schlummern") }
            TextButton(onClick = { AlarmService.send(context, AlarmService.ACTION_DISMISS) }) { Text("Beenden") }
        }
    }
}

@Composable
fun IssueCard(issue: Issue) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (issue.critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WarningAmber, null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(issue.title, fontWeight = FontWeight.SemiBold)
                Text(issue.text, style = MaterialTheme.typography.bodySmall)
            }
            if (issue.intent != null && issue.action != null) {
                TextButton(onClick = {
                    runCatching { context.startActivity(issue.intent) }.onFailure {
                        Toast.makeText(context, "Einstellung konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
                    }
                }) { Text(issue.action) }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: Long,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var editTime by rememberSaveable { mutableStateOf(false) }
    var editRecurrence by rememberSaveable { mutableStateOf(false) }
    var editLabel by rememberSaveable { mutableStateOf(false) }
    val active = alarm.enabled || alarm.snoozeUntil?.let { it > now } == true
    val today = LocalDate.now()

    var editSound by rememberSaveable { mutableStateOf(false) }
    var editSnooze by rememberSaveable { mutableStateOf(false) }
    var editRamp by rememberSaveable { mutableStateOf(false) }
    var editPause by rememberSaveable { mutableStateOf(false) }
    val defaults by AlarmStore.settings.collectAsState()

    Card(
        modifier = Modifier.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.clickable(onClick = onToggleExpand).padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (alarm.label.isNotBlank()) {
                Text(alarm.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Format.time(alarm.hour, alarm.minute),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { editTime = true },
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    modifier = Modifier.semantics { contentDescription = "Wecker ${Format.time(alarm.hour, alarm.minute)} ein" },
                    checked = alarm.enabled, onCheckedChange = {
                    saveAndAnnounce(context, alarm.copy(enabled = it, snoozeUntil = null, temporary = false).let { a -> if (it) a.rearmed() else a })
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Format.describe(alarm.recurrence, today), style = MaterialTheme.typography.bodyMedium)
                    pauseText(alarm, today)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    NextLine(alarm, now)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Zuklappen" else "Aufklappen")
            }

            if (expanded) {
                Spacer(Modifier.size(12.dp))
                DayCircles(alarm.recurrence.selectedDays()) { d ->
                    saveAndAnnounce(context, alarm.copy(enabled = true, recurrence = alarm.recurrence.toggleDay(d, today)))
                }
                Spacer(Modifier.size(4.dp))
                DetailRow(Icons.Filled.Repeat, "Wiederholung", Format.describe(alarm.recurrence, today)) { editRecurrence = true }
                if (alarm.recurrence.isRepeating) {
                    DetailRow(Icons.Filled.PauseCircle, "Aussetzen", pauseText(alarm, today) ?: "Für Tage, Wochen oder Monate pausieren") {
                        editPause = true
                    }
                }
                DetailRow(Icons.AutoMirrored.Filled.Label, "Label", alarm.label.ifBlank { "Hinzufügen" }) { editLabel = true }
                DetailRow(Icons.Filled.MusicNote, "Weckton", alarm.ringtoneName ?: "Standard-Weckton") { editSound = true }
                DetailRow(
                    Icons.Filled.Snooze, "Schlummern",
                    alarm.snoozeMinutes?.let { "$it Minuten" } ?: "Standard (${defaults.snoozeMinutes} Minuten)",
                ) { editSnooze = true }
                DetailRow(
                    Icons.AutoMirrored.Filled.VolumeUp, "Lauter werden",
                    alarm.rampSeconds?.let(Format::ramp) ?: "Standard (${Format.ramp(defaults.rampSeconds)})",
                ) { editRamp = true }
                Row(
                    Modifier.fillMaxWidth().clickable { Alarms.save(context, alarm.copy(vibrate = !alarm.vibrate)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Vibration, null, Modifier.padding(end = 16.dp))
                    Text("Vibrieren", Modifier.weight(1f))
                    Checkbox(checked = alarm.vibrate, onCheckedChange = { Alarms.save(context, alarm.copy(vibrate = it)) })
                }
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onDelete).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, null, Modifier.padding(end = 16.dp), tint = MaterialTheme.colorScheme.error)
                    Text("Löschen", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (editTime) {
        TimePickDialog(alarm.hour, alarm.minute, onDismiss = { editTime = false }) { h, m ->
            editTime = false
            saveAndAnnounce(context, alarm.copy(hour = h, minute = m, enabled = true, snoozeUntil = null).rearmed())
        }
    }
    if (editRecurrence) {
        RecurrenceDialog(alarm.recurrence, alarm.time, alarm.pausedUntil, onDismiss = { editRecurrence = false }) {
            editRecurrence = false
            // Eine Pause gilt nur für wiederholende Wecker.
            saveAndAnnounce(context, alarm.copy(recurrence = it, enabled = true, pausedUntil = alarm.pausedUntil.takeIf { _ -> it.isRepeating }))
        }
    }
    if (editSound) {
        SoundPickerDialog(alarm.ringtone, onDismiss = { editSound = false }) { sound ->
            editSound = false
            Alarms.save(context, alarm.copy(ringtone = sound.uri, ringtoneName = sound.uri?.let { sound.name }))
        }
    }
    if (editSnooze) {
        OptionDialog(
            title = "Schlummerdauer",
            options = listOf<Int?>(null) + SNOOZE_CHOICES,
            selected = alarm.snoozeMinutes,
            label = { v -> v?.let { "$it Minuten" } ?: "Standard (${defaults.snoozeMinutes} Minuten)" },
            onDismiss = { editSnooze = false },
        ) { Alarms.save(context, alarm.copy(snoozeMinutes = it)) }
    }
    if (editRamp) {
        OptionDialog(
            title = "Lauter werden",
            options = listOf<Int?>(null) + RAMP_CHOICES,
            selected = alarm.rampSeconds,
            label = { v -> v?.let(Format::ramp) ?: "Standard (${Format.ramp(defaults.rampSeconds)})" },
            onDismiss = { editRamp = false },
        ) { Alarms.save(context, alarm.copy(rampSeconds = it)) }
    }
    if (editPause) {
        PauseDialog(
            alarm = alarm,
            onDismiss = { editPause = false },
            onPause = { resume, amount, unit ->
                editPause = false
                saveAndAnnounce(
                    context,
                    alarm.copy(
                        pausedUntil = resume, enabled = true, snoozeUntil = null,
                        lastPauseAmount = amount, lastPauseUnit = unit,
                    ),
                )
            },
            onResume = {
                editPause = false
                saveAndAnnounce(context, alarm.copy(pausedUntil = null))
            },
        )
    }
    if (editLabel) {
        var text by remember { mutableStateOf(alarm.label) }
        AlertDialog(
            onDismissRequest = { editLabel = false },
            title = { Text("Label") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("z. B. Biomüll") }) },
            confirmButton = {
                TextButton(onClick = {
                    editLabel = false
                    Alarms.save(context, alarm.copy(label = text.trim()))
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editLabel = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun NextLine(alarm: Alarm, now: Long) {
    val snooze = alarm.snoozeUntil?.takeIf { it > now }
    val next = remember(alarm, now) { Alarms.nextFireMillis(alarm, now) }
    val text = when {
        snooze != null && snooze == next -> {
            val t = Instant.ofEpochMilli(snooze).atZone(ZoneId.systemDefault())
            "Schlummert bis ${Format.time(t)}"
        }
        next == null && alarm.enabled -> "Keine weiteren Termine"
        next == null -> return
        else -> {
            val t = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
            "${Format.relativeDay(t.toLocalDate(), LocalDate.now())}, ${Format.time(t)} · ${Format.until(next, now)}"
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun DayCircles(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val on = d in selected
            Surface(
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .toggleable(value = on, role = Role.Checkbox) { onToggle(d) }
                    .semantics { contentDescription = Format.longDay(d) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(Format.shortDay(d), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.padding(end = 16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
