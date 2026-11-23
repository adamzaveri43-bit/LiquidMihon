package eu.kanade.presentation.library.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.library.model.LibraryContentType
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.glass.LiquidGlassDefaults
import tachiyomi.presentation.core.components.glass.LocalLiquidGlass
import tachiyomi.presentation.core.components.glass.liquidGlass
import tachiyomi.presentation.core.i18n.stringResource

private val BarHeight = 52.dp
private val BarMaxWidth = 480.dp
private val IndicatorInset = 4.dp

// Same feel as the navigation bar's droplet: the leading edge darts ahead, the trailing edge catches up
private const val INDICATOR_LEADING_STIFFNESS = 2500f

/**
 * Floating glass capsule that splits the library into Manga, Books and Magazines. The selected section sits
 * in a glass droplet that stretches towards the next one as you switch, like the navigation bar. Without
 * Liquid Glass it's drawn as a plain tonal capsule with the same layout.
 *
 * @param selected the section being shown, or null for the whole library
 * @param counts how many entries of each type match the current search and filters
 */
@Composable
fun LibraryContentTypeBar(
    selected: LibraryContentType?,
    counts: Map<LibraryContentType, Int>,
    onSelect: (LibraryContentType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = remember { listOf<LibraryContentType?>(null) + LibraryContentType.entries }
    val selectedIndex = sections.indexOf(selected).coerceAtLeast(0)
    val glass = LocalLiquidGlass.current
    val glassColors = LiquidGlassDefaults.colors()
    val colorScheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = BarMaxWidth)
            .fillMaxWidth()
            .height(BarHeight)
            .then(
                if (glass) {
                    Modifier.liquidGlass(shape = CircleShape, colors = glassColors, shadowElevation = 8.dp)
                } else {
                    Modifier.clip(CircleShape).background(colorScheme.surfaceContainerHigh)
                },
            ),
    ) {
        val itemWidth = (maxWidth - IndicatorInset * 2) / sections.size
        val target = itemWidth * selectedIndex
        val leading = animateDpAsState(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = INDICATOR_LEADING_STIFFNESS),
            label = "sectionIndicatorLeading",
        )
        val trailing = animateDpAsState(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
            label = "sectionIndicatorTrailing",
        )
        Box(
            modifier = Modifier
                // Read while placing rather than composing, so the animation doesn't rebuild the bar
                .offset {
                    val start = minOf(leading.value, trailing.value)
                    IntOffset((IndicatorInset + start).roundToPx(), IndicatorInset.roundToPx())
                }
                .layout { measurable, constraints ->
                    val start = minOf(leading.value, trailing.value)
                    val end = maxOf(leading.value, trailing.value) + itemWidth
                    val width = (end - start).roundToPx().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .padding(bottom = IndicatorInset * 2)
                .then(
                    if (glass) {
                        Modifier.liquidGlass(
                            shape = CircleShape,
                            colors = glassColors.copy(
                                tint = lerp(glassColors.tint, colorScheme.primary, 0.12f).copy(alpha = 0.55f),
                                rim = glassColors.rim.copy(alpha = glassColors.rim.alpha * 0.8f),
                            ),
                        )
                    } else {
                        Modifier.clip(CircleShape).background(colorScheme.secondaryContainer)
                    },
                ),
        )
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(IndicatorInset),
        ) {
            sections.forEach { type ->
                SectionItem(
                    label = stringResource(type?.sectionLabel ?: MR.strings.content_type_all),
                    count = if (type == null) counts.values.sum() else counts[type] ?: 0,
                    selected = type == selected,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.SectionItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sectionScale",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "sectionContentColor",
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
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            color = contentColor.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** Plural label used for a library section. */
val LibraryContentType.sectionLabel: StringResource
    get() = when (this) {
        LibraryContentType.MANGA -> MR.strings.content_type_manga
        LibraryContentType.BOOK -> MR.strings.content_type_books
        LibraryContentType.MAGAZINE -> MR.strings.content_type_magazines
    }

/** Singular label used when filing one entry under a type. */
val LibraryContentType.itemLabel: StringResource
    get() = when (this) {
        LibraryContentType.MANGA -> MR.strings.content_type_manga
        LibraryContentType.BOOK -> MR.strings.content_type_book
        LibraryContentType.MAGAZINE -> MR.strings.content_type_magazine
    }
