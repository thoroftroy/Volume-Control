package com.volumecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class AudioProcessingService : Service() {

    companion object {
        const val CHANNEL_ID = "volume_control_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
            private set
    }

    private lateinit var audioManager: AudioManager
    private lateinit var equalizerEngine: EqualizerEngine
    private lateinit var volumeScaler: VolumeScaler
    private val handler = Handler(Looper.getMainLooper())
    private var currentMinDb = -40
    private val volumeStreams = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        equalizerEngine = EqualizerEngine()
        volumeScaler = VolumeScaler(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startProcessing()
            "STOP" -> stopProcessing()
            "UPDATE_EQUALIZER" -> {
                currentMinDb = intent.getIntExtra("min_db", -40)
                equalizerEngine.updateRange(currentMinDb, intent.getIntExtra("max_db", -10))
                pushSystemVolume()
            }
            "SCALE_UP" -> { volumeScaler.increase(); applyScale() }
            "SCALE_DOWN" -> { volumeScaler.decrease(); applyScale() }
            "SCALE_RESET" -> { volumeScaler.reset(); applyScale() }
            "SET_SCALE" -> { volumeScaler.setScale(intent.getIntExtra("scale", 100)); applyScale() }
            null -> {
                if (getSharedPreferences("volume_control", MODE_PRIVATE)
                        .getBoolean("service_enabled", false)
                ) startProcessing() else stopSelf()
            }
        }
        return START_STICKY
    }

    private fun applyScale() {
        equalizerEngine.updateScaleFactor(volumeScaler.getScaleFactor())
        updateNotification()
        pushSystemVolume()
    }

    private fun pushSystemVolume() {
        val minFraction = (96 + currentMinDb).toFloat() / 96f
        val baseVol = (0.2f + minFraction * 0.8f).coerceIn(0.1f, 1f)
        val scale = volumeScaler.getScaleFactor()
        val finalFactor = (baseVol * scale.coerceAtMost(1f)).coerceIn(0.1f, 1f)

        for (stream in volumeStreams) {
            try {
                val max = audioManager.getStreamMaxVolume(stream)
                val target = (max * finalFactor).toInt().coerceIn(1, max)
                audioManager.setStreamVolume(stream, target, 0)
            } catch (_: Exception) {}
        }
    }

    private fun startProcessing() {
        if (isRunning) return
        isRunning = true
        getSharedPreferences("volume_control", MODE_PRIVATE).edit()
            .putBoolean("service_enabled", true).apply()
        startForeground(NOTIFICATION_ID, buildNotification())
        volumeScaler.loadScale()
        equalizerEngine.attachGlobal()
        val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
        currentMinDb = prefs.getInt("min_db", -40)
        equalizerEngine.updateRange(currentMinDb, prefs.getInt("max_db", -10))
        applyScale()
    }

    private fun stopProcessing() {
        isRunning = false
        getSharedPreferences("volume_control", MODE_PRIVATE).edit()
            .putBoolean("service_enabled", false).apply()
        equalizerEngine.releaseAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): android.app.Notification {
        val remoteViews = RemoteViews(packageName, R.layout.widget_notification)
        val scalePercent = volumeScaler.getScalePercent()
        remoteViews.setTextViewText(R.id.tv_scale, "${scalePercent}%")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun intent(action: String) = Intent(this, AudioProcessingService::class.java).apply { this.action = action }
        remoteViews.setOnClickPendingIntent(R.id.btn_decrease, PendingIntent.getService(this, 1, intent("SCALE_DOWN"), flags))
        remoteViews.setOnClickPendingIntent(R.id.btn_increase, PendingIntent.getService(this, 2, intent("SCALE_UP"), flags))
        remoteViews.setOnClickPendingIntent(R.id.btn_reset, PendingIntent.getService(this, 3, intent("SCALE_RESET"), flags))

        val contentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Volume Control Active")
            .setContentText("Volume scale: ${scalePercent}%")
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setCustomContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()
    }

    fun updateNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Volume Control",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Volume Control service"
                setShowBadge(false)
            })
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { isRunning = false; super.onDestroy() }
}
