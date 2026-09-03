package com.bixby.voiceassistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var speech: SpeechRecognizer
    private lateinit var status: TextView
    private lateinit var orb: TextView
    private lateinit var tts: TextToSpeech
    private val animationSet = mutableListOf<ObjectAnimator>()
    private var listeningByButton = false
    private var launchedByHotword = false
    private val speechHandler = Handler(Looper.getMainLooper())
    private val gemini: GeminiApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder().baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(GeminiApi::class.java)
    }
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestAssistantRoleIfAvailable()
            if (launchedByHotword) speechHandler.postDelayed({ listen() }, 250L)
        }
    }
    private val assistantRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText); orb = findViewById(R.id.voiceOrb)
        if (!isSupportedDevice()) { status.text = "Device not supported"; orb.text = "●"; return }
        launchedByHotword = intent.getBooleanExtra("HOTWORD_TRIGGERED", false)
        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { runOnUiThread { status.text = "Speaking..."; startListeningAnimation() } }
                override fun onDone(utteranceId: String?) { runOnUiThread { finishVoiceTurn() } }
                override fun onError(utteranceId: String?) { runOnUiThread { finishVoiceTurn() } }
            })
        }
        findViewById<ImageButton>(R.id.micButton).setOnClickListener { if (listeningByButton) stopListening() else listen() }
        startIdleAnimation()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            requestAssistantRoleIfAvailable()
            if (launchedByHotword) speechHandler.postDelayed({ listen() }, 300L)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        if (intent.getBooleanExtra("HOTWORD_TRIGGERED", false)) {
            launchedByHotword = true
            speechHandler.postDelayed({ listen() }, 200L)
        }
    }

    private fun finishVoiceTurn() {
        listeningByButton = false; status.text = "How can I help?"; orb.text = "●"; startIdleAnimation()
        if (launchedByHotword) {
            speechHandler.postDelayed({ HotwordListeningService.start(this) }, 300L)
            launchedByHotword = false
        }
    }

    private fun isSupportedDevice() = Build.MANUFACTURER.equals("samsung", true) && Build.MODEL.equals("SM-A166P", true)

    private fun requestAssistantRoleIfAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT))
            assistantRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
    }

    private fun clearAnimations() { animationSet.forEach { it.cancel() }; animationSet.clear() }
    private fun addPulse(v: View, min: Float, max: Float, duration: Long, delay: Long = 0L) {
        ObjectAnimator.ofFloat(v, View.SCALE_X, min, max, min).apply { this.duration=duration; startDelay=delay; repeatCount=ValueAnimator.INFINITE; animationSet.add(this); start() }
        ObjectAnimator.ofFloat(v, View.SCALE_Y, min, max, min).apply { this.duration=duration; startDelay=delay; repeatCount=ValueAnimator.INFINITE; animationSet.add(this); start() }
    }
    private fun addRotation(v: View, degrees: Float, duration: Long, delay: Long = 0L) {
        ObjectAnimator.ofFloat(v, View.ROTATION, 0f, degrees).apply { this.duration=duration; startDelay=delay; repeatCount=ValueAnimator.INFINITE; animationSet.add(this); start() }
    }
    private fun startIdleAnimation() {
        clearAnimations(); val o=findViewById<View>(R.id.orb_outer); val m=findViewById<View>(R.id.orb_middle); val g=findViewById<View>(R.id.orb_glow)
        addPulse(o,1f,1.035f,2600); addPulse(m,1f,1.055f,2200,120); addPulse(g,1f,1.10f,1800,220); addPulse(orb,1f,1.07f,1600,280); addRotation(o,-360f,14000); addRotation(m,360f,10000,180)
    }
    private fun startListeningAnimation() {
        clearAnimations(); val o=findViewById<View>(R.id.orb_outer); val m=findViewById<View>(R.id.orb_middle); val g=findViewById<View>(R.id.orb_glow)
        addPulse(o,1f,1.07f,1000); addPulse(m,1f,1.10f,850,80); addPulse(g,1f,1.16f,700,140); addPulse(orb,1f,1.16f,700,180); addRotation(o,-360f,4200); addRotation(m,360f,3000,100)
    }
    private fun startProcessingAnimation() {
        clearAnimations(); val o=findViewById<View>(R.id.orb_outer); val m=findViewById<View>(R.id.orb_middle); val g=findViewById<View>(R.id.orb_glow)
        addPulse(o,1f,1.045f,1800); addPulse(m,1f,1.065f,1500,100); addPulse(g,1f,1.12f,1200,180); addPulse(orb,1f,1.10f,1100,220); addRotation(o,-360f,9000); addRotation(m,360f,7000,120)
    }
    private fun stopAnimation() {
        clearAnimations(); findViewById<View>(R.id.orb_outer).animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(180).start(); findViewById<View>(R.id.orb_middle).animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(180).start(); findViewById<View>(R.id.orb_glow).animate().scaleX(1f).scaleY(1f).setDuration(180).start(); orb.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status.text="Voice recognition unavailable"; return }
        if (::speech.isInitialized) speech.destroy()
        speechHandler.removeCallbacksAndMessages(null); listeningByButton=true; speech=SpeechRecognizer.createSpeechRecognizer(this)
        speech.setRecognitionListener(object: RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { status.text="Listening..."; startListeningAnimation() }
            override fun onBeginningOfSpeech() { status.text="I'm listening..."; startListeningAnimation() }
            override fun onRmsChanged(rms: Float) { val n=((rms+2f)/12f).coerceIn(0f,1f); val s=1.04f+n*.14f; orb.animate().scaleX(s).scaleY(s).setDuration(90).start() }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { status.text="Processing..."; startProcessingAnimation() }
            override fun onError(error: Int) { listeningByButton=false; stopAnimation(); status.text="How can I help?"; startIdleAnimation(); if(launchedByHotword) speechHandler.postDelayed({ HotwordListeningService.start(this@MainActivity); launchedByHotword=false },300) }
            override fun onResults(r: Bundle?) {
                listeningByButton=false; val text=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim(); stopAnimation()
                if(text.isBlank()){ if(launchedByHotword){speechHandler.postDelayed({HotwordListeningService.start(this@MainActivity); launchedByHotword=false},300)} else {status.text="How can I help?";startIdleAnimation()}; return }
                val command=extractBixbyCommand(text)
                if(command!=null && command.isBlank()){status.text="Bixby activated"; speak("हाँ, बताइए"); return}
                val request=command ?: text; val local=CommandExecutor.execute(this@MainActivity,request)
                if(local!=null){status.text=local; speak(local)} else askGemini(request)
            }
            override fun onPartialResults(r: Bundle?) { val p=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty(); if(p.isNotBlank()) status.text=p }
            override fun onEvent(t:Int,p:Bundle?) {}
        })
        val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hi-IN"); putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"hi-IN"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3) }
        try{speech.startListening(i)}catch(_:Exception){listeningByButton=false;speech.destroy();stopAnimation();status.text="How can I help?";startIdleAnimation()}
    }

    private fun askGemini(userText:String){
        val key=BuildConfig.GEMINI_API_KEY.trim(); if(key.isBlank()){status.text="API key not configured";speak("Gemini API key अभी configure नहीं है।");startIdleAnimation();return}
        status.text="Thinking...";startProcessingAnimation(); val prompt="You are Bixby, a concise Android voice assistant for a Samsung Galaxy A16 5G. Reply naturally. Prefer Hindi for Hindi/Hinglish and English for English. Keep spoken answers short. User request: $userText"
        gemini.generateContent(model="gemini-3.7-flash",apiKey=key,request=GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(prompt)))))).enqueue(object:Callback<GeminiResponse>{
            override fun onResponse(c:Call<GeminiResponse>,r:Response<GeminiResponse>){val a=r.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim().orEmpty();runOnUiThread{if(!r.isSuccessful||a.isBlank()){status.text="I couldn't complete that.";speak("माफ कीजिए, अभी उसका जवाब नहीं मिल पाया।")}else{status.text=a;speak(a)};startIdleAnimation()}}
            override fun onFailure(c:Call<GeminiResponse>,t:Throwable){runOnUiThread{status.text="Connection problem";speak("अभी इंटरनेट कनेक्शन में समस्या है।");startIdleAnimation()}}
        })
    }

    private fun stopListening(){listeningByButton=false;speechHandler.removeCallbacksAndMessages(null);if(::speech.isInitialized){speech.stopListening();speech.cancel();speech.destroy()};stopAnimation();status.text="How can I help?";startIdleAnimation()}
    private fun extractBixbyCommand(text:String):String?{val n=text.trim().lowercase(Locale.ROOT).replace(Regex("[\\p{Punct}]+")," ").replace(Regex("\\s+")," ").trim();return when{n=="bixby"->"";n.startsWith("bixby ")->text.trim().substring(5).trim();n=="hey bixby"->"";n.startsWith("hey bixby ")->text.trim().substring(10).trim();n=="हे बिक्सबी"->"";n.startsWith("हे बिक्सबी ")->text.trim().substring(10).trim();n=="है बिक्सबी"->"";n.startsWith("है बिक्सबी ")->text.trim().substring(10).trim();else->null}}
    private fun speak(text:String){if(!::tts.isInitialized)return;val l=if(text.any{it in '\u0900'..'\u097F'})Locale.forLanguageTag("hi-IN") else Locale.forLanguageTag("en-US");val r=tts.setLanguage(l);if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)tts.setLanguage(Locale.forLanguageTag("en-US"));tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"BIXBY_RESPONSE")}
    override fun onDestroy(){listeningByButton=false;speechHandler.removeCallbacksAndMessages(null);clearAnimations();if(::speech.isInitialized)speech.destroy();if(::tts.isInitialized){tts.stop();tts.shutdown()};super.onDestroy()}
}
