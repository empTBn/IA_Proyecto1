package com.example.proyecto_1_ia.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),      // Purple
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF4A148C),
    onPrimaryContainer = Color(0xFFE8DEF8),
    secondary = Color(0xFF03DAC6),    // Teal
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFF7597),     // Pink
    onTertiary = Color(0xFF000000),
    background = Color(0xFF121212),   // Dark background
    onBackground = Color(0xFFE0E0E0), // Light text on dark
    surface = Color(0xFF1E1E1E),      // Dark surface
    onSurface = Color(0xFFE0E0E0),    // Light text on dark surface
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),      // Deep purple
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DEF8),
    onPrimaryContainer = Color(0xFF1D1B20),
    secondary = Color(0xFF00897B),    // Teal
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFFE91E63),     // Pink
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBFE),   // White/light background
    onBackground = Color(0xFF1C1B1F), // Dark text on light
    surface = Color(0xFFFFFBFE),      // Light surface
    onSurface = Color(0xFF1C1B1F),    // Dark text on light surface
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun Proyecto_1_IATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,   //Desactivamos para que no afecte mucho en el área visual
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}