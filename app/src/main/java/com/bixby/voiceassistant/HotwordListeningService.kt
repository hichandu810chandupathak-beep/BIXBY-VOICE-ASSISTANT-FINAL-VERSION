package com.bixby.voiceassistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Best-effort always-listening foreground layer for the Hey Bixby wake phrase. */
class HotwordListeningService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(android.os.Looper.getMainLooper())
    private var restarting = false
    private var wakeTriggered = false
    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = restartListening()
        override fun onPartialResults(partialResults: Bundle?) { handleResults(partialResults) }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onError(error: Int) = restartListening()
        override fun onResults(results: Bundle?) { handleResults(results); restartListening() }
    }
    override fun onCreate() {
        super.onCreate()
        try {
            if (!hasRequiredPermissions()) { stopSelf(); return }
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            startListening()
        } catch (e: Exception) {
            android.util.Log.e("BixbyLifecycle", "Hotword service startup failed", e)
            stopSelf()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { if (recognizer == null && !restarting && hasRequiredPermissions()) startListening() } catch (e: Exception) { android.util.Log.e("BixbyLifecycle", "Hotword service start failed", e); stopSelf() }
        return START_STICKY
    }
    private fun hasRequiredPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    private fun startListening() {
        if (!hasRequiredPermissions() || !SpeechRecognizer.isRecognitionAvailable(this)) return
        handler.post {
            try {
                if (!hasRequiredPermissions()) return@post
                if (wakeTriggered) wakeTriggered = false
                recognizer?.destroy()
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(listener) }
                val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
                try { recognizer?.startListening(recognitionIntent) } catch (e: Exception) { android.util.Log.e("BixbyLifecycle", "Hotword SpeechRecognizer start failed", e); restartListening() }
            } catch (e: Exception) { android.util.Log.e("BixbyLifecycle", "Hotword SpeechRecognizer initialization failed", e); recognizer = null; restartListening() }
        }
    }
    private fun restartListening() { if (restarting || wakeTriggered || !hasRequiredPermissions()) return; restarting = true; handler.postDelayed({ restarting = false; startListening() }, 250L) }
    private fun handleResults(bundle: Bundle?): Boolean {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        if (matches.any { isWakePhrase(it) }) { if (!wakeTriggered) { wakeTriggered = true; openAssistant() }; return true }
        return false
    }
    private fun isWakePhrase(text: String): Boolean {
        val normalized = text.lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
        return normalized == "bixby" || normalized.contains("hey bixby") || normalized.contains("hey bix bee") || normalized.contains("hi bixby") || normalized.contains("हे बिक्सबी") || normalized.contains("है बिक्सबी")
    }
    private fun openAssistant() {
        try { recognizer?.cancel(); recognizer?.destroy(); recognizer = null; val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP); putExtra("HOTWORD_TRIGGERED", true) }; startActivity(intent); stopSelf() } catch (e: Exception) { android.util.Log.e("BixbyLifecycle", "Failed to open assistant", e); wakeTriggered = false; restartListening() }
    }
    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_bixby_launcher).setContentTitle("Bixby voice listening").setContentText("Listening for Hey Bixby").setContentIntent(pending).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Bixby voice listening", NotificationManager.IMPORTANCE_LOW)) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); try { recognizer?.destroy() } catch (e: Exception) { android.util.Log.e("BixbyLifecycle", "Hotword recognizer destroy failed", e) }; recognizer = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL_ID = "bixby_voice_listening"; private const val NOTIFICATION_ID = 1001
        fun start(context: Context) { try { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return; if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return; val intent = Intent(context, HotwordListeningService::class.java); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent) } catch (e: Exception) { android.util.Log.e("BixbyLifecycle", "Failed to request HotwordListeningService", e) } }
    }
}
