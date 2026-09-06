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
        val bgColor = if (isDark) android.graphics.Color.parseColor("#08080A") else android.graphics.Color.parseColor("#F2F2F2")
        val cardColor = if (isDark) android.graphics.Color.parseColor("#1C1C1E") else android.graphics.Color.WHITE
        val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); setBackgroundColor(bgColor) }
        
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(20)) }
        toolbar.addView(ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_previous); setColorFilter(textColor); setBackgroundColor(android.graphics.Color.TRANSPARENT); setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(TextView(this).apply { text = "Settings"; textSize = 28f; setTextColor(textColor); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(10), 0, 0, 0) })
        root.addView(toolbar)

        // Account Section
        root.addView(TextView(this).apply { text = "Samsung Account"; setTextColor(android.graphics.Color.parseColor("#3478F6")); setPadding(dp(4), dp(10), 0, dp(4)) })
        root.addView(createCard(cardColor, "Manage Account", textColor))

        // API Key Section
        root.addView(TextView(this).apply { text = "AI Brain (Gemini API)"; setTextColor(android.graphics.Color.parseColor("#3478F6")); setPadding(dp(4), dp(20), 0, dp(4)) })
        val apiCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = android.graphics.drawable.GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(20).toFloat() }; setPadding(dp(16), dp(16), dp(16), dp(16)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) } }
        val tokenInput = EditText(this).apply { hint = "Paste API Key here"; setText(prefs.getString("gemini_api_key", "")); setTextColor(textColor) }
        apiCard.addView(tokenInput)
        apiCard.addView(Button(this).apply { text = "Save Key"; setOnClickListener { prefs.edit().putString("gemini_api_key", tokenInput.text.toString().trim()).apply(); Toast.makeText(this@SettingsActivity, "API Key Saved", Toast.LENGTH_SHORT).show() } })
        root.addView(apiCard)

        // Theme Section
        root.addView(TextView(this).apply { text = "Appearance"; setTextColor(android.graphics.Color.parseColor("#3478F6")); setPadding(dp(4), dp(20), 0, dp(4)) })
        root.addView(createCard(cardColor, "Theme: System Default", textColor))
        
        setContentView(root)
    }
    private fun createCard(bgColor: Int, textStr: String, txtColor: Int): LinearLayout {
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = android.graphics.drawable.GradientDrawable().apply { setColor(bgColor); cornerRadius = dp(20).toFloat() }; setPadding(dp(16), dp(20), dp(16), dp(20)); layoutParams = LinearLayout.LayoutParams(-1, -2); addView(TextView(context).apply { text = textStr; setTextColor(txtColor); textSize = 16f }) }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
