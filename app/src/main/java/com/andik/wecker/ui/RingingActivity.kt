package com.andik.wecker.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andik.wecker.alarm.AlarmService
import com.andik.wecker.alarm.RingingState
import com.andik.wecker.data.AlarmStore
import com.andik.wecker.model.Format
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Vollbild über dem Sperrbildschirm, solange ein Wecker klingelt. */
class RingingActivity : ComponentActivity() {
    companion object {
        const val EXTRA_EMERGENCY_NOTIFICATION = "emergencyNotification"
        const val EXTRA_EMERGENCY_LABEL = "emergencyLabel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AlarmStore.init(this)
        val emergencyId = intent.getIntExtra(EXTRA_EMERGENCY_NOTIFICATION, -1)
        if (emergencyId >= 0) {
            // Notfall-Klingeln (Benachrichtigung mit wiederholtem Ton): „Beenden“ entfernt sie.
            val label = intent.getStringExtra(EXTRA_EMERGENCY_LABEL).orEmpty()
            setContent {
                WeckerTheme {
                    BackHandler { }
                    RingingScreen(label = label, snoozeMinutes = 0, onSnooze = null, onDismiss = {
                        NotificationManagerCompat.from(this).cancel(emergencyId)
                        finish()
                    })
                }
            }
            return
        }
        setContent {
            WeckerTheme {
                val alarm by RingingState.ringing.collectAsState()
                LaunchedEffect(alarm) { if (alarm == null) finish() }
                BackHandler { /* Zurück beendet den Wecker nicht versehentlich. */ }
                RingingScreen(
                    label = alarm?.label.orEmpty(),
                    snoozeMinutes = AlarmStore.snoozeMinutesFor(alarm),
                    onSnooze = { AlarmService.send(this, AlarmService.ACTION_SNOOZE); finish() },
                    onDismiss = { AlarmService.send(this, AlarmService.ACTION_DISMISS); finish() },
                )
            }
        }
    }
}

@Composable
private fun RingingScreen(label: String, snoozeMinutes: Int, onSnooze: (() -> Unit)?, onDismiss: () -> Unit) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(Format.time(now), fontSize = 88.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                label.ifBlank { "Wecker" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                if (onSnooze != null) {
                    FilledTonalButton(onClick = onSnooze, modifier = Modifier.weight(1f).height(72.dp)) {
                        Icon(Icons.Filled.Snooze, null, Modifier.size(24.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("$snoozeMinutes Min.", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(72.dp)) {
                    Icon(Icons.Filled.AlarmOff, null, Modifier.size(24.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Beenden", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
