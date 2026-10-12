package eu.kanade.presentation.manga.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.glass.LiquidGlassDefaults
import tachiyomi.presentation.core.components.glass.LocalLiquidGlass
import tachiyomi.presentation.core.components.glass.LocalLiquidGlassBackdrop
import tachiyomi.presentation.core.components.glass.liquidGlass

/**
 * Start/Resume button, a floating glass capsule with the glass styles.
 */
@Composable
fun ResumeFab(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!LocalLiquidGlass.current) {
        SmallExtendedFloatingActionButton(
            text = text,
            icon = icon,
            onClick = onClick,
            expanded = expanded,
            modifier = modifier,
        )
        return
    }
    SmallExtendedFloatingActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        expanded = expanded,
        shape = CircleShape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier.liquidGlass(
            shape = CircleShape,
            colors = LiquidGlassDefaults.colors(),
            backdrop = LocalLiquidGlassBackdrop.current,
            shadowElevation = 10.dp,
        ),
    )
}
