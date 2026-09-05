package com.example.calmcompanion.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// A single calm light-blue-on-white palette. Distress screens should not change
// appearance with system dark mode or wallpaper colours.
private val AiraColorScheme = lightColorScheme(
    primary = AiraBlue,
    onPrimary = AiraWhite,
    primaryContainer = AiraBlueTint,
    onPrimaryContainer = AiraBlueDeep,
    secondary = AiraBlueDeep,
    onSecondary = AiraWhite,
    secondaryContainer = AiraBlueTint,
    onSecondaryContainer = AiraBlueDeep,
    tertiary = AiraBlueSoft,
    onTertiary = AiraInk,
    background = AiraWhite,
    onBackground = AiraInk,
    surface = AiraWhite,
    onSurface = AiraInk,
    surfaceVariant = AiraBlueMist,
    onSurfaceVariant = AiraSlate,
    outline = AiraBlueSoft,
    error = AiraAlert,
    onError = AiraWhite
)

@Composable
fun CalmCompanionTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AiraWhite.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = AiraColorScheme,
        typography = Typography,
        content = content
    )
}
