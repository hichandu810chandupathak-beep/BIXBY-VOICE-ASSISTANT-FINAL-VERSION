package com.bixby.voiceassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private companion object { const val REQUEST_PERMISSIONS = 1001; const val TYPING_DELAY_MS = 24L; const val EXTRA_START_VOICE = "start_voice_after_welcome" }
    private lateinit var responseSheet: LinearLayout; private lateinit var status: TextView; private lateinit var responseContent: TextView; private lateinit var commandInput: EditText; private lateinit var orbGlow: ImageView
    private var speechRecognizer: SpeechRecognizer? = null; private var textToSpeech: TextToSpeech? = null; private var isListening = false; private var pendingResponse: String? = null; private var pendingTtsLocale: Locale = Locale("hi", "IN"); private var typingRunnable: Runnable? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper()); private val aiHandler by lazy { AssistantAiHandler(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
        if (intent.getBooleanExtra(EXTRA_START_VOICE, false)) startVoiceAfterBackgroundSetup()
    }

    private fun setupUi() {
        setContentView(R.layout.activity_main)
        responseSheet = findViewById(R.id.responseSheet); status = findViewById(R.id.tvAssistantStatus); responseContent = findViewById(R.id.tvResponseContent); commandInput = findViewById(R.id.etCommandInput); orbGlow = findViewById(R.id.orbGlow)
        applySystemBarInsets(); startOrbPulse()
        findViewById<View>(R.id.btnKeyboard).setOnClickListener { toggleKeyboardInput() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        commandInput.setOnEditorActionListener { _, actionId, event -> val submit = actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN); if (submit) { val text = commandInput.text.toString().trim(); if (text.isNotEmpty()) { hideKeyboardInput(); status.text = "Thinking..."; handleRecognizedText(text) }; true } else false }
        findViewById<View>(R.id.btnMicTrigger).setOnClickListener { if (isListening) stopListening() else requestPermissionsAndListen() }
        findViewById<View>(R.id.floatingBar).setOnClickListener { toggleResponseSheet() }
    }

    private fun hasRecordAudioPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun hasNotificationPermission() = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun applySystemBarInsets() { val settings = findViewById<View>(R.id.btnSettings); settings.setOnApplyWindowInsetsListener { view, insets -> val top = insets.getInsets(WindowInsets.Type.statusBars()).top; val lp = view.layoutParams as android.widget.FrameLayout.LayoutParams; lp.topMargin = top + dp(8); view.layoutParams = lp; insets }; settings.requestApplyInsets() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun requestPermissionsAndListen() {
        val needed = buildList { if (!hasRecordAudioPermission()) add(Manifest.permission.RECORD_AUDIO); if (!hasNotificationPermission() && android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS); if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA); if (android.os.Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS) else startVoiceAfterBackgroundSetup()
    }

    private fun startVoiceAfterBackgroundSetup() {
        lifecycleScope.launch(Dispatchers.IO) {
            val micGranted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val notificationGranted = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!micGranted || !notificationGranted) { withContext(Dispatchers.Main) { if (::status.isInitialized) status.text = "Microphone permission needed" }; return@launch }
            withContext(Dispatchers.Main) {
                try {
                    setupSpeechRecognizer()
                    HotwordListeningService.start(this@MainActivity)
                    startListening()
                } catch (e: Exception) {
                    android.util.Log.e("BixbyCrash", "Manual voice activation failed", e)
                    if (::status.isInitialized) status.text = "Speech recognition unavailable"
                }
            }
        }
    }

    private fun toggleKeyboardInput() { stopListening(); if (commandInput.visibility == View.VISIBLE) { hideKeyboardInput(); return }; status.visibility = View.GONE; commandInput.visibility = View.VISIBLE; commandInput.requestFocus(); commandInput.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT) } }
    private fun hideKeyboardInput() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(commandInput.windowToken, 0); commandInput.clearFocus(); commandInput.visibility = View.GONE; status.visibility = View.VISIBLE; status.text = "Listening..." }
    private fun setupSpeechRecognizer() {
        if (speechRecognizer != null || !hasRecordAudioPermission()) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { if (::status.isInitialized) status.text = "Speech recognition unavailable"; return }
        try { speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(object : RecognitionListener { override fun onReadyForSpeech(params: Bundle?) { isListening = true; if (::status.isInitialized) status.text = "Listening..." }; override fun onBeginningOfSpeech() { if (::status.isInitialized) status.text = "Listening..." }; override fun onRmsChanged(rmsdB: Float) = Unit; override fun onBufferReceived(buffer: ByteArray?) = Unit; override fun onEndOfSpeech() { isListening = false; if (::status.isInitialized) status.text = "Thinking..." }; override fun onPartialResults(partialResults: Bundle?) { partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (::status.isInitialized) status.text = it } }; override fun onResults(results: Bundle?) { isListening = false; val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if (text.isNullOrBlank()) { if (::status.isInitialized) status.text = "I didn't catch that"; return }; if (::status.isInitialized) status.text = "Thinking..."; handleRecognizedText(text) }; override fun onError(error: Int) { isListening = false; if (::status.isInitialized) status.text = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "Didn't catch that" else "Try again" }; override fun onEvent(eventType: Int, params: Bundle?) = Unit }) } } catch (e: Exception) { android.util.Log.e("BixbyCrash", "SpeechRecognizer initialization failed", e); speechRecognizer = null; if (::status.isInitialized) status.text = "Speech recognition unavailable" }
    }
    private fun startListening() { if (!hasRecordAudioPermission()) { if (::status.isInitialized) status.text = "Microphone permission needed"; return }; if (speechRecognizer == null) setupSpeechRecognizer(); hideKeyboardInput(); val recognizer = speechRecognizer ?: return; val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN"); putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3) }; if (::status.isInitialized) status.text = "Listening..."; isListening = true; try { recognizer.startListening(intent) } catch (e: Exception) { android.util.Log.e("BixbyCrash", "SpeechRecognizer start failed", e); isListening = false; if (::status.isInitialized) status.text = "Try again" } }
    private fun stopListening() { try { speechRecognizer?.cancel() } catch (e: Exception) { android.util.Log.e("BixbyCrash", "SpeechRecognizer cancel failed", e) }; isListening = false; if (::status.isInitialized) status.text = "Listening..." }
    private fun handleRecognizedText(userText: String) { lifecycleScope.launch(Dispatchers.IO) { val result = aiHandler.generateResponse(userText); withContext(Dispatchers.Main) { if (isFinishing || isDestroyed) return@withContext; result.fold(onSuccess = { response -> if (::status.isInitialized) status.text = "Answering..."; showResponseWithTyping(response); speakResponse(response, userText) }, onFailure = { error -> val message = if (error is AssistantAiHandler.MissingGeminiApiKeyException) "I am sorry, I cannot connect right now." else "I am sorry, I am having trouble connecting right now."; if (::status.isInitialized) status.text = message; showResponseWithTyping(message); speakResponse(message, userText) }) } } }
    private fun showResponseWithTyping(response: String) { typingRunnable?.let(mainHandler::removeCallbacks); responseSheet.visibility = View.VISIBLE; responseSheet.alpha = 1f; responseSheet.post { responseSheet.translationY = responseSheet.height.toFloat(); responseSheet.animate().translationY(0f).alpha(1f).setDuration(280L).start() }; responseContent.text = ""; var index = 0; val typeNext = object : Runnable { override fun run() { if (index >= response.length) { typingRunnable = null; return }; responseContent.append(response[index++].toString()); mainHandler.postDelayed(this, TYPING_DELAY_MS) } }; typingRunnable = typeNext; mainHandler.post(typeNext) }
    private fun speakResponse(response: String, userText: String = "") { if (textToSpeech == null) { try { textToSpeech = TextToSpeech(this, this) } catch (e: Exception) { android.util.Log.e("BixbyCrash", "TextToSpeech initialization failed", e); return } }; val tts = textToSpeech ?: return; val locale = chooseTtsLocale(userText); pendingTtsLocale = locale; tts.language = locale; if (tts.isSpeaking) tts.stop(); startOrbPulse(); tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "bixby_response") }
    private fun chooseTtsLocale(userText: String): Locale { val hasDevanagari = userText.any { it in '\u0900'..'\u097F' }; val lower = userText.lowercase(); val likelyHindi = hasDevanagari || listOf("kya", "kaise", "hai", "haan", "nahi", "nahin", "mujhe", "mera", "meri", "aap", "tum", "kar", "karo", "chahiye", "batao", "dikhao", "kholo", "band", "chalu", "chaloo").count { lower.contains(it) } >= 1; return if (likelyHindi) Locale("hi", "IN") else Locale("en", "US") }
    private fun startOrbPulse() { if (::orbGlow.isInitialized) { orbGlow.clearAnimation(); orbGlow.startAnimation(AnimationUtils.loadAnimation(this, R.anim.orb_pulse)) } }
    private fun stopOrbPulse() { if (::orbGlow.isInitialized) { orbGlow.clearAnimation() } }
    private fun toggleResponseSheet() { if (responseSheet.visibility == View.VISIBLE) responseSheet.animate().translationY(responseSheet.height.toFloat()).alpha(0f).setDuration(220L).withEndAction { responseSheet.visibility = View.GONE; responseSheet.translationY = 0f; responseSheet.alpha = 1f }.start() else { responseSheet.translationY = responseSheet.height.toFloat(); responseSheet.alpha = 0f; responseSheet.visibility = View.VISIBLE; responseSheet.animate().translationY(0f).alpha(1f).setDuration(280L).start() } }
    override fun onInit(statusCode: Int) { if (statusCode == TextToSpeech.SUCCESS) textToSpeech?.language = Locale("hi", "IN"); textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() { override fun onStart(utteranceId: String?) = runOnUiThread { startOrbPulse() }; override fun onDone(utteranceId: String?) = runOnUiThread { stopOrbPulse() }; override fun onError(utteranceId: String?) = runOnUiThread { stopOrbPulse() } }); pendingResponse?.let { pendingResponse = null; textToSpeech?.language = pendingTtsLocale; textToSpeech?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "bixby_response") } }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQUEST_PERMISSIONS) { if (hasRecordAudioPermission() && hasNotificationPermission()) startVoiceAfterBackgroundSetup() else if (::status.isInitialized) status.text = "Microphone permission needed" } }
    override fun onPause() { try { speechRecognizer?.cancel() } catch (e: Exception) { android.util.Log.e("BixbyCrash", "SpeechRecognizer cancel failed", e) }; try { speechRecognizer?.destroy() } catch (e: Exception) { android.util.Log.e("BixbyCrash", "SpeechRecognizer destroy failed", e) }; speechRecognizer = null; isListening = false; stopService(Intent(this, HotwordListeningService::class.java)); super.onPause() }
    override fun onDestroy() { typingRunnable?.let(mainHandler::removeCallbacks); try { speechRecognizer?.cancel() } catch (e: Exception) { android.util.Log.e("BixbyCrash", "SpeechRecognizer cancel failed", e) }; try { speechRecognizer?.destroy() } catch (e: Exception) { android.util.Log.e("BixbyCrash", "SpeechRecognizer destroy failed", e) }; speechRecognizer = null; stopService(Intent(this, HotwordListeningService::class.java)); try { textToSpeech?.stop(); textToSpeech?.shutdown() } catch (e: Exception) { android.util.Log.e("BixbyCrash", "TTS shutdown failed", e) }; textToSpeech = null; stopOrbPulse(); mainHandler.removeCallbacksAndMessages(null); super.onDestroy() }
}
