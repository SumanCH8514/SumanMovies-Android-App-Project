package com.sumanonline.sumanmovies.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.sumanonline.sumanmovies.MainActivity
import com.sumanonline.sumanmovies.SumanMoviesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var progressCollectorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        // Guarantee startForeground is called immediately on service creation
        startForegroundSafely(buildInitialNotification())
        observeProgress()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Guarantee foreground state is maintained on each command
        startForegroundSafely(buildInitialNotification())

        val action = intent?.action
        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)

        if (action != null && downloadId != null) {
            val app = SumanMoviesApp.get()
            when (action) {
                ACTION_PAUSE -> {
                    serviceScope.launch {
                        app.downloadManager.pauseDownload(downloadId)
                    }
                }
                ACTION_RESUME -> {
                    serviceScope.launch {
                        app.downloadManager.resumeDownload(downloadId)
                    }
                }
                ACTION_CANCEL -> {
                    serviceScope.launch {
                        app.downloadManager.deleteDownloadById(downloadId)
                    }
                }
            }
        } else if (action == ACTION_STOP) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadService", "Failed to startForeground", e)
        }
    }

    private fun buildInitialNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "LIBRARY_DOWNLOADS")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SumanMovies Downloader")
            .setContentText("Initializing download...")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun observeProgress() {
        progressCollectorJob?.cancel()
        progressCollectorJob = serviceScope.launch {
            val app = SumanMoviesApp.get()
            var hasObservedActive = false

            app.downloadManager.progressFlow.collectLatest { progressMap ->
                val activeList = progressMap.values.filter {
                    it.status == "DOWNLOADING" || it.status == "FETCHING_PLAYLIST" || it.status == "MERGING"
                }

                if (activeList.isEmpty()) {
                    if (hasObservedActive) {
                        // Debounce stop to allow sequential downloads or temporary state transitions
                        delay(2000)
                        val stillActive = app.downloadManager.progressFlow.value.values.any {
                            it.status == "DOWNLOADING" || it.status == "FETCHING_PLAYLIST" || it.status == "MERGING"
                        }
                        if (!stillActive) {
                            try {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                            } catch (_: Exception) {}
                            stopSelf()
                        }
                    }
                } else {
                    hasObservedActive = true
                    val primary = activeList.first()
                    updateNotification(primary, activeList.size)
                }
            }
        }
    }

    private fun updateNotification(progress: DownloadProgress, totalActive: Int) {
        val percent = if (progress.totalBytes > 0) {
            ((progress.downloadedBytes.toDouble() / progress.totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val downloadedMb = progress.downloadedBytes / (1024.0 * 1024.0)
        val totalMb = progress.totalBytes / (1024.0 * 1024.0)
        val speedMb = progress.speedBytesPerSec / (1024.0 * 1024.0)

        val contentText = when (progress.status) {
            "FETCHING_PLAYLIST" -> "Parsing stream playlist..."
            "MERGING" -> "Finalizing and muxing media file..."
            else -> {
                val totalStr = if (progress.totalBytes > 0) String.format("%.1f MB", totalMb) else "calculating..."
                String.format("%.1f MB / %s (%.1f MB/s) • %d%%", downloadedMb, totalStr, speedMb, percent)
            }
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "LIBRARY_DOWNLOADS")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_DOWNLOAD_ID, progress.id)
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, progress.id)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleSuffix = if (totalActive > 1) " (+$totalActive downloading)" else ""

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading Media$titleSuffix")
            .setContentText(contentText)
            .setProgress(100, percent, progress.status == "FETCHING_PLAYLIST" || progress.status == "MERGING" || progress.totalBytes <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForegroundSafely(notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SumanMovies:DownloadWakeLock")?.apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for active media downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressCollectorJob?.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "sumanmovies_active_downloads"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.sumanonline.sumanmovies.ACTION_START"
        const val ACTION_PAUSE = "com.sumanonline.sumanmovies.ACTION_PAUSE"
        const val ACTION_RESUME = "com.sumanonline.sumanmovies.ACTION_RESUME"
        const val ACTION_CANCEL = "com.sumanonline.sumanmovies.ACTION_CANCEL"
        const val ACTION_STOP = "com.sumanonline.sumanmovies.ACTION_STOP"
        const val EXTRA_DOWNLOAD_ID = "EXTRA_DOWNLOAD_ID"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "Failed to start foreground service", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "Failed to stop service", e)
            }
        }
    }
}
