package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.StudyProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    companion object {
        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.ACTION_RESUME"
        const val ACTION_RESET = "com.example.ACTION_RESET"

        const val CHANNEL_ID = "studymate_timer_channel"
        const val NOTIFICATION_ID = 4567

        private val _pomodoroMinutes = MutableStateFlow(25)
        val pomodoroMinutes: StateFlow<Int> = _pomodoroMinutes.asStateFlow()

        private val _pomodoroSeconds = MutableStateFlow(0)
        val pomodoroSeconds: StateFlow<Int> = _pomodoroSeconds.asStateFlow()

        private val _isPomodoroRunning = MutableStateFlow(false)
        val isPomodoroRunning: StateFlow<Boolean> = _isPomodoroRunning.asStateFlow()

        private val _pomodoroMode = MutableStateFlow("STUDY") // "STUDY" or "BREAK"
        val pomodoroMode: StateFlow<String> = _pomodoroMode.asStateFlow()

        private val _customStudyMinutes = MutableStateFlow(25)
        val customStudyMinutes: StateFlow<Int> = _customStudyMinutes.asStateFlow()

        private val _customBreakMinutes = MutableStateFlow(5)
        val customBreakMinutes: StateFlow<Int> = _customBreakMinutes.asStateFlow()

        private val _savedStudyMinutes = MutableStateFlow(25)
        val savedStudyMinutes: StateFlow<Int> = _savedStudyMinutes.asStateFlow()

        private val _savedStudySeconds = MutableStateFlow(0)
        val savedStudySeconds: StateFlow<Int> = _savedStudySeconds.asStateFlow()

        fun setCustomStudyMinutes(minutes: Int) {
            _customStudyMinutes.value = minutes.coerceIn(1, 180)
            if (_pomodoroMode.value == "STUDY") {
                _pomodoroMinutes.value = _customStudyMinutes.value
                _pomodoroSeconds.value = 0
                _savedStudyMinutes.value = _customStudyMinutes.value
                _savedStudySeconds.value = 0
            }
        }

        fun setCustomBreakMinutes(minutes: Int) {
            _customBreakMinutes.value = minutes.coerceIn(1, 180)
            if (_pomodoroMode.value == "BREAK") {
                _pomodoroMinutes.value = _customBreakMinutes.value
                _pomodoroSeconds.value = 0
            }
        }

        fun setPomodoroMode(mode: String) {
            if (_pomodoroMode.value == "STUDY" && mode == "BREAK") {
                // Safeguard of active study timer progress
                _savedStudyMinutes.value = _pomodoroMinutes.value
                _savedStudySeconds.value = _pomodoroSeconds.value
            }
            
            _pomodoroMode.value = mode
            if (mode == "STUDY") {
                _pomodoroMinutes.value = _savedStudyMinutes.value
                _pomodoroSeconds.value = _savedStudySeconds.value
            } else {
                _pomodoroMinutes.value = _customBreakMinutes.value
                _pomodoroSeconds.value = 0
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startTimer()
            }
            ACTION_PAUSE -> {
                pauseTimer()
            }
            ACTION_RESUME -> {
                startTimer()
            }
            ACTION_RESET -> {
                resetTimer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer() {
        if (_isPomodoroRunning.value && timerJob != null) return
        _isPomodoroRunning.value = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, 
                    createNotification(), 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Send initial widget update on start
        com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(applicationContext)

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_isPomodoroRunning.value) {
                delay(1000)
                val sec = _pomodoroSeconds.value
                val min = _pomodoroMinutes.value

                if (sec > 0) {
                    _pomodoroSeconds.value = sec - 1
                } else if (min > 0) {
                    _pomodoroMinutes.value = min - 1
                    _pomodoroSeconds.value = 59
                } else {
                    if (_pomodoroMode.value == "STUDY") {
                        _pomodoroMode.value = "BREAK"
                        _pomodoroMinutes.value = _customBreakMinutes.value
                        _pomodoroSeconds.value = 0
                        
                        // Study session completed, reset saved progress back to default custom Study duration
                        _savedStudyMinutes.value = _customStudyMinutes.value
                        _savedStudySeconds.value = 0
                        
                        val dao = AppDatabase.getDatabase(applicationContext).studyMateDao()
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        try {
                            val list = dao.getAllProgressDays().first()
                            val existing = list.firstOrNull { it.dateString == today }
                            val count = if (existing != null) existing.countCompleted + 1 else 1
                            dao.insertProgressDay(StudyProgress(dateString = today, countCompleted = count))
                        } catch (e: Exception) {
                            Log.e("TimerService", "Error logging focus milestone to database", e)
                        }
                    } else {
                        // Break mode ended! Keep / continue the study timer instead of fresh 25m reset
                        _pomodoroMode.value = "STUDY"
                        _pomodoroMinutes.value = _savedStudyMinutes.value
                        _pomodoroSeconds.value = _savedStudySeconds.value
                        
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, createNotification())
                        // Don't stop service, immediately continue study countdown loop
                        com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(applicationContext)
                        continue
                    }
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, createNotification())
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification())
                
                // Trigger realtime updates for the app widget as the timer ticks
                com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(applicationContext)
            }
        }
    }

    private fun pauseTimer() {
        _isPomodoroRunning.value = false
        timerJob?.cancel()
        timerJob = null
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
        com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(applicationContext)
    }

    private fun resetTimer() {
        if (_pomodoroMode.value == "BREAK") {
            // "When I stopped/reset the break, switch back and continue study timer - don't reset it"
            _pomodoroMode.value = "STUDY"
            _pomodoroMinutes.value = _savedStudyMinutes.value
            _pomodoroSeconds.value = _savedStudySeconds.value
            startTimer()
        } else {
            _isPomodoroRunning.value = false
            timerJob?.cancel()
            timerJob = null
            _pomodoroMinutes.value = _customStudyMinutes.value
            _pomodoroSeconds.value = 0
            _savedStudyMinutes.value = _customStudyMinutes.value
            _savedStudySeconds.value = 0
            com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(applicationContext)
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        val minStr = _pomodoroMinutes.value.toString().padStart(2, '0')
        val secStr = _pomodoroSeconds.value.toString().padStart(2, '0')
        val modeStr = if (_pomodoroMode.value == "STUDY") "Studying" else "Break"
        
        val titleText = "Pomodoro Timer: $modeStr"
        val contentText = if (_isPomodoroRunning.value) "Time Remaining: $minStr:$secStr" else "Timer Paused - $minStr:$secStr"

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainActivityPendingIntent = PendingIntent.getActivity(
            this, 100, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (_isPomodoroRunning.value) {
            val pauseIntent = Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(
                this, 101, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause", pausePendingIntent
            ).build()
        } else {
            val resumeIntent = Intent(this, TimerService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(
                this, 102, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Resume", resumePendingIntent
            ).build()
        }

        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_RESET }
        val stopPendingIntent = PendingIntent.getService(
            this, 103, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(_isPomodoroRunning.value)
            .setContentIntent(mainActivityPendingIntent)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "StudyMate Pomodoro Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Pomodoro study and break sessions"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isPomodoroRunning.value = false
        timerJob?.cancel()
    }
}
