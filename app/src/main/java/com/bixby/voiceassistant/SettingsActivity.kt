package com.bixby.voiceassistant
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val etKey = findViewById<EditText>(R.id.etApiKey)
        val prefs = getSharedPreferences("bixby_settings", Context.MODE_PRIVATE)
        etKey.setText(prefs.getString("gemini_api_key", ""))
        
        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            prefs.edit().putString("gemini_api_key", etKey.text.toString().trim()).apply()
            Toast.makeText(this, "Bixby is Ready! Press Home Button.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}