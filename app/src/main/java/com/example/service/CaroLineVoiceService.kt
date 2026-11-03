package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * Android Foreground Microphone Service for CaroLine AI 2.9 – Ultimate Smart JARVIS.
 * Maintains the microphone voice session across screen-off and app backgrounding
 * within Android 8–15 foreground-service requirements, provides a persistent
 * notification with Resume and Stop controls, and monitors screen & network state.
 */
class CaroLineVoiceService : Service() {

    companion object {
        private const val TAG = "CaroLineVoiceService"
        const val CHANNEL_ID = "caroline_voice_channel_v29"
        const val NOTIFICATION_ID = 2901

        const val ACTION_START_SERVICE = "com.example.service.ACTION_START_VOICE_SERVICE"
        const val ACTION_UPDATE_STATUS = "com.example.service.ACTION_UPDATE_STATUS"
        const val ACTION_RESUME_LISTENING = "com.example.service.ACTION_RESUME_LISTENING"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_VOICE_SERVICE"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _isScreenInteractive = MutableStateFlow(true)
        val isScreenInteractive = _isScreenInteractive.asStateFlow()

        private val _stopRequestedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val stopRequestedEvents = _stopRequestedEvents.asSharedFlow()

        private val _resumeRequestedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val resumeRequestedEvents = _resumeRequestedEvents.asSharedFlow()

        private val _networkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val networkRestoredEvents = _networkRestoredEvents.asSharedFlow()

        fun startOrUpdate(context: Context, statusText: String = "कैरोलाइन आपकी बात सुन रही है...") {
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
    private var screenStateReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireSafeWakeLock()
        registerNetworkMonitor()
        registerScreenStateMonitor()
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

        if (action == ACTION_RESUME_LISTENING) {
            _resumeRequestedEvents.tryEmit(Unit)
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: "कैरोलाइन 2.9 सक्रिय — हिंदी वॉइस कमांड सुन रही है"

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
        unregisterScreenStateMonitor()
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
                "CaroLine AI 2.9 Ultimate Smart JARVIS Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "निरंतर हिंदी वॉइस असिस्टेंट और माइक्रोफ़ोन स्टेटस"
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

        val resumeIntent = Intent(this, CaroLineVoiceService::class.java).apply {
            action = ACTION_RESUME_LISTENING
        }
        val pendingResumeIntent = PendingIntent.getService(
            this,
            1,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopServiceIntent = Intent(this, CaroLineVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            2,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CaroLine AI 2.9 • Ultimate Smart JARVIS")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                "फिर से सुनें",
                pendingResumeIntent
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "वॉइस मोड बंद करें",
                pendingStopIntent
            )
            .build()
    }

    private fun acquireSafeWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CaroLineAI::JarvisVoiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(20 * 60 * 1000L)
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

    private fun registerScreenStateMonitor() {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            _isScreenInteractive.value = false
                            Log.d(TAG, "Screen turned OFF — maintaining foreground service within OS limits")
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            _isScreenInteractive.value = true
                            Log.d(TAG, "Screen turned ON — signaling microphone recovery")
                            _resumeRequestedEvents.tryEmit(Unit)
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            screenStateReceiver = receiver
        } catch (e: Exception) {
            Log.w(TAG, "Could not register screen state receiver", e)
        }
    }

    private fun unregisterScreenStateMonitor() {
        try {
            screenStateReceiver?.let { unregisterReceiver(it) }
            screenStateReceiver = null
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering screen state receiver", e)
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
