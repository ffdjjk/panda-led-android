package com.biexi.pandaled.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60E6D2),
    onPrimary = Color(0xFF003B35),
    primaryContainer = Color(0xFF0D4D48),
    onPrimaryContainer = Color(0xFFB9FFF2),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF422C00),
    secondaryContainer = Color(0xFF4F3A10),
    onSecondaryContainer = Color(0xFFFFE3A1),
    tertiary = Color(0xFFFF7A90),
    onTertiary = Color(0xFF4A0715),
    tertiaryContainer = Color(0xFF681C2A),
    onTertiaryContainer = Color(0xFFFFD9DE),
    background = Color(0xFF070A0F),
    onBackground = Color(0xFFE7ECF2),
    surface = Color(0xFF0D1118),
    onSurface = Color(0xFFE7ECF2),
    surfaceVariant = Color(0xFF171D27),
    onSurfaceVariant = Color(0xFFB7C1CE),
    surfaceContainerLowest = Color(0xFF06080D),
    surfaceContainerLow = Color(0xFF0B0F16),
    surfaceContainer = Color(0xFF101620),
    surfaceContainerHigh = Color(0xFF161D28),
    surfaceContainerHighest = Color(0xFF1D2531),
    outline = Color(0xFF3A4656),
    outlineVariant = Color(0xFF242D3A),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF4F0000),
    errorContainer = Color(0xFF5A1517),
    onErrorContainer = Color(0xFFFFD7D7)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9FFF2),
    onPrimaryContainer = Color(0xFF00211D),
    secondary = Color(0xFF665000),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE3A1),
    onSecondaryContainer = Color(0xFF201700),
    tertiary = Color(0xFF8E0033),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DE),
    onTertiaryContainer = Color(0xFF2E000D),
    background = Color(0xFFF8F8F8),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF8F8F8),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E3E9),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F7),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E8EC),
    surfaceContainerHighest = Color(0xFFE1E3E7),
    outline = Color(0xFF737780),
    outlineVariant = Color(0xFFC3C6CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun PandaLedTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("pandaled_prefs", Context.MODE_PRIVATE)
    }
    var colorMode by remember(prefs) {
        mutableStateOf(prefs.getString("color_mode", "system") ?: "system")
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "color_mode") {
                colorMode = sharedPrefs.getString("color_mode", "system") ?: "system"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (colorMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
