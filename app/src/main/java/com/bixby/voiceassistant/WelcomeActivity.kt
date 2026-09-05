package com.bixby.voiceassistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WelcomeActivity : Activity() {
    private companion object { const val REQUEST_ONBOARDING = 7001 }
    private val prefs by lazy { getSharedPreferences("bixby_onboarding", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.getBoolean("completed", false)) { openMain(); return }
        buildWelcome()
    }

    private fun buildWelcome() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(48), dp(28), dp(40)); setBackgroundColor(android.graphics.Color.rgb(8, 8, 10)) }
        root.addView(TextView(this).apply { text = "Bixby"; textSize = 42f; gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply { text = "Your conversational voice assistant"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(android.graphics.Color.LTGRAY); setPadding(0, dp(12), 0, dp(8)) }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply { text = "Talk naturally in Hindi, Hinglish, or English.\nBixby needs a few permissions to make calls, messages, voice controls, and device actions work."; textSize = 15f; gravity = Gravity.CENTER; setTextColor(android.graphics.Color.GRAY); setPadding(0, dp(20), 0, dp(28)) }, LinearLayout.LayoutParams(-1, -2))
        val button = Button(this).apply { text = "Continue & Allow"; setOnClickListener { requestPermissionsForAssistant() } }
        root.addView(button, LinearLayout.LayoutParams(-1, dp(52)))
        setContentView(root)
    }

    private fun requestPermissionsForAssistant() {
        val needed = buildList {
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CALENDAR)
            if (android.os.Build.VERSION.SDK_INT >= 31) permissions += Manifest.permission.BLUETOOTH_CONNECT
            if (android.os.Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
            permissions.filterTo(this) { ContextCompat.checkSelfPermission(this@WelcomeActivity, it) != PackageManager.PERMISSION_GRANTED }
        }
        if (needed.isEmpty()) finishWelcome() else ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_ONBOARDING)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ONBOARDING) finishWelcome()
    }

    private fun finishWelcome() {
        prefs.edit().putBoolean("completed", true).apply()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) runCatching { HotwordListeningService.start(this) }
        openMain()
    }
    private fun openMain() { startActivity(Intent(this, MainActivity::class.java)); finish() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
