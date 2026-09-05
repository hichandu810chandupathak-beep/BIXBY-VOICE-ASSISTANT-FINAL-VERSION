package com.bixby.voiceassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("bixby_settings", Context.MODE_PRIVATE) }
    private lateinit var accountSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildSettingsUi()
    }

    private fun buildSettingsUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
            setBackgroundColor(android.graphics.Color.rgb(18, 18, 18))
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { finish() }
            contentDescription = "Back"
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(TextView(this).apply {
            text = "Settings"; textSize = 24f; setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(toolbar)

        root.addView(sectionTitle("Samsung Account"))
        accountSummary = TextView(this).apply { setTextColor(android.graphics.Color.LTGRAY); textSize = 15f; setPadding(0, dp(6), 0, dp(10)) }
        root.addView(accountSummary)
        val accountButton = Button(this).apply {
            text = if (prefs.getString("account_name", null).isNullOrBlank()) "Sign in" else "Account"
            setOnClickListener { showAccountDialog() }
        }
        root.addView(accountButton, matchWrap())

        root.addView(sectionTitle("Appearance"))
        val themeButton = Button(this).apply {
            text = "Theme Mode · ${prefs.getString("theme", "System")}"
            setOnClickListener {
                val modes = arrayOf("Light", "Dark", "System")
                val current = modes.indexOf(prefs.getString("theme", "System")).coerceAtLeast(0)
                AlertDialog.Builder(this@SettingsActivity).setTitle("Theme Mode").setSingleChoiceItems(modes, current) { d, which ->
                    prefs.edit().putString("theme", modes[which]).apply()
                    text = "Theme Mode · ${modes[which]}"
                    d.dismiss()
                }.show()
            }
        }
        root.addView(themeButton, matchWrap())

        root.addView(sectionTitle("Account"))
        val logout = Button(this).apply {
            text = "Withdrawal / Logout"
            setOnClickListener {
                prefs.edit().remove("account_name").remove("account_email").apply()
                updateAccountSummary()
                Toast.makeText(this@SettingsActivity, "Logged out.", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(logout, matchWrap())
        root.addView(TextView(this).apply {
            text = "Bixby-inspired settings · Account details are stored locally on this device."
            setTextColor(android.graphics.Color.GRAY); textSize = 12f; setPadding(0, dp(20), 0, 0)
        })
        setContentView(root)
        updateAccountSummary()
    }

    private fun showAccountDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val name = EditText(this).apply { hint = "Your name"; setText(prefs.getString("account_name", "")) }
        val email = EditText(this).apply { hint = "Email"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; setText(prefs.getString("account_email", "")) }
        box.addView(name, matchWrap()); box.addView(email, matchWrap())
        AlertDialog.Builder(this).setTitle("Samsung Account").setMessage("Demo sign-in — no real Samsung credentials are requested.").setView(box)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim(); val e = email.text.toString().trim()
                if (n.isNotEmpty() || e.isNotEmpty()) prefs.edit().putString("account_name", n).putString("account_email", e).apply()
                updateAccountSummary()
            }.show()
    }

    private fun updateAccountSummary() {
        val name = prefs.getString("account_name", null).orEmpty()
        val email = prefs.getString("account_email", null).orEmpty()
        accountSummary.text = if (name.isBlank() && email.isBlank()) "Not signed in · Set up your account" else "$name\n$email"
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(android.graphics.Color.rgb(140, 180, 255)); setPadding(0, dp(22), 0, dp(4))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
