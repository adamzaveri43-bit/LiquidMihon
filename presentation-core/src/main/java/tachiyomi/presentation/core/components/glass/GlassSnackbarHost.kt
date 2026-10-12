package tachiyomi.presentation.core.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * [SnackbarHost] whose snackbars are floating glass with the Liquid Glass theme.
 */
@Composable
fun GlassSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        if (!LocalLiquidGlass.current) {
            Snackbar(data)
            return@SnackbarHost
        }
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .padding(12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .liquidGlass(
                    shape = shape,
                    colors = LiquidGlassDefaults.colors(),
                    backdrop = LocalLiquidGlassBackdrop.current,
                    cornerRadius = 22.dp,
                    shadowElevation = 10.dp,
                ),
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                )
                data.visuals.actionLabel?.let { label ->
                    TextButton(onClick = data::performAction) {
                        Text(label)
                    }
                }
            }
        }
    }
}
