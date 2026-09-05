package com.bixby.voiceassistant

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("bixby_settings", Context.MODE_PRIVATE) }
    private lateinit var accountSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildSettingsUi() }

    private fun buildSettingsUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(28)); setBackgroundColor(android.graphics.Color.rgb(8, 8, 10)) }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_previous); setBackgroundColor(android.graphics.Color.TRANSPARENT); contentDescription = "Back"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(TextView(this).apply { text = "Settings"; textSize = 28f; setTextColor(android.graphics.Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(toolbar)

        root.addView(section("Samsung Account"))
        val accountCard = card()
        accountSummary = TextView(this).apply { textSize = 16f; setTextColor(android.graphics.Color.WHITE); setPadding(dp(18), dp(16), dp(18), dp(6)) }
        accountCard.addView(accountSummary, LinearLayout.LayoutParams(-1, -2))
        accountCard.addView(TextView(this).apply { text = "Manage your account on this device"; textSize = 13f; setTextColor(android.graphics.Color.GRAY); setPadding(dp(18), 0, dp(18), dp(10)) }, LinearLayout.LayoutParams(-1, -2))
        accountCard.addView(Button(this).apply { text = "Samsung Account"; setOnClickListener { showAccountDialog() } }, LinearLayout.LayoutParams(-1, dp(50)))
        root.addView(accountCard)

        root.addView(section("Appearance"))
        val themeCard = card()
        themeCard.addView(TextView(this).apply { text = "Theme mode"; textSize = 17f; setTextColor(android.graphics.Color.WHITE); setPadding(dp(18), dp(16), dp(18), dp(4)) }, LinearLayout.LayoutParams(-1, -2))
        val modes = arrayOf("Light", "Dark", "System")
        val radio = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(10)) }
        modes.forEach { mode -> radio.addView(RadioButton(this).apply { text = mode; textSize = 15f; setTextColor(android.graphics.Color.LTGRAY); isChecked = prefs.getString("theme", "System") == mode; setOnClickListener { prefs.edit().putString("theme", mode).apply() } }, RadioGroup.LayoutParams(-1, dp(48))) }
        themeCard.addView(radio, LinearLayout.LayoutParams(-1, -2)); root.addView(themeCard)

        root.addView(section("Account"))
        val logoutCard = card()
        logoutCard.addView(Button(this).apply { text = "Withdrawal / Logout"; setOnClickListener { prefs.edit().clear().apply(); updateAccountSummary(); Toast.makeText(this@SettingsActivity, "Logged out", Toast.LENGTH_SHORT).show() } }, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(logoutCard)
        root.addView(TextView(this).apply { text = "Bixby settings\nYour demo account information is stored locally on this device."; textSize = 12f; setTextColor(android.graphics.Color.GRAY); setPadding(dp(4), dp(22), dp(4), 0) })
        setContentView(root); updateAccountSummary()
    }

    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = android.graphics.drawable.GradientDrawable().apply { setColor(android.graphics.Color.rgb(27, 27, 30)); cornerRadius = dp(24).toFloat() }; setPadding(0, 0, 0, dp(4)); elevation = dp(2).toFloat(); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) } }
    private fun section(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(android.graphics.Color.rgb(150, 190, 255)); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(4), dp(20), 0, dp(8)) }
    private fun showAccountDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0) }
        val name = EditText(this).apply { hint = "Name"; setText(prefs.getString("account_name", "")) }
        val email = EditText(this).apply { hint = "Email"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; setText(prefs.getString("account_email", "")) }
        box.addView(name); box.addView(email)
        AlertDialog.Builder(this).setTitle("Samsung Account").setMessage("Demo sign-in. No real Samsung credentials are requested.").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> prefs.edit().putString("account_name", name.text.toString().trim()).putString("account_email", email.text.toString().trim()).apply(); updateAccountSummary() }.show()
    }
    private fun updateAccountSummary() { val name = prefs.getString("account_name", "").orEmpty(); val email = prefs.getString("account_email", "").orEmpty(); accountSummary.text = if (name.isBlank() && email.isBlank()) "Samsung Account\nNot signed in" else "$name\n$email" }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
