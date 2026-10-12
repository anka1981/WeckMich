package com.andik.wecker.ui

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import com.andik.wecker.data.SoundFiles
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andik.wecker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ein wählbarer Ton; `uri == null` steht für den Standard-Weckton des Systems. */
data class Sound(val uri: String?, val name: String)

private data class SoundSection(val title: String, val sounds: List<Sound>)

fun builtinSound(context: Context) =
    Sound(SoundFiles.builtinUri(context), context.getString(R.string.builtin_sound))

/** Anzeigename des Tons eines Weckers; der eingebaute folgt der aktuellen Sprache. */
fun soundName(context: Context, uri: String?, storedName: String?): String = when {
    uri == null -> context.getString(R.string.default_sound)
    uri == SoundFiles.builtinUri(context) -> context.getString(R.string.builtin_sound)
    else -> storedName ?: context.getString(R.string.custom_file)
}

private fun systemSounds(context: Context, type: Int): List<Sound> = runCatching {
    val rm = RingtoneManager(context).apply { setType(type) }
    val cursor = rm.cursor
    buildList {
        while (cursor.moveToNext()) {
            add(Sound(rm.getRingtoneUri(cursor.position).toString(), cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)))
        }
    }.distinctBy { it.uri }.filter { isReadable(context, it.uri) }.sortedBy { it.name.lowercase() }
}.getOrDefault(emptyList())

/**
 * Nur Töne anbieten, die die App selbst öffnen darf. Eigene Klingeltöne im Gerätespeicher bräuchten eine
 * Medienberechtigung – sie würden sonst in der Liste stehen, am Morgen aber stumm bleiben.
 */
private fun isReadable(context: Context, uri: String?): Boolean = uri == null || runCatching {
    context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
}.getOrDefault(false)

private fun defaultSoundName(context: Context): String {
    val title = runCatching {
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
    }.getOrNull()
    return if (title.isNullOrBlank()) context.getString(R.string.default_sound) else context.getString(R.string.default_sound_named, title)
}

private fun fileName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull()?.substringBeforeLast('.') ?: context.getString(R.string.custom_file)

/** Spielt einen Ton zum Vorhören über den Wecker-Audiokanal, also in der späteren Lautstärke. */
private class Preview(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(uri: Uri) {
        stop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(context, uri)
            mp.setOnPreparedListener { it.start() }
            // Nach einmaligem Abspielen freigeben statt den Player offen zu halten.
            mp.setOnCompletionListener { if (player === it) stop() }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            mp.release()
        }
    }

    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPickerDialog(current: String?, currentName: String?, onDismiss: () -> Unit, onConfirm: (Sound) -> Unit) {
    val context = LocalContext.current
    val preview = remember { Preview(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    var sections by remember { mutableStateOf<List<SoundSection>?>(null) }
    // Überlebt Drehen: sonst wäre nach einer Drehung die Auswahl weg und eine gerade übernommene
    // eigene Datei fehlte in der Liste.
    var custom by rememberSaveable(stateSaver = SoundSaver) { mutableStateOf<Sound?>(null) }
    var selected by rememberSaveable(stateSaver = SoundSaver) { mutableStateOf<Sound?>(null) }

    val alarmSoundsTitle = stringResource(R.string.alarm_sounds)
    val ringtonesTitle = stringResource(R.string.ringtones)
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            listOf(
                SoundSection("", listOf(Sound(null, defaultSoundName(context)), builtinSound(context))),
                SoundSection(alarmSoundsTitle, systemSounds(context, RingtoneManager.TYPE_ALARM)),
                SoundSection(ringtonesTitle, systemSounds(context, RingtoneManager.TYPE_RINGTONE)),
            ).filter { it.sounds.isNotEmpty() }
        }
        val all = loaded.flatMap { it.sounds }
        val match = all.firstOrNull { it.uri == current }
        if (custom == null && match == null && current != null) {
            // Der Name einer kopierten Datei ist nur noch am Wecker gespeichert, nicht an der Datei-URI.
            custom = Sound(current, currentName ?: withContext(Dispatchers.IO) { fileName(context, Uri.parse(current)) })
        }
        if (selected == null) selected = match ?: custom ?: all.first()
        sections = loaded
    }

    val scope = rememberCoroutineScope()
    var copying by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        copying = true
        scope.launch {
            val name = withContext(Dispatchers.IO) { fileName(context, uri) }
            val local = withContext(Dispatchers.IO) { SoundFiles.import(context, uri) }
            copying = false
            if (local == null) {
                Toast.makeText(context, R.string.file_import_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val sound = Sound(local.toString(), name)
            custom = sound
            selected = sound
            preview.play(local)
        }
    }
    // Vorhören endet, sobald die App in den Hintergrund geht – sonst spielte eine lange eigene
    // Datei in Weckerlautstärke weiter.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { preview.stop() }

    fun select(sound: Sound) {
        selected = sound
        preview.play(sound.uri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.sound)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) } },
                    actions = {
                        TextButton(onClick = { selected?.let(onConfirm) }, enabled = selected != null) { Text(stringResource(R.string.done)) }
                    },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(if (copying) R.string.file_importing else R.string.file_choose)) },
                        leadingContent = { Icon(Icons.Filled.FolderOpen, null) },
                        modifier = Modifier.selectable(false) { filePicker.launch(arrayOf("audio/*")) },
                    )
                }
                custom?.let { c -> item { SoundRow(c, selected?.uri == c.uri) { select(c) } } }
                if (sections == null) {
                    item { Text(stringResource(R.string.sounds_loading), Modifier.padding(16.dp)) }
                }
                sections?.forEach { section ->
                    if (section.title.isNotEmpty()) {
                        item {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(section.sounds, key = { section.title + it.uri }) { s ->
                        SoundRow(s, selected?.uri == s.uri && selected?.name == s.name) { select(s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundRow(sound: Sound, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Text(sound.name, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

private val SoundSaver = listSaver<Sound?, String?>(
    save = { s -> if (s == null) emptyList() else listOf(s.uri, s.name) },
    restore = { l -> if (l.isEmpty()) null else Sound(l[0], l[1].orEmpty()) },
)
