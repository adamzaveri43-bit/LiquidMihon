package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Liquid Glass theme
 * Airy, high-contrast neutrals with a vivid system blue, meant to sit under translucent glass surfaces.
 *
 * Key colors:
 * Primary #0A84FF
 * Secondary #64D2FF
 * Tertiary #30D158
 * Neutral #8E8E93
 */
internal object LiquidGlassColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF0A3D7A),
        onPrimaryContainer = Color(0xFFD6E6FF),
        inversePrimary = Color(0xFF007AFF),
        secondary = Color(0xFF64D2FF), // Unread badge
        onSecondary = Color(0xFF00344A), // Unread badge text
        secondaryContainer = Color(0xFF2C3A4E), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFDDEBFF), // Navigation bar selector icon
        tertiary = Color(0xFF30D158), // Downloaded badge
        onTertiary = Color(0xFF00390F), // Downloaded badge text
        tertiaryContainer = Color(0xFF0E5223),
        onTertiaryContainer = Color(0xFFB4F5C2),
        background = Color(0xFF0B0B0F),
        onBackground = Color(0xFFF2F2F7),
        surface = Color(0xFF0B0B0F),
        onSurface = Color(0xFFF2F2F7),
        surfaceVariant = Color(0xFF1C1C22), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFAEAEB2),
        surfaceTint = Color(0xFF0A84FF),
        inverseSurface = Color(0xFFF2F2F7),
        inverseOnSurface = Color(0xFF1C1C1E),
        error = Color(0xFFFF453A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFF7A1C17),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF636366),
        outlineVariant = Color(0xFF38383A),
        surfaceContainerLowest = Color(0xFF0E0E12),
        surfaceContainerLow = Color(0xFF141419),
        surfaceContainer = Color(0xFF1C1C22), // Navigation bar background
        surfaceContainerHigh = Color(0xE624242B), // Dialogs & sheets: translucent over the blurred window behind
        surfaceContainerHighest = Color(0xFF2C2C34),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF007AFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E6FF),
        onPrimaryContainer = Color(0xFF001B3D),
        inversePrimary = Color(0xFF0A84FF),
        secondary = Color(0xFF007AFF), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFE3EDFB), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF001B3D), // Navigation bar selector icon
        tertiary = Color(0xFF248A3D), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFC6F6D0),
        onTertiaryContainer = Color(0xFF00210A),
        background = Color(0xFFF2F2F7),
        onBackground = Color(0xFF1C1C1E),
        surface = Color(0xFFF2F2F7),
        onSurface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFFFFFFFF), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF6C6C70),
        surfaceTint = Color(0xFF007AFF),
        inverseSurface = Color(0xFF2C2C2E),
        inverseOnSurface = Color(0xFFF2F2F7),
        error = Color(0xFFFF3B30),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF8E8E93),
        outlineVariant = Color(0xFFD1D1D6),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF9F9FC),
        surfaceContainer = Color(0xFFFFFFFF), // Navigation bar background
        surfaceContainerHigh = Color(0xE6FFFFFF), // Dialogs & sheets: translucent over the blurred window behind
        surfaceContainerHighest = Color(0xFFFFFFFF),
    )
}
