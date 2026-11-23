package tachiyomi.presentation.core.components.material

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import tachiyomi.presentation.core.components.glass.LocalGlassAero

/*
 * Drop-in replacements for Material's Icon that render as glossy Frutiger Aero icons with the Aero glass style:
 * a bright-to-deep vertical gradient in the icon's hue (aqua for neutral icons) with a specular band over the
 * top half. Untinted icons (images such as extension icons) are left alone.
 */

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (LocalGlassAero.current && tint != Color.Unspecified) {
        androidx.compose.material3.Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier.aeroIcon(tint, isDarkTheme()),
            tint = Color.White,
        )
    } else {
        androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint)
    }
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (LocalGlassAero.current && tint != Color.Unspecified) {
        androidx.compose.material3.Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier.aeroIcon(tint, isDarkTheme()),
            tint = Color.White,
        )
    } else {
        androidx.compose.material3.Icon(painter, contentDescription, modifier, tint)
    }
}

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (LocalGlassAero.current && tint != Color.Unspecified) {
        androidx.compose.material3.Icon(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier.aeroIcon(tint, isDarkTheme()),
            tint = Color.White,
        )
    } else {
        androidx.compose.material3.Icon(bitmap, contentDescription, modifier, tint)
    }
}

@Composable
private fun isDarkTheme() = MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun Modifier.aeroIcon(tint: Color, isDark: Boolean): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        // Disabled and secondary icons keep their dimming
        alpha = tint.alpha
    }
    .drawWithCache {
        val base = aeroBaseColor(tint, isDark)
        val body = Brush.verticalGradient(
            0f to lerp(base, Color.White, 0.55f),
            0.5f to base,
            1f to lerp(base, Color.Black, 0.3f),
        )
        val gloss = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.8f),
            0.44f to Color.White.copy(alpha = 0.18f),
            0.48f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            // Recolor the glyph with the gradient, then lay the gloss over it
            drawRect(body, blendMode = BlendMode.SrcIn)
            drawRect(gloss, blendMode = BlendMode.SrcAtop)
        }
    }

private val AeroAquaDark = Color(0xFF7AD8FF)
private val AeroAquaLight = Color(0xFF0B86D8)

/**
 * Neutral (grey, white, black) tints become Aero aqua, colored ones keep their hue but get vivid.
 */
private fun aeroBaseColor(tint: Color, isDark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(tint.copy(alpha = 1f).toArgb(), hsv)
    if (hsv[1] < 0.2f) return if (isDark) AeroAquaDark else AeroAquaLight
    hsv[1] = hsv[1].coerceAtLeast(0.65f)
    hsv[2] = if (isDark) hsv[2].coerceAtLeast(0.85f) else hsv[2].coerceIn(0.55f, 0.85f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
