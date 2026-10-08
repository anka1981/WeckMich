package com.andik.wecker.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.andik.wecker.R
import com.andik.wecker.data.AlarmStore
import com.andik.wecker.model.Alarm
import com.andik.wecker.model.Format
import com.andik.wecker.model.rampGain
import com.andik.wecker.ui.RingingActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Welcher Wecker gerade klingelt; der Klingelbildschirm schließt sich, sobald das wieder `null` ist. */
object RingingState {
    internal val current = MutableStateFlow<Alarm?>(null)
    val ringing: StateFlow<Alarm?> = current.asStateFlow()
}

/**
 * Vordergrunddienst, der den Weckton abspielt, vibriert und die Vollbild-Benachrichtigung zeigt.
 * Er wird aus dem [AlarmReceiver] gestartet; weil der Auslöser ein `setAlarmClock`-Wecker ist, darf
 * er das auch im Doze- und Stromsparmodus aus dem Hintergrund.
 */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var volume = 1f
    private var focusRequest: AudioFocusRequest? = null

    private var ringStartedAt = 0L
    private var rampMillis = 0L

    private val timeout = Runnable {
        val alarm = RingingState.ringing.value
        finishRinging()
        if (alarm != null && !alarm.temporary) {
            val minutes = AlarmStore.settings.value.ringMinutes
            MissedNotifier.notify(this, alarm, ringStartedAt, "Nach $minutes Min. ohne Reaktion verstummt")
        }
    }

    /** Lautstärke gleichmäßig in Dezibel anheben – so empfindet das Ohr den Anstieg als stetig. */
    private val ramp = object : Runnable {
        override fun run() {
            val progress = ((System.currentTimeMillis() - ringStartedAt).toFloat() / rampMillis).coerceIn(0f, 1f)
            volume = rampGain(progress)
            player?.setVolume(volume, volume)
            if (progress < 1f) handler.postDelayed(this, 250)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlarmStore.init(this)
        val id = intent?.getIntExtra(Alarms.EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_RING -> {
                val alarm = AlarmStore.get(id)
                // startForeground muss nach startForegroundService in jedem Fall folgen.
                goForeground(alarm)
                if (alarm == null) finishRinging() else ring(alarm)
            }
            ACTION_SNOOZE -> {
                (RingingState.ringing.value?.id ?: id.takeIf { it >= 0 })?.let { Alarms.snooze(this, it) }
                finishRinging(snoozed = true)
            }
            ACTION_DISMISS -> finishRinging()
            else -> if (RingingState.ringing.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(alarm: Alarm?) {
        createChannel(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(alarm),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    private fun ring(alarm: Alarm) {
        stopPlayback()
        RingingState.current.value = alarm
        val settings = AlarmStore.settings.value
        val wakeMillis = (if (settings.ringMinutes > 0) settings.ringMinutes else 60) * 60_000L + 60_000L
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WeckMich:klingeln")
            .apply { acquire(wakeMillis) }
        ringStartedAt = System.currentTimeMillis()
        startSound(alarm, AlarmStore.rampSecondsFor(alarm))
        if (alarm.vibrate) startVibration()
        if (settings.ringMinutes > 0) handler.postDelayed(timeout, settings.ringMinutes * 60_000L)
        // Vollbild-Aktivität zusätzlich direkt starten; klappt nur, wenn das System es gerade zulässt
        // (sonst übernimmt die Vollbild-Benachrichtigung bzw. die Heads-up-Benachrichtigung).
        runCatching { startActivity(ringingIntent(this, alarm.id)) }
    }

    private fun startSound(alarm: Alarm, rampSeconds: Int) {
        // Musik oder Podcasts pausieren, solange der Wecker klingelt.
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ALARM_AUDIO)
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
        focusRequest = request

        val fallback = Uri.parse("android.resource://$packageName/${R.raw.fallback_alarm}")
        val candidates = listOfNotNull(
            alarm.ringtone?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            fallback,
        ).distinct()
        rampMillis = rampSeconds * 1000L
        volume = if (rampSeconds > 0) rampGain(0f) else 1f
        if (playFirstWorking(candidates) && rampSeconds > 0) handler.postDelayed(ramp, 250)
    }

    /** Spielt den ersten abspielbaren Ton; fällt er unterwegs aus, geht es mit dem nächsten weiter. */
    private fun playFirstWorking(candidates: List<Uri>): Boolean {
        candidates.forEachIndexed { i, uri ->
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(ALARM_AUDIO)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                // Hält die CPU wach, solange der Ton läuft – auch über das Zeitlimit des Wakelocks hinaus.
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.setOnErrorListener { failed, _, _ ->
                    failed.release()
                    if (player === failed) {
                        player = null
                        playFirstWorking(candidates.drop(i + 1))
                    }
                    true
                }
                mp.prepare()
                mp.setVolume(volume, volume)
                mp.start()
                player = mp
                return true
            } catch (e: Exception) {
                mp.release()
            }
        }
        return false
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vib.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(effect, ALARM_AUDIO)
        }
        vibrator = vib
    }

    private fun stopPlayback() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(ramp)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun finishRinging(snoozed: Boolean = false) {
        val alarm = RingingState.ringing.value
        stopPlayback()
        if (alarm != null && alarm.temporary && !snoozed) Alarms.delete(this, alarm.id)
        RingingState.current.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        RingingState.current.value = null
        super.onDestroy()
    }

    private fun buildNotification(alarm: Alarm?): Notification {
        val id = alarm?.id ?: 0
        val full = PendingIntent.getActivity(
            this, id, ringingIntent(this, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String) = PendingIntent.getService(
            this, id * 10 + action.length,
            Intent(this, AlarmService::class.java).setAction(action).putExtra(Alarms.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = alarm?.label?.takeIf { it.isNotBlank() } ?: "Wecker"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(alarm?.let { Format.time(it.hour, it.minute) } ?: "")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .apply { if (alarm != null) setFullScreenIntent(full, true) }
            .setContentIntent(full)
            .addAction(0, "Schlummern", action(ACTION_SNOOZE))
            .addAction(0, "Beenden", action(ACTION_DISMISS))
            .build()
    }

    companion object {
        const val ACTION_RING = "com.andik.wecker.RING"
        const val ACTION_SNOOZE = "com.andik.wecker.SNOOZE"
        const val ACTION_DISMISS = "com.andik.wecker.DISMISS"
        const val CHANNEL_ID = "klingeln"
        private val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        private const val NOTIFICATION_ID = 1

        fun ringingIntent(context: Context, id: Int): Intent =
            Intent(context, RingingActivity::class.java)
                .putExtra(Alarms.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Klingelnder Wecker", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Zeigt den klingelnden Wecker an, auch auf dem Sperrbildschirm"
                    setSound(null, null) // Der Ton kommt vom Dienst selbst über den Wecker-Audiokanal.
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
