package com.bixby.voiceassistant

import android.app.Application

class BixbyApplication : Application() {
    override fun onCreate() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val intent = android.content.Intent(this, CrashActivity::class.java).apply {
                putExtra("crash_log", throwable.stackTraceToString())
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onCreate()
    }
}
