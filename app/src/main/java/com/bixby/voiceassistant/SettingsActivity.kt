package com.bixby.voiceassistant
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("bixby_settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildSettingsUi() }

    private fun buildSettingsUi() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDark) android.graphics.Color.rgb(8, 8, 10) else android.graphics.Color.WHITE
        val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val cardColor = if (isDark) android.graphics.Color.rgb(27, 27, 30) else android.graphics.Color.rgb(245, 245, 245)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(28)); setBackgroundColor(bgColor) }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_previous); setColorFilter(textColor); setBackgroundColor(android.graphics.Color.TRANSPARENT); setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(TextView(this).apply { text = "Settings"; textSize = 28f; setTextColor(textColor); typeface = android.graphics.Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(toolbar)

        root.addView(TextView(this).apply { text = "AI Assistant Configuration"; textSize = 14f; setTextColor(android.graphics.Color.rgb(150, 190, 255)); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(4), dp(20), 0, dp(8)) })
        val configCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = android.graphics.drawable.GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(24).toFloat() }; setPadding(dp(18), dp(16), dp(18), dp(16)); elevation = dp(2).toFloat(); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) } }
        configCard.addView(TextView(this).apply { text = "Gemini API Key"; textSize = 17f; setTextColor(textColor); setPadding(0, 0, 0, dp(8)) })
        val tokenInput = EditText(this).apply { hint = "Paste API Key here"; setText(prefs.getString("gemini_api_key", "")); setTextColor(textColor); setHintTextColor(android.graphics.Color.GRAY) }
        configCard.addView(tokenInput)
        configCard.addView(Button(this).apply { text = "Save Configuration"; setOnClickListener { prefs.edit().putString("gemini_api_key", tokenInput.text.toString().trim()).apply(); Toast.makeText(this@SettingsActivity, "Configuration Saved!", Toast.LENGTH_SHORT).show() } }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        root.addView(configCard)
        setContentView(root)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}