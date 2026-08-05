package com.example.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import com.example.BuildConfig
import com.example.data.*
import java.io.File
import java.io.ByteArrayOutputStream
import android.util.Base64
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas

@Composable
fun StudyMateTopBar(viewModel: StudyMateViewModel, onLogoutClick: () -> Unit) {
    val progressList by viewModel.progressDays.collectAsStateWithLifecycle()
    val streak = remember(progressList) { viewModel.calculateCurrentStreak() }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    val initials = remember(currentUser) {
        val name = currentUser?.name ?: "Student"
        val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (parts.size >= 2) {
            "${parts[0].firstOrNull()?.uppercase() ?: ""}${parts[1].firstOrNull()?.uppercase() ?: ""}"
        } else {
            name.take(2).uppercase()
        }
    }

    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "StudyMate Pro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Hello, ${currentUser?.name ?: "Student"}!",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hot Streak badge
            Row(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("🔥", fontSize = 14.sp)
                Text(
                    text = "$streak",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            val isDarkModeOverrideState by viewModel.isDarkModeOverride.collectAsStateWithLifecycle()
            IconButton(
                onClick = {
                    val next = when (isDarkModeOverrideState) {
                        null -> true // Forced Dark
                        true -> false // Forced Light
                        false -> null // System Adaptive
                    }
                    viewModel.isDarkModeOverride.value = next
                },
                modifier = Modifier.testTag("app_theme_toggle_btn")
            ) {
                val icon = when (isDarkModeOverrideState) {
                    null -> Icons.Default.Brightness4
                    true -> Icons.Default.DarkMode
                    false -> Icons.Default.LightMode
                }
                val desc = when (isDarkModeOverrideState) {
                    null -> "System Adaptive Theme"
                    true -> "Forced Dark Theme"
                    false -> "Forced Light Theme"
                }
                Icon(
                    imageVector = icon,
                    contentDescription = desc,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            
            // User Avatar Initials with dropdown menu for logout
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                        .clickable { expanded = true }
                        .testTag("user_avatar_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Logout (${currentUser?.email ?: ""})") },
                        onClick = {
                            expanded = false
                            onLogoutClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = "Logout") },
                        modifier = Modifier.testTag("logout_menu_item")
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(viewModel: StudyMateViewModel) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("studymate_folders", android.content.Context.MODE_PRIVATE) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20), // Cosmic Midnight Violet
                        Color(0xFF07050B)  // Absolute black deep canvas
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.Center),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                width = 1.5.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎓", fontSize = 28.sp)
                }

                Text(
                    text = if (isLoginMode) "Welcome back to StudyMate Pro" else "Create StudyMate Account",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isLoginMode) "Log in to access notebooks, planner & helper features" else "Enter fields to register locally on current device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (!isLoginMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_name_field")
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_field")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("auth_password_field")
                )

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank() || (!isLoginMode && name.isBlank())) {
                            errorMessage = "All credentials must be fully filled!"
                            return@Button
                        }
                        if (isLoginMode) {
                            val registeredEmail = prefs.getString("user_registered_email", null)
                            val registeredPassword = prefs.getString("user_registered_p", null)
                            val registeredName = prefs.getString("user_name", null)

                            if (registeredEmail == null) {
                                errorMessage = "No account found! Please Switch to Register first."
                            } else if (registeredEmail.trim().lowercase(Locale.ROOT) != email.trim().lowercase(Locale.ROOT) || registeredPassword != password) {
                                errorMessage = "Wrong credentials email or password!"
                            } else {
                                viewModel.registerAndLogin(registeredName ?: "Student User", registeredEmail)
                            }
                        } else {
                            prefs.edit().apply {
                                putString("user_registered_email", email)
                                putString("user_registered_p", password)
                                putString("user_name", name)
                                putString("user_email", email)
                                apply()
                            }
                            viewModel.registerAndLogin(name, email)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("auth_submit_btn")
                ) {
                    Text(if (isLoginMode) "Log In" else "Sign Up")
                }

                TextButton(
                    onClick = {
                        isLoginMode = !isLoginMode
                        errorMessage = null
                    }
                ) {
                    Text(
                        text = if (isLoginMode) "Don't have an account? Register" else "Already have an account? Log In",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: StudyMateViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    var currentTab by remember { mutableStateOf("Home") }

    val activity = context as? androidx.activity.ComponentActivity
    val action = activity?.intent?.action

    LaunchedEffect(action) {
        if (action == "NAVIGATE_CHAT") {
            activity?.intent?.action = android.content.Intent.ACTION_MAIN
            currentTab = "AI Chat"
        }
    }

    if (currentUser == null) {
        AuthScreen(viewModel)
        return
    }

    Scaffold(
        topBar = {
            StudyMateTopBar(viewModel = viewModel, onLogoutClick = { viewModel.logout() })
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                val tabs = listOf(
                    Triple("Home", Icons.Default.Home, "Home"),
                    Triple("AI Chat", Icons.Default.Chat, "AI Helper"),
                    Triple("Notebook", Icons.Default.Book, "Notes"),
                    Triple("Planner", Icons.Default.DateRange, "Planner"),
                    Triple("Credentials", Icons.Default.VpnKey, "Keys")
                )
                tabs.forEach { (tab, icon, label) ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentTab = tab
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            when (currentTab) {
                "Home" -> DashboardTab(viewModel, onNavigateToTab = { currentTab = it })
                "AI Chat" -> HomeworkHelperTab(viewModel)
                "Notebook" -> NotebooksTab(viewModel)
                "Planner" -> PlannerTab(viewModel)
                "Credentials" -> SettingsCredentialsTab(viewModel)
            }
        }
    }
}

// ==================== DASHBOARD TAB ====================
@Composable
fun DashboardTab(viewModel: StudyMateViewModel, onNavigateToTab: (String) -> Unit) {
    val tasksList by viewModel.tasks.collectAsStateWithLifecycle()
    val eventsList by viewModel.studyEvents.collectAsStateWithLifecycle()
    val progressList by viewModel.progressDays.collectAsStateWithLifecycle()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }

    val streak = remember(progressList) { viewModel.calculateCurrentStreak() }
    val maxStreak = remember(progressList) { viewModel.calculateMaxStreak() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scroll_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. AI Homework Helper Bar
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToTab("AI Chat") },
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("✨", fontSize = 20.sp)
                    Text(
                        text = "Ask AI or upload a photo...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷", fontSize = 14.sp)
                    }
                }
            }
        }

        // 2. Quick Stats Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Planner
                val upcomingEvent = eventsList.filter { !it.isCompleted }.minByOrNull { it.studyTimeMillis }
                val plannerTime = if (upcomingEvent != null) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    sdf.format(Date(upcomingEvent.studyTimeMillis))
                } else {
                    "--:--"
                }
                val plannerTitle = upcomingEvent?.subject ?: "No upcoming sessions"

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clickable { onNavigateToTab("Planner") },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📅", fontSize = 24.sp)
                            Text(
                                text = "PLANNER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        }
                        Column {
                            Text(
                                  text = plannerTime,
                                  fontSize = 20.sp,
                                  fontWeight = FontWeight.Bold,
                                  color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                  text = plannerTitle,
                                  style = MaterialTheme.typography.bodySmall,
                                  fontWeight = FontWeight.Medium,
                                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Card 2: Library
                val notesCount = viewModel.notes.collectAsStateWithLifecycle().value.count { it.fileType != "DICTIONARY" }
                val flashCount = viewModel.flashcardSets.collectAsStateWithLifecycle().value.size
                val quizCount = viewModel.quizSets.collectAsStateWithLifecycle().value.size
                val totalLibraryCount = notesCount + flashCount + quizCount

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clickable { onNavigateToTab("Notebook") },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🗂️", fontSize = 24.sp)
                            Text(
                                text = "LIBRARY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        }
                        Column {
                            Text(
                                  text = "$totalLibraryCount",
                                  fontSize = 22.sp,
                                  fontWeight = FontWeight.Bold,
                                  color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                  text = "Saved Docs",
                                  style = MaterialTheme.typography.bodySmall,
                                  fontWeight = FontWeight.Medium,
                                  color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // Custom Inline Progress Calendar View
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Daily Completion Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    ProgressCalendarView(progressList)
                }
            }
        }

        // Today's Study Schedule Checklist
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pending Alarms & Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${eventsList.filter { !it.isCompleted }.size} scheduled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        val activeEvents = eventsList.filter { !it.isCompleted }
        if (activeEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No study sessions scheduled today. Tap 'Planner' to design your schedules!",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(activeEvents) { event ->
                val formatter = remember { SimpleDateFormat("h:mm a, dd MMM", Locale.getDefault()) }
                val timeStr = formatter.format(Date(event.studyTimeMillis))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.subject,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Alarm, contentDescription = "Alarm Icon", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Alert pre-15min: Start study at $timeStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.completeStudyEvent(event.id, event.subject) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("complete_event_btn_${event.id}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Daily Tasks Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header of Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Daily Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showAddTaskDialog = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("add_task_fab")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "Add task",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        val pendingCount = tasksList.filter { !it.isCompleted }.size
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$pendingCount Pending",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Task checklist items
                    if (tasksList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tasks recorded today. Tap + to add!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tasksList.forEach { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                        )
                                        .then(
                                            if (!task.isCompleted) Modifier.border(
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                shape = RoundedCornerShape(16.dp)
                                            ) else Modifier
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = task.isCompleted,
                                            onCheckedChange = { viewModel.completeTaskItem(task.id, it) },
                                            modifier = Modifier.testTag("task_checkbox_${task.id}")
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (task.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.onSurface,
                                            textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough
                                            else null,
                                            modifier = Modifier.testTag("task_text_${task.id}")
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeTaskItem(task) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("delete_task_btn_${task.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete task",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Progress Dots
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WEEKLY PROGRESS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 7) {
                                val isCompletedDay = if (progressList.isNotEmpty()) {
                                    val index = i % progressList.size
                                    progressList[index].countCompleted > 0
                                } else {
                                    i < 3 // Default filled dots pattern
                                }

                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (isCompletedDay) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Quick Tools Carousel
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quick Study Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .clickable { onNavigateToTab("Notebook") }
                                .height(44.dp),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🃏", fontSize = 16.sp)
                                Text(
                                    text = "Flashcard Maker",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .clickable { onNavigateToTab("Notebook") }
                                .height(44.dp),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📝", fontSize = 16.sp)
                                Text(
                                    text = "Quiz Generator",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("Create Checklist Task") },
            text = {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("Task details") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_task_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            viewModel.createTaskItem(newTaskTitle)
                            newTaskTitle = ""
                            showAddTaskDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_task_btn")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Custom Grid Progress Calendar
@Composable
fun ProgressCalendarView(progressList: List<StudyProgress>) {
    val completedDays = remember(progressList) { progressList.map { it.dateString }.toSet() }

    var currentCalendarState by remember { mutableStateOf(Calendar.getInstance()) }

    val daysInMonth = remember(currentCalendarState) {
        currentCalendarState.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val monthTitle = remember(currentCalendarState) {
        monthFormat.format(currentCalendarState.time)
    }

    val year = remember(currentCalendarState) {
        currentCalendarState.get(Calendar.YEAR)
    }
    val month = remember(currentCalendarState) {
        currentCalendarState.get(Calendar.MONTH) + 1 // 1-indexed for string serialization
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val prevCal = (currentCalendarState.clone() as Calendar).apply {
                        add(Calendar.MONTH, -1)
                    }
                    currentCalendarState = prevCal
                }
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
            }

            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = {
                    val nextCal = (currentCalendarState.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                    }
                    currentCalendarState = nextCal
                }
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Weekdays headers
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val daysOfWeekHeader = listOf("M", "T", "W", "T", "F", "S", "S")
            daysOfWeekHeader.forEach { d ->
                Text(
                    text = d,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Dynamic 35/42 days grid based on rows count
        val firstDayOfWeekOffset = (tempFirstDayOffset(currentCalendarState) + 5) % 7 // Monday base
        val totalCells = firstDayOfWeekOffset + daysInMonth
        val rowsCount = if (totalCells % 7 == 0) totalCells / 7 else (totalCells / 7) + 1

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (r in 0 until rowsCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        ) {
                            if (cellIndex >= firstDayOfWeekOffset && cellIndex < totalCells) {
                                val dayNum = cellIndex - firstDayOfWeekOffset + 1
                                val dayStr = String.format(Locale.US, "%d-%02d-%02d", year, month, dayNum)
                                val isCompleted = completedDays.contains(dayStr)

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCompleted) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCompleted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tempFirstDayOffset(state: Calendar): Int {
    val tempCal = (state.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return tempCal.get(Calendar.DAY_OF_WEEK)
}


// ==================== AI HOMEWORK HELPER TAB ====================
@Composable
fun HomeworkHelperTab(viewModel: StudyMateViewModel) {
    val chatList by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isAILoading by viewModel.isAILoading.collectAsStateWithLifecycle()
    val apiError by viewModel.apiErrorFeedback.collectAsStateWithLifecycle()

    var questionInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    selectedImageBitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.e("StudyMateVM", "Error loading image bytes", e)
            }
        }
    }

    val selectedTeacher by viewModel.selectedTeacherPersonality.collectAsStateWithLifecycle()
    val selectedLevel by viewModel.selectedExplanationLevel.collectAsStateWithLifecycle()

    var personaExpanded by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = "AI Teacher Modes", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Teacher Modes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                TextButton(
                    onClick = { viewModel.clearHomeworkChat() },
                    modifier = Modifier.testTag("clear_chat_btn")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Chat", fontSize = 11.sp)
                }
            }
        }

        // Side-by-side dropdown selectors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dropdown 1: Persona
            Box(modifier = Modifier.weight(1f)) {
                Card(
                    onClick = { personaExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Teacher Style",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = selectedTeacher,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Dropdown menu",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(
                    expanded = personaExpanded,
                    onDismissRequest = { personaExpanded = false }
                ) {
                    listOf("Friendly Teacher", "Strict Teacher", "Board Exam Expert", "Fast Revision Teacher").forEach { persona ->
                        DropdownMenuItem(
                            text = { Text(persona, fontSize = 13.sp) },
                            onClick = {
                                viewModel.selectedTeacherPersonality.value = persona
                                personaExpanded = false
                            }
                        )
                    }
                }
            }

            // Dropdown 2: Comprehension Level
            Box(modifier = Modifier.weight(1f)) {
                Card(
                    onClick = { levelExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Comprehension Level",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = selectedLevel,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Dropdown menu",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(
                    expanded = levelExpanded,
                    onDismissRequest = { levelExpanded = false }
                ) {
                    listOf("Explain Like I'm 10", "Beginner", "Intermediate", "Exam Level", "Expert").forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level, fontSize = 13.sp) },
                            onClick = {
                                viewModel.selectedExplanationLevel.value = level
                                levelExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Re-explain button
        val hasUserMessages = chatList.any { it.sender == "User" }
        if (hasUserMessages) {
            val lastUserMsg = chatList.lastOrNull { it.sender == "User" }
            Button(
                onClick = {
                    if (lastUserMsg != null) {
                        viewModel.askTeacherAgain(lastUserMsg.text, lastUserMsg.localImageUri)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Re-explain", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("🔄 Teach / Re-Explain in selected style", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (apiError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = "API Alert: $apiError",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Chat conversation bubble stream
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            reverseLayout = false
        ) {
            items(chatList) { msg ->
                val isAI = msg.sender == "Gemini"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAI) Arrangement.Start else Arrangement.End
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .testTag("chat_bubble_${msg.id}"),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isAI) 2.dp else 16.dp,
                            bottomEnd = if (isAI) 16.dp else 2.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAI) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // If user posted image, display preview
                            if (msg.localImageUri != null) {
                                AsyncImage(
                                    model = msg.localImageUri,
                                    contentDescription = "Uploaded task image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            LatexText(
                                text = msg.text,
                                fontSizeSp = 14,
                                color = if (isAI) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            if (isAILoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Awaiting AI solution...", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Cancel",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.cancelActiveAIGeneration() }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Image upload preview strip at the bottom
        if (selectedImageUri != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Image to upload",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Question Photo Attached", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        selectedImageUri = null
                        selectedImageBitmap = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Entry panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.testTag("upload_image_btn")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach question photo", tint = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = questionInput,
                onValueChange = { questionInput = it },
                placeholder = { Text("Ask your AI Teacher...", fontSize = 14.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            IconButton(
                onClick = {
                    if (questionInput.isNotBlank() || selectedImageBitmap != null) {
                        var copiedPath: String? = null
                        selectedImageUri?.let { uri ->
                            copiedPath = viewModel.copyUriToLocalStorage(uri, "chat_image.jpg", "IMAGE")
                        }
                        viewModel.askHomeworkHelper(questionInput, selectedImageBitmap, copiedPath)
                        questionInput = ""
                        selectedImageUri = null
                        selectedImageBitmap = null
                    }
                },
                modifier = Modifier.testTag("send_chat_btn"),
                enabled = !isAILoading
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send prompt", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}


// ==================== NOTEBOOK / IN-APP STORAGE TAB ====================
sealed interface ActiveViewer {
    data object None : ActiveViewer
    data class TextNote(val id: Int, val title: String, val content: String, val path: String) : ActiveViewer
    data class ImageNote(val title: String, val path: String) : ActiveViewer
    data class PdfNote(val title: String, val path: String) : ActiveViewer
    data class MindMapNote(val title: String, val content: String) : ActiveViewer
}

@Composable
fun NotebooksTab(viewModel: StudyMateViewModel) {
    val notesList by viewModel.notes.collectAsStateWithLifecycle()
    val flashSets by viewModel.flashcardSets.collectAsStateWithLifecycle()
    val quizSets by viewModel.quizSets.collectAsStateWithLifecycle()

    var activeViewMode by remember { mutableStateOf<ActiveViewer>(ActiveViewer.None) }
    val activeSubTab by viewModel.selectedSubTab.collectAsStateWithLifecycle()

    if (activeViewMode != ActiveViewer.None) {
        DocOpenerInbuiltView(activeViewMode, viewModel) { activeViewMode = ActiveViewer.None }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val tabs = listOf(
            "Files" to "Notebook",
            "Flashcards" to "AI Flashcards",
            "Quizzes" to "AI Quizzes",
            "Summarizer" to "AI Summarizer",
            "Mindmap" to "AI Mind Map",
            "Dictionary" to "Dictionary"
        )
        val selectedIdx = tabs.indexOfFirst { it.first == activeSubTab }.coerceAtLeast(0)

        ScrollableTabRow(
            selectedTabIndex = selectedIdx,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { idx, pair ->
                Tab(
                    selected = selectedIdx == idx,
                    onClick = { viewModel.selectedSubTab.value = pair.first },
                    text = { Text(pair.second, fontWeight = FontWeight.Medium, fontSize = 12.sp) }
                )
            }
        }

        LaunchedEffect(Unit) {
            viewModel.searchQuery.value = ""
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (activeSubTab) {
            "Files" -> NotesFolderSection(viewModel) { mode -> activeViewMode = mode }
            "Flashcards" -> FlashcardsDeckSection(viewModel)
            "Quizzes" -> QuizzesPlaySection(viewModel)
            "Summarizer" -> SummarizerSection(viewModel)
            "Mindmap" -> MindMapSection(viewModel)
            "Dictionary" -> DictionarySection(viewModel)
        }
    }
}

@Composable
fun NotesFolderSection(viewModel: StudyMateViewModel, onOpenDoc: (ActiveViewer) -> Unit) {
    val notesList by viewModel.notes.collectAsStateWithLifecycle()
    val subjectsList by viewModel.subjectsList.collectAsStateWithLifecycle()
    val chaptersMap by viewModel.chaptersMap.collectAsStateWithLifecycle()

    var currentSubject by remember { mutableStateOf<String?>(null) }
    var currentChapter by remember { mutableStateOf<String?>(null) }

    var showCreateTextDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddChapterDialog by remember { mutableStateOf(false) }

    var newSubjectName by remember { mutableStateOf("") }
    var newChapterName by remember { mutableStateOf("") }
    var textNoteTitle by remember { mutableStateOf("") }
    var textNoteContent by remember { mutableStateOf("") }

    var noteToMove by remember { mutableStateOf<NoteEntry?>(null) }
    var showMoveFileDialog by remember { mutableStateOf(false) }
    var selectedMoveSubject by remember { mutableStateOf("") }
    var selectedMoveChapter by remember { mutableStateOf("") }
    var showDownloadThemeDialog by remember { mutableStateOf(false) }
    var noteToDownload by remember { mutableStateOf<NoteEntry?>(null) }

    // Rename states
    var subjectToRename by remember { mutableStateOf<String?>(null) }
    var chapterToRename by remember { mutableStateOf<String?>(null) }
    var noteToRename by remember { mutableStateOf<NoteEntry?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Register generic file upload document picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Document"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            val ext = fileName.substringAfterLast('.', "dat")
            val localPath = viewModel.copyUriToLocalStorage(uri, fileName, if (ext.uppercase() == "PDF") "PDF" else "DOC")
            if (localPath != null) {
                viewModel.createDocumentNote(
                    title = fileName,
                    extension = ext,
                    localPath = localPath,
                    subject = currentSubject ?: "",
                    chapter = currentChapter ?: ""
                )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Add Folder Buttons
                if (currentSubject == null) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddSubjectDialog = true },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        text = { Text("New Subject") },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.testTag("add_subject_folder_fab")
                    )
                } else if (currentChapter == null) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddChapterDialog = true },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        text = { Text("New Chapter") },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.testTag("add_chapter_folder_fab")
                    )
                }

                FloatingActionButton(
                    onClick = { showCreateTextDialog = true },
                    modifier = Modifier.testTag("write_note_fab"),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = "Write Rich Text Note")
                }
                FloatingActionButton(
                    onClick = { documentPickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain")) },
                    modifier = Modifier.testTag("upload_file_fab")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Import PDF/Doc Inline")
                }
            }
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingVals.calculateBottomPadding())
        ) {
            // Folders Breadcrumbs / Header Path Navigation
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentSubject != null) {
                        IconButton(
                            onClick = {
                                if (currentChapter != null) {
                                    currentChapter = null
                                } else {
                                    currentSubject = null
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    TextButton(
                        onClick = {
                            currentSubject = null
                            currentChapter = null
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Root Storage",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (currentSubject == null) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    if (currentSubject != null) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        TextButton(
                            onClick = { currentChapter = null },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentSubject!!,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (currentChapter == null) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    if (currentChapter != null) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        TextButton(
                            onClick = {},
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentChapter!!,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Filtering items
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val currentLevelNotes = remember(notesList, currentSubject, currentChapter, searchQuery) {
                val nonDict = notesList.filter { it.fileType != "DICTIONARY" }
                if (searchQuery.isNotBlank()) {
                    nonDict.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.content.contains(searchQuery, ignoreCase = true)
                    }
                } else {
                    nonDict.filter {
                        val subMatch = (it.subject == (currentSubject ?: ""))
                        val chapMatch = (it.chapter == (currentChapter ?: ""))
                        subMatch && chapMatch
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // SUBJECT FOLDERS DISPLAY (Only at Root)
                if (currentSubject == null && searchQuery.isBlank()) {
                    if (subjectsList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Subject Folders",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        itemsIndexed(subjectsList) { index, subject ->
                            val fileCount = remember(notesList, subject) {
                                notesList.count { it.subject == subject }
                            }
                            StaggeredEntrance(index = index) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentSubject = subject
                                            currentChapter = null
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = "Subject Folder",
                                            tint = Color(0xFFFFA000), // Nice warm orange folder color
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = subject,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (fileCount == 1) "1 file" else "$fileCount files",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.testTag("subject_menu_btn_$subject")
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "Subject Options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename Folder") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    subjectToRename = subject
                                                    renameInputText = subject
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.deleteSubject(subject)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }

                // CHAPTER FOLDERS DISPLAY (Only inside a Subject, showing chapters)
                if (currentSubject != null && currentChapter == null && searchQuery.isBlank()) {
                    val subjectChapters = chaptersMap[currentSubject!!] ?: emptyList()
                    if (subjectChapters.isNotEmpty()) {
                        item {
                            Text(
                                text = "Chapters / Subfolders",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        itemsIndexed(subjectChapters) { index, chapter ->
                            val chapFileCount = remember(notesList, currentSubject, chapter) {
                                notesList.count { it.subject == currentSubject && it.chapter == chapter }
                            }
                            StaggeredEntrance(index = index) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentChapter = chapter
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.FolderOpen,
                                            contentDescription = "Chapter Subfolder",
                                            tint = Color(0xFF00ACC1), // Cyan/teal folder
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = chapter,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = if (chapFileCount == 1) "1 file" else "$chapFileCount files",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.testTag("chapter_menu_btn_$chapter")
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "Chapter Options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename Subfolder") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    chapterToRename = chapter
                                                    renameInputText = chapter
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Subfolder", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.deleteChapter(currentSubject!!, chapter)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }

                // FILE LISTINGS SECTION
                if (currentLevelNotes.isNotEmpty()) {
                    item {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Search Results (${currentLevelNotes.size})" else if (currentSubject == null) "Direct Files & Drafts" else "Study Materials",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    itemsIndexed(currentLevelNotes) { index, note ->
                        val fileTypeIcon = when (note.fileType) {
                            "PDF" -> Icons.Default.PictureAsPdf
                            "IMAGE" -> Icons.Default.Image
                            "MINDMAP" -> Icons.Default.AccountTree
                            else -> Icons.Default.Description
                        }
                        val iconColor = when (note.fileType) {
                            "PDF" -> Color(0xFFE91E63)
                            "IMAGE" -> Color(0xFF2196F3)
                            "MINDMAP" -> Color(0xFF8E75FF)
                            else -> Color(0xFF4CAF50)
                        }

                        StaggeredEntrance(index = index) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when (note.fileType) {
                                            "TEXT" -> note.filePath?.let { path ->
                                                val content = File(path).readText()
                                                onOpenDoc(ActiveViewer.TextNote(note.id, note.title, content, path))
                                            }
                                            "PDF" -> note.filePath?.let { onOpenDoc(ActiveViewer.PdfNote(note.title, it)) }
                                            "IMAGE" -> note.filePath?.let { onOpenDoc(ActiveViewer.ImageNote(note.title, it)) }
                                            "MINDMAP" -> onOpenDoc(ActiveViewer.MindMapNote(note.title, note.content))
                                        }
                                    }
                                    .testTag("note_entry_card_${note.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(fileTypeIcon, contentDescription = "Type", tint = iconColor, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(note.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "Created: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(note.createdAt))}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row {
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.testTag("note_menu_btn_${note.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "File Options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename File") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    noteToRename = note
                                                    renameInputText = note.title
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Download / Export") },
                                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF4CAF50)) },
                                                onClick = {
                                                    showMenu = false
                                                    noteToDownload = note
                                                    showDownloadThemeDialog = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Move to Folder") },
                                                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                onClick = {
                                                    showMenu = false
                                                    noteToMove = note
                                                    selectedMoveSubject = note.subject
                                                    selectedMoveChapter = note.chapter
                                                    showMoveFileDialog = true
                                                }
                                            )
                                            
                                            if (note.fileType == "PDF") {
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                                DropdownMenuItem(
                                                    text = { Text("Use for Summary (AI)") },
                                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFE91E63)) },
                                                    onClick = {
                                                        showMenu = false
                                                        viewModel.summarizerActiveMode.value = "SUMMARIZER"
                                                        viewModel.selectedSubTab.value = "Summarizer"
                                                        viewModel.attachedFileForGeneration.value = note
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Use for Formula Sheet (AI)") },
                                                    leadingIcon = { Icon(Icons.Default.Functions, contentDescription = null, tint = Color(0xFFFFA000)) },
                                                    onClick = {
                                                        showMenu = false
                                                        viewModel.summarizerActiveMode.value = "FORMULA"
                                                        viewModel.selectedSubTab.value = "Summarizer"
                                                        viewModel.attachedFileForGeneration.value = note
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Use for Mind Map (AI)") },
                                                    leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF8E75FF)) },
                                                    onClick = {
                                                        showMenu = false
                                                        viewModel.selectedSubTab.value = "Mindmap"
                                                        viewModel.attachedFileForGeneration.value = note
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Use for Quiz (AI)") },
                                                    leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null, tint = Color(0xFF2196F3)) },
                                                    onClick = {
                                                        showMenu = false
                                                        viewModel.selectedSubTab.value = "Quizzes"
                                                        viewModel.attachedFileForGeneration.value = note
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Use for Flashcards (AI)") },
                                                    leadingIcon = { Icon(Icons.Default.Style, contentDescription = null, tint = Color(0xFF4CAF50)) },
                                                    onClick = {
                                                        showMenu = false
                                                        viewModel.selectedSubTab.value = "Flashcards"
                                                        viewModel.attachedFileForGeneration.value = note
                                                    }
                                                )
                                            }
                                            
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            DropdownMenuItem(
                                                text = { Text("Delete File", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.deleteNoteEntry(note)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }

                // EMPTY DIRECTORIES HANDLING
                if (currentLevelNotes.isEmpty()) {
                    val isSubjectListEmpty = (currentSubject == null && subjectsList.isEmpty())
                    val isChapterListEmpty = (currentSubject != null && currentChapter == null && (chaptersMap[currentSubject!!] ?: emptyList()).isEmpty())
                    val isOnlyFilesCurrent = (currentSubject != null && currentChapter != null) || (currentSubject != null) || (currentSubject == null)

                    if (isSubjectListEmpty || isChapterListEmpty || isOnlyFilesCurrent) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = "Empty",
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No files inside this folder",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Use the write or upload buttons, or create subfolders to start organizing subjects.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Move File Dialog
    if (showMoveFileDialog && noteToMove != null) {
        var isSubjectDropdownExpanded by remember { mutableStateOf(false) }
        var isChapterDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showMoveFileDialog = false },
            title = { Text("Move File Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Move '${noteToMove!!.title}' to a different subject folder / chapter subfolder:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Subject select
                    Column {
                        Text("Select Subject Folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isSubjectDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (selectedMoveSubject.isBlank()) "Root Storage / General" else selectedMoveSubject)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = isSubjectDropdownExpanded,
                                onDismissRequest = { isSubjectDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Root Storage (No Subject)") },
                                    onClick = {
                                        selectedMoveSubject = ""
                                        selectedMoveChapter = ""
                                        isSubjectDropdownExpanded = false
                                    }
                                )
                                subjectsList.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text(sub) },
                                        onClick = {
                                            selectedMoveSubject = sub
                                            selectedMoveChapter = ""
                                            isSubjectDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Chapter select (only showing if valid subject is selected)
                    if (selectedMoveSubject.isNotBlank()) {
                        val subChaps = chaptersMap[selectedMoveSubject] ?: emptyList()
                        Column {
                            Text("Select Chapter Subfolder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { isChapterDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (selectedMoveChapter.isBlank()) "Subject Root (No Chapter)" else selectedMoveChapter)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = isChapterDropdownExpanded,
                                    onDismissRequest = { isChapterDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Subject Root (No Chapter)") },
                                        onClick = {
                                            selectedMoveChapter = ""
                                            isChapterDropdownExpanded = false
                                        }
                                    )
                                    subChaps.forEach { chap ->
                                        DropdownMenuItem(
                                            text = { Text(chap) },
                                            onClick = {
                                                selectedMoveChapter = chap
                                                isChapterDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNoteFolder(noteToMove!!, selectedMoveSubject, selectedMoveChapter)
                        showMoveFileDialog = false
                        noteToMove = null
                    }
                ) {
                    Text("Apply Path")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveFileDialog = false; noteToMove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export PDF Theme Dialog
    if (showDownloadThemeDialog && noteToDownload != null) {
        AlertDialog(
            onDismissRequest = { showDownloadThemeDialog = false; noteToDownload = null },
            title = { Text("Choose PDF Export Theme", fontWeight = FontWeight.Bold) },
            text = {
                Text("Select whether you want to save this note in light mode (classic black text on white background) or eye-safe dark mode (light text on dark background).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.downloadNoteFile(context, noteToDownload!!, isDarkTheme = true)
                        showDownloadThemeDialog = false
                        noteToDownload = null
                    },
                    modifier = Modifier.testTag("pdf_download_dark_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Dark Theme")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.downloadNoteFile(context, noteToDownload!!, isDarkTheme = false)
                        showDownloadThemeDialog = false
                        noteToDownload = null
                    },
                    modifier = Modifier.testTag("pdf_download_light_btn")
                ) {
                    Text("Light Theme")
                }
            }
        )
    }

    // Rename Dialog (Subject, Chapter, Note)
    if (subjectToRename != null || chapterToRename != null || noteToRename != null) {
        val titleText = when {
            subjectToRename != null -> "Rename Subject Folder"
            chapterToRename != null -> "Rename Chapter Subfolder"
            else -> "Rename File"
        }
        AlertDialog(
            onDismissRequest = {
                subjectToRename = null
                chapterToRename = null
                noteToRename = null
                renameInputText = ""
            },
            title = { Text(titleText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = renameInputText.trim()
                        if (input.isNotBlank()) {
                            when {
                                subjectToRename != null -> viewModel.renameSubject(subjectToRename!!, input)
                                chapterToRename != null -> viewModel.renameChapter(currentSubject!!, chapterToRename!!, input)
                                noteToRename != null -> viewModel.renameNote(noteToRename!!, input)
                            }
                        }
                        subjectToRename = null
                        chapterToRename = null
                        noteToRename = null
                        renameInputText = ""
                    },
                    modifier = Modifier.testTag("rename_confirm_btn")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        subjectToRename = null
                        chapterToRename = null
                        noteToRename = null
                        renameInputText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Subject Dialog
    if (showAddSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = { Text("Create New Subject Folder") },
            text = {
                OutlinedTextField(
                    value = newSubjectName,
                    onValueChange = { newSubjectName = it },
                    label = { Text("Subject Name (e.g. Science, Mathematics)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_subject_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSubjectName.isNotBlank()) {
                            viewModel.addSubject(newSubjectName)
                            newSubjectName = ""
                            showAddSubjectDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubjectDialog = false; newSubjectName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Chapter Dialog
    if (showAddChapterDialog && currentSubject != null) {
        AlertDialog(
            onDismissRequest = { showAddChapterDialog = false },
            title = { Text("New Chapter Subfolder") },
            text = {
                Column {
                    Text("Adding subfolder inside chapter scope of: $currentSubject", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newChapterName,
                        onValueChange = { newChapterName = it },
                        label = { Text("Chapter Subfolder (e.g. Chapter 1 - Cells)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_chapter_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newChapterName.isNotBlank()) {
                            viewModel.addChapter(currentSubject!!, newChapterName)
                            newChapterName = ""
                            showAddChapterDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapterDialog = false; newChapterName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Write Rich Text note Dialog
    if (showCreateTextDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTextDialog = false },
            title = { Text("Write In-App Rich Text Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val locationLabel = when {
                        currentSubject != null && currentChapter != null -> "In: $currentSubject > $currentChapter"
                        currentSubject != null -> "In Subject: $currentSubject"
                        else -> "In Root Storage (General)"
                    }
                    Text(text = locationLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = textNoteTitle,
                        onValueChange = { textNoteTitle = it },
                        label = { Text("Note Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rich_note_title")
                    )
                    OutlinedTextField(
                        value = textNoteContent,
                        onValueChange = { textNoteContent = it },
                        label = { Text("Draft Content") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("rich_note_body")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textNoteTitle.isNotBlank() && textNoteContent.isNotBlank()) {
                            viewModel.createTextNote(
                                title = textNoteTitle,
                                content = textNoteContent,
                                subject = currentSubject ?: "",
                                chapter = currentChapter ?: ""
                            )
                            textNoteTitle = ""
                            textNoteContent = ""
                            showCreateTextDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_rich_note_btn")
                ) {
                    Text("Save Draft")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class EditableDiagram(
    val id: Int,
    val originalBlock: String,
    val isBase64: Boolean,
    val contentCode: String
)

fun parseDiagramsFromContent(content: String): List<EditableDiagram> {
    val list = mutableListOf<EditableDiagram>()
    var idCounter = 1
    
    // Find all markdown code blocks: ```lang\ncontent\n```
    val regex = Regex("```([a-zA-Z0-9_\\[\\]\\-]*)\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
    val matches = regex.findAll(content)
    
    for (match in matches) {
        val originalBlock = match.value
        val lang = match.groupValues[1].trim()
        val blockBody = match.groupValues[2].trim()
        
        val isBase64 = lang.startsWith("[Base64]") || 
                       lang.lowercase().contains("base64") || 
                       blockBody.startsWith("[Base64]")
                       
        val isHtmlDiagram = lang.lowercase().contains("html") || 
                            lang.lowercase().contains("xml") || 
                            lang.lowercase().contains("svg") || 
                            lang.lowercase().contains("diagram") || 
                            blockBody.contains("[diagram]")
                            
        if (isBase64) {
            val cleanCode = blockBody.removePrefix("[Base64]").trim()
            list.add(EditableDiagram(
                id = idCounter++,
                originalBlock = originalBlock,
                isBase64 = true,
                contentCode = cleanCode
            ))
        } else if (isHtmlDiagram) {
            val cleanCode = blockBody.removePrefix("[diagram]").trim()
            list.add(EditableDiagram(
                id = idCounter++,
                originalBlock = originalBlock,
                isBase64 = false,
                contentCode = cleanCode
            ))
        }
    }
    return list
}

fun replaceDiagramInContent(originalContent: String, diagram: EditableDiagram, newCode: String): String {
    val newBlock = if (diagram.isBase64) {
        "```[Base64]\n[Base64]$newCode\n```"
    } else {
        "```html\n[diagram]\n$newCode\n```"
    }
    return originalContent.replace(diagram.originalBlock, newBlock)
}

// In-App Doc & PDF render page visualizer
@Composable
fun DocOpenerInbuiltView(mode: ActiveViewer, viewModel: StudyMateViewModel, onClose: () -> Unit) {
    var currentNoteContent by remember(mode) {
        mutableStateOf(if (mode is ActiveViewer.TextNote) mode.content else "")
    }
    var isPdfInverted by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("close_viewer_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            val title = when (mode) {
                is ActiveViewer.TextNote -> mode.title
                is ActiveViewer.ImageNote -> mode.title
                is ActiveViewer.PdfNote -> mode.title
                is ActiveViewer.MindMapNote -> mode.title
                else -> "Document Opener"
            }
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (mode is ActiveViewer.PdfNote) {
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { isPdfInverted = !isPdfInverted },
                        modifier = Modifier.testTag("pdf_invert_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isPdfInverted) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Inverted Dark Mode",
                            tint = if (isPdfInverted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        var aiMenuExpanded by remember { mutableStateOf(false) }
                        Button(
                            onClick = { aiMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("pdf_ai_tools_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Study Tools", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                    DropdownMenu(
                        expanded = aiMenuExpanded,
                        onDismissRequest = { aiMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Use for Summary (AI)") },
                            leadingIcon = { Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                aiMenuExpanded = false
                                val noteEntry = NoteEntry(
                                    title = mode.title,
                                    content = "",
                                    fileType = "PDF",
                                    filePath = mode.path
                                )
                                viewModel.summarizerActiveMode.value = "SUMMARIZER"
                                viewModel.attachedFileForGeneration.value = noteEntry
                                viewModel.selectedSubTab.value = "Summarizer"
                                onClose()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Use for Formula Sheet (AI)") },
                            leadingIcon = { Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                aiMenuExpanded = false
                                val noteEntry = NoteEntry(
                                    title = mode.title,
                                    content = "",
                                    fileType = "PDF",
                                    filePath = mode.path
                                )
                                viewModel.summarizerActiveMode.value = "FORMULA"
                                viewModel.attachedFileForGeneration.value = noteEntry
                                viewModel.selectedSubTab.value = "Summarizer"
                                onClose()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Use for Mind Map (AI)") },
                            leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                aiMenuExpanded = false
                                val noteEntry = NoteEntry(
                                    title = mode.title,
                                    content = "",
                                    fileType = "PDF",
                                    filePath = mode.path
                                )
                                viewModel.attachedFileForGeneration.value = noteEntry
                                viewModel.selectedSubTab.value = "Mindmap"
                                onClose()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Use for Quiz (AI)") },
                            leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                aiMenuExpanded = false
                                val noteEntry = NoteEntry(
                                    title = mode.title,
                                    content = "",
                                    fileType = "PDF",
                                    filePath = mode.path
                                )
                                viewModel.attachedFileForGeneration.value = noteEntry
                                viewModel.selectedSubTab.value = "Quizzes"
                                onClose()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Use for Flashcards (AI)") },
                            leadingIcon = { Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                aiMenuExpanded = false
                                val noteEntry = NoteEntry(
                                    title = mode.title,
                                    content = "",
                                    fileType = "PDF",
                                    filePath = mode.path
                                )
                                viewModel.attachedFileForGeneration.value = noteEntry
                                viewModel.selectedSubTab.value = "Flashcards"
                                onClose()
                            }
                        )
                    }
                }
            }
        }

        if (mode is ActiveViewer.TextNote) {
                val diagrams = remember(currentNoteContent) { parseDiagramsFromContent(currentNoteContent) }
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (diagrams.isNotEmpty()) {
                        var showEditDialog by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("edit_diagram_html_btn")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Diagrams",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (showEditDialog) {
                            var selectedDiagramIdx by remember { mutableStateOf(0) }
                            val selectedDiag = diagrams.getOrNull(selectedDiagramIdx)
                            
                            var editableHtml by remember(selectedDiag) { 
                                mutableStateOf(selectedDiag?.contentCode ?: "") 
                            }

                            val isB64 = selectedDiag?.isBase64 == true
                            val titleText = if (isB64) "Edit Base64 Diagram Image" else "Edit Diagram HTML & CSS Code"
                            val descriptionText = if (isB64) {
                                "Paste your custom Base64 image code below. The app will decode and render it as an inline diagram:"
                            } else {
                                "Correct errors, customize colors, layout, or arrows of your HTML diagram:"
                            }
                            val labelText = if (isB64) "Base64 Image Code" else "HTML / CSS Code"

                            AlertDialog(
                                onDismissRequest = { showEditDialog = false },
                                title = {
                                    Text(titleText, fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                        Text(
                                            descriptionText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        if (diagrams.size > 1) {
                                            Text("Select Diagram to Edit:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                diagrams.forEachIndexed { index, diag ->
                                                    val isSelected = selectedDiagramIdx == index
                                                    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                    val tc = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    Surface(
                                                        onClick = { selectedDiagramIdx = index },
                                                        shape = MaterialTheme.shapes.small,
                                                        color = bg,
                                                        contentColor = tc,
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    ) {
                                                        val typeLabel = if (diag.isBase64) "Base64" else "HTML"
                                                        Text("Figure ${diag.id} ($typeLabel)", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }

                                        OutlinedTextField(
                                            value = editableHtml,
                                            onValueChange = { editableHtml = it },
                                            label = { Text(labelText) },
                                            modifier = Modifier.fillMaxWidth().height(250.dp).testTag("diagram_html_input"),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            maxLines = 15
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (selectedDiag != null) {
                                                val updated = replaceDiagramInContent(currentNoteContent, selectedDiag, editableHtml)
                                                currentNoteContent = updated
                                                viewModel.updateNoteTextContent(mode.id, mode.path, updated)
                                            }
                                            showEditDialog = false
                                        },
                                        modifier = Modifier.testTag("save_diagram_html_btn")
                                    ) {
                                        Text("Save Changes")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showEditDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }

                    Box {
                        var noteAiMenuExpanded by remember { mutableStateOf(false) }
                        Button(
                            onClick = { noteAiMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            modifier = Modifier.testTag("note_ai_tools_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Study Tools", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = noteAiMenuExpanded,
                            onDismissRequest = { noteAiMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Create Mind Map (AI)") },
                                leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    noteAiMenuExpanded = false
                                    val noteEntry = NoteEntry(
                                        title = mode.title,
                                        content = currentNoteContent,
                                        fileType = "TEXT",
                                        filePath = mode.path
                                    )
                                    viewModel.attachedFileForGeneration.value = noteEntry
                                    viewModel.selectedSubTab.value = "Mindmap"
                                    onClose()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Create Quiz (AI)") },
                                leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    noteAiMenuExpanded = false
                                    val noteEntry = NoteEntry(
                                        title = mode.title,
                                        content = currentNoteContent,
                                        fileType = "TEXT",
                                        filePath = mode.path
                                    )
                                    viewModel.attachedFileForGeneration.value = noteEntry
                                    viewModel.selectedSubTab.value = "Quizzes"
                                    onClose()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Create Flashcards (AI)") },
                                leadingIcon = { Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    noteAiMenuExpanded = false
                                    val noteEntry = NoteEntry(
                                        title = mode.title,
                                        content = currentNoteContent,
                                        fileType = "TEXT",
                                        filePath = mode.path
                                    )
                                    viewModel.attachedFileForGeneration.value = noteEntry
                                    viewModel.selectedSubTab.value = "Flashcards"
                                    onClose()
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (isPdfInverted) Color(0xFF1E2124) else Color(0xFFF1F5F9))
        ) {
            when (mode) {
                is ActiveViewer.TextNote -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
                        ) {
                            LazyColumn(modifier = Modifier.padding(16.dp)) {
                                item {
                                    LatexText(
                                        text = currentNoteContent,
                                        fontSizeSp = 14,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }
                    }
                }
                is ActiveViewer.ImageNote -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = mode.path,
                            contentDescription = "Visual Image Document note",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                is ActiveViewer.PdfNote -> {
                    val totalPages = remember(mode.path) {
                        var count = 0
                        try {
                            val file = File(mode.path)
                            if (file.exists()) {
                                val input = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                try {
                                    val renderer = android.graphics.pdf.PdfRenderer(input)
                                    try {
                                        count = renderer.pageCount
                                    } finally {
                                        renderer.close()
                                    }
                                } finally {
                                    input.close()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PdfRenderer", "Error getting page count", e)
                        }
                        count
                    }

                    if (totalPages == 0) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Page rendered failed. The PDF structure might be encrypted offline.", color = Color.White, textAlign = TextAlign.Center)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            repeat(totalPages) { index ->
                                PdfPageItem(
                                    pdfPath = mode.path,
                                    pageIndex = index,
                                    totalPages = totalPages,
                                    isInverted = isPdfInverted
                                )
                            }
                        }
                    }
                }
                is ActiveViewer.MindMapNote -> {
                    val rootNode = remember(mode.content) {
                        try {
                            parseMindMapJson(mode.content)
                        } catch (e: Exception) {
                            MindMapNode("Failed to parse mind map: ${e.localizedMessage}")
                        }
                    }
                    val context = LocalContext.current
                    InteractiveMindMapView(
                        rootNode = rootNode,
                        title = mode.title,
                        onDownloadHtml = {
                            viewModel.downloadMindMapAsHtml(context, mode.title, mode.content)
                        }
                    )
                }
                else -> {}
            }
        }
    }
}


// ==================== FLASHCARDS SECTION ====================
@Composable
fun FlashcardsDeckSection(viewModel: StudyMateViewModel) {
    val sets by viewModel.flashcardSets.collectAsStateWithLifecycle()
    val isAILoading by viewModel.isAILoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val filteredSets = remember(sets, searchQuery) {
        if (searchQuery.isBlank()) {
            sets
        } else {
            sets.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    var showMakerDialog by remember { mutableStateOf(false) }
    var activeFlashDeckSet by remember { mutableStateOf<FlashcardSet?>(null) }

    var deckTitle by remember { mutableStateOf("") }
    var notesDraftInput by remember { mutableStateOf("") }
    var targetQuestionsCount by remember { mutableStateOf(5) }

    var selectedPdfNotesPath by remember { mutableStateOf<String?>(null) }
    var attachedPdfFileName by remember { mutableStateOf<String?>(null) }

    val attachedFileForGen by viewModel.attachedFileForGeneration.collectAsStateWithLifecycle()

    LaunchedEffect(attachedFileForGen) {
        val f = attachedFileForGen
        if (f != null) {
            if (f.fileType == "PDF") {
                selectedPdfNotesPath = f.filePath
                attachedPdfFileName = f.title
                showMakerDialog = true
            } else if (f.fileType == "TEXT") {
                notesDraftInput = f.content
                deckTitle = f.title
                showMakerDialog = true
            }
            viewModel.attachedFileForGeneration.value = null
        }
    }

    val context = LocalContext.current
    val pdfUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Document.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    fileName = cursor.getString(nameIdx)
                }
            }
            val localPath = viewModel.copyUriToLocalStorage(uri, fileName, "PDF")
            selectedPdfNotesPath = localPath
            attachedPdfFileName = fileName
        }
    }

    if (activeFlashDeckSet != null) {
        FlashcardDeckViewer(viewModel, activeFlashDeckSet!!) { activeFlashDeckSet = null }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showMakerDialog = true },
                modifier = Modifier.testTag("new_flashcards_fab")
            ) {
                Icon(Icons.Default.School, contentDescription = "Create Flashcards Set")
            }
        }
    ) { paddingVals ->
        Column(modifier = Modifier.fillMaxSize().padding(bottom = paddingVals.calculateBottomPadding())) {
            if (isAILoading) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Extracting contents & instructing Gemini to write flashcards...", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = { viewModel.cancelActiveAIGeneration() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.align(Alignment.End).testTag("cancel_flashcards_generation")
                        ) {
                            Text("Cancel Generator")
                        }
                    }
                }
            }

            if (filteredSets.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.School, contentDescription = "Flashcards icon", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching flashcards" else "No flashcard sets generated",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "Refine your query or clear the search bar to show all decks." else "Paste text context or drop notes PDF to let Gemini structure question/answer decks automatically",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredSets) { deck ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeFlashDeckSet = deck }
                                .testTag("flashcard_set_card_${deck.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play deck", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(deck.title, fontWeight = FontWeight.Bold)
                                        Text("Study Cards Deck", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.deleteFlashcardCollection(deck) },
                                    modifier = Modifier.testTag("delete_flashcard_set_btn_${deck.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete set", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMakerDialog) {
        AlertDialog(
            onDismissRequest = { showMakerDialog = false },
            title = { Text("Generate AI Flashcards Set") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Provide study notes details, and the app will command Gemini to write complete Q&A cards.", fontSize = 11.sp)
                    OutlinedTextField(
                        value = deckTitle,
                        onValueChange = { deckTitle = it },
                        label = { Text("Set Subject Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("flash_set_title_input")
                    )

                    OutlinedTextField(
                        value = notesDraftInput,
                        onValueChange = { notesDraftInput = it },
                        placeholder = { Text("Paste textbook paragraphs, slide notes or concepts...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .testTag("flash_text_input"),
                        minLines = 3
                    )

                    HorizontalDivider()

                    Text("Optionally attach educational PDF note files to analyze", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    if (attachedPdfFileName != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "PDF Attached", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(attachedPdfFileName!!, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                                }
                                IconButton(onClick = {
                                    attachedPdfFileName = null
                                    selectedPdfNotesPath = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear pdf attachment", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { pdfUploadLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth().testTag("attach_pdf_flash_btn")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "PDF")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Drop PDF notes", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Target Cards Count:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { 
                                if (targetQuestionsCount > 3) {
                                    if (targetQuestionsCount > 10) targetQuestionsCount -= 5 else targetQuestionsCount--
                                }
                            }) {
                                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(targetQuestionsCount.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            TextButton(onClick = { 
                                if (targetQuestionsCount < 200) {
                                    if (targetQuestionsCount >= 10) targetQuestionsCount += 5 else targetQuestionsCount++
                                }
                            }) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deckTitle.isNotBlank() && (notesDraftInput.isNotBlank() || selectedPdfNotesPath != null)) {
                            viewModel.generateAIFlashcards(deckTitle, notesDraftInput, selectedPdfNotesPath, targetQuestionsCount)
                            deckTitle = ""
                            notesDraftInput = ""
                            selectedPdfNotesPath = null
                            attachedPdfFileName = null
                            showMakerDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_flash_btn")
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMakerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FlashcardDeckViewer(viewModel: StudyMateViewModel, deck: FlashcardSet, onBack: () -> Unit) {
    val cards by viewModel.getFlashcardsForSet(deck.id).collectAsState(emptyList())
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var currentIndex by remember { mutableStateOf(0) }
    var isRevealed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_dashboard_deck_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(deck.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        if (cards.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Analyzing flashcard items... Please wait.")
            }
        } else if (currentIndex >= cards.size) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Finished", modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Deck Study Completed!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        currentIndex = 0
                        isRevealed = false
                    }) {
                        Text("Review Set Again")
                    }
                }
            }
        } else {
            val card = cards[currentIndex]

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Card ${currentIndex + 1} of ${cards.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Standard flip card box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isRevealed = !isRevealed
                        }
                        .testTag("flashcard_flipper_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRevealed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isRevealed) "ANSWER" else "QUESTION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRevealed) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LatexText(
                                text = if (isRevealed) card.answer else card.question,
                                fontSizeSp = 20,
                                textAlign = TextAlign.Center,
                                color = if (isRevealed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Tap Card to Flip",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.updateFlashcardKnowledge(card.id, false)
                            isRevealed = false
                            currentIndex++
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("still_learning_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "No")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Still learning")
                    }

                    Button(
                        onClick = {
                            viewModel.updateFlashcardKnowledge(card.id, true)
                            isRevealed = false
                            currentIndex++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.testTag("know_this_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Yes")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("I know this")
                    }
                }
            }
        }
    }
}


// ==================== QUIZ SECTION ====================
@Composable
fun QuizzesPlaySection(viewModel: StudyMateViewModel) {
    val quizSets by viewModel.quizSets.collectAsStateWithLifecycle()
    val isAILoading by viewModel.isAILoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val filteredQuizSets = remember(quizSets, searchQuery) {
        if (searchQuery.isBlank()) {
            quizSets
        } else {
            quizSets.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    var showQuizMakerDialog by remember { mutableStateOf(false) }
    var activeQuizSet by remember { mutableStateOf<QuizSet?>(null) }

    var quizTitle by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var targetQuestionsCount by remember { mutableStateOf(5) }

    var selectedPdfPath by remember { mutableStateOf<String?>(null) }
    var pdfAttachedName by remember { mutableStateOf<String?>(null) }

    val attachedFileForGen by viewModel.attachedFileForGeneration.collectAsStateWithLifecycle()

    LaunchedEffect(attachedFileForGen) {
        val f = attachedFileForGen
        if (f != null) {
            if (f.fileType == "PDF") {
                selectedPdfPath = f.filePath
                pdfAttachedName = f.title
                showQuizMakerDialog = true
            } else if (f.fileType == "TEXT") {
                notesInput = f.content
                quizTitle = f.title
                showQuizMakerDialog = true
            }
            viewModel.attachedFileForGeneration.value = null
        }
    }

    val context = LocalContext.current
    val quizPdfUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Document.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    fileName = cursor.getString(nameIdx)
                }
            }
            val localPath = viewModel.copyUriToLocalStorage(uri, fileName, "PDF")
            selectedPdfPath = localPath
            pdfAttachedName = fileName
        }
    }

    if (activeQuizSet != null) {
        QuizGameplayViewer(viewModel, activeQuizSet!!) { activeQuizSet = null }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuizMakerDialog = true },
                modifier = Modifier.testTag("generate_quiz_fab")
            ) {
                Icon(Icons.Default.Quiz, contentDescription = "Create Quiz")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(bottom = paddingValues.calculateBottomPadding())) {
            if (isAILoading) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Gemini is reading text & forming multiple choice quiz...", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = { viewModel.cancelActiveAIGeneration() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.align(Alignment.End).testTag("cancel_quiz_generation")
                        ) {
                            Text("Cancel Generator")
                        }
                    }
                }
            }

            if (filteredQuizSets.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Quiz, contentDescription = "Quiz icon", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching quizzes" else "No quizzes built yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "Refine your query or clear the search bar to show all quizzes." else "Ask StudyMate Gemini to generate a quiz from study notes or drop educational documents offline!",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredQuizSets) { quiz ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeQuizSet = quiz }
                                .testTag("quiz_set_card_${quiz.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = "Start", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(quiz.title, fontWeight = FontWeight.Bold)
                                        Text("Diagnostic Choice Quiz", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            viewModel.resetQuizProgress(quiz.id)
                                            val sp = context.getSharedPreferences("QuizProgress_${quiz.id}", Context.MODE_PRIVATE)
                                            sp.edit().clear().apply()
                                            activeQuizSet = quiz
                                        },
                                        modifier = Modifier.testTag("reattempt_quiz_btn_${quiz.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reset and reattempt",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.downloadQuizAnswerKey(context, quiz) },
                                        modifier = Modifier.testTag("download_quiz_answer_key_btn_${quiz.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download Answer Key PDF",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteQuizCollection(quiz) },
                                        modifier = Modifier.testTag("delete_quiz_set_btn_${quiz.id}")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete set", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuizMakerDialog) {
        AlertDialog(
            onDismissRequest = { showQuizMakerDialog = false },
            title = { Text("Generate AI Knowledge Quiz") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Design multiple choice questions based on study materials instantly.", fontSize = 11.sp)
                    OutlinedTextField(
                        value = quizTitle,
                        onValueChange = { quizTitle = it },
                        label = { Text("Quiz Subject Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("quiz_title_input")
                    )

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("Paste concepts, slide highlights or syllabus notes...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .testTag("quiz_notes_input"),
                        minLines = 3
                    )

                    HorizontalDivider()

                    Text("Optionally drop textbook PDF file to formulate quiz", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    if (pdfAttachedName != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "PDF Attached", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(pdfAttachedName!!, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                                }
                                IconButton(onClick = {
                                    pdfAttachedName = null
                                    selectedPdfPath = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear attachment", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { quizPdfUploadLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.testTag("attach_pdf_quiz_btn")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "PDF")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Attach PDF note", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Target Questions Count:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { 
                                if (targetQuestionsCount > 3) {
                                    if (targetQuestionsCount > 10) targetQuestionsCount -= 5 else targetQuestionsCount--
                                }
                            }) {
                                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(targetQuestionsCount.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            TextButton(onClick = { 
                                if (targetQuestionsCount < 200) {
                                    if (targetQuestionsCount >= 10) targetQuestionsCount += 5 else targetQuestionsCount++
                                }
                            }) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (quizTitle.isNotBlank() && (notesInput.isNotBlank() || selectedPdfPath != null)) {
                            viewModel.generateAIQuiz(quizTitle, notesInput, selectedPdfPath, targetQuestionsCount)
                            quizTitle = ""
                            notesInput = ""
                            selectedPdfPath = null
                            pdfAttachedName = null
                            showQuizMakerDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_quiz_btn")
                ) {
                    Text("Generate Quiz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuizMakerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QuizGameplayViewer(viewModel: StudyMateViewModel, quizSet: QuizSet, onBack: () -> Unit) {
    val questions by viewModel.getQuizQuestionsForSet(quizSet.id).collectAsState(emptyList())

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember(quizSet.id) { context.getSharedPreferences("QuizProgress_${quizSet.id}", Context.MODE_PRIVATE) }

    var currentIndex by remember { mutableStateOf(0) }
    var selectedOptions by remember { mutableStateOf(emptySet<String>()) }
    var isSubmitted by remember { mutableStateOf(false) }
    var quizMode by remember(quizSet.id) { mutableStateOf(prefs.getString("quiz_mode", null)) } // "PRACTICE" or "EXAM"
    val answersMap = remember { mutableStateMapOf<Int, String?>() }
    var hasInitializedProgress by remember(quizSet.id) { mutableStateOf(false) }

    fun isAnswerCorrect(userAnswer: String?, correctAnswer: String, questionType: String = "MCQ"): Boolean {
        if (userAnswer == null) return false
        if (questionType == "INT") {
            val userClean = userAnswer.trim().replace(Regex("[^0-9\\-]"), "")
            val correctClean = correctAnswer.trim().replace(Regex("[^0-9\\-]"), "")
            if (userClean.isNotBlank() && correctClean.isNotBlank()) {
                val userInt = userClean.toIntOrNull()
                val correctInt = correctClean.toIntOrNull()
                if (userInt != null && correctInt != null) {
                    return userInt == correctInt
                }
            }
            return userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
        }
        val userSet = userAnswer.split("||").filter { it.isNotBlank() }.map { it.trim() }.toSet()
        val correctSet = correctAnswer.split("||").filter { it.isNotBlank() }.map { it.trim() }.toSet()
        return userSet == correctSet
    }

    var secondsSpent by remember(currentIndex, quizMode) { mutableStateOf(prefs.getInt("time_spent_$currentIndex", 0)) }

    LaunchedEffect(currentIndex) {
        if (questions.isNotEmpty() && currentIndex >= questions.size) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }

    val isTimerRunning = quizMode != null && currentIndex < questions.size && !(quizMode == "PRACTICE" && isSubmitted)
    LaunchedEffect(isTimerRunning, currentIndex) {
        if (isTimerRunning) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                secondsSpent++
                prefs.edit().putInt("time_spent_$currentIndex", secondsSpent).apply()
            }
        }
    }

    LaunchedEffect(questions) {
        if (questions.isNotEmpty() && !hasInitializedProgress) {
            var firstUnanswered = -1
            questions.forEachIndexed { idx, q ->
                if (q.userAnswer != null) {
                    answersMap[idx] = if (q.userAnswer == "__SKIPPED__") null else q.userAnswer
                } else {
                    if (firstUnanswered == -1) {
                        firstUnanswered = idx
                    }
                }
            }
            if (firstUnanswered != -1) {
                currentIndex = firstUnanswered
            } else {
                currentIndex = questions.size
            }
            hasInitializedProgress = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_dashboard_quiz_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(quizSet.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        if (questions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Preparing diagnostic quiz challenge... Please wait.")
            }
        } else if (quizMode == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 450.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = "Quiz icon",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Choose Quiz Mode",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select how you would like to tackle this academic quiz challenge:",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            onClick = { 
                                quizMode = "PRACTICE"
                                prefs.edit().putString("quiz_mode", "PRACTICE").apply()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("💡 Practice Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Answer checked instantly during playback. Review solution breakdown page-by-page. Earn +4 for correct, -1 for incorrect, 0 for skip.", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }

                        Card(
                            onClick = { 
                                quizMode = "EXAM"
                                prefs.edit().putString("quiz_mode", "EXAM").apply()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("📝 Exam Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                                Text("Real examination style. No indicators shown during test. Scorecard and comprehensive solutions visible only after submitting.", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
            }
        } else if (currentIndex >= questions.size) {
            var correctCount = 0
            var wrongCount = 0
            var skippedCount = 0
            questions.forEachIndexed { idx, q ->
                val userAns = answersMap[idx]
                val isCorrect = isAnswerCorrect(userAns, q.correctAnswer, q.questionType)
                if (userAns == null) {
                    skippedCount++
                } else if (isCorrect) {
                    correctCount++
                } else {
                    wrongCount++
                }
            }
            val finalScore = (correctCount * 4) - (wrongCount * 1)
            val maxScore = questions.size * 4

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Stars, contentDescription = "Trophy icon", modifier = Modifier.size(72.dp), tint = Color(0xFFFFD700))
                Text("Quiz Accomplished!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your Performance Scorecard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$finalScore / $maxScore Marks", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✅ Correct (+4)", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                Text("$correctCount", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("❌ Incorrect (-1)", fontSize = 11.sp, color = Color(0xFFC62828))
                                Text("$wrongCount", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⏭️ Skipped (0)", fontSize = 11.sp, color = Color.Gray)
                                Text("$skippedCount", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Time statistics row
                        val totalTime = (0 until questions.size).sumOf { i -> prefs.getInt("time_spent_$i", 0) }
                        val formattedTotalTime = formatTime(totalTime)
                        val formattedAvgTime = if (questions.isNotEmpty()) {
                            val avgSecs = totalTime / questions.size
                            val rem = (totalTime % questions.size) * 10 / questions.size
                            "$avgSecs.${rem}s"
                        } else "0s"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⏱️ Total Time Taken", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formattedTotalTime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚡ Average Time / Qn", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formattedAvgTime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Text("📋 Question Solutions & Keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))

                questions.forEachIndexed { idx, q ->
                    val userAns = answersMap[idx]
                    val corrAns = q.correctAnswer
                    val isCorrect = isAnswerCorrect(userAns, corrAns, q.questionType)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (userAns == null) Color.Gray else if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val questionTime = prefs.getInt("time_spent_$idx", 0)
                                Text("Question ${idx + 1} (⏱️ ${formatTime(questionTime)})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                if (userAns == null) {
                                    Text("Skipped (0 pts)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else if (isCorrect) {
                                    Text("Correct (+4 pts)", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Incorrect (-1 pts)", color = Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Text(q.question, fontSize = 14.sp)
                            
                            // Question type marked below the qn
                            Text(
                                text = if (q.questionType == "MSQ") "☑ Multiple Select Question (Select all correct options)" else "🔘 Multiple Choice Question (Select one option)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))

                            q.optionsList.forEach { option ->
                                val isSelectedOption = userAns?.split("||")?.contains(option) == true
                                val isCorrectChoice = corrAns.split("||").contains(option)
                                
                                val textBackground = if (isCorrectChoice) {
                                    Color(0xFFE8F5E9)
                                } else if (isSelectedOption) {
                                    Color(0xFFFFEBEE)
                                } else {
                                    Color.Transparent
                                }

                                val borderStroke = if (isCorrectChoice) {
                                    BorderStroke(1.dp, Color(0xFF2E7D32))
                                } else if (isSelectedOption) {
                                    BorderStroke(1.dp, Color(0xFFC62828))
                                } else {
                                    null
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = textBackground),
                                    border = borderStroke,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (q.questionType == "MSQ") {
                                                if (isCorrectChoice) Icons.Default.CheckBox else if (isSelectedOption) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank
                                            } else {
                                                if (isCorrectChoice) Icons.Default.CheckCircle else if (isSelectedOption) Icons.Default.Cancel else Icons.Default.RadioButtonUnchecked
                                            },
                                            contentDescription = null,
                                            tint = if (isCorrectChoice) Color(0xFF2E7D32) else if (isSelectedOption) Color(0xFFC62828) else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(option, fontSize = 12.sp)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "💡 Study Solution Details:\nThe correct response is \"${corrAns.replace("||", ", ")}\". Study material validates this concept directly. Analyze incorrect options to secure marks.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.downloadQuizAnswerKey(context, quizSet)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("download_quiz_answer_key_result_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download PDF Answer Key")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.resetQuizProgress(quizSet.id)
                        prefs.edit().clear().apply()
                        currentIndex = 0
                        selectedOptions = emptySet()
                        isSubmitted = false
                        answersMap.clear()
                        quizMode = null
                        hasInitializedProgress = true
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Replay Quiz Deck")
                }
            }
        } else {
            val q = questions[currentIndex]

            // Calculate live score during playing
            var currentScoreIter = 0
            for (i in 0 until currentIndex) {
                val userAns = answersMap[i]
                if (userAns != null) {
                    val ques = questions[i]
                    if (isAnswerCorrect(userAns, ques.correctAnswer, ques.questionType)) {
                        currentScoreIter += 4
                    } else {
                        currentScoreIter -= 1
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Question ${currentIndex + 1} of ${questions.size} (${quizMode})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Ticker timer info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Timer",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatTime(secondsSpent),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (quizMode == "PRACTICE") {
                            Text("Score: $currentScoreIter pts", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    LatexText(
                        text = q.question,
                        modifier = Modifier.padding(20.dp),
                        fontSizeSp = 16,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Question type marked below the qn
                Text(
                    text = when (q.questionType) {
                        "MSQ" -> "☑ Multiple Select Question (Select all correct options)"
                        "INT" -> "🔢 Integer Answer Question (Type a rounded integer)"
                        else -> "🔘 Multiple Choice Question (Select one option)"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                if (q.questionType == "INT") {
                    var typedText by remember(currentIndex) { 
                        mutableStateOf(selectedOptions.firstOrNull() ?: "") 
                    }
                    
                    OutlinedTextField(
                        value = typedText,
                        onValueChange = { input ->
                            val filtered = input.filterIndexed { index, char ->
                                char.isDigit() || (char == '-' && index == 0)
                            }
                            typedText = filtered
                            selectedOptions = if (filtered.isNotBlank()) setOf(filtered) else emptySet()
                        },
                        label = { Text("Your Integer Answer") },
                        placeholder = { Text("e.g. 42") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quiz_int_input")
                            .padding(vertical = 8.dp),
                        enabled = quizMode != "PRACTICE" || !isSubmitted,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                } else {
                    // Display options
                    q.optionsList.forEach { option ->
                        val isSelectedOption = selectedOptions.contains(option)
                        val isCorrectValue = q.correctAnswer.split("||").contains(option)
                        val btnColors = if (quizMode == "PRACTICE" && isSubmitted) {
                            if (isCorrectValue) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
                            } else if (isSelectedOption) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        } else {
                            if (isSelectedOption) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        }

                        Button(
                            onClick = { 
                                if (quizMode != "PRACTICE" || !isSubmitted) {
                                    if (q.questionType == "MSQ") {
                                        selectedOptions = if (isSelectedOption) selectedOptions - option else selectedOptions + option
                                    } else {
                                        selectedOptions = setOf(option)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quiz_option_${option.take(15)}"),
                            colors = btnColors,
                            border = if (quizMode == "PRACTICE" && isSubmitted) null else if (!isSelectedOption) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (q.questionType == "MSQ") {
                                        if (quizMode == "PRACTICE" && isSubmitted && isCorrectValue) Icons.Default.CheckBox
                                        else if (quizMode == "PRACTICE" && isSubmitted && isSelectedOption) Icons.Default.CheckBox
                                        else if (isSelectedOption) Icons.Default.CheckBox
                                        else Icons.Default.CheckBoxOutlineBlank
                                    } else {
                                        if (quizMode == "PRACTICE" && isSubmitted && isCorrectValue) Icons.Default.CheckCircle
                                        else if (quizMode == "PRACTICE" && isSubmitted && isSelectedOption) Icons.Default.Cancel
                                        else if (isSelectedOption) Icons.Default.CheckCircle
                                        else Icons.Default.RadioButtonUnchecked
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                LatexText(text = option, fontSizeSp = 14)
                            }
                        }
                    }
                }

                if (quizMode == "PRACTICE" && isSubmitted) {
                    val answerStr = selectedOptions.joinToString("||")
                    val isCorr = isAnswerCorrect(answerStr, q.correctAnswer, q.questionType)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("💡 Study Solution Summary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            Text(
                                text = "Correct explanation: \"${q.correctAnswer.replace("||", ", ")}\". " +
                                       if (isCorr) "Superb response! You gained +4 Marks." else "Incorrect response. You lost -1 Mark.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val skipAllowed = if (quizMode == "PRACTICE") !isSubmitted else true
                    OutlinedButton(
                        onClick = {
                            viewModel.submitQuizResponse(q.id, "__SKIPPED__", false)
                            answersMap[currentIndex] = null
                            currentIndex++
                            selectedOptions = emptySet()
                            isSubmitted = false
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = skipAllowed,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Skip (0 pts)")
                    }

                    Button(
                        onClick = {
                            val answerStr = selectedOptions.joinToString("||")
                            val isCorr = isAnswerCorrect(answerStr, q.correctAnswer, q.questionType)
                            if (quizMode == "PRACTICE") {
                                if (!isSubmitted) {
                                    isSubmitted = true
                                    answersMap[currentIndex] = answerStr
                                    viewModel.submitQuizResponse(q.id, answerStr, isCorr)
                                } else {
                                    currentIndex++
                                    selectedOptions = emptySet()
                                    isSubmitted = false
                                }
                            } else {
                                answersMap[currentIndex] = answerStr
                                viewModel.submitQuizResponse(q.id, answerStr, isCorr)
                                currentIndex++
                                selectedOptions = emptySet()
                                isSubmitted = false
                            }
                        },
                        modifier = Modifier.weight(1.3f).height(50.dp),
                        enabled = selectedOptions.isNotEmpty()
                    ) {
                        val label = if (quizMode == "PRACTICE") {
                            if (isSubmitted) "Next Question" else "Check Answer"
                        } else {
                            if (currentIndex == questions.size - 1) "Finalize & Submit" else "Save & Next"
                        }
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// ==================== STUDY PLANNER TAB ====================
@Composable
fun PlannerTab(viewModel: StudyMateViewModel) {
    val events by viewModel.studyEvents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val pomodoroMin by viewModel.pomodoroMinutes.collectAsStateWithLifecycle()
    val pomodoroSec by viewModel.pomodoroSeconds.collectAsStateWithLifecycle()
    val isPomodoroRunning by viewModel.isPomodoroRunning.collectAsStateWithLifecycle()
    val pomodoroMode by viewModel.pomodoroMode.collectAsStateWithLifecycle()
    val customStudyMin by viewModel.customStudyMinutes.collectAsStateWithLifecycle()
    val customBreakMin by viewModel.customBreakMinutes.collectAsStateWithLifecycle()

    var subjectName by remember { mutableStateOf("") }
    var selectedDateTimeCalendar by remember { mutableStateOf<Calendar?>(null) }
    var pickedDateText by remember { mutableStateOf("No study date selected") }
    var pickedTimeText by remember { mutableStateOf("No study hour picked") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("planner_view_panel"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("pomodoro_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (pomodoroMode == "STUDY") 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                    else 
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, if (pomodoroMode == "STUDY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Focus Timer (Pomodoro)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (pomodoroMode == "STUDY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                        IconButton(onClick = { viewModel.resetPomodoro() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Mode Toggle buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setPomodoroMode("STUDY")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pomodoroMode == "STUDY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (pomodoroMode == "STUDY") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).testTag("pomodoro_study_mode_btn")
                        ) {
                            Text("Study ($customStudyMin Min)")
                        }
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setPomodoroMode("BREAK")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pomodoroMode == "BREAK") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (pomodoroMode == "BREAK") MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).testTag("pomodoro_break_mode_btn")
                        ) {
                            Text("Break ($customBreakMin Min)")
                        }
                    }

                    // Custom Duration Adjusters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Adjust Study Time", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledIconButton(
                                    onClick = { viewModel.setCustomStudyMinutes(customStudyMin - 1) },
                                    enabled = customStudyMin > 1,
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Study Time", modifier = Modifier.size(16.dp))
                                }
                                Text("$customStudyMin m", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                FilledIconButton(
                                    onClick = { viewModel.setCustomStudyMinutes(customStudyMin + 1) },
                                    enabled = customStudyMin < 180,
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase Study Time", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Adjust Break Time", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledIconButton(
                                    onClick = { viewModel.setCustomBreakMinutes(customBreakMin - 1) },
                                    enabled = customBreakMin > 1,
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Break Time", modifier = Modifier.size(16.dp))
                                }
                                Text("$customBreakMin m", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                FilledIconButton(
                                    onClick = { viewModel.setCustomBreakMinutes(customBreakMin + 1) },
                                    enabled = customBreakMin < 180,
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase Break Time", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Timer Counter Display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color.Black.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "%02d : %02d", pomodoroMin, pomodoroSec),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (pomodoroMode == "STUDY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                    }

                    // Play/Pause Action Row
                    Button(
                        onClick = {
                            if (isPomodoroRunning) {
                                viewModel.pausePomodoro()
                            } else {
                                viewModel.startPomodoro()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPomodoroRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("pomodoro_start_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPomodoroRunning) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = if (isPomodoroRunning) "Pause" else "Start"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPomodoroRunning) "Pause focus session" else "Start focus session",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Schedule Study Focus Session", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Input subject guidelines & study intervals. The system trigger notifications exactly 15 minutes prior to the study milestones.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject / Chapters focus") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("planner_subject_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        if (selectedDateTimeCalendar == null) {
                                            selectedDateTimeCalendar = Calendar.getInstance()
                                        }
                                        selectedDateTimeCalendar!!.set(Calendar.YEAR, y)
                                        selectedDateTimeCalendar!!.set(Calendar.MONTH, m)
                                        selectedDateTimeCalendar!!.set(Calendar.DAY_OF_MONTH, d)
                                        pickedDateText = String.format(Locale.getDefault(), "%02d-%02d-%04d", d, m + 1, y)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f).testTag("pick_date_btn")
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Date", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Set Date", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val cal = Calendar.getInstance()
                                TimePickerDialog(
                                    context,
                                    { _, hh, mm ->
                                        if (selectedDateTimeCalendar == null) {
                                            selectedDateTimeCalendar = Calendar.getInstance()
                                        }
                                        selectedDateTimeCalendar!!.set(Calendar.HOUR_OF_DAY, hh)
                                        selectedDateTimeCalendar!!.set(Calendar.MINUTE, mm)
                                        selectedDateTimeCalendar!!.set(Calendar.SECOND, 0)
                                        pickedTimeText = String.format(Locale.getDefault(), "%02d:%02d", hh, mm)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            modifier = Modifier.weight(1f).testTag("pick_clock_btn")
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Clock", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick Time", fontSize = 12.sp)
                        }
                    }

                    // Selection indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(pickedDateText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(pickedTimeText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = {
                            if (subjectName.isNotBlank() && selectedDateTimeCalendar != null) {
                                viewModel.addStudyEvent(subjectName, selectedDateTimeCalendar!!.timeInMillis)
                                subjectName = ""
                                selectedDateTimeCalendar = null
                                pickedDateText = "No study date selected"
                                pickedTimeText = "No study hour picked"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("schedule_event_btn"),
                        enabled = subjectName.isNotBlank() && selectedDateTimeCalendar != null
                    ) {
                        Text("Add to Planner Stack", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("Your Scheduled Timeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        if (events.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No events scheduled.", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(events) { ev ->
                val formatter = remember { SimpleDateFormat("h:mm a, dd MMM yyyy", Locale.getDefault()) }
                val timeFormatted = formatter.format(Date(ev.studyTimeMillis))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ev.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ev.subject,
                                fontWeight = FontWeight.Bold,
                                style = if (ev.isCompleted) MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodyLarge
                            )
                            Text("Scheduled focus: $timeFormatted", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (ev.isCompleted) {
                                Text("COMPLETED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                        }
                        Row {
                            if (!ev.isCompleted) {
                                IconButton(
                                    onClick = { viewModel.completeStudyEvent(ev.id, ev.subject) },
                                    modifier = Modifier.testTag("mark_event_completed_${ev.id}")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Complete", tint = Color(0xFF4CAF50))
                                }
                            }
                            IconButton(
                                onClick = { viewModel.removeStudyEvent(ev) },
                                modifier = Modifier.testTag("delete_planner_event_${ev.id}")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==================== SETTINGS & CREDENTIALS TAB ====================
@Composable
fun SettingsCredentialsTab(viewModel: StudyMateViewModel) {
    val keysList by viewModel.customApiKeys.collectAsStateWithLifecycle()

    var customKeySecret by remember { mutableStateOf("") }
    var keyLabel by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("credentials_page"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val context = LocalContext.current
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    viewModel.importBackup(context, it)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Account Backup & Recovery",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = "Export a complete backup containing all notes, flashcards, quiz sets, folder structure, API keys, and database entries as a single compressed ZIP file to your phone's storage. You can restore your data anytime using the Import option.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportBackup(context) },
                            modifier = Modifier.weight(1f).testTag("btn_export_backup"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export Backup")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export ZIP", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { launcher.launch(arrayOf("application/zip")) },
                            modifier = Modifier.weight(1f).testTag("btn_import_backup"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = "Import Backup")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import ZIP", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            var expanded by remember { mutableStateOf(false) }
            val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
            val models = listOf(
                "gemini-3.5-flash",
                "gemini-2.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-1.5-pro"
            )

            Card(
                modifier = Modifier.fillMaxWidth().testTag("card_model_selection"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Primary Gemini Model",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Select the model for text processing, notes summarization, mind map, flashcards, and quiz generation.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth().testTag("dropdown_model_trigger")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedModel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Expand dropdown"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.updateSelectedModel(model)
                                        expanded = false
                                    },
                                    modifier = Modifier.testTag("model_item_$model")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Built-in Chat API Key Provider Selection Card
        item {
            val chatProvider by viewModel.chatApiProvider.collectAsStateWithLifecycle()
            val openRouterKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
            val openRouterModel by viewModel.openRouterModelId.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier.fillMaxWidth().testTag("card_chat_provider_settings"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Built-in Chat API Provider",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Select API key provider specifically for Built-In Chat (AI Teacher / Homework Helper).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = chatProvider == "Google Gemini",
                            onClick = { viewModel.updateChatApiProvider("Google Gemini") },
                            label = { Text("Google Gemini") },
                            leadingIcon = if (chatProvider == "Google Gemini") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f).testTag("chat_provider_google")
                        )

                        FilterChip(
                            selected = chatProvider == "OpenRouter",
                            onClick = { viewModel.updateChatApiProvider("OpenRouter") },
                            label = { Text("OpenRouter") },
                            leadingIcon = if (chatProvider == "OpenRouter") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f).testTag("chat_provider_openrouter")
                        )
                    }

                    if (chatProvider == "Google Gemini") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "✓ Using collected Gemini API Key from AI Studio Secrets / Backup Fallback Pool.",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = openRouterKey,
                                onValueChange = { viewModel.updateOpenRouterApiKey(it) },
                                label = { Text("OpenRouter API Key (sk-or-v1-...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("openrouter_key_input")
                            )

                            OutlinedTextField(
                                value = openRouterModel,
                                onValueChange = { viewModel.updateOpenRouterModelId(it) },
                                label = { Text("OpenRouter Model ID") },
                                placeholder = { Text("e.g. google/gemini-2.0-flash-001") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("openrouter_model_input")
                            )

                            Text(
                                text = "Quick Model suggestions:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "google/gemini-2.0-flash-001",
                                    "anthropic/claude-3.5-sonnet",
                                    "deepseek/deepseek-r1"
                                ).forEach { suggestion ->
                                    AssistChip(
                                        onClick = { viewModel.updateOpenRouterModelId(suggestion) },
                                        label = { Text(suggestion.split("/").lastOrNull() ?: suggestion, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("API Keys Fallback Pool", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "StudyMate Pro automatically aggregates all custom keys defined in settings. If the main key reaches the daily quota limit (Error 429), the app cycles and rotatively falls back to other backup API keys without workflow disruptions.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Add Backup API Key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = keyLabel,
                        onValueChange = { keyLabel = it },
                        label = { Text("Key description (e.g. Backup Key 2)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("cred_label_input")
                    )

                    OutlinedTextField(
                        value = customKeySecret,
                        onValueChange = { customKeySecret = it },
                        label = { Text("Gemini API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("cred_key_input")
                    )

                    Button(
                        onClick = {
                            if (customKeySecret.isNotBlank() && keyLabel.isNotBlank()) {
                                viewModel.addCustomKey(customKeySecret, keyLabel)
                                customKeySecret = ""
                                keyLabel = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_save_key"),
                        enabled = customKeySecret.isNotBlank() && keyLabel.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Key")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add to Fallback Pool")
                    }
                }
            }
        }

        item {
            Text("Credentials Fallback Chain", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        // Show default built-in credentials
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("BuildConfig Main Key", fontWeight = FontWeight.Bold)
                            val available = BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
                            Text(
                                if (available) "Configured in AI Studio Secrets" else "Placeholder defined",
                                fontSize = 11.sp,
                                color = if (available) Color(0xFF4CAF50) else Color(0xFFE91E63)
                            )
                        }
                    }
                    Text("PRIMARY", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (keysList.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("No backup keys registered.", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                }
            }
        } else {
            items(keysList) { customKey ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (customKey.isWorking) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (customKey.isWorking) Icons.Default.Key else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (customKey.isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(customKey.label, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (customKey.key.length > 8) customKey.key.take(4) + "..." + customKey.key.takeLast(4) else "******",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (customKey.isWorking) "Status: Working & Pool Active" else "Status: Failed/Exhausted",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customKey.isWorking) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.removeCustomKey(customKey) },
                            modifier = Modifier.testTag("remove_key_btn_${customKey.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Key", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ==================== LATEX FORMATTING & PREVIEW ENGNE ====================

@Composable
fun LatexText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSizeSp: Int = 14,
    textAlign: TextAlign = TextAlign.Start
) {
    if (!hasMathDelimiters(text)) {
        // Render natively with standard Text if no LaTeX equations or rich markdown are present.
        Text(
            text = text,
            modifier = modifier,
            color = if (color != Color.Unspecified) color else LocalContentColor.current,
            fontSize = fontSizeSp.sp,
            textAlign = textAlign
        )
    } else {
        val resolvedColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
        val hexColor = remember(resolvedColor) {
            String.format("#%06X", 0xFFFFFF and resolvedColor.toArgb())
        }

        val isDark = MaterialTheme.colorScheme.background.red < 0.5f || resolvedColor == Color(0xFFE2E8F0) || resolvedColor == Color.White
        val currentHtml = remember(text, hexColor, fontSizeSp, textAlign, isDark) {
            generateKatexHtml(text, hexColor, fontSizeSp, textAlign, isDark)
        }
        val bodyHtml = remember(text) {
            convertMarkdownToHtml(text)
        }

        var webViewHeightDp by remember { mutableStateOf(0) }
        var isPageLoaded by remember { mutableStateOf(false) }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0) // 100% transparent background
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        useWideViewPort = false
                    }
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun resize(heightPx: Float) {
                            val density = ctx.resources.displayMetrics.density
                            val dp = (heightPx / density).toInt() + 16
                            if (dp > webViewHeightDp || dp < webViewHeightDp - 60) {
                                (ctx as? android.app.Activity)?.runOnUiThread {
                                    webViewHeightDp = dp
                                }
                            }
                        }
                    }, "Android")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageLoaded = true
                            view?.evaluateJavascript("if (typeof renderAll === 'function') { renderAll(); } else if (typeof updateContentHeight === 'function') { updateContentHeight(); }", null)
                        }
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            },
            update = { webView ->
                val tag = webView.tag as? String
                if (tag != currentHtml) {
                    webView.tag = currentHtml
                    val jsEscapedBody = bodyHtml
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "")
                    
                    if (isPageLoaded && tag != null) {
                        webView.evaluateJavascript("if (typeof updateContent === 'function') { updateContent('$jsEscapedBody'); } else { location.reload(); }", null)
                    } else {
                        webView.loadDataWithBaseURL("https://localhost", currentHtml, "text/html", "UTF-8", null)
                    }
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .then(if (webViewHeightDp > 0) Modifier.height(webViewHeightDp.dp) else Modifier)
        )
    }
}

fun hasMathDelimiters(text: String): Boolean {
    val hasInlinePair = text.count { it == '$' } >= 2
    return hasInlinePair || 
           text.contains("$$") || 
           text.contains("\\[") || 
           text.contains("\\(") || 
           text.contains("\\begin{") ||
           text.contains("**") ||
           text.contains("#") ||
           text.contains("\n-") ||
           text.contains("\n*") ||
           text.contains("1.") ||
           text.contains("|") ||
           text.contains("```") ||
           text.contains("`") ||
           text.contains("\n>")
}

fun generateKatexHtml(text: String, hexColor: String, fontSizeSp: Int, textAlign: TextAlign, isDark: Boolean): String {
    val bodyHtml = convertMarkdownToHtml(text)
    val cssAlign = if (textAlign == TextAlign.Center) "center" else "left"
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/contrib/auto-render.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.js"></script>
            <style>
                body {
                    margin: 0;
                    padding: 0 4px 24px 4px;
                    background-color: transparent !important;
                    color: $hexColor;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    font-size: ${fontSizeSp}px;
                    line-height: 1.6;
                    text-align: $cssAlign;
                    word-wrap: break-word;
                    word-break: break-word;
                    overflow-wrap: break-word;
                }
                #content {
                    word-wrap: break-word;
                }
                .katex-display {
                    overflow-x: auto !important;
                    overflow-y: hidden !important;
                    -webkit-overflow-scrolling: touch;
                    padding: 8px 0;
                }
                .katex {
                    max-width: 100%;
                    overflow-x: auto;
                    overflow-y: hidden;
                    -webkit-overflow-scrolling: touch;
                }
                h1, h2, h4, h5, h6 {
                    color: ${if (isDark) "#A384FF" else "#312E81"};
                    margin-top: 14px;
                    margin-bottom: 6px;
                }
                h1 {
                    font-size: 1.4em;
                    border-bottom: 1px solid ${if (isDark) "#444" else "#CBD5E1"};
                    padding-bottom: 4px;
                }
                h2 {
                    font-size: 1.25em;
                }
                h3 {
                    color: ${if (isDark) "#90CAF9" else "#0EA5E9"};
                    font-size: 1.15em;
                }
                ul {
                    padding-left: 20px;
                    margin-top: 6px;
                    margin-bottom: 6px;
                }
                ol {
                    padding-left: 20px;
                    margin-top: 6px;
                    margin-bottom: 6px;
                }
                li {
                    margin-bottom: 4px;
                }
                .bullet-item {
                    margin-left: 12px;
                    margin-bottom: 4px;
                }
                .table-container {
                    overflow-x: auto;
                    margin: 16px 0;
                    border-radius: 8px;
                    border: 1px solid ${if (isDark) "#444" else "#CBD5E1"};
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    font-size: 0.95em;
                }
                th {
                    background-color: ${if (isDark) "#3b2a75" else "#EEF2FF"};
                    color: ${if (isDark) "#ffd000" else "#4F46E5"};
                    font-weight: bold;
                    padding: 10px 12px;
                    border: 1px solid ${if (isDark) "#444" else "#CBD5E1"};
                    text-align: left;
                }
                td {
                    padding: 8px 12px;
                    border: 1px solid ${if (isDark) "#444" else "#CBD5E1"};
                    text-align: left;
                }
                tr:nth-child(even) {
                    background-color: ${if (isDark) "rgba(255, 255, 255, 0.05)" else "rgba(0, 0, 0, 0.02)"};
                }
                tr:hover {
                    background-color: ${if (isDark) "rgba(255, 255, 255, 0.1)" else "rgba(0, 0, 0, 0.04)"};
                }
                .code-container {
                    margin: 16px 0;
                    border-radius: 8px;
                    background-color: ${if (isDark) "#1e1e2d" else "#F8FAFC"};
                    border: 1px solid ${if (isDark) "#4E3E85" else "#E2E8F0"};
                    overflow: hidden;
                }
                .code-lang {
                    background-color: ${if (isDark) "#2e2e3f" else "#E2E8F0"};
                    color: ${if (isDark) "#A384FF" else "#4F46E5"};
                    font-size: 0.75em;
                    padding: 4px 12px;
                    font-weight: bold;
                    text-transform: uppercase;
                    letter-spacing: 1px;
                    border-bottom: 1px solid ${if (isDark) "#4E3E85" else "#CBD5E1"};
                }
                pre {
                    margin: 0;
                    padding: 12px;
                    overflow-x: auto;
                }
                code {
                    font-family: "JetBrains Mono", Consolas, Monaco, monospace;
                    font-size: 0.9em;
                    color: ${if (isDark) "#E5C07B" else "#0F172A"};
                }
                .diagram-code {
                    color: ${if (isDark) "#A384FF" else "#4F46E5"} !important;
                    font-weight: bold;
                    display: inline-block;
                    padding: 4px;
                    line-height: 1.4;
                    white-space: pre;
                    font-size: 0.85em;
                    background: ${if (isDark) "radial-gradient(circle, rgba(142,117,255,0.05) 0%, rgba(30,30,45,0) 80%)" else "radial-gradient(circle, rgba(79,70,229,0.05) 0%, rgba(248,250,252,0) 80%)"};
                }
                .inline-code {
                    background-color: ${if (isDark) "rgba(142, 117, 255, 0.15)" else "#EEF2FF"};
                    color: ${if (isDark) "#F8BBD0" else "#4F46E5"};
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-family: monospace;
                    font-size: 0.9em;
                }
                .modern-blockquote {
                    border-left: 4px solid ${if (isDark) "#8E75FF" else "#4F46E5"};
                    background-color: ${if (isDark) "rgba(142, 117, 255, 0.08)" else "#EEF2FF"};
                    margin: 12px 0;
                    padding: 8px 16px;
                    border-radius: 0 8px 8px 0;
                    font-style: italic;
                    color: ${if (isDark) "#E0E0E0" else "#334155"};
                }
                .modern-blockquote:before {
                    color: ${if (isDark) "#8E75FF" else "#4F46E5"};
                    content: "\201C";
                    font-size: 2em;
                    line-height: 0.1em;
                    margin-right: 0.15em;
                    vertical-align: -0.3em;
                }
                .modern-hr {
                    border: 0;
                    height: 1px;
                    background-image: ${if (isDark) "linear-gradient(to right, rgba(142,117,255,0), rgba(142,117,255,0.75), rgba(142,117,255,0))" else "linear-gradient(to right, rgba(79,70,229,0), rgba(79,70,229,0.75), rgba(79,70,229,0))"};
                    margin: 20px 0;
                }
                .mermaid-container {
                    width: 100%;
                    max-width: 100%;
                    margin: 16px 0;
                    border-radius: 8px;
                    background-color: #1e1e2d;
                    border: 1px solid #4E3E85;
                    padding: 16px;
                    box-sizing: border-box;
                    display: flex;
                    justify-content: center;
                    overflow-x: auto;
                    -webkit-overflow-scrolling: touch;
                }
                .mermaid {
                    max-width: 100% !important;
                    height: auto !important;
                }
            </style>
        </head>
        <body>
            <div id="content">$bodyHtml</div>
            <script>
                function updateContentHeight() {
                    var body = document.body;
                    var html = document.documentElement;
                    var height = Math.max(
                        body.scrollHeight, body.offsetHeight,
                        html.clientHeight, html.scrollHeight, html.offsetHeight
                    );
                    if (window.Android && window.Android.resize) {
                        window.Android.resize(height);
                    }
                }

                function tryRenderMath() {
                    if (typeof renderMathInElement !== 'undefined') {
                        renderMathInElement(document.body, {
                            delimiters: [
                                {left: "$$", right: "$$", display: true},
                                {left: "$", right: "$", display: false},
                                {left: "\\(", right: "\\)", display: false},
                                {left: "\\[", right: "\\]", display: true},
                                {left: "\\begin{equation}", right: "\\end{equation}", display: true}
                            ],
                            throwOnError: false
                        });
                    } else {
                        setTimeout(tryRenderMath, 50);
                    }
                }
                
                function tryRenderMermaid() {
                    if (typeof mermaid !== 'undefined') {
                        mermaid.initialize({
                            startOnLoad: false,
                            theme: 'dark',
                            securityLevel: 'loose'
                        });
                        mermaid.parseError = function(err, hash) {
                            console.warn("Mermaid parse error ignored: ", err);
                        };
                        var unprocessed = [];
                        document.querySelectorAll('.mermaid').forEach(function(el) {
                            if (!el.getAttribute('data-processed')) {
                                let html = el.innerHTML;
                                html = html.replace(/<br\s*\/?>/gi, '\n');
                                html = html.replace(/&nbsp;/g, ' ');
                                var temp = document.createElement('div');
                                temp.innerHTML = html;
                                el.textContent = temp.textContent || temp.innerText;
                                el.setAttribute('data-processed', 'true');
                                unprocessed.push(el);
                            }
                        });
                        unprocessed.forEach(function(el) {
                            try {
                                mermaid.init(undefined, el);
                            } catch (err) {
                                console.error("Individual mermaid init failed: ", err);
                                var text = el.textContent || "";
                                el.innerHTML = "<div style='color: #ff6b6b; font-size: 0.85em; padding: 10px; border: 1px dashed #ff6b6b; border-radius: 4px; text-align: left;'>[Unable to render diagram due to a Mermaid syntax error. Raw text below:]<pre style='margin: 8px 0 0 0; font-family: monospace; font-size: 0.9em; color: #ffd000; overflow-x: auto; white-space: pre-wrap;'>" + text + "</pre></div>";
                            }
                        });
                    } else {
                        setTimeout(tryRenderMermaid, 50);
                    }
                }

                function renderAll() {
                    tryRenderMath();
                    tryRenderMermaid();
                    setTimeout(updateContentHeight, 50);
                    setTimeout(updateContentHeight, 200);
                    setTimeout(updateContentHeight, 600);
                }

                function updateContent(newHtml) {
                    var el = document.getElementById('content');
                    if (el) {
                        el.innerHTML = newHtml;
                        renderAll();
                    }
                }
                
                tryRenderMath();
                tryRenderMermaid();
                window.addEventListener('load', renderAll);
                document.addEventListener('DOMContentLoaded', renderAll);
            </script>
        </body>
        </html>
    """.trimIndent()
}

fun formatSingleLineMermaid(rawCode: String): String {
    // If the raw code is already multiline (contains multiple non-empty lines), don't alter its line breaks.
    val lines = rawCode.lines().filter { it.trim().isNotEmpty() }
    if (lines.size > 2) {
        return rawCode
    }
    
    // It is a single line or has very few lines. Let's split it into clean lines!
    var code = rawCode.replace("\n", " ").replace("\r", " ").trim()
    
    // Let's replace multiple spaces with a single space to clean it up
    code = code.replace(Regex("\\s+"), " ")
    
    // We want to insert a newline before certain keywords and statements.
    // Let's first put placeholders or directly insert newlines.
    
    // Insert newline before 'subgraph'
    code = code.replace(Regex("(?i)\\bsubgraph\\b"), "\nsubgraph")
    
    // Insert newline before 'end' (when it is a standalone word)
    code = code.replace(Regex("(?i)\\bend\\b"), "\nend")
    
    // Insert newline before 'style'
    code = code.replace(Regex("(?i)\\bstyle\\b"), "\nstyle")
    
    // Insert newline before 'linkStyle'
    code = code.replace(Regex("(?i)\\blinkStyle\\b"), "\nlinkStyle")
    
    // Insert newline before node shapes: e.g., ID["text"], ID("text"), ID{"text"}, ID(("text")), ID{"text"} etc.
    code = code.replace(Regex("(?<=.)\\b([a-zA-Z0-9_-]+)\\s*([\\[\\({>])")) { matchResult ->
        val id = matchResult.groupValues[1]
        val brace = matchResult.groupValues[2]
        val isKeyword = id.lowercase() in listOf("graph", "flowchart", "subgraph", "style", "end", "linkstyle", "class", "click", "classdef")
        if (isKeyword) {
            matchResult.value
        } else {
            "\n$id$brace"
        }
    }
    
    // Insert newline before connections: e.g., ID -->, ID ---, ID ==>, ID -.->
    code = code.replace(Regex("(?<=.)\\b([a-zA-Z0-9_-]+)\\s*(?=-->|---|==>|-\\.-|-[^-]*->)")) { matchResult ->
        val id = matchResult.groupValues[1]
        val isKeyword = id.lowercase() in listOf("graph", "flowchart", "subgraph", "style", "end", "linkstyle", "class", "click", "classdef")
        if (isKeyword) {
            matchResult.value
        } else {
            "\n$id"
        }
    }
    
    // Clean up empty lines or double newlines
    val formatted = code.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        
    return formatted
}

fun sanitizeMermaid(raw: String): String {
    val formattedRaw = formatSingleLineMermaid(raw)
    var code = formattedRaw.trim()
    
    // Remove triple-backtick wrapping if model put them inside the block
    if (code.startsWith("```")) {
        code = code.replace(Regex("^```[a-zA-Z0-9_-]*\\n"), "")
        code = code.replace(Regex("\\n```$"), "")
    }
    
    val wrapInQuotes = { content: String ->
        val trimmed = content.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed
        } else {
            "\"" + trimmed.replace("\"", "'") + "\""
        }
    }
    
    val lines = code.lines().map { line ->
        var l = line
        val trimmed = l.trim()
        
        // Skip keywords lines entirely or skip processing if it's style, click, etc.
        val skipKeywords = trimmed.startsWith("subgraph") || 
                           trimmed.startsWith("click") || 
                           trimmed.startsWith("style") || 
                           trimmed.startsWith("classDef") || 
                           trimmed.startsWith("class") ||
                           trimmed.startsWith("end") ||
                           trimmed.startsWith("linkStyle")
                           
        if (!skipKeywords) {
            // 1. Stadium shape: ID([Text]) -> ID(["Text"])
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\(\\[\\s*(.*?)\\s*\\]\\)")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                java.util.regex.Matcher.quoteReplacement("""$id([${wrapInQuotes(content)}])""")
            }
            
            // 2. Subroutine shape: ID[[Text]] -> ID[["Text"]]
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\[\\[\\s*(.*?)\\s*\\]\\]")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                java.util.regex.Matcher.quoteReplacement("""$id[[${wrapInQuotes(content)}]]""")
            }
            
            // 3. Cylindrical/database shape: ID[(Text)] -> ID[("Text")]
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\[\\(\\s*(.*?)\\s*\\)\\]")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                java.util.regex.Matcher.quoteReplacement("""$id[(${wrapInQuotes(content)})]""")
            }
            
            // 4. Double circle: ID((Text)) -> ID(("Text"))
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\(\\(\\s*(.*?)\\s*\\)\\)")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                java.util.regex.Matcher.quoteReplacement("""$id((${wrapInQuotes(content)}))""")
            }
            
            // 5. Hexagon shape: ID{{Text}} -> ID{{"Text"}}
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\{\\{\\s*(.*?)\\s*\\}\\}")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                java.util.regex.Matcher.quoteReplacement("""$id{{${wrapInQuotes(content)}}}""")
            }
            
            // 6. Parallelogram / Trapezoid shapes: ID[/Text/], ID[\Text\], ID[/Text\], ID[\Text/]
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\[\\s*([/\\\\])\\s*(.*?)\\s*([/\\\\])\\s*\\]")) { matchResult ->
                val id = matchResult.groupValues[1]
                val delimStart = matchResult.groupValues[2]
                val content = matchResult.groupValues[3]
                val delimEnd = matchResult.groupValues[4]
                java.util.regex.Matcher.quoteReplacement("""$id[$delimStart${wrapInQuotes(content)}$delimEnd]""")
            }
            
            // 7. Rhombus/Decision shape: ID{Text} -> ID{"Text"}
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\{\\s*(.*?)\\s*\\}")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                java.util.regex.Matcher.quoteReplacement("""$id{${wrapInQuotes(content)}}""")
            }
            
            // 8. Asymmetric shape: ID>Text] -> ID>"Text"]
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*>\\s*(.*?)\\s*\\]")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                java.util.regex.Matcher.quoteReplacement("""$id>${wrapInQuotes(content)}]""")
            }
            
            // 9. Standard brackets: ID[Text] -> ID["Text"]
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\[\\s*(.*?)\\s*\\]")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                java.util.regex.Matcher.quoteReplacement("""$id[${wrapInQuotes(content)}]""")
            }
            
            // 10. Simple parentheses: ID(Text) -> ID("Text")
            l = l.replace(Regex("\\b([a-zA-Z0-9_-]+)\\s*\\(\\s*(.*?)\\s*\\)")) { matchResult ->
                val id = matchResult.groupValues[1]
                val content = matchResult.groupValues[2]
                val isKeyword = id == "subgraph" || id == "click" || id == "style" || id == "classDef" || id == "class" || id == "end" || id == "linkStyle"
                if (isKeyword) {
                    matchResult.value
                } else {
                    java.util.regex.Matcher.quoteReplacement("""$id(${wrapInQuotes(content)})""")
                }
            }
        } else {
            // Check if it's a subgraph line with unquoted title containing spaces
            // E.g. subgraph Venn Diagram for A U B
            l = l.replace(Regex("(?i)\\bsubgraph\\s+([^\"\\s\\[\\(\\{\\n]+(?:\\s+[^\"\\s\\[\\(\\{\\n]+)+)")) { matchResult ->
                val content = matchResult.groupValues[1]
                "subgraph \"$content\""
            }
        }
        
        l
    }
    
    return lines.joinToString("\n")
}

fun buildHtmlCodeBlock(language: String, lines: List<String>): String {
    val rawCodeContent = lines.joinToString("\n")
    
    val decodedContent = rawCodeContent
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    val isBase64 = language.trim().startsWith("[Base64]") ||
                   language.lowercase().contains("base64") ||
                   decodedContent.trim().startsWith("[Base64]")
                   
    if (isBase64) {
        var cleanB64 = decodedContent.trim().removePrefix("[Base64]").trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
        if (cleanB64.startsWith("[Base64]")) {
            cleanB64 = cleanB64.substring("[Base64]".length).trim()
        }
        val mime = if (cleanB64.startsWith("iVBORw0KGgo")) "image/png" else "image/jpeg"
        return """
            <div class="base64-diagram-container" style="width:100%; max-width:100%; margin:20px 0; border-radius:12px; background-color:#1e1e2d; border:1.5px solid #8E75FF; padding:15px; box-sizing:border-box; display:flex; flex-direction:column; justify-content:center; align-items:center; overflow-x:auto; box-shadow: 0 4px 15px rgba(0,0,0,0.3);">
                <img src="data:$mime;base64,$cleanB64" style="max-width:100%; max-height:450px; border-radius:8px; object-fit:contain;" alt="Base64 Diagram Image" />
            </div>
        """.trimIndent()
    }

    val isMermaid = language.lowercase().contains("mermaid") ||
                    decodedContent.trim().startsWith("graph") ||
                    decodedContent.trim().startsWith("flowchart") ||
                    decodedContent.trim().startsWith("sequenceDiagram") ||
                    decodedContent.trim().startsWith("classDiagram") ||
                    decodedContent.trim().startsWith("stateDiagram") ||
                    decodedContent.trim().startsWith("erDiagram") ||
                    decodedContent.trim().startsWith("gantt") ||
                    decodedContent.trim().startsWith("pie") ||
                    decodedContent.trim().startsWith("gitGraph") ||
                    decodedContent.trim().startsWith("mindmap") ||
                    decodedContent.trim().startsWith("timeline")

    if (isMermaid) {
        val sanitized = sanitizeMermaid(decodedContent)
        return """
            <div class="mermaid-container">
                <div class="mermaid">
                    $sanitized
                </div>
            </div>
        """.trimIndent()
    }

    // If it contains [diagram] or is marked as a diagram language, simply avoid / ignore it
    if (decodedContent.contains("[diagram]") || (language.lowercase().contains("diagram") && !language.lowercase().contains("mermaid"))) {
        return ""
    }

    val isHtmlDiagram = language.lowercase().contains("svg") ||
                        language.lowercase().contains("xml") ||
                        decodedContent.trim().startsWith("<svg")

    if (isHtmlDiagram) {
        return """
            <div class="html-diagram-container" style="width:100%; max-width:100%; margin:20px 0; border-radius:12px; background-color:#1A1348; border:1.5px solid #8E75FF; padding:20px; box-sizing:border-box; display:flex; justify-content:center; align-items:center; overflow-x:auto; box-shadow: 0 4px 15px rgba(0,0,0,0.3);">
                <div style="width:100%; text-align:center;">
                    $decodedContent
                </div>
            </div>
        """.trimIndent()
    }
    
    val langLabel = if (language.isNotEmpty()) {
        "<div class='code-lang'>$language</div>"
    } else ""
    
    val codeClass = "standard-code"
    
    return """
        <div class='code-container'>
            $langLabel
            <pre><code class='$codeClass'>$rawCodeContent</code></pre>
        </div>
    """.trimIndent()
}

fun parseCodeBlocksToHtml(text: String): String {
    val lines = text.split("\n")
    val result = java.lang.StringBuilder()
    var inCodeBlock = false
    var codeBlockLanguage = ""
    val codeBlockLines = mutableListOf<String>()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (!inCodeBlock) {
                inCodeBlock = true
                codeBlockLanguage = line.trim().substring(3).trim()
                codeBlockLines.clear()
            } else {
                val htmlCode = buildHtmlCodeBlock(codeBlockLanguage, codeBlockLines)
                result.append(htmlCode).append("\n")
                inCodeBlock = false
            }
        } else {
            if (inCodeBlock) {
                codeBlockLines.add(line)
            } else {
                result.append(line).append("\n")
            }
        }
    }

    if (inCodeBlock && codeBlockLines.isNotEmpty()) {
        val htmlCode = buildHtmlCodeBlock(codeBlockLanguage, codeBlockLines)
        result.append(htmlCode).append("\n")
    }

    return result.toString()
}

fun splitTableLine(line: String): List<String> {
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

fun buildHtmlTable(lines: List<String>): String {
    if (lines.isEmpty()) return ""
    val html = java.lang.StringBuilder()
    html.append("<div class='table-container'><table>")
    
    var headerParsed = false
    
    for (line in lines) {
        val trimmedLine = line.trim()
        val rawCells = splitTableLine(trimmedLine)
        val cells = if (trimmedLine.startsWith("|") && trimmedLine.endsWith("|") && rawCells.size >= 2) {
            rawCells.subList(1, rawCells.size - 1)
        } else {
            rawCells
        }
        
        val isSeparator = cells.all { cell -> cell.all { it == '-' || it == ':' || it == ' ' } && cell.isNotEmpty() }
        if (isSeparator) {
            continue
        }
        
        html.append("<tr>")
        for (cell in cells) {
            if (!headerParsed) {
                html.append("<th>").append(cell).append("</th>")
            } else {
                html.append("<td>").append(cell).append("</td>")
            }
        }
        html.append("</tr>")
        headerParsed = true
    }
    
    html.append("</table></div>")
    return html.toString()
}

fun parseMarkdownTablesToHtml(text: String): String {
    val lines = text.split("\n")
    val result = java.lang.StringBuilder()
    var inTable = false
    val tableLines = mutableListOf<String>()

    for (line in lines) {
        val trimmed = line.trim()
        val isTableLine = trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 1
        
        if (isTableLine) {
            if (!inTable) {
                inTable = true
            }
            tableLines.add(trimmed)
        } else {
            if (inTable) {
                val htmlTable = buildHtmlTable(tableLines)
                result.append(htmlTable).append("\n")
                tableLines.clear()
                inTable = false
            }
            result.append(line).append("\n")
        }
    }
    
    if (inTable && tableLines.isNotEmpty()) {
        val htmlTable = buildHtmlTable(tableLines)
        result.append(htmlTable).append("\n")
    }

    return result.toString()
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

fun convertMarkdownToHtml(text: String): String {
    val wrappedText = autoWrapRawMermaid(text)
    var formattedHtml = wrappedText.replace("\r", "")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    formattedHtml = parseCodeBlocksToHtml(formattedHtml)
    formattedHtml = parseMarkdownTablesToHtml(formattedHtml)

    val boldRegex = "\\*\\*(.*?)\\*\\*".toRegex()
    formattedHtml = boldRegex.replace(formattedHtml) { match ->
        "<b>${match.groupValues[1]}</b>"
    }

    val italicRegex = "\\*(.*?)\\*".toRegex()
    formattedHtml = italicRegex.replace(formattedHtml) { match ->
        "<i>${match.groupValues[1]}</i>"
    }

    val inlineCodeRegex = "`([^`]+)`".toRegex()
    formattedHtml = inlineCodeRegex.replace(formattedHtml) { match ->
        "<code class='inline-code'>${match.groupValues[1]}</code>"
    }

    val h6Regex = "(?m)^[ \t]*######[ \t]+(.*)$".toRegex()
    formattedHtml = h6Regex.replace(formattedHtml) { match ->
        "<h6>${match.groupValues[1]}</h6>"
    }
    val h5Regex = "(?m)^[ \t]*#####[ \t]+(.*)$".toRegex()
    formattedHtml = h5Regex.replace(formattedHtml) { match ->
        "<h5>${match.groupValues[1]}</h5>"
    }
    val h4Regex = "(?m)^[ \t]*####[ \t]+(.*)$".toRegex()
    formattedHtml = h4Regex.replace(formattedHtml) { match ->
        "<h4>${match.groupValues[1]}</h4>"
    }
    val h3Regex = "(?m)^[ \t]*###[ \t]+(.*)$".toRegex()
    formattedHtml = h3Regex.replace(formattedHtml) { match ->
        "<h3>${match.groupValues[1]}</h3>"
    }
    val h2Regex = "(?m)^[ \t]*##[ \t]+(.*)$".toRegex()
    formattedHtml = h2Regex.replace(formattedHtml) { match ->
        "<h2>${match.groupValues[1]}</h2>"
    }
    val h1Regex = "(?m)^[ \t]*#[ \t]+(.*)$".toRegex()
    formattedHtml = h1Regex.replace(formattedHtml) { match ->
        "<h1>${match.groupValues[1]}</h1>"
    }

    val blockquoteRegex = "(?m)^&gt;[ \t]+(.*)$".toRegex()
    formattedHtml = blockquoteRegex.replace(formattedHtml) { match ->
        "<blockquote class='modern-blockquote'>${match.groupValues[1]}</blockquote>"
    }

    val hrRegex = "(?m)^[ \t]*[\\-*\\_]{3,}[ \t]*$".toRegex()
    formattedHtml = hrRegex.replace(formattedHtml, "<hr class='modern-hr'>")

    val bulletRegex = "(?m)^[*\\-][ \t]+(.*)$".toRegex()
    formattedHtml = bulletRegex.replace(formattedHtml) { match ->
        "<div class='bullet-item'>&bull; ${match.groupValues[1]}</div>"
    }

    formattedHtml = formattedHtml.replace("\r\n", "<br>").replace("\n", "<br>")

    return formattedHtml
}

// ==================== INTERACTIVE MAND MAP CANVAS VISUALIZER ====================
@Composable
fun InteractiveMindMapView(
    rootNode: MindMapNode,
    title: String,
    onDownloadHtml: () -> Unit
) {
    var stateNode by remember(rootNode) { mutableStateOf(rootNode) }
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var selectedNodeTopic by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pinch to zoom, drag to pan. Tap nodes to toggle branches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                    selectedNodeTopic = null
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset viewport")
                }

                Button(
                    onClick = onDownloadHtml,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download HTML")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("HTML", fontSize = 12.sp)
                }
            }
        }

        if (selectedNodeTopic != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Selected details: ${selectedNodeTopic}", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selectedNodeTopic = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close description", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 3.0f)
                        panOffset += pan
                    }
                }
                .pointerInput(stateNode, zoomScale, panOffset) {
                    detectTapGestures { tapLoc ->
                        val paint = android.graphics.Paint().apply {
                            textSize = (10f * zoomScale).coerceIn(12f, 24f)
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val tappedNode = findTappedNode(stateNode, tapLoc.x, tapLoc.y, zoomScale, paint)
                        if (tappedNode != null) {
                            tappedNode.isExpanded = !tappedNode.isExpanded
                            selectedNodeTopic = tappedNode.topic
                            val freshNode = stateNode.copy()
                            syncExpandState(stateNode, freshNode)
                            stateNode = freshNode
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f + panOffset.x
                val cy = size.height / 2f + panOffset.y

                stateNode.x = cx
                stateNode.y = cy

                layoutMindMap(stateNode, cx, cy, zoomScale)
                drawNodeLinks(this, stateNode, zoomScale)
                drawNodeBubbles(this, stateNode, zoomScale, selectedNodeTopic)
            }
        }
    }
}

fun syncExpandState(source: MindMapNode, dest: MindMapNode) {
    dest.isExpanded = source.isExpanded
    source.children.indices.forEach { idx ->
        if (idx < dest.children.size) {
            syncExpandState(source.children[idx], dest.children[idx])
        }
    }
}

fun findTappedNode(
    node: MindMapNode,
    tapX: Float,
    tapY: Float,
    scale: Float,
    paint: android.graphics.Paint
): MindMapNode? {
    val rawTopic = node.topic
    val displayTopic = if (rawTopic.length > 20) rawTopic.take(18) + ".." else rawTopic
    val textWidth = paint.measureText(displayTopic)
    val width = (textWidth + 24f * scale).coerceAtLeast(64f * scale)
    val height = 32f * scale

    val left = node.x - width / 2
    val right = node.x + width / 2
    val top = node.y - height / 2
    val bottom = node.y + height / 2

    if (tapX in left..right && tapY in top..bottom) {
        return node
    }

    if (node.isExpanded) {
        for (child in node.children) {
            val tapped = findTappedNode(child, tapX, tapY, scale, paint)
            if (tapped != null) return tapped
        }
    }
    return null
}

data class PlacedNode(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

fun estimateNodeWidth(topic: String, scale: Float): Float {
    val displayTopic = if (topic.length > 20) topic.take(18) + ".." else topic
    val charWidth = 7f * scale
    return (displayTopic.length * charWidth + 24f * scale).coerceAtLeast(64f * scale)
}

fun estimateNodeHeight(scale: Float): Float {
    return 32f * scale
}

fun isOverlapping(
    x1: Float, y1: Float, w1: Float, h1: Float,
    x2: Float, y2: Float, w2: Float, h2: Float,
    padding: Float
): Boolean {
    val halfW1 = w1 / 2 + padding
    val halfH1 = h1 / 2 + padding
    val halfW2 = w2 / 2 + padding
    val halfH2 = h2 / 2 + padding

    val left1 = x1 - halfW1
    val right1 = x1 + halfW1
    val top1 = y1 - halfH1
    val bottom1 = y1 + halfH1

    val left2 = x2 - halfW2
    val right2 = x2 + halfW2
    val top2 = y2 - halfH2
    val bottom2 = y2 + halfH2

    return !(right1 < left2 || left1 > right2 || bottom1 < top2 || top1 > bottom2)
}

fun layoutMindMap(
    node: MindMapNode,
    cx: Float,
    cy: Float,
    scale: Float
) {
    val positioned = mutableListOf<PlacedNode>()
    
    // Root position
    val rootWidth = estimateNodeWidth(node.topic, scale)
    val rootHeight = estimateNodeHeight(scale)
    positioned.add(PlacedNode(cx, cy, rootWidth, rootHeight))

    fun arrange(parent: MindMapNode, angleCenter: Float?, angleSpread: Float, depth: Int) {
        if (!parent.isExpanded || parent.children.isEmpty()) return
        val count = parent.children.size

        if (depth == 1) {
            val baseRadius = (200f + count * 15f) * scale
            for (i in 0 until count) {
                val child = parent.children[i]
                val angle = (2 * Math.PI * i / count).toFloat()
                
                val childWidth = estimateNodeWidth(child.topic, scale)
                val childHeight = estimateNodeHeight(scale)
                
                var currentDistance = baseRadius
                var foundSafePos = false
                var attempts = 0
                val maxAttempts = 35
                val stepSize = 35f * scale
                
                while (!foundSafePos && attempts < maxAttempts) {
                    val tx = parent.x + currentDistance * kotlin.math.cos(angle)
                    val ty = parent.y + currentDistance * kotlin.math.sin(angle)
                    
                    val hasOverlap = positioned.any { placed ->
                        isOverlapping(tx, ty, childWidth, childHeight, placed.x, placed.y, placed.width, placed.height, 25f * scale)
                    }
                    
                    if (!hasOverlap) {
                        child.x = tx
                        child.y = ty
                        foundSafePos = true
                    } else {
                        currentDistance += stepSize
                        attempts++
                    }
                }
                
                if (!foundSafePos) {
                    child.x = parent.x + currentDistance * kotlin.math.cos(angle)
                    child.y = parent.y + currentDistance * kotlin.math.sin(angle)
                }
                
                positioned.add(PlacedNode(child.x, child.y, childWidth, childHeight))
                arrange(child, angle, (Math.PI / 3.2).toFloat(), depth + 1)
            }
        } else {
            val baseAngle = angleCenter ?: 0f
            val startAngle = baseAngle - angleSpread / 2
            val step = if (count > 1) angleSpread / (count - 1) else 0f
            val baseRadius = (160f + count * 10f) * scale
            
            for (i in 0 until count) {
                val child = parent.children[i]
                val angle = startAngle + step * i
                
                val childWidth = estimateNodeWidth(child.topic, scale)
                val childHeight = estimateNodeHeight(scale)
                
                var currentDistance = baseRadius
                var foundSafePos = false
                var attempts = 0
                val maxAttempts = 35
                val stepSize = 35f * scale
                
                while (!foundSafePos && attempts < maxAttempts) {
                    val tx = parent.x + currentDistance * kotlin.math.cos(angle)
                    val ty = parent.y + currentDistance * kotlin.math.sin(angle)
                    
                    val hasOverlap = positioned.any { placed ->
                        isOverlapping(tx, ty, childWidth, childHeight, placed.x, placed.y, placed.width, placed.height, 20f * scale)
                    }
                    
                    if (!hasOverlap) {
                        child.x = tx
                        child.y = ty
                        foundSafePos = true
                    } else {
                        currentDistance += stepSize
                        attempts++
                    }
                }
                
                if (!foundSafePos) {
                    child.x = parent.x + currentDistance * kotlin.math.cos(angle)
                    child.y = parent.y + currentDistance * kotlin.math.sin(angle)
                }
                
                positioned.add(PlacedNode(child.x, child.y, childWidth, childHeight))
                arrange(child, angle, (angleSpread * 0.55f), depth + 1)
            }
        }
    }

    arrange(node, null, (2 * Math.PI).toFloat(), 1)
}

fun drawNodeLinks(
    drawScope: DrawScope,
    node: MindMapNode,
    scale: Float
) {
    if (!node.isExpanded) return
    node.children.forEach { child ->
        drawScope.drawLine(
            color = Color(0xFF8E75FF),
            start = Offset(node.x, node.y),
            end = Offset(child.x, child.y),
            strokeWidth = 3f * scale,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * scale, 5f * scale), 0f)
        )
        drawNodeLinks(drawScope, child, scale)
    }
}

fun drawNodeBubbles(
    drawScope: DrawScope,
    node: MindMapNode,
    scale: Float,
    selectedTopic: String?
) {
    val isSelected = node.topic == selectedTopic
    val primaryColor = if (isSelected) 0xFFE91E63.toInt() else 0xFF8E75FF.toInt()
    
    val bgPaint = android.graphics.Paint().apply {
        color = primaryColor
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(8f * scale, 0f, 4f * scale, 0x55000000)
    }
    
    val borderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f * scale
        isAntiAlias = true
    }

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = (10f * scale).coerceIn(12f, 24f)
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    val rawTopic = node.topic
    val displayTopic = if (rawTopic.length > 20) rawTopic.take(18) + ".." else rawTopic

    val textWidth = textPaint.measureText(displayTopic)
    val width = (textWidth + 24f * scale).coerceAtLeast(64f * scale)
    val height = 32f * scale
    
    val rect = android.graphics.RectF(
        node.x - width / 2,
        node.y - height / 2,
        node.x + width / 2,
        node.y + height / 2
    )
    
    drawScope.drawContext.canvas.nativeCanvas.drawRoundRect(rect, 16f * scale, 16f * scale, bgPaint)
    drawScope.drawContext.canvas.nativeCanvas.drawRoundRect(rect, 16f * scale, 16f * scale, borderPaint)
    
    val textY = node.y - ((textPaint.descent() + textPaint.ascent()) / 2)
    drawScope.drawContext.canvas.nativeCanvas.drawText(displayTopic, node.x, textY, textPaint)

    if (node.isExpanded) {
        node.children.forEach { child ->
            drawNodeBubbles(drawScope, child, scale, selectedTopic)
        }
    }
}

// ==================== SUMMARIZER & FORMULA GENERATOR SECTION ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarizerSection(viewModel: StudyMateViewModel) {
    val isAILoading by viewModel.isAILoading.collectAsStateWithLifecycle()
    val apiErrorFeedback by viewModel.apiErrorFeedback.collectAsStateWithLifecycle()
    val subjectsList by viewModel.subjectsList.collectAsStateWithLifecycle()
    val chaptersMap by viewModel.chaptersMap.collectAsStateWithLifecycle()

    val activeMode by viewModel.summarizerActiveMode.collectAsStateWithLifecycle()

    var summaryTitle by remember { mutableStateOf("") }
    var summaryInputText by remember { mutableStateOf("") }
    
    var lengthMode by remember { mutableStateOf("STANDARD") }
    var formatMode by remember { mutableStateOf("Q&A") }

    var selectedSubject by remember { mutableStateOf("") }
    var selectedChapter by remember { mutableStateOf("") }

    var attachedPdfPath by remember { mutableStateOf<String?>(null) }
    var attachedPdfName by remember { mutableStateOf<String?>(null) }

    val attachedFileForGen by viewModel.attachedFileForGeneration.collectAsStateWithLifecycle()

    LaunchedEffect(attachedFileForGen) {
        val f = attachedFileForGen
        if (f != null && f.fileType == "PDF") {
            attachedPdfPath = f.filePath
            attachedPdfName = f.title
            viewModel.attachedFileForGeneration.value = null
        }
    }

    val context = LocalContext.current
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Document.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    fileName = cursor.getString(nameIdx)
                }
            }
            val localPath = viewModel.copyUriToLocalStorage(uri, fileName, "PDF")
            attachedPdfPath = localPath
            attachedPdfName = fileName
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Selector Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { viewModel.summarizerActiveMode.value = "SUMMARIZER" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeMode == "SUMMARIZER") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (activeMode == "SUMMARIZER") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Notes Summarizer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.summarizerActiveMode.value = "FORMULA" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeMode == "FORMULA") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (activeMode == "FORMULA") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Formula Sheet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Header Description card based on mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeMode == "SUMMARIZER") {
                    Text(
                        text = "AI Study Notes Summarizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Instantly condense complex syllabus notes, textbook PDFs, or lecture slide text into concise flash briefs, terminologies, or detailed Q&A grids.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = "AI Formula & Key Points Extractor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Scan or paste text/textbook PDFs to construct an academic Formula Sheet containing scientific formulas, chemical equations, constants, and high-yield key review points.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (isAILoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        val loadingStr = if (activeMode == "SUMMARIZER") {
                            "Gemini is reading notes. Assembling summary page..."
                        } else {
                            "Gemini is analyzing science material. Constructing formula sheet..."
                        }
                        Text(loadingStr, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = { viewModel.cancelActiveAIGeneration() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.End).testTag("cancel_summarizer_generation")
                    ) {
                        Text("Cancel Generator")
                    }
                }
            }
        }

        if (apiErrorFeedback != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = apiErrorFeedback!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
            value = summaryTitle,
            onValueChange = { summaryTitle = it },
            label = { Text(if (activeMode == "SUMMARIZER") "Summary File Title" else "Formula Sheet Title") },
            placeholder = { Text(if (activeMode == "SUMMARIZER") "e.g. Photosynthesis Quick Summary" else "e.g. Physics 101 Midterm Formulas") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("summary_title_input")
        )

        OutlinedTextField(
            value = summaryInputText,
            onValueChange = { summaryInputText = it },
            label = { Text(if (activeMode == "SUMMARIZER") "Paste Study Notes / Text" else "Paste Formula Materials / Textbook Pages") },
            placeholder = { Text(if (activeMode == "SUMMARIZER") "Paste full lecture slides or chapters content here..." else "Paste math chapters or physics notes here to parse laws and formulas...") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("summary_text_input")
        )

        if (attachedPdfName != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attached PDF", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(attachedPdfName!!, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                    }
                    IconButton(onClick = {
                        attachedPdfName = null
                        attachedPdfPath = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear pdf")
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth().testTag("attach_pdf_summary_btn")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "Attach notes pdf")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Optional: Import Textbook PDF note", fontSize = 12.sp)
            }
        }

        // Render option chips only for Notes Summarizer
        if (activeMode == "SUMMARIZER") {
            HorizontalDivider()

            Text("Select Length Level:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CONCISE" to "Short (<150w)", "STANDARD" to "Balanced", "DETAILED" to "Exhaustive").forEach { (valStr, labelStr) ->
                    FilterChip(
                        selected = lengthMode == valStr,
                        onClick = { lengthMode = valStr },
                        label = { Text(labelStr, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Select Structure Layout Style:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Q&A" to "Question-Wise", "DEFINITION" to "Term-Wise").forEach { (valStr, labelStr) ->
                    FilterChip(
                        selected = formatMode == valStr,
                        onClick = { formatMode = valStr },
                        label = { Text(labelStr, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HorizontalDivider()

        Text("Save Document To Directory Folder:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        
        if (subjectsList.isNotEmpty()) {
            var expandedSub by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { expandedSub = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedSubject.isBlank()) "Select Subject Folder (Optional)" else "Subject: $selectedSubject")
                }
                DropdownMenu(expanded = expandedSub, onDismissRequest = { expandedSub = false }) {
                    DropdownMenuItem(
                        text = { Text("None (Loose in Notebook)") },
                        onClick = {
                            selectedSubject = ""
                            selectedChapter = ""
                            expandedSub = false
                        }
                    )
                    subjectsList.forEach { sub ->
                        DropdownMenuItem(
                            text = { Text(sub) },
                            onClick = {
                                selectedSubject = sub
                                selectedChapter = ""
                                expandedSub = false
                            }
                        )
                    }
                }
            }
        }

        if (selectedSubject.isNotBlank() && (chaptersMap[selectedSubject]?.isNotEmpty() == true)) {
            var expandedCh by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { expandedCh = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedChapter.isBlank()) "Select Chapter Folders (Optional)" else "Chapter: $selectedChapter")
                }
                DropdownMenu(expanded = expandedCh, onDismissRequest = { expandedCh = false }) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            selectedChapter = ""
                            expandedCh = false
                        }
                    )
                    chaptersMap[selectedSubject]?.forEach { ch ->
                        DropdownMenuItem(
                            text = { Text(ch) },
                            onClick = {
                                selectedChapter = ch
                                expandedCh = false
                            }
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                if (summaryTitle.isBlank()) {
                    summaryTitle = if (activeMode == "SUMMARIZER") {
                        "AI Summary - " + SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                    } else {
                        "Formula Sheet - " + SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                    }
                }
                
                if (activeMode == "SUMMARIZER") {
                    viewModel.generateSummary(
                        title = summaryTitle,
                        pastedText = summaryInputText,
                        pdfPath = attachedPdfPath,
                        lengthMode = lengthMode,
                        structureMode = formatMode,
                        subject = selectedSubject,
                        chapter = selectedChapter
                    )
                    summaryInputText = ""
                    attachedPdfPath = null
                    attachedPdfName = null
                    android.widget.Toast.makeText(context, "Assembling summary page with Gemini...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.generateFormulaSheet(
                        title = summaryTitle,
                        pastedText = summaryInputText,
                        pdfPath = attachedPdfPath,
                        subject = selectedSubject,
                        chapter = selectedChapter
                    )
                    summaryInputText = ""
                    attachedPdfPath = null
                    attachedPdfName = null
                    android.widget.Toast.makeText(context, "Extracting formulas with Gemini...", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("run_summarizer_action_btn"),
            enabled = !isAILoading && (summaryInputText.isNotBlank() || attachedPdfPath != null)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "Run summary")
            Spacer(modifier = Modifier.width(8.dp))
            val btnLabel = if (activeMode == "SUMMARIZER") "Generate & Save AI Summary" else "Extract & Save Formula Sheet"
            Text(btnLabel)
        }
    }
}

// ==================== MAND MAP TAB SECTION ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindMapSection(viewModel: StudyMateViewModel) {
    val isAILoading by viewModel.isAILoading.collectAsStateWithLifecycle()
    val apiErrorFeedback by viewModel.apiErrorFeedback.collectAsStateWithLifecycle()
    val subjectsList by viewModel.subjectsList.collectAsStateWithLifecycle()
    val chaptersMap by viewModel.chaptersMap.collectAsStateWithLifecycle()

    var mapTitle by remember { mutableStateOf("") }
    var mapInputText by remember { mutableStateOf("") }

    var selectedSubject by remember { mutableStateOf("") }
    var selectedChapter by remember { mutableStateOf("") }

    var attachedPdfPath by remember { mutableStateOf<String?>(null) }
    var attachedPdfName by remember { mutableStateOf<String?>(null) }

    val attachedFileForGen by viewModel.attachedFileForGeneration.collectAsStateWithLifecycle()

    LaunchedEffect(attachedFileForGen) {
        val f = attachedFileForGen
        if (f != null) {
            if (f.fileType == "PDF") {
                attachedPdfPath = f.filePath
                attachedPdfName = f.title
            } else if (f.fileType == "TEXT") {
                mapInputText = f.content
                mapTitle = f.title
            }
            viewModel.attachedFileForGeneration.value = null
        }
    }

    val context = LocalContext.current
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Document.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    fileName = cursor.getString(nameIdx)
                }
            }
            val localPath = viewModel.copyUriToLocalStorage(uri, fileName, "PDF")
            attachedPdfPath = localPath
            attachedPdfName = fileName
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AI Mind Map Generator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Instantly visualize lectures, textbook chapters and syllabus materials as an interactive, drillable connection tree. Export your mind map tree directly as HTML or print vector PDF diagrams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        if (isAILoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Gemini is brainstorming concepts & building tree structures...", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = { viewModel.cancelActiveAIGeneration() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.End).testTag("cancel_mindmap_generation")
                    ) {
                        Text("Cancel Generator")
                    }
                }
            }
        }

        if (apiErrorFeedback != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = apiErrorFeedback!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
            value = mapTitle,
            onValueChange = { mapTitle = it },
            label = { Text("Mind Map Name") },
            placeholder = { Text("e.g. Physics Laws Map Tree") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("mindmap_title_input")
        )

        OutlinedTextField(
            value = mapInputText,
            onValueChange = { mapInputText = it },
            label = { Text("Paste Notes context to parse") },
            placeholder = { Text("Enter textbook definitions, chapter contents, or class notes context...") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("mindmap_text_input")
        )

        if (attachedPdfName != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attached PDF", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(attachedPdfName!!, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                    }
                    IconButton(onClick = {
                        attachedPdfName = null
                        attachedPdfPath = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear pdf")
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth().testTag("attach_pdf_mindmap_btn")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "Attach notes pdf")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Optional: Import Textbook PDF note", fontSize = 12.sp)
            }
        }

        HorizontalDivider()

        Text("Save Mind Map To Subject Directory:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        
        if (subjectsList.isNotEmpty()) {
            var expandedSub by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { expandedSub = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedSubject.isBlank()) "Select Subject Folder (Optional)" else "Subject: $selectedSubject")
                }
                DropdownMenu(expanded = expandedSub, onDismissRequest = { expandedSub = false }) {
                    DropdownMenuItem(
                        text = { Text("None (Loose in Notebook)") },
                        onClick = {
                            selectedSubject = ""
                            selectedChapter = ""
                            expandedSub = false
                        }
                    )
                    subjectsList.forEach { sub ->
                        DropdownMenuItem(
                            text = { Text(sub) },
                            onClick = {
                                selectedSubject = sub
                                selectedChapter = ""
                                expandedSub = false
                            }
                        )
                    }
                }
            }
        }

        if (selectedSubject.isNotBlank() && (chaptersMap[selectedSubject]?.isNotEmpty() == true)) {
            var expandedCh by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { expandedCh = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedChapter.isBlank()) "Select Chapter Folders (Optional)" else "Chapter: $selectedChapter")
                }
                DropdownMenu(expanded = expandedCh, onDismissRequest = { expandedCh = false }) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            selectedChapter = ""
                            expandedCh = false
                        }
                    )
                    chaptersMap[selectedSubject]?.forEach { ch ->
                        DropdownMenuItem(
                            text = { Text(ch) },
                            onClick = {
                                selectedChapter = ch
                                expandedCh = false
                            }
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                if (mapTitle.isBlank()) {
                    mapTitle = "AI Mind Map - " + SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                }
                viewModel.generateMindMap(
                    title = mapTitle,
                    pastedText = mapInputText,
                    pdfPath = attachedPdfPath,
                    subject = selectedSubject,
                    chapter = selectedChapter
                )
                mapInputText = ""
                attachedPdfPath = null
                attachedPdfName = null
                android.widget.Toast.makeText(context, "Formulating mind map with Gemini...", android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("run_mindmap_action_btn"),
            enabled = !isAILoading && (mapInputText.isNotBlank() || attachedPdfPath != null)
        ) {
            Icon(Icons.Default.Hub, contentDescription = "Generate Map")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate & Store Connection Mind Map")
        }
    }
}

@Composable
fun PdfPageItem(pdfPath: String, pageIndex: Int, totalPages: Int, isInverted: Boolean = false) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var renderError by remember { mutableStateOf(false) }

    LaunchedEffect(pdfPath, pageIndex) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(pdfPath)
                if (file.exists()) {
                    val input = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    try {
                        val renderer = android.graphics.pdf.PdfRenderer(input)
                        try {
                            if (pageIndex < renderer.pageCount) {
                                val page = renderer.openPage(pageIndex)
                                try {
                                    val bmp = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmap = bmp
                                } finally {
                                    page.close()
                                }
                            }
                        } finally {
                            renderer.close()
                        }
                    } finally {
                        input.close()
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfRenderer", "Error rendering page $pageIndex", e)
                renderError = true
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Page ${pageIndex + 1} of $totalPages", color = if (isInverted) Color.Gray else Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isInverted) Color(0xFF1E2124) else Color.White)
        ) {
            if (bitmap != null) {
                val colorFilter = if (isInverted) {
                    val invertMatrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                        -1.0f,  0.0f,  0.0f, 0.0f, 255.0f,
                         0.0f, -1.0f,  0.0f, 0.0f, 255.0f,
                         0.0f,  0.0f, -1.0f, 0.0f, 255.0f,
                         0.0f,  0.0f,  0.0f, 1.0f,   0.0f
                    ))
                    androidx.compose.ui.graphics.ColorFilter.colorMatrix(invertMatrix)
                } else {
                    null
                }
                AsyncImage(
                    model = bitmap,
                    contentDescription = "Page ${pageIndex + 1}",
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (renderError) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("Error rendering page ${pageIndex + 1}", color = Color.Red, fontSize = 12.sp)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// Reusable adaptive shimmer modifier for loading states
@Composable
fun Modifier.shimmer(): Modifier {
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer_transition")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * (size.width.toFloat().coerceAtLeast(1f)),
        targetValue = 2 * (size.width.toFloat().coerceAtLeast(1f)),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    return this.background(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = androidx.compose.ui.geometry.Offset(startOffsetX, 0f),
            end = androidx.compose.ui.geometry.Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

@Composable
fun StaggeredEntrance(
    index: Int,
    content: @Composable () -> Unit
) {
    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        visible.value = true
    }
    AnimatedVisibility(
        visible = visible.value,
        enter = fadeIn(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        ) + slideInVertically(
            initialOffsetY = { 60 },
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        )
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySection(viewModel: StudyMateViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubSection by remember { mutableStateOf("SEARCH") } // "SEARCH" or "SAVED"
    val searchState by viewModel.dictionarySearchState.collectAsStateWithLifecycle()
    val notesList by viewModel.notes.collectAsStateWithLifecycle()
    
    val savedWords = remember(notesList) {
        notesList.filter { it.fileType == "DICTIONARY" }
    }
    
    val context = LocalContext.current
    val mediaPlayer = remember { android.media.MediaPlayer() }
    
    // Clean up MediaPlayer when section is disposed
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Tab Selector for Search vs Saved Words
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedFilterChip(
                selected = selectedSubSection == "SEARCH",
                onClick = { selectedSubSection = "SEARCH" },
                label = { Text("Word Lookup") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("dict_search_tab_chip")
            )
            ElevatedFilterChip(
                selected = selectedSubSection == "SAVED",
                onClick = { selectedSubSection = "SAVED" },
                label = { Text("Saved Words (${savedWords.size})") },
                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("dict_saved_tab_chip")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedSubSection == "SEARCH") {
            // Search Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Enter a word to lookup...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dict_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            viewModel.searchDictionary(searchQuery)
                        }
                    )
                )

                Button(
                    onClick = { viewModel.searchDictionary(searchQuery) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp).testTag("dict_search_button")
                ) {
                    Text("Search")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Results State Renderer
            when (val state = searchState) {
                is DictionarySearchState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                text = "Search any word to see definitions, phonetics, and speech components.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
                is DictionarySearchState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Querying academic dictionary...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                is DictionarySearchState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                is DictionarySearchState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.entries) { entry ->
                            DictionaryEntryCard(
                                entry = entry,
                                onSave = { viewModel.saveWordToDictionaryNotes(entry.word, state.entries) },
                                isSaved = savedWords.any { it.title.equals(entry.word, ignoreCase = true) },
                                onPlayAudio = { audioUrl ->
                                    var cleanedAudio = audioUrl
                                    if (cleanedAudio.startsWith("//")) {
                                        cleanedAudio = "https:$cleanedAudio"
                                    }
                                    try {
                                        mediaPlayer.reset()
                                        mediaPlayer.setDataSource(cleanedAudio)
                                        mediaPlayer.prepareAsync()
                                        mediaPlayer.setOnPreparedListener { it.start() }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Audio pronunciation not available.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Saved Words sub-section
            if (savedWords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "No saved dictionary terms yet.\nSearch and bookmark words to study them offline!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(savedWords) { savedNote ->
                        var isExpanded by remember { mutableStateOf(false) }
                        val savedEntries = remember(savedNote.content) {
                            try {
                                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, DictionaryEntry::class.java)
                                val adapter = DictionaryNetwork.moshi.adapter<List<DictionaryEntry>>(type)
                                adapter.fromJson(savedNote.content) ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = savedNote.title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (savedEntries.isNotEmpty() && !savedEntries[0].phonetic.isNullOrBlank()) {
                                            Text(
                                                text = savedEntries[0].phonetic ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { isExpanded = !isExpanded },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle Details"
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteWordFromDictionaryNotes(savedNote.id) },
                                        modifier = Modifier.size(40.dp).testTag("delete_saved_word_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete from Dictionary",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                if (isExpanded && savedEntries.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    savedEntries.forEach { entry ->
                                        entry.meanings.forEach { meaning ->
                                            Text(
                                                text = meaning.partOfSpeech.replaceFirstChar { it.uppercaseChar() },
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                            )
                                            meaning.definitions.forEachIndexed { defIdx, def ->
                                                Row(
                                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "${defIdx + 1}.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Column {
                                                        Text(
                                                            text = def.definition,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (!def.example.isNullOrBlank()) {
                                                            Text(
                                                                text = "\"${def.example}\"",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DictionaryEntryCard(
    entry: DictionaryEntry,
    isSaved: Boolean,
    onSave: () -> Unit,
    onPlayAudio: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row (Word, Phonetic, Audio, Save)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!entry.phonetic.isNullOrBlank()) {
                        Text(
                            text = entry.phonetic,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Audio Button
                val audioUrl = remember(entry.phonetics) {
                    entry.phonetics?.firstOrNull { !it.audio.isNullOrBlank() }?.audio
                }
                if (!audioUrl.isNullOrBlank()) {
                    IconButton(
                        onClick = { onPlayAudio(audioUrl) },
                        modifier = Modifier.size(40.dp).testTag("play_pronunciation_btn")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Play Audio Pronunciation", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Bookmark/Save Button
                IconButton(
                    onClick = onSave,
                    modifier = Modifier.size(40.dp).testTag("bookmark_word_btn")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save Word",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Meanings List
            entry.meanings.forEachIndexed { mIdx, meaning ->
                Text(
                    text = meaning.partOfSpeech.replaceFirstChar { it.uppercaseChar() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                meaning.definitions.forEachIndexed { dIdx, def ->
                    Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${dIdx + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = def.definition,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (!def.example.isNullOrBlank()) {
                            Text(
                                text = "\"${def.example}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                            )
                        }
                    }
                }

                // Display Synonyms/Antonyms if present
                if (!meaning.synonyms.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Synonyms: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = meaning.synonyms.take(5).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!meaning.antonyms.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Antonyms: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = meaning.antonyms.take(5).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}


