package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val fileType: String, // "PDF", "IMAGE", "DOC", "TEXT"
    val filePath: String?, // Local file path in context.filesDir
    val subject: String = "",
    val chapter: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "flashcard_sets")
data class FlashcardSet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "flashcard_items")
data class FlashcardItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val setId: Int,
    val question: String,
    val answer: String,
    val isKnown: Boolean = false
)

@Entity(tableName = "quiz_sets")
data class QuizSet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_questions")
data class QuizQuestion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quizSetId: Int,
    val question: String,
    val optionsString: String, // Concatenated with "||"
    val correctAnswer: String,
    val userAnswer: String? = null,
    val isCorrect: Boolean? = null
) {
    val optionsList: List<String>
        get() = optionsString.split("||").filter { it.isNotBlank() }
}

@Entity(tableName = "study_events")
data class StudyEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subject: String,
    val studyTimeMillis: Long, // Notification scheduled at (studyTimeMillis - 15 mins)
    val isCompleted: Boolean = false,
    val notified: Boolean = false
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_progress")
data class StudyProgress(
    @PrimaryKey val dateString: String, // Format "yyyy-MM-dd"
    val countCompleted: Int = 1
)

@Entity(tableName = "api_keys")
data class ApiKeyEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val key: String,
    val label: String,
    val isWorking: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
