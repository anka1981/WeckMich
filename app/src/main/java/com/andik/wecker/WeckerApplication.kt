package com.andik.wecker

import com.andik.wecker.model.Format
import com.andik.wecker.data.SoundFiles
import android.app.Application
import com.andik.wecker.alarm.AlarmService
import com.andik.wecker.alarm.MissedNotifier
import com.andik.wecker.data.AlarmStore

class WeckerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Format.init(this)
        AlarmStore.init(this)
        AlarmService.createChannel(this)
        MissedNotifier.createChannel(this)
        // Nicht aufräumen, solange Wecker unlesbar sind – ihre eigenen Töne würden sonst gelöscht.
        if (!AlarmStore.loadProblem && AlarmStore.unreadableIds.isEmpty()) {
            SoundFiles.cleanup(this, AlarmStore.alarms.value.map { it.ringtone })
        }
    }
}
