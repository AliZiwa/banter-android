package com.banter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B4D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFDD),
    onPrimaryContainer = Color(0xFF002012),
    secondary = Color(0xFF4C6358),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFF6F3EC),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFE3E5DF),
    onSurfaceVariant = Color(0xFF424842),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED6B2),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFFA9F2CD),
    secondary = Color(0xFFB3CCBE),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE1E4DE),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE1E4DE),
    surfaceVariant = Color(0xFF3F4A43),
    onSurfaceVariant = Color(0xFFBFC9C0),
)

/** Palette used to colour each character's name and avatar, picked by cast position. */
val SpeakerColors = listOf(
    Color(0xFF2E7D6F),
    Color(0xFFB4541E),
    Color(0xFF6A4C93),
    Color(0xFF1F6FB2),
    Color(0xFF9A6A00),
    Color(0xFFA33E63),
)

@Composable
fun BanterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
