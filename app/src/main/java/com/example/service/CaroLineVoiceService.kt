package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Requirement F & N: Android Foreground Service + Movable System Floating CaroLine Window (A3.2).
 *
 * - Hosts the movable floating assistant bubble over other apps when `Settings.canDrawOverlays(context)` is granted.
 * - When tapped, opens the compact floating window containing:
 *   1. Microphone button
 *   2. Text command field
 *   3. Send button
 *   4. Short AI response
 *   5. Close button
 *   6. Expand assistant button (returns to MainActivity without restarting the engine)
 * - Shares a single command pipeline with CaroLineViewModel so no duplicate services or restarts occur.
 */
class CaroLineVoiceService : Service() {

    companion object {
        private const val TAG = "CaroLineVoiceService"
        const val CHANNEL_ID = "caroline_voice_channel_a32"
        const val NOTIFICATION_ID = 3201

        const val ACTION_START_SERVICE = "com.example.service.ACTION_START_VOICE_SERVICE"
        const val ACTION_UPDATE_STATUS = "com.example.service.ACTION_UPDATE_STATUS"
        const val ACTION_RESUME_LISTENING = "com.example.service.ACTION_RESUME_LISTENING"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_VOICE_SERVICE"
        const val ACTION_SHOW_FLOATING_BUBBLE = "com.example.service.ACTION_SHOW_FLOATING_BUBBLE"
        const val ACTION_HIDE_FLOATING_BUBBLE = "com.example.service.ACTION_HIDE_FLOATING_BUBBLE"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _isScreenInteractive = MutableStateFlow(true)
        val isScreenInteractive = _isScreenInteractive.asStateFlow()

        private val _isSystemOverlayShowing = MutableStateFlow(false)
        val isSystemOverlayShowing = _isSystemOverlayShowing.asStateFlow()

        private val _floatingResponseSnippet = MutableStateFlow(
            "नमस्ते बॉस! मैं कैरोलाइन हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?"
        )
        val floatingResponseSnippet = _floatingResponseSnippet.asStateFlow()

        private val _stopRequestedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val stopRequestedEvents = _stopRequestedEvents.asSharedFlow()

        private val _resumeRequestedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val resumeRequestedEvents = _resumeRequestedEvents.asSharedFlow()

        private val _networkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val networkRestoredEvents = _networkRestoredEvents.asSharedFlow()

        private val _floatingMicClickedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
        val floatingMicClickedEvents = _floatingMicClickedEvents.asSharedFlow()

        private val _floatingTextCommandEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
        val floatingTextCommandEvents = _floatingTextCommandEvents.asSharedFlow()

        fun updateFloatingSnippet(text: String) {
            if (text.isNotBlank()) {
                _floatingResponseSnippet.value = text
            }
        }

        fun canDrawOverlays(context: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else {
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        fun startOrUpdate(context: Context, statusText: String = "CaroLine AI A3.2 Active — Listening") {
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

        fun showSystemFloatingOverlay(context: Context) {
            if (!canDrawOverlays(context)) return
            try {
                val intent = Intent(context, CaroLineVoiceService::class.java).apply {
                    action = ACTION_SHOW_FLOATING_BUBBLE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not show floating overlay: ${e.message}")
            }
        }

        fun hideSystemFloatingOverlay(context: Context) {
            try {
                val intent = Intent(context, CaroLineVoiceService::class.java).apply {
                    action = ACTION_HIDE_FLOATING_BUBBLE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not hide floating overlay: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, CaroLineVoiceService::class.java)
                context.stopService(intent)
                _isServiceRunning.value = false
                _isSystemOverlayShowing.value = false
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping foreground service: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var screenStateReceiver: BroadcastReceiver? = null

    private var windowManager: WindowManager? = null
    private var floatingRootView: FrameLayout? = null
    private var bubbleView: TextView? = null
    private var expandedCardView: LinearLayout? = null
    private var responseTextView: TextView? = null
    private var commandInputView: EditText? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkMonitor()
        registerScreenStateMonitor()

        serviceScope.launch {
            _floatingResponseSnippet.collect { snippet ->
                responseTextView?.text = snippet
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        when (action) {
            ACTION_STOP_SERVICE -> {
                _stopRequestedEvents.tryEmit(Unit)
                removeSystemOverlayWindow()
                stopForegroundCompat()
                stopSelf()
                _isServiceRunning.value = false
                return START_NOT_STICKY
            }
            ACTION_RESUME_LISTENING -> {
                _resumeRequestedEvents.tryEmit(Unit)
            }
            ACTION_SHOW_FLOATING_BUBBLE -> {
                if (canDrawOverlays(this)) {
                    ensureSystemOverlayWindow()
                }
            }
            ACTION_HIDE_FLOATING_BUBBLE -> {
                removeSystemOverlayWindow()
            }
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: "CaroLine AI A3.2 Active — Ready for commands"

        val hasMicPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPerm) {
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
                acquireSafeWakeLock()
            } catch (e: Exception) {
                Log.w(TAG, "Foreground promotion skipped safely: ${e.message}")
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isServiceRunning.value = false
        removeSystemOverlayWindow()
        unregisterScreenStateMonitor()
        unregisterNetworkMonitor()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun ensureSystemOverlayWindow() {
        if (floatingRootView != null || !canDrawOverlays(this)) return

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            windowManager = wm

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpToPx(16)
                y = dpToPx(140)
            }
            layoutParams = params

            val root = FrameLayout(this)

            // 1. Compact Draggable Floating Bubble ("AI")
            val bubble = TextView(this).apply {
                text = "AI"
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                val sizePx = dpToPx(56)
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#FFB300"))
                ).apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dpToPx(2), Color.WHITE)
                }
                elevation = dpToPx(10).toFloat()
            }

            // 2. Expanded Compact Floating Window (6 required controls)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                val pad = dpToPx(14)
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(dpToPx(300), FrameLayout.LayoutParams.WRAP_CONTENT)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(18).toFloat()
                    setColor(Color.parseColor("#F20A1224"))
                    setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
                }
                elevation = dpToPx(14).toFloat()
            }

            // Top Row: Title + Expand Button + Close Button
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleTv = TextView(this).apply {
                text = "CaroLine AI A3.2"
                setTextColor(Color.parseColor("#FFB300"))
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val expandBtn = Button(this).apply {
                text = "Expand"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.parseColor("#00E5FF"))
                setOnClickListener {
                    val openIntent = Intent(this@CaroLineVoiceService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(openIntent)
                    card.visibility = View.GONE
                    bubble.visibility = View.VISIBLE
                }
            }
            val closeBtn = Button(this).apply {
                text = "Close"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#37474F"))
                setOnClickListener {
                    card.visibility = View.GONE
                    bubble.visibility = View.VISIBLE
                }
            }
            headerRow.addView(titleTv)
            headerRow.addView(expandBtn)
            headerRow.addView(closeBtn)

            // Short AI Response Display
            val respTv = TextView(this).apply {
                text = _floatingResponseSnippet.value
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            }
            responseTextView = respTv

            // Command Input Row: Mic Button + Text Field + Send Button
            val inputRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val micBtn = Button(this).apply {
                text = "Mic"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.parseColor("#FFB300"))
                setOnClickListener {
                    _floatingMicClickedEvents.tryEmit(Unit)
                }
            }
            val editCmd = EditText(this).apply {
                hint = "Type command..."
                setHintTextColor(Color.parseColor("#90A4AE"))
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(6)
                    marginEnd = dpToPx(6)
                }
            }
            commandInputView = editCmd

            val sendBtn = Button(this).apply {
                text = "Send"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.parseColor("#00E5FF"))
                setOnClickListener {
                    val cmd = editCmd.text?.toString()?.trim().orEmpty()
                    if (cmd.isNotEmpty()) {
                        editCmd.setText("")
                        _floatingTextCommandEvents.tryEmit(cmd)
                    }
                }
            }
            inputRow.addView(micBtn)
            inputRow.addView(editCmd)
            inputRow.addView(sendBtn)

            card.addView(headerRow)
            card.addView(respTv)
            card.addView(inputRow)

            root.addView(bubble)
            root.addView(card)

            // Drag & Tap handling for the Floating Bubble
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            bubble.setOnTouchListener { _, event ->
                val p = layoutParams ?: return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = p.x
                        initialY = p.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = initialX + (event.rawX - initialTouchX).toInt()
                        p.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            wm.updateViewLayout(root, p)
                        } catch (_: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = abs(event.rawX - initialTouchX)
                        val dy = abs(event.rawY - initialTouchY)
                        if (dx < dpToPx(10) && dy < dpToPx(10)) {
                            bubble.visibility = View.GONE
                            card.visibility = View.VISIBLE
                        }
                        true
                    }
                    else -> false
                }
            }

            wm.addView(root, params)
            floatingRootView = root
            bubbleView = bubble
            expandedCardView = card
            _isSystemOverlayShowing.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach system overlay window", e)
            _isSystemOverlayShowing.value = false
        }
    }

    private fun removeSystemOverlayWindow() {
        try {
            val root = floatingRootView
            if (root != null) {
                windowManager?.removeView(root)
            }
        } catch (_: Exception) {}
        floatingRootView = null
        bubbleView = null
        expandedCardView = null
        responseTextView = null
        commandInputView = null
        _isSystemOverlayShowing.value = false
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
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
                "CaroLine AI A3.2 Smart JARVIS Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CaroLine AI A3.2 Voice & Floating Assistant Service"
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
            .setContentTitle("CaroLine AI A3.2 • Ultimate Smart JARVIS")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                "Resume Mic",
                pendingResumeIntent
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Service",
                pendingStopIntent
            )
            .build()
    }

    private fun acquireSafeWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CaroLineAI::JarvisVoiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(15 * 60 * 1000L)
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
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            _isScreenInteractive.value = true
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
