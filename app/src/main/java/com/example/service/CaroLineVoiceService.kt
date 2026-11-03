package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android Foreground Microphone Service for CaroLine AI (GREAT V2.1).
 * Maintains microphone voice session across screen-off and app backgrounding
 * within Android's supported foreground-service restrictions, provides a persistent
 * status notification, and monitors network connectivity for automatic reconnection.
 */
class CaroLineVoiceService : Service() {

    companion object {
        private const val TAG = "CaroLineVoiceService"
        const val CHANNEL_ID = "caroline_voice_channel_v21"
        const val NOTIFICATION_ID = 2101

        const val ACTION_START_SERVICE = "com.example.service.ACTION_START_VOICE_SERVICE"
        const val ACTION_UPDATE_STATUS = "com.example.service.ACTION_UPDATE_STATUS"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_VOICE_SERVICE"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _stopRequestedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val stopRequestedEvents = _stopRequestedEvents.asSharedFlow()

        private val _networkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val networkRestoredEvents = _networkRestoredEvents.asSharedFlow()

        fun startOrUpdate(context: Context, statusText: String = "ক্যারোলাইন আপনার কথা শুনছে...") {
            try {
                val intent = Intent(context, CaroLineVoiceService::class.java).apply {
                    action = if (_isServiceRunning.value) ACTION_UPDATE_STATUS else ACTION_START_SERVICE
                    putExtra(EXTRA_STATUS_TEXT, statusText)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !_isServiceRunning.value) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, CaroLineVoiceService::class.java)
                context.stopService(intent)
                _isServiceRunning.value = false
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping foreground service: ${e.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireSafeWakeLock()
        registerNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        if (action == ACTION_STOP_SERVICE) {
            _stopRequestedEvents.tryEmit(Unit)
            stopForegroundCompat()
            stopSelf()
            _isServiceRunning.value = false
            return START_NOT_STICKY
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: "ক্যারোলাইন আপনার কথা শুনছে... (হ্যালো ক্যারোলাইন বলুন)"

        val notification = buildNotification(statusText)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isServiceRunning.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to promote service to foreground with microphone type", e)
            _isServiceRunning.value = false
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isServiceRunning.value = false
        unregisterNetworkMonitor()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing foreground notification", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CaroLine AI Voice Service (GREAT V2.1)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "অবিচ্ছিন্ন বাংলা ভয়েস অ্যাসিস্ট্যান্ট এবং মাইক্রোফোন স্ট্যাটাস"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopServiceIntent = Intent(this, CaroLineVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CaroLine AI • GREAT V2.1 (বাংলা)")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "ভয়েস মোড বন্ধ করুন",
                pendingStopIntent
            )
            .build()
    }

    private fun acquireSafeWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CaroLineAI::VoiceSessionWakeLock"
            )?.apply {
                setReferenceCounted(false)
                // Acquire for up to 30 minutes per active voice session
                acquire(30 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock", e)
        }
    }

    private fun registerNetworkMonitor() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available — signaling session recovery")
                    _networkRestoredEvents.tryEmit(Unit)
                }
            }
            networkCallback = callback
            connectivityManager?.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }
    }

    private fun unregisterNetworkMonitor() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering network callback", e)
        }
    }
}
