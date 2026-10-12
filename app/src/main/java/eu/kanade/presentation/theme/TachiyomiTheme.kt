package eu.kanade.presentation.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import eu.kanade.domain.ui.model.AppFont
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.GlassStyle
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CatppuccinColorScheme
import eu.kanade.presentation.theme.colorscheme.FrutigerAeroColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.LiquidGlassColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetCompatColorScheme
import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.TokyoNightColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import eu.kanade.tachiyomi.R
import mihon.app.di.appGraph
import tachiyomi.presentation.core.components.glass.LocalGlassAero
import tachiyomi.presentation.core.components.glass.LocalLiquidGlass

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val uiPreferences = remember { context.appGraph.uiPreferences }
    BaseTachiyomiTheme(
        appTheme = appTheme ?: uiPreferences.appTheme.get(),
        isAmoled = amoled ?: uiPreferences.themeDarkAmoled.get(),
        glassStyle = remember { uiPreferences.glassStyle.get() },
        // Theme previews show the theme's own colors
        accentColor = if (appTheme == null) remember { uiPreferences.accentColor.get() } else 0,
        font = remember { uiPreferences.appFont.get() },
        content = content,
    )
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseTachiyomiTheme(
    appTheme = appTheme,
    isAmoled = isAmoled,
    glassStyle = GlassStyle.OFF,
    accentColor = 0,
    font = AppFont.DEFAULT,
    content = content,
)

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    glassStyle: GlassStyle,
    accentColor: Int,
    font: AppFont,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isGlass = glassStyle != GlassStyle.OFF
    MaterialExpressiveTheme(
        colorScheme = remember(appTheme, isDark, isAmoled, accentColor, isGlass) {
            getThemeColorScheme(
                context = context,
                appTheme = appTheme,
                isDark = isDark,
                isAmoled = isAmoled,
            )
                .withAccent(accentColor, isDark)
                .let { scheme ->
                    // Dialogs and sheets sit on a frosted window, let it show through
                    if (isGlass) {
                        scheme.copy(
                            surfaceContainerHigh = scheme.surfaceContainerHigh.copy(
                                alpha = minOf(scheme.surfaceContainerHigh.alpha, 0.9f),
                            ),
                        )
                    } else {
                        scheme
                    }
                }
        },
        shapes = if (isGlass) LiquidGlassShapes else DefaultShapes,
        typography = remember(font) { typographyFor(font) },
    ) {
        CompositionLocalProvider(
            LocalLiquidGlass provides isGlass,
            LocalGlassAero provides (glassStyle == GlassStyle.FRUTIGER_AERO),
            content = content,
        )
    }
}

/**
 * Replaces the accent roles of this scheme with tones generated from [accent], keeping its surfaces.
 */
private fun ColorScheme.withAccent(accent: Int, isDark: Boolean): ColorScheme {
    if (accent == 0) return this
    val generated = MonetCompatColorScheme.generateColorSchemeFromSeed(
        seed = Color(accent),
        dark = isDark,
        style = PaletteStyle.Fidelity,
    )
    return copy(
        primary = generated.primary,
        onPrimary = generated.onPrimary,
        primaryContainer = generated.primaryContainer,
        onPrimaryContainer = generated.onPrimaryContainer,
        inversePrimary = generated.inversePrimary,
        secondary = generated.primary,
        onSecondary = generated.onPrimary,
        secondaryContainer = generated.secondaryContainer,
        onSecondaryContainer = generated.onSecondaryContainer,
        surfaceTint = generated.surfaceTint,
    )
}

private val DefaultTypography = Typography()

private fun typographyFor(font: AppFont): Typography {
    val family = when (font) {
        AppFont.DEFAULT -> return DefaultTypography
        AppFont.SERIF -> FontFamily.Serif
        AppFont.MONOSPACE -> FontFamily.Monospace
        AppFont.INTER -> variableFontFamily(R.font.inter)
        AppFont.NUNITO -> variableFontFamily(R.font.nunito)
        AppFont.LEXEND -> variableFontFamily(R.font.lexend)
        AppFont.QUICKSAND -> variableFontFamily(R.font.quicksand)
        AppFont.COMFORTAA -> variableFontFamily(R.font.comfortaa)
        AppFont.OPEN_SANS -> variableFontFamily(R.font.open_sans)
        AppFont.ATKINSON_HYPERLEGIBLE -> variableFontFamily(R.font.atkinson_hyperlegible)
    }
    return with(DefaultTypography) {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge.copy(fontFamily = family),
            bodyMedium = bodyMedium.copy(fontFamily = family),
            bodySmall = bodySmall.copy(fontFamily = family),
            labelLarge = labelLarge.copy(fontFamily = family),
            labelMedium = labelMedium.copy(fontFamily = family),
            labelSmall = labelSmall.copy(fontFamily = family),
        )
    }
}

/** A bundled variable font instanced at the weights the UI uses. */
@OptIn(ExperimentalTextApi::class)
private fun variableFontFamily(resId: Int): FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(
            resId = resId,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

private val DefaultShapes = Shapes()

// Softer, rounder corners to match the glass look
private val LiquidGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private fun getThemeColorScheme(
    context: Context,
    appTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme {
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(context)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        isDark = isDark,
        isAmoled = isAmoled,
        overrideDarkSurfaceContainers = appTheme != AppTheme.MONET,
    )
}

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CATPPUCCIN to CatppuccinColorScheme,
    AppTheme.TOKYONIGHT to TokyoNightColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.FRUTIGER_AERO to FrutigerAeroColorScheme,
    AppTheme.LAVENDER to LavenderColorScheme,
    AppTheme.LIQUID_GLASS to LiquidGlassColorScheme,
    AppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    AppTheme.MONOCHROME to MonochromeColorScheme,
    AppTheme.NORD to NordColorScheme,
    AppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    AppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
)
