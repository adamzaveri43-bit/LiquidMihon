package eu.kanade.presentation.library.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.random.Random

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
            background == LibraryBackground.AERO -> AeroBackground(animate = animate, isDark = isDark)
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
    val time = rememberAnimationTime(animate)
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
                val zoom = 0.5f + 0.5f * sin(time.value * 2 * PI.toFloat() / 30f)
                val scale = overscan + 0.06f * zoom
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
    val time = rememberAnimationTime(animate)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = time.value
        blobs.forEachIndexed { index, blob ->
            // Each blob orbits its anchor at its own pace and breathes, so the mesh keeps morphing
            val speed = 0.32f + 0.07f * index
            val drift = size.minDimension * 0.22f
            val center = Offset(
                x = size.width * blob.x + drift * cos(t * speed + index * 1.7f),
                y = size.height * blob.y + drift * sin(t * speed * 1.3f + index * 2.3f),
            )
            val radius = size.maxDimension * blob.radius * (1f + 0.12f * sin(t * 0.5f + index))
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
 * Frutiger Aero: bright sky over a green hill, with glossy bubbles drifting upwards.
 */
@Composable
private fun AeroBackground(animate: Boolean, isDark: Boolean) {
    val time = rememberAnimationTime(animate)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = time.value
        val sky = if (isDark) {
            listOf(Color(0xFF02142A), Color(0xFF07406B), Color(0xFF0B6E8C))
        } else {
            listOf(Color(0xFF3BA8F5), Color(0xFF9ED9FF), Color(0xFFEAF8FF))
        }
        drawRect(Brush.verticalGradient(sky))
        // Aurora swooshes
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7CF3E4).copy(alpha = if (isDark) 0.35f else 0.45f), Color.Transparent),
                center = Offset(size.width * (0.2f + 0.08f * sin(t * 0.3f)), size.height * 0.3f),
                radius = size.maxDimension * 0.5f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = if (isDark) 0.08f else 0.5f), Color.Transparent),
                center = Offset(size.width * (0.85f + 0.06f * cos(t * 0.25f)), size.height * 0.12f),
                radius = size.maxDimension * 0.35f,
            ),
        )
        // Rolling green hill
        val hillTop = size.height * 0.8f
        drawOval(
            brush = Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(Color(0xFF2E8B57), Color(0xFF0B3D2A))
                } else {
                    listOf(Color(0xFF9BE15D), Color(0xFF2E9E3E))
                },
                startY = hillTop,
                endY = size.height,
            ),
            topLeft = Offset(-size.width * 0.4f, hillTop),
            size = Size(size.width * 1.8f, size.height * 0.6f),
        )
        // Bubbles
        aeroBubbles.forEach { bubble ->
            val travel = ((t * bubble.speed + bubble.phase) % 1.3f)
            val y = size.height * (1.15f - travel)
            val x = size.width * bubble.x + size.width * 0.03f * sin(t * 0.8f + bubble.phase * 6f)
            val r = size.minDimension * bubble.size
            val center = Offset(x, y)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.04f),
                    0.75f to Color.White.copy(alpha = 0.10f),
                    1f to Color.White.copy(alpha = 0.32f),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = r,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
            drawOval(
                color = Color.White.copy(alpha = 0.6f),
                topLeft = Offset(x - r * 0.55f, y - r * 0.7f),
                size = Size(r * 0.6f, r * 0.35f),
            )
        }
    }
}

@Immutable
private data class Bubble(val x: Float, val size: Float, val speed: Float, val phase: Float)

private val aeroBubbles = List(14) { index ->
    val random = Random(index * 7919)
    Bubble(
        x = random.nextFloat(),
        size = 0.03f + random.nextFloat() * 0.07f,
        speed = 0.02f + random.nextFloat() * 0.03f,
        phase = random.nextFloat() * 1.3f,
    )
}

/**
 * Seconds since this was first shown, advancing every frame while [animate] is set.
 *
 * Driven by the frame clock directly so it keeps moving even when system animations are turned down.
 */
@Composable
private fun rememberAnimationTime(animate: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val offset = time.floatValue
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { time.floatValue = offset + (it - start) / 1_000_000_000f }
            }
        }
    }
    return time
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
    LibraryBackground.AERO -> listOf(Color(0xFF3BA8F5), Color(0xFF9ED9FF), Color(0xFF9BE15D))
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
