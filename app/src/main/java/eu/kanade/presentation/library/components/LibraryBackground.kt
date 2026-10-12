package eu.kanade.presentation.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.model.LibraryBackground
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * How the library is decorated, provided by the library screen to its components.
 */
@Immutable
data class LibraryStyle(
    val hasBackground: Boolean = false,
    val animations: Boolean = false,
)

val LocalLibraryStyle = staticCompositionLocalOf { LibraryStyle() }

@Composable
fun LibraryBackgroundLayer(
    background: LibraryBackground,
    imagePath: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.background
    val isDark = baseColor.luminance() < 0.5f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor),
    ) {
        when (background) {
            LibraryBackground.NONE -> Unit
            LibraryBackground.CUSTOM -> CustomImageBackground(imagePath = imagePath, animate = animate)
            else -> GradientBackground(
                blobs = gradientBlobs.getValue(background),
                animate = animate,
                blobAlpha = if (isDark) 0.55f else 0.45f,
            )
        }
        if (background != LibraryBackground.NONE) {
            // Keeps titles and badges readable on top of busy backgrounds
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor.copy(alpha = if (background == LibraryBackground.CUSTOM) 0.55f else 0.2f)),
            )
        }
    }
}

@Composable
private fun CustomImageBackground(imagePath: String, animate: Boolean) {
    if (imagePath.isBlank()) return
    val zoom = animatedPhase(animate = animate, durationMillis = 30_000)
    AsyncImage(
        model = File(imagePath),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Slow "Ken Burns" drift
                val scale = 1.04f + 0.06f * zoom.value
                scaleX = scale
                scaleY = scale
            },
    )
}

@Composable
private fun GradientBackground(
    blobs: List<Blob>,
    animate: Boolean,
    blobAlpha: Float,
) {
    val phase = animatedPhase(animate = animate, durationMillis = 24_000)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val angle = phase.value * 2 * PI
        blobs.forEachIndexed { index, blob ->
            // Each blob orbits its anchor on its own path so the mesh slowly morphs
            val drift = size.minDimension * 0.08f
            val center = Offset(
                x = size.width * blob.x + drift * cos(angle + index * 1.7).toFloat(),
                y = size.height * blob.y + drift * sin(angle * (1 + index % 2) + index).toFloat(),
            )
            val radius = size.maxDimension * blob.radius
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(blob.color.copy(alpha = blobAlpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
            )
        }
    }
}

/**
 * A 0..1..0 value cycling forever when [animate] is set, else a constant.
 */
@Composable
private fun animatedPhase(animate: Boolean, durationMillis: Int): State<Float> {
    if (!animate) return remember { mutableFloatStateOf(0.5f) }
    return rememberInfiniteTransition(label = "libraryBackground").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "libraryBackgroundPhase",
    )
}

@Immutable
private data class Blob(val color: Color, val x: Float, val y: Float, val radius: Float)

private val gradientBlobs = mapOf(
    LibraryBackground.AURORA to listOf(
        Blob(Color(0xFF34D399), 0.1f, 0.15f, 0.55f),
        Blob(Color(0xFF22D3EE), 0.85f, 0.3f, 0.5f),
        Blob(Color(0xFF8B5CF6), 0.3f, 0.85f, 0.6f),
        Blob(Color(0xFF3B82F6), 0.9f, 0.9f, 0.45f),
    ),
    LibraryBackground.SUNSET to listOf(
        Blob(Color(0xFFFF7A59), 0.15f, 0.1f, 0.55f),
        Blob(Color(0xFFFFC857), 0.85f, 0.25f, 0.45f),
        Blob(Color(0xFFE5468A), 0.25f, 0.75f, 0.6f),
        Blob(Color(0xFF7C3AED), 0.9f, 0.95f, 0.5f),
    ),
    LibraryBackground.OCEAN to listOf(
        Blob(Color(0xFF0EA5E9), 0.15f, 0.2f, 0.6f),
        Blob(Color(0xFF14B8A6), 0.85f, 0.15f, 0.45f),
        Blob(Color(0xFF1D4ED8), 0.7f, 0.8f, 0.6f),
        Blob(Color(0xFF67E8F9), 0.1f, 0.9f, 0.4f),
    ),
    LibraryBackground.FOREST to listOf(
        Blob(Color(0xFF22C55E), 0.2f, 0.15f, 0.55f),
        Blob(Color(0xFF84CC16), 0.9f, 0.35f, 0.45f),
        Blob(Color(0xFF0F766E), 0.3f, 0.85f, 0.6f),
        Blob(Color(0xFFEAB308), 0.85f, 0.9f, 0.35f),
    ),
    LibraryBackground.NEBULA to listOf(
        Blob(Color(0xFFD946EF), 0.15f, 0.2f, 0.55f),
        Blob(Color(0xFF6366F1), 0.85f, 0.15f, 0.5f),
        Blob(Color(0xFFF43F5E), 0.75f, 0.75f, 0.45f),
        Blob(Color(0xFF0EA5E9), 0.15f, 0.9f, 0.5f),
    ),
)

/** Colors for previewing a background option. */
internal fun LibraryBackground.previewColors(): List<Color>? = gradientBlobs[this]?.map { it.color }

/**
 * Library item that springs into place the first time it's shown, when animations are enabled.
 */
@Composable
internal fun AnimatedLibraryItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val enabled = LocalLibraryStyle.current.animations
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    if (enabled) {
        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            val scale = 0.86f + 0.14f * p
            scaleX = scale
            scaleY = scale
            translationY = (1f - p) * 28.dp.toPx()
        },
    ) {
        content()
    }
}
