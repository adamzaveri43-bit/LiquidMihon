package tachiyomi.presentation.core.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Set when an enclosing container already draws the glass scroll edge, so bars inside it don't draw their own.
 */
val LocalLiquidGlassEdgeProvided = staticCompositionLocalOf { false }

/**
 * Top bar made of several rows (toolbar plus chips or tabs). With the glass styles the whole stack shares one
 * scroll edge blur instead of the toolbar blurring and the rows below being opaque.
 */
@Composable
fun GlassTopBarContainer(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalLiquidGlass.current) {
        Column(modifier = modifier.background(background), content = content)
        return
    }
    Column(
        modifier = modifier.liquidGlassEdge(
            backdrop = LocalLiquidGlassBackdrop.current,
            tint = liquidGlassEdgeTint(),
        ),
    ) {
        CompositionLocalProvider(LocalLiquidGlassEdgeProvided provides true) {
            content()
        }
    }
}
