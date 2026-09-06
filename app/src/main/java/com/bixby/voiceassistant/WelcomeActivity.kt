package com.bixby.voiceassistant
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bixby_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("setup_complete", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish(); return
        }
        
        val layout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; 
            setPadding(dp(30), dp(50), dp(30), dp(50))
            setBackgroundColor(Color.parseColor("#08080A")) // Premium Bixby Dark
        }
        
        layout.addView(TextView(this).apply { 
            text = "Meet your Bixby"; textSize = 34f; 
            setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        
        layout.addView(TextView(this).apply { 
            text = "Samsung's intelligent assistant is here to help you get things done faster and easier.\n\nSet up your preferences, connect your accounts, and tap the mic to unleash the power of Gemini AI."; 
            textSize = 16f; setTextColor(Color.parseColor("#A0A0A0")); gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(60))
        })
        
        layout.addView(Button(this).apply { 
            text = "Start Setup"; setTextColor(Color.WHITE); textSize = 16f
            setBackgroundColor(Color.parseColor("#3478F6")) // Bixby Blue
            setOnClickListener { 
                prefs.edit().putBoolean("setup_complete", true).apply()
                startActivity(Intent(this@WelcomeActivity, MainActivity::class.java).apply { putExtra("start_voice_after_welcome", true) })
                finish() 
            } 
        }, LinearLayout.LayoutParams(dp(220), dp(55)))
        
        setContentView(layout)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}