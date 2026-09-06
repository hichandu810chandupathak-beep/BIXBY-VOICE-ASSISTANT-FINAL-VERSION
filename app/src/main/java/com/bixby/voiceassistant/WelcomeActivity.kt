package com.bixby.voiceassistant
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bixby_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("setup_complete", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(80, 80, 80, 80) }
        layout.addView(TextView(this).apply { text = "Welcome to Bixby"; textSize = 32f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, 60) })
        layout.addView(Button(this).apply { text = "Get Started"; setOnClickListener { 
            prefs.edit().putBoolean("setup_complete", true).apply()
            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java).apply { putExtra("start_voice_after_welcome", true) })
            finish() 
        } })
        setContentView(layout)
    }
}
