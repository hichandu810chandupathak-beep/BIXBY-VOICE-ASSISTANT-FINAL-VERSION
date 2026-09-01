package com.bixby.voiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var speech: SpeechRecognizer
    private lateinit var status: TextView
    private lateinit var orb: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        orb = findViewById(R.id.voiceOrb)

        findViewById<ImageButton>(R.id.micButton).setOnClickListener {
            listen()
        }

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

    private fun listen() {
        status.text = "Listening..."
        orb.text = "◉"

        speech = SpeechRecognizer.createSpeechRecognizer(this)

        speech.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                status.text = if (
                    text.contains("hey bixby", true)
                ) {
                    "Bixby activated"
                } else if (text.isNotBlank()) {
                    text
                } else {
                    "How can I help?"
                }
            }

            override fun onError(error: Int) {
                status.text = "Try again"
            }

            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Listening..."
            }

            override fun onBeginningOfSpeech() {
                status.text = "I'm listening..."
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status.text = "Processing..."
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {}

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {}
        })

        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "en-US"
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )
            }
        )
    }

    override fun onDestroy() {
        if (::speech.isInitialized) {
            speech.destroy()
        }
        super.onDestroy()
    }
}