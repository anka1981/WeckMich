package com.andik.wecker.alarm

import android.content.Context
import android.os.PowerManager

/**
 * Hält die CPU wach zwischen dem Auslösen ([AlarmReceiver]) und dem Moment, in dem der Klingeldienst
 * seinen eigenen Wakelock hat. Der AlarmManager garantiert das Wachbleiben nur während `onReceive` –
 * bis der Dienst (ggf. mit Kaltstart des Prozesses) läuft, könnte das Gerät sonst wieder einschlafen.
 */
object WakeBridge {
    private var lock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context) {
        if (lock?.isHeld == true) return
        lock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WeckMich:auslösen")
            .apply {
                setReferenceCounted(false)
                acquire(60_000L) // Obergrenze, falls der Dienst nie startet
            }
    }

    @Synchronized
    fun release() {
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }
}
