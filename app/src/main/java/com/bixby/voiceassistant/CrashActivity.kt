package com.bixby.voiceassistant // Ensure this matches your project's package

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashLog = intent.getStringExtra("crash_log") ?: "No crash log found."
        
        val scrollView = ScrollView(this)
        val textView = TextView(this)
        textView.text = crashLog
        
        scrollView.addView(textView)
        setContentView(scrollView)
    }
}
