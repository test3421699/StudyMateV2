package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.TimerService
import com.example.data.AppDatabase
import com.example.data.StudyProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudyProgressWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TICK_TASK = "com.example.widget.ACTION_TICK_TASK"
        const val EXTRA_TASK_ID = "com.example.widget.EXTRA_TASK_ID"

        fun updateStudyWidget(context: Context) {
            try {
                val intent = Intent(context, StudyProgressWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val ids = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, StudyProgressWidgetProvider::class.java)
                    )
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e("StudyWidget", "Error in updateStudyWidget trigger", e)
            }
        }
    }

    private val widgetScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK_TASK) {
            val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
            if (taskId != -1) {
                val applicationContext = context.applicationContext
                widgetScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(applicationContext)
                        withContext(Dispatchers.IO) {
                            db.studyMateDao().updateTaskCompletion(taskId, isCompleted = true)
                            // Track study activity
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val today = sdf.format(Date())
                            val existing = db.studyMateDao().getAllStudyProgressDirect().firstOrNull { it.dateString == today }
                            val count = if (existing != null) existing.countCompleted + 1 else 1
                            db.studyMateDao().insertProgressDay(StudyProgress(dateString = today, countCompleted = count))
                        }
                        updateStudyWidget(applicationContext)
                    } catch (e: Exception) {
                        Log.e("StudyWidget", "Error ticking task via widget action", e)
                    }
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val applicationContext = context.applicationContext
        
        // Launch dynamic update inside a coroutine
        widgetScope.launch {
            try {
                // Fetch data from database on IO thread
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.studyMateDao()
                
                val tasks = withContext(Dispatchers.IO) { dao.getAllTasksDirect() }
                val events = withContext(Dispatchers.IO) { dao.getAllStudyEventsDirect() }
                val progressDays = withContext(Dispatchers.IO) { dao.getAllStudyProgressDirect() }

                // Calculate calculations
                val pendingTasks = tasks.filter { !it.isCompleted }.sortedByDescending { it.createdAt }
                val pendingTasksCount = pendingTasks.size
                val streak = calculateCurrentStreak(progressDays)

                // Learn plan: allow showing recent uncompleted sessions scheduled within past 2 hours or in future
                val upcomingEvents = events
                    .filter { !it.isCompleted && it.studyTimeMillis > (System.currentTimeMillis() - 2 * 60 * 60 * 1000) }
                    .sortedBy { it.studyTimeMillis }

                val nextStudyText = if (upcomingEvents.isNotEmpty()) {
                    upcomingEvents.joinToString("\n") { ev ->
                        val timeString = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(ev.studyTimeMillis))
                        "📅 ${ev.subject} ($timeString)"
                    }
                } else {
                    "📅 No upcoming sessions"
                }

                // Gather Pomodoro status from TimerService (if alive)
                val isPomoRunning = TimerService.isPomodoroRunning.value
                val pomoMin = TimerService.pomodoroMinutes.value
                val pomoSec = TimerService.pomodoroSeconds.value
                val pomoMode = TimerService.pomodoroMode.value

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(applicationContext.packageName, R.layout.study_widget_layout)

                    // Bind Streak
                    views.setTextViewText(R.id.widget_streak_text, "🔥 Streak: $streak Days")

                    // Bind Pending tasks count
                    views.setTextViewText(R.id.widget_tasks_count, "📝 $pendingTasksCount tasks left")

                    // Bind dynamic checklist rows (up to 3 items)
                    val displayTasks = pendingTasks.take(3)
                    
                    // Task Row 1
                    if (displayTasks.size > 0) {
                        val task = displayTasks[0]
                        views.setViewVisibility(R.id.widget_todo_row_1, View.VISIBLE)
                        views.setTextViewText(R.id.widget_todo_text_1, task.title)
                        views.setTextViewText(R.id.widget_todo_check_1, "☐")
                        
                        val tickIntent = Intent(applicationContext, StudyProgressWidgetProvider::class.java).apply {
                            action = ACTION_TICK_TASK
                            putExtra(EXTRA_TASK_ID, task.id)
                        }
                        val tickPending = PendingIntent.getBroadcast(
                            applicationContext,
                            task.id,
                            tickIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_todo_row_1, tickPending)
                    } else {
                        views.setViewVisibility(R.id.widget_todo_row_1, View.GONE)
                    }

                    // Task Row 2
                    if (displayTasks.size > 1) {
                        val task = displayTasks[1]
                        views.setViewVisibility(R.id.widget_todo_row_2, View.VISIBLE)
                        views.setTextViewText(R.id.widget_todo_text_2, task.title)
                        views.setTextViewText(R.id.widget_todo_check_2, "☐")
                        
                        val tickIntent = Intent(applicationContext, StudyProgressWidgetProvider::class.java).apply {
                            action = ACTION_TICK_TASK
                            putExtra(EXTRA_TASK_ID, task.id)
                        }
                        val tickPending = PendingIntent.getBroadcast(
                            applicationContext,
                            task.id,
                            tickIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_todo_row_2, tickPending)
                    } else {
                        views.setViewVisibility(R.id.widget_todo_row_2, View.GONE)
                    }

                    // Task Row 3
                    if (displayTasks.size > 2) {
                        val task = displayTasks[2]
                        views.setViewVisibility(R.id.widget_todo_row_3, View.VISIBLE)
                        views.setTextViewText(R.id.widget_todo_text_3, task.title)
                        views.setTextViewText(R.id.widget_todo_check_3, "☐")
                        
                        val tickIntent = Intent(applicationContext, StudyProgressWidgetProvider::class.java).apply {
                            action = ACTION_TICK_TASK
                            putExtra(EXTRA_TASK_ID, task.id)
                        }
                        val tickPending = PendingIntent.getBroadcast(
                            applicationContext,
                            task.id,
                            tickIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_todo_row_3, tickPending)
                    } else {
                        views.setViewVisibility(R.id.widget_todo_row_3, View.GONE)
                    }

                    // Bind Upcoming study session
                    views.setTextViewText(R.id.widget_upcoming_event, nextStudyText)

                    // Bind Pomodoro timer details
                    if (isPomoRunning) {
                        val modeLabel = if (pomoMode == "STUDY") "⚡ STUDYING" else "☕ BREAK"
                        views.setTextViewText(R.id.widget_pomodoro_state, "⏱️ $modeLabel")
                        views.setTextViewText(R.id.widget_pomodoro_countdown, String.format(Locale.getDefault(), "%02d:%02d", pomoMin, pomoSec))
                    } else {
                        views.setTextViewText(R.id.widget_pomodoro_state, "⏱️ INACTIVE")
                        views.setTextViewText(R.id.widget_pomodoro_countdown, "25:00")
                    }

                    // Intent to launch application on click of widget background (unless specifically clicking a task row)
                    val mainIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        appWidgetId,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e("StudyWidget", "Error updating widget details", e)
            }
        }
    }

    private fun calculateCurrentStreak(progressList: List<StudyProgress>): Int {
        val days = progressList.map { it.dateString }.toSet()
        if (days.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        var streak = 0

        val todayStr = sdf.format(cal.time)
        val hasToday = days.contains(todayStr)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)
        val hasYesterday = days.contains(yesterdayStr)

        if (!hasToday && !hasYesterday) {
            return 0
        }

        cal.time = Date()
        if (!hasToday && hasYesterday) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val dateStr = sdf.format(cal.time)
            if (days.contains(dateStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }
}
