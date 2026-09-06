package com.bixby.voiceassistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        val allGranted = permissions.values.all { it == true }
        if (allGranted) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            renderWelcome()
        } catch (throwable: Throwable) {
            startActivity(Intent(this, CrashActivity::class.java).apply {
                putExtra("crash_log", throwable.stackTraceToString())
            })
            finish()
        }
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
            setOnClickListener {
                Toast.makeText(this@WelcomeActivity, "Requesting permissions...", Toast.LENGTH_SHORT).show()
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
            }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
