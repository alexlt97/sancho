package com.unplugged.app

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

private val SanchoDarkColors = darkColorScheme(
    primary = Color(0xFFA7E6AD),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF18502F),
    onPrimaryContainer = Color(0xFFC2FBC8),
    secondary = Color(0xFFC7B9FF),
    onSecondary = Color(0xFF32245B),
    secondaryContainer = Color(0xFF2E2940),
    onSecondaryContainer = Color(0xFFEAE1FF),
    tertiary = Color(0xFFFFC06A),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3D00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF101216),
    onBackground = Color(0xFFF4F7FB),
    surface = Color(0xFF171B20),
    onSurface = Color(0xFFF4F7FB),
    surfaceVariant = Color(0xFF2B333B),
    onSurfaceVariant = Color(0xFFC7D0DA),
    outline = Color(0xFF89939E),
    error = Color(0xFFFF9F9A),
    onError = Color(0xFF4A0506),
    errorContainer = Color(0xFF6B2D2D),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val SanchoLightColors = lightColorScheme(
    primary = Color(0xFF176B49),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F1C4),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Color(0xFF5A4B83),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DEFF),
    onSecondaryContainer = Color(0xFF211345),
    tertiary = Color(0xFF8A4F00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCB0),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF171A1E),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF171A1E),
    surfaceVariant = Color(0xFFE0E5EB),
    onSurfaceVariant = Color(0xFF424A54),
    outline = Color(0xFF737D88),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
internal fun SanchoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SanchoDarkColors else SanchoLightColors
    val activity = LocalActivity.current

    SideEffect {
        activity?.window?.let { window ->
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
