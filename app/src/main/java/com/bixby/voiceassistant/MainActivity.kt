package com.bixby.voiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private companion object {
        const val REQUEST_RECORD_AUDIO = 1001
        const val TYPING_DELAY_MS = 24L
    }

    private lateinit var floatingBar: LinearLayout
    private lateinit var responseSheet: LinearLayout
    private lateinit var status: TextView
    private lateinit var responseContent: TextView
    private lateinit var orbGlow: ImageView

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var pendingResponse: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val aiHandler = AssistantAiHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        floatingBar = findViewById(R.id.floatingBar)
        responseSheet = findViewById(R.id.responseSheet)
        status = findViewById(R.id.tvAssistantStatus)
        responseContent = findViewById(R.id.tvResponseContent)
        orbGlow = findViewById(R.id.orbGlow)

        startOrbPulse()
        textToSpeech = TextToSpeech(this, this)
        setupSpeechRecognizer()

        findViewById<View>(R.id.btnKeyboard).setOnClickListener {
            stopListening()
            status.text = "Type a command"
        }

        findViewById<View>(R.id.btnMicTrigger).setOnClickListener {
            if (isListening) stopListening() else requestMicrophoneAndListen()
        }

        // Keep the existing visual interaction; no layout changes are made in this phase.
        floatingBar.setOnClickListener { view ->
            if (view.id == R.id.floatingBar) toggleResponseSheet()
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition unavailable"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    status.text = "Listening..."
                }

                override fun onBeginningOfSpeech() {
                    status.text = "Listening..."
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    isListening = false
                    status.text = "Thinking..."
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) status.text = text
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        status.text = "I didn't catch that"
                        return
                    }
                    status.text = "Thinking..."
                    handleRecognizedText(text)
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        status.text = "Didn't catch that"
                    } else {
                        status.text = "Try again"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun requestMicrophoneAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
            return
        }
        startListening()
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        status.text = "Listening..."
        isListening = true
        recognizer.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.cancel()
        isListening = false
        status.text = "Listening..."
    }

    private fun handleRecognizedText(userText: String) {
        mainHandler.post {
            val response = aiHandler.generateResponse(userText)
            pendingResponse = response
            showResponseWithTyping(response)
            speakResponse(response)
        }
    }

    private fun showResponseWithTyping(response: String) {
        responseSheet.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(280L)
            .start()
        responseSheet.visibility = View.VISIBLE
        responseContent.text = ""

        var index = 0
        val typeNext = object : Runnable {
            override fun run() {
                if (index >= response.length) return
                responseContent.append(response[index].toString())
                index++
                mainHandler.postDelayed(this, TYPING_DELAY_MS)
            }
        }
        mainHandler.post(typeNext)
    }

    private fun speakResponse(response: String) {
        val tts = textToSpeech ?: return
        if (tts.isSpeaking) tts.stop()
        startOrbPulse()
        tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "bixby_response")
    }

    private fun startOrbPulse() {
        orbGlow.clearAnimation()
        orbGlow.startAnimation(AnimationUtils.loadAnimation(this, R.anim.orb_pulse))
    }

    private fun stopOrbPulse() {
        orbGlow.clearAnimation()
    }

    private fun toggleResponseSheet() {
        if (responseSheet.visibility == View.VISIBLE) {
            responseSheet.animate()
                .translationY(responseSheet.height.toFloat())
                .alpha(0f)
                .setDuration(220L)
                .withEndAction {
                    responseSheet.visibility = View.GONE
                    responseSheet.translationY = 0f
                    responseSheet.alpha = 1f
                }
                .start()
        } else {
            responseSheet.translationY = responseSheet.height.toFloat()
            responseSheet.alpha = 0f
            responseSheet.visibility = View.VISIBLE
            responseSheet.animate().translationY(0f).alpha(1f).setDuration(280L).start()
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
        }
        textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = runOnUiThread { startOrbPulse() }
            override fun onDone(utteranceId: String?) = runOnUiThread { stopOrbPulse() }
            override fun onError(utteranceId: String?) = runOnUiThread { stopOrbPulse() }
        })
        pendingResponse?.let {
            pendingResponse = null
            speakResponse(it)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            status.text = "Microphone permission needed"
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        stopOrbPulse()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
