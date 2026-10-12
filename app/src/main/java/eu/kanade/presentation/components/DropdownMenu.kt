package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowRight
import mihon.icons.materialsymbols.rounded.RadioButtonChecked
import mihon.icons.materialsymbols.rounded.RadioButtonUnchecked
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.glass.LiquidGlassDefaults
import tachiyomi.presentation.core.components.glass.LocalLiquidGlass
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.material3.DropdownMenu as ComposeDropdownMenu

/**
 * DropdownMenu but overlaps anchor and has width constraints to better
 * match non-Compose implementation.
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(8.dp, (-56).dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalLiquidGlass.current) {
        // Popups can't see the window behind them, so the glass stays nearly opaque with a bright rim
        val colors = LiquidGlassDefaults.colors()
        ComposeDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.sizeIn(minWidth = 196.dp, maxWidth = 196.dp),
            offset = offset,
            scrollState = scrollState,
            properties = properties,
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(colors.rim, colors.rim.copy(alpha = colors.rim.alpha * 0.2f))),
            ),
            shadowElevation = 12.dp,
            content = content,
        )
        return
    }
    ComposeDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.sizeIn(minWidth = 196.dp, maxWidth = 196.dp),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        content = content,
    )
}

@Composable
fun RadioMenuItem(
    text: @Composable () -> Unit,
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        trailingIcon = {
            if (isChecked) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.RadioButtonChecked,
                    contentDescription = stringResource(MR.strings.selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = MaterialSymbols.Rounded.RadioButtonUnchecked,
                    contentDescription = stringResource(MR.strings.not_selected),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun NestedMenuItem(
    text: @Composable () -> Unit,
    children: @Composable ColumnScope.(() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nestedExpanded by remember { mutableStateOf(false) }
    val closeMenu = { nestedExpanded = false }

    Box {
        DropdownMenuItem(
            text = text,
            onClick = { nestedExpanded = true },
            trailingIcon = {
                Icon(
                    imageVector = MaterialSymbols.AutoMirroredRounded.ArrowRight,
                    contentDescription = null,
                )
            },
        )

        DropdownMenu(
            expanded = nestedExpanded,
            onDismissRequest = closeMenu,
            modifier = modifier,
        ) {
            children(closeMenu)
        }
    }
}
