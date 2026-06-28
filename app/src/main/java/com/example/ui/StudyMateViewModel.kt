package com.example.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.StudyAlarmReceiver
import com.example.TimerService
import com.example.data.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "User" or "Gemini"
    val text: String,
    val localImageUri: String? = null
)

class StudyMateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        @Volatile var activeInstance: StudyMateViewModel? = null
        const val AI_NOTIFICATION_CHANNEL_ID = "studymate_ai_channel"
        const val AI_RUNNING_NOTIFICATION_ID = 10001
        const val AI_RESULT_NOTIFICATION_ID = 10002
    }

    private var activeAIGeneratorJob: kotlinx.coroutines.Job? = null

    init {
        activeInstance = this
    }

    override fun onCleared() {
        if (activeInstance == this) {
            activeInstance = null
        }
        activeAIGeneratorJob?.cancel()
        super.onCleared()
    }

    fun cancelActiveAIGeneration() {
        val job = activeAIGeneratorJob
        if (job != null && job.isActive) {
            job.cancel()
            activeAIGeneratorJob = null
            isAILoading.value = false
            showResultNotification(
                isSuccess = false,
                title = "AI Generation Canceled",
                message = "The generation process was canceled by user."
            )
        }
    }

    private fun showRunningNotification(title: String, message: String) {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                AI_NOTIFICATION_CHANNEL_ID,
                "AI StudyMate Generator",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows real-time notifications for background study material generation"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, com.example.AICancelReceiver::class.java)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, AI_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setProgress(0, 0, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )

        try {
            notificationManager.notify(AI_RUNNING_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e("StudyMateVM", "Error posting running notification", e)
        }
    }

    private fun hideRunningNotification() {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(AI_RUNNING_NOTIFICATION_ID)
    }

    private fun showResultNotification(isSuccess: Boolean, title: String, message: String) {
        hideRunningNotification()
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val openIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, AI_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(if (isSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e("StudyMateVM", "Error posting result notification", e)
        }
    }

    private val database = AppDatabase.getDatabase(application)
    private val dao = database.studyMateDao()

    // Screen states
    val notes = dao.getAllNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val flashcardSets = dao.getAllFlashcardSets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val quizSets = dao.getAllQuizSets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studyEvents = dao.getAllStudyEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tasks = dao.getAllTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progressDays = dao.getAllProgressDays().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customApiKeys = dao.getAllApiKeys().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Pomodoro Focus Timer States ---
    private val _customStudyMinutes = TimerService.customStudyMinutes
    val customStudyMinutes: StateFlow<Int> = _customStudyMinutes

    private val _customBreakMinutes = TimerService.customBreakMinutes
    val customBreakMinutes: StateFlow<Int> = _customBreakMinutes

    private val _pomodoroMinutes = TimerService.pomodoroMinutes
    val pomodoroMinutes: StateFlow<Int> = _pomodoroMinutes

    private val _pomodoroSeconds = TimerService.pomodoroSeconds
    val pomodoroSeconds: StateFlow<Int> = _pomodoroSeconds

    private val _isPomodoroRunning = TimerService.isPomodoroRunning
    val isPomodoroRunning: StateFlow<Boolean> = _isPomodoroRunning

    private val _pomodoroMode = TimerService.pomodoroMode
    val pomodoroMode: StateFlow<String> = _pomodoroMode

    fun setCustomStudyMinutes(minutes: Int) {
        TimerService.setCustomStudyMinutes(minutes)
    }

    fun setCustomBreakMinutes(minutes: Int) {
        TimerService.setCustomBreakMinutes(minutes)
    }

    fun startPomodoro() {
        val intent = Intent(getApplication(), TimerService::class.java).apply {
            action = TimerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun pausePomodoro() {
        val intent = Intent(getApplication(), TimerService::class.java).apply {
            action = TimerService.ACTION_PAUSE
        }
        getApplication<Application>().startService(intent)
    }

    fun resetPomodoro() {
        val intent = Intent(getApplication(), TimerService::class.java).apply {
            action = TimerService.ACTION_RESET
        }
        getApplication<Application>().startService(intent)
    }

    fun setPomodoroMode(mode: String) {
        TimerService.setPomodoroMode(mode)
        if (mode == "BREAK" || isPomodoroRunning.value) {
            startPomodoro()
        }
    }

    // Folders SharedPreferences persistence
    private val prefs = application.getSharedPreferences("studymate_folders", Context.MODE_PRIVATE)

    // User session state flow
    private val _currentUser = MutableStateFlow<UserSession?>(null)
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    // A state flow of subjects
    private val _subjectsList = MutableStateFlow<List<String>>(emptyList())
    val subjectsList: StateFlow<List<String>> = _subjectsList.asStateFlow()

    // A state flow of chapters mapped per subject. Format key: "subjectName", value: list of chapters
    private val _chaptersMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val chaptersMap: StateFlow<Map<String, List<String>>> = _chaptersMap.asStateFlow()

    // Homework helper chat history
    val selectedTeacherPersonality = MutableStateFlow("Friendly Teacher")
    val selectedExplanationLevel = MutableStateFlow("Intermediate")
    val selectedModel = MutableStateFlow(prefs.getString("selected_gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash")

    fun updateSelectedModel(model: String) {
        selectedModel.value = model
        prefs.edit().putString("selected_gemini_model", model).apply()
    }

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage(sender = "Gemini", text = "Welcome to AI Teacher Modes! I can teach you in any style. Choose my personality (Friendly Teacher, Strict Teacher, Board Exam Expert, Fast Revision Teacher) and comprehension level (Explain Like I'm 10, Beginner, Intermediate, Exam Level, Expert) above and let's start learning!"))
    )
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    // Current homework assistant conversation context
    private val conversationTurns = mutableListOf<GeminiContent>()

    init {
        loadFolders()
        val savedName = prefs.getString("user_name", null)
        val savedEmail = prefs.getString("user_email", null)
        if (savedName != null && savedEmail != null) {
            _currentUser.value = UserSession(savedName, savedEmail)
        }

        val loadedHistory = try { loadChatHistory() } catch (e: Exception) { emptyList() }
        if (loadedHistory.isNotEmpty()) {
            _chatHistory.value = loadedHistory
            // Repopulate conversation context turns from history
            for (msg in loadedHistory) {
                if (msg.sender == "User") {
                    val parts = mutableListOf<GeminiPart>()
                    parts.add(GeminiPart(text = msg.text))
                    if (msg.localImageUri != null) {
                        try {
                            val file = File(msg.localImageUri)
                            if (file.exists()) {
                                val bmp = android.graphics.BitmapFactory.decodeFile(msg.localImageUri)
                                if (bmp != null) {
                                    val out = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
                                    val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                    parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64)))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("StudyMateVM", "Error rebuilding chat image part", e)
                        }
                    }
                    conversationTurns.add(GeminiContent(parts))
                } else {
                    conversationTurns.add(GeminiContent(listOf(GeminiPart(text = msg.text))))
                }
            }
            // Retain up to last 12 entries
            while (conversationTurns.size > 12) {
                conversationTurns.removeAt(0)
            }
        }
    }

    fun registerAndLogin(name: String, email: String) {
        viewModelScope.launch {
            prefs.edit().apply {
                putString("user_name", name)
                putString("user_email", email)
                apply()
            }
            _currentUser.value = UserSession(name, email)
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefs.edit().apply {
                remove("user_name")
                remove("user_email")
                apply()
            }
            _currentUser.value = null
        }
    }

    private fun loadFolders() {
        val subjects = prefs.getStringSet("subjects", emptySet())?.toList() ?: emptyList()
        _subjectsList.value = subjects.sorted()

        val chaptersRaw = prefs.getStringSet("chapters", emptySet()) ?: emptySet()
        val tempMap = mutableMapOf<String, MutableList<String>>()
        for (raw in chaptersRaw) {
            val parts = raw.split("||")
            if (parts.size >= 2) {
                val sub = parts[0]
                val chap = parts[1]
                tempMap.getOrPut(sub) { mutableListOf() }.add(chap)
            }
        }
        val sortedMap = tempMap.mapValues { entry -> entry.value.sorted() }
        _chaptersMap.value = sortedMap
    }

    fun addSubject(subject: String) {
        val current = _subjectsList.value.toMutableSet()
        if (subject.isNotBlank() && current.add(subject)) {
            prefs.edit().putStringSet("subjects", current).apply()
            loadFolders()
        }
    }

    fun deleteSubject(subject: String) {
        val currentSubjects = _subjectsList.value.toMutableSet()
        if (currentSubjects.remove(subject)) {
            prefs.edit().putStringSet("subjects", currentSubjects).apply()

            val chaptersRaw = prefs.getStringSet("chapters", emptySet())?.toMutableSet() ?: mutableSetOf()
            chaptersRaw.removeAll { it.startsWith("$subject||") }
            prefs.edit().putStringSet("chapters", chaptersRaw).apply()

            loadFolders()
        }
    }

    fun addChapter(subject: String, chapter: String) {
        if (subject.isBlank() || chapter.isBlank()) return
        val chaptersRaw = prefs.getStringSet("chapters", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (chaptersRaw.add("$subject||$chapter")) {
            prefs.edit().putStringSet("chapters", chaptersRaw).apply()
            loadFolders()
        }
    }

    fun deleteChapter(subject: String, chapter: String) {
        val chaptersRaw = prefs.getStringSet("chapters", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (chaptersRaw.remove("$subject||$chapter")) {
            prefs.edit().putStringSet("chapters", chaptersRaw).apply()
            loadFolders()
        }
    }

    fun renameNote(note: NoteEntry, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                val updated = note.copy(title = newName)
                dao.insertNote(updated)
            }
        }
    }

    fun renameSubject(oldSubject: String, newSubject: String) {
        if (oldSubject.isBlank() || newSubject.isBlank() || oldSubject == newSubject) return
        viewModelScope.launch {
            val currentSubjects = _subjectsList.value.toMutableSet()
            if (currentSubjects.remove(oldSubject)) {
                currentSubjects.add(newSubject)
                prefs.edit().putStringSet("subjects", currentSubjects).apply()

                val chaptersRaw = prefs.getStringSet("chapters", emptySet())?.toMutableSet() ?: mutableSetOf()
                val updatedChapters = chaptersRaw.map { raw ->
                    val parts = raw.split("||")
                    if (parts.size >= 2 && parts[0] == oldSubject) {
                        "$newSubject||${parts[1]}"
                    } else {
                        raw
                    }
                }.toSet()
                prefs.edit().putStringSet("chapters", updatedChapters).apply()

                val notes = dao.getAllNotesDirect()
                notes.forEach { note ->
                    if (note.subject == oldSubject) {
                        dao.insertNote(note.copy(subject = newSubject))
                    }
                }

                loadFolders()
            }
        }
    }

    fun renameChapter(subject: String, oldChapter: String, newChapter: String) {
        if (subject.isBlank() || oldChapter.isBlank() || newChapter.isBlank() || oldChapter == newChapter) return
        viewModelScope.launch {
            val chaptersRaw = prefs.getStringSet("chapters", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (chaptersRaw.remove("$subject||$oldChapter")) {
                chaptersRaw.add("$subject||$newChapter")
                prefs.edit().putStringSet("chapters", chaptersRaw).apply()

                val notes = dao.getAllNotesDirect()
                notes.forEach { note ->
                    if (note.subject == subject && note.chapter == oldChapter) {
                        dao.insertNote(note.copy(chapter = newChapter))
                    }
                }

                loadFolders()
            }
        }
    }

    // Loading states
    val isAILoading = MutableStateFlow(false)
    val apiErrorFeedback = MutableStateFlow<String?>(null)

    // Navigation & PDF contextual attach states
    val selectedSubTab = MutableStateFlow("Files") // "Files", "Flashcards", "Quizzes", "Summarizer", "Mindmap"
    val summarizerActiveMode = MutableStateFlow("SUMMARIZER") // "SUMMARIZER" or "FORMULA"
    val attachedFileForGeneration = MutableStateFlow<NoteEntry?>(null)
    val isDarkModeOverride = MutableStateFlow<Boolean?>(null) // null = system adaptive, true = forced dark, false = forced light
    val searchQuery = MutableStateFlow("")

    // Local file picker copier helper
    fun copyUriToLocalStorage(uri: Uri, originalName: String, fileType: String): String? {
        val extension = originalName.substringAfterLast('.', "")
        val localFileName = "notes_${System.currentTimeMillis()}.${if (extension.isNotEmpty()) extension else "dat"}"
        val localFile = File(getApplication<Application>().filesDir, localFileName)
        return try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            localFile.absolutePath
        } catch (e: Exception) {
            Log.e("StudyMateVM", "Error copying file", e)
            null
        }
    }

    fun formatMarkdownAndLatexToSpannable(input: String): android.text.SpannableStringBuilder {
        val ssb = android.text.SpannableStringBuilder()
        val lines = input.split("\n")
        
        for (index in lines.indices) {
            var line = lines[index]
            val isLastLine = index == lines.size - 1
            
            // Clean up LaTeX parts before applying formatting
            line = formatLatexToUnicode(line)
            
            val startPos = ssb.length
            
            if (line.startsWith("# ")) {
                val cleanLine = line.removePrefix("# ")
                ssb.append(cleanLine)
                ssb.setSpan(
                    android.text.style.AbsoluteSizeSpan(18, true),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val h1Color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(220, 210, 255) else android.graphics.Color.rgb(12, 8, 36)
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(h1Color), // Deep Violet or Lavendar
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else if (line.startsWith("## ")) {
                val cleanLine = line.removePrefix("## ")
                ssb.append(cleanLine)
                ssb.setSpan(
                    android.text.style.AbsoluteSizeSpan(15, true),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val h2Color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(190, 180, 255) else android.graphics.Color.rgb(44, 30, 115)
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(h2Color), // Indigo or Pastel Indigo
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else if (line.startsWith("### ")) {
                val cleanLine = line.removePrefix("### ")
                ssb.append(cleanLine)
                ssb.setSpan(
                    android.text.style.AbsoluteSizeSpan(13, true),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val h3Color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(170, 160, 255) else android.graphics.Color.rgb(66, 50, 145)
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(h3Color),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else if (line.startsWith("#### ")) {
                val cleanLine = line.removePrefix("#### ")
                ssb.append(cleanLine)
                ssb.setSpan(
                    android.text.style.AbsoluteSizeSpan(12, true),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val h4Color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(150, 140, 255) else android.graphics.Color.rgb(80, 60, 160)
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(h4Color),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else if (line.startsWith("##### ")) {
                val cleanLine = line.removePrefix("##### ")
                ssb.append(cleanLine)
                ssb.setSpan(
                    android.text.style.AbsoluteSizeSpan(11, true),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val h5Color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(130, 110, 240) else android.graphics.Color.rgb(100, 70, 180)
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(h5Color),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else if (line.startsWith("###### ")) {
                val cleanLine = line.removePrefix("###### ")
                ssb.append(cleanLine)
                ssb.setSpan(
                    android.text.style.AbsoluteSizeSpan(10, true),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val h6Color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(120, 100, 220) else android.graphics.Color.rgb(120, 80, 200)
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(h6Color),
                    startPos,
                    ssb.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                // Regular line, but handle bold markdown (**text**)
                // Also check if line starts with lists
                var processedLine = line
                var isListItem = false
                if (processedLine.startsWith("- ")) {
                    processedLine = "• " + processedLine.substring(2)
                    isListItem = true
                } else if (processedLine.startsWith("* ")) {
                    processedLine = "• " + processedLine.substring(2)
                    isListItem = true
                }
                
                ssb.append(processedLine)
                
                if (isListItem) {
                    ssb.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        startPos,
                        startPos + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    val bulletColor = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(179, 157, 219) else android.graphics.Color.rgb(103, 58, 183)
                    ssb.setSpan(
                        android.text.style.ForegroundColorSpan(bulletColor), // Purple bullet
                        startPos,
                        startPos + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            
            if (!isLastLine) {
                ssb.append("\n")
            }
        }
        
        // Post-processing: let's replace all **bold** marks in the entire builder in a clean way!
        var boldStart = ssb.indexOf("**")
        while (boldStart != -1) {
            val boldEnd = ssb.indexOf("**", boldStart + 2)
            if (boldEnd != -1) {
                // Delete the ending "**"
                ssb.delete(boldEnd, boldEnd + 2)
                // Delete the starting "**"
                ssb.delete(boldStart, boldStart + 2)
                // Set span on the inner text
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    boldStart,
                    boldEnd - 2,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // Also colorize bold terms nicely (dark slate or white)
                val boldColor = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(255, 255, 255) else android.graphics.Color.rgb(0, 0, 0)
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(boldColor),
                    boldStart,
                    boldEnd - 2,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                boldStart = ssb.indexOf("**", boldEnd - 2)
            } else {
                break
            }
        }

        // Style lines that look like mathematical formulas/equations
        val entireText = ssb.toString()
        val textLines = entireText.split("\n")
        var currentOffset = 0
        for (tline in textLines) {
            if (tline.contains("=") && (tline.contains("+") || tline.contains("-") || tline.contains("·") || tline.contains("/") || tline.contains("×") || tline.any { it in '⁰'..'⁹' || it in '₀'..'₉' })) {
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC),
                    currentOffset,
                    currentOffset + tline.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val formulaColor = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(170, 180, 255) else android.graphics.Color.rgb(26, 35, 126)
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(formulaColor), // Dark Indigo or Pastel formula
                    currentOffset,
                    currentOffset + tline.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            currentOffset += tline.length + 1
        }
        
        return ssb
    }

    private sealed class PdfBlock {
        abstract fun getHeight(width: Int, textPaint: android.text.TextPaint): Float
        abstract fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, width: Int, textPaint: android.text.TextPaint)
    }

    private inner class HrBlock : PdfBlock() {
        override fun getHeight(width: Int, textPaint: android.text.TextPaint): Float {
            return 20f
        }
        override fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, width: Int, textPaint: android.text.TextPaint) {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(180, 180, 180)
                strokeWidth = 1.5f
                style = android.graphics.Paint.Style.STROKE
            }
            val centerY = y + 10f
            canvas.drawLine(x, centerY, x + width, centerY, paint)
        }
    }

    private inner class TextBlock(val text: String) : PdfBlock() {
        private var cachedLayout: android.text.StaticLayout? = null
        private var cachedWidth: Int = -1

        private fun getLayout(width: Int, textPaint: android.text.TextPaint): android.text.StaticLayout {
            if (cachedLayout != null && cachedWidth == width) {
                return cachedLayout!!
            }
            val spannableText = formatMarkdownAndLatexToSpannable(text)
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.text.StaticLayout.Builder.obtain(spannableText, 0, spannableText.length, textPaint, width)
                    .setLineSpacing(0f, 1.15f)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.text.StaticLayout(spannableText, textPaint, width, android.text.Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
            }
            cachedLayout = layout
            cachedWidth = width
            return layout
        }

        override fun getHeight(width: Int, textPaint: android.text.TextPaint): Float {
            return getLayout(width, textPaint).height.toFloat()
        }

        override fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, width: Int, textPaint: android.text.TextPaint) {
            canvas.save()
            canvas.translate(x, y)
            getLayout(width, textPaint).draw(canvas)
            canvas.restore()
        }
    }

    private inner class TableBlock(
        val headers: List<String>,
        val alignments: List<String>,
        val rows: List<List<String>>
    ) : PdfBlock() {
        private val paddingX = 8f
        private val paddingY = 8f
        private val headerBgColor get() = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(40, 25, 70) else android.graphics.Color.rgb(235, 230, 255)
        private val headerTextColor get() = if (renderingPdfInDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.rgb(12, 8, 36)
        private val gridColor get() = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(90, 90, 90) else android.graphics.Color.rgb(210, 210, 210)
        private val rowAltColor get() = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(40, 43, 46) else android.graphics.Color.rgb(250, 250, 252)
        private val rowTextColor get() = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(230, 230, 230) else android.graphics.Color.rgb(33, 33, 33)

        private var formattedHeaders: List<android.text.SpannableStringBuilder> = emptyList()
        private var formattedRows: List<List<android.text.SpannableStringBuilder>> = emptyList()

        var colWidths: List<Float> = emptyList()
        var rowHeights: List<Float> = emptyList()
        var headerHeight: Float = 0f

        fun calculateLayout(width: Int, paint: android.text.TextPaint) {
            val numCols = headers.size
            if (numCols == 0) return

            if (formattedHeaders.isEmpty()) {
                formattedHeaders = headers.map { formatMarkdownAndLatexToSpannable(it) }
                formattedRows = rows.map { row -> row.map { formatMarkdownAndLatexToSpannable(it) } }
            }

            val maxContentLengths = FloatArray(numCols)
            val cellPaint = android.text.TextPaint(paint).apply {
                textSize = 10f
            }

            for (col in 0 until numCols) {
                var maxLen = cellPaint.measureText(formattedHeaders[col], 0, formattedHeaders[col].length)
                for (rowIdx in formattedRows.indices) {
                    val row = formattedRows[rowIdx]
                    if (col < row.size) {
                        val len = cellPaint.measureText(row[col], 0, row[col].length)
                        if (len > maxLen) {
                            maxLen = len
                        }
                    }
                }
                maxContentLengths[col] = maxLen + 2 * paddingX
            }

            val totalRequired = maxContentLengths.sum()
            val calculatedWidths = FloatArray(numCols)
            if (totalRequired <= width) {
                val extra = (width - totalRequired) / numCols
                for (col in 0 until numCols) {
                    calculatedWidths[col] = maxContentLengths[col] + extra
                }
            } else {
                val minColWidth = width.toFloat() / numCols * 0.5f
                var remainingWidth = width.toFloat()
                var columnsToDistribute = numCols
                val fixedCols = BooleanArray(numCols)

                var iterations = 0
                while (iterations < numCols) {
                    var changed = false
                    val share = remainingWidth / columnsToDistribute
                    for (col in 0 until numCols) {
                        if (!fixedCols[col] && maxContentLengths[col] < share && maxContentLengths[col] < minColWidth) {
                            calculatedWidths[col] = minColWidth
                            remainingWidth -= minColWidth
                            columnsToDistribute--
                            fixedCols[col] = true
                            changed = true
                        }
                    }
                    if (!changed) break
                    iterations++
                }

                if (columnsToDistribute > 0) {
                    val share = remainingWidth / columnsToDistribute
                    for (col in 0 until numCols) {
                        if (!fixedCols[col]) {
                            calculatedWidths[col] = share
                        }
                    }
                }
            }
            colWidths = calculatedWidths.toList()

            var maxHeaderHeight = 0f
            for (col in 0 until numCols) {
                val cellWidth = colWidths[col] - 2 * paddingX
                val text = formattedHeaders[col]
                val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.text.StaticLayout.Builder.obtain(text, 0, text.length, cellPaint, cellWidth.toInt().coerceAtLeast(10))
                        .setLineSpacing(0f, 1.0f)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    android.text.StaticLayout(text, cellPaint, cellWidth.toInt().coerceAtLeast(10), android.text.Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
                }
                val h = layout.height.toFloat() + 2 * paddingY
                if (h > maxHeaderHeight) {
                    maxHeaderHeight = h
                }
            }
            headerHeight = maxHeaderHeight

            val calculatedRowHeights = FloatArray(rows.size)
            for (r in rows.indices) {
                val row = formattedRows[r]
                var maxRowHeight = 0f
                for (col in 0 until numCols) {
                    val text = if (col < row.size) row[col] else android.text.SpannableStringBuilder("")
                    val cellWidth = colWidths[col] - 2 * paddingX
                    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        android.text.StaticLayout.Builder.obtain(text, 0, text.length, cellPaint, cellWidth.toInt().coerceAtLeast(10))
                            .setLineSpacing(0f, 1.0f)
                            .build()
                    } else {
                        @Suppress("DEPRECATION")
                        android.text.StaticLayout(text, cellPaint, cellWidth.toInt().coerceAtLeast(10), android.text.Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
                    }
                    val h = layout.height.toFloat() + 2 * paddingY
                    if (h > maxRowHeight) {
                        maxRowHeight = h
                    }
                }
                calculatedRowHeights[r] = maxRowHeight.coerceAtLeast(24f)
            }
            rowHeights = calculatedRowHeights.toList()
        }

        override fun getHeight(width: Int, textPaint: android.text.TextPaint): Float {
            if (colWidths.isEmpty()) {
                calculateLayout(width, textPaint)
            }
            return headerHeight + rowHeights.sum() + 10f
        }

        override fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, width: Int, textPaint: android.text.TextPaint) {
            if (colWidths.isEmpty()) {
                calculateLayout(width, textPaint)
            }
            val numCols = headers.size
            if (numCols == 0) return

            val cellPaint = android.text.TextPaint(textPaint).apply {
                textSize = 10f
                isAntiAlias = true
            }

            val fillPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
            }

            val strokePaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                color = gridColor
                strokeWidth = 1f
            }

            var currentY = y

            fillPaint.color = headerBgColor
            canvas.drawRect(x, currentY, x + width, currentY + headerHeight, fillPaint)

            var currentX = x
            for (col in 0 until numCols) {
                val cellWidth = colWidths[col]
                val text = formattedHeaders[col]
                val align = if (col < alignments.size) alignments[col] else "left"
                val textAlignment = when (align) {
                    "center" -> android.text.Layout.Alignment.ALIGN_CENTER
                    "right" -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                    else -> android.text.Layout.Alignment.ALIGN_NORMAL
                }

                cellPaint.color = headerTextColor
                cellPaint.isFakeBoldText = true

                val textInnerWidth = cellWidth - 2 * paddingX
                val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.text.StaticLayout.Builder.obtain(text, 0, text.length, cellPaint, textInnerWidth.toInt().coerceAtLeast(10))
                        .setAlignment(textAlignment)
                        .setLineSpacing(0f, 1.0f)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    android.text.StaticLayout(text, cellPaint, textInnerWidth.toInt().coerceAtLeast(10), textAlignment, 1.0f, 0f, false)
                }

                canvas.save()
                val textHeight = layout.height.toFloat()
                val textY = currentY + (headerHeight - textHeight) / 2f
                canvas.translate(currentX + paddingX, textY)
                layout.draw(canvas)
                canvas.restore()

                canvas.drawLine(currentX, currentY, currentX, currentY + headerHeight, strokePaint)
                currentX += cellWidth
            }
            canvas.drawLine(x + width, currentY, x + width, currentY + headerHeight, strokePaint)
            canvas.drawLine(x, currentY, x + width, currentY, strokePaint)
            canvas.drawLine(x, currentY + headerHeight, x + width, currentY + headerHeight, strokePaint)

            currentY += headerHeight

            for (r in formattedRows.indices) {
                val row = formattedRows[r]
                val rowHeight = rowHeights[r]

                if (r % 2 == 1) {
                    fillPaint.color = rowAltColor
                } else {
                    fillPaint.color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(30, 33, 36) else android.graphics.Color.WHITE
                }
                canvas.drawRect(x, currentY, x + width, currentY + rowHeight, fillPaint)

                currentX = x
                for (col in 0 until numCols) {
                    val cellWidth = colWidths[col]
                    val text = if (col < row.size) row[col] else android.text.SpannableStringBuilder("")
                    val align = if (col < alignments.size) alignments[col] else "left"
                    val textAlignment = when (align) {
                        "center" -> android.text.Layout.Alignment.ALIGN_CENTER
                        "right" -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                        else -> android.text.Layout.Alignment.ALIGN_NORMAL
                    }

                    cellPaint.color = rowTextColor
                    cellPaint.isFakeBoldText = false

                    val textInnerWidth = cellWidth - 2 * paddingX
                    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        android.text.StaticLayout.Builder.obtain(text, 0, text.length, cellPaint, textInnerWidth.toInt().coerceAtLeast(10))
                            .setAlignment(textAlignment)
                            .setLineSpacing(0f, 1.0f)
                            .build()
                    } else {
                        @Suppress("DEPRECATION")
                        android.text.StaticLayout(text, cellPaint, textInnerWidth.toInt().coerceAtLeast(10), textAlignment, 1.0f, 0f, false)
                    }

                    canvas.save()
                    val textHeight = layout.height.toFloat()
                    val textY = currentY + (rowHeight - textHeight) / 2f
                    canvas.translate(currentX + paddingX, textY)
                    layout.draw(canvas)
                    canvas.restore()

                    canvas.drawLine(currentX, currentY, currentX, currentY + rowHeight, strokePaint)
                    currentX += cellWidth
                }

                canvas.drawLine(x + width, currentY, x + width, currentY + rowHeight, strokePaint)
                canvas.drawLine(x, currentY + rowHeight, x + width, currentY + rowHeight, strokePaint)

                currentY += rowHeight
            }
        }
    }

    private fun convertHtmlToPlainText(html: String): String {
        var text = html
        // Remove style blocks completely
        text = text.replace(Regex("(?s)<style>.*?</style>"), "")
        // Replace common block/break tags with newlines
        text = text.replace(Regex("(?i)</?div>"), "\n")
        text = text.replace(Regex("(?i)</?p>"), "\n")
        text = text.replace(Regex("(?i)</?li>"), "\n")
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</?h[1-6]>"), "\n")
        // Strip all remaining HTML tags
        text = text.replace(Regex("<[^>]*>"), "")
        // Unescape common HTML entities
        text = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&rarr;", "➔")
            .replace("&larr;", "←")
            .replace("&#10145;", "➔")
            .replace("&bull;", "•")
        
        // Clean up multiple newlines or spaces
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return lines.joinToString("\n")
    }

    private fun android.content.Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private suspend fun renderHtmlToBitmap(htmlContent: String, width: Int, height: Int, context: Context): android.graphics.Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        val result = kotlinx.coroutines.CompletableDeferred<android.graphics.Bitmap?>()
        val activity = context.findActivity()
        var webView: android.webkit.WebView? = null
        var attached = false
        val initialWidth = 1200
        val initialHeight = 1200

        try {
            try {
                android.webkit.WebView.enableSlowWholeDocumentDraw()
            } catch (e: Exception) {
                // Ignore if not supported or already initialized
            }

            val wv = android.webkit.WebView(context)
            webView = wv

            wv.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(initialWidth, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(initialHeight, android.view.View.MeasureSpec.EXACTLY)
            )
            wv.layout(0, 0, initialWidth, initialHeight)
            
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.useWideViewPort = true
            wv.settings.loadWithOverviewMode = true
            wv.settings.offscreenPreRaster = true
            wv.setWillNotDraw(false)
            
            val styledHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        html, body {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            background-color: #1A1348;
                            color: #FFFFFF;
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            box-sizing: border-box;
                        }
                        .diagram-wrapper {
                            width: 100%;
                            padding: 16px;
                            box-sizing: border-box;
                            display: flex;
                            flex-direction: column;
                            justify-content: center;
                            align-items: center;
                            text-align: center;
                        }
                    </style>
                </head>
                <body>
                    <div class="diagram-wrapper">
                        $htmlContent
                    </div>
                </body>
                </html>
            """.trimIndent()
            
            if (activity != null) {
                val decorView = activity.window.decorView as? android.view.ViewGroup
                if (decorView != null) {
                    wv.visibility = android.view.View.INVISIBLE
                    wv.translationX = 10000f
                    wv.translationY = 10000f
                    decorView.addView(wv, android.view.ViewGroup.LayoutParams(initialWidth, initialHeight))
                    attached = true
                }
            }

            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    wv.postDelayed({
                        wv.evaluateJavascript(
                            "(function() {\n" +
                            "  var wrapper = document.querySelector('.diagram-wrapper');\n" +
                            "  if (!wrapper) return '800,500';\n" +
                            "  var svg = wrapper.querySelector('svg');\n" +
                            "  if (svg) {\n" +
                            "    var viewbox = svg.getAttribute('viewBox');\n" +
                            "    if (viewbox) {\n" +
                            "      var parts = viewbox.trim().split(/[\\s,]+/);\n" +
                            "      if (parts.length >= 4) {\n" +
                            "        var vbW = parseFloat(parts[2]);\n" +
                            "        var vbH = parseFloat(parts[3]);\n" +
                            "        if (!isNaN(vbW) && vbW > 0 && !isNaN(vbH) && vbH > 0) {\n" +
                            "          return Math.ceil(vbW + 32) + ',' + Math.ceil(vbH + 32);\n" +
                            "        }\n" +
                            "      }\n" +
                            "    }\n" +
                            "    var svgW = parseFloat(svg.getAttribute('width'));\n" +
                            "    var svgH = parseFloat(svg.getAttribute('height'));\n" +
                            "    if (!isNaN(svgW) && svgW > 0 && !isNaN(svgH) && svgH > 0) {\n" +
                            "      return Math.ceil(svgW + 32) + ',' + Math.ceil(svgH + 32);\n" +
                            "    }\n" +
                            "  }\n" +
                            "  var originalWidthStyle = wrapper.style.width;\n" +
                            "  var originalDisplay = wrapper.style.display;\n" +
                            "  wrapper.style.display = 'inline-block';\n" +
                            "  wrapper.style.width = 'auto';\n" +
                            "  var scrollW = wrapper.scrollWidth || wrapper.offsetWidth;\n" +
                            "  var scrollH = wrapper.scrollHeight || wrapper.offsetHeight;\n" +
                            "  var elements = wrapper.querySelectorAll('*');\n" +
                            "  var maxRight = 0;\n" +
                            "  var maxBottom = 0;\n" +
                            "  for (var i = 0; i < elements.length; i++) {\n" +
                            "    var rightVal = elements[i].offsetLeft + elements[i].offsetWidth;\n" +
                            "    var bottomVal = elements[i].offsetTop + elements[i].offsetHeight;\n" +
                            "    if (rightVal > maxRight) maxRight = rightVal;\n" +
                            "    if (bottomVal > maxBottom) maxBottom = bottomVal;\n" +
                            "  }\n" +
                            "  wrapper.style.display = originalDisplay;\n" +
                            "  wrapper.style.width = originalWidthStyle;\n" +
                            "  var finalW = Math.max(scrollW, maxRight, 600);\n" +
                            "  var finalH = Math.max(scrollH, maxBottom, 400);\n" +
                            "  return Math.ceil(finalW + 32) + ',' + Math.ceil(finalH + 32);\n" +
                            "})()"
                        ) { dimensionsStr ->
                            val parts = dimensionsStr?.replace("\"", "")?.trim()?.split(",")
                            var finalW = initialWidth
                            var finalH = initialHeight
                            if (parts != null && parts.size == 2) {
                                val parsedW = parts[0].toDoubleOrNull()?.toInt() ?: 0
                                val parsedH = parts[1].toDoubleOrNull()?.toInt() ?: 0
                                if (parsedW > 0 && parsedH > 0) {
                                    finalW = parsedW
                                    finalH = parsedH
                                }
                            }
                            
                            wv.post {
                                try {
                                    wv.measure(
                                        android.view.View.MeasureSpec.makeMeasureSpec(finalW, android.view.View.MeasureSpec.EXACTLY),
                                        android.view.View.MeasureSpec.makeMeasureSpec(finalH, android.view.View.MeasureSpec.EXACTLY)
                                    )
                                    wv.layout(0, 0, finalW, finalH)
                                    
                                    val bitmap = android.graphics.Bitmap.createBitmap(finalW, finalH, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    wv.draw(canvas)
                                    result.complete(bitmap)
                                } catch (e: Exception) {
                                    Log.e("StudyMateVM", "Error drawing WebView to Canvas", e)
                                    result.complete(null)
                                }
                            }
                        }
                    }, 600)
                }
                
                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    result.complete(null)
                }
            }
            
            wv.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Log.e("StudyMateVM", "Error in renderHtmlToBitmap setup", e)
            result.complete(null)
        }
        
        val rendered = try {
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                result.await()
            }
        } catch (e: Exception) {
            null
        } finally {
            if (attached && activity != null && webView != null) {
                try {
                    (activity.window.decorView as? android.view.ViewGroup)?.removeView(webView)
                } catch (e: Exception) {
                    Log.e("StudyMateVM", "Error removing webview from decorView", e)
                }
            }
        }
        rendered
    }

    private inner class HtmlDiagramBlock(val htmlContent: String) : PdfBlock() {
        private var loadedBitmap: android.graphics.Bitmap? = null
        private var isLoadAttempted = false

        suspend fun loadBitmap(context: Context, width: Int) {
            if (isLoadAttempted) return
            isLoadAttempted = true
            
            var cleanCode = htmlContent.trim()
            if (cleanCode.startsWith("```")) {
                cleanCode = cleanCode.replace(Regex("^```[a-zA-Z0-9_-]*\\n"), "")
                if (cleanCode.endsWith("```")) {
                    cleanCode = cleanCode.substring(0, cleanCode.length - 3).trim()
                }
            }
            if (cleanCode.startsWith("[diagram]")) {
                cleanCode = cleanCode.substring("[diagram]".length).trim()
            }
            
            loadedBitmap = renderHtmlToBitmap(cleanCode, width, 800, context)
        }

        private val cleanLines: List<String> by lazy {
            val stripped = convertHtmlToPlainText(htmlContent)
            stripped.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }

        override fun getHeight(width: Int, textPaint: android.text.TextPaint): Float {
            val bitmap = loadedBitmap
            if (bitmap != null && bitmap.width > 0) {
                val scaleWidth = width.toFloat() / bitmap.width.toFloat()
                var scaledHeight = bitmap.height.toFloat() * scaleWidth
                val maxAllowedHeight = 640f
                if (scaledHeight > maxAllowedHeight) {
                    scaledHeight = maxAllowedHeight
                }
                return scaledHeight + 10f
            } else {
                val lineCount = cleanLines.size.coerceAtMost(20)
                return 24f + 20f + (lineCount * 15f) + 30f
            }
        }

        override fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, width: Int, textPaint: android.text.TextPaint) {
            val bitmap = loadedBitmap
            if (bitmap != null && bitmap.width > 0) {
                val h = getHeight(width, textPaint) - 10f
                val srcAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                val destAspect = width.toFloat() / h
                
                val drawRect = if (srcAspect > destAspect) {
                    val drawH = width.toFloat() / srcAspect
                    val topOffset = (h - drawH) / 2f
                    android.graphics.RectF(x, y + 5f + topOffset, x + width, y + 5f + topOffset + drawH)
                } else {
                    val drawW = h * srcAspect
                    val leftOffset = (width.toFloat() - drawW) / 2f
                    android.graphics.RectF(x + leftOffset, y + 5f, x + leftOffset + drawW, y + 5f + h)
                }
                
                canvas.drawBitmap(bitmap, null, drawRect, null)
            } else {
                val cardPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(245, 244, 252)
                    style = android.graphics.Paint.Style.FILL
                }
                
                val borderPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(110, 93, 211)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.5f
                    isAntiAlias = true
                }
                
                val blockHeight = getHeight(width, textPaint) - 15f
                val rect = android.graphics.RectF(x, y, x + width, y + blockHeight)
                
                canvas.drawRoundRect(rect, 8f, 8f, cardPaint)
                canvas.drawRoundRect(rect, 8f, 8f, borderPaint)
                
                val headerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(110, 93, 211)
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRect(x + 1, y + 1, x + width - 1, y + 26f, headerPaint)
                
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 10f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                canvas.drawText("📊 SYSTEM FLOW / STUDY DIAGRAM", x + 12f, y + 17f, titlePaint)
                
                val contentPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(50, 40, 100)
                    textSize = 9.5f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.SANS_SERIF
                }
                
                var currentY = y + 45f
                for (idx in 0 until minOf(cleanLines.size, 20)) {
                    val rawLine = cleanLines[idx]
                    val maxChars = (width / 6f).toInt()
                    val truncatedLine = if (rawLine.length > maxChars) rawLine.substring(0, maxChars - 3) + "..." else rawLine
                    
                    if (truncatedLine.contains("➔") || truncatedLine.contains("➡") || truncatedLine.contains("->")) {
                        val arrowPaint = android.graphics.Paint(contentPaint).apply {
                            color = android.graphics.Color.rgb(110, 93, 211)
                            isFakeBoldText = true
                        }
                        canvas.drawText("  $truncatedLine", x + 16f, currentY, arrowPaint)
                    } else {
                        canvas.drawText("  • $truncatedLine", x + 16f, currentY, contentPaint)
                    }
                    currentY += 15f
                }
                
                if (cleanLines.size > 20) {
                    val metaPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(120, 120, 150)
                        textSize = 8.5f
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    canvas.drawText("  [... view full interactive layout inside StudyMate Notebook ...]", x + 16f, currentY + 2f, metaPaint)
                }
            }
        }
    }

    private fun partitionTextIntoBlocks(textContent: String): List<PdfBlock> {
        val lines = textContent.split("\n")
        val blocks = mutableListOf<PdfBlock>()
        
        var i = 0
        val currentTextLines = mutableListOf<String>()
        
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            
            if (trimmed.startsWith("```")) {
                var j = i + 1
                val blockLines = mutableListOf<String>()
                while (j < lines.size && !lines[j].trim().startsWith("```")) {
                    blockLines.add(lines[j])
                    j++
                }
                val blockText = blockLines.joinToString("\n")
                
                val isDiagram = trimmed.startsWith("```html") || 
                                trimmed.startsWith("```xml") || 
                                trimmed.startsWith("```svg") || 
                                trimmed.startsWith("```css") || 
                                trimmed.startsWith("```mermaid") || 
                                trimmed.startsWith("```diagram") || 
                                blockText.contains("[diagram]")
                
                if (isDiagram) {
                    if (currentTextLines.isNotEmpty()) {
                        blocks.add(TextBlock(currentTextLines.joinToString("\n")))
                        currentTextLines.clear()
                    }
                    
                    blocks.add(HtmlDiagramBlock(blockText))
                    i = j + 1
                    continue
                }
            }
            
            val isHr = trimmed.matches(Regex("^[\\-*\\_]{3,}\\s*$"))
            if (isHr) {
                if (currentTextLines.isNotEmpty()) {
                    blocks.add(TextBlock(currentTextLines.joinToString("\n")))
                    currentTextLines.clear()
                }
                blocks.add(HrBlock())
                i++
                continue
            }
            
            if (trimmed.startsWith("|") && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                if (nextLine.startsWith("|") && nextLine.contains("-") && nextLine.filter { it != '|' && it != '-' && it != ':' && it != ' ' && it != '\t' }.isEmpty()) {
                    if (currentTextLines.isNotEmpty()) {
                        blocks.add(TextBlock(currentTextLines.joinToString("\n")))
                        currentTextLines.clear()
                    }
                    
                    val tableLines = mutableListOf<String>()
                    tableLines.add(line)
                    tableLines.add(lines[i + 1])
                    i += 2
                    
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        tableLines.add(lines[i])
                        i++
                    }
                    
                    val tableBlock = parseMarkdownTable(tableLines)
                    if (tableBlock != null) {
                        blocks.add(tableBlock)
                    } else {
                        blocks.add(TextBlock(tableLines.joinToString("\n")))
                    }
                    continue
                }
            }
            
            currentTextLines.add(line)
            i++
        }
        
        if (currentTextLines.isNotEmpty()) {
            blocks.add(TextBlock(currentTextLines.joinToString("\n")))
        }
        
        return blocks
    }

    private fun splitTableLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val currentCell = StringBuilder()
        var inSingleDollar = false
        var inDoubleDollar = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            
            if (c == '\\' && i + 1 < line.length) {
                val next = line[i + 1]
                if (next == '|') {
                    currentCell.append('|')
                } else {
                    currentCell.append(c)
                    currentCell.append(next)
                }
                i += 2
                continue
            }
            
            if (c == '$' && i + 1 < line.length && line[i + 1] == '$') {
                inDoubleDollar = !inDoubleDollar
                currentCell.append("$$")
                i += 2
                continue
            }
            
            if (c == '$' && !inDoubleDollar) {
                inSingleDollar = !inSingleDollar
                currentCell.append('$')
                i++
                continue
            }
            
            if (c == '|' && !inSingleDollar && !inDoubleDollar) {
                cells.add(currentCell.toString().trim())
                currentCell.setLength(0)
            } else {
                currentCell.append(c)
            }
            i++
        }
        cells.add(currentCell.toString().trim())
        return cells
    }

    private fun parseMarkdownTable(lines: List<String>): TableBlock? {
        if (lines.size < 2) return null
        
        val line0Trimmed = lines[0].trim()
        val rawHeaderCells = splitTableLine(line0Trimmed)
        val headerCells = if (line0Trimmed.startsWith("|") && line0Trimmed.endsWith("|") && rawHeaderCells.size >= 2) {
            rawHeaderCells.subList(1, rawHeaderCells.size - 1)
        } else {
            rawHeaderCells
        }
        if (headerCells.isEmpty()) return null
        
        val line1Trimmed = lines[1].trim()
        val rawSepCells = splitTableLine(line1Trimmed)
        val sepCells = if (line1Trimmed.startsWith("|") && line1Trimmed.endsWith("|") && rawSepCells.size >= 2) {
            rawSepCells.subList(1, rawSepCells.size - 1)
        } else {
            rawSepCells
        }
        
        val alignments = sepCells.map { cell ->
            val c = cell.trim()
            when {
                c.startsWith(":") && c.endsWith(":") -> "center"
                c.endsWith(":") -> "right"
                else -> "left"
            }
        }
        
        val rows = mutableListOf<List<String>>()
        for (idx in 2 until lines.size) {
            val rowLine = lines[idx]
            val rowLineTrimmed = rowLine.trim()
            val rawRowCells = splitTableLine(rowLineTrimmed)
            val cleanCells = if (rowLineTrimmed.startsWith("|") && rowLineTrimmed.endsWith("|") && rawRowCells.size >= 2) {
                rawRowCells.subList(1, rawRowCells.size - 1)
            } else {
                rawRowCells
            }
            
            val rowData = List(headerCells.size) { colIdx ->
                if (colIdx < cleanCells.size) cleanCells[colIdx] else ""
            }
            rows.add(rowData)
        }
        
        return TableBlock(headerCells, alignments, rows)
    }

    private fun looksLikeMermaidLine(trimmedLine: String): Boolean {
        if (trimmedLine.isBlank()) return false
        val keywords = listOf("graph", "flowchart", "subgraph", "end", "style", "linkStyle", "click", "classDef", "class", "sequenceDiagram", "classDiagram", "stateDiagram", "erDiagram", "gantt", "pie", "gitGraph", "mindmap", "timeline")
        if (keywords.any { trimmedLine.startsWith(it) }) return true
        
        val operators = listOf("-->", "---", "==>", "-.->", "->", "|", "[\"", "(\"", "{\"", "rx:", "ry:", "fill:", "stroke:")
        if (operators.any { trimmedLine.contains(it) }) return true
        
        val regex = Regex("\\b[a-zA-Z0-9_-]+\\s*[\\[\\({]")
        if (regex.containsMatchIn(trimmedLine)) return true
        
        return false
    }

    fun autoWrapRawMermaid(text: String): String {
        val lines = text.split("\n")
        val result = mutableListOf<String>()
        var inRawMermaid = false
        val mermaidBlock = mutableListOf<String>()
        
        val mermaidKeywords = listOf(
            "graph TD", "graph LR", "graph BT", "graph RL",
            "flowchart TD", "flowchart LR", "flowchart BT", "flowchart RL",
            "sequenceDiagram", "classDiagram", "stateDiagram", "erDiagram",
            "gantt", "pie", "gitGraph", "mindmap", "timeline"
        )
        
        var inExistingCodeBlock = false
        
        for (line in lines) {
            val trimmed = line.trim()
            
            if (trimmed.startsWith("```")) {
                if (inRawMermaid) {
                    result.add("```mermaid")
                    result.addAll(mermaidBlock)
                    result.add("```")
                    mermaidBlock.clear()
                    inRawMermaid = false
                }
                inExistingCodeBlock = !inExistingCodeBlock
                result.add(line)
                continue
            }
            
            if (inExistingCodeBlock) {
                result.add(line)
                continue
            }
            
            if (!inRawMermaid) {
                val startsMermaid = mermaidKeywords.any { keyword -> 
                    trimmed.startsWith(keyword) || 
                    (trimmed.contains(keyword) && (trimmed.contains("-->") || trimmed.contains("---") || trimmed.contains("style ") || trimmed.contains("linkStyle ")))
                }
                if (startsMermaid) {
                    inRawMermaid = true
                    mermaidBlock.add(line)
                } else {
                    result.add(line)
                }
            } else {
                val endsMermaid = !looksLikeMermaidLine(trimmed)
                                 
                if (endsMermaid) {
                    result.add("```mermaid")
                    result.addAll(mermaidBlock)
                    result.add("```")
                    mermaidBlock.clear()
                    inRawMermaid = false
                    
                    result.add(line)
                } else {
                    mermaidBlock.add(line)
                }
            }
        }
        
        if (inRawMermaid && mermaidBlock.isNotEmpty()) {
            result.add("```mermaid")
            result.addAll(mermaidBlock)
            result.add("```")
        }
        
        return result.joinToString("\n")
    }

    private var renderingPdfInDarkTheme = false

    private suspend fun writeTextAsPdfToStream(context: android.content.Context, title: String, textContent: String, outputStream: java.io.OutputStream, isDarkTheme: Boolean = false) {
        renderingPdfInDarkTheme = isDarkTheme
        val wrappedText = textContent // No autoWrapRawMermaid needed anymore since we do pure html/css diagrams
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageWidth = 595 // Standard A4 Width
        val pageHeight = 842 // Standard A4 Height
        val margin = 40
        val contentWidth = pageWidth - 2 * margin
        val contentHeight = pageHeight - 2 * margin - 70 // Leave space for beautiful header and footer

        val textPaint = android.text.TextPaint().apply {
            color = if (isDarkTheme) android.graphics.Color.rgb(230, 230, 230) else android.graphics.Color.rgb(33, 33, 33)
            textSize = 12f
            isAntiAlias = true
        }

        val blocks = partitionTextIntoBlocks(wrappedText)

        // Pre-load all HtmlDiagramBlocks asynchronously on Main Thread before pagination
        blocks.forEach { block ->
            if (block is HtmlDiagramBlock) {
                block.loadBitmap(context, contentWidth)
            }
        }

        val pages = mutableListOf<List<Pair<PdfBlock, Float>>>()
        
        var currentPage = mutableListOf<Pair<PdfBlock, Float>>()
        var currentY = 0f
        
        val blockQueue = java.util.LinkedList(blocks)
        
        while (blockQueue.isNotEmpty()) {
            val block = blockQueue.poll()!!
            val blockHeight = block.getHeight(contentWidth, textPaint)
            
            if (currentY + blockHeight <= contentHeight) {
                currentPage.add(Pair(block, currentY))
                currentY += blockHeight + 12f
            } else {
                if (block is TextBlock) {
                    val rawLines = block.text.split("\n")
                    var fitLineCount = 0
                    val remainingHeight = contentHeight - currentY
                    
                    for (i in 1..rawLines.size) {
                        val partialText = rawLines.subList(0, i).joinToString("\n")
                        val tempBlock = TextBlock(partialText)
                        val partialH = tempBlock.getHeight(contentWidth, textPaint)
                        if (partialH <= remainingHeight) {
                            fitLineCount = i
                        } else {
                            break
                        }
                    }
                    
                    if (fitLineCount > 0) {
                        val fitText = rawLines.subList(0, fitLineCount).joinToString("\n")
                        val remainingText = rawLines.subList(fitLineCount, rawLines.size).joinToString("\n")
                        
                        currentPage.add(Pair(TextBlock(fitText), currentY))
                        
                        if (remainingText.trim().isNotEmpty()) {
                            blockQueue.addFirst(TextBlock(remainingText))
                        }
                        
                        pages.add(currentPage)
                        currentPage = mutableListOf()
                        currentY = 0f
                    } else {
                        if (currentPage.isNotEmpty()) {
                            pages.add(currentPage)
                            currentPage = mutableListOf()
                            currentY = 0f
                            blockQueue.addFirst(block)
                        } else {
                            // Current page is empty but the first paragraph of this block is too long to fit in contentHeight.
                            // We split this first paragraph word-by-word to fit within contentHeight perfectly.
                            val firstLineText = rawLines.first()
                            val otherLinesText = if (rawLines.size > 1) rawLines.subList(1, rawLines.size).joinToString("\n") else ""
                            
                            val words = firstLineText.split(" ")
                            var low = 1
                            var high = words.size
                            var optimalWordCount = 0
                            
                            while (low <= high) {
                                val mid = (low + high) / 2
                                val partialText = words.subList(0, mid).joinToString(" ")
                                val tempBlock = TextBlock(partialText)
                                val partialH = tempBlock.getHeight(contentWidth, textPaint)
                                if (partialH <= contentHeight) {
                                    optimalWordCount = mid
                                    low = mid + 1
                                } else {
                                    high = mid - 1
                                }
                            }
                            
                            if (optimalWordCount > 0) {
                                val fitText = words.subList(0, optimalWordCount).joinToString(" ")
                                val remainingTextOfFirstLine = words.subList(optimalWordCount, words.size).joinToString(" ")
                                
                                currentPage.add(Pair(TextBlock(fitText), currentY))
                                
                                val remainingBuilder = java.lang.StringBuilder()
                                if (remainingTextOfFirstLine.trim().isNotEmpty()) {
                                    remainingBuilder.append(remainingTextOfFirstLine)
                                }
                                if (otherLinesText.isNotEmpty()) {
                                    if (remainingBuilder.isNotEmpty()) remainingBuilder.append("\n")
                                    remainingBuilder.append(otherLinesText)
                                }
                                
                                val remainingTotal = remainingBuilder.toString()
                                if (remainingTotal.trim().isNotEmpty()) {
                                    blockQueue.addFirst(TextBlock(remainingTotal))
                                }
                                
                                pages.add(currentPage)
                                currentPage = mutableListOf()
                                currentY = 0f
                            } else {
                                // Forced fallback split to ensure forward progress and prevent infinite loop
                                val splitIndex = maxOf(1, words.size / 2)
                                val fitText = words.subList(0, splitIndex).joinToString(" ")
                                val remainingTextOfFirstLine = words.subList(splitIndex, words.size).joinToString(" ")
                                
                                currentPage.add(Pair(TextBlock(fitText), currentY))
                                
                                val remainingBuilder = java.lang.StringBuilder()
                                if (remainingTextOfFirstLine.trim().isNotEmpty()) {
                                    remainingBuilder.append(remainingTextOfFirstLine)
                                }
                                if (otherLinesText.isNotEmpty()) {
                                    if (remainingBuilder.isNotEmpty()) remainingBuilder.append("\n")
                                    remainingBuilder.append(otherLinesText)
                                }
                                
                                val remainingTotal = remainingBuilder.toString()
                                if (remainingTotal.trim().isNotEmpty()) {
                                    blockQueue.addFirst(TextBlock(remainingTotal))
                                }
                                
                                pages.add(currentPage)
                                currentPage = mutableListOf()
                                currentY = 0f
                            }
                        }
                    }
                } else if (block is TableBlock) {
                    val remainingHeight = contentHeight - currentY
                    if (block.colWidths.isEmpty()) {
                        block.calculateLayout(contentWidth, textPaint)
                    }
                    val minRequired = block.headerHeight + (block.rowHeights.firstOrNull() ?: 24f)
                    
                    if (remainingHeight >= minRequired && block.rows.isNotEmpty()) {
                        var accumulatedRowsHeight = block.headerHeight
                        var fitRowsCount = 0
                        for (r in block.rows.indices) {
                            val nextHeight = accumulatedRowsHeight + block.rowHeights[r]
                            if (nextHeight <= remainingHeight) {
                                accumulatedRowsHeight = nextHeight
                                fitRowsCount = r + 1
                            } else {
                                break
                            }
                        }
                        
                        if (fitRowsCount > 0) {
                            val fitRows = block.rows.subList(0, fitRowsCount)
                            val remainingRows = block.rows.subList(fitRowsCount, block.rows.size)
                            
                            val fitTable = TableBlock(block.headers, block.alignments, fitRows)
                            currentPage.add(Pair(fitTable, currentY))
                            
                            if (remainingRows.isNotEmpty()) {
                                blockQueue.addFirst(TableBlock(block.headers, block.alignments, remainingRows))
                            }
                            
                            pages.add(currentPage)
                            currentPage = mutableListOf()
                            currentY = 0f
                        } else {
                            if (currentPage.isNotEmpty()) {
                                pages.add(currentPage)
                                currentPage = mutableListOf()
                                currentY = 0f
                            }
                            blockQueue.addFirst(block)
                        }
                    } else {
                        if (currentPage.isNotEmpty()) {
                            pages.add(currentPage)
                            currentPage = mutableListOf()
                            currentY = 0f
                        }
                        blockQueue.addFirst(block)
                    }
                } else {
                    if (currentPage.isNotEmpty()) {
                        pages.add(currentPage)
                        currentPage = mutableListOf()
                        currentY = 0f
                        blockQueue.addFirst(block)
                    } else {
                        currentPage.add(Pair(block, currentY))
                        currentY += blockHeight + 12f
                    }
                }
            }
        }
        
        if (currentPage.isNotEmpty()) {
            pages.add(currentPage)
        }

        val numberOfPages = pages.size

        for (i in 0 until numberOfPages) {
            val pageBlocks = pages[i]
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            if (renderingPdfInDarkTheme) {
                val bgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(30, 33, 36)
                }
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
            }

            val headerPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(12, 8, 36)
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 65f, headerPaint)

            val headerTextPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 14f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("StudyMate 2.0 - Generated Document", margin.toFloat(), 30f, headerTextPaint)

            val headerSubPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(204, 196, 255)
                textSize = 9f
                isAntiAlias = true
            }
            val sanitizedTitle = if (title.lowercase().endsWith(".pdf")) title.dropLast(4) else title
            canvas.drawText(sanitizedTitle, margin.toFloat(), 48f, headerSubPaint)

            val footerPaint = android.graphics.Paint().apply {
                color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(180, 180, 180) else android.graphics.Color.rgb(100, 100, 100)
                textSize = 9f
                isAntiAlias = true
            }
            canvas.drawText("Page ${i + 1} of $numberOfPages", (pageWidth - margin - 80).toFloat(), (pageHeight - 20).toFloat(), footerPaint)
            canvas.drawText("Created with StudyMate 2.0 AI Companion", margin.toFloat(), (pageHeight - 20).toFloat(), footerPaint)

            val dividerPaint = android.graphics.Paint().apply {
                color = if (renderingPdfInDarkTheme) android.graphics.Color.rgb(80, 80, 80) else android.graphics.Color.rgb(220, 220, 220)
                strokeWidth = 1f
            }
            canvas.drawLine(margin.toFloat(), (pageHeight - 35).toFloat(), (pageWidth - margin).toFloat(), (pageHeight - 35).toFloat(), dividerPaint)

            val contentTop = 80f
            val contentBottom = (pageHeight - margin - 40).toFloat()

            for (pair in pageBlocks) {
                val block = pair.first
                val relativeY = pair.second
                canvas.save()
                canvas.clipRect(margin.toFloat(), contentTop, (pageWidth - margin).toFloat(), contentBottom)
                block.draw(canvas, margin.toFloat(), contentTop + relativeY, contentWidth, textPaint)
                canvas.restore()
            }

            pdfDocument.finishPage(page)
        }

        try {
            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
            renderingPdfInDarkTheme = false
        }
    }

    private fun toSuperscript(str: String): String {
        val superscripts = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
            'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'a' to 'ᵃ', 'b' to 'ᵇ',
            'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ', 'g' to 'ᵍ',
            'h' to 'ʰ', 'j' to 'ʲ', 'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ',
            'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ',
            'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'y' to 'ʸ', 'z' to 'ᶻ',
            'A' to 'ᴬ', 'B' to 'ᴮ', 'D' to 'ᴰ', 'E' to 'ᴱ', 'G' to 'ᴳ',
            'H' to 'ᴴ', 'I' to 'ᴵ', 'J' to 'ᴶ', 'K' to 'ᴲ', 'L' to 'ᴸ',
            'M' to 'ᴹ', 'N' to 'ᴺ', 'O' to 'ᴼ', 'P' to 'ᴾ', 'R' to 'ᴿ',
            'T' to 'ᵀ', 'U' to 'ᵁ', 'V' to 'ⱽ', 'W' to 'ᵂ'
        )
        return str.map { superscripts[it] ?: it }.joinToString("")
    }

    private fun toSubscript(str: String): String {
        val subscripts = mapOf(
            '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
            '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
            '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
            'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
            'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
            'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
            'v' to 'ᵥ', 'x' to 'ₓ'
        )
        return str.map { subscripts[it] ?: it }.joinToString("")
    }

    fun formatLatexToUnicode(input: String): String {
        var text = input

        // Replace common double backslash escaped characters first
        text = text.replace("\\\\{", "{")
        text = text.replace("\\\\}", "}")
        text = text.replace("\\\\_", "_")
        text = text.replace("\\\\%", "%")
        text = text.replace("\\\\&", "&")
        text = text.replace("\\\\$", "$")
        
        // Replace standalone double backslashes with newlines
        text = text.replace("\\\\", "\n")

        // Replace some common spacing markers
        text = text.replace("\\,", " ")
        text = text.replace("\\;", " ")
        text = text.replace("\\!", "")
        text = text.replace("\\quad", "    ")
        text = text.replace("\\qquad", "        ")

        // Remove \left and \right delimiters which are latex specific
        text = text.replace("\\left(", "(")
        text = text.replace("\\right)", ")")
        text = text.replace("\\left[", "[")
        text = text.replace("\\right]", "]")
        text = text.replace("\\left\\{", "{")
        text = text.replace("\\right\\}", "}")
        text = text.replace("\\left|", "|")
        text = text.replace("\\right|", "|")
        text = text.replace("\\{", "{")
        text = text.replace("\\}", "}")
        text = text.replace("\\_", "_")
        text = text.replace("\\%", "%")
        text = text.replace("\\&", "&")
        text = text.replace("\\$", "$")
        text = text.replace("\\`", "")
        text = text.replace("`", "")
        text = text.replace("\\[", "[")
        text = text.replace("\\]", "]")
        text = text.replace("\\(", "(")
        text = text.replace("\\)", ")")

        // Strip out $$ and $ math block wrappers
        text = text.replace("$$", "")
        text = text.replace("$", "")

        // Text formatting
        text = text.replace(Regex("\\\\text\\{([^}]+)\\}")) { it.groupValues[1] }
        text = text.replace(Regex("\\\\mathrm\\{([^}]+)\\}")) { it.groupValues[1] }
        text = text.replace(Regex("\\\\mathbf\\{([^}]+)\\}")) { it.groupValues[1] }

        // Blackboard bold math letters: \mathbb{N} -> ℕ etc.
        val mathbbMap = mapOf(
            "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "R" to "ℝ", "C" to "ℂ", "W" to "𝕎", "T" to "𝕋"
        )
        text = text.replace(Regex("\\\\mathbb\\{([^}]+)\\}")) { matchResult ->
            val inner = matchResult.groupValues[1]
            mathbbMap[inner] ?: inner
        }

        // Fractions: \frac{a}{b} -> (a) / (b)
        val fracRegex = Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}")
        for (i in 1..4) { // multiple passes to handle potential nesting
            text = text.replace(fracRegex) { matchResult ->
                val num = matchResult.groupValues[1]
                val den = matchResult.groupValues[2]
                "($num)/($den)"
            }
        }

        // Square roots: \sqrt{x} -> √(x)
        val sqrtRegex = Regex("\\\\sqrt\\{([^}]+)\\}")
        for (i in 1..3) {
            text = text.replace(sqrtRegex) { matchResult ->
                val inner = matchResult.groupValues[1]
                "√($inner)"
            }
        }
        // Also support root with index \sqrt[n]{x}
        text = text.replace(Regex("\\\\sqrt\\[([^]]+)\\]\\{([^}]+)\\}")) { matchResult ->
            val index = matchResult.groupValues[1]
            val inner = matchResult.groupValues[2]
            "${toSuperscript(index)}√($inner)"
        }

        // Subscripts and Superscripts
        // Handle curly braces first: ^{abc} -> superscript(abc), _{abc} -> subscript(abc)
        text = text.replace(Regex("\\^\\{([^}]+)\\}")) { matchResult ->
            toSuperscript(matchResult.groupValues[1])
        }
        text = text.replace(Regex("_\\{([^}]+)\\}")) { matchResult ->
            toSubscript(matchResult.groupValues[1])
        }
        // Handle single characters: ^2 -> superscript(2), _3 -> subscript(3)
        text = text.replace(Regex("\\^([a-zA-Z0-9+-=])")) { matchResult ->
            toSuperscript(matchResult.groupValues[1])
        }
        text = text.replace(Regex("_([a-zA-Z0-9+-=])")) { matchResult ->
            toSubscript(matchResult.groupValues[1])
        }

        // Common Greek letters
        val greekSymbols = mapOf(
            "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\Gamma" to "Γ",
            "\\delta" to "δ", "\\Delta" to "Δ", "\\epsilon" to "ε", "\\varepsilon" to "ε",
            "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ", "\\Theta" to "Θ",
            "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\Lambda" to "Λ",
            "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\Xi" to "Ξ",
            "\\pi" to "π", "\\Pi" to "Π", "\\rho" to "ρ",
            "\\sigma" to "σ", "\\Sigma" to "Σ", "\\tau" to "τ",
            "\\upsilon" to "υ", "\\phi" to "φ", "\\varphi" to "φ", "\\Phi" to "Φ",
            "\\chi" to "χ", "\\psi" to "ψ", "\\Psi" to "Ψ",
            "\\omega" to "ω", "\\Omega" to "Ω"
        )
        for ((key, value) in greekSymbols) {
            text = text.replace(key, value)
        }

        // Mathematical symbols
        val mathSymbols = mapOf(
            "\\times" to "×",
            "\\div" to "÷",
            "\\pm" to "±",
            "\\mp" to "∓",
            "\\cdot" to "·",
            "\\bullet" to "•",
            "\\infty" to "∞",
            "\\partial" to "∂",
            "\\nabla" to "∇",
            "\\approx" to "≈",
            "\\neq" to "≠",
            "\\le" to "≤",
            "\\leq" to "≤",
            "\\ge" to "≥",
            "\\geq" to "≥",
            "\\propto" to "∝",
            "\\in" to "∈",
            "\\notin" to "∉",
            "\\sum" to "∑",
            "\\prod" to "∏",
            "\\int" to "∫",
            "\\oint" to "∮",
            "\\hbar" to "ℏ",
            "\\to" to "→",
            "\\rightarrow" to "→",
            "\\leftarrow" to "←",
            "\\uparrow" to "↑",
            "\\downarrow" to "↓",
            "\\leftrightarrow" to "↔",
            "\\implies" to "⇒",
            "\\iff" to "⇔",
            "\\forall" to "∀",
            "\\exists" to "∃",
            "\\empty" to "∅",
            "\\emptyset" to "∅",
            "\\cap" to "∩",
            "\\cup" to "∪",
            "\\subset" to "⊂",
            "\\supset" to "⊃",
            "\\subseteq" to "⊆",
            "\\supseteq" to "⊇",
            "\\angle" to "∠",
            "\\degree" to "°",
            "\\parallel" to "∥",
            "\\dots" to "...",
            "\\ldots" to "..."
        )
        for ((key, value) in mathSymbols) {
            text = text.replace(key, value)
        }

        // Remove remaining backslashes for undefined commands
        text = text.replace(Regex("\\\\[a-zA-Z]+"), "")

        // Clean up block delimiters $$...$$ and inline $...$
        text = text.replace("$$", "")
        text = text.replace("$", "")

        return text
    }

    fun downloadQuizAnswerKey(context: Context, quiz: QuizSet) {
        viewModelScope.launch {
            try {
                // Collect the quiz questions from the flow
                val questions = dao.getQuizQuestionsForSet(quiz.id).firstOrNull() ?: emptyList()
                if (questions.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "No questions found for this quiz!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Format the quiz content as raw text
                val stringBuilder = StringBuilder()
                stringBuilder.append("QUIZ ANSWER KEY & STUDY RECONSTRUCTION\n")
                stringBuilder.append("Quiz: ${quiz.title}\n")
                stringBuilder.append("Generated with StudyMate AI Companion\n")
                stringBuilder.append("==================================================\n\n")

                questions.forEachIndexed { index, q ->
                    stringBuilder.append("Question ${index + 1}:\n")
                    stringBuilder.append("${q.question}\n\n")
                    stringBuilder.append("Options:\n")
                    q.optionsList.forEachIndexed { optIndex, option ->
                        val letter = ('A' + optIndex)
                        if (option == q.correctAnswer) {
                            stringBuilder.append("  [x] $letter) $option  <-- CORRECT ANSWER\n")
                        } else {
                            stringBuilder.append("  [ ] $letter) $option\n")
                        }
                    }
                    stringBuilder.append("\nCorrect Answer: ${q.correctAnswer}\n")
                    stringBuilder.append("Study Solution: Study material validates that \"${q.correctAnswer}\" is the correct response. Practice active recall on related terms.\n")
                    stringBuilder.append("--------------------------------------------------\n\n")
                }

                val textToWrite = stringBuilder.toString()
                val pdfTitle = "Quiz_${quiz.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_AnswerKey"

                // Write the PDF using MediaStore downloads or app local external downloads folder, just like note download!
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        val displayName = "$pdfTitle.pdf"
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }
                    }

                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    } else {
                        null
                    }

                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { output ->
                            writeTextAsPdfToStream(context, pdfTitle, textToWrite, output)
                        }
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Successfully downloaded: $pdfTitle.pdf to your Downloads folder!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val targetFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "$pdfTitle.pdf")
                        targetFile.outputStream().use { output ->
                            writeTextAsPdfToStream(context, pdfTitle, textToWrite, output)
                        }
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Saved as PDF to private downloads: ${targetFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error downloading quiz answer key", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to download answer key: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadNoteFile(context: Context, note: NoteEntry, isDarkTheme: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val srcFile = note.filePath?.let { File(it) }
            val isTextMode = note.fileType == "TEXT"

            val textToWrite = if (isTextMode) {
                if (srcFile != null && srcFile.exists()) srcFile.readText() else note.content
            } else ""

            if (!isTextMode && (srcFile == null || !srcFile.exists())) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error: File path is invalid or does not exist!", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    val displayName = if (isTextMode) {
                        if (note.title.lowercase().endsWith(".pdf")) note.title else "${note.title}.pdf"
                    } else {
                        note.title
                    }
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    
                    if (note.fileType == "PDF" || isTextMode) {
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    } else if (note.fileType == "IMAGE") {
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    } else {
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                }

                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                } else {
                    null
                }

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        if (isTextMode) {
                            writeTextAsPdfToStream(context, note.title, textToWrite, output, isDarkTheme)
                        } else {
                            srcFile!!.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                    val downloadedName = if (isTextMode) {
                        if (note.title.lowercase().endsWith(".pdf")) note.title else "${note.title}.pdf"
                    } else note.title
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Successfully downloaded: $downloadedName as a PDF to your Downloads folder!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    val downloadedName = if (isTextMode) {
                        if (note.title.lowercase().endsWith(".pdf")) note.title else "${note.title}.pdf"
                    } else note.title
                    val targetFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), downloadedName)
                    targetFile.outputStream().use { output ->
                        if (isTextMode) {
                            writeTextAsPdfToStream(context, note.title, textToWrite, output, isDarkTheme)
                        } else {
                            srcFile!!.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Saved as PDF to private downloads: ${targetFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error exporting note as PDF", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to download: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Save Text Note
    fun createTextNote(title: String, content: String, subject: String = "", chapter: String = "") {
        viewModelScope.launch {
            val fileName = "notes_${System.currentTimeMillis()}.txt"
            val localFile = File(getApplication<Application>().filesDir, fileName)
            try {
                localFile.writeText(content)
                dao.insertNote(
                    NoteEntry(
                        title = title,
                        content = content.take(150) + if (content.length > 150) "..." else "",
                        fileType = "TEXT",
                        filePath = localFile.absolutePath,
                        subject = subject,
                        chapter = chapter
                    )
                )
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error writing text note", e)
            }
        }
    }

    // Save Document / PDF notes linkage
    fun createDocumentNote(title: String, extension: String, localPath: String, subject: String = "", chapter: String = "") {
        viewModelScope.launch {
            val type = when (extension.uppercase()) {
                "PDF" -> "PDF"
                "PNG", "JPG", "JPEG" -> "IMAGE"
                else -> "DOC"
            }
            dao.insertNote(
                NoteEntry(
                    title = title,
                    content = "Local visual document note stored securely",
                    fileType = type,
                    filePath = localPath,
                    subject = subject,
                    chapter = chapter
                )
            )
        }
    }

    fun updateNoteFolder(note: NoteEntry, subject: String, chapter: String) {
        viewModelScope.launch {
            dao.insertNote(note.copy(subject = subject, chapter = chapter))
        }
    }

    fun updateNoteTextContent(id: Int, filePath: String, newFullContent: String) {
        viewModelScope.launch {
            try {
                val notes = dao.getAllNotesDirect()
                val note = notes.find { it.id == id }
                if (note != null) {
                    val file = File(filePath)
                    file.writeText(newFullContent)
                    dao.insertNote(note.copy(
                        content = newFullContent.take(150) + if (newFullContent.length > 150) "..." else ""
                    ))
                }
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error updating text note content", e)
            }
        }
    }

    fun deleteNoteEntry(note: NoteEntry) {
        viewModelScope.launch {
            note.filePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            dao.deleteNote(note)
        }
    }

    // Load active api key (dynamic fallback rotation)
    private suspend fun getActiveApiKeysStream(): List<String> {
        val dbKeys = try {
            dao.getAllApiKeysDirect()
        } catch (e: Exception) {
            emptyList()
        }
        val customKeys = dbKeys.filter { it.isWorking }.map { it.key }
        val finalKeys = mutableListOf<String>()
        // User custom keys prioritized, falls back to Build config key
        finalKeys.addAll(customKeys)
        if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            finalKeys.add(BuildConfig.GEMINI_API_KEY)
        }
        return finalKeys
    }

    private suspend fun invalidateApiKey(failedKey: String) {
        val dbKeys = try {
            dao.getAllApiKeysDirect()
        } catch (e: Exception) {
            emptyList()
        }
        val entry = dbKeys.firstOrNull { it.key == failedKey }
        if (entry != null) {
            dao.updateApiKeyStatus(entry.id, isWorking = false)
        }
    }

    // Helper execute dynamic call with API fallback carousel
    private suspend fun <T> executeWithFallback(callBlock: suspend (String) -> T): T {
        val keys = getActiveApiKeysStream()
        if (keys.isEmpty()) {
            throw Exception("No Gemini API key found! Please go to settings and add your API key, or define it in your SECRETS panel.")
        }
        
        var lastException: Exception? = null
        val primaryModel = selectedModel.value
        
        // Phase 1: Try all keys using selected model
        com.example.data.GeminiNetwork.activeModel = primaryModel
        Log.d("StudyMateVM", "Phase 1: Attempting to call API using $primaryModel for all active keys...")
        
        for (key in keys) {
            val currentWorkingKeys = getActiveApiKeysStream()
            if (!currentWorkingKeys.contains(key)) {
                continue
            }
            
            var attempt = 0
            val maxAttempts = 3
            var success = false
            var result: T? = null
            
            while (attempt < maxAttempts && !success) {
                try {
                    result = callBlock(key)
                    success = true
                } catch (e: Exception) {
                    attempt++
                    lastException = e
                    Log.e("StudyMateVM", "API Call ($primaryModel) failed (attempt $attempt / $maxAttempts) with key", e)
                    
                    var isTransient = false
                    var shouldRotateImmediately = false
                    if (e is retrofit2.HttpException) {
                        val code = e.code()
                        if (code == 429 || code == 503 || code >= 500) {
                            isTransient = true
                            if (keys.size > 1) {
                                shouldRotateImmediately = true
                            }
                        } else if (code >= 500) {
                            isTransient = true
                        }
                    } else if (e is java.io.IOException) {
                        isTransient = true
                        val msg = e.message ?: ""
                        if (e is java.net.UnknownHostException || msg.contains("Unable to resolve host") || msg.contains("No address associated with hostname") || msg.contains("ConnectException")) {
                            if (keys.size > 1) {
                                shouldRotateImmediately = true
                            }
                        }
                    }
                    
                    if (shouldRotateImmediately) {
                        Log.w("StudyMateVM", "Encountered 503, 429, or 5xx. Initiating fast key rotation...")
                        break
                    }
                    
                    if (isTransient && attempt < maxAttempts) {
                        val backoffMs = attempt * 1000L
                        kotlinx.coroutines.delay(backoffMs)
                    } else {
                        break
                    }
                }
            }
            
            if (success && result != null) {
                return result
            }
            
            val failedException = lastException
            var shouldInvalidate = true
            if (failedException is retrofit2.HttpException) {
                val code = failedException.code()
                if (code == 429 || code >= 500) {
                    shouldInvalidate = false
                }
            } else if (failedException is java.io.IOException) {
                shouldInvalidate = false
            }
            
            if (shouldInvalidate) {
                invalidateApiKey(key)
            }
            
            withContext(Dispatchers.Main) {
                if (shouldInvalidate) {
                    apiErrorFeedback.value = "Active API Key reached limit/failed on $primaryModel. Auto-routing to fallback key..."
                } else {
                    apiErrorFeedback.value = "Transient error $primaryModel (${failedException?.message ?: "unknown"}). Trying next key..."
                }
            }
        }
        
        // Phase 2: If Phase 1 fails for ALL keys, fallback to fallback model for all working keys
        val fallbackModel = if (primaryModel == "gemini-2.5-flash") "gemini-1.5-flash" else "gemini-2.5-flash"
        com.example.data.GeminiNetwork.activeModel = fallbackModel
        Log.e("StudyMateVM", "Phase 1 ($primaryModel) failed for all keys. Phase 2: Falling back to $fallbackModel...")
        
        val remainingKeys = getActiveApiKeysStream()
        if (remainingKeys.isEmpty()) {
            throw lastException ?: Exception("All API keys were invalidated or failed on $primaryModel.")
        }
        
        for (key in remainingKeys) {
            val currentWorkingKeys = getActiveApiKeysStream()
            if (!currentWorkingKeys.contains(key)) {
                continue
            }
            
            var attempt = 0
            val maxAttempts = 3
            var success = false
            var result: T? = null
            
            while (attempt < maxAttempts && !success) {
                try {
                    result = callBlock(key)
                    success = true
                } catch (e: Exception) {
                    attempt++
                    lastException = e
                    Log.e("StudyMateVM", "API Call ($fallbackModel) failed (attempt $attempt / $maxAttempts) with key", e)
                    
                    var isTransient = false
                    var shouldRotateImmediately = false
                    if (e is retrofit2.HttpException) {
                        val code = e.code()
                        if (code == 429 || code == 503 || code >= 500) {
                            isTransient = true
                            if (remainingKeys.size > 1) {
                                shouldRotateImmediately = true
                            }
                        } else if (code >= 500) {
                            isTransient = true
                        }
                    } else if (e is java.io.IOException) {
                        isTransient = true
                        val msg = e.message ?: ""
                        if (e is java.net.UnknownHostException || msg.contains("Unable to resolve host") || msg.contains("No address associated with hostname") || msg.contains("ConnectException")) {
                            if (remainingKeys.size > 1) {
                                shouldRotateImmediately = true
                            }
                        }
                    }
                    
                    if (shouldRotateImmediately) {
                        Log.w("StudyMateVM", "Encountered 503, 429, or 5xx. Initiating fast key rotation...")
                        break
                    }
                    
                    if (isTransient && attempt < maxAttempts) {
                        val backoffMs = attempt * 1000L
                        kotlinx.coroutines.delay(backoffMs)
                    } else {
                        break
                    }
                }
            }
            
            if (success && result != null) {
                return result
            }
            
            val failedException = lastException
            var shouldInvalidate = true
            if (failedException is retrofit2.HttpException) {
                val code = failedException.code()
                if (code == 429 || code >= 500) {
                    shouldInvalidate = false
                }
            } else if (failedException is java.io.IOException) {
                shouldInvalidate = false
            }
            
            if (shouldInvalidate) {
                invalidateApiKey(key)
            }
            
            withContext(Dispatchers.Main) {
                if (shouldInvalidate) {
                    apiErrorFeedback.value = "Active API Key reached limit/failed on $fallbackModel. Auto-routing to fallback key..."
                } else {
                    apiErrorFeedback.value = "Transient error $fallbackModel (${failedException?.message ?: "unknown"}). Trying next key..."
                }
            }
        }
        
        throw lastException ?: Exception("All available API Keys failed or hit limit parameters.")
    }

    private suspend fun renderPdfPagesToBase64(pdfPath: String?, maxPages: Int = 200): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()
        if (pdfPath == null) return@withContext result
        try {
            val file = File(pdfPath)
            if (file.exists()) {
                val input = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(input)
                val pageCount = renderer.pageCount
                
                val indicesToProcess = (0 until minOf(pageCount, maxPages)).toList()
                val pagesToProcess = indicesToProcess.size
                
                // Smart adaptive resolution & compression to avoid OOM or API request timeout
                val targetWidthConfig = when {
                    pagesToProcess <= 3 -> 900
                    pagesToProcess <= 8 -> 750
                    pagesToProcess <= 20 -> 600
                    pagesToProcess <= 50 -> 480
                    else -> 360
                }
                
                val compressQuality = when {
                    pagesToProcess <= 3 -> 65
                    pagesToProcess <= 8 -> 55
                    pagesToProcess <= 20 -> 45
                    pagesToProcess <= 50 -> 35
                    else -> 25
                }
                
                for (i in indicesToProcess) {
                    try {
                        val page = renderer.openPage(i)
                        
                        val targetWidth = if (page.width > targetWidthConfig) targetWidthConfig else page.width
                        val targetHeight = (page.height * (targetWidth.toFloat() / page.width)).toInt()
                        
                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, compressQuality, out)
                        bmp.recycle() // Direct memory recycle! Important for JVM heap space
                        
                        val base64Str = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        result.add(base64Str)
                    } catch (e: Exception) {
                        Log.e("StudyMateVM", "Error rendering PDF page $i", e)
                    }
                }
                renderer.close()
                input.close()
            }
        } catch (e: Exception) {
            Log.e("StudyMateVM", "Error rendering PDF pages to Base64", e)
        }
        result
    }

    private fun saveChatHistory(history: List<ChatMessage>) {
        val listStrings = history.mapIndexed { index, msg ->
            val indexStr = index.toString().padStart(4, '0')
            val encodedId = Base64.encodeToString((msg.id).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val encodedSender = Base64.encodeToString((msg.sender).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val encodedText = Base64.encodeToString((msg.text).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val encodedUri = Base64.encodeToString((msg.localImageUri ?: "").toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "$indexStr||$encodedId||$encodedSender||$encodedText||$encodedUri"
        }.toSet()
        prefs.edit().putStringSet("saved_chat_history_v3", listStrings).apply()
    }

    fun loadChatHistory(): List<ChatMessage> {
        val loadedStrings = prefs.getStringSet("saved_chat_history_v3", null) ?: return emptyList()
        val loadedItems = loadedStrings.mapNotNull { str ->
            val parts = str.split("||")
            if (parts.size >= 5) {
                try {
                    val index = parts[0].toIntOrNull() ?: 0
                    val id = String(Base64.decode(parts[1], Base64.NO_WRAP), Charsets.UTF_8)
                    val sender = String(Base64.decode(parts[2], Base64.NO_WRAP), Charsets.UTF_8)
                    val text = String(Base64.decode(parts[3], Base64.NO_WRAP), Charsets.UTF_8)
                    val uriStr = String(Base64.decode(parts[4], Base64.NO_WRAP), Charsets.UTF_8)
                    val uri = if (uriStr.isEmpty()) null else uriStr
                    index to ChatMessage(id = id, sender = sender, text = text, localImageUri = uri)
                } catch (e: Exception) {
                    null
                }
            } else null
        }
        return loadedItems.sortedBy { it.first }.map { it.second }
    }

    // --- AI Homework Chat ---
    fun askHomeworkHelper(question: String, imageBitmap: Bitmap?, localUriPath: String? = null) {
        viewModelScope.launch {
            isAILoading.value = true
            apiErrorFeedback.value = null

            // Add user turn visually
            val updatedUserHistory = _chatHistory.value + ChatMessage(sender = "User", text = question, localImageUri = localUriPath)
            _chatHistory.value = updatedUserHistory
            saveChatHistory(updatedUserHistory)

            val geminiMsgId = UUID.randomUUID().toString()

            try {
                // Build parts for current prompt
                val parts = mutableListOf<GeminiPart>()
                parts.add(GeminiPart(text = question))

                if (imageBitmap != null) {
                    val base64Image = withContext(Dispatchers.IO) {
                        val out = ByteArrayOutputStream()
                        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                    parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)))
                }

                // Append prompt turn to history context
                conversationTurns.add(GeminiContent(parts))

                // Limit conversation context history size to save tokens/avoid latency
                if (conversationTurns.size > 12) {
                    conversationTurns.removeAt(0)
                }

                val systemPrompt = """
                    You are an AI Teacher. Your teaching persona (style) is: ${selectedTeacherPersonality.value}.
                    You must explain concepts at the following comprehension level: ${selectedExplanationLevel.value}.
                    
                    Persona styling instructions to follow strictly:
                    - Friendly Teacher: Speak in a warm, encouraging, kind, and supportive voice. Use friendly words, helpful emojis, and gentle praise to inspire confidence. Explain concepts step-by-step with clear, patient, real-world examples.
                    - Strict Teacher: Speak in a highly direct, serious, and academic voice. Deliver pure, no-nonsense content. Demand focus, skip pleasantries/emojis, write formal and rigorous structures, point out potential student mistakes, and organize with concise headers.
                    - Board Exam Expert: Speak from the perspective of an expert evaluator. Highlight marking criteria, expected scoring points, correct terminologies, syllabus coverage, standard templates, presentation tips, and alerts on common scoring pitfalls.
                    - Fast Revision Teacher: Deliver lightning-fast, high-density, energetic summaries. Use list elements, bullet points, memory retention phrases, keyword highlights, active recall questions, and direct formulas for quick exams.
                    
                    Comprehension level limits to follow strictly:
                    - Explain Like I'm 10: Use extremely simple, friendly language. Avoid technical jargon or advanced mathematics entirely. Use fun analogies (like building blocks, baking cakes, or playground toys). Limit explanations to short, easy sentences.
                    - Beginner: Use standard clear language with basic level background. Limit references to complex theories; present things gradually step-by-step.
                    - Intermediate: Use standard school/college textbook language. Support explanations with typical definitions, mathematical formulas, and normal examples.
                    - Exam Level: Use academically precise technical terminology suitable for scoring maximum marks in written exams. Focus on precise wording, mark-yielding definitions, step-by-step proofs, and precise formats.
                    - Expert: Speak at a collegiate, scholarly level. Deliver deep, advanced, technically complete, and rigorous explanations. Detail underlying mathematical theory, physical laws, edge cases, scientific nuances, and advanced practical implementations.
                    
                    Adopt the teacher style and comprehension level perfectly in your output response.
                """.trimIndent()

                val requestContents = listOf(
                    GeminiContent(listOf(GeminiPart(text = "System: $systemPrompt")))
                ) + conversationTurns.toList()

                // Add empty Gemini message placeholder to history
                withContext(Dispatchers.Main) {
                    _chatHistory.value = _chatHistory.value + ChatMessage(id = geminiMsgId, sender = "Gemini", text = "")
                }

                var accumulatedResponse = ""
                executeWithFallback { apiKey ->
                    val request = GeminiRequest(
                        contents = requestContents,
                        generationConfig = GeminiGenerationConfig(temperature = 0.4f)
                    )
                    var displayedText = ""
                    GeminiNetwork.streamGenerateContent(apiKey, request).collect { chunk ->
                        accumulatedResponse += chunk
                        while (displayedText.length < accumulatedResponse.length) {
                            val increment = (accumulatedResponse.length - displayedText.length).coerceAtMost(3)
                            displayedText += accumulatedResponse.substring(displayedText.length, displayedText.length + increment)
                            withContext(Dispatchers.Main) {
                                _chatHistory.value = _chatHistory.value.map { msg ->
                                    if (msg.id == geminiMsgId) msg.copy(text = displayedText) else msg
                                }
                            }
                            kotlinx.coroutines.delay(10)
                        }
                    }
                    if (displayedText != accumulatedResponse) {
                        displayedText = accumulatedResponse
                        withContext(Dispatchers.Main) {
                            _chatHistory.value = _chatHistory.value.map { msg ->
                                if (msg.id == geminiMsgId) msg.copy(text = displayedText) else msg
                            }
                        }
                    }
                    if (accumulatedResponse.isEmpty()) {
                        throw Exception("Streaming did not return any text candidates")
                    }
                    true
                }

                // Add AI answer to history context
                conversationTurns.add(GeminiContent(listOf(GeminiPart(text = accumulatedResponse))))
                saveChatHistory(_chatHistory.value)

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val currentList = _chatHistory.value
                    if (currentList.isNotEmpty() && currentList.last().sender == "Gemini" && currentList.last().text.isEmpty()) {
                        _chatHistory.value = currentList.dropLast(1) + ChatMessage(sender = "Gemini", text = "Sorry, I had an error responding: ${e.localizedMessage}")
                    } else {
                        _chatHistory.value = currentList + ChatMessage(sender = "Gemini", text = "Sorry, I had an error responding: ${e.localizedMessage}")
                    }
                    saveChatHistory(_chatHistory.value)
                }
                apiErrorFeedback.value = e.localizedMessage
            } finally {
                isAILoading.value = false
            }
        }
    }

    fun askTeacherAgain(question: String, localUriPath: String? = null) {
        viewModelScope.launch {
            isAILoading.value = true
            apiErrorFeedback.value = null

            var imageBitmap: Bitmap? = null
            if (localUriPath != null) {
                try {
                    val file = java.io.File(localUriPath)
                    if (file.exists()) {
                        imageBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    }
                } catch (e: Exception) {
                    Log.e("StudyMateVM", "Error loading image bytes", e)
                }
            }

            val geminiMsgId = UUID.randomUUID().toString()

            try {
                val currentHist = _chatHistory.value.toMutableList()
                if (currentHist.isNotEmpty() && currentHist.last().sender == "Gemini") {
                    currentHist.removeAt(currentHist.size - 1)
                    _chatHistory.value = currentHist
                    saveChatHistory(currentHist)
                }

                if (conversationTurns.isNotEmpty()) {
                    conversationTurns.removeAt(conversationTurns.size - 1)
                }

                val parts = mutableListOf<GeminiPart>()
                parts.add(GeminiPart(text = question))

                if (imageBitmap != null) {
                    val base64Image = withContext(Dispatchers.IO) {
                        val out = ByteArrayOutputStream()
                        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                    parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)))
                }

                val systemPrompt = """
                    You are an AI Teacher. Your teaching persona (style) is: ${selectedTeacherPersonality.value}.
                    You must explain concepts at the following comprehension level: ${selectedExplanationLevel.value}.
                    
                    Persona styling instructions to follow strictly:
                    - Friendly Teacher: Speak in a warm, encouraging, kind, and supportive voice. Use friendly words, helpful emojis, and gentle praise to inspire confidence. Explain concepts step-by-step with clear, patient, real-world examples.
                    - Strict Teacher: Speak in a highly direct, serious, and academic voice. Deliver pure, no-nonsense content. Demand focus, skip pleasantries/emojis, write formal and rigorous structures, point out potential student mistakes, and organize with concise headers.
                    - Board Exam Expert: Speak from the perspective of an expert evaluator. Highlight marking criteria, expected scoring points, correct terminologies, syllabus coverage, standard templates, presentation tips, and alerts on common scoring pitfalls.
                    - Fast Revision Teacher: Deliver lightning-fast, high-density, energetic summaries. Use list elements, bullet points, memory retention phrases, keyword highlights, active recall questions, and direct formulas for quick exams.
                    
                    Comprehension level limits to follow strictly:
                    - Explain Like I'm 10: Use extremely simple, friendly language. Avoid technical jargon or advanced mathematics entirely. Use fun analogies (like building blocks, baking cakes, or playground toys). Limit explanations to short, easy sentences.
                    - Beginner: Use standard clear language with basic level background. Limit references to complex theories; present things gradually step-by-step.
                    - Intermediate: Use standard school/college textbook language. Support explanations with typical definitions, mathematical formulas, and normal examples.
                    - Exam Level: Use academically precise technical terminology suitable for scoring maximum marks in written exams. Focus on precise wording, mark-yielding definitions, step-by-step proofs, and precise formats.
                    - Expert: Speak at a collegiate, scholarly level. Deliver deep, advanced, technically complete, and rigorous explanations. Detail underlying mathematical theory, physical laws, edge cases, scientific nuances, and advanced practical implementations.
                    
                    Adopt the teacher style and comprehension level perfectly in your output response.
                """.trimIndent()

                val requestContents = listOf(
                    GeminiContent(listOf(GeminiPart(text = "System: $systemPrompt")))
                ) + conversationTurns.toList()

                // Add empty Gemini message placeholder to history
                withContext(Dispatchers.Main) {
                    _chatHistory.value = _chatHistory.value + ChatMessage(id = geminiMsgId, sender = "Gemini", text = "")
                }

                var accumulatedResponse = ""
                executeWithFallback { apiKey ->
                    val request = GeminiRequest(
                        contents = requestContents,
                        generationConfig = GeminiGenerationConfig(temperature = 0.4f)
                    )
                    var displayedText = ""
                    GeminiNetwork.streamGenerateContent(apiKey, request).collect { chunk ->
                        accumulatedResponse += chunk
                        while (displayedText.length < accumulatedResponse.length) {
                            val increment = (accumulatedResponse.length - displayedText.length).coerceAtMost(3)
                            displayedText += accumulatedResponse.substring(displayedText.length, displayedText.length + increment)
                            withContext(Dispatchers.Main) {
                                _chatHistory.value = _chatHistory.value.map { msg ->
                                    if (msg.id == geminiMsgId) msg.copy(text = displayedText) else msg
                                }
                            }
                            kotlinx.coroutines.delay(10)
                        }
                    }
                    if (displayedText != accumulatedResponse) {
                        displayedText = accumulatedResponse
                        withContext(Dispatchers.Main) {
                            _chatHistory.value = _chatHistory.value.map { msg ->
                                if (msg.id == geminiMsgId) msg.copy(text = displayedText) else msg
                            }
                        }
                    }
                    if (accumulatedResponse.isEmpty()) {
                        throw Exception("Streaming did not return any text candidates")
                    }
                    true
                }

                conversationTurns.add(GeminiContent(listOf(GeminiPart(text = accumulatedResponse))))
                saveChatHistory(_chatHistory.value)

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val currentList = _chatHistory.value
                    if (currentList.isNotEmpty() && currentList.last().sender == "Gemini" && currentList.last().text.isEmpty()) {
                        _chatHistory.value = currentList.dropLast(1) + ChatMessage(sender = "Gemini", text = "Sorry, I had an error responding in new style: ${e.localizedMessage}")
                    } else {
                        _chatHistory.value = currentList + ChatMessage(sender = "Gemini", text = "Sorry, I had an error responding in new style: ${e.localizedMessage}")
                    }
                    saveChatHistory(_chatHistory.value)
                }
                apiErrorFeedback.value = e.localizedMessage
            } finally {
                isAILoading.value = false
            }
        }
    }

    fun clearHomeworkChat() {
        conversationTurns.clear()
        val defaultChat = listOf(ChatMessage(sender = "Gemini", text = "Welcome to AI Teacher Modes! I can teach you in any style. Choose my personality (Friendly Teacher, Strict Teacher, Board Exam Expert, Fast Revision Teacher) and comprehension level (Explain Like I'm 10, Beginner, Intermediate, Exam Level, Expert) above and let's start learning!"))
        _chatHistory.value = defaultChat
        saveChatHistory(defaultChat)
    }

    // --- AI Flashcards Generator ---
    fun generateAIFlashcards(title: String, textToPaster: String, pdfPath: String?, targetCount: Int = 5) {
        cancelActiveAIGeneration()
        activeAIGeneratorJob = viewModelScope.launch {
            isAILoading.value = true
            apiErrorFeedback.value = null
            showRunningNotification("Generating Flashcards", "Creating '$title' deck...")
            try {
                var contentToSummarize = textToPaster
                val base64Images = if (pdfPath != null) {
                    renderPdfPagesToBase64(pdfPath)
                } else null

                if (pdfPath != null && contentToSummarize.isBlank()) {
                    contentToSummarize = "Extract text from uploaded PDF pages and create flashcards."
                }

                val systemPrompt = """
                    You are an automated flashcard maker. Generate comprehensive flashcards representing the academic content of the provided text/image.
                    CRITICAL RULE: Study the provided material and generate cards EXCLUSIVELY about the core academic concepts in the material.
                    
                    STRICT DIRECTIVE ON QUESTION QUALITY:
                    - NEVER ask meta-questions, reference section numbers, figure numbers, or table numbers (e.g., DO NOT ask: 'What is mentioned in section 1.2?', 'According to figure 2...', 'What is the example given in section 1.2?').
                    - Instead, formulate direct, self-contained educational/academic questions about the actual content, terms, and processes described.
                    - Example: If the material discusses chloroplasts in Section 3, ask: 'What is the main function of the chloroplast?' rather than 'What organelle is discussed in section 3?'.
                    
                    MATH/LATEX SUPPORT:
                    - You can use LaTeX math formatting for technical equations, math formulas, chemical notation, and expressions (e.g. \frac{a}{b}, \sqrt{x}, ^2, _1, Greek symbols like \alpha, \beta, etc.) inside the questions or answers. The app automatically compiles and formats them elegantly for readers!
                    
                    DO NOT use the Photosynthesis example topic or create questions/answers about random facts unless that is what is actually in the material.
                    Only use the following formatting style: Use '[Q]' directly before each question, and '[A]' directly before the matching answer. Do not use any markdown lists or bullet points. Every Q must be followed by exactly one A.
                    
                    Format style example (only for structure, do not copy this photosynthesis topic unless the provided material is actually about photosynthesis):
                    [Q] What is Photosynthesis?
                    [A] Process of converting light to energy.
                """.trimIndent()

                val parsedCards = mutableListOf<FlashcardItem>()

                if (base64Images != null && base64Images.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Generating flashcards...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                val responseText = executeWithFallback { apiKey ->
                    val parts = mutableListOf<GeminiPart>()
                    parts.add(GeminiPart(text = "Subject/Notes text: $contentToSummarize\n\nPlease generate exactly $targetCount customized study flashcards strictly based on the provided material text and image details, avoiding any generic placeholder topics. Build actual academic cards directly referencing the main terms and formulas."))
                    if (base64Images != null) {
                        for (base64Image in base64Images) {
                            parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)))
                        }
                    }

                    val request = GeminiRequest(
                        contents = listOf(
                            GeminiContent(listOf(GeminiPart(text = systemPrompt))),
                            GeminiContent(parts)
                        ),
                        generationConfig = GeminiGenerationConfig(temperature = 0.5f)
                    )
                    GeminiNetwork.api.generateContent(apiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: throw Exception("No AI responses gathered.")
                }

                // Parse standard symbols: '[Q]' and '[A]'
                val lines = responseText.lines()
                var currentQ = ""
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("[Q]")) {
                        currentQ = trimmed.substring(3).trim()
                    } else if (trimmed.startsWith("[A]") && currentQ.isNotBlank()) {
                        val currentA = trimmed.substring(3).trim()
                        parsedCards.add(FlashcardItem(setId = 0, question = currentQ, answer = currentA))
                        currentQ = ""
                    }
                }

                if (parsedCards.isNotEmpty()) {
                    val setId = dao.insertFlashcardSet(FlashcardSet(title = title)).toInt()
                    val finalCards = parsedCards.map { it.copy(setId = setId) }
                    dao.insertFlashcardItems(finalCards)

                    showResultNotification(
                        isSuccess = true,
                        title = "Flashcard Generation Success",
                        message = "Successfully created '$title' deck with ${finalCards.size} cards."
                    )
                } else {
                    throw Exception("Could not find clear study cards format in AI reply. Try pasting text containing more clear facts!")
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error generating cards", e)
                apiErrorFeedback.value = e.localizedMessage
                showResultNotification(
                    isSuccess = false,
                    title = "Flashcard Generation Failed",
                    message = e.localizedMessage ?: "Unknown error occurred"
                )
            } finally {
                isAILoading.value = false
            }
        }
    }

    fun getFlashcardsForSet(setId: Int): Flow<List<FlashcardItem>> {
        return dao.getFlashcardsForSet(setId)
    }

    fun updateFlashcardKnowledge(cardId: Int, isKnown: Boolean) {
        viewModelScope.launch {
            dao.updateFlashcardKnowledge(cardId, isKnown)
        }
    }

    fun deleteFlashcardCollection(set: FlashcardSet) {
        viewModelScope.launch {
            dao.deleteFlashcardItemsForSet(set.id)
            dao.deleteFlashcardSet(set)
        }
    }

    // --- AI Quiz Generator ---
    fun generateAIQuiz(title: String, textToPaster: String, pdfPath: String?, targetCount: Int = 5) {
        cancelActiveAIGeneration()
        activeAIGeneratorJob = viewModelScope.launch {
            isAILoading.value = true
            apiErrorFeedback.value = null
            showRunningNotification("Generating Quiz", "Creating '$title' quiz...")
            try {
                var contentToSummarize = textToPaster
                val base64Images = if (pdfPath != null) {
                    renderPdfPagesToBase64(pdfPath)
                } else null

                if (pdfPath != null && contentToSummarize.isBlank()) {
                    contentToSummarize = "Analyze this PDF file and construct choice questions."
                }

                val systemPrompt = """
                    You are an AI Quiz Generator. Generate a high-quality multiple-choice quiz based strictly and entirely on the provided academic text/image content.
                    CRITICAL RULE: Analyze the provided study material and formulate questions and choices EXCLUSIVELY covering that specific material.
                    
                    STRICT DIRECTIVE ON QUESTION QUALITY:
                    - NEVER ask meta-questions, reference section numbers, chapter headers, figure numbers, or table numbers (e.g., DO NOT ask: 'What is mentioned in section 1.2?', 'According to figure 2...', 'What is the example given in section 1.2?', 'What is described on page 5?').
                    - Formulate direct, self-contained educational/academic questions about the actual scientific, historical, mathematical, or literary concepts described.
                    - Example: If the material discusses ATP production in Mitochondria in Section 2, ask: 'Which molecule is the main chemical energy currency of the cell?' rather than 'What is discussed as an example in section 2?'.
                    
                    MATH/LATEX SUPPORT:
                    - You can use LaTeX math formatting for technical equations, math formulas, chemical notation, and expressions (e.g. \frac{a}{b}, \sqrt{x}, ^2, _1, Greek symbols like \alpha, \beta, etc.) inside questions or options. The app automatically compiles and formats them elegantly for readers!
                    
                    DO NOT use the 5 x 5 math example topic or create questions about unrelated generic math unless the provided material is actually about that math topic. 
                    Only use the following formatting style: Use '[Q]' directly before each question. Use '[A]' directly before the CORRECT option. Use '[O]' directly before each other alternative INCORRECT options. Please provide exactly 4 options per question (1 correct and 3 incorrect). Do not add bullet points.
                    
                    Format style example (only for structure, do not copy this math topic unless the provided material is actually about math):
                    [Q] What is 5 x 5?
                    [A] 25
                    [O] 20
                    [O] 30
                    [O] 35
                """.trimIndent()

                val parsedQuestions = mutableListOf<QuizQuestion>()

                if (base64Images != null && base64Images.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Generating quiz questions...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                val responseText = executeWithFallback { apiKey ->
                    val parts = mutableListOf<GeminiPart>()
                    parts.add(GeminiPart(text = "Source context: $contentToSummarize\n\nPlease construct a high-quality, comprehensive multiple choice quiz with exactly $targetCount questions based strictly on the uploaded source context. Ensure questions cover important terms, formulas, and concepts."))
                    if (base64Images != null) {
                        for (base64Image in base64Images) {
                            parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)))
                        }
                    }

                    val request = GeminiRequest(
                        contents = listOf(
                            GeminiContent(listOf(GeminiPart(text = systemPrompt))),
                            GeminiContent(parts)
                        ),
                        generationConfig = GeminiGenerationConfig(temperature = 0.5f)
                    )
                    GeminiNetwork.api.generateContent(apiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: throw Exception("No AI response gathered.")
                }

                // Parse using specific symbols: '[Q]', '[A]', and '[O]'
                val lines = responseText.lines()
                var currentQ = ""
                var correctOpt = ""
                val wrongOpts = mutableListOf<String>()

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("[Q]")) {
                        if (currentQ.isNotBlank() && correctOpt.isNotBlank() && wrongOpts.isNotEmpty()) {
                            val options = (listOf(correctOpt) + wrongOpts).shuffled()
                            parsedQuestions.add(
                                QuizQuestion(
                                    quizSetId = 0,
                                    question = currentQ,
                                    optionsString = options.joinToString("||"),
                                    correctAnswer = correctOpt
                                )
                            )
                        }
                        currentQ = trimmed.substring(3).trim()
                        correctOpt = ""
                        wrongOpts.clear()
                    } else if (trimmed.startsWith("[A]")) {
                        correctOpt = trimmed.substring(3).trim()
                    } else if (trimmed.startsWith("[O]")) {
                        wrongOpts.add(trimmed.substring(3).trim())
                    }
                }

                // Add trailing
                if (currentQ.isNotBlank() && correctOpt.isNotBlank() && wrongOpts.isNotEmpty()) {
                    val options = (listOf(correctOpt) + wrongOpts).shuffled()
                    parsedQuestions.add(
                        QuizQuestion(
                            quizSetId = 0,
                            question = currentQ,
                            optionsString = options.joinToString("||"),
                            correctAnswer = correctOpt
                        )
                    )
                }

                if (parsedQuestions.isNotEmpty()) {
                    val quizId = dao.insertQuizSet(QuizSet(title = title)).toInt()
                    val finalQuestions = parsedQuestions.map { it.copy(quizSetId = quizId) }
                    dao.insertQuizQuestions(finalQuestions)

                    showResultNotification(
                        isSuccess = true,
                        title = "Quiz Generation Success",
                        message = "Successfully created '$title' quiz with ${finalQuestions.size} questions."
                    )
                } else {
                    throw Exception("Could not parse options successfully. Ensure input contains explicit paragraphs.")
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error generating quiz", e)
                apiErrorFeedback.value = e.localizedMessage
                showResultNotification(
                    isSuccess = false,
                    title = "Quiz Generation Failed",
                    message = e.localizedMessage ?: "Unknown error occurred"
                )
            } finally {
                isAILoading.value = false
            }
        }
    }

    fun getQuizQuestionsForSet(quizSetId: Int): Flow<List<QuizQuestion>> {
        return dao.getQuizQuestionsForSet(quizSetId)
    }

    fun submitQuizResponse(questionId: Int, answer: String, isCorrect: Boolean) {
        viewModelScope.launch {
            dao.submitQuizAnswer(questionId, answer, isCorrect)
            // Log today's study progress!
            addProgressActivity()
        }
    }

    fun resetQuizProgress(quizSetId: Int) {
        viewModelScope.launch {
            dao.resetQuizAnswers(quizSetId)
        }
    }

    fun deleteQuizCollection(set: QuizSet) {
        viewModelScope.launch {
            dao.deleteQuizQuestionsForSet(set.id)
            dao.deleteQuizSet(set)
        }
    }

    // --- Study Planner Schedule ---
    private fun scheduleEventAlarm(eventId: Int, subject: String, triggerTimeMillis: Long, alarmType: Int, title: String, message: String) {
        if (triggerTimeMillis <= System.currentTimeMillis()) return

        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(getApplication(), StudyAlarmReceiver::class.java).apply {
            putExtra("subject", subject)
            putExtra("eventId", eventId)
            putExtra("alarmType", alarmType)
            putExtra("title", title)
            putExtra("message", message)
        }
        val requestCode = eventId * 10 + alarmType
        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(),
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
        }
        Log.d("StudyMateVM", "Scheduled alert type $alarmType for event $eventId at $triggerTimeMillis")
    }

    private fun cancelEventAlarms(eventId: Int) {
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(getApplication(), StudyAlarmReceiver::class.java)
        for (alarmType in 1..3) {
            val requestCode = eventId * 10 + alarmType
            val pendingIntent = PendingIntent.getBroadcast(
                getApplication(),
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    fun addStudyEvent(subject: String, timeMillis: Long) {
        viewModelScope.launch {
            val eventId = dao.insertStudyEvent(
                StudyEvent(
                    subject = subject,
                    studyTimeMillis = timeMillis,
                    isCompleted = false,
                    notified = false
                )
            ).toInt()

            // Schedule alarm 1: 15 minutes before
            scheduleEventAlarm(
                eventId = eventId,
                subject = subject,
                triggerTimeMillis = timeMillis - 15 * 60 * 1000,
                alarmType = 1,
                title = "Study Session starting in 15 mins",
                message = "Your session for '$subject' starts in 15 minutes. Let's get ready!"
            )

            // Schedule alarm 2: 5 minutes before
            scheduleEventAlarm(
                eventId = eventId,
                subject = subject,
                triggerTimeMillis = timeMillis - 5 * 60 * 1000,
                alarmType = 2,
                title = "Study Session starting in 5 mins",
                message = "Your session for '$subject' starts in 5 minutes. Put everything else away!"
            )

            // Schedule Alarm 3: At the exact time of the event
            scheduleEventAlarm(
                eventId = eventId,
                subject = subject,
                triggerTimeMillis = timeMillis,
                alarmType = 3,
                title = "Study Session Starting Now!",
                message = "It's time to study '$subject'. Let's do this!"
            )

            // Direct Clock App Alarm Integration:
            // "If i plan a study with date and time the app should fix an alarm for that time in my clock app with the correct event name"
            try {
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = timeMillis
                }
                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val minutes = calendar.get(java.util.Calendar.MINUTE)
                val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) // Exact weekday of the study session

                val alarmIntent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                    putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minutes)
                    putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Study: $subject")
                    putExtra(android.provider.AlarmClock.EXTRA_DAYS, arrayListOf(dayOfWeek))
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<android.app.Application>().startActivity(alarmIntent)
            } catch (e: Exception) {
                Log.w("StudyMateVM", "Could not set physical clock alarm directly", e)
            }
            com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(getApplication())
        }
    }

    fun completeStudyEvent(eventId: Int, subject: String) {
        viewModelScope.launch {
            dao.updateEventCompletion(eventId, isCompleted = true)
            cancelEventAlarms(eventId)
            addProgressActivity()
            com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(getApplication())
        }
    }

    fun removeStudyEvent(event: StudyEvent) {
        viewModelScope.launch {
            dao.deleteStudyEvent(event)
            cancelEventAlarms(event.id)
            com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(getApplication())
        }
    }

    // --- Task Manager ---
    fun createTaskItem(title: String) {
        viewModelScope.launch {
            dao.insertTask(TaskItem(title = title, isCompleted = false))
            com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(getApplication())
        }
    }

    fun completeTaskItem(taskId: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            dao.updateTaskCompletion(taskId, isCompleted)
            if (isCompleted) {
                // Increment study activity for streaks!
                addProgressActivity()
            }
            com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(getApplication())
        }
    }

    fun removeTaskItem(task: TaskItem) {
        viewModelScope.launch {
            dao.deleteTask(task)
            com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(getApplication())
        }
    }

    // --- Streaks Progress & Calendar ---
    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun addProgressActivity() {
        viewModelScope.launch {
            val today = getTodayDateString()
            val existing = progressDays.value.firstOrNull { it.dateString == today }
            val count = if (existing != null) existing.countCompleted + 1 else 1
            dao.insertProgressDay(StudyProgress(dateString = today, countCompleted = count))
            com.example.widget.StudyProgressWidgetProvider.updateStudyWidget(getApplication())
        }
    }

    // Calculate streaks logic dynamically
    fun calculateCurrentStreak(): Int {
        val days = progressDays.value.map { it.dateString }.toSet()
        if (days.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        var streak = 0

        // Check if there is study recorded for today
        val todayStr = sdf.format(cal.time)
        val hasToday = days.contains(todayStr)

        // Check if there is study recorded for yesterday
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)
        val hasYesterday = days.contains(yesterdayStr)

        if (!hasToday && !hasYesterday) {
            return 0
        }

        // Reset to today OR yesterday depending on where we start
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

    fun calculateMaxStreak(): Int {
        val sortedDays = progressDays.value.map { it.dateString }.sorted()
        if (sortedDays.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var maxStreak = 0
        var currentStreak = 0
        var lastDate: Date? = null

        for (dayStr in sortedDays) {
            try {
                val date = sdf.parse(dayStr) ?: continue
                if (lastDate == null) {
                    currentStreak = 1
                } else {
                    val diff = date.time - lastDate.time
                    val diffDays = diff / (24 * 60 * 60 * 1000)
                    if (diffDays <= 1L) {
                        currentStreak++
                    } else {
                        if (currentStreak > maxStreak) {
                            maxStreak = currentStreak
                        }
                        currentStreak = 1
                    }
                }
                lastDate = date
            } catch (e: Exception) {
                // Ignore parsing issues
            }
        }

        if (currentStreak > maxStreak) {
            maxStreak = currentStreak
        }

        return maxStreak
    }

    // --- Custom API Keys Pool ---
    fun addCustomKey(key: String, label: String) {
        viewModelScope.launch {
            if (key.isNotBlank()) {
                dao.insertApiKey(ApiKeyEntry(key = key, label = label, isWorking = true))
            }
        }
    }

    fun removeCustomKey(entry: ApiKeyEntry) {
        viewModelScope.launch {
            dao.deleteApiKey(entry)
        }
    }

    // --- AI Summarizer ---
    fun generateSummary(
        title: String,
        pastedText: String,
        pdfPath: String?,
        lengthMode: String,
        structureMode: String,
        subject: String = "",
        chapter: String = ""
    ) {
        cancelActiveAIGeneration()
        activeAIGeneratorJob = viewModelScope.launch {
            isAILoading.value = true
            apiErrorFeedback.value = null
            showRunningNotification("Generating Summary", "Creating summary notes for '$title'...")
            try {
                var contentToSummarize = pastedText
                val base64Images = if (pdfPath != null) {
                    renderPdfPagesToBase64(pdfPath)
                } else null

                if (pdfPath != null && contentToSummarize.isBlank()) {
                    contentToSummarize = "Analyze this PDF file and perform notes summarization request."
                }

                val systemPrompt = """
                    You are an elite, professional study notes compiler and educational assistant.
                    Your goal is to write highly detailed, clear, and comprehensive study notes from the provided textbook or study materials.
                    IMPORTANT: Make sure to capture ALL key points, scientific theories, historic events, steps of any process, definitions, formulas, and details. DO NOT truncate or summarize loosely. Do not leave out details or lose any key point.
                    
                    Structure and Format instructions:
                    - Render the output in beautiful, standard raw Markdown formatting. Use nested headings of all levels ('#', '##', '###', '####') for elegant structure, '**bold**' for key words, '*' or '-' for bullet points, and numbered lists where sequential steps are mentioned.
                    - MATH / LATEX SUPPORT: You MUST format all mathematical formulas, scientific equations, chemical reactions, and technical/numeric expressions in clean LaTeX delimiters. Use centered block equations wrapped in '$' (e.g., ${'$'}${'$'}E = mc^2${'$'}${'$'}) for major formulas, and inline expressions wrapped in '${'$'}' (e.g., ${'$'}f(x) = \sin(x)${'$'}) for inline variables or expressions.
                    - TABLES SUPPORT: Use standard Markdown table syntax (e.g. '| Header | Header |' with separator rows) to present structured comparisons, classifications, scientific variables, data properties, or historical timelines wherever helpful and logical.
                    - DIAGRAMS / VISUAL REPRESENTATIONS SUPPORT: Whenever a process, hierarchy, flow, sequence, system architecture, or relationship between concepts is explained, you MUST represent it visually using standard, beautifully-styled pure HTML and CSS! Render the HTML/CSS code in a standard markdown code block with the language label 'html', and prepend the line `[diagram]` right after the opening triple-backticks to identify it as a diagram for the app. Keep the HTML visual, modern, colorful, eye-catching, and beautifully styled with embedded inline styles (e.g. styled cards, grids, flex containers, arrows, columns). For example:
                      ```html
                      [diagram]
                      <div style="background-color: #2c1e73; padding: 16px; border-radius: 8px; border: 1.5px solid #8e75ff; color: white; display: inline-block;">
                         <div style="font-weight: bold; font-size: 14px; margin-bottom: 4px;">1. Input Data</div>
                         <div style="color: #8e75ff; font-size: 16px; margin: 4px 0;">➔</div>
                         <div style="font-weight: bold; font-size: 14px;">2. Processing</div>
                      </div>
                      ```
                      Always provide both this HTML diagram block and a corresponding textual explanation.
                    - Never lose any technical detail, formula, diagram explanation, or historical context.
                    
                    1. Summary Length Mode: $lengthMode
                       - CONCISE: Keep it highly structured with bullet points. Cover all essential concepts comprehensively.
                       - STANDARD: A detailed overview of everything, complete with clear sections, explanations, lists, and formulas.
                       - DETAILED: A fully exhaustive study guide. Convert the input material into an expansive, thorough handbook with detailed paragraphs explaining each concept, fully detailed lists, step-by-step breakdowns of any mechanisms, with zero loss of academic points.
                    
                    2. Structure Layout Style: $structureMode
                       - Q&A style: Structure as a thorough, question-by-question academic grid (e.g. "Q1: ...", "A1: ...") explaining every core concept.
                       - DEFINITION style: Structure as an extensive glossary/textbook encyclopedia defining and explaining every relevant technical term, theory, concept, and system.
                """.trimIndent()

                val finalResponse = StringBuilder()

                if (base64Images != null && base64Images.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Generating study notes...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                val responseText = executeWithFallback { apiKey ->
                    val parts = mutableListOf<GeminiPart>()
                    parts.add(GeminiPart(text = "Subject Material Content:\n$contentToSummarize\n\nPlease structure an extremely detailed, high-resolution study note guide in Markdown for the material provided. Do not summarize briefly; ensure complete point-by-point coverage of the concept."))
                    if (base64Images != null) {
                        for (base64Image in base64Images) {
                            parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)))
                        }
                    }

                    val request = GeminiRequest(
                        contents = listOf(
                            GeminiContent(listOf(GeminiPart(text = systemPrompt))),
                            GeminiContent(parts)
                        ),
                        generationConfig = GeminiGenerationConfig(temperature = 0.4f)
                    )
                    GeminiNetwork.api.generateContent(apiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: throw Exception("No AI responses gathered for summarization.")
                }
                finalResponse.append(responseText)

                // Save as a text note in the system
                createTextNote(title, finalResponse.toString(), subject, chapter)

                showResultNotification(
                    isSuccess = true,
                    title = "Summary Notes Created",
                    message = "Successfully created summary notes for '$title'."
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error generating summary", e)
                apiErrorFeedback.value = e.localizedMessage
                showResultNotification(
                    isSuccess = false,
                    title = "Summary Generation Failed",
                    message = e.localizedMessage ?: "Unknown error occurred"
                )
            } finally {
                isAILoading.value = false
            }
        }
    }

    // --- AI Formula Sheet Generator ---
    fun generateFormulaSheet(
        title: String,
        pastedText: String,
        pdfPath: String?,
        subject: String = "",
        chapter: String = ""
    ) {
        cancelActiveAIGeneration()
        activeAIGeneratorJob = viewModelScope.launch {
            isAILoading.value = true
            apiErrorFeedback.value = null
            showRunningNotification("Generating Formulas", "Extracting formulas for '$title'...")
            try {
                var contentToExtract = pastedText
                val base64Images = if (pdfPath != null) {
                    renderPdfPagesToBase64(pdfPath)
                } else null

                if (pdfPath != null && contentToExtract.isBlank()) {
                    contentToExtract = "Extract all mathematical, physical, chemical or engineering formulas and important quick-reference high-yield points from this PDF study material."
                }

                val systemPrompt = """
                    You are an expert scientific researcher, mathematician, and Academic Formula Sheet compiler.
                    Your sole goal is to extract ALL equations, formulas, physical constants, chemical reactions, mathematical laws, and corresponding key quick-reference points from the provided textbook page or study materials.
                    
                    Rules of output extraction:
                    1. ONLY extract formulas, variables definitions, constants, and high-yield key study points, tips, or facts.
                    2. Do NOT write unnecessary introductory text, commentary or filler paragraphs.
                    3. MATH / LATEX SUPPORT: You MUST format all mathematical formulas, scientific equations, chemical reactions, and technical/numeric expressions in clean LaTeX delimiters. Use centered block equations wrapped in '$' (e.g., ${'$'}${'$'}v = u + at${'$'}${'$'}) for major formulas, and inline expressions wrapped in '${'$'}' (e.g., ${'$'}t${'$'} is time) for inline variables or expressions.
                    4. Render the entire output in beautiful, elegant raw Markdown formatting:
                       - Organised chapters with '##' and '###' or '####' headings (e.g. "## Kinematics Formulas", "## Key Constants")
                       - Use bold text for terms and definitions.
                       - Bulleted lists or standard Markdown tables (e.g. '| Parameter | Symbol | Meaning |' with separator rows) of all parameters / variable symbols used.
                    5. Ensure total precision: double check symbol signs, exponents, and subscript markers for absolute scientific correctness.
                """.trimIndent()

                val finalResponse = StringBuilder()

                if (base64Images != null && base64Images.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Generating formula sheet...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                val responseText = executeWithFallback { apiKey ->
                    val parts = mutableListOf<GeminiPart>()
                    parts.add(GeminiPart(text = "Study/Textbook Material Content:\n$contentToExtract\n\nPlease compile a comprehensive Academic Formula Sheet and High-Yield Study Key Points guide from the material provided."))
                    if (base64Images != null) {
                        for (base64Image in base64Images) {
                            parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)))
                        }
                    }

                    val request = GeminiRequest(
                        contents = listOf(
                            GeminiContent(listOf(GeminiPart(text = systemPrompt))),
                            GeminiContent(parts)
                        ),
                        generationConfig = GeminiGenerationConfig(temperature = 0.2f)
                    )
                    GeminiNetwork.api.generateContent(apiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: throw Exception("No formulas or key points could be extracted.")
                }
                finalResponse.append(responseText)

                // Save as a text note in the system
                createTextNote(title, finalResponse.toString(), subject, chapter)

                showResultNotification(
                    isSuccess = true,
                    title = "Formula Sheet Created",
                    message = "Successfully created formula sheet for '$title'."
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error generating formula sheet", e)
                apiErrorFeedback.value = e.localizedMessage
                showResultNotification(
                    isSuccess = false,
                    title = "Formula Sheet Generation Failed",
                    message = e.localizedMessage ?: "Unknown error occurred"
                )
            } finally {
                isAILoading.value = false
            }
        }
    }

    // --- Mind Map Generator ---
    fun generateMindMap(
        title: String,
        pastedText: String,
        pdfPath: String?,
        subject: String = "",
        chapter: String = ""
    ) {
        cancelActiveAIGeneration()
        activeAIGeneratorJob = viewModelScope.launch {
            isAILoading.value = true
            apiErrorFeedback.value = null
            showRunningNotification("Generating Mind Map", "Mapping topics for '$title'...")
            try {
                var contentToMap = pastedText
                val base64Images = if (pdfPath != null) {
                    renderPdfPagesToBase64(pdfPath)
                } else null

                if (pdfPath != null && contentToMap.isBlank()) {
                    contentToMap = "Extract the study text from the uploaded PDF pages and represent it as a mind map."
                }

                val systemPrompt = """
                    You are an expert Mind Map Generator. Produce an extremely detailed, expansive, and deep multi-tiered hierarchical mind map from the provided text/image.
                    IMPORTANT: You must branch out EVERY single sub-concept, subtopic, detail, definition, and example present in the source material.
                    Make the mind map tree very comprehensive, spanning multiple levels of depth (e.g., grand-children and great-grand-children nodes) to thoroughly map out all connections with zero loss of points.
                    
                    You must return ONLY a single valid JSON object containing branches and sub-branches representing the hierarchy of concepts.
                    The JSON output schema MUST look exactly like this:
                    {
                      "topic": "Main Topic Name",
                      "children": [
                        {
                          "topic": "Syllabus Branch A",
                          "children": [
                            {
                              "topic": "Concept A.1",
                              "children": [
                                { "topic": "Detail A.1.a" },
                                { "topic": "Detail A.1.b" }
                              ]
                            },
                            { "topic": "Concept A.2", "children": [] }
                          ]
                        }
                      ]
                    }
                    
                    Return raw valid JSON ONLY. Ensure all JSON fields are quotes-escaped properly. Avoid wrapping in markdown markers or adding any surrounding text.
                """.trimIndent()

                val finalChildrenList = mutableListOf<org.json.JSONObject>()

                if (base64Images != null && base64Images.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Generating mind map...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                val responseText = executeWithFallback { apiKey ->
                    val parts = mutableListOf<GeminiPart>()
                    parts.add(GeminiPart(text = "Subject material content:\n$contentToMap\n\nPlease structure an extremely detailed, multi-level hierarchical mind map in JSON now. Include multiple generations of sub-nodes to cover everything thoroughly."))
                    if (base64Images != null) {
                        for (base64Image in base64Images) {
                            parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)))
                        }
                    }

                    val request = GeminiRequest(
                        contents = listOf(
                            GeminiContent(listOf(GeminiPart(text = systemPrompt))),
                            GeminiContent(parts)
                        ),
                        generationConfig = GeminiGenerationConfig(temperature = 0.5f)
                    )
                    GeminiNetwork.api.generateContent(apiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: throw Exception("No AI responses gathered for mind map.")
                }

                var cleanJson = responseText.trim()
                if (cleanJson.startsWith("```json")) {
                    cleanJson = cleanJson.removePrefix("```json")
                }
                if (cleanJson.endsWith("```")) {
                    cleanJson = cleanJson.removeSuffix("```")
                }
                cleanJson = cleanJson.trim()
                finalChildrenList.add(org.json.JSONObject(cleanJson))

                if (finalChildrenList.isEmpty()) {
                    throw Exception("Failed to generate or parse any mind map parts.")
                }

                val finalJsonString = if (finalChildrenList.size == 1) {
                    finalChildrenList[0].toString()
                } else {
                    val masterNode = org.json.JSONObject()
                    masterNode.put("topic", title)
                    val childrenArray = org.json.JSONArray()
                    for (child in finalChildrenList) {
                        childrenArray.put(child)
                    }
                    masterNode.put("children", childrenArray)
                    masterNode.toString()
                }

                // Save as a MINDMAP type note in the Room Database
                dao.insertNote(
                    NoteEntry(
                        title = title,
                        content = finalJsonString,
                        fileType = "MINDMAP",
                        filePath = null, // No physical text file needed as JSON is in room
                        subject = subject,
                        chapter = chapter
                    )
                )

                showResultNotification(
                    isSuccess = true,
                    title = "Mind Map Created",
                    message = "Successfully created mind map for '$title'."
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error generating mind map", e)
                apiErrorFeedback.value = "Error parsing or generating: ${e.localizedMessage}"
                showResultNotification(
                    isSuccess = false,
                    title = "Mind Map Generation Failed",
                    message = e.localizedMessage ?: "Unknown error occurred"
                )
            } finally {
                isAILoading.value = false
            }
        }
    }

    fun downloadMindMapAsHtml(context: Context, title: String, jsonContent: String) {
        viewModelScope.launch {
            try {
                val node = parseMindMapJson(jsonContent)
                val htmlTree = generateHtmlTree(node)
                val fullHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <title>StudyMate Pro - $title</title>
                        <style>
                            :root {
                                --bg-color: #0c0824;
                                --card-bg: #211951;
                                --accent-color: #8e75ff;
                                --text-color: #faf9ff;
                                --muted-text: #ccc4ff;
                                --border-color: rgba(142, 117, 255, 0.4);
                                --node-shadow: 0 4px 10px rgba(0, 0, 0, 0.4);
                            }

                            body, html {
                                margin: 0;
                                padding: 0;
                                width: 100%;
                                height: 100%;
                                font-family: system-ui, -apple-system, sans-serif;
                                background-color: var(--bg-color);
                                color: var(--text-color);
                                overflow: hidden; /* JavaScript drag/pan */
                            }

                            /* Controls Window */
                            .controls {
                                position: fixed;
                                top: 20px;
                                left: 20px;
                                z-index: 1000;
                                background: rgba(33, 25, 81, 0.85);
                                backdrop-filter: blur(10px);
                                border: 1.5px solid var(--accent-color);
                                border-radius: 12px;
                                padding: 15px;
                                box-shadow: 0 8px 32px rgba(0,0,0,0.5);
                                max-width: 300px;
                                transition: all 0.3s ease;
                            }
                            .controls h2 {
                                margin: 0 0 10px 0;
                                font-size: 16px;
                                color: var(--accent-color);
                                font-weight: 800;
                            }
                            .controls p {
                                margin: 0 0 15px 0;
                                font-size: 11px;
                                color: var(--muted-text);
                                line-height: 1.4;
                            }
                            .btn-group {
                                display: flex;
                                flex-wrap: wrap;
                                gap: 8px;
                                margin-bottom: 12px;
                            }
                            .btn {
                                background-color: var(--accent-color);
                                color: var(--bg-color);
                                border: none;
                                padding: 6px 12px;
                                border-radius: 6px;
                                font-size: 11px;
                                font-weight: bold;
                                cursor: pointer;
                                transition: all 0.2s ease;
                            }
                            .btn:hover {
                                transform: translateY(-2px);
                                filter: brightness(1.1);
                            }
                            .btn.secondary {
                                background-color: transparent;
                                color: var(--accent-color);
                                border: 1px solid var(--accent-color);
                            }
                            .btn.secondary:hover {
                                background: rgba(142, 117, 255, 0.15);
                            }

                            .layout-banner {
                                display: flex;
                                align-items: center;
                                gap: 8px;
                                font-size: 11px;
                                color: var(--muted-text);
                            }

                            .viewport {
                                width: 100%;
                                height: 100%;
                                cursor: grab;
                                overflow: auto;
                                position: relative;
                                user-select: none;
                            }
                            .viewport:active {
                                cursor: grabbing;
                            }
                            .canvas-container {
                                position: absolute;
                                transform-origin: 0 0;
                                padding: 300px;
                                display: inline-block;
                                transition: transform 0.05s ease-out;
                            }

                            /* Traditional Horizontal CSS Tree Layout (Top-to-Bottom) */
                            .tree {
                                display: inline-block;
                                white-space: nowrap;
                                text-align: center;
                                margin: 0 auto;
                            }
                            .tree ul {
                                padding-top: 20px;
                                position: relative;
                                transition: all 0.5s;
                                display: flex;
                                justify-content: center;
                                padding-left: 0;
                                margin: 0;
                            }
                            .tree li {
                                text-align: center;
                                list-style-type: none;
                                position: relative;
                                padding: 20px 8px 0 8px;
                                transition: all 0.5s;
                                vertical-align: top;
                                display: inline-block;
                            }

                            /* Connector Lines (Vertical Top-Down) */
                            .tree li::before, .tree li::after {
                                content: '';
                                position: absolute;
                                top: 0;
                                right: 50%;
                                border-top: 2px solid var(--accent-color);
                                width: 50%;
                                height: 20px;
                            }
                            .tree li::after {
                                right: auto;
                                left: 50%;
                                border-left: 2px solid var(--accent-color);
                            }
                            .tree li:only-child::after, .tree li:only-child::before {
                                display: none;
                            }
                            .tree li:only-child { padding-top: 0; }
                            .tree li:first-child::before, .tree li:last-child::after {
                                border: 0 none;
                            }
                            .tree li:last-child::before {
                                border-right: 2px solid var(--accent-color);
                                border-radius: 0 5px 0 0;
                            }
                            .tree li:first-child::after {
                                border-radius: 5px 0 0 0;
                            }
                            .tree ul ul::before {
                                content: '';
                                position: absolute;
                                top: 0;
                                left: 50%;
                                border-left: 2px solid var(--accent-color);
                                width: 0;
                                height: 20px;
                            }

                            /* Node Text Box Design */
                            .tree li .node-text {
                                border: 2.5px solid var(--accent-color);
                                padding: 12px 24px;
                                text-decoration: none;
                                color: var(--text-color);
                                background-color: var(--card-bg);
                                font-weight: bold;
                                font-size: 14px;
                                border-radius: 12px;
                                display: inline-flex;
                                align-items: center;
                                gap: 8px;
                                transition: all 0.3s;
                                box-shadow: var(--node-shadow);
                                cursor: pointer;
                                white-space: normal;
                                max-width: 250px;
                                min-width: 120px;
                                text-align: center;
                                position: relative;
                            }
                            .tree li .node-text:hover {
                                background: var(--accent-color);
                                color: var(--bg-color);
                                transform: translateY(-3px);
                                box-shadow: 0 8px 18px rgba(142, 117, 255, 0.4);
                            }

                            /* Collapse/Expand classes */
                            .tree li.collapsed > ul {
                                display: none !important;
                            }
                            
                            .toggle-icon {
                                display: inline-block;
                                width: 16px;
                                height: 16px;
                                background: rgba(255,255,255,0.1);
                                color: var(--accent-color);
                                border-radius: 50%;
                                text-align: center;
                                line-height: 14px;
                                font-size: 11px;
                                font-weight: bold;
                                transition: all 0.2s;
                                border: 1px solid var(--accent-color);
                            }
                            .node-text:hover .toggle-icon {
                                background: var(--bg-color);
                                color: var(--accent-color);
                            }
                            
                            li.parent > .node-text .toggle-icon::before {
                                content: '−';
                            }
                            li.parent.collapsed > .node-text .toggle-icon::before {
                                content: '+';
                            }
                            li.parent.collapsed > .node-text {
                                border-style: dashed;
                                opacity: 0.85;
                            }

                            /* ================== VERTICAL LEFT-TO-RIGHT TREE LAYOUT ================== */
                            body.vertical-mode .tree ul {
                                flex-direction: column;
                                padding-top: 0;
                                padding-left: 40px;
                                align-items: flex-start;
                            }
                            body.vertical-mode .tree li {
                                display: flex;
                                align-items: center;
                                padding: 10px 0;
                                position: relative;
                                text-align: left;
                            }
                            
                            body.vertical-mode .tree li::before, body.vertical-mode .tree li::after {
                                display: none;
                            }
                            body.vertical-mode .tree ul ul::before {
                                display: none;
                            }

                            body.vertical-mode .tree li::before {
                                content: '';
                                position: absolute;
                                left: -20px;
                                top: 50%;
                                border-top: 2px solid var(--accent-color);
                                width: 20px;
                                height: 0;
                                display: block;
                            }
                            body.vertical-mode .tree li:only-child::before {
                                display: block;
                            }
                            body.vertical-mode .tree ul::before {
                                content: '';
                                position: absolute;
                                left: 20px;
                                top: 20px;
                                bottom: 20px;
                                border-left: 2px solid var(--accent-color);
                                width: 0;
                                display: block;
                            }
                            body.vertical-mode .tree > ul::before {
                                display: none;
                            }
                            body.vertical-mode .tree > ul > li::before {
                                display: none;
                            }

                            body.vertical-mode .tree li .node-text {
                                text-align: left;
                                margin-right: 20px;
                            }
                        </style>
                    </head>
                    <body class="vertical-mode">
                        <div class="controls">
                            <h2>$title</h2>
                            <p>StudyMate Pro Interactive Mind Map. Click to drag/pan, scroll to zoom. Click nodes to toggle.</p>
                            <div class="btn-group">
                                <button class="btn" onclick="zoomIn()">Zoom +</button>
                                <button class="btn" onclick="zoomOut()">Zoom −</button>
                                <button class="btn" onclick="resetZoom()">Reset</button>
                                <button class="btn" onclick="toggleLayout()">Change View</button>
                            </div>
                            <div class="btn-group">
                                <button class="btn secondary" onclick="expandAll()">Expand All</button>
                                <button class="btn secondary" onclick="collapseAll()">Collapse All</button>
                            </div>
                            <div class="layout-banner">
                                <span>Layout:</span>
                                <strong id="layout-label">Vertical Map</strong>
                            </div>
                        </div>

                        <div class="viewport" id="viewport">
                            <div class="canvas-container" id="canvas">
                                <div class="tree">
                                    <ul>
                                        $htmlTree
                                    </ul>
                                </div>
                            </div>
                        </div>

                        <script>
                            var scale = 0.9;
                            var panX = 50;
                            var panY = 50;
                            var isDragging = false;
                            var startX, startY;

                            const viewport = document.getElementById('viewport');
                            const canvas = document.getElementById('canvas');

                            updateTransform();

                            viewport.addEventListener('mousedown', function(e) {
                                if (e.target.closest('.controls') || e.target.closest('.node-text')) return;
                                isDragging = true;
                                startX = e.clientX - panX;
                                startY = e.clientY - panY;
                                viewport.style.cursor = 'grabbing';
                            });

                            window.addEventListener('mousemove', function(e) {
                                if (!isDragging) return;
                                panX = e.clientX - startX;
                                panY = e.clientY - startY;
                                updateTransform();
                            });

                            window.addEventListener('mouseup', function() {
                                isDragging = false;
                                viewport.style.cursor = 'grab';
                            });

                            viewport.addEventListener('touchstart', function(e) {
                                if (e.target.closest('.controls') || e.target.closest('.node-text')) return;
                                if (e.touches.length === 1) {
                                    isDragging = true;
                                    startX = e.touches[0].clientX - panX;
                                    startY = e.touches[0].clientY - panY;
                                }
                            });

                            viewport.addEventListener('touchmove', function(e) {
                                if (!isDragging) return;
                                if (e.touches.length === 1) {
                                    panX = e.touches[0].clientX - startX;
                                    panY = e.touches[0].clientY - startY;
                                    updateTransform();
                                }
                            });

                            viewport.addEventListener('touchend', function() {
                                isDragging = false;
                            });

                            viewport.addEventListener('wheel', function(e) {
                                e.preventDefault();
                                const zoomSpeed = 0.05;
                                if (e.deltaY < 0) {
                                    scale = Math.min(2.5, scale + zoomSpeed);
                                } else {
                                    scale = Math.max(0.3, scale - zoomSpeed);
                                }
                                updateTransform();
                            }, { passive: false });

                            function updateTransform() {
                                canvas.style.transform = 'translate(' + panX + 'px, ' + panY + 'px) scale(' + scale + ')';
                            }

                            function zoomIn() {
                                scale = Math.min(2.5, scale + 0.15);
                                updateTransform();
                            }

                            function zoomOut() {
                                scale = Math.max(0.3, scale - 0.15);
                                updateTransform();
                            }

                            function resetZoom() {
                                scale = 0.9;
                                panX = 50;
                                panY = 50;
                                updateTransform();
                            }

                            function toggleLayout() {
                                const body = document.body;
                                const label = document.getElementById('layout-label');
                                if (body.classList.contains('vertical-mode')) {
                                    body.classList.remove('vertical-mode');
                                    label.innerText = 'Horizontal Map';
                                } else {
                                    body.classList.add('vertical-mode');
                                    label.innerText = 'Vertical Map';
                                }
                            }

                            function toggleNode(element) {
                                const li = element.closest('li');
                                if (li && li.classList.contains('parent')) {
                                    li.classList.toggle('collapsed');
                                }
                            }

                            function expandAll() {
                                document.querySelectorAll('li.parent').forEach(li => {
                                    li.classList.remove('collapsed');
                                });
                            }

                            function collapseAll() {
                                document.querySelectorAll('li.parent').forEach(li => {
                                    li.classList.add('collapsed');
                                });
                            }
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                val fileName = "mindmap_${System.currentTimeMillis()}.html"
                val localFile = File(context.filesDir, fileName)
                localFile.writeText(fullHtml)
                
                // Now download this local html file to external downloads
                val note = NoteEntry(
                    title = "html_$title.html",
                    content = "html mind map",
                    fileType = "HTML",
                    filePath = localFile.absolutePath
                )
                downloadNoteFile(context, note)
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error exporting HTML mindmap", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun generateHtmlTree(node: MindMapNode): String {
        val builder = java.lang.StringBuilder()
        val hasChildren = node.children.isNotEmpty()
        builder.append("<li class=\"${if (hasChildren) "parent" else "leaf"}\">")
        builder.append("<span class=\"node-text\" onclick=\"toggleNode(this)\">")
        builder.append(escapeHtml(node.topic))
        if (hasChildren) {
            builder.append("<span class=\"toggle-icon\"></span>")
        }
        builder.append("</span>")
        if (hasChildren) {
            builder.append("<ul>")
            for (child in node.children) {
                builder.append(generateHtmlTree(child))
            }
            builder.append("</ul>")
        }
        builder.append("</li>")
        return builder.toString()
    }

    // --- ACCOUNT DATA BACKUP AND RESTORE SYSTEM (EXPORT/IMPORT ZIP) ---
    fun exportBackup(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Compile backup metadata JSON
                val jsonBackup = JSONObject().apply {
                    put("version", 1)
                    put("export_time", System.currentTimeMillis())

                    // SharedPreferences Backup
                    val prefsJson = JSONObject().apply {
                        put("user_name", prefs.getString("user_name", ""))
                        put("user_email", prefs.getString("user_email", ""))
                        put("user_registered_email", prefs.getString("user_registered_email", ""))
                        put("user_registered_p", prefs.getString("user_registered_p", ""))
                        
                        val subjArr = JSONArray()
                        prefs.getStringSet("subjects", emptySet())?.forEach { subjArr.put(it) }
                        put("subjects", subjArr)

                        val chapArr = JSONArray()
                        prefs.getStringSet("chapters", emptySet())?.forEach { chapArr.put(it) }
                        put("chapters", chapArr)

                        val chatArr = JSONArray()
                        prefs.getStringSet("saved_chat_history_v3", emptySet())?.forEach { chatArr.put(it) }
                        put("saved_chat_history_v3", chatArr)
                    }
                    put("shared_prefs", prefsJson)

                    // Database Tables Serialization
                    val notesArray = JSONArray()
                    val notes = dao.getAllNotesDirect()
                    for (n in notes) {
                        notesArray.put(JSONObject().apply {
                            put("id", n.id)
                            put("title", n.title)
                            put("content", n.content)
                            put("fileType", n.fileType)
                            put("filePath", n.filePath?.let { File(it).name })
                            put("subject", n.subject)
                            put("chapter", n.chapter)
                            put("createdAt", n.createdAt)
                        })
                    }
                    put("notes", notesArray)

                    val fSetsArray = JSONArray()
                    val fcardSets = dao.getAllFlashcardSetsDirect()
                    for (fs in fcardSets) {
                        fSetsArray.put(JSONObject().apply {
                            put("id", fs.id)
                            put("title", fs.title)
                            put("createdAt", fs.createdAt)
                        })
                    }
                    put("flashcard_sets", fSetsArray)

                    val fItemsArray = JSONArray()
                    val fcardItems = dao.getAllFlashcardItemsDirect()
                    for (fi in fcardItems) {
                        fItemsArray.put(JSONObject().apply {
                            put("id", fi.id)
                            put("setId", fi.setId)
                            put("question", fi.question)
                            put("answer", fi.answer)
                            put("isKnown", fi.isKnown)
                        })
                    }
                    put("flashcard_items", fItemsArray)

                    val qSetsArray = JSONArray()
                    val quizSets = dao.getAllQuizSetsDirect()
                    for (qs in quizSets) {
                        qSetsArray.put(JSONObject().apply {
                            put("id", qs.id)
                            put("title", qs.title)
                            put("createdAt", qs.createdAt)
                        })
                    }
                    put("quiz_sets", qSetsArray)

                    val qQuestionsArray = JSONArray()
                    val quizQuestions = dao.getAllQuizQuestionsDirect()
                    for (qq in quizQuestions) {
                        qQuestionsArray.put(JSONObject().apply {
                            put("id", qq.id)
                            put("quizSetId", qq.quizSetId)
                            put("question", qq.question)
                            put("optionsString", qq.optionsString)
                            put("correctAnswer", qq.correctAnswer)
                            put("userAnswer", qq.userAnswer ?: "")
                            put("isCorrect", qq.isCorrect ?: false)
                        })
                    }
                    put("quiz_questions", qQuestionsArray)

                    val sEventsArray = JSONArray()
                    val studyEvents = dao.getAllStudyEventsDirect()
                    for (se in studyEvents) {
                        sEventsArray.put(JSONObject().apply {
                            put("id", se.id)
                            put("subject", se.subject)
                            put("studyTimeMillis", se.studyTimeMillis)
                            put("isCompleted", se.isCompleted)
                            put("notified", se.notified)
                        })
                    }
                    put("study_events", sEventsArray)

                    val tasksArray = JSONArray()
                    val tasks = dao.getAllTasksDirect()
                    for (t in tasks) {
                        tasksArray.put(JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("isCompleted", t.isCompleted)
                            put("createdAt", t.createdAt)
                        })
                    }
                    put("tasks", tasksArray)

                    val progressArray = JSONArray()
                    val progressList = dao.getAllStudyProgressDirect()
                    for (p in progressList) {
                        progressArray.put(JSONObject().apply {
                            put("dateString", p.dateString)
                            put("countCompleted", p.countCompleted)
                        })
                    }
                    put("study_progress", progressArray)

                    val apiKeysArray = JSONArray()
                    val apiKeys = dao.getAllApiKeysDirect()
                    for (ak in apiKeys) {
                        apiKeysArray.put(JSONObject().apply {
                            put("id", ak.id)
                            put("key", ak.key)
                            put("label", ak.label)
                            put("isWorking", ak.isWorking)
                            put("addedAt", ak.addedAt)
                        })
                    }
                    put("api_keys", apiKeysArray)
                }

                // 2. Write details to temp file
                val metadataFile = File(context.cacheDir, "backup_metadata.json")
                metadataFile.writeText(jsonBackup.toString(4))

                // Create ZIP file inside context's cacheDir
                val zipFileName = "studymate_backup_${System.currentTimeMillis()}.zip"
                val zipFile = File(context.cacheDir, zipFileName)
                
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    // Pack backup_metadata.json
                    val metadataEntry = ZipEntry("backup_metadata.json")
                    zos.putNextEntry(metadataEntry)
                    metadataFile.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()

                    // Pack physical files in filesDir (referenced by notes)
                    val notes = dao.getAllNotesDirect()
                    for (note in notes) {
                        if (!note.filePath.isNullOrBlank()) {
                            val f = File(note.filePath)
                            if (f.exists() && f.isFile) {
                                val fileEntry = ZipEntry("files/${f.name}")
                                zos.putNextEntry(fileEntry)
                                f.inputStream().use { input ->
                                    input.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }

                metadataFile.delete()

                // Save ZIP file to the phone's public downloads directory
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, zipFileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                }

                val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                } else {
                    null
                }

                if (targetUri != null) {
                    resolver.openOutputStream(targetUri)?.use { output ->
                        zipFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    zipFile.delete()
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Full Backup ZIP exported successfully to Downloads folder!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    val fallbackFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), zipFileName)
                    zipFile.copyTo(fallbackFile, overwrite = true)
                    zipFile.delete()
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Backup exported successfully to: ${fallbackFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error exporting backup", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Backup failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importBackup(context: Context, backupUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val tempZip = File(context.cacheDir, "temp_import.zip")
                resolver.openInputStream(backupUri)?.use { input ->
                    tempZip.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (!tempZip.exists() || tempZip.length() == 0L) {
                    throw Exception("Could not access or read the selected file.")
                }

                var metadataContent: String? = null

                // Extract Zip files
                ZipInputStream(FileInputStream(tempZip)).use { zis ->
                    var entry = zis.nextEntry
                    val buffer = ByteArray(4096)
                    while (entry != null) {
                        if (entry.name == "backup_metadata.json") {
                            val baos = ByteArrayOutputStream()
                            val chunk = ByteArray(4096)
                            var count = zis.read(chunk)
                            while (count != -1) {
                                baos.write(chunk, 0, count)
                                count = zis.read(chunk)
                            }
                            metadataContent = baos.toString("UTF-8")
                        } else if (entry.name.startsWith("files/")) {
                            val name = entry.name.substringAfter("files/")
                            if (name.isNotEmpty()) {
                                val destFile = File(context.filesDir, name)
                                FileOutputStream(destFile).use { output ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        output.write(buffer, 0, len)
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                tempZip.delete()

                if (metadataContent == null) {
                    throw Exception("Invalid backup backup: metadata file index (backup_metadata.json) missing inside zip.")
                }

                val jsonBackup = JSONObject(metadataContent!!)

                // Restore SharedPreferences
                val prefsJson = jsonBackup.optJSONObject("shared_prefs")
                if (prefsJson != null) {
                    val editor = prefs.edit()
                    editor.putString("user_name", prefsJson.optString("user_name", ""))
                    editor.putString("user_email", prefsJson.optString("user_email", ""))
                    editor.putString("user_registered_email", prefsJson.optString("user_registered_email", ""))
                    editor.putString("user_registered_p", prefsJson.optString("user_registered_p", ""))
                    
                    val subjArr = prefsJson.optJSONArray("subjects")
                    if (subjArr != null) {
                        val set = mutableSetOf<String>()
                        for (i in 0 until subjArr.length()) {
                            set.add(subjArr.getString(i))
                        }
                        editor.putStringSet("subjects", set)
                    }

                    val chapArr = prefsJson.optJSONArray("chapters")
                    if (chapArr != null) {
                        val set = mutableSetOf<String>()
                        for (i in 0 until chapArr.length()) {
                            set.add(chapArr.getString(i))
                        }
                        editor.putStringSet("chapters", set)
                    }

                    val chatArr = prefsJson.optJSONArray("saved_chat_history_v3")
                    if (chatArr != null) {
                        val set = mutableSetOf<String>()
                        for (i in 0 until chatArr.length()) {
                            set.add(chatArr.getString(i))
                        }
                        editor.putStringSet("saved_chat_history_v3", set)
                    }
                    editor.apply()
                }

                // Delete Database Tables
                dao.clearNotes()
                dao.clearFlashcardSets()
                dao.clearFlashcardItems()
                dao.clearQuizSets()
                dao.clearQuizQuestions()
                dao.clearStudyEvents()
                dao.clearTasks()
                dao.clearStudyProgress()
                dao.clearApiKeys()

                // Insert Database tables
                // 1. Notes
                val notesArray = jsonBackup.optJSONArray("notes")
                if (notesArray != null) {
                    for (i in 0 until notesArray.length()) {
                        val obj = notesArray.getJSONObject(i)
                        val shortPath = obj.optString("filePath", "")
                        val fullPath = if (shortPath.isNotEmpty()) {
                            File(context.filesDir, shortPath).absolutePath
                        } else null

                        dao.insertNote(NoteEntry(
                            title = obj.optString("title", "Notes"),
                            content = obj.optString("content", ""),
                            fileType = obj.optString("fileType", "TEXT"),
                            filePath = fullPath,
                            subject = obj.optString("subject", ""),
                            chapter = obj.optString("chapter", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        ))
                    }
                }

                // 2. Flashcard Decks
                val decksArray = jsonBackup.optJSONArray("flashcard_sets")
                val deckMap = mutableMapOf<Int, Int>() // oldId -> newId mapping
                if (decksArray != null) {
                    for (i in 0 until decksArray.length()) {
                        val obj = decksArray.getJSONObject(i)
                        val oldId = obj.optInt("id")
                        val newId = dao.insertFlashcardSet(FlashcardSet(
                            title = obj.optString("title", "Flashcards"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        ))
                        deckMap[oldId] = newId.toInt()
                    }
                }

                // 3. Flashcard Items
                val fItemsArray = jsonBackup.optJSONArray("flashcard_items")
                if (fItemsArray != null) {
                    val list = mutableListOf<FlashcardItem>()
                    for (i in 0 until fItemsArray.length()) {
                        val obj = fItemsArray.getJSONObject(i)
                        val oldSetId = obj.optInt("setId")
                        val newSetId = deckMap[oldSetId]
                        if (newSetId != null) {
                            list.add(FlashcardItem(
                                setId = newSetId,
                                question = obj.optString("question", ""),
                                answer = obj.optString("answer", ""),
                                isKnown = obj.optBoolean("isKnown", false)
                            ))
                        }
                    }
                    dao.insertFlashcardItems(list)
                }

                // 4. Quiz Decks
                val qSetsArray = jsonBackup.optJSONArray("quiz_sets")
                val quizMap = mutableMapOf<Int, Int>() // oldId -> newId mapping
                if (qSetsArray != null) {
                    for (i in 0 until qSetsArray.length()) {
                        val obj = qSetsArray.getJSONObject(i)
                        val oldId = obj.optInt("id")
                        val newId = dao.insertQuizSet(QuizSet(
                            title = obj.optString("title", "Quiz"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        ))
                        quizMap[oldId] = newId.toInt()
                    }
                }

                // 5. Quiz Questions
                val qQuestionsArray = jsonBackup.optJSONArray("quiz_questions")
                if (qQuestionsArray != null) {
                    val list = mutableListOf<QuizQuestion>()
                    for (i in 0 until qQuestionsArray.length()) {
                        val obj = qQuestionsArray.getJSONObject(i)
                        val oldSetId = obj.optInt("quizSetId")
                        val newSetId = quizMap[oldSetId]
                        if (newSetId != null) {
                            list.add(QuizQuestion(
                                quizSetId = newSetId,
                                question = obj.optString("question", ""),
                                optionsString = obj.optString("optionsString", ""),
                                correctAnswer = obj.optString("correctAnswer", ""),
                                userAnswer = obj.optString("userAnswer", "").takeIf { it.isNotEmpty() },
                                isCorrect = if (obj.has("isCorrect")) obj.optBoolean("isCorrect", false) else null
                            ))
                        }
                    }
                    dao.insertQuizQuestions(list)
                }

                // 6. Study Events
                val sEventsArray = jsonBackup.optJSONArray("study_events")
                if (sEventsArray != null) {
                    for (i in 0 until sEventsArray.length()) {
                        val obj = sEventsArray.getJSONObject(i)
                        dao.insertStudyEvent(StudyEvent(
                            subject = obj.optString("subject", ""),
                            studyTimeMillis = obj.optLong("studyTimeMillis", System.currentTimeMillis()),
                            isCompleted = obj.optBoolean("isCompleted", false),
                            notified = obj.optBoolean("notified", false)
                        ))
                    }
                }

                // 7. Tasks
                val tasksArray = jsonBackup.optJSONArray("tasks")
                if (tasksArray != null) {
                    for (i in 0 until tasksArray.length()) {
                        val obj = tasksArray.getJSONObject(i)
                        dao.insertTask(TaskItem(
                            title = obj.optString("title", ""),
                            isCompleted = obj.optBoolean("isCompleted", false),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        ))
                    }
                }

                // 8. Progress
                val progressArray = jsonBackup.optJSONArray("study_progress")
                if (progressArray != null) {
                    for (i in 0 until progressArray.length()) {
                        val obj = progressArray.getJSONObject(i)
                        dao.insertProgressDay(StudyProgress(
                            dateString = obj.optString("dateString", ""),
                            countCompleted = obj.optInt("countCompleted", 1)
                        ))
                    }
                }

                // 9. API Keys
                val apiKeysArray = jsonBackup.optJSONArray("api_keys")
                if (apiKeysArray != null) {
                    for (i in 0 until apiKeysArray.length()) {
                        val obj = apiKeysArray.getJSONObject(i)
                        dao.insertApiKey(ApiKeyEntry(
                            key = obj.optString("key", ""),
                            label = obj.optString("label", ""),
                            isWorking = obj.optBoolean("isWorking", true),
                            addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                        ))
                    }
                }

                // Reload local settings state lists
                loadFolders()
                
                // Trigger reactive login session if username is set
                val savedName = prefs.getString("user_name", null)
                val savedEmail = prefs.getString("user_email", null)
                withContext(Dispatchers.Main) {
                    if (savedName != null && savedEmail != null) {
                        _currentUser.value = UserSession(savedName, savedEmail)
                    }
                    val loadedHistory = try { loadChatHistory() } catch (e: Exception) { emptyList() }
                    _chatHistory.value = loadedHistory
                    android.widget.Toast.makeText(context, "Full Backup ZIP imported and restored successfully!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error importing backup", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Restore failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

data class MindMapNode(
    val topic: String,
    val children: List<MindMapNode> = emptyList(),
    var x: Float = 0f,
    var y: Float = 0f,
    var isExpanded: Boolean = true
)

fun parseMindMapJson(jsonString: String): MindMapNode {
    var cleanJson = jsonString.trim()
    if (cleanJson.startsWith("```json")) {
        cleanJson = cleanJson.removePrefix("```json")
    }
    if (cleanJson.endsWith("```")) {
        cleanJson = cleanJson.removeSuffix("```")
    }
    cleanJson = cleanJson.trim()
    val jsonObject = org.json.JSONObject(cleanJson)
    return parseNode(jsonObject)
}

private fun parseNode(jsonObject: org.json.JSONObject): MindMapNode {
    val topic = jsonObject.optString("topic", "Branch")
    val childrenList = mutableListOf<MindMapNode>()
    val childrenJson = jsonObject.optJSONArray("children")
    if (childrenJson != null) {
        for (i in 0 until childrenJson.length()) {
            val childObj = childrenJson.getJSONObject(i)
            childrenList.add(parseNode(childObj))
        }
    }
    return MindMapNode(topic, childrenList)
}

data class UserSession(val name: String, val email: String)
