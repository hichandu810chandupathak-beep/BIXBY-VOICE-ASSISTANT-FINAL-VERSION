package com.bixby.voiceassistant

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Base UI controller only. Backend AI and voice recognition are intentionally deferred. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val floatingBar = findViewById<LinearLayout>(R.id.floatingBar)
        val responseSheet = findViewById<LinearLayout>(R.id.responseSheet)
        val status = findViewById<TextView>(R.id.tvAssistantStatus)
        val keyboard = findViewById<View>(R.id.btnKeyboard)
        val mic = findViewById<View>(R.id.btnMicTrigger)
        val orbGlow = findViewById<ImageView>(R.id.orbGlow)

        orbGlow.startAnimation(AnimationUtils.loadAnimation(this, R.anim.orb_pulse))

        // UI-only state hooks. No speech, network, or AI work is performed here yet.
        keyboard.setOnClickListener {
            status.text = "Type a command"
        }

        mic.setOnClickListener {
            status.text = "Listening..."
        }

        floatingBar.setOnClickListener {
            responseSheet.visibility =
                if (responseSheet.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }
}
