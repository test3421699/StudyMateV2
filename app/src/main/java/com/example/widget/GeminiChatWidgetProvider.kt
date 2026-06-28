package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.GeminiContent
import com.example.data.GeminiNetwork
import com.example.data.GeminiPart
import com.example.data.GeminiRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeminiChatWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_TIP = "com.example.widget.ACTION_REFRESH_TIP"
        private const val PREFS_NAME = "studymate_chat_widget_prefs"
        private const val KEY_LAST_TIP = "last_tip_response"

        fun updateChatWidget(context: Context) {
            try {
                val intent = Intent(context, GeminiChatWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val ids = appWidgetManager.getAppWidgetIds(
                        android.content.ComponentName(context, GeminiChatWidgetProvider::class.java)
                    )
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e("ChatWidget", "Error in updateChatWidget trigger", e)
            }
        }
    }

    private val widgetScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val applicationContext = context.applicationContext
        
        // Initial setup for widgets of this type
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(applicationContext.packageName, R.layout.chat_widget_layout)

            // Read the cached last tip from SharedPreferences
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastTip = prefs.getString(KEY_LAST_TIP, null)
            if (lastTip != null) {
                views.setTextViewText(R.id.widget_chat_response, lastTip)
            }

            // Route to MainActivity Chat Screen on clicking the Bottom Mimic edit text or top Go to App button
            val chatIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = "NAVIGATE_CHAT"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val chatPendingIntent = PendingIntent.getActivity(
                applicationContext,
                appWidgetId,
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_input_mimic_btn, chatPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_chat_go_to_app, chatPendingIntent)

            // Direct Broadcast Intent to Refresh study tip right in this Widget without opening the app!
            val refreshIntent = Intent(applicationContext, GeminiChatWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_TIP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_chat_refresh, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_REFRESH_TIP) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
            val applicationContext = context.applicationContext

            // Update loading status immediately in the UI
            for (widgetId in appWidgetIds) {
                val views = RemoteViews(applicationContext.packageName, R.layout.chat_widget_layout)
                views.setTextViewText(R.id.widget_chat_response, "✨ Activating Gemini API...\nConnecting to server & tailoring your daily active study tip...\n\nPlease allow up to 3 seconds.")
                appWidgetManager.updateAppWidget(widgetId, views)
            }

            // Launch network routine on background thread
            widgetScope.launch {
                var generatedTip = ""
                try {
                    val keys = withContext(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val custom = db.studyMateDao().getAllApiKeysDirect().filter { it.isWorking }.map { it.key }
                        val list = mutableListOf<String>()
                        list.addAll(custom)
                        if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                            list.add(BuildConfig.GEMINI_API_KEY)
                        }
                        list
                    }

                    if (keys.isEmpty()) {
                        generatedTip = "No API Key found! Please set your Gemini key inside StudyMate Settings or the AI Studio Secrets panel."
                    } else {
                        val randomPrompts = listOf(
                            "Give me an active recall study strategy tip. Be motivational, concise, and professional (under 75 words).",
                            "Explain a quick Feynman technique learning tip that I can implement today. Be concise (under 75 words).",
                            "Give me a highly practical scientific focus tip to boost concentration during study blocks. Be concise (under 75 words).",
                            "Provide a short, inspirational learning quote and dynamic brief advice for students. Be concise (under 75 words)."
                        )
                        val chosenPrompt = randomPrompts.random()

                        // Make Retrofit request
                        val apiResponse = withContext(Dispatchers.IO) {
                            var responseText: String? = null
                            for (key in keys) {
                                try {
                                    val req = GeminiRequest(
                                        contents = listOf(
                                            GeminiContent(parts = listOf(GeminiPart(text = chosenPrompt)))
                                        )
                                    )
                                    responseText = GeminiNetwork.api.generateContent(key, req)
                                        .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                    if (responseText != null) break
                                } catch (e: Exception) {
                                    Log.e("ChatWidget", "Failed key api query", e)
                                }
                            }
                            responseText
                        }

                        generatedTip = apiResponse ?: "Gemini API limits exceeded or timeout. Please check your internet connectivity or set up a valid Gemini key in settings."
                    }
                } catch (e: Exception) {
                    Log.e("ChatWidget", "Error during widget generation request", e)
                    generatedTip = "Connection failed: ${e.localizedMessage}. Please verify you have configured your active Gemini API key."
                }

                // Cache response
                val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_LAST_TIP, generatedTip).apply()

                // Finalize Bind layout views refresh
                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(applicationContext.packageName, R.layout.chat_widget_layout)
                    views.setTextViewText(R.id.widget_chat_response, generatedTip)

                    // Refresh click bindings
                    val chatIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        action = "NAVIGATE_CHAT"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val chatPendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        widgetId,
                        chatIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_input_mimic_btn, chatPendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_chat_go_to_app, chatPendingIntent)

                    val refreshIntent = Intent(applicationContext, GeminiChatWidgetProvider::class.java).apply {
                        action = ACTION_REFRESH_TIP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                    }
                    val refreshPendingIntent = PendingIntent.getBroadcast(
                        applicationContext,
                        widgetId,
                        refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_chat_refresh, refreshPendingIntent)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }
    }
}
