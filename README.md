# StudyMate Pro 2

[![Build Android APK](https://github.com/test3421699/StudyMateV2/actions/workflows/build-apk.yml/badge.svg)](https://github.com/test3421699/StudyMateV2/actions/workflows/build-apk.yml)

**StudyMate Pro 2** is a comprehensive, feature-rich Android academic assistant and study organizer built with Kotlin and Jetpack Compose. It combines modern study tools, local offline document and note management, intelligent quiz and flashcard generation, study schedule tracking, and interactive AI teacher modes.

---

## 🌟 Key Features

### 🤖 AI Teacher & Homework Helper
- **Custom Teacher Personalities**: Switch between Friendly Teacher, Strict Teacher, Board Exam Expert, and Fast Revision modes.
- **Comprehension Levels**: Adapt explanations from *"Explain Like I'm 10"* to *"Exam Level"* or *"Expert"*.
- **Multi-Provider API Support**:
  - **Google Gemini**: Built-in fallback API key pool or custom Gemini API keys.
  - **OpenRouter**: Configure custom OpenRouter API keys and any model ID (e.g., `google/gemini-2.0-flash-001`, `anthropic/claude-3.5-sonnet`, `deepseek/deepseek-r1`).
- **Rich Rendering**: Full KaTeX math equation rendering, Markdown formatting, and dynamically resizing Mermaid JS flowcharts/diagrams.
- **Image Input Support**: Attach diagrams, formulas, or textbook pages directly into chat requests.

### 📝 Notes & Document Storage
- **Folder & Chapter Organization**: Categorize study materials by Subject and Chapter.
- **Multiple Note Formats**: Create rich Markdown/LaTeX text notes or attach local PDF documents.
- **In-App PDF Viewer**: View attached study PDFs directly inside the app with full text-extraction capabilities for AI study generation.

### ⚡ AI Flashcards & Quiz Generator
- **Automatic Flashcard Generation**: Turn raw study notes or PDF texts into interactive study decks.
- **Adaptive Knowledge Tracking**: Track mastery for each card with review queues.
- **Interactive Quizzes**: Generate custom multiple-choice quizzes with detailed answer keys and explanations.

### 📅 Study Planner, Alarm Alerts & Streak Tracking
- **Interactive Timetable**: Schedule study sessions tied to subjects.
- **System Alarm Service**: Receive system notification alarms and reminders for upcoming study sessions.
- **Task Checklist & Daily Streaks**: Daily progress activity logs, current streak counters, and maximum streak tracking.

### 📖 Dictionary & Study Tools
- **In-App Dictionary**: Quick word lookups with instant definitions, phonetic spellings, synonyms, and example sentences.
- **Save to Study Notes**: Save dictionary entries directly into study note folders.

### 💾 Backup, Export & Import
- **Full Data Backup**: Export all notes, flashcards, quizzes, study events, and custom key settings to encrypted JSON.
- **Easy Restore**: Restore full application state seamlessly.

---

## 🛠️ Built With

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3
- **Local Database**: [Room Database](https://developer.android.com/training/data-storage/room) with KSP
- **Asynchronous Flow**: Kotlin Coroutines & StateFlow
- **Networking**: Retrofit & OkHttp (OpenRouter SSE Streaming & Gemini REST API)
- **Document & LaTeX Engine**: Android WebView with KaTeX, Mermaid.js, & Android-Java JS Interface height synchronization

---

## 📱 Build & Setup

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/test3421699/StudyMateV2.git
   cd StudyMateV2
   ```

2. **Build with Gradle**:
   ```bash
   gradle assembleDebug
   ```

3. **Install on Android Device**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🔒 License & Permissions

- Standard Android system permissions used for local file access, alarm scheduling, and internet access for AI API services.
