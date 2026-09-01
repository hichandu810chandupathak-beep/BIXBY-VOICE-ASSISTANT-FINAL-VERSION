package com.bixby.voiceassistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
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

    private var orbAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        orb = findViewById(R.id.voiceOrb)

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts.language = Locale.forLanguageTag("hi-IN")
            }
        }

        findViewById<ImageButton>(R.id.micButton).setOnClickListener {
            listen()
        }

        startIdleAnimation()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }

    private fun startIdleAnimation() {
        orbAnimator?.cancel()

        orbAnimator = ObjectAnimator.ofFloat(
            orb,
            View.SCALE_X,
            1.0f,
            1.035f,
            1.0f
        ).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        ObjectAnimator.ofFloat(
            orb,
            View.SCALE_Y,
            1.0f,
            1.035f,
            1.0f
        ).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun startListeningAnimation() {
        orbAnimator?.cancel()

        orbAnimator = ObjectAnimator.ofFloat(
            orb,
            View.SCALE_X,
            1.0f,
            1.12f,
            1.0f
        ).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        ObjectAnimator.ofFloat(
            orb,
            View.SCALE_Y,
            1.0f,
            1.12f,
            1.0f
        ).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopAnimation() {
        orbAnimator?.cancel()
        orbAnimator = null

        orb.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()
    }

    private fun listen() {
        status.text = "Listening..."
        orb.text = "●"

        startListeningAnimation()

        if (::speech.isInitialized) {
            speech.destroy()
        }

        speech = SpeechRecognizer.createSpeechRecognizer(this)

        speech.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Listening..."
                orb.text = "●"
            }

            override fun onBeginningOfSpeech() {
                status.text = "I'm listening..."
                orb.text = "●"
                startListeningAnimation()
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                stopAnimation()
            }

            override fun onError(error: Int) {
                stopAnimation()
                status.text = "Try again"
                orb.text = "●"
                startIdleAnimation()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()

                stopAnimation()

                if (text.isBlank()) {
                    status.text = "How can I help?"
                    orb.text = "●"
                    startIdleAnimation()
                    return
                }

                if (isBixbyWakeWord(text)) {
                    status.text = "Bixby activated"
                    orb.text = "●"
                    speak("हाँ, बताइए")
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
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "hi-IN"
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "hi-IN"
            )
            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )
            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )
        }

        speech.startListening(intent)
    }

    private fun isBixbyWakeWord(text: String): Boolean {
        val normalized = text
            .lowercase(Locale.ROOT)
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")

        val bixby =
            normalized.contains("bixby") ||
            normalized.contains("bixbee") ||
            normalized.contains("bixbi") ||
            normalized.contains("बिक्सबी") ||
            normalized.contains("बिक्सबि") ||
            normalized.contains("बिक्सबे")

        val hey =
            normalized.contains("hey") ||
            normalized.contains("हे") ||
            normalized.contains("है")

        return bixby && hey || bixby
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) {
            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "BIXBY_RESPONSE"
            )
        }
    }

    override fun onDestroy() {
        orbAnimator?.cancel()

        if (::speech.isInitialized) {
            speech.destroy()
        }

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        super.onDestroy()
    }
}