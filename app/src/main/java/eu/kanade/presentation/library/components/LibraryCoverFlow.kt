package eu.kanade.presentation.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.folder.LibraryEntry
import eu.kanade.tachiyomi.ui.library.folder.coverData
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Folder
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.glass.LiquidGlassDefaults
import tachiyomi.presentation.core.components.glass.LocalLiquidGlass
import tachiyomi.presentation.core.components.glass.liquidGlass
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * Cover Flow: one cover front and center, its neighbours tilted away in 3D on either side.
 */
@Composable
internal fun LibraryCoverFlow(
    entries: List<LibraryEntry>,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickFolder: (LibraryEntry.Folder) -> Unit,
    onLongClickFolder: (LibraryEntry.Folder) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    val pagerState = rememberPagerState { entries.size }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }

    val current = entries.getOrNull(pagerState.currentPage.coerceIn(0, (entries.size - 1).coerceAtLeast(0)))

    // Tint the library background with the settled cover's colors
    val coverColors = LocalLibraryCoverColors.current
    if (coverColors != null) {
        val context = LocalContext.current
        val colorCache = remember { mutableMapOf<String, List<Color>>() }
        val lastWritten = remember { mutableListOf<List<Color>>() }
        val settled = entries.getOrNull(pagerState.settledPage)
        LaunchedEffect(settled?.key) {
            val entry = settled ?: return@LaunchedEffect
            val coverData = entry.coverData() ?: return@LaunchedEffect
            val colors = colorCache[entry.key]
                ?: extractCoverColors(context, coverData)?.also { colorCache[entry.key] = it }
            if (colors != null) {
                coverColors.value = colors
                lastWritten.clear()
                lastWritten.add(colors)
            }
        }
        DisposableEffect(coverColors) {
            // Only clear if another category's cover flow hasn't taken over since
            onDispose { if (coverColors.value === lastWritten.firstOrNull()) coverColors.value = null }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val coverWidth = min(maxWidth * 0.56f, (maxHeight - 200.dp) / 1.8f).coerceAtLeast(96.dp)
            val sidePadding = (maxWidth - coverWidth) / 2
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!searchQuery.isNullOrEmpty()) {
                    GlobalSearchItem(
                        searchQuery = searchQuery,
                        onClick = onGlobalSearchClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.padding.small),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(coverWidth),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    beyondViewportPageCount = 3,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(12),
                    ),
                    key = { entries[it].key },
                    modifier = Modifier
                        .fillMaxWidth()
                        // Taps are resolved by where they land in the row rather than by which cover is
                        // under the finger: neighbours are drawn over the edges of the focused cover
                        .pointerInput(entries, coverWidth) {
                            val centerZone = coverWidth.toPx() * 0.6f
                            fun zone(x: Float): Int {
                                val dx = x - size.width / 2f
                                return when {
                                    abs(dx) <= centerZone -> 0
                                    dx < 0 -> -1
                                    else -> 1
                                }
                            }
                            detectTapGestures(
                                onTap = { position ->
                                    // A tap while flinging just stops it
                                    if (pagerState.isScrollInProgress) return@detectTapGestures
                                    val page = pagerState.currentPage
                                    when (val z = zone(position.x)) {
                                        0 -> when (val entry = entries.getOrNull(page)) {
                                            is LibraryEntry.Folder -> onClickFolder(entry)
                                            is LibraryEntry.Manga -> onClick(entry.item.libraryManga)
                                            null -> Unit
                                        }
                                        else -> scope.launch {
                                            pagerState.animateScrollToPage((page + z).coerceIn(0, entries.lastIndex))
                                        }
                                    }
                                },
                                onLongPress = { position ->
                                    if (zone(position.x) == 0) {
                                        when (val entry = entries.getOrNull(pagerState.currentPage)) {
                                            is LibraryEntry.Folder -> onLongClickFolder(entry)
                                            is LibraryEntry.Manga -> onLongClick(entry.item.libraryManga)
                                            null -> Unit
                                        }
                                    }
                                },
                            )
                        },
                ) { page ->
                    val entry = entries[page]
                    CoverFlowItem(
                        coverData = entry.coverData(),
                        isSelected = entry is LibraryEntry.Manga && entry.item.id in selection,
                        pageOffset = { (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction },
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentKey = { it?.key },
                    label = "coverFlowDetails",
                ) { entry ->
                    when (entry) {
                        is LibraryEntry.Folder -> CoverFlowFolderDetails(entry)
                        is LibraryEntry.Manga -> {
                            val item = entry.item
                            CoverFlowDetails(
                                item = item,
                                onClickContinueReading = onClickContinueReading
                                    ?.takeIf { item.unreadCount > 0 }
                                    ?.let { { it(item.libraryManga) } },
                            )
                        }
                        null -> Unit
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoverFlowItem(
    coverData: Any?,
    isSelected: Boolean,
    pageOffset: () -> Float,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier.graphicsLayer {
            val offset = pageOffset().coerceIn(-4f, 4f)
            val distance = offset.absoluteValue
            val near = distance.coerceAtMost(1f)
            val far = (distance - 1f).coerceAtLeast(0f)
            rotationY = offset.sign * near * MAX_TILT
            val scale = 1f - near * 0.18f - far * 0.05f
            scaleX = scale
            scaleY = scale
            // Pull the tilted neighbours in towards the centre so the flow feels continuous
            translationX = -offset.sign * size.width * (near * 0.2f + far * 0.42f)
            alpha = 1f - far * 0.3f
            cameraDistance = 14f * density
        },
    ) {
        MangaCover.Book(
            data = coverData,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 18.dp, shape = shape)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                ),
        )

        // Mirror reflection fading into the floor. The fade is applied to the whole unflipped box so it
        // reaches zero exactly at the cut
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clipToBounds()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.32f),
                            0.6f to Color.Black.copy(alpha = 0.08f),
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            MangaCover.Book(
                data = coverData,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .graphicsLayer { scaleY = -1f },
            )
        }
    }
}

@Composable
private fun CoverFlowFolderDetails(folder: LibraryEntry.Folder) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Icon(
                imageVector = MaterialSymbols.RoundedFilled.Folder,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = folder.folder.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(MR.strings.library_folder_entry_count, folder.items.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoverFlowDetails(
    item: LibraryItem,
    onClickContinueReading: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = item.libraryManga.manga.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 32.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            BadgeGroup {
                DownloadsBadge(count = item.badges.downloadCount)
                UnreadBadge(count = item.badges.unreadCount)
            }
            BadgeGroup {
                LanguageBadge(
                    isLocal = item.badges.isLocal,
                    sourceLanguage = item.badges.sourceLanguage,
                )
            }
        }
        if (onClickContinueReading != null) {
            val glass = LocalLiquidGlass.current
            FilledTonalButton(
                onClick = onClickContinueReading,
                colors = if (glass) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
                modifier = if (glass) {
                    Modifier.liquidGlass(
                        shape = CircleShape,
                        colors = LiquidGlassDefaults.colors(),
                        shadowElevation = 8.dp,
                    )
                } else {
                    Modifier
                },
            ) {
                Icon(
                    imageVector = MaterialSymbols.RoundedFilled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                Text(stringResource(MR.strings.action_resume))
            }
        }
    }
}

private fun LibraryEntry.coverData(): Any? = when (this) {
    is LibraryEntry.Folder -> coverData
    is LibraryEntry.Manga -> item.coverData()
}

private const val MAX_TILT = 52f
