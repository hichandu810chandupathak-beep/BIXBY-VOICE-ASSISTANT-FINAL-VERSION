package com.bixby.voiceassistant

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

class BixbyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val message = throwable.message ?: throwable.javaClass.simpleName
                Log.e("BixbyCrash", "Fatal crash on ${thread.name}: $message", throwable)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Bixby crash: $message", Toast.LENGTH_LONG).show()
                }
            } catch (handlerError: Exception) {
                Log.e("BixbyCrash", "Crash handler failed", handlerError)
            }
        }
    }
}
