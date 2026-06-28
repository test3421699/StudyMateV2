package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class StudyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra("subject") ?: "Study Session"
        val eventId = intent.getIntExtra("eventId", 0)
        val alarmType = intent.getIntExtra("alarmType", 0) // 1 for 15m, 2 for 5m, 3 for now
        val customTitle = intent.getStringExtra("title") ?: "Study Session Starting Soon!"
        val customMessage = intent.getStringExtra("message") ?: "Your session for '$subject' starts in 15 minutes. Let's make it count!"

        Log.d("StudyAlarmReceiver", "Received alarm for subject: $subject, eventId: $eventId, type: $alarmType")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "studymate_planner_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "StudyMate Planner Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for planned study sessions"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val notificationUniqueId = eventId * 10 + alarmType
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationUniqueId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(customTitle)
            .setContentText(customMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationUniqueId, notification)
    }
}
