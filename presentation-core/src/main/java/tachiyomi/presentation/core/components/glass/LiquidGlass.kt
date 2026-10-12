package tachiyomi.presentation.core.components.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the Liquid Glass look is active, i.e. chrome should be drawn as translucent glass instead of opaque
 * Material surfaces.
 */
val LocalLiquidGlass = staticCompositionLocalOf { false }

/**
 * The content glass chrome (top bars, bottom bars) of the closest Scaffold should refract, if any.
 */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LiquidGlassBackdrop?> { null }

/**
 * Holds a recording of the content that glass surfaces should refract.
 *
 * The content is marked with [liquidGlassSource] and glass surfaces drawn with [liquidGlass] sample it, blurred
 * and saturated (API 31+) and bent at the edges like a lens (API 33+), behind their own fill. Older versions fall
 * back to a denser frosted fill.
 */
@Stable
class LiquidGlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var positionInRoot by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberLiquidGlassBackdrop(): LiquidGlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidGlassBackdrop(layer) }
}

fun Modifier.liquidGlassSource(backdrop: LiquidGlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.positionInRoot = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@Immutable
data class LiquidGlassColors(
    val tint: Color,
    val sheen: Color,
    val rim: Color,
    val shadow: Color,
)

object LiquidGlassDefaults {

    val isBackdropBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val BlurRadius = 20.dp

    @Composable
    fun colors(): LiquidGlassColors {
        val colorScheme = MaterialTheme.colorScheme
        val isDark = colorScheme.background.luminance() < 0.5f
        // iOS glass is mostly clear; without a real blur behind it, it has to be denser to stay legible
        val tintAlpha = when {
            !isBackdropBlurSupported -> 0.92f
            isDark -> 0.36f
            else -> 0.42f
        }
        val tint = if (isDark) Color(0xFF1C1C22) else Color.White
        return if (isDark) {
            LiquidGlassColors(
                tint = tint.copy(alpha = tintAlpha),
                sheen = Color.White.copy(alpha = 0.08f),
                rim = Color.White.copy(alpha = 0.34f),
                shadow = Color.Black.copy(alpha = 0.5f),
            )
        } else {
            LiquidGlassColors(
                tint = tint.copy(alpha = tintAlpha),
                sheen = Color.White.copy(alpha = 0.5f),
                rim = Color.White.copy(alpha = 0.95f),
                shadow = Color.Black.copy(alpha = 0.18f),
            )
        }
    }
}

/**
 * Draws this element as a piece of liquid glass: a blurred, saturated and lens-bent view of [backdrop] (when
 * given and supported), a translucent tint, a soft sheen and a specular rim.
 *
 * @param cornerRadius corner radius of [shape] for the lens effect, [Dp.Unspecified] for a capsule.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    colors: LiquidGlassColors,
    backdrop: LiquidGlassBackdrop? = null,
    blurRadius: Dp = LiquidGlassDefaults.BlurRadius,
    cornerRadius: Dp = Dp.Unspecified,
    shadowElevation: Dp = 0.dp,
): Modifier {
    val shadow = if (shadowElevation > 0.dp) {
        Modifier.shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = colors.shadow,
            spotColor = colors.shadow,
        )
    } else {
        Modifier
    }
    val refraction = if (backdrop != null && LiquidGlassDefaults.isBackdropBlurSupported) {
        BackdropElement(backdrop, blurRadius, cornerRadius, lens = true, fade = false)
    } else {
        Modifier
    }
    return this
        .then(shadow)
        .clip(shape)
        .then(refraction)
        .background(colors.tint)
        .background(
            Brush.verticalGradient(
                0f to colors.sheen,
                0.4f to Color.Transparent,
                1f to colors.sheen.copy(alpha = colors.sheen.alpha * 0.4f),
            ),
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                0f to colors.rim,
                0.35f to colors.rim.copy(alpha = colors.rim.alpha * 0.15f),
                0.7f to colors.rim.copy(alpha = colors.rim.alpha * 0.1f),
                1f to colors.rim.copy(alpha = colors.rim.alpha * 0.7f),
            ),
            shape = shape,
        )
}

/**
 * iOS style scroll edge: the content scrolling underneath a bar is blurred and fades out towards the bar's
 * bottom edge, with [tint] laid over it.
 */
fun Modifier.liquidGlassEdge(
    backdrop: LiquidGlassBackdrop?,
    tint: Color,
    blurRadius: Dp = LiquidGlassDefaults.BlurRadius,
): Modifier {
    val blur = if (backdrop != null && LiquidGlassDefaults.isBackdropBlurSupported) {
        BackdropElement(backdrop, blurRadius, Dp.Unspecified, lens = false, fade = true)
    } else {
        Modifier
    }
    val tintAlpha = if (LiquidGlassDefaults.isBackdropBlurSupported) tint.alpha else 1f
    return this
        .then(blur)
        .background(
            Brush.verticalGradient(
                0f to tint.copy(alpha = tintAlpha),
                0.7f to tint.copy(alpha = tintAlpha * 0.75f),
                1f to tint.copy(alpha = tintAlpha * if (LiquidGlassDefaults.isBackdropBlurSupported) 0f else 1f),
            ),
        )
}

private data class BackdropElement(
    val backdrop: LiquidGlassBackdrop,
    val blurRadius: Dp,
    val cornerRadius: Dp,
    val lens: Boolean,
    val fade: Boolean,
) : ModifierNodeElement<BackdropNode>() {
    override fun create() = BackdropNode(backdrop, blurRadius, cornerRadius, lens, fade)

    override fun update(node: BackdropNode) {
        node.backdrop = backdrop
        node.blurRadius = blurRadius
        node.cornerRadius = cornerRadius
        node.lens = lens
        node.fade = fade
        node.invalidateEffect()
    }
}

/**
 * Draws the backdrop behind this element's content through its own effect layer, so that the content (icons,
 * labels) itself stays sharp.
 */
private class BackdropNode(
    var backdrop: LiquidGlassBackdrop,
    var blurRadius: Dp,
    var cornerRadius: Dp,
    var lens: Boolean,
    var fade: Boolean,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var effectLayer: GraphicsLayer? = null
    private var effectKey: Any? = null
    private var positionInRoot = Offset.Zero

    fun invalidateEffect() {
        effectKey = null
        invalidateDraw()
    }

    override fun onAttach() {
        effectLayer = requireGraphicsContext().createGraphicsLayer()
        effectKey = null
    }

    override fun onDetach() {
        effectLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        effectLayer = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        if (position != positionInRoot) {
            positionInRoot = position
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val layer = effectLayer
        if (layer == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            drawContent()
            return
        }
        val radius = blurRadius.toPx()
        val corner = if (cornerRadius == Dp.Unspecified) size.minDimension / 2 else cornerRadius.toPx()
        val key = listOf(radius, corner, size, lens)
        if (key != effectKey) {
            layer.renderEffect = createGlassRenderEffect(radius, if (lens) size else null, corner, density)
            effectKey = key
        }
        val offset = backdrop.positionInRoot - positionInRoot
        layer.record {
            translate(offset.x, offset.y) {
                drawLayer(backdrop.layer)
            }
        }
        if (fade) {
            // Fade the blur out towards the bottom edge
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                drawLayer(layer)
                drawRect(
                    brush = Brush.verticalGradient(0f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
                canvas.restore()
            }
        } else {
            drawLayer(layer)
        }
        drawContent()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun createGlassRenderEffect(radius: Float, lensSize: Size?, cornerRadius: Float, density: Float): RenderEffect {
    // Boost saturation like real glass does with the colors behind it, then frost it
    val saturation = ColorMatrix().apply { setSaturation(1.7f) }
    val frost = android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturation)),
        android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
    )
    if (lensSize == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return frost.asComposeRenderEffect()
    }
    return android.graphics.RenderEffect.createChainEffect(
        createLensEffect(lensSize, cornerRadius, density),
        frost,
    ).asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createLensEffect(size: Size, cornerRadius: Float, density: Float): android.graphics.RenderEffect {
    val shader = RuntimeShader(LENS_SHADER)
    val band = minOf(size.minDimension * 0.42f, 22f * density)
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("radius", cornerRadius.coerceAtMost(size.minDimension / 2))
    shader.setFloatUniform("band", band)
    shader.setFloatUniform("strength", band * 0.75f)
    return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/**
 * Refraction at the rim of a rounded rectangle: near the edge, content is sampled from further inside so it
 * looks magnified and bent around the edge, with slight chromatic dispersion.
 */
private const val LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float band;
uniform float strength;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundRect(p, halfSize, radius);
    float t = clamp(1.0 + d / band, 0.0, 1.0);
    t = t * t * t;
    float2 e = float2(1.0, 0.0);
    float2 n = float2(
        sdRoundRect(p + e.xy, halfSize, radius) - sdRoundRect(p - e.xy, halfSize, radius),
        sdRoundRect(p + e.yx, halfSize, radius) - sdRoundRect(p - e.yx, halfSize, radius)
    );
    n = n / max(length(n), 0.0001);
    float2 offset = -n * strength * t;
    half4 c = content.eval(coord + offset);
    c.r = content.eval(coord + offset * 1.08).r;
    c.b = content.eval(coord + offset * 0.92).b;
    return c;
}
"""
