package com.andik.wecker.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.andik.wecker.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.ACTION_FIRE) return
        AlarmStore.init(context)
        val id = intent.getIntExtra(Alarms.EXTRA_ID, -1)
        val alarm = Alarms.onFired(context, id) ?: return
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java)
                .setAction(AlarmService.ACTION_RING)
                .putExtra(Alarms.EXTRA_ID, alarm.id),
        )
    }
}

/** Nach Neustart, Zeit- oder Zeitzonenwechsel und App-Update alle Wecker neu planen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        AlarmStore.init(context)
        Alarms.rescheduleAll(context)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
