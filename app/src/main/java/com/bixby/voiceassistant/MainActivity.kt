package com.bixby.voiceassistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var speech: SpeechRecognizer
    private lateinit var status: TextView
    private lateinit var orb: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var root: View
    private lateinit var themeButton: TextView

    private var orbAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.rootContainer)
        status = findViewById(R.id.statusText)
        orb = findViewById(R.id.voiceOrb)
        themeButton = findViewById(R.id.themeButton)

        themeButton.setOnClickListener { toggleTheme() }
        updateThemeIcon()

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts.language = Locale.forLanguageTag("hi-IN")
            }
        }

        findViewById<ImageButton>(R.id.micButton).setOnClickListener { listen() }

        startIdleAnimation()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun applySavedTheme() {
        val dark = getSharedPreferences("bixby_settings", MODE_PRIVATE)
            .getBoolean("dark_mode", true)
        val mask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        resources.configuration.uiMode = mask or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }

    private fun toggleTheme() {
        val currentlyDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        getSharedPreferences("bixby_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("dark_mode", !currentlyDark)
            .apply()
        recreate()
    }

    private fun updateThemeIcon() {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        themeButton.text = if (dark) "☼" else "☾"
        themeButton.contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode"
    }

    private fun startIdleAnimation() {
        orbAnimator?.cancel()
        val orbScaleX = ObjectAnimator.ofFloat(orb, View.SCALE_X, 1.0f, 1.08f, 1.0f)
        val orbScaleY = ObjectAnimator.ofFloat(orb, View.SCALE_Y, 1.0f, 1.08f, 1.0f)
        val glow = findViewById<View>(R.id.orb_glow)
        val glowScaleX = ObjectAnimator.ofFloat(glow, View.SCALE_X, 1.0f, 1.12f, 1.0f)
        val glowScaleY = ObjectAnimator.ofFloat(glow, View.SCALE_Y, 1.0f, 1.12f, 1.0f)

        orbAnimator = orbScaleX.apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        orbScaleY.duration = 1800
        orbScaleY.repeatCount = ValueAnimator.INFINITE
        orbScaleY.repeatMode = ValueAnimator.REVERSE
        orbScaleY.start()
        glowScaleX.duration = 1800
        glowScaleX.repeatCount = ValueAnimator.INFINITE
        glowScaleX.repeatMode = ValueAnimator.REVERSE
        glowScaleX.start()
        glowScaleY.duration = 1800
        glowScaleY.repeatCount = ValueAnimator.INFINITE
        glowScaleY.repeatMode = ValueAnimator.REVERSE
        glowScaleY.start()
    }

    private fun startListeningAnimation() {
        orbAnimator?.cancel()
        orbAnimator = ObjectAnimator.ofFloat(orb, View.SCALE_X, 1.0f, 1.16f, 1.0f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        ObjectAnimator.ofFloat(orb, View.SCALE_Y, 1.0f, 1.16f, 1.0f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopAnimation() {
        orbAnimator?.cancel()
        orbAnimator = null
        orb.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun listen() {
        status.text = "Listening..."
        orb.text = "●"
        startListeningAnimation()
        if (::speech.isInitialized) speech.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)

        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Listening..."; orb.text = "●" }
            override fun onBeginningOfSpeech() { status.text = "I'm listening..."; orb.text = "●"; startListeningAnimation() }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { stopAnimation() }
            override fun onError(error: Int) { stopAnimation(); status.text = "Try again"; orb.text = "●"; startIdleAnimation() }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                stopAnimation()
                if (text.isBlank()) {
                    status.text = "How can I help?"
                    orb.text = "●"
                    startIdleAnimation()
                    return
                }
                val command = extractBixbyCommand(text)
                if (command != null) {
                    if (command.isBlank()) {
                        status.text = "Bixby activated"
                        orb.text = "●"
                        speak("हाँ, बताइए")
                    } else {
                        status.text = command
                        orb.text = "●"
                    }
                } else {
                    status.text = text
                    orb.text = "●"
                }
                startIdleAnimation()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speech.startListening(intent)
    }

    private fun extractBixbyCommand(text: String): String? {
        val normalized = text.trim().lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return when {
            normalized == "bixby" -> ""
            normalized.startsWith("bixby ") -> text.trim().substring(5).trim()
            normalized == "hey bixby" -> ""
            normalized.startsWith("hey bixby ") -> text.trim().substring(10).trim()
            normalized == "हे बिक्सबी" -> ""
            normalized.startsWith("हे बिक्सबी ") -> text.trim().substring(10).trim()
            normalized == "है बिक्सबी" -> ""
            normalized.startsWith("है बिक्सबी ") -> text.trim().substring(10).trim()
            else -> null
        }
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BIXBY_RESPONSE")
    }

    override fun onDestroy() {
        orbAnimator?.cancel()
        if (::speech.isInitialized) speech.destroy()
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}
