package com.bixby.voiceassistant
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView; private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null; private var isListening = false
    private val aiHandler by lazy { AssistantAiHandler(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.tvAssistantStatus)
        tts = TextToSpeech(this, this)
        
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.bixby_orb).setOnClickListener { toggleListening() }
        
        // Closes the translucent overlay if you tap outside the Bixby panel
        findViewById<View>(android.R.id.content).setOnClickListener { finish() }

        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) toggleListening() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (intent.getStringExtra("trigger_source") == "hardware_button") {
            toggleListening()
        }
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.language = Locale("hi", "IN") }

    private fun toggleListening() {
        if (isListening) { speechRecognizer?.cancel(); isListening = false; status.text = "Tap orb to talk" } 
        else startListening()
    }

    private fun startListening() {
        tts.stop()
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(p0: Bundle?) { isListening = true; status.text = "Listening..." }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(p0: Float) {}
                    override fun onBufferReceived(p0: ByteArray?) {}
                    override fun onEndOfSpeech() { isListening = false; status.text = "Thinking..." }
                    override fun onError(p0: Int) { isListening = false; status.text = "Try again" }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!text.isNullOrBlank()) processCommand(text) else status.text = "Didn't catch that"
                    }
                    override fun onPartialResults(p0: Bundle?) {}
                    override fun onEvent(p0: Int, p1: Bundle?) {}
                })
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }
        speechRecognizer?.startListening(intent)
    }

    private fun processCommand(text: String) {
        status.text = "You: $text\n\nThinking..."
        lifecycleScope.launch(Dispatchers.IO) {
            val result = aiHandler.generateResponse(text)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { reply -> status.text = reply; tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null) },
                    onFailure = { err -> status.text = "Error: ${err.message}" }
                )
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); tts.shutdown(); speechRecognizer?.destroy() }
}