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

        val textView = TextView(this)
        textView.text = crashLog
        textView.textSize = 14f
        textView.setTextColor(Color.WHITE)
        textView.setBackgroundColor(Color.BLACK)
        textView.setPadding(24, 24, 24, 24)
        textView.isTextSelectable = true

        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(Color.BLACK)
        scrollView.addView(textView)
        setContentView(scrollView)
    }
}
