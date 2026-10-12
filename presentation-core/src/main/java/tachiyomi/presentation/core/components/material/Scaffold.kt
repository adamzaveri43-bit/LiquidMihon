package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.glass.LocalLiquidGlass
import tachiyomi.presentation.core.components.glass.LocalLiquidGlassBackdrop
import tachiyomi.presentation.core.components.glass.liquidGlassSource
import tachiyomi.presentation.core.components.glass.rememberLiquidGlassBackdrop

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBarScrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState(),
    ),
    topBar: @Composable (TopAppBarScrollBehavior) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    var fabHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val floatingBottomBarPadding by rememberUpdatedState(LocalFloatingBottomBarPadding.current)
    // Glass bars refract the content scrolling underneath them
    val backdrop = if (LocalLiquidGlass.current) rememberLiquidGlassBackdrop() else null

    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
                topBar(topBarScrollBehavior)
            }
        },
        bottomBar = {
            CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
                bottomBar()
            }
        },
        snackbarHost = {
            // When a FAB is shown the snackbar is already placed above it
            Box(Modifier.padding(bottom = if (fabHeight == 0) floatingBottomBarPadding else 0.dp)) {
                CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
                    snackbarHost()
                }
            }
        },
        floatingActionButton = {
            Box(
                Modifier
                    .padding(bottom = if (fabHeight == 0) 0.dp else floatingBottomBarPadding)
                    .onSizeChanged { fabHeight = it.height },
            ) {
                floatingActionButton()
            }
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { contentPadding ->
        val paddingValues = remember(contentPadding) {
            FabAwarePaddingValues(contentPadding) {
                val fabPadding = if (fabHeight == 0) 0.dp else with(density) { fabHeight.toDp() } + FabSpacing
                floatingBottomBarPadding + fabPadding
            }
        }
        // Only the outermost Scaffold should account for the floating bar
        CompositionLocalProvider(LocalFloatingBottomBarPadding provides 0.dp) {
            if (backdrop != null) {
                Box(modifier = Modifier.liquidGlassSource(backdrop)) {
                    content(paddingValues)
                }
            } else {
                content(paddingValues)
            }
        }
    }
}

/**
 * Height of a bottom bar floating above this Scaffold's content (e.g. the Liquid Glass navigation bar).
 * Content keeps drawing behind it, but is padded so nothing ends up permanently hidden underneath.
 */
val LocalFloatingBottomBarPadding = compositionLocalOf { 0.dp }

private class FabAwarePaddingValues(
    private val base: PaddingValues,
    private val extraBottom: () -> Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = base.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = base.calculateBottomPadding() + extraBottom()
}
private val FabSpacing = 16.dp
