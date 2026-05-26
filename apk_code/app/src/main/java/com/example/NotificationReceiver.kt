package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phrase = CutePhrases.getRandom()

        // Persistent storage update
        val prefs = context.getSharedPreferences("valentinka_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("latest_message", phrase)
            putLong("latest_message_time", System.currentTimeMillis())
            apply()
        }

        // Save to Room database history asynchronously
        val pendingResult = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val db = DatabaseProvider.getDatabase(context)
                db.valentineHistoryDao().insertHistoryItem(
                    ValentineHistoryItem(message = phrase, timestamp = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }

        // Send active OS notification
        showNotification(context, phrase)

        // Automatically reschedule next alarm loop for infinite warm surprises!
        val isActive = prefs.getBoolean("timer_is_active", false)
        if (isActive) {
            val intervalSeconds = prefs.getLong("timer_interval_seconds", 60L)
            AlarmScheduler.schedule(context, intervalSeconds)
        }
    }

    private fun showNotification(context: Context, phrase: String) {
        val channelId = "valentinka_surprises"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Notification Channel for modern API compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Милые валентинки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для получения теплых и поддерживающих валентинок"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action when user clicks the notification (Redirects straight into the visual greeting card screen)
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.btn_star_big_on) // Reliable fallback device icon
            .setContentTitle("Кое-что приятное для тебя... ❤️")
            .setContentText(phrase)
            .setStyle(NotificationCompat.BigTextStyle().bigText(phrase))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(4242, notification)
    }
}

/**
 * Universal safe scheduler helper to orchestrate exact alarm wakeups and looping background intervals.
 */
object AlarmScheduler {
    fun schedule(context: Context, seconds: Long, baseInterval: Long = seconds) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTimeMs = System.currentTimeMillis() + (seconds * 1000L)
        val prefs = context.getSharedPreferences("valentinka_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("timer_end_time", triggerTimeMs)
            putLong("timer_interval_seconds", baseInterval)
            putBoolean("timer_is_active", true)
            apply()
        }

        val canScheduleExact = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }

        try {
            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                }
            }
        } catch (e: SecurityException) {
            // Safe fallback method if Permission restrictions block exact timings (e.g. Android 14 schedule limits)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        val prefs = context.getSharedPreferences("valentinka_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("timer_end_time", 0L)
            putBoolean("timer_is_active", false)
            apply()
        }
    }
}
