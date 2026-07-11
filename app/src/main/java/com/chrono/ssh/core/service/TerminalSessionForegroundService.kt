package com.chrono.ssh.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chrono.ssh.R

class TerminalSessionForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val connectionCount = startCommandConnectionCount(
            intentConnectionCount = intent?.takeIf { it.hasExtra(ExtraConnectionCount) }?.getIntExtra(ExtraConnectionCount, 0),
            intentNonTerminalConnectionCount = intent
                ?.takeIf { it.hasExtra(ExtraNonTerminalConnectionCount) }
                ?.getIntExtra(ExtraNonTerminalConnectionCount, 0),
            registeredTerminalSessionCount = TerminalSessionRegistry.activeCount()
        )
        ensureChannel()
        startForeground(NotificationId, notification(connectionCount.coerceAtLeast(1)))
        if (connectionCount <= 0) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return serviceRestartMode(connectionCount)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val connectionCount = TerminalSessionRegistry.activeCount()
        if (connectionCount > 0) {
            ensureChannel()
            startForeground(NotificationId, notification(connectionCount))
        } else {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "Background connections",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps active chronoSSH connections running."
        }
        manager.createNotificationChannel(channel)
    }

    private fun notification(connectionCount: Int): Notification {
        val connectionLabel = when (connectionCount) {
            0 -> "Keeping terminal, file, tunnel, and desktop sessions connected."
            1 -> "Keeping 1 active connection running in the background."
            else -> "Keeping $connectionCount active connections running in the background."
        }
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("chronoSSH connections active")
            .setContentText(connectionLabel)
            .setContentIntent(terminalSessionNotificationIntent(this))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val ChannelId = "terminal_sessions"
        private const val NotificationId = 41
        private const val NotificationRequestCode = 41
        private const val ExtraConnectionCount = "connection_count"
        private const val ExtraNonTerminalConnectionCount = "non_terminal_connection_count"

        fun setRunning(
            context: Context,
            running: Boolean,
            connectionCount: Int = 0,
            nonTerminalConnectionCount: Int = connectionCount
        ) {
            val intent = Intent(context, TerminalSessionForegroundService::class.java)
                .putExtra(ExtraConnectionCount, connectionCount.coerceAtLeast(0))
                .putExtra(ExtraNonTerminalConnectionCount, nonTerminalConnectionCount.coerceAtLeast(0))
            runCatching {
                if (running) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.stopService(intent)
                }
            }
        }

        internal fun notificationActivityFlags(): Int {
            return Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        internal fun startCommandConnectionCount(
            intentConnectionCount: Int?,
            intentNonTerminalConnectionCount: Int? = null,
            registeredTerminalSessionCount: Int
        ): Int {
            if (intentConnectionCount == null) return registeredTerminalSessionCount.coerceAtLeast(0)
            val registeredCount = registeredTerminalSessionCount.coerceAtLeast(0)
            val nonTerminalCount = intentNonTerminalConnectionCount?.coerceAtLeast(0)
            return if (nonTerminalCount != null) {
                nonTerminalCount + registeredCount
            } else {
                maxOf(intentConnectionCount.coerceAtLeast(0), registeredCount)
            }
        }

        internal fun serviceRestartMode(connectionCount: Int): Int =
            if (connectionCount > 0) START_STICKY else START_NOT_STICKY

        private fun terminalSessionNotificationIntent(context: Context): PendingIntent? {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(notificationActivityFlags())
                ?: return null
            return PendingIntent.getActivity(
                context,
                NotificationRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
