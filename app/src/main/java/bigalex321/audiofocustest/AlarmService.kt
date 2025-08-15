package bigalex321.audiofocustest

import android.app.ActivityOptions
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
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "ACTION_DONE" -> {
                stopSelf()
            }
            else -> {
                makeServiceForeground()
                playAlarmSound()
            }
        }
        return START_NOT_STICKY
    }

    private fun makeServiceForeground() {
        Log.d("src", "AlarmService before startForeground")
        ServiceCompat.startForeground(
            this@AlarmService,
            NOTIFICATION_ID,
            createNotif(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Log.d("src", "AlarmService put type")
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
        Log.d("src", "AlarmService after startForeground")
    }

    private var hasAudioFocus = false
    private fun playAlarmSound() {
        if (mediaPlayer.isPlaying) return
        hasAudioFocus = requestAudioFocus()
        if (!hasAudioFocus) {
            Log.d("src", "AlarmService playAlarmSound focus not granted")
            vibrateAlarm()
            return
        }

        mediaPlayer.apply {
            setDataSource(this@AlarmService, alarmUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isLooping = true
            prepare()
            start()
        }

        vibrateAlarm()
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private val audioFocusRequest: AudioFocusRequest? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { /* optional logging */ }
                .build()
        } else null
    }

    private fun vibrateAlarm() {
        val pattern = longArrayOf(0, 500, 1000) // wait, vibrate, sleep
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, 0) // repeat indefinitely
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun createNotif(): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("fromNotif", true)
        }

        // Create ActivityOptions to allow background activity launch
        val activityIntentOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Bundle().apply {
                // Allow background activity launch
                putInt(
                    "android.activity.pendingIntent.backgroundActivityStartMode",
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
        } else {
            null
        }

        // Create the PendingIntent with ActivityOptions
        val activityPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                activityIntentOptions
            )
        } else {
            PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val doneIntent = Intent(this, AlarmService::class.java).apply {
            action = "ACTION_DONE"
        }
        val donePendingIntent = PendingIntent.getService(
            this, 0, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return notifBuilder
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Alarm")
            .setContentText("Alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(R.mipmap.ic_launcher_round, "Done", donePendingIntent)
            .setFullScreenIntent(activityPendingIntent, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    private lateinit var notifManager: NotificationManager
    private lateinit var vibrator: Vibrator
    private lateinit var audioManager: AudioManager
    private val notifBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
    private val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    private val mediaPlayer = MediaPlayer()

    companion object {
        const val CHANNEL_ID = "audio_focus_alarm_channel"
        const val NOTIFICATION_ID = 395639
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("src", "AlarmService onCreate")
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        createNotificationChannel() // before showing notification
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Alarm Notifications", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm alerts"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notifManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.release()
        vibrator.cancel()
        abandonAudioFocus()
        notifManager.cancel(NOTIFICATION_ID)
        Log.d("src", "AlarmService alarm done")
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        hasAudioFocus = false
    }
}
