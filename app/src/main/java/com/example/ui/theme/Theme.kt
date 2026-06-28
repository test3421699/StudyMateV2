package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF86A8FF), // Sleek sapphire blue
    onPrimary = Color(0xFF002F6C),
    primaryContainer = Color(0xFF1C2C54), // Deep navy container
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFF4DEEEA), // Luminous professional cyan/teal
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF004F4D),
    onSecondaryContainer = Color(0xFF9EFFF9),
    tertiary = Color(0xFFF7C948), // Premium status gold/amber
    onTertiary = Color(0xFF402D00),
    background = Color(0xFF0D0E12), // Deep slate obsidian
    onBackground = Color(0xFFF0F4F8),
    surface = Color(0xFF151720), // Elegant surface grey-blue slate
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = Color(0xFF1E2230), // Sleek card variant
    onSurfaceVariant = Color(0xFFBAC7D5),
    outline = Color(0xFF627D98),
    errorContainer = Color(0xFF8C1F1D),
    onErrorContainer = Color(0xFFFBE4E1),
    error = Color(0xFFF2B8B5)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF4F46E5), // Modern Indigo
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEF2FF), // Soft indigo tint
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF0EA5E9), // Clean Sky Blue
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE), // Soft Sky tint
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFFF59E0B), // Radiant Amber
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7), // Soft Amber tint
    onTertiaryContainer = Color(0xFF78350F),
    background = Color(0xFFF8FAFC), // Fresh Pearl/Slate Canvas
    onBackground = Color(0xFF0F172A), // Crisp slate text
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9), // Modern card gray
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0), // Clean light borders
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    error = Color(0xFFEF4444)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Use our customized "Professional Polish" scheme explicitly
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
