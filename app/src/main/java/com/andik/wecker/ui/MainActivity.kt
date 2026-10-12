package com.andik.wecker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.andik.wecker.alarm.Alarms

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) Alarms.rescheduleAll(this)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            WeckerTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
                if (screen != Screen.LIST) BackHandler { screen = Screen.LIST }
                when (screen) {
                    Screen.LIST -> AlarmListScreen(onOpenSettings = { screen = Screen.SETTINGS }, onOpenHelp = { screen = Screen.HELP })
                    Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.LIST })
                    Screen.HELP -> HelpScreen(onBack = { screen = Screen.LIST })
                }
            }
        }
    }
}

private enum class Screen { LIST, SETTINGS, HELP }
