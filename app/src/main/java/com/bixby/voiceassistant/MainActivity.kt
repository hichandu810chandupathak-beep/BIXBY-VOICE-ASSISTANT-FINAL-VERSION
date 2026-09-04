package com.bixby.voiceassistant // Keep the actual existing package name

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var tvAssistantStatus: TextView
    private lateinit var tvUserQuery: TextView
    private lateinit var bixbyListeningBar: View
    private lateinit var btnMicTrigger: View
    private lateinit var rootOverlay: View

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var barAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAssistantStatus = findViewById(R.id.tvAssistantStatus)
        tvUserQuery = findViewById(R.id.tvUserQuery)
        bixbyListeningBar = findViewById(R.id.bixbyListeningBar)
        btnMicTrigger = findViewById(R.id.btnMicTrigger)
        rootOverlay = findViewById(R.id.rootOverlay)

        rootOverlay.setOnClickListener { finish() }
        findViewById<View>(R.id.bixbyBottomSheet).setOnClickListener { }

        setupSuggestionChips()
        initMaleVoiceTTS()
        initSpeechRecognizer()

        btnMicTrigger.setOnClickListener { checkPermissionAndListen() }
        checkPermissionAndListen()
    }

    private fun setupSuggestionChips() {
        findViewById<TextView>(R.id.chipWeather)?.setOnClickListener {
            handleQuery("What's the weather?")
        }
        findViewById<TextView>(R.id.chipAlarm)?.setOnClickListener {
            handleQuery("Set alarm at 7 AM")
        }
        findViewById<TextView>(R.id.chipJoke)?.setOnClickListener {
            handleQuery("Tell me a joke")
        }
    }

    private fun initMaleVoiceTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.ENGLISH

                val availableVoices = textToSpeech?.voices
                val maleVoice = availableVoices?.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    !voice.isNetworkConnectionRequired &&
                            (name.contains("male") || name.contains("#male") || name.contains("en-us-x-sfg#male") || name.contains("en-in-x-ahp#male"))
                } ?: availableVoices?.firstOrNull { it.name.lowercase(Locale.ROOT).contains("male") }

                if (maleVoice != null) {
                    textToSpeech?.voice = maleVoice
                } else {
                    textToSpeech?.setPitch(0.80f)
                    textToSpeech?.setSpeechRate(0.95f)
                }

                speak("Hello! How can I help you today?")
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        tvUserQuery.text = "Listening..."
                        startListeningAnimation()
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        val scale = 1.0f + (rmsdB.coerceAtLeast(0f) / 10f)
                        bixbyListeningBar.scaleX = scale.coerceIn(1.0f, 2.2f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { stopListeningAnimation() }
                    override fun onError(error: Int) {
                        stopListeningAnimation()
                        tvUserQuery.text = "Tap mic to try again."
                    }
                    override fun onResults(results: Bundle?) {
                        stopListeningAnimation()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) handleQuery(matches[0])
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) tvUserQuery.text = matches[0]
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun handleQuery(query: String) {
        tvUserQuery.text = query
        val response = when {
            query.contains("weather", ignoreCase = true) -> "It's currently clear and 24 degrees outside."
            query.contains("alarm", ignoreCase = true) -> "Alarm set for 7:00 AM."
            query.contains("joke", ignoreCase = true) -> "Why don't scientists trust atoms? Because they make up everything!"
            else -> "I heard you say: $query. How else can I assist?"
        }
        tvAssistantStatus.text = response
        speak(response)
    }

    private fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BixbyUtterance")
    }

    private fun startListeningAnimation() {
        bixbyListeningBar.animate().alpha(1.0f).setDuration(200).start()
    }

    private fun stopListeningAnimation() {
        bixbyListeningBar.animate().scaleX(1.0f).setDuration(200).start()
    }

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
