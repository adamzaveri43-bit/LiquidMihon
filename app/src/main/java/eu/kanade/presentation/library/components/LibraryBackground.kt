package eu.kanade.presentation.library.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import eu.kanade.domain.ui.model.LibraryBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    gradientColors: List<Color>,
    animate: Boolean,
    blur: Int,
    coverColors: List<Color>?,
    modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.background
    val isDark = baseColor.luminance() < 0.5f
    val blobAlpha = if (isDark) 0.55f else 0.45f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor),
    ) {
        when {
            coverColors != null -> CoverGradientBackground(
                colors = coverColors,
                animate = animate,
                blobAlpha = blobAlpha,
            )
            background == LibraryBackground.NONE -> Unit
            background == LibraryBackground.CUSTOM -> CustomImageBackground(
                imagePath = imagePath,
                animate = animate,
                blur = blur,
            )
            background == LibraryBackground.CUSTOM_GRADIENT -> GradientBackground(
                blobs = gradientColors.ifEmpty { DefaultCustomGradient }.toBlobs(),
                animate = animate,
                blobAlpha = blobAlpha,
            )
            else -> GradientBackground(
                blobs = gradientBlobs.getValue(background),
                animate = animate,
                blobAlpha = blobAlpha,
            )
        }
        if (coverColors != null || background != LibraryBackground.NONE) {
            // Keeps titles and badges readable on top of busy backgrounds
            val scrim = if (coverColors == null && background == LibraryBackground.CUSTOM) 0.5f else 0.2f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor.copy(alpha = scrim)),
            )
        }
    }
}

@Composable
private fun CoverGradientBackground(colors: List<Color>, animate: Boolean, blobAlpha: Float) {
    // Each corner blob blends to the new cover's color
    val animated = List(CoverAnchors.size) { index ->
        animateColorAsState(
            targetValue = colors.getOrElse(index) { colors.lastOrNull() ?: Color.Transparent },
            animationSpec = tween(durationMillis = 700),
            label = "coverBlob$index",
        ).value
    }
    GradientBackground(
        blobs = animated.mapIndexed { index, color ->
            val (x, y) = CoverAnchors[index]
            Blob(color, x, y, 0.62f)
        },
        animate = animate,
        blobAlpha = blobAlpha + 0.1f,
    )
}

@Composable
private fun CustomImageBackground(imagePath: String, animate: Boolean, blur: Int) {
    if (imagePath.isBlank()) return
    val context = LocalContext.current
    // Separate loader so GIFs and animated WebPs play
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    val zoom = animatedPhase(animate = animate, durationMillis = 30_000)
    AsyncImage(
        model = File(imagePath),
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Slow "Ken Burns" drift, and enough overscan to hide the blur's soft edges
                val overscan = if (blur > 0) 1.12f else 1.04f
                val scale = overscan + 0.06f * zoom.value
                scaleX = scale
                scaleY = scale
            }
            .then(if (blur > 0) Modifier.blur(blur.dp) else Modifier),
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
                    colors = listOf(blob.color.copy(alpha = blobAlpha.coerceAtMost(1f)), Color.Transparent),
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
internal fun LibraryBackground.previewColors(customColors: List<Color>): List<Color>? = when (this) {
    LibraryBackground.CUSTOM_GRADIENT -> customColors.ifEmpty { DefaultCustomGradient }
    else -> gradientBlobs[this]?.map { it.color }
}

private val CustomAnchors = listOf(
    0.12f to 0.15f,
    0.88f to 0.3f,
    0.3f to 0.85f,
    0.9f to 0.92f,
    0.5f to 0.5f,
)

private val CoverAnchors = listOf(
    0.1f to 0.1f,
    0.9f to 0.1f,
    0.1f to 0.9f,
    0.9f to 0.9f,
)

private fun List<Color>.toBlobs(): List<Blob> = take(CustomAnchors.size).mapIndexed { index, color ->
    val (x, y) = CustomAnchors[index]
    Blob(color, x, y, if (size == 1) 0.9f else 0.58f)
}

val DefaultCustomGradient = listOf(Color(0xFFFF6B9D), Color(0xFFFFD166), Color(0xFF6A5CFF))

fun parseGradientColors(serialized: String): List<Color> = serialized.split(',')
    .mapNotNull { it.trim().toLongOrNull(16)?.let { argb -> Color(argb.toInt()) } }

fun serializeGradientColors(colors: List<Color>): String = colors.joinToString(",") {
    "%08X".format(it.toArgb())
}

/**
 * Library screens that want the background tinted by a cover (Cover flow) write its colors here.
 */
val LocalLibraryCoverColors = staticCompositionLocalOf<MutableState<List<Color>?>?> { null }

/**
 * Samples the four quadrants of a cover into vivid colors for the background corners.
 */
suspend fun extractCoverColors(context: Context, cover: Any): List<Color>? {
    val request = ImageRequest.Builder(context)
        .data(cover)
        .size(48)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return withContext(Dispatchers.Default) {
        val bitmap = result.image.toBitmap()
        val halfWidth = bitmap.width / 2
        val halfHeight = bitmap.height / 2
        if (halfWidth == 0 || halfHeight == 0) return@withContext null
        listOf(0 to 0, halfWidth to 0, 0 to halfHeight, halfWidth to halfHeight).map { (left, top) ->
            var r = 0L
            var g = 0L
            var b = 0L
            for (x in left until left + halfWidth) {
                for (y in top until top + halfHeight) {
                    val pixel = bitmap.getPixel(x, y)
                    r += android.graphics.Color.red(pixel)
                    g += android.graphics.Color.green(pixel)
                    b += android.graphics.Color.blue(pixel)
                }
            }
            val count = halfWidth * halfHeight
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV((r / count).toInt(), (g / count).toInt(), (b / count).toInt(), hsv)
            // Averages come out muddy, so push them towards vivid
            hsv[1] = (hsv[1] * 1.5f).coerceIn(0f, 1f)
            hsv[2] = hsv[2].coerceIn(0.4f, 0.95f)
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
}

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
