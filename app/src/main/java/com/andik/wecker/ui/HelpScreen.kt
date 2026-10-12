package com.andik.wecker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andik.wecker.R

private const val SOURCE_URL = "https://github.com/anka1981/WeckMich"

private val SECTIONS = listOf(
    R.string.help_repeat_title to R.string.help_repeat_text,
    R.string.help_timetable_title to R.string.help_timetable_text,
    R.string.help_pause_title to R.string.help_pause_text,
    R.string.help_ringing_title to R.string.help_ringing_text,
    R.string.help_flip_title to R.string.help_flip_text,
    R.string.help_reliability_title to R.string.help_reliability_text,
    R.string.help_privacy_title to R.string.help_privacy_text,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SECTIONS.forEach { (title, text) -> Section(stringResource(title), stringResource(text)) }
            Section(stringResource(R.string.help_about_title), stringResource(R.string.help_about_text, version, SOURCE_URL))
        }
    }
}

@Composable
private fun Section(title: String, text: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
