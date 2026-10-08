package com.andik.wecker.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.andik.wecker.alarm.Alarms
import com.andik.wecker.alarm.Issue
import com.andik.wecker.alarm.Reliability
import com.andik.wecker.data.AlarmStore
import com.andik.wecker.model.Alarm
import com.andik.wecker.model.Recurrence
import com.andik.wecker.model.Format
import com.andik.wecker.model.RAMP_CHOICES
import com.andik.wecker.model.SNOOZE_CHOICES
import java.time.LocalTime

private data class Choice(val title: String, val options: List<Pair<Int, String>>, val current: Int, val apply: (Int) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by AlarmStore.settings.collectAsState()
    var choice by remember { mutableStateOf<Choice?>(null) }
    var issues by remember { mutableStateOf(emptyList<Issue>()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { issues = Reliability.issues(context) }

    val snoozeOptions = SNOOZE_CHOICES.map { it to "$it Minuten" }
    val ringOptions = listOf(1, 5, 10, 15, 20, 30).map { it to "$it Minuten" } + (0 to "Nie")
    val rampOptions = RAMP_CHOICES.map { it to Format.ramp(it) }
    fun label(options: List<Pair<Int, String>>, v: Int) = options.firstOrNull { it.first == v }?.second ?: "$v"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Standard-Schlummerdauer") },
                supportingContent = { Text(label(snoozeOptions, settings.snoozeMinutes) + " – gilt für Wecker ohne eigene Dauer") },
                modifier = Modifier.clickable {
                    choice = Choice("Schlummerdauer", snoozeOptions, settings.snoozeMinutes) {
                        AlarmStore.updateSettings(AlarmStore.settings.value.copy(snoozeMinutes = it))
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Wecker verstummt nach") },
                supportingContent = {
                    Text(label(ringOptions, settings.ringMinutes) + " ohne Reaktion – danach gibt es den Hinweis „Verpasster Wecker“")
                },
                modifier = Modifier.clickable {
                    choice = Choice("Wecker verstummt nach", ringOptions, settings.ringMinutes) {
                        AlarmStore.updateSettings(AlarmStore.settings.value.copy(ringMinutes = it))
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Standard für „Lauter werden“") },
                supportingContent = { Text(label(rampOptions, settings.rampSeconds) + " – gilt für Wecker ohne eigene Einstellung") },
                modifier = Modifier.clickable {
                    choice = Choice("Lautstärke langsam erhöhen", rampOptions, settings.rampSeconds) {
                        AlarmStore.updateSettings(AlarmStore.settings.value.copy(rampSeconds = it))
                    }
                },
            )

            Text(
                "Zuverlässigkeit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            )
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (issues.isEmpty()) {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Alles eingerichtet – Wecker klingeln auch im Ruhe- und Stromsparmodus.", Modifier.padding(start = 12.dp))
                    }
                }
                issues.forEach { IssueCard(it) }
                Text(
                    "Teste den Wecker am besten einmal bei gesperrtem Bildschirm und eingeschaltetem " +
                        "„Nicht stören“. Wichtig: „Stopp erzwingen“ in den App-Infos löscht alle geplanten Wecker, " +
                        "bis du die App wieder öffnest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
                OutlinedButton(
                    onClick = {
                        val t = LocalTime.now()
                        Alarms.save(
                            context,
                            Alarm(
                                id = AlarmStore.nextId(), hour = t.hour, minute = t.minute, enabled = false,
                                label = "Probewecker", recurrence = Recurrence(), temporary = true,
                                snoozeUntil = System.currentTimeMillis() + 60_000L,
                            ),
                        )
                        Toast.makeText(context, "Probewecker klingelt in 1 Minute – jetzt Bildschirm sperren", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Probewecker in 1 Minute") }
            }
        }
    }

    choice?.let { c ->
        OptionDialog(
            title = c.title,
            options = c.options.map { it.first },
            selected = c.current,
            label = { v -> c.options.first { it.first == v }.second },
            onDismiss = { choice = null },
            onSelect = c.apply,
        )
    }
}
