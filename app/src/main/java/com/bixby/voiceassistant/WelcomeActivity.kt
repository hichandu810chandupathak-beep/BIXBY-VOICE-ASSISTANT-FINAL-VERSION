package com.bixby.voiceassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class WelcomeActivity : Activity() {
    private companion object { const val EXTRA_START_VOICE = "start_voice_after_welcome" }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            result[Manifest.permission.POST_NOTIFICATIONS] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (micGranted && notificationGranted) {
            getSharedPreferences("bixby_onboarding", MODE_PRIVATE)
                .edit().putBoolean("completed", true).apply()
            HotwordListeningService.start(this)
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_START_VOICE, true)
            })
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderWelcome()
    }

    private fun renderWelcome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(48), dp(28), dp(40))
            setBackgroundColor(android.graphics.Color.rgb(8, 8, 10))
        }
        root.addView(TextView(this).apply {
            text = "Bixby"; textSize = 42f; gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Your conversational voice assistant"; textSize = 18f; gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY); setPadding(0, dp(12), 0, dp(8))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Talk naturally in Hindi, Hinglish, or English.\nBixby needs a few permissions to make calls, messages, voice controls, and device actions work."
            textSize = 15f; gravity = Gravity.CENTER; setTextColor(android.graphics.Color.GRAY)
            setPadding(0, dp(20), 0, dp(28))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "Continue & Allow"
            setOnClickListener { launchPermissions() }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        setContentView(root)
    }

    private fun launchPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALENDAR
        )
        if (Build.VERSION.SDK_INT >= 31) permissions += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
