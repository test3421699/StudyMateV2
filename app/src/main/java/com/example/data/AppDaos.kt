package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyMateDao {

    // --- Notes Entries ---
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntry): Long

    @Delete
    suspend fun deleteNote(note: NoteEntry)

    // --- Flashcards ---
    @Query("SELECT * FROM flashcard_sets ORDER BY createdAt DESC")
    fun getAllFlashcardSets(): Flow<List<FlashcardSet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcardSet(set: FlashcardSet): Long

    @Delete
    suspend fun deleteFlashcardSet(set: FlashcardSet)

    @Query("DELETE FROM flashcard_items WHERE setId = :setId")
    suspend fun deleteFlashcardItemsForSet(setId: Int)

    @Query("SELECT * FROM flashcard_items WHERE setId = :setId")
    fun getFlashcardsForSet(setId: Int): Flow<List<FlashcardItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcardItems(items: List<FlashcardItem>)

    @Query("UPDATE flashcard_items SET isKnown = :isKnown WHERE id = :cardId")
    suspend fun updateFlashcardKnowledge(cardId: Int, isKnown: Boolean)

    // --- Quizzes ---
    @Query("SELECT * FROM quiz_sets ORDER BY createdAt DESC")
    fun getAllQuizSets(): Flow<List<QuizSet>>

    @Query("SELECT * FROM quiz_questions WHERE quizSetId = :quizSetId")
    fun getQuizQuestionsForSet(quizSetId: Int): Flow<List<QuizQuestion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizSet(set: QuizSet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestions(questions: List<QuizQuestion>)

    @Query("UPDATE quiz_questions SET userAnswer = :userAnswer, isCorrect = :isCorrect WHERE id = :questionId")
    suspend fun submitQuizAnswer(questionId: Int, userAnswer: String, isCorrect: Boolean)

    @Query("UPDATE quiz_questions SET userAnswer = NULL, isCorrect = NULL WHERE quizSetId = :quizSetId")
    suspend fun resetQuizAnswers(quizSetId: Int)

    @Delete
    suspend fun deleteQuizSet(set: QuizSet)

    @Query("DELETE FROM quiz_questions WHERE quizSetId = :quizSetId")
    suspend fun deleteQuizQuestionsForSet(quizSetId: Int)

    // --- Study Planner ---
    @Query("SELECT * FROM study_events ORDER BY studyTimeMillis ASC")
    fun getAllStudyEvents(): Flow<List<StudyEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyEvent(event: StudyEvent): Long

    @Query("UPDATE study_events SET isCompleted = :isCompleted WHERE id = :eventId")
    suspend fun updateEventCompletion(eventId: Int, isCompleted: Boolean)

    @Query("UPDATE study_events SET notified = 1 WHERE id = :eventId")
    suspend fun markEventNotified(eventId: Int)

    @Delete
    suspend fun deleteStudyEvent(event: StudyEvent)

    // --- Tasks ---
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItem): Long

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun updateTaskCompletion(taskId: Int, isCompleted: Boolean)

    @Delete
    suspend fun deleteTask(task: TaskItem)

    // --- Study Progress Tracking & Streaks ---
    @Query("SELECT * FROM study_progress ORDER BY dateString ASC")
    fun getAllProgressDays(): Flow<List<StudyProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressDay(progress: StudyProgress)

    @Query("DELETE FROM study_progress WHERE dateString = :dateString")
    suspend fun removeProgressDay(dateString: String)

    // --- Custom API Keys Pool ---
    @Query("SELECT * FROM api_keys ORDER BY addedAt ASC")
    fun getAllApiKeys(): Flow<List<ApiKeyEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(key: ApiKeyEntry): Long

    @Query("UPDATE api_keys SET isWorking = :isWorking WHERE id = :id")
    suspend fun updateApiKeyStatus(id: Int, isWorking: Boolean)

    @Delete
    suspend fun deleteApiKey(key: ApiKeyEntry)

    // --- Backup & Restore Helper Queries ---
    @Query("SELECT * FROM notes")
    suspend fun getAllNotesDirect(): List<NoteEntry>

    @Query("SELECT * FROM flashcard_sets")
    suspend fun getAllFlashcardSetsDirect(): List<FlashcardSet>

    @Query("SELECT * FROM flashcard_items")
    suspend fun getAllFlashcardItemsDirect(): List<FlashcardItem>

    @Query("SELECT * FROM quiz_sets")
    suspend fun getAllQuizSetsDirect(): List<QuizSet>

    @Query("SELECT * FROM quiz_questions")
    suspend fun getAllQuizQuestionsDirect(): List<QuizQuestion>

    @Query("SELECT * FROM study_events")
    suspend fun getAllStudyEventsDirect(): List<StudyEvent>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksDirect(): List<TaskItem>

    @Query("SELECT * FROM study_progress")
    suspend fun getAllStudyProgressDirect(): List<StudyProgress>

    @Query("SELECT * FROM api_keys")
    suspend fun getAllApiKeysDirect(): List<ApiKeyEntry>

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM flashcard_sets")
    suspend fun clearFlashcardSets()

    @Query("DELETE FROM flashcard_items")
    suspend fun clearFlashcardItems()

    @Query("DELETE FROM quiz_sets")
    suspend fun clearQuizSets()

    @Query("DELETE FROM quiz_questions")
    suspend fun clearQuizQuestions()

    @Query("DELETE FROM study_events")
    suspend fun clearStudyEvents()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM study_progress")
    suspend fun clearStudyProgress()

    @Query("DELETE FROM api_keys")
    suspend fun clearApiKeys()
}
