package com.zevbuild.zevsafe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.zevbuild.zevsafe.MainActivity

class VaultForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_ID = "zevsafe_operations_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.zevbuild.zevsafe.action.START"
        const val ACTION_UPDATE = "com.zevbuild.zevsafe.action.UPDATE"
        const val ACTION_STOP = "com.zevbuild.zevsafe.action.STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_PROGRESS = "extra_progress" // 0 to 100

        fun startService(context: Context, title: String, message: String) {
            val intent = Intent(context, VaultForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, title: String, message: String, progress: Int) {
            val intent = Intent(context, VaultForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_PROGRESS, progress)
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VaultForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZevSafe:StreamingCryptoWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "ZevSafe Streaming Engine"
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Processing vault..."
                wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hours max safety timeout
                val notification = buildNotification(title, message, 0, indeterminate = true)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "ZevSafe Streaming Engine"
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Processing vault..."
                val progress = intent.getIntExtra(EXTRA_PROGRESS, -1)
                val notification = if (progress >= 0) {
                    buildNotification(title, message, progress, indeterminate = false)
                } else {
                    buildNotification(title, message, 0, indeterminate = true)
                }
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopForegroundTask()
            }
        }

        return START_NOT_STICKY
    }

    private fun stopForegroundTask() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(
        title: String,
        message: String,
        progress: Int,
        indeterminate: Boolean
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZevSafe Cryptographic Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for active vault encryption and decryption operations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopForegroundTask()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
