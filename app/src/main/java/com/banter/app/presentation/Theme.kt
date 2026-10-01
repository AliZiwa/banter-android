package com.banter.app.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.banter.app.domain.Scenario

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D0D66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDF5),
    onPrimaryContainer = Color(0xFF26003F),
    secondary = Color(0xFF5E5A66),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1A1A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1E),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF48474E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD3B8F0),
    onPrimary = Color(0xFF26003F),
    primaryContainer = Color(0xFF3D0D66),
    onPrimaryContainer = Color(0xFFE9DDF5),
    secondary = Color(0xFFC8C3D0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E6EB),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFE6E6EB),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFC9C8CF),
)

/**
 * The accents a group can take. Same five as the iOS app's leagues, so a screenshot of either
 * app reads as the same product: blue, red, green, navy, purple.
 */
val AccentPalette = listOf(
    Color(0xFF0D529E),
    Color(0xFFC94033),
    Color(0xFF1A6B4D),
    Color(0xFF263366),
    Color(0xFF3D0D66),
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

/** The one colour that sets the mood of a scenario's screens. */
val Scenario.tint: Color
    get() = AccentPalette[accent.mod(AccentPalette.size)]

/** The tile gradient: the accent, fading to a lighter version of itself. */
val Scenario.gradient: Brush
    get() = Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.72f)))

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
