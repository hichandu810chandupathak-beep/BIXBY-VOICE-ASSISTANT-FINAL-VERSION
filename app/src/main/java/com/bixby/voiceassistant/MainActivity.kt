package com.bixby.voiceassistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var speech: SpeechRecognizer
    private lateinit var status: TextView
    private lateinit var orb: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var root: View
    private lateinit var themeButton: ImageButton

    private var animationSet = mutableListOf<ObjectAnimator>()
    private var listeningByButton = false
    private val speechHandler = Handler(Looper.getMainLooper())

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestAssistantRoleIfAvailable()
    }

    private val assistantRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySavedTheme()
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
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            status.text = "Speaking..."
                            orb.text = "●"
                            startListeningAnimation()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            status.text = "How can I help?"
                            orb.text = "●"
                            startIdleAnimation()
                            listeningByButton = false
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            status.text = "How can I help?"
                            orb.text = "●"
                            startIdleAnimation()
                            listeningByButton = false
                        }
                    }
                })
            }
        }

        findViewById<ImageButton>(R.id.micButton).setOnClickListener {
            if (listeningByButton) {
                stopListening()
            } else {
                listen()
            }
        }
        startIdleAnimation()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            requestAssistantRoleIfAvailable()
        }
    }

    private fun requestAssistantRoleIfAvailable() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) return
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
        assistantRoleLauncher.launch(intent)
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
        themeButton.setImageResource(if (dark) R.drawable.ic_sun else R.drawable.ic_moon)
        themeButton.contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode"
    }

    private fun clearAnimations() {
        animationSet.forEach { it.cancel() }
        animationSet.clear()
    }

    private fun addPulse(view: View, minScale: Float, maxScale: Float, durationMs: Long, delayMs: Long = 0L) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, minScale, maxScale, minScale).apply {
            duration = durationMs
            startDelay = delayMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            animationSet.add(this)
            start()
        }
        ObjectAnimator.ofFloat(view, View.SCALE_Y, minScale, maxScale, minScale).apply {
            duration = durationMs
            startDelay = delayMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            animationSet.add(this)
            start()
        }
    }

    private fun addRotation(view: View, degrees: Float, durationMs: Long, delayMs: Long = 0L) {
        ObjectAnimator.ofFloat(view, View.ROTATION, 0f, degrees).apply {
            duration = durationMs
            startDelay = delayMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            animationSet.add(this)
            start()
        }
    }

    private fun startIdleAnimation() {
        clearAnimations()
        val outer = findViewById<View>(R.id.orb_outer)
        val middle = findViewById<View>(R.id.orb_middle)
        val glow = findViewById<View>(R.id.orb_glow)

        addPulse(outer, 1.00f, 1.035f, 2600L)
        addPulse(middle, 1.00f, 1.055f, 2200L, 120L)
        addPulse(glow, 1.00f, 1.10f, 1800L, 220L)
        addPulse(orb, 1.00f, 1.07f, 1600L, 280L)
        addRotation(outer, -360f, 14000L)
        addRotation(middle, 360f, 10000L, 180L)
    }

    private fun startListeningAnimation() {
        clearAnimations()
        val outer = findViewById<View>(R.id.orb_outer)
        val middle = findViewById<View>(R.id.orb_middle)
        val glow = findViewById<View>(R.id.orb_glow)

        addPulse(outer, 1.00f, 1.07f, 1000L)
        addPulse(middle, 1.00f, 1.10f, 850L, 80L)
        addPulse(glow, 1.00f, 1.16f, 700L, 140L)
        addPulse(orb, 1.00f, 1.16f, 700L, 180L)
        addRotation(outer, -360f, 4200L)
        addRotation(middle, 360f, 3000L, 100L)
    }

    private fun startProcessingAnimation() {
        clearAnimations()
        val outer = findViewById<View>(R.id.orb_outer)
        val middle = findViewById<View>(R.id.orb_middle)
        val glow = findViewById<View>(R.id.orb_glow)

        addPulse(outer, 1.00f, 1.045f, 1800L)
        addPulse(middle, 1.00f, 1.065f, 1500L, 100L)
        addPulse(glow, 1.00f, 1.12f, 1200L, 180L)
        addPulse(orb, 1.00f, 1.10f, 1100L, 220L)
        addRotation(outer, -360f, 9000L)
        addRotation(middle, 360f, 7000L, 120L)
    }

    private fun stopAnimation() {
        clearAnimations()
        findViewById<View>(R.id.orb_outer).animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(180).start()
        findViewById<View>(R.id.orb_middle).animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(180).start()
        findViewById<View>(R.id.orb_glow).animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        orb.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (::speech.isInitialized) speech.destroy()
        speechHandler.removeCallbacksAndMessages(null)
        listeningByButton = true
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Listening..."
                orb.text = "●"
                startListeningAnimation()
            }
            override fun onBeginningOfSpeech() { status.text = "I'm listening..."; orb.text = "●"; startListeningAnimation() }
            override fun onRmsChanged(rmsdB: Float) {
                if (!::orb.isInitialized) return
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                val target = 1.04f + (normalized * 0.14f)
                orb.animate().scaleX(target).scaleY(target).setDuration(90L).start()
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                status.text = "Processing..."
                orb.text = "●"
                startProcessingAnimation()
            }
            override fun onError(error: Int) {
                listeningByButton = false
                stopAnimation()
                status.text = "How can I help?"
                orb.text = "●"
                startIdleAnimation()
            }
            override fun onResults(results: Bundle?) {
                listeningByButton = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                stopAnimation()
                if (text.isBlank()) {
                    status.text = "How can I help?"
                    orb.text = "●"
                    startIdleAnimation()
                    return
                }

                val command = extractBixbyCommand(text)
                if (command != null && command.isBlank()) {
                    status.text = "Bixby activated"
                    orb.text = "●"
                    speak("हाँ, बताइए")
                } else {
                    status.text = command ?: text
                    orb.text = "●"
                    startIdleAnimation()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) status.text = partial
            }
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

    private fun stopListening() {
        listeningByButton = false
        speechHandler.removeCallbacksAndMessages(null)
        if (::speech.isInitialized) {
            speech.stopListening()
            speech.cancel()
            speech.destroy()
        }
        stopAnimation()
        status.text = "How can I help?"
        orb.text = "●"
        startIdleAnimation()
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
        listeningByButton = false
        speechHandler.removeCallbacksAndMessages(null)
        clearAnimations()
        if (::speech.isInitialized) speech.destroy()
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}
