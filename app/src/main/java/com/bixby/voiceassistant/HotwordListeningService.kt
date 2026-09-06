package com.bixby.voiceassistant
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HotwordListeningService : Service() {
    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, HotwordListeningService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) { android.util.Log.e("Bixby", "Failed to start service", e) }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "bixby_background")
            .setContentTitle("Bixby is ready")
            .setContentText("Tap the Bixby button or Mic to talk")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, notification) // CRITICAL FIX: Must be called immediately
        return START_STICKY
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("bixby_background", "Bixby Core", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}