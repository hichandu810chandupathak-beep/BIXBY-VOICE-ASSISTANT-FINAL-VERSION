package com.bixby.voiceassistant
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView; private lateinit var responseContent: TextView
    private var speechRecognizer: SpeechRecognizer? = null; private var isListening = false
    private val aiHandler by lazy { AssistantAiHandler(applicationContext) }
    private lateinit var audioManager: AudioManager; private lateinit var tts: TextToSpeech
    private var permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tts = TextToSpeech(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        status = findViewById(R.id.tvAssistantStatus)
        responseContent = findViewById(R.id.tvResponseContent)
        
        // CRITICAL UI FIX: Enforce Text Color based on System Theme
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        responseContent.setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        responseContent.visibility = View.VISIBLE
        responseContent.text = "Hi, I am Bixby. How can I help?"

        val settingsIcon = findViewById<ImageView>(R.id.btnSettings)
        settingsIcon.setColorFilter(if (isDark) Color.WHITE else Color.BLACK)
        settingsIcon.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        findViewById<View>(R.id.btnMicTrigger).setOnClickListener { 
            if (isListening) stopListening() else startListening() 
        }

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { 
            if (intent.getBooleanExtra("start_voice_after_welcome", false)) startListening()
        }

        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (permissions.isNotEmpty()) permissionLauncher?.launch(permissions.toTypedArray())
        else if (intent.getBooleanExtra("start_voice_after_welcome", false)) startListening()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("hi", "IN") // Hinglish Support
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        tts.stop() 
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { isListening = true; status.text = "Listening..."; audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { isListening = false; status.text = "Thinking..." }
                    override fun onError(error: Int) { isListening = false; audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0); status.text = "Try again" }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!text.isNullOrBlank()) { status.text = "Answering..."; processCommand(text) } else { status.text = "Didn't catch that" }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() { speechRecognizer?.cancel(); isListening = false; status.text = "Tap mic to talk" }

    private fun processCommand(text: String) {
        responseContent.text = "You: $text\n\nThinking..."
        lifecycleScope.launch(Dispatchers.IO) {
            val result = aiHandler.generateResponse(text)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { reply -> responseContent.text = reply; status.text = "Tap mic to talk"; tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null) },
                    onFailure = { error -> responseContent.text = error.message; status.text = "Error"; tts.speak("I encountered an error.", TextToSpeech.QUEUE_FLUSH, null, null) }
                )
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); tts.shutdown(); speechRecognizer?.destroy() }
}
