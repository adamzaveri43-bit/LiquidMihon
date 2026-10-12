package eu.kanade.presentation.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Holds a recording of the content that glass surfaces should refract.
 *
 * The content is marked with [liquidGlassSource] and glass surfaces drawn with [liquidGlass] sample it,
 * blurred and saturated, behind their own fill (API 31+). Older versions fall back to a denser frosted fill.
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

    val BlurRadius = 24.dp

    @Composable
    fun colors(): LiquidGlassColors {
        val colorScheme = MaterialTheme.colorScheme
        val isDark = colorScheme.background.luminance() < 0.5f
        // Without a real blur behind it the glass needs to be denser to keep content legible
        val tintAlpha = when {
            !isBackdropBlurSupported -> 0.92f
            isDark -> 0.55f
            else -> 0.6f
        }
        return if (isDark) {
            LiquidGlassColors(
                tint = colorScheme.surfaceContainerHigh.copy(alpha = tintAlpha),
                sheen = Color.White.copy(alpha = 0.10f),
                rim = Color.White.copy(alpha = 0.30f),
                shadow = Color.Black.copy(alpha = 0.6f),
            )
        } else {
            LiquidGlassColors(
                tint = colorScheme.surfaceContainerHigh.copy(alpha = tintAlpha),
                sheen = Color.White.copy(alpha = 0.6f),
                rim = Color.White.copy(alpha = 0.95f),
                shadow = Color.Black.copy(alpha = 0.22f),
            )
        }
    }
}

/**
 * Draws this element as a piece of liquid glass: a blurred, saturated view of [backdrop] (when given and
 * supported), a translucent tint, a top sheen and a specular rim.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    colors: LiquidGlassColors,
    backdrop: LiquidGlassBackdrop? = null,
    blurRadius: Dp = LiquidGlassDefaults.BlurRadius,
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
        BackdropBlurElement(backdrop, blurRadius)
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
                0.45f to Color.Transparent,
                1f to colors.sheen.copy(alpha = colors.sheen.alpha * 0.35f),
            ),
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                0f to colors.rim,
                0.4f to colors.rim.copy(alpha = 0f),
                0.75f to colors.rim.copy(alpha = 0f),
                1f to colors.rim.copy(alpha = colors.rim.alpha * 0.6f),
            ),
            shape = shape,
        )
}

private data class BackdropBlurElement(
    val backdrop: LiquidGlassBackdrop,
    val blurRadius: Dp,
) : ModifierNodeElement<BackdropBlurNode>() {
    override fun create() = BackdropBlurNode(backdrop, blurRadius)

    override fun update(node: BackdropBlurNode) {
        node.backdrop = backdrop
        node.blurRadius = blurRadius
        node.invalidateDraw()
    }
}

/**
 * Draws the backdrop behind this element's content through its own blurred layer, so that the content
 * (icons, labels) itself stays sharp.
 */
private class BackdropBlurNode(
    var backdrop: LiquidGlassBackdrop,
    var blurRadius: Dp,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var blurLayer: GraphicsLayer? = null
    private var blurLayerRadius = -1f
    private var positionInRoot = Offset.Zero

    override fun onAttach() {
        blurLayer = requireGraphicsContext().createGraphicsLayer()
        blurLayerRadius = -1f
    }

    override fun onDetach() {
        blurLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        blurLayer = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        if (position != positionInRoot) {
            positionInRoot = position
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val layer = blurLayer
        if (layer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = blurRadius.toPx()
            if (radius != blurLayerRadius) {
                layer.renderEffect = createGlassRenderEffect(radius)
                blurLayerRadius = radius
            }
            val offset = backdrop.positionInRoot - positionInRoot
            layer.record {
                translate(offset.x, offset.y) {
                    drawLayer(backdrop.layer)
                }
            }
            drawLayer(layer)
        }
        drawContent()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun createGlassRenderEffect(radius: Float): RenderEffect {
    // Boost saturation like real glass does with the colors behind it, then frost it
    val saturation = ColorMatrix().apply { setSaturation(1.8f) }
    return android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturation)),
        android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
    ).asComposeRenderEffect()
}

object LiquidGlassNavigationBarDefaults {
    val Height = 64.dp
    val HorizontalMargin = 16.dp
    val BottomMargin = 12.dp
    val MaxWidth = 480.dp

    /** Space the floating bar takes above the system navigation bar. */
    val FloatingHeight = Height + BottomMargin
}

private val IndicatorInset = 4.dp

/**
 * Floating capsule navigation bar made of liquid glass. The selection is a glass droplet that stretches
 * towards the newly selected item before settling.
 */
@Composable
fun LiquidGlassNavigationBar(
    backdrop: LiquidGlassBackdrop,
    selectedIndex: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LiquidGlassDefaults.colors()
    BoxWithConstraints(
        modifier = modifier
            .height(LiquidGlassNavigationBarDefaults.Height)
            .liquidGlass(
                shape = CircleShape,
                colors = colors,
                backdrop = backdrop,
                shadowElevation = 12.dp,
            ),
    ) {
        val itemWidth = (maxWidth - IndicatorInset * 2) / itemCount.coerceAtLeast(1)
        val target = itemWidth * selectedIndex
        // Leading edge moves quickly, trailing edge follows lazily: the gap between them is the "stretch"
        val leading by animateDpAsState(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
            label = "indicatorLeading",
        )
        val trailing by animateDpAsState(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "indicatorTrailing",
        )
        val start = minOf(leading, trailing)
        val end = maxOf(leading, trailing) + itemWidth
        val primary = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .offset(x = IndicatorInset + start, y = IndicatorInset)
                .width(end - start)
                .fillMaxHeight()
                .padding(bottom = IndicatorInset * 2)
                .liquidGlass(
                    shape = CircleShape,
                    colors = colors.copy(
                        tint = primary.copy(alpha = 0.16f),
                        rim = colors.rim.copy(alpha = colors.rim.alpha * 0.8f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(IndicatorInset),
            content = content,
        )
    }
}

@Composable
fun RowScope.LiquidGlassNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    badge: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "itemScale",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "itemContentColor",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Tab,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            BadgedBox(badge = { badge?.invoke() }) {
                icon()
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
