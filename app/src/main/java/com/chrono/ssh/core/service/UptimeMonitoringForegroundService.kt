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
import com.chrono.ssh.core.data.ChronoSSHRepository
import com.chrono.ssh.core.model.ServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class UptimeMonitoringForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NotificationId, notification())
        val enabled = intent?.getBooleanExtra(ExtraEnabled, true) ?: true
        if (!enabled) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!ChronoSSHRepository(applicationContext).loadSettings().uptimeBackgroundMonitoringEnabled) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun monitorLoop() {
        val repository = ChronoSSHRepository(applicationContext)
        val probe = TcpReachabilityProbe()
        while (coroutineContext.isActive) {
            val settings = repository.loadSettings()
            if (!settings.uptimeBackgroundMonitoringEnabled) {
                stopSelf()
                break
            }
            val servers = repository.servers.filter { it.monitoringConfig.enabled }
            servers.forEach { server ->
                val result = runCatching { probe.probe(server) }.getOrElse { error ->
                    ReachabilityResult(
                        reachable = false,
                        latencyMs = null,
                        message = "TCP probe failed for ${server.host}:${server.port}: ${error.message ?: error::class.java.simpleName}"
                    )
                }
                repository.updateProbeResult(
                    serverId = server.id,
                    status = if (result.reachable) ServerStatus.Online else ServerStatus.Offline,
                    latencyMs = result.latencyMs,
                    message = result.message
                )
            }
            delay(ServerStatusRefreshPolicy.liveLoopSeconds(settings.autoRefreshSeconds) * 1000L)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ChannelId, "Uptime monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps chronoSSH uptime checks running in the background."
            }
        )
    }

    private fun notification(): Notification {
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_chrono)
            .setContentTitle("chronoSSH uptime monitoring")
            .setContentText("Checking enabled hosts in the background.")
            .setContentIntent(notificationIntent(this))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val ChannelId = "uptime_monitoring"
        private const val NotificationId = 42
        private const val NotificationRequestCode = 42
        private const val ExtraEnabled = "enabled"

        fun setRunning(context: Context, running: Boolean) {
            val intent = Intent(context, UptimeMonitoringForegroundService::class.java)
                .putExtra(ExtraEnabled, running)
            runCatching {
                if (running) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.stopService(intent)
                }
            }
        }

        private fun notificationIntent(context: Context): PendingIntent? {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
