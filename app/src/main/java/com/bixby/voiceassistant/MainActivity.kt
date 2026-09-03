package com.bixby.voiceassistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
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
import android.speech.tts.Voice
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private lateinit var homeContent: LinearLayout
    private lateinit var listeningContent: LinearLayout
    private lateinit var conversationContent: ScrollView
    private lateinit var userBubble: TextView
    private lateinit var assistantBubble: TextView
    private lateinit var weatherCard: LinearLayout
    private val animationSet = mutableListOf<ObjectAnimator>()
    private val speechHandler = Handler(Looper.getMainLooper())
    private var listeningByButton = false
    private var launchedByHotword = false
    private var voiceTurnInProgress = false
    private var ignoreNextMicTap = false
    private val gemini: GeminiApi by lazy { val moshi=Moshi.Builder().add(KotlinJsonAdapterFactory()).build(); Retrofit.Builder().baseUrl("https://generativelanguage.googleapis.com/").addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(GeminiApi::class.java) }
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted){requestAssistantRoleIfAvailable();if(launchedByHotword)speechHandler.postDelayed({listen()},250L)} }
    private val assistantRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    override fun onCreate(savedInstanceState:Bundle?){
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);super.onCreate(savedInstanceState);setContentView(R.layout.activity_main)
        status=findViewById(R.id.statusText);orb=findViewById(R.id.voiceOrb);homeContent=findViewById(R.id.homeContent);listeningContent=findViewById(R.id.listeningContent);conversationContent=findViewById(R.id.conversationContent);userBubble=findViewById(R.id.userBubble);assistantBubble=findViewById(R.id.assistantBubble);weatherCard=findViewById(R.id.weatherCard)
        if(!isSupportedDevice()){status.text="Device not supported";return};launchedByHotword=intent.getBooleanExtra("HOTWORD_TRIGGERED",false)
        tts=TextToSpeech(this){result->if(result==TextToSpeech.SUCCESS){tts.setSpeechRate(.96f);selectMaleVoice(Locale.forLanguageTag("hi-IN"));tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String?){runOnUiThread{status.text="Speaking..."}};override fun onDone(id:String?){runOnUiThread{finishVoiceTurn()}};override fun onError(id:String?){runOnUiThread{finishVoiceTurn()}}})}}
        findViewById<ImageButton>(R.id.micButton).setOnClickListener{if(ignoreNextMicTap){ignoreNextMicTap=false;return@setOnClickListener};if(voiceTurnInProgress)return@setOnClickListener;if(listeningByButton)stopListening()else listen()};findViewById<TextView>(R.id.cancelButton).setOnClickListener{stopListening()};setupUiActions();startIdleAnimation()
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)else{requestAssistantRoleIfAvailable();if(launchedByHotword)speechHandler.postDelayed({listen()},300L)}
    }
    private fun setupUiActions(){
        findViewById<TextView>(R.id.menuButton).setOnClickListener{showMenu()}
        findViewById<TextView>(R.id.settingsButton).setOnClickListener{showSettings()}
        findViewById<TextView>(R.id.actionYoutube).setOnClickListener{runQuickCommand("open YouTube")}
        findViewById<TextView>(R.id.actionMusic).setOnClickListener{runQuickCommand("play music")}
        findViewById<TextView>(R.id.actionWeather).setOnClickListener{runQuickCommand("what's the weather today")}
        findViewById<TextView>(R.id.actionAlarm).setOnClickListener{runQuickCommand("set an alarm")}
    }
    private fun showMenu(){
        AlertDialog.Builder(this).setTitle("Bixby").setItems(arrayOf("Home","Conversation","Voice commands","Accessibility controls")){_,which->when(which){0->showHome();1->if(conversationContent.visibility!=View.VISIBLE)showHome();2->showCommandHelp();3->openAccessibilitySettings()}}.setNegativeButton("Close",null).show()
    }
    private fun showSettings(){
        AlertDialog.Builder(this).setTitle("Bixby Settings").setItems(arrayOf("Assistant & voice","Accessibility controls","App settings")){_,which->when(which){0->requestAssistantRoleIfAvailable();1->openAccessibilitySettings();2->startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply{data=android.net.Uri.parse("package:$packageName")})}}.setNegativeButton("Close",null).show()
    }
    private fun showCommandHelp(){
        AlertDialog.Builder(this).setTitle("Voice commands").setMessage("Try: Open YouTube\nTurn on flashlight\nTurn Wi-Fi on\nScroll down\nGo home\nOpen settings\nSet an alarm").setPositiveButton("OK",null).show()
    }
    private fun openAccessibilitySettings(){startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))}
    private fun runQuickCommand(request:String){
        val local=CommandExecutor.execute(this,request)
        if(local!=null){showConversation(request,local);speak(local)}else{showConversation(request);askGemini(request)}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("HOTWORD_TRIGGERED",false)){launchedByHotword=true;speechHandler.postDelayed({listen()},200L)}}
    private fun isSupportedDevice()=Build.MANUFACTURER.equals("samsung",true)&&Build.MODEL.equals("SM-A166P",true)
    private fun requestAssistantRoleIfAvailable(){if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q)return;val rm=getSystemService(RoleManager::class.java)?:return;if(rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)&&!rm.isRoleHeld(RoleManager.ROLE_ASSISTANT))assistantRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))}
    private fun showHome(){homeContent.visibility=View.VISIBLE;listeningContent.visibility=View.GONE;conversationContent.visibility=View.GONE;status.text="How can I help you?";findViewById<TextView>(R.id.actionHint).text="Ask Bixby anything"}
    private fun showListening(){homeContent.visibility=View.GONE;listeningContent.visibility=View.VISIBLE;conversationContent.visibility=View.GONE;status.text="Listening...";findViewById<TextView>(R.id.listeningTitle).text="Listening...";findViewById<TextView>(R.id.actionHint).text="Listening for your voice"}
    private fun showConversation(question:String,answer:String?=null){homeContent.visibility=View.GONE;listeningContent.visibility=View.GONE;conversationContent.visibility=View.VISIBLE;userBubble.text=question;if(answer!=null)assistantBubble.text=answer;weatherCard.visibility=if(question.lowercase(Locale.ROOT).contains("weather")||question.contains("मौसम"))View.VISIBLE else View.GONE;conversationContent.post{conversationContent.fullScroll(View.FOCUS_DOWN)};findViewById<TextView>(R.id.actionHint).text="Ask Bixby anything"}
    private fun finishVoiceTurn(){voiceTurnInProgress=false;listeningByButton=false;stopAnimation();if(launchedByHotword){speechHandler.postDelayed({HotwordListeningService.start(this)},300L);launchedByHotword=false}else if(conversationContent.visibility!=View.VISIBLE)showHome()}
    private fun clearAnimations(){animationSet.forEach{it.cancel()};animationSet.clear()}
    private fun addPulse(v:View,min:Float,max:Float,duration:Long,delay:Long=0L){ObjectAnimator.ofFloat(v,View.SCALE_X,min,max,min).apply{this.duration=duration;startDelay=delay;repeatCount=ValueAnimator.INFINITE;animationSet.add(this);start()};ObjectAnimator.ofFloat(v,View.SCALE_Y,min,max,min).apply{this.duration=duration;startDelay=delay;repeatCount=ValueAnimator.INFINITE;animationSet.add(this);start()}}
    private fun addRotation(v:View,degrees:Float,duration:Long,delay:Long=0L){ObjectAnimator.ofFloat(v,View.ROTATION,0f,degrees).apply{this.duration=duration;startDelay=delay;repeatCount=ValueAnimator.INFINITE;animationSet.add(this);start()}}
    private fun startIdleAnimation(){clearAnimations();val o=findViewById<View>(R.id.orb_outer);val m=findViewById<View>(R.id.orb_middle);val g=findViewById<View>(R.id.orb_glow);addPulse(o,1f,1.025f,2600);addPulse(m,1f,1.04f,2200,120);addPulse(g,1f,1.08f,1800,220);addPulse(orb,1f,1.045f,1600,280);addRotation(o,-360f,14000);addRotation(m,360f,10000,180)}
    private fun startListeningAnimation(){clearAnimations();val o=findViewById<View>(R.id.listenOuter);val m=findViewById<View>(R.id.listenMiddle);val g=findViewById<View>(R.id.listenGlow);addPulse(o,1f,1.07f,1000);addPulse(m,1f,1.10f,850,80);addPulse(g,1f,1.16f,700,140);addRotation(o,-360f,4200);addRotation(m,360f,3000,100)}
    private fun startProcessingAnimation(){startListeningAnimation()}
    private fun stopAnimation(){clearAnimations();listOf(R.id.orb_outer,R.id.orb_middle,R.id.orb_glow,R.id.voiceOrb,R.id.listenOuter,R.id.listenMiddle,R.id.listenGlow).forEach{findViewById<View>(it)?.animate()?.scaleX(1f)?.scaleY(1f)?.rotation(0f)?.setDuration(180)?.start()}}
    private fun listen(){
        if(voiceTurnInProgress)return;if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return;if(!SpeechRecognizer.isRecognitionAvailable(this)){status.text="Voice recognition unavailable";return};voiceTurnInProgress=true;listeningByButton=true;speechHandler.removeCallbacksAndMessages(null);showListening();startListeningAnimation();if(::speech.isInitialized){try{speech.setRecognitionListener(null)}catch(_:Exception){};try{speech.cancel()}catch(_:Exception){};speech.destroy()};speech=SpeechRecognizer.createSpeechRecognizer(this)
        speech.setRecognitionListener(object:RecognitionListener{override fun onReadyForSpeech(p:Bundle?){status.text="Listening..."};override fun onBeginningOfSpeech(){status.text="Listening..."};override fun onRmsChanged(rms:Float){val n=((rms+2f)/12f).coerceIn(0f,1f);val s=1.04f+n*.14f;findViewById<View>(R.id.listenGlow)?.animate()?.scaleX(s)?.scaleY(s)?.setDuration(90)?.start()};override fun onBufferReceived(b:ByteArray?){ };override fun onEndOfSpeech(){status.text="Processing...";startProcessingAnimation()};override fun onError(error:Int){finishVoiceTurn()};override fun onResults(r:Bundle?){listeningByButton=false;val text=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim();if(text.isBlank()){finishVoiceTurn();return};val command=extractBixbyCommand(text);if(command!=null&&command.isBlank()){showConversation(text,"हाँ, बताइए");speak("हाँ, बताइए");return};val request=command?:text;val local=CommandExecutor.execute(this@MainActivity,request);if(local!=null){showConversation(request,local);speak(local)}else askGemini(request)};override fun onPartialResults(r:Bundle?){val p=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty();if(p.isNotBlank())status.text=p};override fun onEvent(t:Int,p:Bundle?){ }})
        val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hi-IN");putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"hi-IN");putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)};try{speech.startListening(i)}catch(_:Exception){finishVoiceTurn()}
    }
    private fun askGemini(userText:String){val key=BuildConfig.GEMINI_API_KEY.trim();if(key.isBlank()){showConversation(userText,"Gemini API key अभी configure नहीं है।");speak("Gemini API key अभी configure नहीं है।");finishVoiceTurn();return};showConversation(userText);status.text="Thinking...";startProcessingAnimation();val prompt="You are Bixby, a concise and reliable Android voice assistant for a Samsung Galaxy A16 5G. Answer only the user's actual request. Never claim a device action happened unless the app explicitly executed it. Prefer Hindi for Hindi/Hinglish and English for English. Do not invent device state. No unrelated suggestions. Keep spoken answers short and natural. User request: $userText";gemini.generateContent("gemini-3.8-flash",key,GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(prompt)))),GeminiGenerationConfig(temperature=.2,maxOutputTokens=256))).enqueue(object:Callback<GeminiResponse>{override fun onResponse(c:Call<GeminiResponse>,r:Response<GeminiResponse>){val a=r.body()?.candidates.orEmpty().asSequence().flatMap{it.content?.parts.orEmpty().asSequence()}.map{it.text.trim()}.firstOrNull{it.isNotBlank()}.orEmpty();runOnUiThread{if(!r.isSuccessful||a.isBlank()){assistantBubble.text="माफ कीजिए, अभी उसका जवाब नहीं मिल पाया।";speak("माफ कीजिए, अभी उसका जवाब नहीं मिल पाया।")}else{assistantBubble.text=a;speak(a)}}};override fun onFailure(c:Call<GeminiResponse>,t:Throwable){runOnUiThread{assistantBubble.text="अभी इंटरनेट कनेक्शन में समस्या है।";speak("अभी इंटरनेट कनेक्शन में समस्या है।")}}})}
    private fun selectMaleVoice(locale:Locale){try{val candidates: List<Voice> = tts.voices.orEmpty().filter{it.locale.language==locale.language}.sortedWith(compareByDescending<Voice>{val n=it.name.lowercase(Locale.ROOT);if(n.contains("male")||n.contains("man")||n.contains("masculine"))1 else 0}.thenByDescending{it.locale==locale});candidates.firstOrNull()?.let{tts.setVoice(it)};tts.setLanguage(locale)}catch(_:Exception){tts.setLanguage(locale)}}
    private fun speak(text:String){if(!::tts.isInitialized||text.isBlank())return;val locale=if(text.any{it in '\u0900'..'\u097F'})Locale.forLanguageTag("hi-IN")else Locale.forLanguageTag("en-US");selectMaleVoice(locale);tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"BIXBY_RESPONSE")}
    private fun stopListening(){if(!voiceTurnInProgress&&!listeningByButton)return;voiceTurnInProgress=false;listeningByButton=false;speechHandler.removeCallbacksAndMessages(null);if(::speech.isInitialized){try{speech.setRecognitionListener(null)}catch(_:Exception){};try{speech.stopListening()}catch(_:Exception){};try{speech.cancel()}catch(_:Exception){};speech.destroy()};stopAnimation();showHome()}
    private fun extractBixbyCommand(text:String):String?{val n=text.trim().lowercase(Locale.ROOT).replace(Regex("[\\p{Punct}]+")," ").replace(Regex("\\s+")," ").trim();return when{n=="bixby"->"";n.startsWith("bixby ")->text.trim().substring(5).trim();n=="hey bixby"->"";n.startsWith("hey bixby ")->text.trim().substring(10).trim();n=="हे बिक्सबी"->"";n.startsWith("हे बिक्सबी ")->text.trim().substring(10).trim();n=="है बिक्सबी"->"";n.startsWith("है बिक्सबी ")->text.trim().substring(10).trim();else->null}}
    override fun onDestroy(){voiceTurnInProgress=false;listeningByButton=false;speechHandler.removeCallbacksAndMessages(null);clearAnimations();if(::speech.isInitialized)speech.destroy();if(::tts.isInitialized){tts.stop();tts.shutdown()};super.onDestroy()}
}
