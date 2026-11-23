package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Frutiger Aero theme
 * Glossy sky and aqua blues with fresh greens, in the spirit of mid 2000s Aero/Aqua interfaces.
 *
 * Key colors:
 * Primary #1E90E0
 * Secondary #2FB84F
 * Tertiary #00B3C7
 */
internal object FrutigerAeroColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF6CC4FF),
        onPrimary = Color(0xFF00324F),
        primaryContainer = Color(0xFF0B4C78),
        onPrimaryContainer = Color(0xFFCDE8FF),
        inversePrimary = Color(0xFF1E90E0),
        secondary = Color(0xFF7CE08E), // Unread badge
        onSecondary = Color(0xFF003912), // Unread badge text
        secondaryContainer = Color(0xFF14414F), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFCFF3FF), // Navigation bar selector icon
        tertiary = Color(0xFF5FE0EE), // Downloaded badge
        onTertiary = Color(0xFF00363D), // Downloaded badge text
        tertiaryContainer = Color(0xFF004F59),
        onTertiaryContainer = Color(0xFFA8F1FA),
        background = Color(0xFF051826),
        onBackground = Color(0xFFE2F1FC),
        surface = Color(0xFF051826),
        onSurface = Color(0xFFE2F1FC),
        surfaceVariant = Color(0xFF0E2A3E), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFA9C4D8),
        surfaceTint = Color(0xFF6CC4FF),
        inverseSurface = Color(0xFFE2F1FC),
        inverseOnSurface = Color(0xFF0E2A3E),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF5C0A05),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF5D7C92),
        outlineVariant = Color(0xFF1F3D52),
        surfaceContainerLowest = Color(0xFF04131F),
        surfaceContainerLow = Color(0xFF0A2132),
        surfaceContainer = Color(0xFF0E2A3E), // Navigation bar background
        surfaceContainerHigh = Color(0xE6143449),
        surfaceContainerHighest = Color(0xFF1A3D55),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF1E90E0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCDE8FF),
        onPrimaryContainer = Color(0xFF001E33),
        inversePrimary = Color(0xFF6CC4FF),
        secondary = Color(0xFF2FB84F), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFD3F0FF), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF00334D), // Navigation bar selector icon
        tertiary = Color(0xFF008C9C), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFB8F3FA),
        onTertiaryContainer = Color(0xFF002024),
        background = Color(0xFFEAF6FF),
        onBackground = Color(0xFF0B2233),
        surface = Color(0xFFEAF6FF),
        onSurface = Color(0xFF0B2233),
        surfaceVariant = Color(0xFFF6FBFF), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF3F5A6E),
        surfaceTint = Color(0xFF1E90E0),
        inverseSurface = Color(0xFF1C3446),
        inverseOnSurface = Color(0xFFEAF6FF),
        error = Color(0xFFD9342B),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF7896AB),
        outlineVariant = Color(0xFFC3DAEA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF2FAFF),
        surfaceContainer = Color(0xFFF6FBFF), // Navigation bar background
        surfaceContainerHigh = Color(0xE6FFFFFF),
        surfaceContainerHighest = Color(0xFFFFFFFF),
    )
}
