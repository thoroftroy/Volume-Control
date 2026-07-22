package com.volumecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
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

    private lateinit var equalizerEngine: EqualizerEngine
    private lateinit var volumeScaler: VolumeScaler
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        equalizerEngine = EqualizerEngine()
        volumeScaler = VolumeScaler(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startProcessing()
            "STOP" -> stopProcessing()
            "UPDATE_EQUALIZER" -> {
                val minDb = intent.getIntExtra("min_db", -40)
                val maxDb = intent.getIntExtra("max_db", -10)
                equalizerEngine.updateRange(minDb, maxDb)
            }
            "SCALE_UP" -> {
                volumeScaler.increase()
                equalizerEngine.updateScaleFactor(volumeScaler.getScaleFactor())
                updateNotification()
            }
            "SCALE_DOWN" -> {
                volumeScaler.decrease()
                equalizerEngine.updateScaleFactor(volumeScaler.getScaleFactor())
                updateNotification()
            }
            "SCALE_RESET" -> {
                volumeScaler.reset()
                equalizerEngine.updateScaleFactor(volumeScaler.getScaleFactor())
                updateNotification()
            }
            null -> {
                val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
                if (prefs.getBoolean("service_enabled", false)) {
                    startProcessing()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startProcessing() {
        if (isRunning) return
        isRunning = true

        getSharedPreferences("volume_control", MODE_PRIVATE).edit()
            .putBoolean("service_enabled", true)
            .apply()

        startForeground(NOTIFICATION_ID, buildNotification())

        volumeScaler.loadScale()
        equalizerEngine.attachGlobal()
        equalizerEngine.updateScaleFactor(volumeScaler.getScaleFactor())

        val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
        val minDb = -prefs.getInt("min_db", 40)
        val maxDb = -prefs.getInt("max_db", 10)
        equalizerEngine.updateRange(minDb, maxDb)
    }

    private fun stopProcessing() {
        isRunning = false

        getSharedPreferences("volume_control", MODE_PRIVATE).edit()
            .putBoolean("service_enabled", false)
            .apply()

        equalizerEngine.releaseAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): android.app.Notification {
        val remoteViews = RemoteViews(packageName, R.layout.widget_notification)
        val scalePercent = volumeScaler.getScalePercent()
        remoteViews.setTextViewText(R.id.tv_scale, "${scalePercent}%")

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val scaleUpIntent = Intent(this, AudioProcessingService::class.java).apply {
            action = "SCALE_UP"
        }
        val scaleDownIntent = Intent(this, AudioProcessingService::class.java).apply {
            action = "SCALE_DOWN"
        }
        val scaleResetIntent = Intent(this, AudioProcessingService::class.java).apply {
            action = "SCALE_RESET"
        }

        remoteViews.setOnClickPendingIntent(
            R.id.btn_decrease,
            PendingIntent.getService(this, 1, scaleDownIntent, flags)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.btn_increase,
            PendingIntent.getService(this, 2, scaleUpIntent, flags)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.btn_reset,
            PendingIntent.getService(this, 3, scaleResetIntent, flags)
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Volume Control Active")
            .setContentText("Volume scale: ${scalePercent}%")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .setCustomContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()
    }

    fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Volume Control",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for volume control service"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
