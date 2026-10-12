package eu.kanade.presentation.components

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.glass.LiquidGlassBackdrop
import tachiyomi.presentation.core.components.glass.LiquidGlassDefaults
import tachiyomi.presentation.core.components.glass.LocalGlassAero
import tachiyomi.presentation.core.components.glass.liquidGlass

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
                    // iOS uses a brighter, slightly milky droplet for the selection
                    colors = colors.copy(
                        tint = lerp(colors.tint, primary, 0.12f).copy(alpha = 0.55f),
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
                if (selected && LocalGlassAero.current) {
                    AeroOrb { icon() }
                } else {
                    icon()
                }
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

/**
 * Glossy Frutiger Aero bubble behind the selected tab's icon.
 */
@Composable
private fun AeroOrb(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(34.dp)
            .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = base, spotColor = base)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    0f to lerp(base, Color.White, 0.45f),
                    0.55f to base,
                    1f to lerp(base, Color.Black, 0.25f),
                ),
            )
            .drawWithContent {
                drawContent()
                // Specular cap over the top half
                drawOval(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.85f),
                        1f to Color.White.copy(alpha = 0.1f),
                        startY = 0f,
                        endY = size.height * 0.5f,
                    ),
                    topLeft = Offset(size.width * 0.14f, size.height * 0.04f),
                    size = Size(size.width * 0.72f, size.height * 0.46f),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // A plain white glyph reads best on the orb
        CompositionLocalProvider(LocalGlassAero provides false, LocalContentColor provides Color.White) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}
