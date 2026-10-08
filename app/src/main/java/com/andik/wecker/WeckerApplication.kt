package com.andik.wecker

import android.app.Application
import com.andik.wecker.alarm.AlarmService
import com.andik.wecker.alarm.MissedNotifier
import com.andik.wecker.data.AlarmStore

class WeckerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmStore.init(this)
        AlarmService.createChannel(this)
        MissedNotifier.createChannel(this)
    }
}
