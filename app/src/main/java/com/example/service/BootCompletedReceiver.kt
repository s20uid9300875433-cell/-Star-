package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Handles BOOT_COMPLETED safely within Android's microphone foreground service rules.
 * On Android 14+ (API 34+), apps cannot directly start a microphone foreground service
 * from BOOT_COMPLETED without visible user interaction; therefore we post a ready notification
 * that launches CaroLine AI A3.2 immediately when tapped.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val PREFS_NAME = "caroline_ai_prefs_v29"
        private const val KEY_AUTO_BOOT = "auto_boot_notification_enabled"
        private const val BOOT_NOTIFICATION_ID = 3202
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_AUTO_BOOT, true)
            if (!enabled) return

            Log.d(TAG, "Device boot completed — posting CaroLine AI A3.2 Background Voice Assistant ready notification")
            postBootReadyNotification(context)
        }
    }

    private fun postBootReadyNotification(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CaroLineVoiceService.CHANNEL_ID,
                    "CaroLine AI A3.2 Background Voice Assistant",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("auto_start_voice", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                3202,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CaroLineVoiceService.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("CaroLine AI A3.2 • Ready (\"Hey CaroLine\")")
                .setContentText("Tap to start Background Voice Assistant in Battery-Saving Mode.")
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            manager.notify(BOOT_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post boot notification: ${e.message}")
        }
    }
}
