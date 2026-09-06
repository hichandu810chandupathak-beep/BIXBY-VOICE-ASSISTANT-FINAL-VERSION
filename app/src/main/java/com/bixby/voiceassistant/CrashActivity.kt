package com.bixby.voiceassistant

import android.graphics.Color
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashLog = intent.getStringExtra("crash_log") ?: "No crash log available."
        val textView = TextView(this).apply {
            text = crashLog
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
            isTextSelectable = true
        }
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(textView)
        }
        setContentView(scrollView)
    }
}
