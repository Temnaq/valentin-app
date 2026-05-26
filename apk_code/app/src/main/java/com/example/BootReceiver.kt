package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = context.getSharedPreferences("valentinka_prefs", Context.MODE_PRIVATE)
            val isActive = prefs.getBoolean("timer_is_active", false)
            if (isActive) {
                val endTime = prefs.getLong("timer_end_time", 0L)
                val now = System.currentTimeMillis()
                val intervalSeconds = prefs.getLong("timer_interval_seconds", 60L)

                val delaySeconds = if (endTime > now) {
                    (endTime - now) / 1000L
                } else {
                    intervalSeconds
                }

                // Schedule passing the original base interval so we do not overwrite timer_interval_seconds with standard remaining time
                AlarmScheduler.schedule(context, delaySeconds, intervalSeconds)
            }
        }
    }
}
